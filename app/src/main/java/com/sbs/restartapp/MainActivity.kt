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
        val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
        updateModeUI(mode)
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
            updateModeUI(mode)
        }
    }

    private fun setupStatusCheck() {
        val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
        updateModeUI(mode)
    }

    private fun updateModeUI(mode: String) {
        val isOwner = isDeviceOwner()
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()

        // Update Day Buttons Styles based on mode color scheme
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
            updateDayButtonStyle(btn, selectedDays.contains(dayStr))
        }

        if (mode == "adb") {
            // ADB Mode UI
            binding.tvStatusLabel.text = getString(R.string.dpm_status_label)
            if (isOwner) {
                binding.indicatorStatus.setBackgroundResource(R.drawable.status_indicator_green)
                binding.tvStatusValue.text = getString(R.string.dpm_active)
                binding.tvStatusValue.setTextColor(getColor(R.color.accent_green))
            } else {
                binding.indicatorStatus.setBackgroundResource(R.drawable.status_indicator_red)
                binding.tvStatusValue.text = getString(R.string.dpm_inactive)
                binding.tvStatusValue.setTextColor(getColor(R.color.accent_red))
            }

            binding.btnReboot.text = getString(R.string.btn_reboot_dpm)
            binding.btnReboot.setBackgroundColor(getColor(R.color.secondary))
            binding.btnReboot.setTextColor(getColor(R.color.bg_dark))
            binding.btnReboot.rippleColor = ColorStateList.valueOf(0x80000000.toInt())
        } else {
            // Accessibility Mode UI
            binding.tvStatusLabel.text = getString(R.string.accessibility_status_label)
            if (isAccessibilityEnabled) {
                binding.indicatorStatus.setBackgroundResource(R.drawable.status_indicator_green)
                binding.tvStatusValue.text = getString(R.string.accessibility_granted)
                binding.tvStatusValue.setTextColor(getColor(R.color.accent_green))
            } else {
                binding.indicatorStatus.setBackgroundResource(R.drawable.status_indicator_red)
                binding.tvStatusValue.text = getString(R.string.accessibility_denied)
                binding.tvStatusValue.setTextColor(getColor(R.color.accent_red))
            }

            binding.btnReboot.text = getString(R.string.btn_reboot_accessibility)
            binding.btnReboot.setBackgroundColor(getColor(R.color.primary))
            binding.btnReboot.setTextColor(getColor(R.color.white))
            binding.btnReboot.rippleColor = ColorStateList.valueOf(0x80FFFFFF.toInt())
        }
    }

    private fun setupRebootActions() {
        binding.layoutStatus.setOnClickListener {
            val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
            if (mode == "accessibility") {
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "ADB 방식은 PC 연결 및 권한 설정 가이드가 적용되어야 활성화됩니다.", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnReboot.setOnClickListener {
            val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
            if (mode == "adb") {
                if (isDeviceOwner()) {
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
                    Toast.makeText(this, getString(R.string.toast_rebooting), Toast.LENGTH_SHORT).show()
                    try {
                        dpm.reboot(adminComponent)
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.toast_failed_reboot), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_permission), Toast.LENGTH_LONG).show()
                }
            } else {
                val service = RebootAccessibilityService.instance
                if (service != null) {
                    Toast.makeText(this, getString(R.string.toast_rebooting), Toast.LENGTH_SHORT).show()
                    service.triggerPowerMenu()
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_accessibility), Toast.LENGTH_LONG).show()
                    openAccessibilitySettings()
                }
            }
        }
    }

    private fun updateDayButtonStyle(button: com.google.android.material.button.MaterialButton, isChecked: Boolean) {
        val mode = prefs.getString("selected_mode", "accessibility") ?: "accessibility"
        if (isChecked) {
            if (mode == "adb") {
                button.setBackgroundColor(getColor(R.color.secondary))
                button.setTextColor(getColor(R.color.bg_dark))
                button.strokeColor = ColorStateList.valueOf(getColor(R.color.secondary))
            } else {
                button.setBackgroundColor(getColor(R.color.primary))
                button.setTextColor(getColor(R.color.white))
                button.strokeColor = ColorStateList.valueOf(getColor(R.color.primary))
            }
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
        val modeText = if (mode == "adb") "ADB 방식" else "접근성 방식"

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("자동 재시작 실행")
        builder.setMessage("5초 후 다시 시작(${modeText})을 실행합니다...")
        builder.setCancelable(false)
        builder.setNegativeButton("취소") { dialog, _ ->
            countDownTimer?.cancel()
            dialog.dismiss()
            Toast.makeText(this, "자동 재시작이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }

        val dialog = builder.create()
        dialog.show()

        countDownTimer = object : android.os.CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000) + 1
                dialog.setMessage("${secondsRemaining}초 후 다시 시작(${modeText})을 실행합니다...")
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
