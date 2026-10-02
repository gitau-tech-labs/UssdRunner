package com.example.ussdrunner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return

        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val address = msgs[0].originatingAddress ?: ""

        val subId: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            intent.getIntExtra("subscription", -1) else -1

        val selectedSub = AppPrefs.getMpesaSubId(context)
        if (selectedSub != -1 && subId != -1 && subId != selectedSub) return

        if (!MpesaParser.isMpesaMessage(address, body)) return

        val tx = MpesaParser.parse(body) ?: run {
            UssdLog.append("⚠️ M‑Pesa SMS not parsed"); return
        }

        UssdLog.append("💰 ${tx.direction} · KSH ${"%.2f".format(tx.amount)} · ${tx.name} · ${tx.phone}")
        MpesaStore.add(tx)

        if (AppPrefs.isAutoTrigger(context) && tx.direction == MpesaTransaction.Direction.IN) {
            val product = ProductStore.findMatching(tx.amount)
            if (product != null) AutomationEngine.trigger(context, product, tx)
            else UssdLog.append("ℹ️ No product priced KSH ${"%.2f".format(tx.amount)}")
        }
    }
}
