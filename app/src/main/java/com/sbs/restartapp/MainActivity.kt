package com.sbs.restartapp

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sbs.restartapp.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var countDownTimer: android.os.CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("reboot_prefs", Context.MODE_PRIVATE)

        setupModeSelection()
        setupStatusCheck()
        setupRebootActions()
        setupDayButtons()
        setupScheduling()

        startAutoRebootCountdown()
    }

    override fun onResume() {
        super.onResume()
        setupStatusCheck()
    }

    private fun setupModeSelection() {
        val savedMode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
        if (savedMode == "adb") {
            binding.rbAdb.isChecked = true
        } else {
            binding.rbAccessibility.isChecked = true
        }

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbAdb) "adb" else "accessibility"
            prefs.edit().putString("selected_mode", mode).apply()
        }
    }

    private fun setupStatusCheck() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isOwner = isDeviceOwner()

        // 1. Update Accessibility Status & Button
        if (isAccessibilityEnabled) {
            binding.indicatorAccessibility.setBackgroundResource(R.drawable.status_indicator_green)
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_granted)
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.accent_green))

            binding.btnRebootAccessibility.text = getString(R.string.btn_reboot_accessibility)
            binding.btnRebootAccessibility.setBackgroundColor(getColor(R.color.accent_green))
            binding.btnRebootAccessibility.setTextColor(getColor(R.color.bg_dark))
        } else {
            binding.indicatorAccessibility.setBackgroundResource(R.drawable.status_indicator_red)
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_denied)
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.accent_red))

            binding.btnRebootAccessibility.text = getString(R.string.btn_reboot_accessibility_need)
            binding.btnRebootAccessibility.setBackgroundColor(getColor(R.color.primary))
            binding.btnRebootAccessibility.setTextColor(getColor(R.color.white))
        }

        // 2. Update ADB Status & Button
        if (isOwner) {
            binding.indicatorAdb.setBackgroundResource(R.drawable.status_indicator_green)
            binding.tvAdbStatus.text = getString(R.string.dpm_active)
            binding.tvAdbStatus.setTextColor(getColor(R.color.accent_green))

            binding.btnRebootDpm.text = getString(R.string.btn_reboot_dpm)
            binding.btnRebootDpm.setBackgroundColor(getColor(R.color.accent_green))
            binding.btnRebootDpm.setTextColor(getColor(R.color.bg_dark))
        } else {
            binding.indicatorAdb.setBackgroundResource(R.drawable.status_indicator_red)
            binding.tvAdbStatus.text = getString(R.string.dpm_inactive)
            binding.tvAdbStatus.setTextColor(getColor(R.color.accent_red))

            binding.btnRebootDpm.text = getString(R.string.btn_reboot_dpm_need)
            binding.btnRebootDpm.setBackgroundColor(getColor(R.color.primary))
            binding.btnRebootDpm.setTextColor(getColor(R.color.white))
        }
    }

    private fun setupRebootActions() {
        binding.layoutAccessibilityStatus.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.layoutAdbStatus.setOnClickListener {
            copyAdbCommand()
        }

        binding.btnRebootAccessibility.setOnClickListener {
            val isAccessibilityEnabled = isAccessibilityServiceEnabled()
            if (isAccessibilityEnabled) {
                val service = RebootAccessibilityService.instance
                if (service != null) {
                    Toast.makeText(this, getString(R.string.toast_rebooting), Toast.LENGTH_SHORT).show()
                    service.triggerPowerMenu()
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_accessibility), Toast.LENGTH_LONG).show()
                }
            } else {
                openAccessibilitySettings()
            }
        }

        binding.btnRebootDpm.setOnClickListener {
            val isOwner = isDeviceOwner()
            if (isOwner) {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
                Toast.makeText(this, getString(R.string.toast_rebooting), Toast.LENGTH_SHORT).show()
                try {
                    dpm.reboot(adminComponent)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.toast_failed_reboot), Toast.LENGTH_LONG).show()
                }
            } else {
                copyAdbCommand()
            }
        }
    }

    private fun copyAdbCommand() {
        val adbCommand = "adb shell dpm set-device-owner com.sbs.restartapp/.MyDeviceAdminReceiver"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ADB_Command", adbCommand)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "PC에 연결하여 ADB 권한을 획득해야 합니다. 명령어가 클립보드에 복사되었습니다.", Toast.LENGTH_LONG).show()
    }

    private fun updateDayButtonStyle(button: com.google.android.material.button.MaterialButton, isChecked: Boolean) {
        if (isChecked) {
            button.setBackgroundColor(getColor(R.color.light_green))
            button.setTextColor(getColor(R.color.black))
            button.strokeColor = ColorStateList.valueOf(getColor(R.color.light_green))
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(getColor(R.color.text_sub))
            button.strokeColor = ColorStateList.valueOf(getColor(R.color.divider))
        }
    }

    private fun setupDayButtons() {
        val dayButtons = mapOf(
            "1" to binding.btnDaySun,
            "2" to binding.btnDayMon,
            "3" to binding.btnDayTue,
            "4" to binding.btnDayWed,
            "5" to binding.btnDayThu,
            "6" to binding.btnDayFri,
            "7" to binding.btnDaySat
        )

        val selectedDays = prefs.getStringSet("selected_days", setOf("1", "2", "3", "4", "5", "6", "7")) ?: emptySet()

        for ((dayStr, btn) in dayButtons) {
            val isSelected = selectedDays.contains(dayStr)
            btn.isChecked = isSelected
            updateDayButtonStyle(btn, isSelected)

            btn.setOnClickListener {
                val currentSelected = prefs.getStringSet("selected_days", setOf("1", "2", "3", "4", "5", "6", "7"))?.toMutableSet() ?: mutableSetOf()
                val newCheckedState = btn.isChecked
                if (newCheckedState) {
                    currentSelected.add(dayStr)
                } else {
                    currentSelected.remove(dayStr)
                }
                prefs.edit().putStringSet("selected_days", currentSelected).apply()
                updateDayButtonStyle(btn, newCheckedState)

                // Reschedule if reservation switch is enabled
                if (binding.switchSchedule.isChecked) {
                    RebootScheduler.scheduleReboot(this)
                    val h = prefs.getInt("hour", 3)
                    val m = prefs.getInt("minute", 0)
                    updateScheduleInfoText(true, h, m)
                }
            }
        }
    }

    private fun setupScheduling() {
        val enabled = prefs.getBoolean("enabled", false)
        val hour = prefs.getInt("hour", 3)
        val minute = prefs.getInt("minute", 0)

        binding.switchSchedule.isChecked = enabled
        updateTimeText(hour, minute)
        updateScheduleInfoText(enabled, hour, minute)

        binding.tvSelectedTime.setOnClickListener {
            val currentHour = prefs.getInt("hour", 3)
            val currentMinute = prefs.getInt("minute", 0)

            TimePickerDialog(this, { _, h, m ->
                prefs.edit().apply {
                    putInt("hour", h)
                    putInt("minute", m)
                    apply()
                }
                updateTimeText(h, m)

                if (binding.switchSchedule.isChecked) {
                    RebootScheduler.scheduleReboot(this)
                    updateScheduleInfoText(true, h, m)
                }
            }, currentHour, currentMinute, true).show()
        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            val h = prefs.getInt("hour", 3)
            val m = prefs.getInt("minute", 0)

            prefs.edit().putBoolean("enabled", isChecked).apply()

            if (isChecked) {
                RebootScheduler.scheduleReboot(this)
                updateScheduleInfoText(true, h, m)
                val daysText = getSelectedDaysText()
                Toast.makeText(this, String.format(Locale.getDefault(), getString(R.string.toast_schedule_set), daysText, h, m), Toast.LENGTH_SHORT).show()
            } else {
                RebootScheduler.cancelReboot(this)
                updateScheduleInfoText(false, h, m)
                Toast.makeText(this, getString(R.string.toast_schedule_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedDaysText(): String {
        val selectedDays = prefs.getStringSet("selected_days", setOf("1", "2", "3", "4", "5", "6", "7")) ?: emptySet()
        if (selectedDays.size == 7) {
            return "매일"
        }
        if (selectedDays.isEmpty()) {
            return "(요일 선택 없음)"
        }

        val dayNames = mapOf(
            "1" to "일",
            "2" to "월",
            "3" to "화",
            "4" to "수",
            "5" to "목",
            "6" to "금",
            "7" to "토"
        )

        val sortedDays = selectedDays.sortedBy { it.toInt() }
        return sortedDays.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    private fun updateTimeText(hour: Int, minute: Int) {
        binding.tvSelectedTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    private fun updateScheduleInfoText(enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) {
            val daysText = getSelectedDaysText()
            binding.tvScheduleInfo.text = String.format(Locale.getDefault(), getString(R.string.schedule_info_enabled), daysText, hour, minute)
            binding.tvScheduleInfo.setTextColor(getColor(R.color.secondary))
        } else {
            binding.tvScheduleInfo.text = getString(R.string.schedule_info_disabled)
            binding.tvScheduleInfo.setTextColor(getColor(R.color.text_sub))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return RebootAccessibilityService.instance != null
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDeviceOwner(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(packageName)
    }

    private fun startAutoRebootCountdown() {
        val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"

        val dialogView = layoutInflater.inflate(R.layout.dialog_countdown, null)
        val tvNumber = dialogView.findViewById<android.widget.TextView>(R.id.tvCountdownNumber)
        tvNumber.text = "5"
        tvNumber.setTextColor(getColor(R.color.white))

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setView(dialogView)
        builder.setCancelable(false)
        builder.setNegativeButton("취소") { dialog, _ ->
            countDownTimer?.cancel()
            dialog.dismiss()
            Toast.makeText(this, "자동 재시작이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }

        val dialog = builder.create()
        dialog.show()

        // Remove gray card box background
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        // Set cancel button text color to white
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.white))

        countDownTimer = object : android.os.CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000) + 1
                tvNumber.text = secondsRemaining.toString()
            }

            override fun onFinish() {
                dialog.dismiss()
                if (mode == "adb") {
                    if (isDeviceOwner()) {
                        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val adminComponent = ComponentName(this@MainActivity, MyDeviceAdminReceiver::class.java)
                        Toast.makeText(this@MainActivity, "다시 시작(ADB)을 실행합니다.", Toast.LENGTH_SHORT).show()
                        try {
                            dpm.reboot(adminComponent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_failed_reboot), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "ADB 권한이 없어 자동 재시작을 실행할 수 없습니다.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val service = RebootAccessibilityService.instance
                    if (service != null) {
                        Toast.makeText(this@MainActivity, "다시 시작(접근성)을 실행합니다.", Toast.LENGTH_SHORT).show()
                        service.triggerPowerMenu()
                    } else {
                        Toast.makeText(this@MainActivity, "접근성 권한이 비활성화되어 자동 재시작을 실행할 수 없습니다.", Toast.LENGTH_LONG).show()
                        openAccessibilitySettings()
                    }
                }
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
