package com.example.dispatch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import com.example.speech.ParsedOrderItem


// ══════════════════════════════════════════════════════════════════════════════
// WhatsAppDispatcher.kt — Voice order routing layer for நம்ம ஊரு ஆப்
//
// Responsibility:
//   Constructs a structured, Tamil-language order message from a voice
//   transcript and user profile, then fires an Android deep-link Intent
//   that opens WhatsApp directly on the merchant's chat thread.
//
// Architecture note:
//   This is a pure stateless helper — no ViewModel, no coroutine scope.
//   It belongs in the dispatch layer, called from OrderScreen after the user
//   confirms their voice transcript.
//
// Deep-link mechanism:
//   WhatsApp supports the unofficial wa.me link scheme AND the
//   "com.whatsapp" package ACTION_SEND intent.  We use the wa.me HTTPS URL
//   (https://api.whatsapp.com/send?phone=...&text=...) because:
//     • It works even when WhatsApp is not the default handler.
//     • The browser falls back gracefully (WhatsApp Web) if the app isn't installed.
//     • It doesn't require querying installed packages (no <queries> manifest entry).
// ══════════════════════════════════════════════════════════════════════════════

private const val TAG = "WhatsAppDispatcher"

// WhatsApp deep-link base URL — works for both WhatsApp and WhatsApp Business
private const val WHATSAPP_API_BASE = "https://api.whatsapp.com/send"

// ─────────────────────────────────────────────────────────────────────────────
// 1.  Data models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Delivery preference selected by the customer on the Order screen.
 *
 * Both options carry Tamil display strings used verbatim inside the
 * WhatsApp message so the merchant immediately understands what is expected.
 */
enum class DeliveryMode(
    /** The Tamil label injected into the order message. */
    val labelTamil: String
) {
    /** Order should be delivered to the customer's home address. */
    HOME_DELIVERY("வீட்டு விநியோகம் (Home Delivery)"),

    /** Customer will collect the order from the shop in person. */
    SELF_PICKUP("நேரில் வாங்கிக்கொள்ளல் (Self Pickup)")
}

/**
 * Encapsulates all customer-supplied metadata required to construct
 * the WhatsApp order dispatch message.
 *
 * @property customerName  Customer's name as entered in the Order screen form.
 *                         Defaults to the pre-filled placeholder "அன்புராஜ்".
 * @property customerPhone Customer's WhatsApp-enabled mobile number.
 *                         Does NOT need to be in E.164 format — raw local
 *                         format (e.g. "9441234567") is fine for display.
 * @property deliveryMode  Whether the order is home delivery or self pickup.
 * @property audioFileNote Human-readable label indicating whether a voice note
 *                         was recorded alongside the transcript.  Shown as a
 *                         static string in the message body since WhatsApp
 *                         Business API (file attachment) is Phase 2.
 */
data class CustomerOrder(
    val customerName: String,
    val customerPhone: String,
    val deliveryMode: DeliveryMode,
    val audioFileNote: String = "ஆடியோ கோப்பு இணைக்கப்பட்டுள்ளது",
    val itemsList: List<ParsedOrderItem>? = null,
    val totalPrice: Double? = null
)

/**
 * Result of a [WhatsAppDispatcher.dispatchVoiceOrder] call.
 *
 * Using a sealed class instead of throwing exceptions gives the caller
 * explicit control over success/failure UI without a try-catch at the call site.
 */
sealed class DispatchResult {

    /** The Intent was successfully fired; WhatsApp (or browser) opened. */
    data object Success : DispatchResult()

    /**
     * The Intent could not be handled — WhatsApp is not installed and the
     * device has no browser capable of opening the wa.me URL.
     *
     * @property fallbackMessage A Tamil-language user-facing error message.
     */
    data class WhatsAppNotInstalled(val fallbackMessage: String) : DispatchResult()

    /**
     * The merchant phone number failed E.164 validation.
     *
     * @property invalidNumber The raw string that failed validation.
     */
    data class InvalidMerchantNumber(val invalidNumber: String) : DispatchResult()

