package com.sukashawarma.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ParseReceiptPayload(
    @SerializedName("imageBase64") val imageBase64: String,
    @SerializedName("menuText") val menuText: String
)

data class ParseReceiptResponse(
    @SerializedName("items") val items: List<ParsedItem>?,
    @SerializedName("subsidies") val subsidies: List<ParsedSubsidy>?,
    @SerializedName("error") val error: String?
)

data class ParsedItem(
    @SerializedName("name") val name: String,
    @SerializedName("qty") val qty: Int,
    @SerializedName("price") val price: Double?,
    @SerializedName("matched") val matched: Boolean
)

data class ParsedSubsidy(
    @SerializedName("name") val name: String,
    @SerializedName("amount") val amount: Double
)
