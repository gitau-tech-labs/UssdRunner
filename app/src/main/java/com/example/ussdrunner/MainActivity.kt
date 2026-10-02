package com.example.ussdrunner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var etUssdInit: TextInputEditText
    private lateinit var etSteps: TextInputEditText
    private lateinit var actSim: AutoCompleteTextView
    private lateinit var btnStart: MaterialButton

    private var subs: List<SubscriptionInfo> = emptyList()
    private var selectedSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> loadSims() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUssdInit = findViewById(R.id.etUssdInit)
        etSteps    = findViewById(R.id.etSteps)
        actSim     = findViewById(R.id.actSim)
        btnStart   = findViewById(R.id.btnStart)

        ensurePermissions()
        loadSims()

        btnStart.setOnClickListener { onStartClicked() }
    }

    // ---------- Permissions ----------

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.CALL_PHONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.READ_PHONE_STATE
        if (needed.isNotEmpty()) permsLauncher.launch(needed.toTypedArray())
    }

    // ---------- SIM picker ----------

    private fun loadSims() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            actSim.setText("Grant phone permission to list SIMs", false)
            actSim.isEnabled = false
            return
        }

        val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        subs = try {
            sm.activeSubscriptionInfoList ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

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
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        actSim.setAdapter(adapter)
        actSim.setText(labels.first(), false)
        selectedSubId = subs.first().subscriptionId

        actSim.setOnItemClickListener { _, _, position, _ ->
            if (position in subs.indices) {
                selectedSubId = subs[position].subscriptionId
            }
        }
    }

    // ---------- Accessibility check ----------

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${UssdAccessibilityService::class.java.name}"
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage(
                "To auto‑fill USSD replies, enable the USSD Runner accessibility service.\n\n" +
                "Settings → Accessibility → Installed apps → USSD Runner → ON"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Start flow ----------

    private fun onStartClicked() {
        val init = etUssdInit.text?.toString()?.trim().orEmpty()
        val stepsRaw = etSteps.text?.toString()?.trim().orEmpty()

        if (init.isEmpty()) {
            etUssdInit.error = "Enter the USSD initializer"
            return
        }
        if (selectedSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Toast.makeText(this, "Select a SIM first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityDialog()
            return
        }

        UssdStepStore.begin(
            stepsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
        sendUssd(init, selectedSubId)
    }

    private fun sendUssd(code: String, subId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Android 8.0+ required", Toast.LENGTH_SHORT).show()
            return
        }

        val tm = (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            .createForSubscriptionId(subId)

        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    response: CharSequence?
                ) {
                    // The AccessibilityService drives the rest.
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    failureCode: Int
                ) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "USSD failed (code $failureCode)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
