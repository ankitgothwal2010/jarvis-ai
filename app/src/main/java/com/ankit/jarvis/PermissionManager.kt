/*
 * Copyright ©️ 2026 Ankit Gothwal.
 * All rights reserved, subject to applicable third-party license terms.
 *
 * Project: JARVIS AI
 * Developer: Ankit Gothwal
 * Application ID: com.ankit.jarvis
 */
package com.ankit.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles runtime Android permission checks, user disclosures, and requests.
 * Specifically governs microphone access (RECORD_AUDIO) with transparent explanations.
 */
class PermissionManager(private val activity: Activity) {

    companion object {
        const val REQUEST_RECORD_AUDIO = 1001

        fun hasAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if microphone permission is granted; if not, displays a transparent rationale
     * dialog explaining that audio is processed locally on-device and requests permission.
     */
    fun ensureMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (hasAudioPermission(activity)) {
            onGranted()
            return
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(activity, R.style.Theme_JarvisAI_Dialog)
                .setTitle(R.string.perm_mic_title)
                .setMessage(R.string.perm_mic_rationale)
                .setPositiveButton(R.string.btn_grant) { _, _ ->
                    requestAudioPermission()
                }
                .setNegativeButton(R.string.btn_use_text_only) { dialog, _ ->
                    dialog.dismiss()
                    onDenied()
                }
                .show()
        } else {
            requestAudioPermission()
        }
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    /**
     * Handles the callback from Activity.onRequestPermissionsResult.
     */
    fun handlePermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onPermanentlyDenied: () -> Unit
    ) {
        if (requestCode != REQUEST_RECORD_AUDIO) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
            if (!showRationale) {
                showSettingsRedirectDialog()
            }
            onPermanentlyDenied()
        }
    }

    private fun showSettingsRedirectDialog() {
        AlertDialog.Builder(activity, R.style.Theme_JarvisAI_Dialog)
            .setTitle(R.string.perm_mic_denied_title)
            .setMessage(R.string.perm_mic_settings_guide)
            .setPositiveButton(R.string.btn_open_settings) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
