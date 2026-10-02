package com.example.ussdrunner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {

    private var subs: List<SubscriptionInfo> = emptyList()

    private lateinit var actUssd: AutoCompleteTextView
    private lateinit var actSms: AutoCompleteTextView
    private lateinit var actMpesa: AutoCompleteTextView
    private lateinit var etInitializer: TextInputEditText
    private lateinit var etSuccessMessage: TextInputEditText
    private lateinit var etFailedMessage: TextInputEditText
    private lateinit var switchAuto: MaterialSwitch
    private lateinit var switchSms: MaterialSwitch

    private var ussdSubId   = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var smsSubId    = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var mpesaSubId  = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        actUssd          = v.findViewById(R.id.actUssdSim)
        actSms           = v.findViewById(R.id.actSmsSim)
        actMpesa         = v.findViewById(R.id.actMpesaSim)
        etInitializer    = v.findViewById(R.id.etInitializer)
        etSuccessMessage = v.findViewById(R.id.etSuccessMessage)
        etFailedMessage  = v.findViewById(R.id.etFailedMessage)
        switchAuto       = v.findViewById(R.id.switchAuto)
        switchSms        = v.findViewById(R.id.switchSendSms)

        v.findViewById<MaterialButton>(R.id.btnSaveSettings)
            .setOnClickListener { save() }
        v.findViewById<MaterialButton>(R.id.btnResetMessages)
            .setOnClickListener { resetMessages() }

        val ctx = requireContext()
        etInitializer.setText(AppPrefs.getInitializer(ctx))
        etSuccessMessage.setText(AppPrefs.getSuccessMessage(ctx))
        etFailedMessage.setText(AppPrefs.getFailedMessage(ctx))
        switchAuto.isChecked = AppPrefs.isAutoTrigger(ctx)
        switchSms.isChecked  = AppPrefs.isSendSuccessSms(ctx)

        loadSims()
    }

    private fun loadSims() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return

        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        subs = try { sm.activeSubscriptionInfoList ?: emptyList() }
               catch (_: SecurityException) { emptyList() }

        val labels = subs.map { s ->
            val slot = s.simSlotIndex + 1
            val name = s.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM $slot"
            "$name  ·  SIM $slot"
        }
        if (labels.isEmpty()) {
            listOf(actUssd, actSms, actMpesa).forEach {
                it.setText("No SIM detected", false); it.isEnabled = false
            }
            return
        }

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels)
        actUssd.setAdapter(adapter)
        actSms.setAdapter(adapter)
        actMpesa.setAdapter(adapter)

        val savedUssd   = AppPrefs.getUssdSubId(ctx)
        val savedSms    = AppPrefs.getSmsSubId(ctx)
        val savedMpesa  = AppPrefs.getMpesaSubId(ctx)

        val ussdIdx  = subs.indexOfFirst { it.subscriptionId == savedUssd }.takeIf  { it >= 0 } ?: 0
        val smsIdx   = subs.indexOfFirst { it.subscriptionId == savedSms }.takeIf   { it >= 0 } ?: ussdIdx
        val mpesaIdx = subs.indexOfFirst { it.subscriptionId == savedMpesa }.takeIf { it >= 0 } ?: ussdIdx

        actUssd.setText(labels[ussdIdx], false);   ussdSubId  = subs[ussdIdx].subscriptionId
        actSms.setText(labels[smsIdx], false);     smsSubId   = subs[smsIdx].subscriptionId
        actMpesa.setText(labels[mpesaIdx], false); mpesaSubId = subs[mpesaIdx].subscriptionId

        actUssd.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) ussdSubId = subs[pos].subscriptionId }
        actSms.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) smsSubId = subs[pos].subscriptionId }
        actMpesa.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) mpesaSubId = subs[pos].subscriptionId }
    }

    private fun save() {
        val ctx = requireContext()
        AppPrefs.setUssdSubId(ctx, ussdSubId)
        AppPrefs.setSmsSubId(ctx, smsSubId)
        AppPrefs.setMpesaSubId(ctx, mpesaSubId)
        AppPrefs.setInitializer(ctx, etInitializer.text?.toString()?.trim().orEmpty())
        AppPrefs.setSuccessMessage(ctx, etSuccessMessage.text?.toString()?.trim().orEmpty())
        AppPrefs.setFailedMessage(ctx, etFailedMessage.text?.toString()?.trim().orEmpty())
        AppPrefs.setAutoTrigger(ctx, switchAuto.isChecked)
        AppPrefs.setSendSuccessSms(ctx, switchSms.isChecked)
        Toast.makeText(ctx, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }

    private fun resetMessages() {
        val ctx = requireContext()
        AppPrefs.resetMessages(ctx)
        etSuccessMessage.setText(AppPrefs.getSuccessMessage(ctx))
        etFailedMessage.setText(AppPrefs.getFailedMessage(ctx))
        Toast.makeText(ctx, "Messages reset to default", Toast.LENGTH_SHORT).show()
    }
}
