package com.example.simpalarm

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

class SimpNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        SimpEventLog.record(this, "通知監聽服務已連線。")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SimpEventLog.record(this, "通知監聽服務已中斷，請重新啟用通知監聽。")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, SimpNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SimpTargetManager.isPackageMonitored(this, sbn.packageName)) {
            if (SimpTargetManager.isSupportedPackage(sbn.packageName)) {
                SimpEventLog.record(
                    this,
                    "收到 ${SimpTargetManager.appLabelForPackage(sbn.packageName)} 通知，但這個 App 未勾選監聽。"
                )
            }
            return
        }

        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()
        val appLabel = SimpTargetManager.appLabelForPackage(sbn.packageName)
        if (sender.isEmpty()) {
            SimpEventLog.record(this, "收到 $appLabel 通知，但通知標題是空的。")
            return
        }

        SimpEventLog.record(this, "收到 $appLabel 通知：$sender")

        val targets = SimpTargetManager.getEnabledTargets(this)
        val matchedTarget = targets.firstOrNull { target ->
            target.matchesSender(sender)
        } ?: run {
            SimpEventLog.record(this, "未觸發：$sender 沒有符合已開啟的監聽對象。")
            return
        }

        val message = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        val intent = Intent(this, SimpAlarmService::class.java).apply {
            putExtra(SimpAlarmService.EXTRA_SENDER_NAME, sender)
            putExtra(SimpAlarmService.EXTRA_MATCHED_TARGET, matchedTarget.id)
            putExtra(SimpAlarmService.EXTRA_MESSAGE_TEXT, message)
            putExtra(SimpAlarmService.EXTRA_SOURCE_PACKAGE, sbn.packageName)
            putExtra(SimpAlarmService.EXTRA_SOURCE_APP_LABEL, appLabel)
            putExtra(SimpAlarmService.EXTRA_SHOULD_DISABLE_TARGET, shouldDisableAfterTrigger(matchedTarget))
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }.onSuccess {
            SimpEventLog.recordTrigger(this, matchedTarget, appLabel, sender, message)
            SimpEventLog.record(this, "已觸發鬧鐘：$sender 符合 ${matchedTarget.displayName}。")
        }.onFailure { error ->
            SimpEventLog.record(this, "鬧鐘服務啟動失敗：${error.javaClass.simpleName}")
        }
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    private fun shouldDisableAfterTrigger(target: SimpTarget): Boolean {
        return SimpTargetManager.getTriggerMode(this) == TriggerMode.Once &&
            !target.continuousOverride
    }

}
