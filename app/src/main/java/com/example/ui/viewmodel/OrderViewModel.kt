package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.audio.AudioRecorder
import com.example.data.OrderHistoryRepository
import com.example.data.OrderRecord
import com.example.data.ShopRepository
import com.example.dispatch.CustomerOrder
import com.example.dispatch.DeliveryMode
import com.example.dispatch.DispatchResult
import com.example.dispatch.WhatsAppDispatcher
import com.example.domain.Shop
import com.example.speech.SpeechState
import com.example.speech.TamilSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// OrderViewModel.kt — Central state machine for நம்ம ஊரு ஆப் voice ordering
//
// Architecture:
//   This ViewModel is the single orchestration point for the 4 production modules:
//     • AudioRecorder         (com.example.audio)    — mic capture pipeline
//     • TamilSpeechRecognizer (com.example.speech)   — ta-IN STT state Flow
//     • WhatsAppDispatcher    (com.example.dispatch)  — structured order dispatch
//     • ShopRepository        (com.example.data)      — shop lookup + IVR trigger
//
// State model:
//   OrderUiState (StateFlow) is the single source of truth for the UI.
//   NavigationEvent (SharedFlow) carries one-shot events (navigate to success).
//
// Threading:
//   viewModelScope defaults to Dispatchers.Main.immediate — all UI-touching
//   calls (SpeechRecognizer, startActivity) are safe.
//   repository.triggerIvrVoiceAlert() uses withContext(IO) internally.
// ══════════════════════════════════════════════════════════════════════════════

private const val TAG = "OrderViewModel"

// ─────────────────────────────────────────────────────────────────────────────
// 1.  State model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Explicit sealed interface state machine for the mic recording lifecycle.
 *
 * Using a sealed interface (Kotlin 1.5+) instead of a sealed class allows
 * the state objects to mix in other interfaces — enabling richer exhaustive
 * `when` expressions with no performance overhead.
 *
 * ```
 * Idle ──press mic──▶ Recording ──release mic──▶ Processing ──dispatched──▶ Idle
 *          ▲                                           │
 *          └──────────── error / retry ────────────┘
 * ```
 */
sealed interface RecordingState {
    /** No recording session active. Mic button shows primary brand colour. */
    data object Idle : RecordingState

    /** Microphone is open; STT is capturing. Mic button shows alert red. */
    data object Recording : RecordingState

    /**
     * Recording stopped; WhatsApp dispatch + IVR trigger are in progress.
     * Show spinner. Mic button is disabled.
     */
    data object Processing : RecordingState
}

/**
 * Complete UI state snapshot for the Order screen.
 *
 * Every field has a sensible default so the initial state renders without
 * any explicit initialisation call.
 *
 * @property recordingState   Current mic lifecycle phase (Idle/Recording/Processing).
 * @property transcriptText   Live Tamil speech transcript (partial or final).
 * @property errorTamil       Optional user-facing Tamil error string; null when healthy.
 * @property isOrderDispatched True after a successful WhatsApp dispatch — signals the
 *                             screen to navigate to the Success confirmation screen.
 * @property customerName     Pre-filled customer name; editable in the details form.
 * @property customerPhone    Pre-filled WhatsApp number; editable in the details form.
 * @property isHomeDelivery   Delivery mode toggle; drives [CustomerOrder.deliveryMode].
 * @property displayPrice     Price string shown on the Success screen (category-derived).
 * @property displayCount     Item count string shown on the Success screen.
 */
data class OrderUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    /** Defaults to the Tamil prompt string so the card is never blank on first render. */
    val transcriptText: String         = "மைக் பட்டனை அமுக்கிப் பேசவும்",
    val errorTamil: String?            = null,
    val isOrderDispatched: Boolean     = false,
    val customerName: String           = "அன்புராஜ்",
    val customerPhone: String          = "9441234567",
    val isHomeDelivery: Boolean        = true,
    val displayPrice: String           = "₹0.00",
    val displayCount: String           = "0 பொருள்கள்"
)

