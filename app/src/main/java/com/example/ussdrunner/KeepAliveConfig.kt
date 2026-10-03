package com.example.ussdrunner

import org.json.JSONObject

data class KeepAliveConfig(
    val enabled: Boolean = false,
    val code: String = "*144#",
    val intervalValue: Int = 15,
    val intervalUnit: String = "MINUTES",   // "MINUTES" | "HOURS"
    val alternateSim: Boolean = false,
    val preferredSubId: Int = -1,
    val lastUsedSubId: Int = -1
) {
    /** Effective interval in milliseconds. */
    val intervalMs: Long
        get() {
            val minutes = if (intervalUnit == "HOURS") intervalValue * 60 else intervalValue
            return minutes.coerceAtLeast(15) * 60_000L
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("code", code)
        put("intervalValue", intervalValue)
        put("intervalUnit", intervalUnit)
        put("alternateSim", alternateSim)
        put("preferredSubId", preferredSubId)
        put("lastUsedSubId", lastUsedSubId)
    }

    companion object {
        fun fromJson(o: JSONObject): KeepAliveConfig = KeepAliveConfig(
            enabled = o.optBoolean("enabled", false),
            code = o.optString("code", "*144#"),
            intervalValue = o.optInt("intervalValue", 15),
            intervalUnit = o.optString("intervalUnit", "MINUTES"),
            alternateSim = o.optBoolean("alternateSim", false),
            preferredSubId = o.optInt("preferredSubId", -1),
            lastUsedSubId = o.optInt("lastUsedSubId", -1)
        )
    }
}