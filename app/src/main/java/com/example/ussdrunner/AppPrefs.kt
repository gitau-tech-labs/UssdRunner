package com.example.ussdrunner

import android.content.Context

object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_SYS_SUB = "system_sub_id"
    private const val KEY_MPESA_SUB = "mpesa_sub_id"
    private const val KEY_AUTO = "auto_trigger"
    private const val KEY_SEND_SMS = "send_success_sms"
    private const val KEY_INIT = "ussd_initializer"

    fun getSystemSubId(c: Context) = p(c).getInt(KEY_SYS_SUB, -1)
    fun setSystemSubId(c: Context, id: Int) = p(c).edit().putInt(KEY_SYS_SUB, id).apply()

    fun getMpesaSubId(c: Context) = p(c).getInt(KEY_MPESA_SUB, -1)
    fun setMpesaSubId(c: Context, id: Int) = p(c).edit().putInt(KEY_MPESA_SUB, id).apply()

    fun isAutoTrigger(c: Context) = p(c).getBoolean(KEY_AUTO, true)
    fun setAutoTrigger(c: Context, on: Boolean) = p(c).edit().putBoolean(KEY_AUTO, on).apply()

    fun isSendSuccessSms(c: Context) = p(c).getBoolean(KEY_SEND_SMS, true)
    fun setSendSuccessSms(c: Context, on: Boolean) = p(c).edit().putBoolean(KEY_SEND_SMS, on).apply()

    fun getInitializer(c: Context): String = p(c).getString(KEY_INIT, "") ?: ""
    fun setInitializer(c: Context, v: String) = p(c).edit().putString(KEY_INIT, v).apply()

    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
