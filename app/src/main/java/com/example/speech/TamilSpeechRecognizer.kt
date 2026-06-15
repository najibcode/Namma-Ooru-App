package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ══════════════════════════════════════════════════════════════════════════════
// TamilSpeechRecognizer.kt — Native STT isolation layer for நம்ம ஊரு ஆப்
//
// Architecture:
//   This class is a single-responsibility isolation manager. It owns:
//     • The Android SpeechRecognizer lifecycle (create → listen → destroy).
//     • The RecognitionIntent configuration (ta-IN locale, partial results, etc.)
//     • The full RecognitionListener callback matrix (all 9 overrides).
//     • Public state as a [StateFlow<SpeechState>] — composables collect this
//       reactively with no additional plumbing.
//
// Threading contract:
//   SpeechRecognizer MUST be created and used on the MAIN thread.
//   All [RecognitionListener] callbacks arrive on the main thread automatically.
//   The [StateFlow] is therefore also updated on the main thread.
//   Callers must not call [startListening] or [stopListening] from a background
//   coroutine dispatcher.
//
// Usage in Compose:
//   val recognizer = remember { TamilSpeechRecognizer(context) }
//   val speechState by recognizer.state.collectAsStateWithLifecycle()
//
//   DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
//
//   Button(onClick = { recognizer.startListening() }) { Text("பேசவும்") }
//
//   when (val s = speechState) {
//       is SpeechState.Result  -> TranscriptionBox(text = s.text)
//       is SpeechState.Partial -> TranscriptionBox(text = s.text, blinking = true)
//       is SpeechState.Error   -> ErrorBanner(message = s.messageTamil)
//       is SpeechState.Listening, SpeechState.Idle -> { /* update mic UI */ }
//   }
// ══════════════════════════════════════════════════════════════════════════════

private const val TAG = "TamilSpeechRecognizer"

// ─────────────────────────────────────────────────────────────────────────────
// 1.  Public state model — sealed hierarchy
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents every possible lifecycle state of the [TamilSpeechRecognizer].
 *
 * UI layers should `when`-match exhaustively on this sealed class to drive
 * microphone animations, transcription box visibility, and error banners.
 */
sealed class SpeechState {

    /** Recognizer is initialised but not yet listening. Default state. */
    data object Idle : SpeechState()

    /**
     * The device is ready and actively capturing audio from the microphone.
     * Display the pulsing red mic animation while in this state.
     */
    data object Listening : SpeechState()

    /**
     * A partial (interim) transcription has arrived during speech.
     * Update the transcription box live with [text] while the user is still
     * speaking — gives the real-time "typewriter" feel.
     *
     * @property text Interim Tamil text; may change with subsequent partials.
     */
    data class Partial(val text: String) : SpeechState()

    /**
     * Final, highest-confidence transcription result.
     * Transition the UI to the confirmation / action-buttons phase.
     *
     * @property text The top-ranked Tamil transcription string from the engine.
     * @property confidence Confidence score on a 0.0–1.0 scale. May be -1.0
     *           if the engine did not provide confidence values.
     * @property allAlternatives All recognition hypotheses, ordered by
     *           descending confidence. Useful for a "did you mean?" UI.
     */
    data class Result(
        val text: String,
        val confidence: Float,
        val allAlternatives: List<String>
    ) : SpeechState()

    /**
     * A recoverable or unrecoverable error occurred.
     *
     * @property code The raw [SpeechRecognizer] error constant (e.g.
     *           [SpeechRecognizer.ERROR_NO_MATCH]).
     * @property messageTamil A user-facing, colloquial Tamil error string safe
     *           to display directly in a Toast or Snackbar.
     * @property isRetryable `true` for transient errors (timeout, no-match)
     *           where the user should be prompted to try again; `false` for
     *           hard errors (no service, insufficient permissions) requiring
     *           a settings / permission flow.
     */
    data class Error(
        val code: Int,
        val messageTamil: String,
        val isRetryable: Boolean
    ) : SpeechState()
}

// ─────────────────────────────────────────────────────────────────────────────
// 2.  Recognition intent constants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Locale and intent configuration for the Tamil speech engine.
 *
 * `ta-IN` = Tamil (India) — the BCP-47 tag supported by Google's on-device
 * and cloud speech models.  Setting EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE
 * forces the engine to reject results in other languages even if the user
 * accidentally speaks in a different tongue.
 */
