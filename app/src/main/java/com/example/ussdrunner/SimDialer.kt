package com.example.ussdrunner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

/**
 * Dials USSD codes on a SPECIFIC SIM using TelecomManager.
 *
 * The classic "com.android.phone.extra.slot" extras only work on a few OEM dialers.
 * TelecomManager.placeCall() with EXTRA_PHONE_ACCOUNT_HANDLE is the official way
 * to force a call (and USSD) onto a chosen subscription, and it works on every
 * device since Android 6.0.
 */
object SimDialer {

    fun dialUssd(context: Context, code: String, subId: Int) {
        val encoded = code.replace("#", "%23").replace("*", "%2A")
        val uri = Uri.parse("tel:$encoded")

        val handle = resolveHandle(context, subId)
        if (handle == null) {
            UssdLog.append("⚠️ No phone account for subId=$subId — falling back to default SIM")
            fallbackCall(context, uri)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            fallbackCall(context, uri); return
        }

        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.placeCall(uri, extras)
            UssdLog.append("📞 Dialing on SIM account: ${handle.id}")
        } catch (e: SecurityException) {
            UssdLog.append("❌ placeCall denied: ${e.message} — trying fallback")
            fallbackCall(context, uri)
        } catch (e: Exception) {
            UssdLog.append("❌ placeCall error: ${e.message} — trying fallback")
            fallbackCall(context, uri)
        }
    }

    private fun fallbackCall(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            UssdLog.append("❌ Fallback call failed: ${e.message}")
        }
    }

    /** Find the PhoneAccountHandle that corresponds to the given subscriptionId. */
    private fun resolveHandle(context: Context, subId: Int): PhoneAccountHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return null

        val info: SubscriptionInfo? = try {
            sm.getActiveSubscriptionInfo(subId)
        } catch (_: SecurityException) { null }

        val slotIndex = info?.simSlotIndex ?: -1
        val iccId = info?.iccId

        val accounts: List<PhoneAccountHandle> = try {
            telecom.callCapablePhoneAccounts
        } catch (_: SecurityException) { emptyList() }

        if (accounts.isEmpty()) return null

        // 1) Best match: the PhoneAccountHandle id usually contains the ICC ID.
        if (!iccId.isNullOrBlank()) {
            accounts.firstOrNull { it.id.contains(iccId) }?.let { return it }
        }

        // 2) Match by SIM slot index.
        if (slotIndex in accounts.indices) return accounts[slotIndex]

        return null
    }
}