package com.example.ussdrunner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

object AutomationEngine {

    /** If no dialog activity appears within this window, treat the flow as failed. */
    private const val STALL_TIMEOUT_MS = 45_000L

    @Volatile var activeTransaction: MpesaTransaction? = null
        private set
    @Volatile var activeProduct: Product? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var watchdog: Runnable? = null

    fun attach(context: Context) { appCtx = context.applicationContext }

    fun trigger(context: Context, product: Product, tx: MpesaTransaction) {
        val resolved = PlaceholderResolver.resolveAll(product.steps, tx)
        UssdLog.append("🎬 Matched: ${product.name} (KSH %.2f)".format(product.price))
        UssdLog.append("📋 Steps: ${resolved.joinToString(" → ")}")

        activeTransaction = tx
        activeProduct = product
        UssdStepStore.begin(resolved)

        val init = AppPrefs.getInitializer(context)
        if (init.isBlank()) {
            UssdLog.append("❌ No USSD initializer set")
            fail("No initializer configured")
            return
        }
        armWatchdog()
        dialUssd(context, init, AppPrefs.getUssdSubId(context))
    }

    /** Called by the accessibility service after each non-final step is sent. */
    fun heartbeat() { if (activeTransaction != null) armWatchdog() }

    /**
     * Called by the accessibility service the moment the last step is sent.
     * We assume success and send the customer SMS immediately — no wait.
     */
    fun onStepsComplete() {
        val tx = activeTransaction ?: return
        val product = activeProduct ?: return
        val ctx = appCtx ?: return

        cancelWatchdog()
        UssdLog.append("✅ Flow complete for ${tx.code}")

        if (AppPrefs.isSendSuccessSms(ctx)) {
            val template = product.successMessage.ifBlank { AppPrefs.getSuccessMessage(ctx) }
            val msg = PlaceholderResolver.resolveMessage(template, tx)
            SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSmsSubId(ctx))
        }
        clear()
    }

    private fun fail(reason: String) {
        val tx = activeTransaction ?: return
        val ctx = appCtx ?: return

        UssdLog.append("❌ Flow failed: $reason")
        if (AppPrefs.isSendSuccessSms(ctx)) {
            val msg = PlaceholderResolver.resolveMessage(AppPrefs.getFailedMessage(ctx), tx)
            SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSmsSubId(ctx))
        }
        clear()
    }

    private fun clear() {
        cancelWatchdog()
        activeTransaction = null
        activeProduct = null
        UssdStepStore.reset()
    }

    private fun armWatchdog() {
        cancelWatchdog()
        watchdog = Runnable { fail("Flow stalled — no dialog activity") }
        handler.postDelayed(watchdog!!, STALL_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    private fun dialUssd(context: Context, code: String, subId: Int) {
        val encoded = code.replace("#", "%23").replace("*", "%2A")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$encoded")).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra("com.android.phone.extra.slot", subId)
                putExtra("simSlot", subId)
                putExtra("subscription", subId)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) }
        catch (e: Exception) {
            UssdLog.append("❌ Dialer error: ${e.message}")
            fail("Dialer error: ${e.message}")
        }
    }
}
