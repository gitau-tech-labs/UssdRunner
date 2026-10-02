package com.example.ussdrunner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

object AutomationEngine {

    private const val COMPLETION_DELAY_MS = 10_000L

    @Volatile var activeTransaction: MpesaTransaction? = null
        private set
    @Volatile var activeProduct: Product? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var completionScheduled = false

    fun trigger(context: Context, product: Product, tx: MpesaTransaction) {
        val resolved = PlaceholderResolver.resolveAll(product.steps, tx)
        UssdLog.append("🎬 Matched: ${product.name} (KSH ${"%.2f".format(product.price)})")
        UssdLog.append("📋 Steps: ${resolved.joinToString(" → ")}")

        activeTransaction = tx
        activeProduct = product
        completionScheduled = false
        UssdStepStore.begin(resolved)

        val init = AppPrefs.getInitializer(context)
        if (init.isBlank()) {
            UssdLog.append("❌ No USSD initializer set"); return
        }
        val subId = AppPrefs.getSystemSubId(context)
        dialUssd(context, init, subId)
    }

    /** Called by the accessibility service when the last step has been sent. */
    fun scheduleCompletion() {
        if (completionScheduled) return
        completionScheduled = true
        handler.postDelayed({ complete() }, COMPLETION_DELAY_MS)
    }

    private fun complete() {
        val tx = activeTransaction ?: return
        val product = activeProduct ?: return
        val ctx = appCtx ?: return

        UssdLog.append("✅ Flow complete for ${tx.code}")
        if (AppPrefs.isSendSuccessSms(ctx)) {
            val msg = PlaceholderResolver.resolveMessage(product.successMessage, tx)
            SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSystemSubId(ctx))
        }
        activeTransaction = null; activeProduct = null; completionScheduled = false
    }

    // A tiny static context ref so we don't pass Context into complete()
    private var appCtx: Context? = null

    fun attach(context: Context) { appCtx = context.applicationContext }

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
        context.startActivity(intent)
    }
}
