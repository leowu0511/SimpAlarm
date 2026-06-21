package com.example.simpalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class SimpTarget(
    val id: String,
    val displayName: String,
    val notificationNames: String,
    val photoUri: String?,
    val avatarSeed: Int,
    val enabled: Boolean,
    val continuousOverride: Boolean,
    val pinned: Boolean = false
) {
    val name: String get() = displayName

    fun matchesSender(sender: String): Boolean {
        return matchesAnySender(listOf(sender))
    }

    fun matchesAnySender(senders: List<String>): Boolean {
        val cleanedSenders = senders.map { it.trim() }.filter { it.isNotEmpty() }
        return senderAliases().any { alias ->
            cleanedSenders.any { sender ->
                sender.equals(alias, ignoreCase = true) ||
                    sender.contains(alias, ignoreCase = true)
            }
        }
    }

    fun senderAliases(): List<String> {
        return (listOf(displayName) + notificationNames.split(',', '，', '、', '/', '|', '\n', '\t'))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }
}

data class MonitoredApp(
    val label: String,
    val packageName: String
)

enum class TriggerMode {
    Once,
    Continuous
}

enum class AlarmPresentationMode {
    FullScreen,
    SoundOnly
}

object SimpTargetManager {
    private const val PREFS_NAME = "simp_targets"
    private const val TARGET_ITEMS_KEY = "target_items_v2"
    private const val TARGETS_KEY = "targets"
    private const val ENABLED_TARGETS_KEY = "enabled_targets"
    private const val TRIGGER_MODE_KEY = "trigger_mode"
    private const val ALARM_PRESENTATION_MODE_KEY = "alarm_presentation_mode"
    private const val CONTINUOUS_TARGETS_KEY = "continuous_targets"
    private const val MONITORED_APPS_KEY = "monitored_apps"

    val supportedApps = listOf(
        MonitoredApp("Instagram", "com.instagram.android"),
        MonitoredApp("WhatsApp", "com.whatsapp"),
        MonitoredApp("WhatsApp Business", "com.whatsapp.w4b"),
        MonitoredApp("Discord", "com.discord"),
        MonitoredApp("LINE", "jp.naver.line.android"),
        MonitoredApp("Telegram", "org.telegram.messenger")
    )

    fun getTargets(context: Context): Set<String> {
        return getTargetItems(context).map { it.displayName }.toSet()
    }

    fun getTargetItems(context: Context): List<SimpTarget> {
        val prefs = prefs(context)
        val raw = prefs.getString(TARGET_ITEMS_KEY, null)
        if (raw != null) {
            return sortPinnedFirst(decodeTargets(raw))
        }
        val migrated = migrateLegacyTargets(context)
        saveTargetItems(context, migrated)
        return migrated
    }

    fun getTargetById(context: Context, targetId: String): SimpTarget? {
        return getTargetItems(context).firstOrNull { it.id == targetId }
    }

    fun getEnabledTargets(context: Context): List<SimpTarget> {
        return getTargetItems(context).filter { it.enabled }
    }

    fun addTarget(
        context: Context,
        displayName: String,
        notificationNames: String,
        photoUri: String? = null
    ) {
        val cleanedName = displayName.trim()
        val cleanedNotifications = notificationNames.trim()
        if (cleanedName.isEmpty() || cleanedNotifications.isEmpty()) return
        val next = SimpTarget(
            id = System.currentTimeMillis().toString(),
            displayName = cleanedName,
            notificationNames = cleanedNotifications,
            photoUri = photoUri?.takeIf { it.isNotBlank() },
            avatarSeed = createAvatarSeed(cleanedName),
            enabled = true,
            continuousOverride = false,
            pinned = false
        )
        saveTargetItems(context, getTargetItems(context) + next)
    }

    fun addTarget(context: Context, target: String) {
        addTarget(context, target, target)
    }

