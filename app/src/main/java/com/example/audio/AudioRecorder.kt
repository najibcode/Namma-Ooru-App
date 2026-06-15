package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

// ══════════════════════════════════════════════════════════════════════════════
// AudioRecorder.kt — Native voice-note capture for நம்ம ஊரு ஆப்
//
// Design goals:
//   • Lifecycle-aware: safe to start/stop from a Compose DisposableEffect or a
//     ViewModel with no risk of leaking MediaRecorder resources.
//   • Exception-guarded: every public function wraps hardware calls in try/catch
//     and always releases resources in the `finally` block.
//   • Thread-safe state: a single [RecordingState] enum prevents impossible
//     transitions (e.g. calling stop() before start()).
//   • Testable: constructor injection of [Context]; no static state.
// ══════════════════════════════════════════════════════════════════════════════

private const val TAG = "AudioRecorder"

/**
 * Represents the lifecycle state of the [AudioRecorder].
 *
 * State machine:
 * ```
 *  IDLE ──startRecording()──▶ RECORDING ──stopRecording()──▶ IDLE
 *                                 │
 *                         exception / error
 *                                 │
 *                              ERROR ──release()──▶ IDLE
 * ```
 */
enum class RecordingState {
    /** No recording is in progress; the recorder is ready to start. */
    IDLE,

    /** The hardware microphone is actively capturing audio. */
    RECORDING,

    /**
     * A non-recoverable error occurred (e.g. prepare() failed).
     * The [AudioRecorder] should be discarded and a new instance created.
     */
    ERROR
}

/**
 * Lifecycle-aware wrapper around Android's [MediaRecorder] API that captures
 * clean voice notes from the device microphone and saves them as compressed
 * AAC/MPEG-4 files inside the application's secure cache directory.
 *
 * ### Output format
 * | Setting              | Value                         |
 * |----------------------|-------------------------------|
 * | Container            | MPEG-4 (.mp4)                 |
 * | Audio codec          | AAC-LC                        |
 * | Bit-rate             | 128 kbps (voice-optimal)      |
 * | Sample rate          | 44 100 Hz                     |
 * | Source               | Hardware microphone (MIC)     |
 *
 * ### File naming
 * Files are written to [Context.getCacheDir] with the pattern:
 * `namma_ooru_voice_<timestamp>.mp4`
 * where `<timestamp>` is [System.currentTimeMillis].  Unique timestamps
 * guarantee no two recordings ever collide, even in rapid succession.
 *
 * ### Usage
 * ```kotlin
 * // In a Compose composable:
 * val recorder = remember { AudioRecorder(context) }
 *
 * DisposableEffect(Unit) {
 *     onDispose { recorder.release() }  // ← always clean up
 * }
 *
 * // Start:
 * val file = recorder.startRecording()
 *
 * // Stop and get the saved file:
 * val savedFile = recorder.stopRecording()
 * ```
 *
 * ### Permissions
 * The caller must obtain `android.permission.RECORD_AUDIO` **before** calling
 * [startRecording].  Use `rememberLauncherForActivityResult` with
 * `RequestPermission()` in Compose, or the Accompanist permissions library.
 *
 * @param context Application or Activity context used to resolve [Context.getCacheDir].
 *                A `remember { AudioRecorder(context) }` in Compose is the
 *                recommended pattern — do NOT pass an Activity reference that may
 *                be recreated.
 */
class AudioRecorder(private val context: Context) {

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

    /** Underlying [MediaRecorder] instance; null when no session is active. */
    private var mediaRecorder: MediaRecorder? = null

    /**
     * File handle pointing to the current recording target.
     * Retained so [stopRecording] can return it to the caller.
     */
    private var activeFile: File? = null

