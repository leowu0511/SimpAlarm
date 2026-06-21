package com.example.simpalarm

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TriggerHistoryItem(
    val targetId: String,
    val targetName: String,
    val appLabel: String,
    val time: String,
    val message: String
)

object SimpEventLog {
    private const val PREFS_NAME = "simp_event_log"
    private const val LAST_EVENT_KEY = "last_event"
    private const val TRIGGER_HISTORY_KEY = "trigger_history"

    fun record(context: Context, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d("SimpAlarmEvent", message)
        prefs(context).edit()
            .putString(LAST_EVENT_KEY, "$timestamp  $message")
            .apply()
    }

    fun lastEvent(context: Context): String {
        return prefs(context).getString(LAST_EVENT_KEY, "尚未收到通知事件。").orEmpty()
    }

    fun recordTrigger(
        context: Context,
        target: SimpTarget,
        appLabel: String,
        sender: String,
        message: String
    ) {
        val timestamp = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())
        val item = TriggerHistoryItem(
            targetId = target.id,
            targetName = target.displayName,
            appLabel = appLabel,
            time = timestamp,
            message = message.ifBlank { "$sender 傳來新通知" }
        )
        val next = listOf(item) + triggerHistory(context)
        saveTriggerHistory(context, next.take(50))
    }

    fun triggerHistory(context: Context): List<TriggerHistoryItem> {
        val raw = prefs(context).getString(TRIGGER_HISTORY_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TriggerHistoryItem(
                    targetId = item.optString("targetId"),
                    targetName = item.optString("targetName"),
                    appLabel = item.optString("appLabel"),
                    time = item.optString("time"),
                    message = item.optString("message")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clearTriggerHistory(context: Context) {
        prefs(context).edit().remove(TRIGGER_HISTORY_KEY).apply()
    }

    private fun saveTriggerHistory(context: Context, items: List<TriggerHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("targetId", item.targetId)
                put("targetName", item.targetName)
                put("appLabel", item.appLabel)
                put("time", item.time)
                put("message", item.message)
            })
        }
        prefs(context).edit()
            .putString(TRIGGER_HISTORY_KEY, array.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
