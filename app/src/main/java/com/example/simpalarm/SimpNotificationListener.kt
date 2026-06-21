package com.example.simpalarm

import android.content.Intent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class SimpNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        SimpEventLog.record(this, "通知監聽服務已連線。")
        logActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SimpEventLog.record(this, "通知監聽服務已中斷，請重新啟用通知監聽。")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, SimpNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(
            "SimpAlarmEvent",
            "onNotificationPosted package=${sbn.packageName}, monitored=${SimpTargetManager.isPackageMonitored(this, sbn.packageName)}"
        )
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
        logNotificationFields(sbn, extras)
        val senderCandidates = senderCandidates(extras)
        val sender = senderCandidates.firstOrNull().orEmpty()
        if (senderCandidates.isEmpty()) {
            SimpEventLog.record(this, "收到 $appLabel 通知，但通知沒有可比對的寄件者欄位。")
            return
        }

        SimpEventLog.record(this, "收到 $appLabel 通知：${senderCandidates.joinToString(" / ")}")

        val allTargets = SimpTargetManager.getTargetItems(this)
        val targets = allTargets.filter { it.enabled }
        val directMatchedTarget = targets.firstOrNull { target ->
            target.matchesAnySender(senderCandidates)
        }
        val matchedTarget = directMatchedTarget ?: recentMatchedTarget(sbn, targets) ?: run {
            SimpEventLog.record(this, buildNoMatchMessage(senderCandidates, allTargets, sbn))
            return
        }
        if (directMatchedTarget == null) {
            SimpEventLog.record(this, "使用最近通知脈絡匹配：${senderCandidates.joinToString(" / ")} 符合 ${matchedTarget.displayName}。")
        }

        val message = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        val shouldDisableTarget = shouldDisableAfterTrigger(matchedTarget)
        val intent = Intent(this, SimpAlarmService::class.java).apply {
            putExtra(SimpAlarmService.EXTRA_SENDER_NAME, sender)
            putExtra(SimpAlarmService.EXTRA_MATCHED_TARGET, matchedTarget.id)
            putExtra(SimpAlarmService.EXTRA_MESSAGE_TEXT, message)
            putExtra(SimpAlarmService.EXTRA_SOURCE_PACKAGE, sbn.packageName)
            putExtra(SimpAlarmService.EXTRA_SOURCE_APP_LABEL, appLabel)
            putExtra(SimpAlarmService.EXTRA_SHOULD_DISABLE_TARGET, shouldDisableTarget)
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }.onSuccess {
            rememberMatchedTarget(sbn, matchedTarget)
            SimpEventLog.recordTrigger(this, matchedTarget, appLabel, sender, message)
            SimpEventLog.record(this, "已送出鬧鐘啟動要求：$sender 符合 ${matchedTarget.displayName}。")
        }.onFailure { error ->
            SimpEventLog.record(this, "鬧鐘服務啟動失敗：${error.javaClass.simpleName}，已改用備援通知。")
            SimpAlarmFallbackNotifier.show(
                context = this,
                sender = sender,
                message = message,
                matchedTarget = matchedTarget.id,
                sourcePackage = sbn.packageName,
                sourceAppLabel = appLabel,
                shouldDisableTarget = shouldDisableTarget,
                returnToAppOnDismiss = false,
                alarmPresentationMode = SimpTargetManager.getAlarmPresentationMode(this)
            )
        }
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    private fun shouldDisableAfterTrigger(target: SimpTarget): Boolean {
        return SimpTargetManager.getTriggerMode(this) == TriggerMode.Once &&
            !target.continuousOverride
    }

    private fun logActiveNotifications() {
        val packages = runCatching {
            activeNotifications
                ?.map { it.packageName }
                ?.distinct()
                ?.joinToString(", ")
        }.getOrNull().orEmpty()
        Log.d("SimpAlarmEvent", "activeNotifications packages=${packages.ifBlank { "none" }}")
    }

    private fun logNotificationFields(sbn: StatusBarNotification, extras: android.os.Bundle) {
        val watchedKeys = listOf(
            android.app.Notification.EXTRA_TITLE,
            android.app.Notification.EXTRA_TITLE_BIG,
            android.app.Notification.EXTRA_TEXT,
            android.app.Notification.EXTRA_BIG_TEXT,
            android.app.Notification.EXTRA_SUB_TEXT,
            android.app.Notification.EXTRA_SUMMARY_TEXT,
            android.app.Notification.EXTRA_CONVERSATION_TITLE
        )
        val fields = watchedKeys.joinToString(" | ") { key ->
            "$key=${extras.getCharSequence(key)?.toString()?.replace("\n", "\\n") ?: "null"}"
        }
        Log.d(
            "SimpAlarmEvent",
            "notificationFields package=${sbn.packageName}, key=${notificationContextKey(sbn)}, $fields"
        )
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

    private fun buildNoMatchMessage(
        senderCandidates: List<String>,
        allTargets: List<SimpTarget>,
        sbn: StatusBarNotification
    ): String {
        val senderText = senderCandidates.joinToString(" / ")
        val contextKey = notificationContextKey(sbn)
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

        val enabledAliases = allTargets
            .filter { it.enabled }
            .joinToString("；") { target ->
                "${target.displayName}=[${target.senderAliases().joinToString(", ")}]"
            }
            .ifBlank { "無已啟用對象" }
        return "未觸發：收到=[$senderText]，已啟用比對=[$enabledAliases]，通知脈絡=$contextKey"
    }

    private fun rememberMatchedTarget(sbn: StatusBarNotification, target: SimpTarget) {
        val key = notificationContextKey(sbn)
        if (key.isBlank()) return
        contextPrefs().edit()
            .putString(key, target.id)
            .putLong("$key:time", System.currentTimeMillis())
            .apply()
    }

    private fun recentMatchedTarget(sbn: StatusBarNotification, targets: List<SimpTarget>): SimpTarget? {
        val key = notificationContextKey(sbn)
        if (key.isBlank()) return null
        val prefs = contextPrefs()
        val targetId = prefs.getString(key, null) ?: return null
        val updatedAt = prefs.getLong("$key:time", 0L)
        if (System.currentTimeMillis() - updatedAt > RECENT_MATCH_TTL_MS) return null
        return targets.firstOrNull { it.id == targetId }
    }

    private fun notificationContextKey(sbn: StatusBarNotification): String {
        val shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            sbn.notification.shortcutId.orEmpty()
        } else {
            ""
        }
        return listOf(
            sbn.packageName,
            sbn.notification.category.orEmpty(),
            sbn.groupKey.orEmpty(),
            sbn.tag.orEmpty(),
            sbn.id.toString(),
            shortcutId
        ).joinToString("|")
    }

    private fun contextPrefs() =
        applicationContext.getSharedPreferences("simp_recent_notification_matches", Context.MODE_PRIVATE)

}

private const val RECENT_MATCH_TTL_MS = 30 * 60 * 1000L
