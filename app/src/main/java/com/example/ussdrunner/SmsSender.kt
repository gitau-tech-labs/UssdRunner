package com.example.ussdrunner

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

object SmsSender {

    fun send(context: Context, toPhone: String, message: String, subId: Int) {
        if (toPhone.isBlank()) {
            UssdLog.append("⚠️ No customer phone — SMS skipped"); return
        }
        try {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                val base = context.getSystemService(SmsManager::class.java)
                base.createForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }
            val parts = sm.divideMessage(message)
            sm.sendMultipartTextMessage(toPhone, null, parts, null, null)
            UssdLog.append("📨 SMS sent to $toPhone")
        } catch (e: Exception) {
            UssdLog.append("❌ SMS failed: ${e.message}")
        }
    }
}
