package com.example.ussdrunner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeepAliveFragment : Fragment() {

    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var etCode: TextInputEditText
    private lateinit var etInterval: TextInputEditText
    private lateinit var actUnit: AutoCompleteTextView
    private lateinit var switchAltSim: MaterialSwitch
    private lateinit var actSim: AutoCompleteTextView
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var llLog: LinearLayout

    private var subs: List<SubscriptionInfo> = emptyList()
    private var selectedSubId: Int = -1
    private val fmt = SimpleDateFormat("d/M/yy HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_keep_alive, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        switchEnabled = v.findViewById(R.id.switchKeepAliveEnabled)
        etCode        = v.findViewById(R.id.etCode)
        etInterval    = v.findViewById(R.id.etInterval)
        actUnit       = v.findViewById(R.id.actUnit)
        switchAltSim  = v.findViewById(R.id.switchAltSim)
        actSim        = v.findViewById(R.id.actSim)
        tvStatus      = v.findViewById(R.id.tvStatus)
        tvEmpty       = v.findViewById(R.id.tvLogEmpty)
        llLog         = v.findViewById(R.id.llLogList)

        val ctx = requireContext()
        KeepAliveStore.init(ctx)

        // Unit dropdown
        actUnit.setAdapter(ArrayAdapter(
            ctx, android.R.layout.simple_list_item_1, listOf("Minutes", "Hours")
        ))

        loadSims()
        loadConfig()

        switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        actUnit.setOnItemClickListener { _, _, _, _ -> saveConfig() }
        switchAltSim.setOnCheckedChangeListener { _, _ -> saveConfig(); refreshSimEnabled() }
        actSim.setOnItemClickListener { _, _, pos, _ ->
            if (pos in subs.indices) {
                selectedSubId = subs[pos].subscriptionId
                saveConfig()
            }
        }

        v.findViewById<MaterialButton>(R.id.btnClearLog)
            .setOnClickListener { KeepAliveStore.clearLog(ctx) }
        v.findViewById<MaterialButton>(R.id.btnBatteryOpt)
            .setOnClickListener { requestBatteryExemption(ctx) }
        v.findViewById<MaterialButton>(R.id.btnExactAlarm)
            .setOnClickListener { requestExactAlarm(ctx) }

        KeepAliveStore.log.observe(viewLifecycleOwner) { renderLog(it) }
    }

    // ---------- Config ----------

    private fun loadConfig() {
        val cfg = KeepAliveStore.getConfig(requireContext())
        switchEnabled.isChecked = cfg.enabled
        etCode.setText(cfg.code)
        etInterval.setText(cfg.intervalValue.toString())
        actUnit.setText(if (cfg.intervalUnit == "HOURS") "Hours" else "Minutes", false)
        switchAltSim.isChecked = cfg.alternateSim
        selectedSubId = cfg.preferredSubId
        refreshSimEnabled()
        updateStatus()
    }

    private fun saveConfig() {
        val ctx = requireContext()
        val value = etInterval.text?.toString()?.trim()?.toIntOrNull() ?: 15
        val unitStr = actUnit.text?.toString() ?: "Minutes"
        val cfg = KeepAliveStore.getConfig(ctx).copy(
            code = etCode.text?.toString()?.trim().orEmpty().ifBlank { "*144#" },
            intervalValue = value.coerceAtLeast(15),
            intervalUnit = if (unitStr == "Hours") "HOURS" else "MINUTES",
            alternateSim = switchAltSim.isChecked,
            preferredSubId = selectedSubId
        )
        KeepAliveStore.setConfig(ctx, cfg)
    }

    private fun onToggle(on: Boolean) {
        val ctx = requireContext()
        saveConfig()
        val cfg = KeepAliveStore.getConfig(ctx).copy(enabled = on)
        KeepAliveStore.setConfig(ctx, cfg)

        if (on) {
            ensureSchedulingPrerequisites(ctx)
            KeepAliveScheduler.scheduleNext(ctx, cfg)
            Toast.makeText(ctx, "Keep-alive started", Toast.LENGTH_SHORT).show()
        } else {
            KeepAliveScheduler.cancel(ctx)
            KeepAliveSession.cancel()
            Toast.makeText(ctx, "Keep-alive stopped", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun ensureSchedulingPrerequisites(ctx: Context) {
        // Request notification permission (needed on 13+ even for silent updates)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001
                )
            }
        }
    }

    private fun updateStatus() {
        val cfg = KeepAliveStore.getConfig(requireContext())
        tvStatus.text = if (cfg.enabled) "Keep-Alive is running"
                        else "Keep-Alive is stopped"
    }

    // ---------- SIM ----------

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
            actSim.setText("No SIM", false); actSim.isEnabled = false; return
        }
        actSim.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels))
        val idx = subs.indexOfFirst { it.subscriptionId == selectedSubId }.takeIf { it >= 0 } ?: 0
        actSim.setText(labels[idx], false)
        selectedSubId = subs[idx].subscriptionId
    }

    private fun refreshSimEnabled() {
        val alt = switchAltSim.isChecked
        actSim.isEnabled = !alt
        actSim.alpha = if (alt) 0.5f else 1f
    }

    // ---------- Log ----------

    private fun renderLog(list: List<KeepAliveActivity>) {
        llLog.removeAllViews()
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        list.take(50).forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_keep_alive_log, llLog, false)
            val color = when (entry.result) {
                "OK"          -> 0xFF10B981.toInt()
                "DEFERRED"    -> 0xFFF59E0B.toInt()
                "NO RESPONSE" -> 0xFFEF4444.toInt()
                else          -> 0xFFEF4444.toInt()
            }
            row.findViewById<TextView>(R.id.tvLogResult).apply {
                text = entry.result; setTextColor(color)
            }
            row.findViewById<TextView>(R.id.tvLogSim).text = entry.simLabel
            row.findViewById<TextView>(R.id.tvLogTime).text = fmt.format(Date(entry.ts))
            row.findViewById<TextView>(R.id.tvLogResponse).text =
                entry.response.ifBlank { "(empty)" }
            llLog.addView(row)
        }
    }

    // ---------- System helpers ----------

    private fun requestBatteryExemption(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            UssdLog.append("⚠️ Battery exemption unavailable: ${e.message}")
        }
    }

    private fun requestExactAlarm(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(ctx, "Not needed on this Android version", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            UssdLog.append("⚠️ Exact alarm request failed: ${e.message}")
        }
    }
}