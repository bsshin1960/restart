package com.sbs.restartapp

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

        setupStatusCheck()
        setupRebootActions()
        setupScheduling()
        setupAdbGuide()

        startAutoRebootCountdown()
    }

    override fun onResume() {
        super.onResume()
        setupStatusCheck()
    }

    private fun setupStatusCheck() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isOwner = isDeviceOwner()

        // Update Accessibility Status UI
        if (isAccessibilityEnabled) {
            binding.indicatorAccessibility.setBackgroundResource(R.drawable.status_indicator_green)
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_granted)
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.accent_green))
        } else {
            binding.indicatorAccessibility.setBackgroundResource(R.drawable.status_indicator_red)
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_denied)
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.accent_red))
        }

        // Update Device Owner Status UI
        if (isOwner) {
            binding.indicatorDPM.setBackgroundResource(R.drawable.status_indicator_green)
            binding.tvDpmStatus.text = getString(R.string.dpm_active)
            binding.tvDpmStatus.setTextColor(getColor(R.color.accent_green))
        } else {
            binding.indicatorDPM.setBackgroundResource(R.drawable.status_indicator_red)
            binding.tvDpmStatus.text = getString(R.string.dpm_inactive)
            binding.tvDpmStatus.setTextColor(getColor(R.color.accent_red))
        }
    }

    private fun setupRebootActions() {
        binding.layoutAccessibilityStatus.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnRebootAccessibility.setOnClickListener {
            val service = RebootAccessibilityService.instance
            if (service != null) {
                Toast.makeText(this, getString(R.string.toast_rebooting), Toast.LENGTH_SHORT).show()
                service.triggerPowerMenu()
            } else {
                Toast.makeText(this, getString(R.string.toast_no_accessibility), Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
            }
        }

        binding.btnRebootDpm.setOnClickListener {
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
                    RebootScheduler.scheduleReboot(this, h, m)
                    updateScheduleInfoText(true, h, m)
                }
            }, currentHour, currentMinute, true).show()
        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            val h = prefs.getInt("hour", 3)
            val m = prefs.getInt("minute", 0)
            
            if (isChecked) {
                RebootScheduler.scheduleReboot(this, h, m)
                updateScheduleInfoText(true, h, m)
                Toast.makeText(this, String.format(Locale.getDefault(), getString(R.string.toast_schedule_set), h, m), Toast.LENGTH_SHORT).show()
            } else {
                RebootScheduler.cancelReboot(this)
                updateScheduleInfoText(false, h, m)
                Toast.makeText(this, getString(R.string.toast_schedule_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAdbGuide() {
        binding.btnCopyCmd.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ADB_Command", binding.tvAdbCommand.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTimeText(hour: Int, minute: Int) {
        binding.tvSelectedTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    private fun updateScheduleInfoText(enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) {
            binding.tvScheduleInfo.text = String.format(Locale.getDefault(), getString(R.string.schedule_info_enabled), hour, minute)
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
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("자동 재시작 실행")
        builder.setMessage("5초 후 다시 시작(접근성)을 실행합니다...")
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
                dialog.setMessage("${secondsRemaining}초 후 다시 시작(접근성)을 실행합니다...")
            }

            override fun onFinish() {
                dialog.dismiss()
                val service = RebootAccessibilityService.instance
                if (service != null) {
                    Toast.makeText(this@MainActivity, "다시 시작을 실행합니다.", Toast.LENGTH_SHORT).show()
                    service.triggerPowerMenu()
                } else {
                    Toast.makeText(this@MainActivity, "접근성 권한이 비활성화되어 자동 재시작을 실행할 수 없습니다.", Toast.LENGTH_LONG).show()
                    openAccessibilitySettings()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
