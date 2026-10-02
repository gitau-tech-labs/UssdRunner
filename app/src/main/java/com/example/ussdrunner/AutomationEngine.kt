package com.example.ussdrunner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

object AutomationEngine {

    /** If no new USSD dialog appears within this window, treat the flow as failed. */
    private const val STALL_TIMEOUT_MS = 45_000L

    @Volatile var activeTransaction: MpesaTransaction? = null
        private set
    @Volatile var activeProduct: Product? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var stallWatchdog: Runnable? = null

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
        dialUssd(context, init, AppPrefs.getSystemSubId(context))
    }

    /** Called by the accessibility service every time it successfully sends a step. */
    fun heartbeat() {
        if (activeTransaction != null) armWatchdog()
    }

    /** Called by the accessibility service when the last step is sent. */
    fun scheduleCompletion() {
        cancelWatchdog()
        handler.postDelayed({ complete() }, 10_000L)
    }

    private fun armWatchdog() {
        cancelWatchdog()
        stallWatchdog = Runnable { fail("Flow stalled — no dialog activity") }
        handler.postDelayed(stallWatchdog!!, STALL_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        stallWatchdog?.let { handler.removeCallbacks(it) }
        stallWatchdog = null
    }

    private fun complete() {
        val tx = activeTransaction ?: return
        val product = activeProduct ?: return
        val ctx = appCtx ?: return

        UssdLog.append("✅ Flow complete for ${tx.code}")
        if (AppPrefs.isSendSuccessSms(ctx)) {
            val template = product.successMessage.ifBlank { AppPrefs.getSuccessMessage(ctx) }
            val msg = PlaceholderResolver.resolveMessage(template, tx)
            SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSystemSubId(ctx))
        }
        clear()
    }

    private fun fail(reason: String) {
        val tx = activeTransaction ?: return
        val ctx = appCtx ?: return

        UssdLog.append("❌ Flow failed: $reason")
        if (AppPrefs.isSendSuccessSms(ctx)) {   // same toggle governs failed SMS too
            val template = AppPrefs.getFailedMessage(ctx)
            val msg = PlaceholderResolver.resolveMessage(template, tx)
            SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSystemSubId(ctx))
        }
        clear()
    }

    private fun clear() {
        cancelWatchdog()
        activeTransaction = null
        activeProduct = null
        UssdStepStore.reset()
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
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            UssdLog.append("❌ Dialer error: ${e.message}")
            fail("Dialer error: ${e.message}")
        }
    }
}
