package com.example.data

import com.example.domain.Shop
import com.example.domain.ServiceWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShopRepository {

    // Mock local data inserting 3 distinct shops for the active categories
    private val mockShops = listOf(
        Shop(
            shopId = "1",
            nameTamil = "சக்தி உணவகம்",
            category = "ஹோட்டல்", // Hotel
            whatsAppNumber = "+919876543210",
            isSubscribed = true,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC4UsQL2OPJ0msDFRqS-Ys_1i-NdorJZ-rgN9c2Kur0eAQCWLtBcvRdzby2Yimd586VCJDBw9EXvoTZhpIZtxJB5msNIXzdW2igD52Aw3SL1sd9D66OBiHIVGrYfjbsUFI86p49A1wCuUO4w9ILIfe0HaSKxwvMNhDv9918wUe5n7K6Ur4CCZurjKEtqkeH9vj4MkUitTheNd2JPHbK9mt7SqEWXTxbSmRTBP4go5LzacMpunt67Y4yz4FNLi8t3uw7cCaD2KEqkw"
        ),
        Shop(
            shopId = "2",
            nameTamil = "அப்பல்லோ பார்மசி",
            category = "மெடிக்கல்", // Medical
            whatsAppNumber = "+919876543211",
            isSubscribed = true,
            imageUrl = "" // Not essentially required, but nice to have
        ),
        Shop(
            shopId = "3",
            nameTamil = "சரவணா ஸ்டோர்ஸ்",
            category = "மளிகை", // Grocery
            whatsAppNumber = "+919876543212",
            isSubscribed = true,
            imageUrl = "" // For Grocery
        )
    )

    // Mock Service Workers for Phase 2 configurations
    private val mockServiceWorkers = listOf(
        ServiceWorker(
            id = "w1",
            name = "கந்தசாமி",
            roleTamil = "மின்சார வல்லுனர் (Electrician)",
            rating = 4.8f,
            pastWorkImages = listOf("https://images.unsplash.com/photo-1621905251189-08b45d6a269e")
        ),
        ServiceWorker(
            id = "w2",
            name = "மாரியப்பன்",
            roleTamil = "குழாய் பழுதுபார்ப்பவர் (Plumber)",
            rating = 4.5f,
            pastWorkImages = listOf("https://images.unsplash.com/photo-1504307651254-35680f356dfd")
        ),
        ServiceWorker(
            id = "w3",
            name = "செல்வம்",
            roleTamil = "தச்சு வேலை செய்பவர் (Carpenter)",
            rating = 4.7f,
            pastWorkImages = listOf("")
        )
    )

    fun getShops(): Flow<List<Shop>> = flowOf(mockShops)

    fun getShopByCategory(category: String): Flow<List<Shop>> {
        return flowOf(mockShops.filter { it.category == category })
    }

    fun getShopById(shopId: String): Shop? {
        return mockShops.find { it.shopId == shopId }
    }

    fun getServiceWorkers(): Flow<List<ServiceWorker>> = flowOf(mockServiceWorkers)

    fun triggerIvrAlert(shopPhoneNumber: String) {
        // Keeps backwards compatibility for non-suspending calls
        // by launching it or running a basic trigger log
    }

    /**
     * Outbound Telephony Trigger Integration Hub
     * This suspend function initiates an automated outbound call (IVR) alerting the merchant/shopkeeper 
     * that a new Namma Ooru App voice order has arrived.
     *
     * SYSTEM IMPLEMENTATION ARCHITECTURE FLOW:
     * 1. This function will issue an asynchronous HTTP POST payload to the cloud telephony infrastructure gateway 
     *    (such as Twilio or Exotel).
     * 2. The HTTP POST request contains the following required headers and parameters:
     *    - Authorization: Basic authentication string (API Key / Token encoded in Base64).
     *    - Exotel parameters inside form urlencoded format:
     *      - From (Sender ID/Virtual Number)
     *      - To: ${merchantPhoneNumber} (Merchant's direct line)
     *      - Url: The XML flow document URL containing custom text-to-speech directions in Tamil 
     *        (e.g., "வணக்கம்! உங்கள் கடைக்கு நம்ம ஊரு ஆப் மூலமாக ஒரு புதிய வாய்ஸ் ஆர்டர் வந்துள்ளது. தயவுசெய்து உங்கள் வாட்ஸ்அப்பை பார்த்து சரிபார்க்கவும்.")
     *
     * @param merchantPhoneNumber The verified WhatsApp/Mobile number of the merchant.
     * @return Result containing either Unit on success, or Throwable on failure.
     */
    suspend fun triggerIvrVoiceAlert(merchantPhoneNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Simulated local diagnostic trace confirming the outbound telephony network flow trigger
            println("Triggering automated IVR alert call via telephony infrastructure for: $merchantPhoneNumber")
            
            /*
            // ACTUAL RETROFIT / HTTP CLIENT API PIPELINE:
            val client = OkHttpClient()
            val requestBody = FormBody.Builder()
                .add("From", "+9180XXXXXX") // Virtual Business Gateway Line
                .add("To", merchantPhoneNumber)
                .add("CallerId", BuildConfig.EXOTEL_CALLER_ID)
                .add("Url", "https://api.nammaooruapp.org/ivr-tamil-flow.xml")
                .build()

            val request = Request.Builder()
                .url("https://api.exotel.com/v1/Accounts/${BuildConfig.EXOTEL_ACCOUNT_SID}/Calls.json")
                .post(requestBody)
                .addHeader("Authorization", Credentials.basic(BuildConfig.EXOTEL_API_KEY, BuildConfig.EXOTEL_API_TOKEN))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Exotel API call failed: ${response.message}")
            }
            */
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

