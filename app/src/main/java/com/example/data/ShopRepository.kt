package com.example.data

import com.example.domain.ServiceWorker
import com.example.domain.Shop
import com.example.domain.ShopCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════════════════════════
// ShopRepository.kt — Data Layer for நம்ம ஊரு ஆப்
//
// Responsibilities:
//   • Manage the in-memory (and eventually Room / remote) directory of shops.
//   • Expose type-safe, lifecycle-aware Flows to ViewModels and composables.
//   • Bridge outbound telephony (IVR) for voice-order merchant alerts.
//
// Architecture note:
//   This class follows the Repository pattern from Google's recommended Android
//   architecture.  It is the single source of truth for shop data: the UI layer
//   never touches raw data sources directly.
//
// Threading contract:
//   • All public Flow emissions are safe to collect on any dispatcher.
//   • Suspend functions use `withContext(Dispatchers.IO)` to avoid blocking the
//     main thread even when the underlying call is synchronous (future-proofing
//     for real network I/O).
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Single source of truth for shop and service-worker data in நம்ம ஊரு ஆப்.
 *
 * ### Data sources (current — Phase 1)
 * The repository is backed by an in-memory [MutableStateFlow] populated with
 * hard-coded sample data representing real shops from **Pottalpudur** and the
 * surrounding localities.  This lets the team prototype and demo the full order
 * flow without requiring a live backend.
 *
 * ### Data sources (planned — Phase 2)
 * The in-memory store will be replaced (or supplemented) by:
 *  1. **Room database** — for offline-first caching and order history.
 *  2. **Retrofit + Moshi REST API** — for syncing the merchant directory from
 *     the நம்ம ஊரு ஆப் backend (Cloud Run / Firebase Firestore).
 *  3. **Firebase Firestore real-time listener** — for live subscription-status
 *     updates pushed from the merchant partner dashboard.
 *
 * ### Thread safety
 * [MutableStateFlow] is thread-safe by design.  All reads are via the read-only
 * [asStateFlow] view; writes (when added) will be serialised through a
 * single-threaded `Dispatchers.IO` scope.
 */
class ShopRepository {

    // ─────────────────────────────────────────────────────────────────────────
    // 1.  In-memory shop directory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thread-safe in-memory store of all registered shops.
     *
     * [MutableStateFlow] is chosen over a plain `List` so that any future
     * write (e.g. a merchant going live) automatically propagates to all active
     * collectors without extra work.
     */
    private val _shops = MutableStateFlow(buildSampleShops())

    /** Public, read-only view exposed to the rest of the app. */
    val shops: Flow<List<Shop>> = _shops.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // 2.  In-memory service-worker directory (Phase 2)
    // ─────────────────────────────────────────────────────────────────────────

    private val _serviceWorkers = MutableStateFlow(buildSampleServiceWorkers())

    val serviceWorkers: Flow<List<ServiceWorker>> = _serviceWorkers.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // 3.  Query functions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all shops whose [Shop.category] matches [category].
     *
     * The returned [Flow] is backed by the same [MutableStateFlow] as [shops],
     * so any future update to the directory is automatically reflected here.
     * Filtering is performed inline using [Flow.map], which is lazy and
     * non-blocking.
     *
     * ### Thread safety
     * Safe to collect on any coroutine dispatcher.  The `map` operator runs on
     * whichever dispatcher the collector uses.
     *
     * @param category The Tamil category label, e.g. [ShopCategory.Hotel.labelTamil].
     *                 Use [ShopCategory.all] to iterate over all valid values.
     * @return A [Flow] that emits a (possibly empty) list every time the
     *         underlying shop directory changes.
     */
    fun getShopsByCategory(category: String): Flow<List<Shop>> =
        _shops.map { list -> list.filter { it.category == category } }

    /**
     * Convenience overload that accepts a typed [ShopCategory] instead of a
     * raw string, eliminating any risk of typos at call sites.
     *
     * ```kotlin
     * viewModelScope.launch {
     *     repository.getShopsByCategory(ShopCategory.Hotel).collect { hotels ->
     *         _uiState.update { it.copy(hotels = hotels) }
     *     }
     * }
     * ```
     */
    fun getShopsByCategory(category: ShopCategory): Flow<List<Shop>> =
        getShopsByCategory(category.labelTamil)