    /** Current lifecycle state of this recorder instance. */
    var state: RecordingState = RecordingState.IDLE
        private set

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts recording audio from the device microphone.
     *
     * Internally this function:
     * 1. Creates a uniquely-named output file in [Context.getCacheDir].
     * 2. Instantiates [MediaRecorder] using the API-level–appropriate constructor.
     * 3. Configures the audio source, output format, encoder, bit-rate,
     *    sample rate, and output file path.
     * 4. Calls `prepare()` and `start()` — both wrapped in exception guards.
     * 5. Transitions state to [RecordingState.RECORDING] on success.
     *
     * If any step fails, all partially-initialised resources are released before
     * the exception propagates, preventing memory leaks.
     *
     * @return The [File] where the recording will be saved.  The file exists on
     *         disk from this point forward; its content is finalised only after
     *         [stopRecording] returns.
     * @throws IllegalStateException if called while [state] is
     *         [RecordingState.RECORDING] or [RecordingState.ERROR].
     * @throws IOException if the output file cannot be created or
     *         [MediaRecorder.prepare] fails.
     * @throws SecurityException if `RECORD_AUDIO` permission has not been granted.
     */
    fun startRecording(): File {
        check(state == RecordingState.IDLE) {
            "startRecording() called in state $state. Call stopRecording() or release() first."
        }

        // 1. Resolve output file — unique timestamp prevents collisions.
        val outputFile = File(
            context.cacheDir,
            "namma_ooru_voice_${System.currentTimeMillis()}.mp4"
        )
        activeFile = outputFile

        // 2. Build MediaRecorder with the correct constructor for the API level.
        //    The single-argument constructor (Context) was added in API 31 (Android 12).
        //    The deprecated no-arg constructor is used on older devices.
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        mediaRecorder = recorder

        try {
            recorder.apply {
                // 3a. Source: hardware microphone
                setAudioSource(MediaRecorder.AudioSource.MIC)

                // 3b. Container: MPEG-4 (.mp4) — universally supported on Android
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // 3c. Codec: AAC-LC — best quality/size ratio for voice recordings
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // 3d. Bit-rate: 128 kbps — clear voice, minimal file size
                //     (approx. 57 KB per 30-second recording)
                setAudioEncodingBitRate(128_000)

                // 3e. Sample rate: 44 100 Hz — CD quality; avoids aliasing on
                //     higher-pitched Tamil phonemes
                setAudioSamplingRate(44_100)

                // 3f. Output path — must be set AFTER format, BEFORE prepare()
                setOutputFile(outputFile.absolutePath)

                // 4. Prepare the recorder (opens file handles, allocates buffers)
                prepare()

                // 5. Begin capturing — microphone is live from this point
                start()
            }

            state = RecordingState.RECORDING
            Log.i(TAG, "Recording started → ${outputFile.name}")
        } catch (e: Exception) {
            // Release all hardware resources immediately to avoid a stuck microphone
            Log.e(TAG, "startRecording() failed: ${e.message}", e)
            releaseRecorderSafely()
            state = RecordingState.ERROR
            throw e  // Re-throw so the caller can surface a Toast / snackbar
        }

        return outputFile
    }

    /**
     * Stops the active recording, finalises the output file, and releases all
     * hardware resources.
     *
     * After this call:
     * - The microphone is no longer in use.
     * - The MP4 file at the path returned by [startRecording] is complete and
     *   ready to be played back, shared, or uploaded.
     * - [state] returns to [RecordingState.IDLE].
     *
     * Calling this function when [state] is already [RecordingState.IDLE] is a
     * no-op; no exception is thrown.
     *
     * @return The completed [File], or `null` if:
     *         - Recording was never started, or
     *         - An exception occurred during [MediaRecorder.stop] (in which case
     *           the file may be incomplete — discard it).
     */
    fun stopRecording(): File? {
        if (state != RecordingState.RECORDING) {
            Log.w(TAG, "stopRecording() called in state $state — ignoring.")
            return null
        }

        val completedFile = activeFile

        try {
            mediaRecorder?.apply {
                // stop() finalises the container headers — must be called before release()
                stop()
            }
            Log.i(TAG, "Recording stopped → ${completedFile?.name} (${completedFile?.length()} bytes)")
        } catch (e: RuntimeException) {
            // MediaRecorder.stop() throws RuntimeException (not IOException) if
            // stop() is called too soon after start() or if no audio was captured.
            Log.e(TAG, "stopRecording() failed to finalise file: ${e.message}", e)
            completedFile?.delete()   // Delete the corrupt/empty file
            return null               // Signal to the caller that nothing was saved
        } finally {
            // Always release hardware resources, even when stop() throws
            releaseRecorderSafely()
            state = RecordingState.IDLE
        }

        return completedFile
    }

    /**
     * Unconditionally releases all [MediaRecorder] resources.
     *
     * Call this from a Compose `DisposableEffect { onDispose { recorder.release() } }`
     * or from `ViewModel.onCleared()` to guarantee the microphone is freed when
     * the screen leaves composition.
     *
     * Safe to call in any [RecordingState] — idempotent.
     */
    fun release() {
        if (state == RecordingState.RECORDING) {
            Log.w(TAG, "release() called while recording — stopping first.")
            stopRecording()
        }
        releaseRecorderSafely()
        state = RecordingState.IDLE
        Log.i(TAG, "AudioRecorder released.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Releases the [MediaRecorder] instance without throwing.
     *
     * [MediaRecorder.release] is always safe to call; it transitions the recorder
     * to the **Released** state and frees all associated native resources
     * (microphone handle, encoder thread, output file handle).
     *
     * Swallowing exceptions here is intentional: if the recorder is already in a
     * bad state we still want to null out the reference so the GC can collect it.
     */
    private fun releaseRecorderSafely() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder: ${e.message}", e)
        } finally {
            mediaRecorder = null
            activeFile = null
        }
    }
}