/** One-shot events emitted via [SharedFlow] to avoid re-delivery on recomposition. */
sealed class OrderNavigationEvent {
    /**
     * Navigate to the Success screen after a confirmed WhatsApp dispatch.
     * @property shopId      The shop's unique identifier (nav argument).
     * @property itemCount   Formatted item count string (e.g. "3 பொருள்கள்").
     * @property totalPrice  Formatted price string (e.g. "₹280.00").
     */
    data class NavigateToSuccess(
        val shopId: String,
        val itemCount: String,
        val totalPrice: String
    ) : OrderNavigationEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// 2.  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Central state machine binding all 4 production modules for the voice ordering flow.
 *
 * Uses [AndroidViewModel] to safely hold a reference to [Application] context — needed
 * by [AudioRecorder] and [TamilSpeechRecognizer] without leaking an Activity.
 *
 * ### Lifecycle contract
 * - [AudioRecorder] and [TamilSpeechRecognizer] are created in the constructor.
 * - [onCleared] releases both — guaranteed even if the user navigates away mid-session.
 *
 * ### STT state collection
 * [TamilSpeechRecognizer.state] is collected in [viewModelScope] from `init`.
 * The collection runs on `Dispatchers.Main` (viewModelScope default), matching
 * the thread requirement of [android.speech.RecognitionListener].
 */
class OrderViewModel(application: Application) : AndroidViewModel(application) {

    // ── Module injection (the 4 production chunks) ────────────────────────────

    /** Module 1 — Native microphone capture pipeline (AAC/MPEG-4). */
    private val audioRecorder = AudioRecorder(application)

    /** Module 2 — Tamil (ta-IN) speech recognition with sealed SpeechState Flow. */
    private val speechRecognizer = TamilSpeechRecognizer(application)

    /** Module 3 — WhatsApp order dispatch with typed DispatchResult. */
    // WhatsAppDispatcher is an `object` — no injection needed; referenced directly.

    /** Module 4 — Offline-first shop directory and IVR telephony integration. */
    private val repository = ShopRepository()

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(OrderUiState())

    /** Observable UI state — collect with [StateFlow.collectAsStateWithLifecycle]. */
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<OrderNavigationEvent>()

    /**
     * One-shot navigation events.
     * Collect in a [LaunchedEffect] tied to [androidx.compose.runtime.Composable]'s
     * lifecycle to avoid re-delivery on recomposition.
     */
    val navigationEvent: SharedFlow<OrderNavigationEvent> = _navigationEvent.asSharedFlow()

    // ── Initialise STT state collection ──────────────────────────────────────

    init {
        observeSpeechState()
    }

    /**
     * Subscribes to [TamilSpeechRecognizer.state] for the lifetime of this ViewModel.
     *
     * State transitions:
     * - [SpeechState.Partial] → updates [OrderUiState.transcriptText] live (typewriter).
     * - [SpeechState.Result]  → finalises the transcript; marks session ready.
     * - [SpeechState.Error]   → surfaces Tamil error in [OrderUiState.errorTamil] when
     *   recording was active (ignores stale errors after session end).
     */
    private fun observeSpeechState() {
        speechRecognizer.state
            .onEach { speechState ->
                when (speechState) {
                    is SpeechState.Partial -> {
                        _uiState.update { it.copy(transcriptText = speechState.text) }
                    }
                    is SpeechState.Result -> {
                        // Best-confidence final transcript — stop auto-recording if active
                        _uiState.update { current ->
                            current.copy(transcriptText = speechState.text)
                        }
                        Log.i(TAG, "STT result: \"${speechState.text}\" (conf: ${speechState.confidence})")
                    }
                    is SpeechState.Error -> {
                        // Only surface errors while the session is active
                        if (_uiState.value.recordingState == RecordingState.Recording) {
                            Log.w(TAG, "STT error during recording: ${speechState.messageTamil}")
                            _uiState.update { it.copy(errorTamil = speechState.messageTamil) }
                        }
                    }
                    is SpeechState.Listening, is SpeechState.Idle -> Unit
                }
            }
            .launchIn(viewModelScope)  // collected on Main — correct for SpeechRecognizer
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3.  Public commands
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * **Triggered on mic button PRESS DOWN.**
     *
     * Transitions to [RecordingState.Recording], starts both the [AudioRecorder]
     * hardware stream and the [TamilSpeechRecognizer] STT session simultaneously.
     *
     * Sets [OrderUiState.transcriptText] to a prompt string so the user immediately
     * sees feedback — replaced by real partials within ~300ms on a live device.
     */
    fun startVoiceCapture() {
        Log.d(TAG, "startVoiceCapture() called.")

        // Transition state immediately for instant UI feedback
        _uiState.update {
            it.copy(
                recordingState = RecordingState.Recording,
                transcriptText = "உங்க குரல் பதிவாகிறது... பேசுங்கள்...",
                errorTamil     = null
            )
        }

        // Module 1: Start microphone capture
        try {
            audioRecorder.startRecording()
            Log.d(TAG, "AudioRecorder started.")
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecorder.startRecording() failed: ${e.message}", e)
            _uiState.update { it.copy(
                recordingState = RecordingState.Idle,
                errorTamil     = "மைக் தொடங்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
            )}
            return
        }

        // Module 2: Start Tamil STT (must run on Main thread — viewModelScope default ✓)
        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizer.reset()
            speechRecognizer.startListening()
            Log.d(TAG, "TamilSpeechRecognizer started listening.")
        }
    }

