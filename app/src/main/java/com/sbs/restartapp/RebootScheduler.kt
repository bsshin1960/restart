package com.sbs.restartapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object RebootScheduler {
    private const val TAG = "RebootScheduler"
    private const val ALARM_REQ_CODE = 999

    fun scheduleReboot(context: Context) {
        val prefs = context.getSharedPreferences("reboot_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        if (!enabled) {
            cancelReboot(context)
            return
        }

        val hour = prefs.getInt("hour", 3)
        val minute = prefs.getInt("minute", 0)
        // Default to all days selected if empty
        val selectedDaysStr = prefs.getStringSet("selected_days", setOf("1", "2", "3", "4", "5", "6", "7")) ?: emptySet()
        val selectedDays = selectedDaysStr.mapNotNull { it.toIntOrNull() }.toSet()

        if (selectedDays.isEmpty()) {
            cancelReboot(context)
            return
        }

        val triggerTime = getNextTriggerTime(hour, minute, selectedDays)
        if (triggerTime == 0L) {
            cancelReboot(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RebootBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetCalendar = Calendar.getInstance().apply {
            timeInMillis = triggerTime
        }
        Log.d(TAG, "Scheduling reboot at: ${targetCalendar.time} ($triggerTime)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun getNextTriggerTime(hour: Int, minute: Int, selectedDays: Set<Int>): Long {
        val now = Calendar.getInstance()
        var closestTrigger: Calendar? = null

        // Check next 7 days starting from today (0 = today, 1 = tomorrow, ..., 7 = next week same day)
        for (i in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (selectedDays.contains(dayOfWeek)) {
                if (candidate.timeInMillis > now.timeInMillis) {
                    if (closestTrigger == null || candidate.timeInMillis < closestTrigger.timeInMillis) {
                        closestTrigger = candidate
                    }
                }
            }
        }
        return closestTrigger?.timeInMillis ?: 0L
    }

    fun cancelReboot(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RebootBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled scheduled reboot")
    }
}
