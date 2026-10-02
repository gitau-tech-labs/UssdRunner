package com.example.ussdrunner

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray

object FailedStore {

    private const val PREFS = "failed_store"
    private const val KEY = "entries"
    private const val MAX = 300

    private val _failures = MutableLiveData<List<FailedTransaction>>(emptyList())
    val failures: LiveData<List<FailedTransaction>> = _failures

    private var ctx: Context? = null

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        load()
    }

    @Synchronized
    fun add(f: FailedTransaction) {
        val cur = _failures.value.orEmpty()
        val updated = (listOf(f) + cur).take(MAX)
        _failures.postValue(updated)
        persist(updated)
    }

    @Synchronized
    fun markResolved(id: String) {
        val updated = _failures.value.orEmpty().map {
            if (it.id == id) it.copy(resolved = true) else it
        }
        _failures.postValue(updated)
        persist(updated)
    }

    @Synchronized
    fun bumpRetry(id: String) {
        val updated = _failures.value.orEmpty().map {
            if (it.id == id) it.copy(retryCount = it.retryCount + 1) else it
        }
        _failures.postValue(updated)
        persist(updated)
    }

    @Synchronized
    fun clearResolved() {
        val updated = _failures.value.orEmpty().filterNot { it.resolved }
        _failures.postValue(updated)
        persist(updated)
    }

    fun find(id: String) = _failures.value.orEmpty().firstOrNull { it.id == id }

    private fun load() {
        val c = ctx ?: return
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        _failures.value = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { FailedTransaction.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<FailedTransaction>) {
        val c = ctx ?: return
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }
}