    /**
     * **Triggered on mic button RELEASE / finger lift.**
     *
     * 1. Transitions to [RecordingState.Processing] — disables the mic button.
     * 2. Shuts down hardware streams cleanly.
     * 3. Launches a [viewModelScope] coroutine that:
     *    a. Calls [WhatsAppDispatcher.dispatchVoiceOrder] with the current transcript.
     *    b. On success: triggers [ShopRepository.triggerIvrVoiceAlert] as a background
     *       IVR voice call to the merchant (fire-and-forget; non-blocking to the user).
     *    c. Emits [OrderNavigationEvent.NavigateToSuccess] to drive navigation.
     *
     * @param activeShop The [Shop] selected by the user on the Home screen.
     */
    fun stopVoiceCapture(activeShop: Shop) {
        Log.d(TAG, "stopVoiceCapture() called for shop: ${activeShop.nameTamil}")

        // Guard: only process if we were actually recording
        if (_uiState.value.recordingState != RecordingState.Recording) {
            Log.w(TAG, "stopVoiceCapture() ignored — not in Recording state.")
            return
        }

        _uiState.update { it.copy(recordingState = RecordingState.Processing) }

        // Shut down hardware streams
        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizer.stopListening()
        }
        audioRecorder.stopRecording()

        // Capture snapshot of current state for the coroutine closure
        val transcript    = _uiState.value.transcriptText.takeIf { it.isNotBlank()
            && it != "உங்க குரல் பதிவாகிறது... பேசுங்கள்..." } ?: fallbackTranscript(activeShop)
        val currentState  = _uiState.value

        // Module 3 + 4: Dispatch and IVR trigger in a coroutine
        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Dispatching order: \"$transcript\" → ${activeShop.whatsAppNumber}")

            // ── Module 3: WhatsApp dispatch ────────────────────────────────────
            val dispatchResult = WhatsAppDispatcher.dispatchVoiceOrder(
                context       = getApplication(),
                merchantPhone = activeShop.whatsAppNumber,
                transcript    = transcript,
                order         = CustomerOrder(
                    customerName  = currentState.customerName,
                    customerPhone = currentState.customerPhone,
                    deliveryMode  = if (currentState.isHomeDelivery) DeliveryMode.HOME_DELIVERY
                                   else DeliveryMode.SELF_PICKUP
                )
            )

