package com.example.data

// ══════════════════════════════════════════════════════════════════════════════
// OrderHistoryRepository.kt — Live order log for நம்ம ஊரு ஆப்
//
// Architecture:
//   Kotlin object singleton (process-lifetime scope) backed by a
//   MutableStateFlow<List<OrderRecord>>.  Every screen that collects
//   ordersFlow sees the updated list the moment a new order is appended —
//   no manual refresh, no polling.
//
//   In Phase 2 this will be replaced / supplemented by a Room DAO so orders
//   survive process death.  The public API surface is intentionally identical
//   to what a Room-backed repository would expose, making the migration trivial.
//
// Threading:
//   appendOrder() is called from the Main coroutine inside OrderViewModel.
//   All StateFlow reads are thread-safe without additional synchronisation.
// ══════════════════════════════════════════════════════════════════════════════

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// 1.  Domain model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of a single completed voice order.
 *
 * @property id           Auto-generated UUID; stable across the process lifetime.
 * @property shopId       The [Shop.id] of the merchant the order was placed with.
 * @property shopName     Tamil display name of the shop (denormalised for fast display).
 * @property category     Tamil category label (e.g. "ஹோட்டல்").
 * @property transcript   The raw STT text captured during the voice session.
 * @property customerName Customer name as entered in the order form.
 * @property deliveryMode "வீட்டு விநியோகம்" or "நேரில் வாங்கிக்கொள்ளல்".
 * @property displayPrice Formatted price string shown on the Success and Orders screens.
 * @property displayCount Formatted item count string (e.g. "3 பொருள்கள்").
 * @property timestamp    Human-readable Tamil timestamp (e.g. "இன்று, 02:40 PM").
 * @property isDispatched True when WhatsApp dispatch succeeded; false when it fell
 *                        through to the browser fallback (still recorded for history).
 */
data class OrderRecord(
    val id: String           = UUID.randomUUID().toString(),
    val shopId: String,
    val shopName: String,
    val category: String,
    val transcript: String,
    val customerName: String,
    val deliveryMode: String,
    val displayPrice: String = "₹0.00",
    val displayCount: String = "0 பொருள்கள்",
    val timestamp: String    = formattedNow(),
    val isDispatched: Boolean = true
)

/** Formats the current time as a Tamil-friendly string, e.g. "இன்று, 02:40 PM". */
fun formattedNow(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
    return "இன்று, ${sdf.format(Date())}"
}

// ─────────────────────────────────────────────────────────────────────────────
// 2.  Repository singleton
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Process-scoped singleton order history log.
 *
 * Accessed via `OrderHistoryRepository` (Kotlin `object`) — no DI framework
 * required.  For multi-module or test environments this can be wrapped in an
 * interface and injected as a fake.
 *
 * ### Usage from ViewModel
 * ```kotlin
 * // Append a completed order:
 * OrderHistoryRepository.appendOrder(record)
 *
 * // Observe live in the Orders screen:
 * OrderHistoryRepository.ordersFlow.collectAsStateWithLifecycle()
 * ```
 */
object OrderHistoryRepository {

    private val _orders = MutableStateFlow<List<OrderRecord>>(
        // Seed with realistic sample data so the Orders tab is not blank on first install
        buildSeedOrders()
    )

    /**
     * Live, always-up-to-date list of all placed voice orders, newest first.
     *
     * Backed by [MutableStateFlow] — any Compose collector is automatically
     * recomposed when a new order is appended.
     */
    val ordersFlow: Flow<List<OrderRecord>> = _orders.asStateFlow()

    /**
     * Prepends [record] to the order log so the most recent order appears first.
     *
     * Safe to call from the Main thread (StateFlow.update is thread-safe).
     */
    fun appendOrder(record: OrderRecord) {
        _orders.update { existing -> listOf(record) + existing }
    }

    /**
     * Returns the current snapshot of all orders (non-reactive; for one-shot reads).
     */
    fun currentOrders(): List<OrderRecord> = _orders.value

    // ─────────────────────────────────────────────────────────────────────────
    // Seed data — shown on first launch so the Orders tab looks populated
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSeedOrders(): List<OrderRecord> = listOf(
        OrderRecord(
            shopId       = "1",
            shopName     = "சக்தி உணவகம்",
            category     = "ஹோட்டல்",
            transcript   = "4 பரோட்டா, 1 குருமா, ஒரு சிக்கன் ஃப்ரை",
            customerName = "அன்புராஜ்",
            deliveryMode = "வீட்டு விநியோகம் (Home Delivery)",
            displayPrice = "₹280.00",
            displayCount = "3 பொருள்கள்",
            timestamp    = "இன்று, 02:40 PM"
        ),
        OrderRecord(
            shopId       = "3",
            shopName     = "சரவணா ஸ்டோர்ஸ்",
            category     = "மளிகை",
            transcript   = "பொன்னி அரிசி (5KG) - 1 மூட்டை, நல்லெண்ணெய் (1L) - 2 பாட்டில்",
            customerName = "அன்புராஜ்",
            deliveryMode = "நேரில் வாங்கிக்கொள்ளல் (Self Pickup)",
            displayPrice = "₹145.00",
            displayCount = "4 பொருள்கள்",
            timestamp    = "நேற்று, 11:15 AM"
        ),
        OrderRecord(
            shopId       = "2",
            shopName     = "அப்பல்லோ பார்மசி",
            category     = "மெடிக்கல்",
            transcript   = "பாரசிட்டமால் மாத்திரை இரண்டு அட்டை வேண்டும்",
            customerName = "அன்புராஜ்",
            deliveryMode = "வீட்டு விநியோகம் (Home Delivery)",
            displayPrice = "₹74.00",
            displayCount = "2 பொருள்கள்",
            timestamp    = "30 May 2026"
        )
    )
}
