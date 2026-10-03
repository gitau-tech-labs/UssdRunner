package com.example.ussdrunner

import org.json.JSONObject

data class KeepAliveActivity(
    val ts: Long = System.currentTimeMillis(),
    val simLabel: String = "—",
    val result: String = "OK",   // "OK" | "NO RESPONSE" | "FAILED"
    val response: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", ts); put("simLabel", simLabel)
        put("result", result); put("response", response)
    }

    companion object {
        fun fromJson(o: JSONObject): KeepAliveActivity = KeepAliveActivity(
            ts = o.optLong("ts"),
            simLabel = o.optString("simLabel", "—"),
            result = o.optString("result", "OK"),
            response = o.optString("response", "")
        )
    }
}