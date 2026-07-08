package com.frustradar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 1 shell: hosts the consent + runtime-permission gate. No sensor capture, no inference,
 * no backend calls happen in this phase — capture starts only after consent is granted and the
 * required runtime permissions are approved (privacy gate, 08_ANDROID.md §2.1 / A-D5).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val requiredPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_consent).setOnClickListener { showConsentDialog() }
        updateStatus()
    }

    private fun showConsentDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.consent_title)
            .setMessage(R.string.consent_message)
            .setPositiveButton(R.string.consent_accept) { _, _ ->
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_CONSENT, true)
                    .apply()
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            }
            .setNegativeButton(R.string.consent_decline, null)
            .show()
    }

    private fun consentGranted(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSENT, false)

    private fun permissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateStatus() {
        val ready = consentGranted() && permissionsGranted()
        findViewById<TextView>(R.id.status_text).setText(
            if (ready) R.string.status_ready else R.string.status_needs_consent
        )
    }

    private companion object {
        const val PREFS = "consent"
        const val KEY_CONSENT = "consent_granted"
    }
}