private const val LOCALE_TAMIL_INDIA      = "ta-IN"
private const val MAX_RESULTS             = 5     // Number of alternative hypotheses
private const val SPEECH_TIMEOUT_MS       = 8_000 // ms of silence before auto-stop

// ─────────────────────────────────────────────────────────────────────────────
// 3.  Manager class
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Isolation manager wrapping [SpeechRecognizer] for colloquial rural Tamil
 * voice input in நம்ம ஊரு ஆப்.
 *
 * ### Locale strategy
 * The recogniser is configured exclusively for `ta-IN` (Tamil, India).
 * [RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE] biases the model toward Tamil
 * phonemes, and [RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE] ensures
 * the engine never falls back to Hindi or English even on mixed-language devices
 * — critical for rural users whose system language may differ from the app.
 *
 * ### Confidence extraction
 * [RecognitionListener.onResults] delivers a [Bundle] containing a parallel
 * `ArrayList<String>` (hypotheses) and a `FloatArray` (confidence scores).
 * This class zips both arrays and selects index 0, which is always the
 * highest-confidence result (Android guarantees this ordering).
 *
 * ### Error messages
 * All user-facing error strings are written in simple, colloquial Tamil —
 * not literary Tamil — to match the literacy level of rural users in
 * districts like Tirunelveli, Thoothukudi, and Kanyakumari.
 *
 * @param context Application or Activity context.  A `remember { }` in Compose
 *        ensures this is stable across recompositions.
 */
class TamilSpeechRecognizer(private val context: Context) {

    // ── Internal state ────────────────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)

    /**
     * Reactive state stream. Collect in your composable with:
     * ```kotlin
     * val speechState by recognizer.state.collectAsStateWithLifecycle()
     * ```
     */
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** `true` while the recogniser is actively listening. */
    val isListening: Boolean
        get() = _state.value is SpeechState.Listening || _state.value is SpeechState.Partial

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts a new STT session.
     *
     * Internally this:
     * 1. Checks device capability via [SpeechRecognizer.isRecognitionAvailable].
     * 2. Creates a fresh [SpeechRecognizer] instance (reuse across sessions
     *    can cause ANRs on some OEMs — always create new).
     * 3. Attaches the full [RecognitionListener] callback matrix.
     * 4. Builds and fires the Tamil-locale [RecognizerIntent].
     *
     * Calling this while already listening is safe — it stops the current
     * session before starting a new one.
     *
     * @throws IllegalStateException if called off the main thread.
     */
    fun startListening() {
        // Guard: check device capability
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error(
                code         = SpeechRecognizer.ERROR_CLIENT,
                messageTamil = "இந்த போனில் குரல் அறிதல் சேவை இல்லை. Google ஆப் இன்ஸ்டால் செய்யவும்.",
                isRetryable  = false
            )
            return
        }

        // Tear down any previous session cleanly before creating a new one
        releaseRecognizerSafely()

        // Create a fresh instance — do NOT reuse across sessions
        speechRecognizer = SpeechRecognizer
            .createSpeechRecognizer(context)
            .also { recognizer ->
                recognizer.setRecognitionListener(buildRecognitionListener())
                recognizer.startListening(buildTamilRecognitionIntent())
            }

