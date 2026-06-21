package com.example.simpalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat

class SimpListenerHeartbeatService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!isNotificationListenerEnabled()) {
                SimpEventLog.record(this@SimpListenerHeartbeatService, "通知監聽權限未啟用，保活服務已停止。")
                stopSelf()
                return
            }
            requestListenerRebind()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isNotificationListenerEnabled()) {
            SimpEventLog.record(this, "通知監聽權限未啟用，保活服務未啟動。")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (!started) {
            started = true
            SimpEventLog.record(this, "通知監聽保活服務已啟動。")
            handler.removeCallbacks(heartbeatRunnable)
            heartbeatRunnable.run()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeatRunnable)
        started = false
        super.onDestroy()
    }

    private fun requestListenerRebind() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, SimpNotificationListener::class.java)
            )
            SimpEventLog.record(this, "通知監聽保活已要求重新連線。")
        }.onFailure { error ->
            SimpEventLog.record(this, "通知監聽保活重新連線失敗：${error.javaClass.simpleName}")
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Simp Alarm 監聽保活中")
            .setContentText("正在維持通知監聽連線，避免錯過符合的訊息。")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Simp Alarm 監聽保活",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "維持通知監聽服務連線的低頻保活通知。"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val componentName = ComponentName(this, SimpNotificationListener::class.java)
        return enabledListeners.split(':').any {
            it.equals(componentName.flattenToString(), ignoreCase = true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "simp_listener_heartbeat_channel_v1"
        private const val NOTIFICATION_ID = 1003
        private const val OPEN_APP_REQUEST_CODE = 3003
        private const val HEARTBEAT_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, SimpListenerHeartbeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SimpListenerHeartbeatService::class.java))
        }
    }
}