    fun updateTarget(
        context: Context,
        targetId: String,
        displayName: String,
        notificationNames: String,
        photoUri: String?
    ) {
        val cleanedName = displayName.trim()
        val cleanedNotifications = notificationNames.trim()
        if (cleanedName.isEmpty() || cleanedNotifications.isEmpty()) return
        saveTargetItems(
            context,
            getTargetItems(context).map { target ->
                if (target.id == targetId) {
                    target.copy(
                        displayName = cleanedName,
                        notificationNames = cleanedNotifications,
                        photoUri = photoUri?.takeIf { it.isNotBlank() }
                    )
                } else {
                    target
                }
            }
        )
    }

    fun removeTarget(context: Context, targetIdOrName: String) {
        saveTargetItems(
            context,
            getTargetItems(context).filterNot { it.id == targetIdOrName || it.displayName == targetIdOrName }
        )
    }

    fun setTargetEnabled(context: Context, targetIdOrName: String, enabled: Boolean) {
        saveTargetItems(
            context,
            getTargetItems(context).map { target ->
                if (target.id == targetIdOrName || target.displayName == targetIdOrName) {
                    target.copy(
                        enabled = enabled,
                        continuousOverride = if (enabled) target.continuousOverride else false
                    )
                } else {
                    target
                }
            }
        )
    }

    fun setTargetContinuousOverride(context: Context, targetIdOrName: String, enabled: Boolean) {
        saveTargetItems(
            context,
            getTargetItems(context).map { target ->
                if (target.id == targetIdOrName || target.displayName == targetIdOrName) {
                    target.copy(
                        enabled = if (enabled) true else target.enabled,
                        continuousOverride = enabled
                    )
                } else {
                    target
                }
            }
        )
    }

    fun setTargetPinned(context: Context, targetIdOrName: String, pinned: Boolean) {
        val updated = getTargetItems(context).map { target ->
            if (target.id == targetIdOrName || target.displayName == targetIdOrName) {
                target.copy(pinned = pinned)
            } else {
                target
            }
        }
        saveTargetItems(context, sortPinnedFirst(updated))
    }

