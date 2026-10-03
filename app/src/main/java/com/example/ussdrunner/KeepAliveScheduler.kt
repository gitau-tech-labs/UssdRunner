package com.example.ussdrunner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object KeepAliveScheduler {

    private const val REQUEST_CODE = 0x4B4C  // "KL"

    fun scheduleNext(context: Context, cfg: KeepAliveConfig, delayMs: Long = -1L) {
        if (!cfg.enabled) { cancel(context); return }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() +
            if (delayMs > 0) delayMs else cfg.intervalMs

        val pi = pendingIntent(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                UssdLog.append("⏰ Next keep-alive in ${(triggerAt - System.currentTimeMillis()) / 60_000} min")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                UssdLog.append("⏰ Next keep-alive (inexact) in ${(triggerAt - System.currentTimeMillis()) / 60_000} min")
            }
        } catch (e: SecurityException) {
            UssdLog.append("⚠️ Alarm permission denied: ${e.message}")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
        UssdLog.append("🛑 Keep-alive cancelled")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).apply {
            action = KeepAliveReceiver.ACTION_TICK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}