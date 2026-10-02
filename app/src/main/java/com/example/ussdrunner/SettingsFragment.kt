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

    private lateinit var actSys: AutoCompleteTextView
    private lateinit var actMpesa: AutoCompleteTextView
    private lateinit var etInitializer: TextInputEditText
    private lateinit var switchAuto: MaterialSwitch
    private lateinit var switchSms: MaterialSwitch

    private var sysSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var mpesaSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        actSys         = v.findViewById(R.id.actSysSim)
        actMpesa       = v.findViewById(R.id.actMpesaSim)
        etInitializer  = v.findViewById(R.id.etInitializer)
        switchAuto     = v.findViewById(R.id.switchAuto)
        switchSms      = v.findViewById(R.id.switchSendSms)

        v.findViewById<MaterialButton>(R.id.btnSaveSettings)
            .setOnClickListener { save() }

        val ctx = requireContext()
        etInitializer.setText(AppPrefs.getInitializer(ctx))
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
            actSys.setText("No SIM detected", false); actSys.isEnabled = false
            actMpesa.setText("No SIM detected", false); actMpesa.isEnabled = false
            return
        }

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels)
        actSys.setAdapter(adapter)
        actMpesa.setAdapter(adapter)

        val savedSys   = AppPrefs.getSystemSubId(ctx)
        val savedMpesa = AppPrefs.getMpesaSubId(ctx)

        val sysIdx   = subs.indexOfFirst { it.subscriptionId == savedSys }.takeIf { it >= 0 } ?: 0
        val mpesaIdx = subs.indexOfFirst { it.subscriptionId == savedMpesa }.takeIf { it >= 0 } ?: sysIdx

        actSys.setText(labels[sysIdx], false);     sysSubId   = subs[sysIdx].subscriptionId
        actMpesa.setText(labels[mpesaIdx], false); mpesaSubId = subs[mpesaIdx].subscriptionId

        actSys.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) sysSubId = subs[pos].subscriptionId }
        actMpesa.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) mpesaSubId = subs[pos].subscriptionId }
    }

    private fun save() {
        val ctx = requireContext()
        AppPrefs.setSystemSubId(ctx, sysSubId)
        AppPrefs.setMpesaSubId(ctx, mpesaSubId)
        AppPrefs.setInitializer(ctx, etInitializer.text?.toString()?.trim().orEmpty())
        AppPrefs.setAutoTrigger(ctx, switchAuto.isChecked)
        AppPrefs.setSendSuccessSms(ctx, switchSms.isChecked)
        Toast.makeText(ctx, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
}
