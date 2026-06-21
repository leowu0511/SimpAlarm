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

        val appLabel = SimpTargetManager.appLabelForPackage(sbn.packageName)
        val extras = sbn.notification.extras
        val senderCandidates = senderCandidates(extras)
        val sender = senderCandidates.firstOrNull().orEmpty()
        if (senderCandidates.isEmpty()) {
            SimpEventLog.record(this, "收到 $appLabel 通知，但通知沒有可比對的寄件者欄位。")
            return
        }

        SimpEventLog.record(this, "收到 $appLabel 通知：${senderCandidates.joinToString(" / ")}")

        val allTargets = SimpTargetManager.getTargetItems(this)
        val targets = allTargets.filter { it.enabled }
        val matchedTarget = targets.firstOrNull { target ->
            target.matchesAnySender(senderCandidates)
        } ?: run {
            SimpEventLog.record(this, buildNoMatchMessage(senderCandidates, allTargets))
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
            SimpEventLog.record(this, "已送出鬧鐘啟動要求：$sender 符合 ${matchedTarget.displayName}。")
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

    private fun senderCandidates(extras: android.os.Bundle): List<String> {
        val titleCandidates = listOf(
            android.app.Notification.EXTRA_TITLE,
            android.app.Notification.EXTRA_TITLE_BIG,
            android.app.Notification.EXTRA_SUB_TEXT,
            android.app.Notification.EXTRA_CONVERSATION_TITLE
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()
        }.flatMap { senderPieces(it, includeFullValue = true) }

        val textCandidates = listOf(
            android.app.Notification.EXTRA_TEXT,
            android.app.Notification.EXTRA_BIG_TEXT,
            android.app.Notification.EXTRA_SUMMARY_TEXT
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()
        }.flatMap { senderPieces(it, includeFullValue = false) }

        return (titleCandidates + textCandidates)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    private fun senderPieces(value: String, includeFullValue: Boolean): List<String> {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return emptyList()

        val pieces = mutableListOf<String>()
        if (includeFullValue) {
            pieces += cleaned
        }

        cleaned.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val head = line
                    .substringBefore("·")
                    .substringBefore("•")
                    .substringBefore("．")
                    .substringBefore("・")
                    .trim()
                if (head.isNotEmpty() && head != line) {
                    pieces += head
                }
            }

        return pieces.distinctBy { it.lowercase() }
    }

    private fun buildNoMatchMessage(senderCandidates: List<String>, allTargets: List<SimpTarget>): String {
        val senderText = senderCandidates.joinToString(" / ")
        if (allTargets.isEmpty()) {
            return "未觸發：尚未新增監聽對象。"
        }

        val disabledMatch = allTargets.firstOrNull { target ->
            !target.enabled && target.matchesAnySender(senderCandidates)
        }
        if (disabledMatch != null) {
            return "未觸發：$senderText 符合 ${disabledMatch.displayName}，但此對象目前已關閉。請重新打開開關，或改用持續監聽/星號鎖定。"
        }

        if (allTargets.none { it.enabled }) {
            return "未觸發：所有監聽對象目前都已關閉。"
        }

        return "未觸發：$senderText 沒有符合已開啟的監聽對象。"
    }

}
