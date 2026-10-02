package com.example.ussdrunner

import org.json.JSONObject

data class MpesaTransaction(
    val code: String,
    val direction: Direction,
    val amount: Double,
    val phone: String,
    val name: String,
    val dateTime: String,
    val balance: Double,
    val cost: Double,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Direction { IN, OUT, UNKNOWN }

    /** Kenya local format: 254182555814 → 0182555814 */
    val phoneLocal: String get() = when {
        phone.startsWith("254") && phone.length == 12 -> "0" + phone.substring(3)
        phone.startsWith("0") && phone.length == 10 -> phone
        else -> phone
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("code", code); put("direction", direction.name)
        put("amount", amount); put("phone", phone); put("name", name)
        put("dateTime", dateTime); put("balance", balance)
        put("cost", cost); put("rawMessage", rawMessage); put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject) = MpesaTransaction(
            code = o.optString("code"),
            direction = runCatching { Direction.valueOf(o.optString("direction")) }
                .getOrDefault(Direction.UNKNOWN),
            amount = o.optDouble("amount", 0.0),
            phone = o.optString("phone"),
            name = o.optString("name"),
            dateTime = o.optString("dateTime"),
            balance = o.optDouble("balance", 0.0),
            cost = o.optDouble("cost", 0.0),
            rawMessage = o.optString("rawMessage"),
            timestamp = o.optLong("timestamp")
        )
    }
}
