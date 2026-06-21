package com.example.simpalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object SimpAlarmFallbackNotifier {
    private const val CHANNEL_ID = "simp_alarm_fallback_channel_v1"
    private const val REQUEST_CODE_OPEN = 3101
    private const val REQUEST_CODE_DISMISS = 3102

    fun show(
        context: Context,
        sender: String,
        message: String,
        matchedTarget: String,
        sourcePackage: String,
        sourceAppLabel: String,
        shouldDisableTarget: Boolean,
        returnToAppOnDismiss: Boolean,
        alarmPresentationMode: AlarmPresentationMode
    ) {
        ensureChannel(context)
        val openIntent = Intent(context, AlarmDismissActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SimpAlarmService.EXTRA_SENDER_NAME, sender)
            putExtra(SimpAlarmService.EXTRA_MATCHED_TARGET, matchedTarget)
            putExtra(SimpAlarmService.EXTRA_MESSAGE_TEXT, message)
            putExtra(SimpAlarmService.EXTRA_SOURCE_PACKAGE, sourcePackage)
            putExtra(SimpAlarmService.EXTRA_SOURCE_APP_LABEL, sourceAppLabel)
            putExtra(SimpAlarmService.EXTRA_SHOULD_DISABLE_TARGET, shouldDisableTarget)
            putExtra(SimpAlarmService.EXTRA_RETURN_TO_APP_ON_DISMISS, returnToAppOnDismiss)
            putExtra(SimpAlarmService.EXTRA_START_ALARM_FROM_ACTIVITY, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            backgroundActivityStartOptions()
        )
        val dismissPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_DISMISS,
            Intent(context, SimpAlarmService::class.java).apply {
                action = SimpAlarmService.ACTION_DISMISS_ALARM
                putExtra(SimpAlarmService.EXTRA_RETURN_TO_APP_ON_DISMISS, returnToAppOnDismiss)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appPrefix = sourceAppLabel.ifBlank { "監聽 App" }
        val title = if (sender.isBlank()) "Simp Alarm" else "$sender 從 $appPrefix 傳訊息了"
        val text = message.ifBlank { "系統限制背景啟動，點一下開啟鬧鐘。" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "關閉鬧鐘", dismissPendingIntent)
            .apply {
                if (alarmPresentationMode == AlarmPresentationMode.FullScreen) {
                    setFullScreenIntent(openPendingIntent, true)
                }
            }
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(SimpAlarmService.FALLBACK_NOTIFICATION_ID, notification)
        }.onFailure {
            SimpEventLog.record(context, "備援鬧鐘通知顯示失敗：${it.javaClass.simpleName}")
        }
    }

    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(SimpAlarmService.FALLBACK_NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Simp Alarm 備援通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "背景啟動鬧鐘受限時顯示的備援通知。"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun backgroundActivityStartOptions() =
        if (Build.VERSION.SDK_INT >= 35) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()
        } else {
            null
        }
}
