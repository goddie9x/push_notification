package com.god.pushnotification

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.notifypush.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var otpSwitch: Switch
    private lateinit var otpWarning: TextView
    private lateinit var otpPatternsField: EditText
    private lateinit var historySwitch: Switch
    private lateinit var retentionGroup: RadioGroup
    private lateinit var autoRetrySwitch: Switch
    private lateinit var intervalGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        otpSwitch = findViewById(R.id.settings_otp_switch)
        otpWarning = findViewById(R.id.settings_otp_warning)
        otpPatternsField = findViewById(R.id.settings_otp_patterns_field)
        historySwitch = findViewById(R.id.settings_history_switch)
        retentionGroup = findViewById(R.id.settings_history_retention_group)
        autoRetrySwitch = findViewById(R.id.settings_auto_retry_switch)
        intervalGroup = findViewById(R.id.settings_retry_interval_group)
        otpSwitch.setOnCheckedChangeListener { _, checked -> showOtpWarning(!checked) }
        findViewById<Button>(R.id.settings_save).setOnClickListener { save() }
        loadSettings()
    }

    private fun loadSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = ServiceLocator.settingsStore(this@SettingsActivity).load()
            withContext(Dispatchers.Main) { fill(settings) }
        }
    }

    private fun fill(settings: AppSettings) {
        otpSwitch.isChecked = settings.otpFilterEnabled
        showOtpWarning(!settings.otpFilterEnabled)
        otpPatternsField.setText(settings.otpCustomPatterns.joinToString("\n"))
        historySwitch.isChecked = settings.historyEnabled
        checkRadio(retentionGroup, settings.historyRetentionDays)
        autoRetrySwitch.isChecked = settings.autoRetryEnabled
        checkRadio(intervalGroup, settings.retryIntervalMinutes)
    }

    private fun checkRadio(group: RadioGroup, value: Int) {
        val id = when (group.id) {
            R.id.settings_history_retention_group -> when (value) {
                7 -> R.id.settings_retention_7
                90 -> R.id.settings_retention_90
                else -> R.id.settings_retention_30
            }
            else -> when (value) {
                5 -> R.id.settings_interval_5
                60 -> R.id.settings_interval_60
                else -> R.id.settings_interval_15
            }
        }
        group.check(id)
    }

    private fun showOtpWarning(show: Boolean) {
        otpWarning.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun save() {
        val settings = AppSettings(
            otpFilterEnabled = otpSwitch.isChecked,
            otpCustomPatterns = otpPatternsField.text.toString()
                .lines().map { it.trim() }.filter { it.isNotEmpty() },
            historyEnabled = historySwitch.isChecked,
            historyRetentionDays = radioValue(retentionGroup, 30),
            autoRetryEnabled = autoRetrySwitch.isChecked,
            retryIntervalMinutes = radioValue(intervalGroup, 15),
        )
        lifecycleScope.launch(Dispatchers.IO) {
            ServiceLocator.settingsStore(this@SettingsActivity).save(settings)
            withContext(Dispatchers.Main) {
                RetryScheduler.rescheduleAutoRetry(this@SettingsActivity, settings)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_saved),
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
    }

    private fun radioValue(group: RadioGroup, fallback: Int): Int =
        when (group.checkedRadioButtonId) {
            R.id.settings_retention_7 -> 7
            R.id.settings_retention_30 -> 30
            R.id.settings_retention_90 -> 90
            R.id.settings_interval_5 -> 5
            R.id.settings_interval_15 -> 15
            R.id.settings_interval_60 -> 60
            else -> fallback
        }
}
