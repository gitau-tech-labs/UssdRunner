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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RunnerFragment : Fragment() {

    private lateinit var etUssdInit: TextInputEditText
    private lateinit var etSteps: TextInputEditText
    private lateinit var actSim: AutoCompleteTextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnClearLog: MaterialButton
    private lateinit var tvLog: TextView
    private lateinit var svLog: NestedScrollView

    private var subs: List<SubscriptionInfo> = emptyList()
    private var selectedSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_runner, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        etUssdInit  = v.findViewById(R.id.etUssdInit)
        etSteps     = v.findViewById(R.id.etSteps)
        actSim      = v.findViewById(R.id.actSim)
        btnStart    = v.findViewById(R.id.btnStart)
        btnClearLog = v.findViewById(R.id.btnClearLog)
        tvLog       = v.findViewById(R.id.tvLog)
        svLog       = v.findViewById(R.id.svLog)

        UssdLog.lines.observe(viewLifecycleOwner) { lines ->
            tvLog.text = if (lines.isEmpty()) getString(R.string.log_empty)
                         else lines.joinToString("\n")
            svLog.post { svLog.fullScroll(View.FOCUS_DOWN) }
        }
        btnClearLog.setOnClickListener { UssdLog.clear() }

        // Prefill initializer from prefs, if any
        val init = AppPrefs.getInitializer(requireContext())
        if (init.isNotBlank()) etUssdInit.setText(init)

        loadSims()
        btnStart.setOnClickListener { onStartClicked() }
    }

    private fun loadSims() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            actSim.setText("Grant phone permission", false); actSim.isEnabled = false; return
        }
        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        subs = try { sm.activeSubscriptionInfoList ?: emptyList() }
               catch (_: SecurityException) { emptyList() }

        val labels = subs.map { s ->
            val slot = s.simSlotIndex + 1
            val name = s.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM $slot"
            "$name  ·  SIM $slot"
        }
        if (labels.isEmpty()) {
            actSim.setText("No SIM detected", false); actSim.isEnabled = false
            selectedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID; return
        }
        actSim.isEnabled = true
        actSim.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels))

        val saved = AppPrefs.getSystemSubId(ctx)
        val idx = subs.indexOfFirst { it.subscriptionId == saved }.takeIf { it >= 0 } ?: 0
        actSim.setText(labels[idx], false)
        selectedSubId = subs[idx].subscriptionId

        actSim.setOnItemClickListener { _, _, position, _ ->
            if (position in subs.indices) {
                selectedSubId = subs[position].subscriptionId
                AppPrefs.setSystemSubId(requireContext(), selectedSubId)
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "${requireContext().packageName}/${UssdAccessibilityService::class.java.name}"
        val flat = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun showAccessibilityDialog() {
        UssdLog.append("⚠️ Accessibility service not enabled")
        AlertDialog.Builder(requireContext())
            .setTitle("Accessibility Service Required")
            .setMessage("Settings → Accessibility → Installed apps → USSD Runner → ON")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onStartClicked() {
        val init = etUssdInit.text?.toString()?.trim().orEmpty()
        val stepsRaw = etSteps.text?.toString()?.trim().orEmpty()

        if (init.isEmpty()) { etUssdInit.error = "Enter the USSD initializer"; return }
        if (selectedSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Toast.makeText(context, "Select a SIM first", Toast.LENGTH_SHORT).show(); return
        }
        if (!isAccessibilityEnabled()) { showAccessibilityDialog(); return }

        AppPrefs.setInitializer(requireContext(), init)
        AppPrefs.setSystemSubId(requireContext(), selectedSubId)

        val steps = stepsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        UssdStepStore.begin(steps)

        UssdLog.append("🚀 Manual session · subId=$selectedSubId")
        UssdLog.append("📤 Opening dialer: $init")
        if (steps.isNotEmpty()) UssdLog.append("⌨️ Queued: ${steps.joinToString(" → ")}")

        dialUssd(init, selectedSubId)
    }

    private fun dialUssd(code: String, subId: Int) {
        val encoded = code.replace("#", "%23").replace("*", "%2A")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$encoded")).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra("com.android.phone.extra.slot", subId)
                putExtra("simSlot", subId)
                putExtra("subscription", subId)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) }
        catch (e: Exception) { UssdLog.append("❌ Dialer error: ${e.message}") }
    }
}
