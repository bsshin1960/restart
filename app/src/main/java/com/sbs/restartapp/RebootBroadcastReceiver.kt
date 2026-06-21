package com.sbs.restartapp

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class RebootBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "RebootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Reboot alarm received! Attempting reboot...")
        Toast.makeText(context, "자동 예약 재시작을 실행합니다...", Toast.LENGTH_LONG).show()

        val prefs = context.getSharedPreferences("reboot_prefs", Context.MODE_PRIVATE)
        val selectedMode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        if (selectedMode == "adb") {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                Log.d(TAG, "Attempting ADB (Device Owner) reboot...")
                try {
                    // Schedule next weekly alarm first
                    RebootScheduler.scheduleReboot(context)
                    dpm.reboot(adminComponent)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "ADB reboot failed: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "ADB mode selected but app is not Device Owner.")
            }
        } else {
            Log.d(TAG, "Attempting Accessibility reboot...")
            val service = RebootAccessibilityService.instance
            if (service != null) {
                try {
                    // Schedule next weekly alarm first
                    RebootScheduler.scheduleReboot(context)
                    service.triggerPowerMenu()
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Accessibility reboot failed: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "Accessibility service is not running. Cannot perform scheduled reboot.")
            }
        }

        // Fallback: If reboot did not execute, ensure the next alarm is queued
        RebootScheduler.scheduleReboot(context)
    }
}
