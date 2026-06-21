package com.example.simpalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.max

class SimpAlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousMusicVolume: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS_ALARM) {
            SimpEventLog.record(this, "已從通知欄關閉鬧鐘。")
            stopSelf()
            return START_NOT_STICKY
        }

        val sender = intent?.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
        val message = intent?.getStringExtra(EXTRA_MESSAGE_TEXT).orEmpty()
        val matchedTarget = intent?.getStringExtra(EXTRA_MATCHED_TARGET).orEmpty()
        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty()
        val sourceAppLabel = intent?.getStringExtra(EXTRA_SOURCE_APP_LABEL).orEmpty()
        val shouldDisableTarget = intent?.getBooleanExtra(EXTRA_SHOULD_DISABLE_TARGET, false) == true
        val returnToAppOnDismiss = intent?.getBooleanExtra(EXTRA_RETURN_TO_APP_ON_DISMISS, false) == true
        val alarmPresentationMode = SimpTargetManager.getAlarmPresentationMode(this)
        SimpEventLog.record(this, "鬧鐘服務已啟動：${sender.ifBlank { "未知通知" }}")

        if (!isAlarmPlaying) {
            isAlarmPlaying = true
            startForeground(
                NOTIFICATION_ID,
                buildNotification(
                    sender,
                    message,
                    sourcePackage,
                    sourceAppLabel,
                    alarmPresentationMode,
                    returnToAppOnDismiss
                )
            )
            runCatching { maximizeMusicVolume() }
            runCatching { startSound() }
            runCatching { startVibration() }
            if (shouldDisableTarget && matchedTarget.isNotBlank()) {
                SimpTargetManager.markTriggered(this, matchedTarget)
            }
        } else {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(
                    sender,
                    message,
                    sourcePackage,
                    sourceAppLabel,
                    alarmPresentationMode,
                    returnToAppOnDismiss
                )
            )
        }

        if (alarmPresentationMode == AlarmPresentationMode.FullScreen) {
            wakeScreenBriefly()
            openDismissActivity(intent, sender, message)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        restoreMusicVolume()
        isAlarmPlaying = false
        super.onDestroy()
    }

    private fun buildNotification(
        sender: String,
        message: String,
        sourcePackage: String,
        sourceAppLabel: String,
        alarmPresentationMode: AlarmPresentationMode,
        returnToAppOnDismiss: Boolean
    ): Notification {
        ensureNotificationChannel()
        val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SENDER_NAME, sender)
            putExtra(EXTRA_MESSAGE_TEXT, message)
            putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
            putExtra(EXTRA_SOURCE_APP_LABEL, sourceAppLabel)
            putExtra(EXTRA_RETURN_TO_APP_ON_DISMISS, returnToAppOnDismiss)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, SimpAlarmService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            DISMISS_ALARM_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appPrefix = sourceAppLabel.ifBlank { "監聽 App" }
        val title = if (sender.isBlank()) "Simp Alarm" else "$sender 從 $appPrefix 傳訊息了"
        val text = message.ifBlank { "點一下關閉鬧鐘。" }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "關閉鬧鐘",
                stopPendingIntent
            )

        if (alarmPresentationMode == AlarmPresentationMode.FullScreen) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        return builder
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Simp Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "指定 Instagram 通知抵達時顯示的鬧鐘。"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        val powerManager = getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "SimpAlarm:WakeScreen"
        )
        wakeLock.acquire(10_000L)
    }

    private fun maximizeMusicVolume() {
        val audioManager = getSystemService(AudioManager::class.java)
        previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    private fun restoreMusicVolume() {
        val volume = previousMusicVolume ?: return
        getSystemService(AudioManager::class.java)
            .setStreamVolume(AudioManager.STREAM_MUSIC, max(0, volume), 0)
        previousMusicVolume = null
    }

    private fun startSound() {
        val alarmUri = Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@SimpAlarmService, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val pattern = longArrayOf(0, 500, 250, 500, 250, 900)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun openDismissActivity(sourceIntent: Intent?, sender: String, message: String) {
        val activityIntent = Intent(this, AlarmDismissActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SENDER_NAME, sender)
            putExtra(EXTRA_MESSAGE_TEXT, message)
            putExtra(EXTRA_SOURCE_PACKAGE, sourceIntent?.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty())
            putExtra(EXTRA_SOURCE_APP_LABEL, sourceIntent?.getStringExtra(EXTRA_SOURCE_APP_LABEL).orEmpty())
            putExtra(
                EXTRA_RETURN_TO_APP_ON_DISMISS,
                sourceIntent?.getBooleanExtra(EXTRA_RETURN_TO_APP_ON_DISMISS, false) == true
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            DISMISS_ACTIVITY_REQUEST_CODE,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
                pendingIntent.send(this, 0, null, null, null, null, options)
            } else {
                pendingIntent.send()
            }
        } catch (_: PendingIntent.CanceledException) {
            fallbackStartActivity(activityIntent)
        } catch (_: ActivityNotFoundException) {
            fallbackStartActivity(activityIntent)
        } catch (_: SecurityException) {
            fallbackStartActivity(activityIntent)
        } catch (_: RuntimeException) {
            // Background activity launches can be restricted; the full-screen notification remains available.
            fallbackStartActivity(activityIntent)
        }
    }

    private fun fallbackStartActivity(activityIntent: Intent) {
        try {
            startActivity(activityIntent)
        } catch (_: RuntimeException) {
        }
    }

    companion object {
        const val EXTRA_SENDER_NAME = "SENDER_NAME"
        const val EXTRA_MATCHED_TARGET = "MATCHED_TARGET"
        const val EXTRA_MESSAGE_TEXT = "MESSAGE_TEXT"
        const val EXTRA_SOURCE_PACKAGE = "SOURCE_PACKAGE"
        const val EXTRA_SOURCE_APP_LABEL = "SOURCE_APP_LABEL"
        const val EXTRA_SHOULD_DISABLE_TARGET = "SHOULD_DISABLE_TARGET"
        const val EXTRA_RETURN_TO_APP_ON_DISMISS = "RETURN_TO_APP_ON_DISMISS"

        private const val ACTION_DISMISS_ALARM = "com.example.simpalarm.action.DISMISS_ALARM"
        private const val CHANNEL_ID = "simp_alarm_channel_v2"
        private const val NOTIFICATION_ID = 1001
        private const val DISMISS_ACTIVITY_REQUEST_CODE = 2001
        private const val DISMISS_ALARM_REQUEST_CODE = 2002

        @Volatile
        var isAlarmPlaying: Boolean = false
    }
}