    /**
     * An unexpected exception was thrown while building or firing the Intent.
     *
     * @property cause The underlying exception.
     */
    data class UnexpectedError(val cause: Throwable) : DispatchResult()
}

// ─────────────────────────────────────────────────────────────────────────────
// 2.  Dispatcher
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless helper that constructs a structured Tamil order message and
 * dispatches it to a merchant via WhatsApp deep-link.
 *
 * ### Message template (exact product spec)
 * ```
 * புதிய வாய்ஸ் ஆர்டர் வந்துள்ளது!
 * (நம்ம ஊரு ஆப்)
 *
 * பேசிய விபரம் (Text): "[transcript]"
 * குரல் பதிவு (Voice Note): [ஆடியோ கோப்பு இணைக்கப்பட்டுள்ளது]
 *
 * வாடிக்கையாளர் பெயர்: [name]
 * போன்: [phone]
 * டெலிவரி முறை: [mode]
 * ```
 *
 * ### Deep-link mechanism
 * ```
 * https://api.whatsapp.com/send?phone={e164Number}&text={urlEncoded(message)}
 * ```
 * Android resolves this URI to WhatsApp if installed, or the default browser
 * (→ WhatsApp Web) as a graceful fallback.
 *
 * ### Usage
 * ```kotlin
 * val result = WhatsAppDispatcher.dispatchVoiceOrder(
 *     context       = context,
 *     merchantPhone = shop.whatsAppNumber,      // "+919876543210"
 *     transcript    = transcriptionText,         // Tamil voice transcript
 *     order         = CustomerOrder(
 *         customerName  = customerName,
 *         customerPhone = customerPhone,
 *         deliveryMode  = if (isHomeDelivery) DeliveryMode.HOME_DELIVERY
 *                         else DeliveryMode.SELF_PICKUP
 *     )
 * )
 * when (result) {
 *     DispatchResult.Success              -> navController.navigate(Success.route)
 *     is DispatchResult.WhatsAppNotInstalled -> Toast.makeText(ctx, result.fallbackMessage, LENGTH_LONG).show()
 *     is DispatchResult.InvalidMerchantNumber -> showErrorBanner("தவறான தொலைபேசி எண்")
 *     is DispatchResult.UnexpectedError      -> Timber.e(result.cause)
 * }
 * ```
 */
object WhatsAppDispatcher {

