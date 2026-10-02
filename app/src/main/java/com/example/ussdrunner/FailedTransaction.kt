package com.example.ussdrunner

import org.json.JSONObject

data class FailedTransaction(
    val id: String,
    val tx: MpesaTransaction,
    val productId: String?,
    val productName: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    var retryCount: Int = 0,
    var resolved: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("tx", tx.toJson())
        put("productId", productId ?: JSONObject.NULL)
        put("productName", productName)
        put("reason", reason)
        put("timestamp", timestamp)
        put("retryCount", retryCount)
        put("resolved", resolved)
    }

    companion object {
        fun fromJson(o: JSONObject): FailedTransaction = FailedTransaction(
            id = o.optString("id"),
            tx = MpesaTransaction.fromJson(o.getJSONObject("tx")),
            productId = if (o.isNull("productId")) null else o.optString("productId"),
            productName = o.optString("productName"),
            reason = o.optString("reason"),
            timestamp = o.optLong("timestamp"),
            retryCount = o.optInt("retryCount", 0),
            resolved = o.optBoolean("resolved", false)
        )
    }
}
