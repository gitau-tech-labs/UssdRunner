package com.example.ussdrunner

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray

object KeepAliveStore {

    private const val PREFS = "keepalive_prefs"
    private const val KEY_CONFIG = "config"
    private const val KEY_LOG = "log"
    private const val MAX_LOG = 100

    private val _log = MutableLiveData<List<KeepAliveActivity>>(emptyList())
    val log: LiveData<List<KeepAliveActivity>> = _log

    private var ctx: Context? = null

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        loadLog()
    }

    // ---- Config ----

    @Synchronized
    fun getConfig(context: Context): KeepAliveConfig {
        val p = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_CONFIG, null) ?: return KeepAliveConfig()
        return runCatching { KeepAliveConfig.fromJson(org.json.JSONObject(raw)) }
            .getOrDefault(KeepAliveConfig())
    }

    @Synchronized
    fun setConfig(context: Context, cfg: KeepAliveConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONFIG, cfg.toJson().toString()).apply()
    }

    // ---- Activity log ----

    @Synchronized
    fun addActivity(context: Context, entry: KeepAliveActivity) {
        val cur = _log.value.orEmpty()
        val updated = (listOf(entry) + cur).take(MAX_LOG)
        _log.postValue(updated)
        persistLog(context, updated)
    }

    @Synchronized
    fun clearLog(context: Context) {
        _log.postValue(emptyList())
        persistLog(context, emptyList())
    }

    private fun loadLog() {
        val c = ctx ?: return
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, null) ?: return
        _log.value = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { KeepAliveActivity.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persistLog(context: Context, list: List<KeepAliveActivity>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOG, arr.toString()).apply()
    }
}