package com.example.ussdrunner

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val steps: List<String>,
    val successMessage: String,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("price", price)
        put("steps", JSONArray(steps)); put("successMessage", successMessage)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject): Product {
            val arr = o.optJSONArray("steps") ?: JSONArray()
            return Product(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                price = o.optDouble("price", 0.0),
                steps = (0 until arr.length()).map { arr.getString(it) },
                successMessage = o.optString("successMessage"),
                enabled = o.optBoolean("enabled", true)
            )
        }
    }
}
