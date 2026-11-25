package com.god.pushnotification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var notificationTitle: EditText
    private lateinit var notificationBody: EditText
    private lateinit var amountNotification: NumberPicker
    private lateinit var timePushNotificationInterval: NumberPicker
    private lateinit var pushNotificationBtn: Button
    private lateinit var cancelPostingBtn: Button
    private lateinit var postingProgress: TextView
    private lateinit var listenerStatus: TextView

    private var postingJob: Job? = null

    private val postNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startPosting()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.post_notification_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        initViews()
        initPickers()
        initPosting()
        initNavigation()
        RetryScheduler.schedulePeriodic(this)
    }

    override fun onResume() {
        super.onResume()
        refreshListenerStatus()
    }

    private fun initViews() {
        notificationTitle = findViewById(R.id.notification_title)
        notificationBody = findViewById(R.id.notification_body)
        amountNotification = findViewById(R.id.amount_notification)
        timePushNotificationInterval = findViewById(R.id.time_push_notification_interval)
        pushNotificationBtn = findViewById(R.id.push_notification)
        cancelPostingBtn = findViewById(R.id.cancel_posting)
        postingProgress = findViewById(R.id.posting_progress)
        listenerStatus = findViewById(R.id.listener_status)
    }

    private fun initPickers() {
        amountNotification.minValue = 1
        amountNotification.maxValue = 100
        amountNotification.value = 1

        timePushNotificationInterval.minValue = 1000
        timePushNotificationInterval.maxValue = 30000
        timePushNotificationInterval.value = 1000
    }

    private fun initPosting() {
        pushNotificationBtn.setOnClickListener { requestPostPermissionIfNeeded() }
        cancelPostingBtn.setOnClickListener { cancelPosting() }
    }

    private fun initNavigation() {
        findViewById<Button>(R.id.open_listener_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.open_services).setOnClickListener {
            startActivity(Intent(this, ServicesActivity::class.java))
        }
        findViewById<Button>(R.id.open_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.retry_now).setOnClickListener {
            RetryScheduler.retryNow(this)
            Toast.makeText(this, getString(R.string.retry_started), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPostPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startPosting()
    }

    private fun startPosting() {
        val title = notificationTitle.text.toString()
        val body = notificationBody.text.toString()
        val amount = amountNotification.value
        val intervalMs = timePushNotificationInterval.value.toLong()
        val notificationManager = NotificationManagerCompat.from(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.push_notification_ic)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        postingJob?.cancel()
        postingJob = lifecycleScope.launch {
            setPosting(true)
            for (i in 0 until amount) {
                builder.setContentText("$title$i")
                notificationManager.notify(i, builder.build())
                postingProgress.text = getString(R.string.posting_progress, i + 1, amount)
                if (i < amount - 1) delay(intervalMs)
            }
            setPosting(false)
            postingProgress.text = getString(R.string.posting_done, amount)
        }
    }

    private fun cancelPosting() {
        postingJob?.cancel()
        postingJob = null
        setPosting(false)
        postingProgress.text = getString(R.string.posting_cancelled)
    }

    private fun setPosting(posting: Boolean) {
        pushNotificationBtn.isEnabled = !posting
        cancelPostingBtn.visibility = if (posting) View.VISIBLE else View.GONE
        if (!posting) postingJob = null
    }

    private fun refreshListenerStatus() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        listenerStatus.text = getString(
            if (enabled) R.string.listener_enabled else R.string.listener_disabled
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "default"
    }
}
