package com.example.ussdrunner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

object AutomationEngine {

    private const val STALL_TIMEOUT_MS = 20_000L

    @Volatile var activeTransaction: MpesaTransaction? = null
        private set
    @Volatile var activeProduct: Product? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var watchdog: Runnable? = null

    fun attach(context: Context) { appCtx = context.applicationContext }

    // ---------- Trigger (auto or manual) ----------

    fun trigger(context: Context, product: Product, tx: MpesaTransaction) {
        val resolved = PlaceholderResolver.resolveAll(product.steps, tx)
        UssdLog.append("🎬 Matched: ${product.name} (KSH %.2f)".format(product.price))
        UssdLog.append("📋 Steps: ${resolved.joinToString(" → ")}")

        activeTransaction = tx
        activeProduct = product
        UssdStepStore.begin(resolved)

        val init = AppPrefs.getInitializer(context)
        if (init.isBlank()) { fail("No initializer configured"); return }

        armWatchdog()
        dialUssd(context, init, AppPrefs.getUssdSubId(context))
    }

    /** Called from the Failed screen Retry button. */
    fun retry(context: Context, failureId: String) {
        val f = FailedStore.find(failureId) ?: return
        val product = ProductStore.products.value.orEmpty().firstOrNull { it.id == f.productId }
            ?: ProductStore.findMatching(f.tx.amount)

        if (product == null) {
            UssdLog.append("❌ Retry failed: no product for KSH %.2f".format(f.tx.amount))
            return
        }
        FailedStore.bumpRetry(failureId)
        UssdLog.append("🔁 Retry #${f.retryCount + 1} for ${f.tx.code}")
        trigger(context, product, f.tx)
    }

    // ---------- Hooks from accessibility service ----------

    fun heartbeat() { if (activeTransaction != null) armWatchdog() }

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
        refresh(ctx, "success")
    }

    // ---------- Failure ----------

    private fun fail(reason: String) {
        val tx = activeTransaction
        val product = activeProduct
        val ctx = appCtx

        UssdLog.append("❌ Flow failed: $reason")

        if (tx != null && ctx != null) {
            FailedStore.add(
                FailedTransaction(
                    id = UUID.randomUUID().toString(),
                    tx = tx,
                    productId = product?.id,
                    productName = product?.name ?: "Unknown product",
                    reason = reason
                )
            )
            if (AppPrefs.isSendSuccessSms(ctx)) {
                val msg = PlaceholderResolver.resolveMessage(AppPrefs.getFailedMessage(ctx), tx)
                SmsSender.send(ctx, tx.phone, msg, AppPrefs.getSmsSubId(ctx))
            }
            refresh(ctx, "failure")
        } else {
            hardReset()
        }
    }

    // ---------- Full system refresh ----------

    private fun refresh(ctx: Context, tag: String) {
        hardReset()
        UssdLog.append("🔄 Ready for next transaction ($tag)")
    }

    private fun hardReset() {
        cancelWatchdog()
        activeTransaction = null
        activeProduct = null
        UssdStepStore.reset()
        // The accessibility service notices UssdStepStore.active == false
        // and clears its internal menu signature automatically.
    }

    // ---------- Watchdog ----------

    private fun armWatchdog() {
        cancelWatchdog()
        watchdog = Runnable { fail("No dialog activity for ${STALL_TIMEOUT_MS / 1000}s") }
        handler.postDelayed(watchdog!!, STALL_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    // ---------- Dialer ----------

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
        catch (e: Exception) { fail("Dialer error: ${e.message}") }
    }
}
