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
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var etUssdInit: TextInputEditText
    private lateinit var etSteps: TextInputEditText
    private lateinit var actSim: AutoCompleteTextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnClearLog: MaterialButton
    private lateinit var tvLog: TextView
    private lateinit var svLog: NestedScrollView

    private var subs: List<SubscriptionInfo> = emptyList()
    private var selectedSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> loadSims() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUssdInit  = findViewById(R.id.etUssdInit)
        etSteps     = findViewById(R.id.etSteps)
        actSim      = findViewById(R.id.actSim)
        btnStart    = findViewById(R.id.btnStart)
        btnClearLog = findViewById(R.id.btnClearLog)
        tvLog       = findViewById(R.id.tvLog)
        svLog       = findViewById(R.id.svLog)

        UssdLog.lines.observe(this) { lines ->
            tvLog.text = if (lines.isEmpty()) getString(R.string.log_empty)
                         else lines.joinToString("\n")
            svLog.post { svLog.fullScroll(View.FOCUS_DOWN) }
        }
        btnClearLog.setOnClickListener { UssdLog.clear() }

        ensurePermissions()
        loadSims()

        btnStart.setOnClickListener { onStartClicked() }
    }

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.CALL_PHONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.READ_PHONE_STATE
        if (needed.isNotEmpty()) permsLauncher.launch(needed.toTypedArray())
    }

    private fun loadSims() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            actSim.setText("Grant phone permission to list SIMs", false)
            actSim.isEnabled = false
            return
        }
        val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        subs = try { sm.activeSubscriptionInfoList ?: emptyList() }
               catch (_: SecurityException) { emptyList() }

        val labels = subs.map { s ->
            val slot = s.simSlotIndex + 1
            val name = s.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM $slot"
            "$name  ·  SIM $slot"
        }
        if (labels.isEmpty()) {
            actSim.setText("No SIM detected", false)
            actSim.isEnabled = false
            selectedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
            return
        }
        actSim.isEnabled = true
        actSim.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        actSim.setText(labels.first(), false)
        selectedSubId = subs.first().subscriptionId
        actSim.setOnItemClickListener { _, _, position, _ ->
            if (position in subs.indices) selectedSubId = subs[position].subscriptionId
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${UssdAccessibilityService::class.java.name}"
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun showAccessibilityDialog() {
        UssdLog.append("⚠️ Accessibility service not enabled")
        AlertDialog.Builder(this)
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
            Toast.makeText(this, "Select a SIM first", Toast.LENGTH_SHORT).show(); return
        }
        if (!isAccessibilityEnabled()) { showAccessibilityDialog(); return }

        val steps = stepsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        UssdStepStore.begin(steps)

        UssdLog.append("🚀 Session start · SIM subId=$selectedSubId")
        UssdLog.append("📤 Opening dialer with: $init")
        if (steps.isNotEmpty()) UssdLog.append("⌨️ Queued steps: ${steps.joinToString(" → ")}")

        sendUssdViaDialer(init, selectedSubId)
    }

    /**
     * Hands the USSD code to the dialer via ACTION_CALL. This is the ONLY reliable
     * way to make the standard USSD dialog pop up on screen — the TelephonyManager
     * silent API suppresses it on most phones.
     *
     * The '#' character MUST be URL‑encoded as %23, otherwise everything after it
     * is treated as a URI fragment and never reaches the network.
     */
    private fun sendUssdViaDialer(code: String, subId: Int) {
        val encoded = code.replace("#", "%23").replace("*", "%2A")
        val uri = Uri.parse("tel:$encoded")

        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            // Try every known OEM extra for "use this SIM".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra("com.android.phone.extra.slot", subId)
                putExtra("simSlot", subId)
                putExtra("subscription", subId)
                putExtra("android.telecom.extra.PHONE_ACCOUNT_ID", subId)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            UssdLog.append("📞 Launching dialer…")
            startActivity(intent)
        } catch (e: SecurityException) {
            UssdLog.append("❌ CALL_PHONE permission missing")
            Toast.makeText(this, "Grant phone permission", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            UssdLog.append("❌ Dialer error: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
