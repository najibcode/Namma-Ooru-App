package com.example.speech

import android.util.Log
import com.example.domain.Shop
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "VoiceOrderParser"

/** Represents a single parsed item from the voice order. */
data class ParsedOrderItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalItemPrice: Double
)

/** Represents the complete result of parsing a voice transcript. */
data class ParsedOrderResult(
    val items: List<ParsedOrderItem>,
    val totalPrice: Double,
    val totalCount: Int,
    val itemSummaryTamil: String
)

/** Represents a menu item available in a shop. */
data class MenuItem(
    val name: String,
    val price: Double
)

object VoiceOrderParser {

    // ── Predefined menus for Pottalpudur shops ──────────────────────────────
    private val SHOP_MENUS = mapOf(
        "1" to listOf( // शक्ति உணவகம்
            MenuItem("பரோட்டா", 20.0),
            MenuItem("குருமா", 50.0),
            MenuItem("சிக்கன் ஃப்ரை", 140.0),
            MenuItem("இட்லி", 10.0),
            MenuItem("பொங்கல்", 40.0),
            MenuItem("வடை", 10.0),
            MenuItem("தோசை", 30.0),
            MenuItem("மசால் தோசை", 50.0)
        ),
        "2" to listOf( // அப்பல்லோ பார்மசி
            MenuItem("பாரசிட்டமால்", 20.0),
            MenuItem("வலி நிவாரணி", 50.0),
            MenuItem("இருமல் மருந்து", 80.0)
        ),
        "3" to listOf( // சரவணா ஸ்டோர்ஸ்
            MenuItem("பொன்னி அரிசி (5KG)", 350.0),
            MenuItem("நல்லெண்ணெய் (1L)", 220.0),
            MenuItem("பசும்பால் (500ml)", 30.0),
            MenuItem("சர்க்கரை", 40.0),
            MenuItem("டீ தூள்", 60.0)
        ),
        "4" to listOf( // முருகன் சிக்கன் சென்டர்
            MenuItem("கோழி இறைச்சி (1KG)", 240.0),
            MenuItem("மீன் (1KG)", 300.0),
            MenuItem("ஆட்டு கறி (1KG)", 700.0)
        )
    )

    // ── Define JSON schema to enforce structured output from Gemini ─────────
    private val orderSchema = Schema.obj(
        mapOf(
            "items" to Schema.array(
                Schema.obj(
                    mapOf(
                        "name" to Schema.string("The Tamil name of the item. Try to match closely with the shop's menu items."),
                        "quantity" to Schema.integer("The quantity of the item ordered (default to 1 if not specified)."),
                        "unitPrice" to Schema.double("The unit price of the item according to the shop's menu. If not on menu, estimate a reasonable price."),
                        "totalItemPrice" to Schema.double("The calculated total price for this item (quantity * unitPrice).")
                    ),
                    listOf("name", "quantity", "unitPrice", "totalItemPrice")
                )
            ),
            "totalPrice" to Schema.double("The sum of all totalItemPrice values."),
            "totalCount" to Schema.integer("The sum of all quantities of all items."),
            "itemSummaryTamil" to Schema.string("A concise Tamil summary of the order, e.g. '4 பரோட்டா, 1 சிக்கன் ஃப்ரை'")
        ),
        listOf("items", "totalPrice", "totalCount", "itemSummaryTamil")
    )

    private val generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = orderSchema
    }

    // Always use the most recent version of Gemini (gemini-flash-latest or gemini-2.5-flash)
    private val model = Firebase.ai.generativeModel(
        modelName = "gemini-2.5-flash",
        generationConfig = generationConfig,
        systemInstruction = content {
            text("You are a local marketplace ordering assistant in Tamil Nadu, India. " +
                "You translate spoken colloquial Tamil order transcripts into structured JSON orders. " +
                "Read the shop category and menu list provided in the prompt. " +
                "Match the items mentioned in the transcript to the menu list and get their prices. " +
                "If the spoken quantity is in Tamil words (e.g., 'நாலு' for 4, 'ஒரு' for 1, 'அரை' for 0.5, 'இரண்டு' for 2), parse them correctly. " +
                "If an item is not in the menu, estimate a realistic price based on local Tamil Nadu rates. " +
                "Strictly adhere to the JSON schema provided.")
        }
    )

    /**
     * Parses the spoken Tamil transcript into a structured [ParsedOrderResult]
     * using Firebase AI (Gemini).
     */
    suspend fun parseOrder(transcript: String, shop: Shop): Result<ParsedOrderResult> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Parsing transcript: \"$transcript\" for shop: ${shop.nameTamil} (${shop.category})")

                val menuList = SHOP_MENUS[shop.id] ?: emptyList()
                val menuText = menuList.joinToString("\n") { "- ${it.name}: ₹${it.price}" }

                val prompt = """
                    Shop Name: ${shop.nameTamil}
                    Shop Category: ${shop.category}
                    
                    Available Menu List:
                    $menuText
                    
                    Spoken Order Transcript:
                    "$transcript"
                    
                    Please parse this transcript and output a JSON matching the required schema.
                """.trimIndent()

                val response = model.generateContent(content { text(prompt) })
                val jsonText = response.text ?: throw Exception("Empty response from Gemini")

                Log.d(TAG, "Gemini JSON response: $jsonText")

                val json = JSONObject(jsonText)
                val jsonItems = json.getJSONArray("items")
                val parsedItems = mutableListOf<ParsedOrderItem>()

                for (i in 0 until jsonItems.length()) {
                    val itemJson = jsonItems.getJSONObject(i)
                    parsedItems.add(
                        ParsedOrderItem(
                            name = itemJson.getString("name"),
                            quantity = itemJson.getInt("quantity"),
                            unitPrice = itemJson.getDouble("unitPrice"),
                            totalItemPrice = itemJson.getDouble("totalItemPrice")
                        )
                    )
                }

                val result = ParsedOrderResult(
                    items = parsedItems,
                    totalPrice = json.getDouble("totalPrice"),
                    totalCount = json.getInt("totalCount"),
                    itemSummaryTamil = json.getString("itemSummaryTamil")
                )

                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse order: ${e.message}", e)
                Result.failure(e)
            }
        }
}