    /**
     * Returns all shops in the directory, regardless of category.
     *
     * Equivalent to collecting [shops] directly, but provided for a consistent
     * API surface.
     */
    fun getShops(): Flow<List<Shop>> = shops

    /**
     * Looks up a single shop by its [Shop.id].
     *
     * This is intentionally a non-suspending function returning a nullable value
     * rather than a Flow, because the Order screen needs an immediate, one-shot
     * lookup by ID (passed as a navigation argument) — not a live subscription.
     *
     * @param id The shop's unique identifier.
     * @return The matching [Shop], or `null` if no shop with that ID exists.
     */
    fun getShopById(id: String): Shop? =
        _shops.value.find { it.id == id }

    /**
     * Returns all available service workers.
     *
     * Phase 2: this Flow will be backed by a Room DAO and a Firestore listener
     * for real-time availability updates from the partner app.
     */
    fun getServiceWorkers(): Flow<List<ServiceWorker>> = serviceWorkers

    // ─────────────────────────────────────────────────────────────────────────
    // 4.  IVR telephony integration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Triggers an automated, Tamil-language IVR (Interactive Voice Response)
     * phone call to the merchant to notify them of a new voice order placed via
     * நம்ம ஊரு ஆப்.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * ## Background & motivation
     *
     * Many shopkeepers in rural Tamil Nadu check their phone calls more
     * reliably than WhatsApp messages.  A secondary IVR call ensures the
     * merchant is alerted even if they miss (or mute) the WhatsApp notification
     * dispatched by [OrderScreen].
     *
     * ─────────────────────────────────────────────────────────────────────────
     * ## Planned implementation — Exotel (recommended for India)
     *
     * Exotel is an Indian cloud-telephony platform widely used for OTP and IVR
     * flows.  The integration flow will be:
     *
     * ```
     * App  ──POST──▶  நம்ம ஊரு Backend (Cloud Run)
     *                      │
     *                      └──POST──▶  Exotel REST API
     *                                       │
     *                                       └──CALL──▶  Merchant's phone
     * ```
     *
     * ### Step-by-step HTTP flow
     *
     * 1. **Authenticate**: Exotel uses HTTP Basic Auth.
     *    - Username: `EXOTEL_API_KEY`   (from BuildConfig / secrets)
     *    - Password: `EXOTEL_API_TOKEN` (from BuildConfig / secrets)
     *    - Both obtained from the Exotel dashboard after registering the
     *      நம்ம ஊரு ஆப் virtual number (VN).
     *
     * 2. **POST to the Calls endpoint**:
     *    ```
     *    POST https://api.exotel.com/v1/Accounts/{EXOTEL_SID}/Calls.json
     *    Content-Type: application/x-www-form-urlencoded
     *    Authorization: Basic <base64(API_KEY:API_TOKEN)>
     *
     *    From=0XXXXXXXXXX           ← Exotel Virtual Number
     *    To={merchantPhoneNumber}   ← Merchant's mobile
     *    CallerId=0XXXXXXXXXX       ← Same as From
     *    Url=https://cdn.nammaooruapp.in/ivr/new-order-tamil.xml
     *    TimeLimit=60               ← Max call duration in seconds
     *    ```
     *
     * 3. **TwiML/ExoML flow** (hosted XML): The `Url` parameter points to a
     *    simple ExoML document that plays a pre-recorded Tamil audio clip:
     *    ```xml
     *    <Response>
     *      <Play>https://cdn.nammaooruapp.in/audio/new-order-ta.mp3</Play>
     *    </Response>
     *    ```
     *    Audio content (translated):
     *    > "வணக்கம்! நம்ம ஊரு ஆப் மூலமாக உங்கள் கடைக்கு ஒரு புதிய
     *    >  வாய்ஸ் ஆர்டர் வந்துள்ளது. தயவுசெய்து உங்கள் வாட்ஸ்அப்பை
     *    >  சரிபார்க்கவும். நன்றி!"
     *
     * ### Alternative: Twilio (for international expansion)
     * Twilio supports the same flow with minor endpoint differences:
     * ```
     * POST https://api.twilio.com/2010-04-01/Accounts/{ACCOUNT_SID}/Calls.json
     * ```
     * The TwiML body is identical; only the auth headers and base URL differ.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * ## Security considerations
     *
     * - API keys **must never** be hardcoded in the APK.  Store them as
     *   `BuildConfig` fields injected by the Secrets Gradle Plugin from `.env`.
     * - The actual HTTP call should be made from the server side (Cloud Run)
     *   to avoid exposing the Exotel SID / token in the client binary.  The
     *   app POSTs to the நம்ம ஊரு backend, which then calls Exotel.
     * - Apply certificate pinning when calling the backend to prevent MITM.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * ## Error handling strategy
     *
     * | Scenario                  | Action                                      |
     * |---------------------------|---------------------------------------------|
     * | Network unavailable       | Return `Result.failure(IOException(...))`   |
     * | Exotel 4xx (bad params)   | Log + return `Result.failure(...)`          |
     * | Exotel 5xx (server error) | Retry up to 3× with exponential back-off   |
     * | Call not answered         | Exotel retries automatically (configured)   |
     *
     * The UI layer treats IVR failure as non-critical: the WhatsApp message is
     * the primary notification channel; IVR is supplemental.
     *
     * ─────────────────────────────────────────────────────────────────────────
     *
     * @param merchantPhoneNumber The merchant's mobile number in E.164 format
     *        (e.g. "+919876543210").  Sourced from [Shop.whatsAppNumber].
     * @return [Result.success] on a successful API acknowledgment, or
     *         [Result.failure] wrapping the underlying exception on any error.
     */
    suspend fun triggerIvrVoiceAlert(merchantPhoneNumber: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // ── PLACEHOLDER ───────────────────────────────────────────────
                // Replace the log statement below with the actual Retrofit /
                // OkHttp call to the நம்ம ஊரு backend once the Cloud Run
                // service is deployed.
                //
                // Example (using OkHttp directly):
                //
                //   val client = OkHttpClient.Builder()
                //       .certificatePinner(CertificatePinner.Builder()
                //           .add("api.nammaooruapp.in", "sha256/AAAA...")
                //           .build())
                //       .build()
                //
                //   val body = FormBody.Builder()
                //       .add("merchant_phone", merchantPhoneNumber)
                //       .add("order_locale", "ta-IN")
                //       .build()
                //
                //   val request = Request.Builder()
                //       .url("https://api.nammaooruapp.in/v1/ivr/trigger")
                //       .post(body)
                //       .addHeader("X-Api-Key", BuildConfig.NAMMA_OORU_API_KEY)
                //       .build()
                //
                //   client.newCall(request).execute().use { response ->
                //       if (!response.isSuccessful)
                //           throw IOException("IVR trigger failed: ${response.code}")
                //   }
                // ─────────────────────────────────────────────────────────────
                println("[நம்ம ஊரு] IVR placeholder — would call: $merchantPhoneNumber")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5.  Sample data — 4 real-category shops from Pottalpudur
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the initial set of sample shops pre-populated with real-world
     * representative data from the **Pottalpudur** area (Tirunelveli district,
     * Tamil Nadu).
     *
     * Each shop covers one of the four primary categories:
     *  - [ShopCategory.Hotel]   → ஹோட்டல் (food / eateries)
     *  - [ShopCategory.Medical] → மெடிக்கல் (pharmacy / medical)
     *  - [ShopCategory.Grocery] → மளிகை (provisions / grocery)
     *  - [ShopCategory.Meat]    → இறைச்சி (meat / poultry / fish)
     *
     * Image URLs use the Google AIDA public CDN for the hotel card; other
     * shops use empty strings, which trigger category-placeholder rendering
     * in the UI.
     */
    private fun buildSampleShops(): List<Shop> = listOf(

        // ── 1. Hotel ──────────────────────────────────────────────────────────
        Shop(
            id             = "1",
            nameTamil      = "சக்தி உணவகம்",
            category       = ShopCategory.Hotel.labelTamil,
            whatsAppNumber = "+919876543210",
            isSubscribed   = true,
            imageUrl       = "https://lh3.googleusercontent.com/aida-public/" +
                "AB6AXuC4UsQL2OPJ0msDFRqS-Ys_1i-NdorJZ-rgN9c2Kur0eAQCWLtBcvRd" +
                "zby2Yimd586VCJDBw9EXvoTZhpIZtxJB5msNIXzdW2igD52Aw3SL1sd9D66OBiH" +
                "IVGrYfjbsUFI86p49A1wCuUO4w9ILIfe0HaSKxwvMNhDv9918wUe5n7K6Ur4CC" +
                "ZurjKEtqkeH9vj4MkUitTheNd2JPHbK9mt7SqEWXTxbSmRTBP4go5LzacMpunt6" +
                "7Y4yz4FNLi8t3uw7cCaD2KEqkw",
            address        = "பொட்டல்புதூர் பஸ் நிறுத்தம் அருகில், திருநெல்வேலி மாவட்டம்",
            openingHours   = "காலை 6:00 - இரவு 10:00",
            rating         = 4.5f
        ),

        // ── 2. Medical ────────────────────────────────────────────────────────
        Shop(
            id             = "2",
            nameTamil      = "அப்பல்லோ பார்மசி",
            category       = ShopCategory.Medical.labelTamil,
            whatsAppNumber = "+919876543211",
            isSubscribed   = true,
            imageUrl       = "",
            address        = "பொட்டல்புதூர் மெயின் ரோடு, அருகில் பஞ்சாயத்து அலுவலகம்",
            openingHours   = "காலை 8:00 - இரவு 9:00",
            rating         = 4.3f
        ),

        // ── 3. Grocery ────────────────────────────────────────────────────────
        Shop(
            id             = "3",
            nameTamil      = "சரவணா ஸ்டோர்ஸ்",
            category       = ShopCategory.Grocery.labelTamil,
            whatsAppNumber = "+919876543212",
            isSubscribed   = true,
            imageUrl       = "",
            address        = "பொட்டல்புதூர் கிழக்கு தெரு, நாங்குநேரி ரோடு",
            openingHours   = "காலை 7:00 - இரவு 9:30",
            rating         = 4.6f
        ),

        // ── 4. Meat ───────────────────────────────────────────────────────────
        Shop(
            id             = "4",
            nameTamil      = "முருகன் சிக்கன் சென்டர்",
            category       = ShopCategory.Meat.labelTamil,
            whatsAppNumber = "+919876543213",
            isSubscribed   = true,
            imageUrl       = "",
            address        = "பொட்டல்புதூர் பாஜார் தெரு, அங்காடி வளாகம்",
            openingHours   = "காலை 6:00 - மாலை 7:00",
            rating         = 4.4f
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 6.  Sample data — Phase 2 service workers from Pottalpudur area
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSampleServiceWorkers(): List<ServiceWorker> = listOf(

        ServiceWorker(
            id             = "w1",
            name           = "கந்தசாமி",
            roleTamil      = "மின்சார வல்லுனர் (Electrician)",
            rating         = 4.8f,
            pastWorkImages = listOf(
                "https://images.unsplash.com/photo-1621905251189-08b45d6a269e"
            ),
            phoneNumber    = "+919876540001",
            isAvailable    = true,
            serviceArea    = "பொட்டல்புதூர், நாங்குநேரி",
            yearsExp       = 12
        ),

        ServiceWorker(
            id             = "w2",
            name           = "மாரியப்பன்",
            roleTamil      = "குழாய் பழுதுபார்ப்பவர் (Plumber)",
            rating         = 4.5f,
            pastWorkImages = listOf(
                "https://images.unsplash.com/photo-1504307651254-35680f356dfd"
            ),
            phoneNumber    = "+919876540002",
            isAvailable    = true,
            serviceArea    = "பொட்டல்புதூர், திருவேங்கடம்",
            yearsExp       = 8
        ),

        ServiceWorker(
            id             = "w3",
            name           = "செல்வம்",
            roleTamil      = "தச்சு வேலை செய்பவர் (Carpenter)",
            rating         = 4.7f,
            pastWorkImages = listOf(),
            phoneNumber    = "+919876540003",
            isAvailable    = false,
            serviceArea    = "பொட்டல்புதூர்",
            yearsExp       = 15
        )
    )
}
