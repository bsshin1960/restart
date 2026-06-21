package com.sbs.restartapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot received with action: $action")
        
        if (Intent.ACTION_BOOT_COMPLETED == action ||
            "android.intent.action.QUICKBOOT_POWERON" == action ||
            "com.htc.intent.action.QUICKBOOT_POWERON" == action) {
            
            val prefs = context.getSharedPreferences("reboot_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("enabled", false)
            if (enabled) {
                val hour = prefs.getInt("hour", 3)
                val minute = prefs.getInt("minute", 0)
                Log.d(TAG, "Rescheduling reboot at $hour:$minute after boot")
                RebootScheduler.scheduleReboot(context, hour, minute)
            }
        }
    }
}