    fun reorderTargets(context: Context, orderedTargetIds: List<String>) {
        if (orderedTargetIds.isEmpty()) return
        val order = orderedTargetIds.withIndex().associate { it.value to it.index }
        val reordered = getTargetItems(context).sortedWith(
            compareBy<SimpTarget> { order[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.displayName.lowercase() }
        )
        saveTargetItems(context, sortPinnedFirst(reordered))
    }

    fun markTriggered(context: Context, targetIdOrName: String) {
        saveTargetItemsNow(
            context,
            getTargetItems(context).map { target ->
                if (target.id == targetIdOrName || target.displayName == targetIdOrName) {
                    target.copy(enabled = false, continuousOverride = false)
                } else {
                    target
                }
            }
        )
    }

    fun getTriggerMode(context: Context): TriggerMode {
        val rawValue = prefs(context).getString(TRIGGER_MODE_KEY, TriggerMode.Once.name)
        return TriggerMode.entries.firstOrNull { it.name == rawValue } ?: TriggerMode.Once
    }

    fun setTriggerMode(context: Context, mode: TriggerMode) {
        prefs(context).edit()
            .putString(TRIGGER_MODE_KEY, mode.name)
            .apply()
    }

    fun getAlarmPresentationMode(context: Context): AlarmPresentationMode {
        val rawValue = prefs(context).getString(
            ALARM_PRESENTATION_MODE_KEY,
            AlarmPresentationMode.FullScreen.name
        )
        return AlarmPresentationMode.entries.firstOrNull { it.name == rawValue }
            ?: AlarmPresentationMode.FullScreen
    }

    fun setAlarmPresentationMode(context: Context, mode: AlarmPresentationMode) {
        prefs(context).edit()
            .putString(ALARM_PRESENTATION_MODE_KEY, mode.name)
            .apply()
    }

    fun getMonitoredApps(context: Context): Set<String> {
        val prefs = prefs(context)
        if (!prefs.contains(MONITORED_APPS_KEY)) {
            val defaults = setOf("com.instagram.android")
            prefs.edit()
                .putStringSet(MONITORED_APPS_KEY, defaults)
                .apply()
            return defaults
        }
        val supportedPackages = supportedApps.map { it.packageName }.toSet()
        return prefs.getStringSet(MONITORED_APPS_KEY, emptySet()).orEmpty()
            .filter { it in supportedPackages }
            .toSet()
    }

    fun setMonitoredAppEnabled(context: Context, packageName: String, enabled: Boolean) {
        val supportedPackages = supportedApps.map { it.packageName }.toSet()
        if (packageName !in supportedPackages) return

        val nextApps = if (enabled) {
            getMonitoredApps(context) + packageName
        } else {
            getMonitoredApps(context) - packageName
        }
        prefs(context).edit()
            .putStringSet(MONITORED_APPS_KEY, nextApps)
            .apply()
    }

    fun isPackageMonitored(context: Context, packageName: String): Boolean {
        return packageName in getMonitoredApps(context)
    }

    fun isSupportedPackage(packageName: String): Boolean {
        return supportedApps.any { it.packageName == packageName }
    }

    fun appLabelForPackage(packageName: String): String {
        return supportedApps.firstOrNull { it.packageName == packageName }?.label ?: "來源 App"
    }

    private fun migrateLegacyTargets(context: Context): List<SimpTarget> {
        val prefs = prefs(context)
        val targets = prefs.getStringSet(TARGETS_KEY, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val enabledTargets = legacySet(context, ENABLED_TARGETS_KEY).ifEmpty { targets }
        val continuousTargets = legacySet(context, CONTINUOUS_TARGETS_KEY)
        return targets.map { target ->
            SimpTarget(
                id = "legacy-${abs(target.hashCode())}",
                displayName = target,
                notificationNames = target,
                photoUri = null,
                avatarSeed = createAvatarSeed(target),
                enabled = target in enabledTargets || target in continuousTargets,
                continuousOverride = target in continuousTargets,
                pinned = false
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun legacySet(context: Context, key: String): Set<String> {
        return prefs(context).getStringSet(key, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun saveTargetItems(context: Context, targets: List<SimpTarget>) {
        prefs(context).edit()
            .putString(TARGET_ITEMS_KEY, encodeTargets(targets))
            .apply()
    }

    private fun saveTargetItemsNow(context: Context, targets: List<SimpTarget>) {
        prefs(context).edit()
            .putString(TARGET_ITEMS_KEY, encodeTargets(targets))
            .commit()
    }

    private fun encodeTargets(targets: List<SimpTarget>): String {
        val array = JSONArray()
        targets.forEach { target ->
            array.put(JSONObject().apply {
                put("id", target.id)
                put("displayName", target.displayName)
                put("notificationNames", target.notificationNames)
                put("photoUri", target.photoUri)
                put("avatarSeed", target.avatarSeed)
                put("enabled", target.enabled)
                put("continuousOverride", target.continuousOverride)
                put("pinned", target.pinned)
            })
        }
        return array.toString()
    }

    private fun decodeTargets(raw: String): List<SimpTarget> {
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val displayName = item.optString("displayName").ifBlank { item.optString("name") }
                SimpTarget(
                    id = item.optString("id").ifBlank { "target-${System.nanoTime()}-$index" },
                    displayName = displayName,
                    notificationNames = item.optString("notificationNames").ifBlank { displayName },
                    photoUri = item.optString("photoUri").takeIf { it.isNotBlank() && it != "null" },
                    avatarSeed = item.optInt("avatarSeed", createAvatarSeed(displayName)),
                    enabled = item.optBoolean("enabled", true),
                    continuousOverride = item.optBoolean("continuousOverride", false),
                    pinned = item.optBoolean("pinned", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun sortPinnedFirst(targets: List<SimpTarget>): List<SimpTarget> {
        return targets.filter { it.pinned } + targets.filterNot { it.pinned }
    }

    private fun createAvatarSeed(value: String): Int {
        return abs((value + System.currentTimeMillis()).hashCode())
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
