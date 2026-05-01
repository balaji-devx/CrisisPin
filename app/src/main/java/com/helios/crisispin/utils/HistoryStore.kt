package com.helios.crisispin.utils

import android.content.Context
import android.content.SharedPreferences

data class AlertHistoryItem(
    val id: String,
    val type: String,
    val emoji: String,
    val colorHex: Int,
    val timestampMs: Long,
    val direction: String // "sent" or "received"
)

class HistoryStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("crisispin_history", Context.MODE_PRIVATE)

    fun saveEntry(alertType: String, direction: String, msgId: String? = null) {
        val ids = prefs.getStringSet("history_ids", mutableSetOf())!!.toMutableSet()
        
        // Use msgId to prevent duplicate entries for the same alert event
        val entryId = if (msgId != null) "msg_$msgId" else "${System.currentTimeMillis()}_${alertType}_$direction"
        
        if (msgId != null && ids.contains(entryId)) return

        val label = when (alertType.uppercase()) {
            "SOS" -> "SOS Emergency"
            "MED" -> "Medical Alert"
            "FIRE" -> "Fire Alert"
            "PANIC" -> "Panic Alert"
            "HELP" -> "General Help"
            else -> "$alertType Alert"
        }
        val emoji = when (alertType.uppercase()) {
            "MED" -> "🏥"; "FIRE" -> "🔥"; "PANIC" -> "⚠️"; "HELP" -> "🆘"; else -> "🚨"
        }
        val colorHex = when (alertType.uppercase()) {
            "MED" -> 0xFF1E88E5.toInt(); "FIRE" -> 0xFFFF9800.toInt()
            "PANIC" -> 0xFF9C27B0.toInt(); "HELP" -> 0xFF43A047.toInt(); else -> 0xFFE53935.toInt()
        }

        prefs.edit()
            .putStringSet("history_ids", ids + entryId)
            .putString("h_label_$entryId", label)
            .putString("h_emoji_$entryId", emoji)
            .putInt("h_color_$entryId", colorHex)
            .putLong("h_ts_$entryId", System.currentTimeMillis())
            .putString("h_dir_$entryId", direction)
            .apply()
    }

    fun loadHistory(): List<AlertHistoryItem> {
        val ids = prefs.getStringSet("history_ids", emptySet()) ?: return emptyList()
        return ids.mapNotNull { id ->
            val label = prefs.getString("h_label_$id", null) ?: return@mapNotNull null
            val emoji = prefs.getString("h_emoji_$id", "🚨")
            val colorHex = prefs.getInt("h_color_$id", 0xFFE53935.toInt())
            val ts = prefs.getLong("h_ts_$id", 0L)
            val dir = prefs.getString("h_dir_$id", "received") ?: "received"
            AlertHistoryItem(id, label, emoji!!, colorHex, ts, dir)
        }.sortedByDescending { it.timestampMs }
    }
}