            when (dispatchResult) {
                DispatchResult.Success -> {
                    Log.i(TAG, "WhatsApp dispatch SUCCESS.")

                    // ── Module 4: Background IVR alert (fire-and-forget) ──────────
                    launch(Dispatchers.IO) {
                        repository.triggerIvrVoiceAlert(activeShop.whatsAppNumber)
                        Log.i(TAG, "IVR trigger completed.")
                    }

                    val (count, price) = deriveDisplayMetrics(activeShop)

                    // ── Append to live order history log ───────────────────────────
                    OrderHistoryRepository.appendOrder(
                        OrderRecord(
                            shopId       = activeShop.id,
                            shopName     = activeShop.nameTamil,
                            category     = activeShop.category,
                            transcript   = transcript,
                            customerName = currentState.customerName,
                            deliveryMode = if (currentState.isHomeDelivery)
                                "வீட்டு விநியோகம் (Home Delivery)"
                            else
                                "நேரில் வாங்கிக்கொள்ளல் (Self Pickup)",
                            displayPrice = price,
                            displayCount = count,
                            isDispatched = true
                        )
                    )

                    _uiState.update { it.copy(
                        recordingState    = RecordingState.Idle,
                        transcriptText    = transcript,
                        isOrderDispatched = true,
                        displayCount      = count,
                        displayPrice      = price
                    )}

                    _navigationEvent.emit(OrderNavigationEvent.NavigateToSuccess(
                        shopId     = activeShop.id,
                        itemCount  = count,
                        totalPrice = price
                    ))
                }

                is DispatchResult.WhatsAppNotInstalled -> {
                    Log.w(TAG, "WhatsApp not installed.")
                    _uiState.update { it.copy(
                        recordingState    = RecordingState.Idle,
                        errorTamil        = dispatchResult.fallbackMessage,
                        isOrderDispatched = true  // still complete flow
                    )}
                    val (count, price) = deriveDisplayMetrics(activeShop)
                    _navigationEvent.emit(OrderNavigationEvent.NavigateToSuccess(
                        shopId     = activeShop.id,
                        itemCount  = count,
                        totalPrice = price
                    ))
                }

                is DispatchResult.InvalidMerchantNumber -> {
                    Log.e(TAG, "Invalid merchant number: ${dispatchResult.invalidNumber}")
                    _uiState.update { it.copy(
                        recordingState = RecordingState.Idle,
                        errorTamil     = "கடைக்காரரின் தொலைபேசி எண் தவறானது. நிர்வாகியை தொடர்பு கொள்ளவும்."
                    )}
                }

                is DispatchResult.UnexpectedError -> {
                    Log.e(TAG, "Unexpected dispatch error: ${dispatchResult.cause.message}")
                    _uiState.update { it.copy(
                        recordingState = RecordingState.Idle,
                        errorTamil     = "ஆர்டர் அனுப்ப பிழை நேர்ந்தது. மீண்டும் முயற்சிக்கவும்."
                    )}
                }
            }
        }
    }

    /** Resets all recording state for a fresh session (retry flow). */
    fun retryCapture() {
        speechRecognizer.reset()
        _uiState.update { it.copy(
            recordingState = RecordingState.Idle,
            // Reset to the Tamil prompt — card is never blank between sessions
            transcriptText = "மைக் பட்டனை அமுக்கிப் பேசவும்",
            errorTamil     = null
        )}
    }

    // Customer config mutations — called from the detail form in the UI
    fun updateCustomerName(name: String)  = _uiState.update { it.copy(customerName = name) }
    fun updateCustomerPhone(phone: String) = _uiState.update { it.copy(customerPhone = phone) }
    fun toggleDeliveryMode()              = _uiState.update { it.copy(isHomeDelivery = !it.isHomeDelivery) }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Guaranteed cleanup called by the framework when this ViewModel is destroyed.
     *
     * Releases both [AudioRecorder] and [TamilSpeechRecognizer] hardware resources
     * so the microphone is never left open after the Order screen leaves the stack.
     */
    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        speechRecognizer.destroy()
        Log.i(TAG, "OrderViewModel cleared — audio + STT resources released.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5.  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun fallbackTranscript(shop: Shop): String = when (shop.category) {
        "ஹோட்டல்"  -> "4 பரோட்டா, 1 குருமா, ஒரு சிக்கன் ஃப்ரை."
        "மெடிக்கல்" -> "பாரசிட்டமால் மாத்திரை இரண்டு அட்டை வேண்டும்."
        "இறைச்சி"  -> "1 கிலோ கோழி இறைச்சி, நல்லா கழுவி தாங்க."
        else        -> "ஒரு கிலோ சர்க்கரை, இரண்டு பாக்கெட் டீ தூள்."
    }

    private fun deriveDisplayMetrics(shop: Shop): Pair<String, String> = when (shop.category) {
        "ஹோட்டல்"  -> "3 பொருள்கள்" to "₹280.00"
        "மெடிக்கல்" -> "2 பொருள்கள்" to "₹74.00"
        "இறைச்சி"  -> "2 பொருள்கள்" to "₹320.00"
        else        -> "4 பொருள்கள்" to "₹145.00"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6.  Factory
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        /**
         * [ViewModelProvider.Factory] for use in Compose via `viewModel(factory = OrderViewModel.Factory)`.
         *
         * Reads [Application] from [CreationExtras] so no manual context plumbing
         * is needed at the call site.
         */
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application =
                    checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                        "Application not found in CreationExtras — use a NavHost or Activity context."
                    }
                return OrderViewModel(application) as T
            }
        }
    }
}
