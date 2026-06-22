package com.example.speech

import com.example.domain.Shop

data class ParsedOrderItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalItemPrice: Double
)

data class ParsedOrderResult(
    val items: List<ParsedOrderItem>,
    val totalPrice: Double,
    val totalCount: Int,
    val itemSummaryTamil: String
)

object VoiceOrderParser {
    suspend fun parseOrder(transcript: String, shop: Shop): Result<ParsedOrderResult> {
        return Result.failure(Exception("Stub"))
    }
}
