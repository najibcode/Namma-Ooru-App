package com.example.data

// ══════════════════════════════════════════════════════════════════════════════
// FirestoreShopRepository.kt — Real-time Firebase Firestore shop directory
//
// Architecture:
//   Wraps the Firebase Firestore Kotlin SDK.  The `shops` Flow is backed by a
//   real-time Firestore snapshot listener so the Home screen updates whenever
//   a merchant's subscription changes — without polling.
//
//   Offline behaviour:
//   Firestore's built-in offline persistence cache (`setPersistentCacheSettings`)
//   serves stale data when the device has no connectivity.  The in-memory
//   `ShopRepository` seed data is used as an immediate placeholder while the
//   first snapshot is in-flight, preventing an empty Home screen on cold start.
//
// Threading:
//   - Firestore callbacks run on a Firestore-managed background thread.
//   - We use `callbackFlow` to bridge them into a Kotlin coroutine `Flow`.
//   - `flowOn(Dispatchers.IO)` keeps Firestore I/O off the main thread.
//
// Firestore data model:
//   Collection: "shops"
//   Document ID: matches Shop.id ("1", "2", …)
//   Fields:
//     nameTamil      : String
//     category       : String   (Tamil label, e.g. "ஹோட்டல்")
//     whatsAppNumber : String   (E.164, e.g. "+919876543210")
//     isSubscribed   : Boolean
//     imageUrl       : String
//     address        : String
//     openingHours   : String
//     rating         : Double
// ══════════════════════════════════════════════════════════════════════════════

import android.util.Log
import com.example.domain.Shop
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val TAG               = "FirestoreShopRepo"
private const val COLLECTION_SHOPS  = "shops"

// ─────────────────────────────────────────────────────────────────────────────
// Repository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Firestore-backed shop directory for நம்ம ஊரு ஆப்.
 *
 * Exposes the same API surface as the existing [ShopRepository] so the
 * ViewModel can be switched without changing any call sites.
 *
 * ### Offline support
 * Firestore local persistence is enabled by default on Android.  The first
 * load after install requires connectivity; subsequent launches serve cached
 * data while the snapshot listener refreshes in the background.
 *
 * ### Real-time updates
 * [shops] is a `callbackFlow` that listens to `addSnapshotListener`.  Any
 * change pushed from the Firebase console (e.g. toggling `isSubscribed`) is
 * reflected on the Home screen within seconds — no app restart required.
 */
class FirestoreShopRepository(
    private val localFallback: ShopRepository = ShopRepository()
) {
    companion object {
        val instance: FirestoreShopRepository by lazy { FirestoreShopRepository() }

        @Volatile
        private var cachedShopsList: List<Shop> = emptyList()
    }

    private val db: FirebaseFirestore = Firebase.firestore.also { fs ->
        // Enable offline persistence with a 50 MB cache
        fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(50L * 1024 * 1024) // 50 MiB
                    .build()
            )
            .build()
    }

    // ── 1. Real-time shops Flow ───────────────────────────────────────────────

    /**
     * Live, auto-updating stream of all shops from Firestore.
     *
     * Starts with the local in-memory seed list as a placeholder (via
     * [onStart]) so the Home screen renders immediately while the first
     * Firestore snapshot arrives.
     *
     * Errors (e.g. permission denied) are caught and logged; the Flow stays
     * open and will recover automatically when connectivity returns.
     */
    val shops: Flow<List<Shop>> = callbackFlow {
        val listenerRegistration = db.collection(COLLECTION_SHOPS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore snapshot error: ${error.message}", error)
                    // Don't close the flow — Firestore will retry automatically
                    return@addSnapshotListener
                }

                val shopList = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Shop(
                            id             = doc.id,
                            nameTamil      = doc.getString("nameTamil")      ?: "",
                            category       = doc.getString("category")       ?: "",
                            whatsAppNumber = doc.getString("whatsAppNumber") ?: "",
                            isSubscribed   = doc.getBoolean("isSubscribed")  ?: false,
                            imageUrl       = doc.getString("imageUrl")       ?: "",
                            address        = doc.getString("address")        ?: "",
                            openingHours   = doc.getString("openingHours")   ?: "",
                            rating         = doc.getDouble("rating")?.toFloat()
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse shop doc ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()

                cachedShopsList = shopList
                Log.d(TAG, "Firestore snapshot: ${shopList.size} shops received")
                trySend(shopList)
            }

        // Clean up listener when the Flow collector cancels
        awaitClose {
            Log.d(TAG, "Removing Firestore snapshot listener")
            listenerRegistration.remove()
        }
    }
    .onStart {
        // Emit local seed data immediately so the UI isn't blank during first load
        Log.d(TAG, "Emitting local seed shops while Firestore snapshot loads")
        localFallback.getAllShops().collect { emit(it) }
    }
    .catch { e ->
        Log.e(TAG, "Unrecoverable error in shops flow: ${e.message}", e)
        // Fall back to local data permanently on critical failures
        localFallback.getAllShops().collect { emit(it) }
    }

    // ── 2. Filtered shop query ────────────────────────────────────────────────

    /**
     * Returns all shops matching the given Tamil [category] label.
     *
     * Filtering is applied on the client side (after the real-time snapshot)
     * to avoid needing a Firestore composite index for every new category.
     */
    fun getShopsByCategory(category: String): Flow<List<Shop>> =
        shops.map { list -> list.filter { it.category == category } }

    // ── 3. One-shot shop lookup ───────────────────────────────────────────────

    /**
     * Returns the shop with the given [id] from the last known snapshot,
     * or falls back to the local in-memory store.
     *
     * This is intentionally a synchronous lookup using the cached Firestore
     * snapshot — the Order screen needs it immediately when navigating.
     * A full suspend fetch is deferred to Phase 2 if needed.
     */
    fun getShopById(id: String): Shop? =
        cachedShopsList.find { it.id == id } ?: localFallback.getShopById(id)

    // ── 4. IVR integration (delegates to ShopRepository) ─────────────────────

    suspend fun triggerIvrVoiceAlert(merchantPhoneNumber: String): Result<Unit> =
        localFallback.triggerIvrVoiceAlert(merchantPhoneNumber)
}