    /**
     * Constructs the Tamil order message and fires a WhatsApp deep-link Intent.
     *
     * @param context       Activity or Application context for [startActivity].
     * @param merchantPhone Merchant's WhatsApp number in E.164 format
     *                      (e.g. "+919876543210").  Must include country code.
     * @param transcript    The STT transcript string from [TamilSpeechRecognizer].
     *                      May contain Unicode Tamil characters — [Uri.encode]
     *                      handles all necessary percent-encoding.
     * @param order         [CustomerOrder] containing name, phone, and delivery mode.
     * @return              A [DispatchResult] indicating success or the specific failure mode.
     */
    fun dispatchVoiceOrder(
        context: Context,
        merchantPhone: String,
        transcript: String,
        order: CustomerOrder
    ): DispatchResult {
        return try {
            // ── Step 1: Validate merchant number ─────────────────────────────
            val sanitisedPhone = sanitiseMerchantPhone(merchantPhone)
            if (sanitisedPhone == null) {
                Log.w(TAG, "Invalid merchant phone: $merchantPhone")
                return DispatchResult.InvalidMerchantNumber(merchantPhone)
            }

            // ── Step 2: Build message body from template ──────────────────────
            val messageBody = buildOrderMessage(transcript = transcript, order = order)
            Log.d(TAG, "Dispatching order message:\n$messageBody")

            // ── Step 3: Construct deep-link URI ───────────────────────────────
            // Uri.encode() handles Tamil Unicode, spaces, quotes, and special chars
            val deepLinkUri = Uri.parse(
                "$WHATSAPP_API_BASE?phone=$sanitisedPhone&text=${Uri.encode(messageBody)}"
            )

            // ── Step 4: Build and fire the Intent ─────────────────────────────
            val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                // FLAG_ACTIVITY_NEW_TASK required when starting from a non-Activity context
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Log.i(TAG, "WhatsApp intent fired successfully → $sanitisedPhone")
            DispatchResult.Success

        } catch (e: ActivityNotFoundException) {
            // WhatsApp not installed AND no browser available — extremely rare
            Log.w(TAG, "No activity found to handle WhatsApp deep-link: ${e.message}")
            DispatchResult.WhatsAppNotInstalled(
                fallbackMessage = "வாட்ஸ்அப் உங்கள் போனில் இல்லை!\n" +
                    "Play Store-ல் WhatsApp இன்ஸ்டால் செய்து மீண்டும் முயற்சிக்கவும்."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error dispatching WhatsApp order: ${e.message}", e)
            DispatchResult.UnexpectedError(cause = e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3.  Message template builder (exact product spec layout)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructs the order message body using the exact Tamil layout specified
     * in the product requirements.
     *
     * WhatsApp renders `*bold*` and `_italic_` markdown in message bubbles.
     * The header and section labels use `*bold*` to make the message easy to
     * scan at a glance — critical for merchant shopkeepers on small screens.
     *
     * @param transcript Voice order transcript from the STT engine.
     * @param order      Customer metadata.
     * @return           The fully formatted, UTF-8 message string.
     */
    internal fun buildOrderMessage(
        transcript: String,
        order: CustomerOrder
    ): String = buildString {

        // ── Header ────────────────────────────────────────────────────────────
        appendLine("🛒 *புதிய வாய்ஸ் ஆர்டர் வந்துள்ளது!*")
        appendLine("_(நம்ம ஊரு ஆப்)_")
        appendLine()

        // ── Order content ─────────────────────────────────────────────────────
        appendLine("*பேசிய விபரம் (Text):* \"${transcript.trim()}\"")
        appendLine("*குரல் பதிவு (Voice Note):* [${order.audioFileNote}]")
        appendLine()

        // ── Structured Items ──────────────────────────────────────────────────
        if (!order.itemsList.isNullOrEmpty()) {
            appendLine("*ஆர்டர் விவரங்கள் (Items):*")
            order.itemsList.forEach { item ->
                appendLine("- ${item.name} x${item.quantity} (₹${"%.2f".format(item.totalItemPrice)})")
            }
            order.totalPrice?.let {
                appendLine("*மொத்த தொகை (Total):* ₹${"%.2f".format(it)}")
            }
            appendLine()
        }

        // ── Customer details ──────────────────────────────────────────────────
        appendLine("*வாடிக்கையாளர் பெயர்:* ${order.customerName}")
        appendLine("*போன்:* ${order.customerPhone}")
        appendLine("*டெலிவரி முறை:* ${order.deliveryMode.labelTamil}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.  Phone number sanitisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Strips whitespace and formatting characters from [rawPhone] and verifies
     * it is a valid E.164 phone number for use in the wa.me deep-link.
     *
     * ### Accepted formats
     * | Input               | Output          |
     * |---------------------|-----------------|
     * | +91 98765 43210     | 919876543210    |
     * | +919876543210       | 919876543210    |
     * | 91-9876543210       | 919876543210    |
     *
     * The `+` prefix is stripped because the wa.me URL format expects a plain
     * numeric string (no `+`): `?phone=919876543210`.
     *
     * @return The sanitised numeric string, or `null` if [rawPhone] is invalid.
     */
    internal fun sanitiseMerchantPhone(rawPhone: String): String? {
        // Strip all non-digit characters except leading '+'
        val stripped = rawPhone.replace(Regex("[^+0-9]"), "")

        // Remove the leading '+' for the URL parameter
        val numeric = stripped.removePrefix("+")

        // Validate: must be 7–15 digits (ITU-T E.164 range)
        if (numeric.length !in 7..15) {
            Log.w(TAG, "Phone '$rawPhone' failed length validation: ${numeric.length} digits.")
            return null
        }

        // Additional check using Android's built-in pattern
        if (!Patterns.PHONE.matcher(stripped).matches() && numeric.length < 10) {
            Log.w(TAG, "Phone '$rawPhone' failed Patterns.PHONE validation.")
            return null
        }

        return numeric
    }
}
