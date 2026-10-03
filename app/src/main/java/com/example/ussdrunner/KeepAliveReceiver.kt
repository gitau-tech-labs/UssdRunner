package com.example.ussdrunner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

class KeepAliveReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TICK = "com.example.ussdrunner.KEEPALIVE_TICK"
        private const val RESPONSE_WAIT_MS = 6000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule on boot if config was enabled
                val cfg = KeepAliveStore.getConfig(app)
                if (cfg.enabled) KeepAliveScheduler.scheduleNext(app, cfg)
                pending.finish()
            }
            ACTION_TICK -> Thread { runTick(app, pending) }.start()
            else -> pending.finish()
        }
    }

    private fun runTick(context: Context, pending: BroadcastReceiver.PendingResult) {
        try {
            val cfg = KeepAliveStore.getConfig(context)
            if (!cfg.enabled) { KeepAliveScheduler.cancel(context); return }

            // Reschedule FIRST so a crash mid-tick doesn't kill the chain.
            KeepAliveScheduler.scheduleNext(context, cfg)

            // Queue-safe: defer if M-Pesa automation is busy.
            if (AutomationEngine.isBusy()) {
                UssdLog.append("⏸️ Keep-alive deferred: M-Pesa automation busy")
                KeepAliveStore.addActivity(context, KeepAliveActivity(
                    simLabel = "—", result = "DEFERRED",
                    response = "Queue busy — retried in 60s"
                ))
                KeepAliveScheduler.scheduleNext(context, cfg, delayMs = 60_000)
                return
            }

            val subId = pickSubId(context, cfg)
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                UssdLog.append("❌ Keep-alive: no SIM")
                KeepAliveStore.addActivity(context, KeepAliveActivity(
                    simLabel = "—", result = "FAILED", response = "No SIM available"
                ))
                return
            }

            KeepAliveSession.begin(subId)
            UssdLog.append("🔄 Keep-alive tick · subId=$subId · ${cfg.code}")
            SimDialer.dialUssd(context, cfg.code, subId)

            Thread.sleep(RESPONSE_WAIT_MS)

            val response = KeepAliveSession.response.ifBlank { "(no response)" }
            KeepAliveSession.end()

            val result = if (response == "(no response)") "NO RESPONSE" else "OK"
            KeepAliveStore.addActivity(context, KeepAliveActivity(
                simLabel = simLabel(context, subId),
                result = result,
                response = response.replace("\n", " | ").take(200)
            ))

            // Remember for alternation
            KeepAliveStore.setConfig(context, cfg.copy(lastUsedSubId = subId))

        } catch (e: Exception) {
            UssdLog.append("❌ Keep-alive error: ${e.message}")
        } finally {
            pending.finish()
        }
    }

    private fun pickSubId(context: Context, cfg: KeepAliveConfig): Int {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return -1
        val subs = try { sm.activeSubscriptionInfoList ?: emptyList() }
                   catch (_: SecurityException) { emptyList() }
        if (subs.isEmpty()) return -1

        return when {
            cfg.alternateSim && subs.size >= 2 -> {
                // Alternate based on lastUsedSubId
                val current = subs.indexOfFirst { it.subscriptionId == cfg.lastUsedSubId }
                val nextIdx = if (current < 0) 0 else (current + 1) % subs.size
                subs[nextIdx].subscriptionId
            }
            cfg.preferredSubId != -1 -> {
                subs.firstOrNull { it.subscriptionId == cfg.preferredSubId }?.subscriptionId
                    ?: subs.first().subscriptionId
            }
            else -> subs.first().subscriptionId
        }
    }

    private fun simLabel(context: Context, subId: Int): String {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return "SIM"
        val info: SubscriptionInfo? = try { sm.getActiveSubscriptionInfo(subId) }
                                      catch (_: SecurityException) { null }
        val slot = (info?.simSlotIndex ?: 0) + 1
        return "SIM$slot"
    }
}