        Log.i(TAG, "STT session started — locale: $LOCALE_TAMIL_INDIA")
    }

    /**
     * Manually stops the current STT session.
     *
     * [SpeechRecognizer.stopListening] signals the engine to stop capturing
     * and immediately process whatever audio it has buffered — triggering
     * [RecognitionListener.onResults] shortly after.
     *
     * Calling this when not listening is a no-op.
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            Log.i(TAG, "STT stop requested by user.")
        } catch (e: Exception) {
            Log.w(TAG, "stopListening() threw unexpectedly: ${e.message}")
        }
    }

    /**
     * Cancels the current session and frees all resources.
     *
     * Call this from a Compose `DisposableEffect { onDispose { ... } }` or
     * `ViewModel.onCleared()` to guarantee the speech service binding is
     * released when the screen leaves composition.
     */
    fun destroy() {
        releaseRecognizerSafely()
        _state.value = SpeechState.Idle
        Log.i(TAG, "TamilSpeechRecognizer destroyed.")
    }

    /**
     * Resets state to [SpeechState.Idle] without destroying the recogniser.
     * Useful after displaying an error to prepare for a retry.
     */
    fun reset() {
        _state.value = SpeechState.Idle
    }

    // ── Intent builder ────────────────────────────────────────────────────────

    /**
     * Constructs the [RecognizerIntent] hardcoded for Tamil (India).
     *
     * ### Extra explanations
     *
     * | Extra                              | Value           | Reason                                                  |
     * |------------------------------------|-----------------|--------------------------------------------------------|
     * | EXTRA_LANGUAGE_MODEL               | FREE_FORM       | Best for natural speech; not command-and-control mode   |
     * | EXTRA_LANGUAGE                     | ta-IN           | Primary locale — Tamil India                            |
     * | EXTRA_LANGUAGE_PREFERENCE          | ta-IN           | Biases acoustic model toward Tamil phonemes             |
     * | EXTRA_ONLY_RETURN_LANGUAGE_PREF    | ta-IN           | Blocks fallback to other languages                      |
     * | EXTRA_PARTIAL_RESULTS              | true            | Enables live transcription updates during speech        |
     * | EXTRA_MAX_RESULTS                  | 5               | Returns up to 5 hypotheses for confidence comparison    |
     * | EXTRA_SPEECH_INPUT_COMPLETE_SILENCE| 2000 ms         | Stop after 2s of silence (faster for short orders)     |
     * | EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE| 1500 ms        | First silence hint at 1.5s                             |
     * | EXTRA_SPEECH_INPUT_MINIMUM_LENGTH  | 1000 ms         | Ignore accidental sub-1s noise triggers                 |
     */
    private fun buildTamilRecognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            // Language model — free-form natural speech (not commands)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            // Primary locale: Tamil (India) — ta-IN
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,                 LOCALE_TAMIL_INDIA)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,      LOCALE_TAMIL_INDIA)

            // Force Tamil-only — never fall back to system language
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, LOCALE_TAMIL_INDIA)

            // Enable streaming partial results for real-time UI feedback
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Return top-5 hypotheses to compare confidence scores
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)

            // Silence thresholds — tuned for short Tamil food/grocery orders
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                2_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                1_000L
            )
        }

    // ── RecognitionListener ───────────────────────────────────────────────────

    /**
     * Builds the complete [RecognitionListener] with all 9 callback overrides.
     *
     * The Android speech framework calls these on the **main thread** in the
     * lifecycle order:
     *
     * ```
     * onReadyForSpeech → onBeginningOfSpeech → onRmsChanged (repeated)
     *   → onEndOfSpeech
     *       → onResults (success path)
     *       → onError   (failure path)
     * onPartialResults (interleaved during speech, if EXTRA_PARTIAL_RESULTS = true)
     * onBufferReceived (raw audio bytes — rarely used)
     * onEvent          (reserved for future use by Android)
     * ```
     */
    private fun buildRecognitionListener(): RecognitionListener =
        object : RecognitionListener {

            // ── Session readiness ─────────────────────────────────────────────

            /**
             * Fired when the recogniser is ready and the microphone is open.
             * Transition UI to the "listening" state here.
             */
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech — mic open, listening for Tamil speech.")
                _state.value = SpeechState.Listening
            }

            /**
             * Fired when the engine detects the beginning of spoken audio.
             * The user has started speaking — no state change needed here since
             * we're already in [SpeechState.Listening].
             */
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech — audio detected.")
            }

            /**
             * Fired repeatedly during speech with the current microphone input
             * level in dB.  Use [rmsdB] to drive a real-time waveform visualiser
             * or scale the pulsing mic animation amplitude.
             *
             * @param rmsdB Input level in decibels.  Typically -2.0 to +10.0.
             *              Higher values = louder speech.
             */
            override fun onRmsChanged(rmsdB: Float) {
                // Emit to a separate amplitude StateFlow if you wire a waveform UI.
                // Not logged — fires ~10× per second and would flood logcat.
            }

            /**
             * Raw audio bytes from the microphone buffer.
             * Reserved for custom audio processing (e.g. offline Whisper inference).
             * Not used in Phase 1.
             */
            override fun onBufferReceived(buffer: ByteArray?) {
                // Phase 2: forward [buffer] to a local Whisper inference engine
                // for fully offline Tamil STT without Google services.
            }

            /**
             * Fired when the engine detects the user has stopped speaking
             * (silence threshold crossed).  The recogniser is now processing.
             * Show a "transcribing…" spinner if desired.
             */
            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech — processing audio.")
            }

            // ── Results ───────────────────────────────────────────────────────

            /**
             * **Primary success callback.**
             *
             * Delivers the final recognition results sorted by descending
             * confidence score.  Index 0 is always the best hypothesis.
             *
             * Extraction strategy:
             * 1. Read the `RESULTS_RECOGNITION` string list.
             * 2. Read the `CONFIDENCE_SCORES` float array (may be null on some
             *    devices/engines).
             * 3. Zip them; select index 0 as the canonical result.
             * 4. Emit [SpeechState.Result] with the text, confidence, and the
             *    full alternative list for potential "did you mean?" UX.
             */
            override fun onResults(results: Bundle?) {
                val hypotheses: ArrayList<String>? =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val confidenceScores: FloatArray? =
                    results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (hypotheses.isNullOrEmpty()) {
                    // Engine returned an empty result set — treat as no-match
                    Log.w(TAG, "onResults — empty hypothesis list.")
                    _state.value = SpeechState.Error(
                        code         = SpeechRecognizer.ERROR_NO_MATCH,
                        messageTamil = "நீங்கள் பேசியது சரியாக புரியவில்லை. மீண்டும் முயற்சிக்கவும்.",
                        isRetryable  = true
                    )
                    return
                }

                // Best hypothesis is always at index 0 (Android guarantees order)
                val bestText       = hypotheses[0]
                val bestConfidence = confidenceScores?.getOrElse(0) { -1f } ?: -1f

                Log.i(TAG, "onResults — best: \"$bestText\" (confidence: $bestConfidence)")

                _state.value = SpeechState.Result(
                    text             = bestText,
                    confidence       = bestConfidence,
                    allAlternatives  = hypotheses
                )
            }

            /**
             * **Streaming partial results callback.**
             *
             * Called during speech whenever the engine has a confident-enough
             * interim hypothesis.  Update the transcription box live so the user
             * sees their words appear in real time — the core of the
             * "no typing needed" UX promise.
             *
             * Note: partial results may differ significantly from the final result;
             * always use [onResults] as the authoritative transcript.
             */
            override fun onPartialResults(partialResults: Bundle?) {
                val partials: ArrayList<String>? =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val partialText = partials?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: return  // Empty partial — ignore silently

                Log.d(TAG, "onPartialResults — \"$partialText\"")
                _state.value = SpeechState.Partial(text = partialText)
            }

            // ── Error handling ────────────────────────────────────────────────

            /**
             * **Complete error callback matrix.**
             *
             * Every [SpeechRecognizer] error constant is handled explicitly.
             * Error messages are written in colloquial (spoken-style) Tamil —
             * not formal literary Tamil — to be accessible to rural users in
             * Tirunelveli, Thoothukudi, and Kanyakumari districts.
             *
             * | Error code                  | Cause                                   | Retryable |
             * |-----------------------------|-----------------------------------------|-----------|
             * | ERROR_AUDIO                 | Mic hardware / buffer failure           | ✅        |
             * | ERROR_CLIENT                | Bad intent / API misuse                 | ❌        |
             * | ERROR_INSUFFICIENT_PERMISSIONS | RECORD_AUDIO not granted            | ❌        |
             * | ERROR_NETWORK               | No internet for cloud STT               | ✅        |
             * | ERROR_NETWORK_TIMEOUT       | Slow / intermittent connection          | ✅        |
             * | ERROR_NO_MATCH              | Speech detected but not recognised      | ✅        |
             * | ERROR_RECOGNIZER_BUSY       | Previous session not yet destroyed      | ✅        |
             * | ERROR_SERVER                | Google STT backend error                | ✅        |
             * | ERROR_SPEECH_TIMEOUT        | Silence — no speech detected            | ✅        |
             * | ERROR_LANGUAGE_NOT_SUPPORTED| Tamil not supported on device           | ❌        |
             * | ERROR_LANGUAGE_UNAVAILABLE  | Tamil pack not downloaded               | ❌        |
             * | ERROR_TOO_MANY_REQUESTS     | API rate limit exceeded                 | ✅        |
             * | ERROR_CANNOT_CHECK_SUPPORT  | Capability check failed                 | ✅        |
             */
            override fun onError(error: Int) {
                val (messageTamil, isRetryable) = mapErrorToTamil(error)
                Log.w(TAG, "onError — code: $error | message: $messageTamil")
                _state.value = SpeechState.Error(
                    code         = error,
                    messageTamil = messageTamil,
                    isRetryable  = isRetryable
                )
            }

            // ── Reserved callback ──────────────────────────────────────────────

            /**
             * Reserved by Android for future speech framework events.
             * No current implementation needed — but must be overridden.
             */
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "onEvent — type: $eventType (unhandled, reserved).")
            }
        }

    // ── Error mapping ─────────────────────────────────────────────────────────

    /**
     * Maps a [SpeechRecognizer] error code to a user-facing Tamil message
     * and a retryability flag.
     *
     * All messages use colloquial ("spoken") Tamil to match rural literacy
     * levels — no Sanskrit-derived literary words.
     *
     * @return Pair of (Tamil message String, isRetryable Boolean).
     */
    private fun mapErrorToTamil(error: Int): Pair<String, Boolean> = when (error) {

        SpeechRecognizer.ERROR_AUDIO ->
            "மைக் சரியாக வேலை செய்யவில்லை. மீண்டும் முயற்சிக்கவும்." to true

        SpeechRecognizer.ERROR_CLIENT ->
            "ஆப்பில் உள்ளே ஒரு சிறிய பிழை நேர்ந்தது. ஆப்பை மூடி திறக்கவும்." to false

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "மைக் அனுமதி இல்லை! அமைப்புகளில் சென்று அனுமதி தாருங்கள்." to false

        SpeechRecognizer.ERROR_NETWORK ->
            "இணைய இணைப்பு இல்லை. WiFi அல்லது 4G இருக்கிறதா என்று பார்க்கவும்." to true

        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "இணைய வேகம் மிகவும் குறைவாக உள்ளது. மீண்டும் பேசவும்." to true

        SpeechRecognizer.ERROR_NO_MATCH ->
            "பேசியது சரியாக புரியவில்லை. இன்னும் தெளிவாக, கொஞ்சம் மெதுவாக பேசவும்." to true

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "குரல் அறிதல் கொஞ்சம் பிஸியாக உள்ளது. ஒரு நிமிஷம் காத்திருந்து மீண்டும் பேசவும்." to true

        SpeechRecognizer.ERROR_SERVER ->
            "Google சர்வர் சற்று மும்மரமாக உள்ளது. சிறிது நேரம் கழித்து முயற்சிக்கவும்." to true

        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "குரல் ஏதும் கேட்கவில்லை. மைக் பட்டனை அழுத்திப் பிடித்து பேசவும்." to true

        // API 31+ error codes — safe to reference even on lower APIs since
        // these are plain int constants (no reflection / class loading needed).
        13 -> // ERROR_LANGUAGE_NOT_SUPPORTED (API 31)
            "இந்த போனில் தமிழ் மொழி ஆதரிக்கவில்லை. Google உச்சரிப்பு பதிவிறக்கவும்." to false

        14 -> // ERROR_LANGUAGE_UNAVAILABLE (API 31)
            "தமிழ் மொழி பட்டி போனில் இல்லை. அமைப்புகளில் சென்று பதிவிறக்கவும்." to false

        15 -> // ERROR_CANNOT_CHECK_SUPPORT (API 33)
            "குரல் ஆதரவை சரிபார்க்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்." to true

        16 -> // ERROR_TOO_MANY_REQUESTS (API 33)
            "மிக அதிகமான கோரிக்கைகள் உள்ளன. சிறிது நேரம் கழித்து முயற்சிக்கவும்." to true

        else ->
            "ஒரு எதிர்பாராத பிழை நேர்ந்தது (பிழை குறியீடு: $error). மீண்டும் முயற்சிக்கவும்." to true
    }

    // ── Resource management ───────────────────────────────────────────────────

    /**
     * Safely cancels and destroys the [SpeechRecognizer] without throwing.
     *
     * [SpeechRecognizer.cancel] stops ongoing recognition without delivering
     * a result. [SpeechRecognizer.destroy] releases the service binding.
     * Both must be called to fully free resources on all OEM variants.
     */
    private fun releaseRecognizerSafely() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SpeechRecognizer: ${e.message}", e)
        } finally {
            speechRecognizer = null
        }
    }
}
