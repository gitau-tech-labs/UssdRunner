package com.example.ussdrunner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* fragments re-check on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Init singletons once, at app start.
        MpesaStore.init(this)
        ProductStore.init(this)
        AutomationEngine.attach(this)

        ensurePermissions()

        if (savedInstanceState == null) {
            swap(RunnerFragment())
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val frag: Fragment = when (item.itemId) {
                R.id.nav_runner   -> RunnerFragment()
                R.id.nav_inbox    -> InboxFragment()
                R.id.nav_products -> ProductsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            swap(frag)
            true
        }
    }

    private fun swap(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHost, f)
            .commit()
    }

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        fun check(p: String) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed += p
        }
        check(Manifest.permission.CALL_PHONE)
        check(Manifest.permission.READ_PHONE_STATE)
        check(Manifest.permission.READ_SMS)
        check(Manifest.permission.RECEIVE_SMS)
        check(Manifest.permission.SEND_SMS)
        if (needed.isNotEmpty()) permsLauncher.launch(needed.toTypedArray())
    }
}
