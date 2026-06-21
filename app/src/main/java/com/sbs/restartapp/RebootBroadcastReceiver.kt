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

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            Log.d(TAG, "Attempting Device Owner reboot...")
            try {
                dpm.reboot(adminComponent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Device Owner reboot failed: ${e.message}", e)
            }
        }

        Log.d(TAG, "Device Owner not available or failed. Attempting Accessibility reboot...")
        val service = RebootAccessibilityService.instance
        if (service != null) {
            try {
                service.triggerPowerMenu()
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility reboot failed: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "Accessibility service is not running. Cannot perform scheduled reboot.")
        }
    }
}
