package com.example.ussdrunner

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import kotlin.math.abs

object ProductStore {

    private const val PREFS = "product_store"
    private const val KEY = "products"

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private var ctx: Context? = null

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        load()
    }

    @Synchronized
    fun save(product: Product) {
        val current = _products.value.orEmpty().toMutableList()
        val i = current.indexOfFirst { it.id == product.id }
        if (i >= 0) current[i] = product else current.add(product)
        _products.postValue(current); persist(current)
    }

    @Synchronized fun delete(id: String) {
        val u = _products.value.orEmpty().filterNot { it.id == id }
        _products.postValue(u); persist(u)
    }

    fun findMatching(amount: Double): Product? =
        _products.value.orEmpty().firstOrNull { it.enabled && abs(it.price - amount) < 0.01 }

    private fun load() {
        val c = ctx ?: return
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        _products.value = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Product.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Product>) {
        val c = ctx ?: return
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }
}
