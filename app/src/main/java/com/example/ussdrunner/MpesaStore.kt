package com.example.ussdrunner

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray

object MpesaStore {

    private const val PREFS = "mpesa_store"
    private const val KEY = "transactions"

    private val _transactions = MutableLiveData<List<MpesaTransaction>>(emptyList())
    val transactions: LiveData<List<MpesaTransaction>> = _transactions

    private var ctx: Context? = null

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        load()
    }

    @Synchronized
    fun add(tx: MpesaTransaction) {
        val current = _transactions.value.orEmpty()
        if (tx.code.isNotEmpty() && current.any { it.code == tx.code }) return
        val updated = (listOf(tx) + current).take(1000)
        _transactions.postValue(updated)
        persist(updated)
    }

    @Synchronized fun clear() { _transactions.postValue(emptyList()); persist(emptyList()) }

    private fun load() {
        val c = ctx ?: return
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        _transactions.value = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { MpesaTransaction.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<MpesaTransaction>) {
        val c = ctx ?: return
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }
}
