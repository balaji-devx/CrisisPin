package com.helios.crisispin.utils

import android.content.Context

/**
 * SharedPreferences-backed alert history store.
 * Prevents duplicate entries using msgId.
 */
object HistoryPrefs {

    private const val PREF_NAME = "crisispin_history"
    private const val KEY_IDS = "history_ids"

    data class HistoryRecord(
        val id: String,
        val label: String,
        val emoji: String,
        val colorHex: Int,
        val timestampMs: Long,
        val direction: String
    )

    data class Presentation(
        val label: String,
        val emoji: String,
        val colorHex: Int
    )

    fun presentationFor(alertType: String): Presentation {
        val t = alertType.uppercase()
        val label = when (t) {
            "SOS" -> "SOS Emergency"
            "MED" -> "Medical Alert"
            "FIRE" -> "Fire Alert"
            "PANIC" -> "Panic Alert"
            "HELP" -> "General Help"
            else -> "$alertType Alert"
        }
        val emoji = when (t) {
            "MED" -> "🏥"
            "FIRE" -> "🔥"
            "PANIC" -> "⚠️"
            "HELP" -> "🆘"
            else -> "🚨"
        }
        val colorHex = when (t) {
            "MED" -> 0xFF1E88E5.toInt()
            "FIRE" -> 0xFFFF9800.toInt()
            "PANIC" -> 0xFF9C27B0.toInt()
            "HELP" -> 0xFF43A047.toInt()
            else -> 0xFFE53935.toInt()
        }
        return Presentation(label = label, emoji = emoji, colorHex = colorHex)
    }

    /**
     * Append a new entry to history.
     * @param msgId Optional unique message identifier to prevent duplicates.
     */
    fun save(context: Context, alertType: String, direction: String, msgId: String? = null): String {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ids = p.getStringSet(KEY_IDS, mutableSetOf())!!.toMutableSet()

        // Use msgId to prevent duplicate entries for the same alert event
        val entryId = if (msgId != null) "msg_$msgId" else "${System.currentTimeMillis()}_${alertType}_$direction"
        
        if (ids.contains(entryId)) return entryId

        val now = System.currentTimeMillis()
        val pres = presentationFor(alertType)

        p.edit()
            .putStringSet(KEY_IDS, ids + entryId)
            .putString("h_label_$entryId", pres.label)
            .putString("h_emoji_$entryId", pres.emoji)
            .putInt("h_color_$entryId", pres.colorHex)
            .putLong("h_ts_$entryId", now)
            .putString("h_dir_$entryId", direction)
            .apply()

        return entryId
    }

    fun load(context: Context): List<HistoryRecord> {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ids = p.getStringSet(KEY_IDS, emptySet()) ?: return emptyList()

        return ids.mapNotNull { id ->
            val label = p.getString("h_label_$id", null) ?: return@mapNotNull null
            val emoji = p.getString("h_emoji_$id", "🚨") ?: "🚨"
            val colorHex = p.getInt("h_color_$id", 0xFFE53935.toInt())
            val ts = p.getLong("h_ts_$id", 0L)
            val dir = p.getString("h_dir_$id", "received") ?: "received"

            HistoryRecord(
                id = id,
                label = label,
                emoji = emoji,
                colorHex = colorHex,
                timestampMs = ts,
                direction = dir
            )
        }.sortedByDescending { it.timestampMs }
    }
}

class NearbyDeviceTracker(
    private val ttlMs: Long = 30 * 1000L, // TTL window reduced to 30s as per requirement
    private val maxTracked: Int = 256
) {
    private val lastSeenMs = java.util.LinkedHashMap<String, Long>()

    @Synchronized
    fun markSeen(deviceId: String, nowMs: Long = System.currentTimeMillis()): Int {
        cleanup(nowMs)
        lastSeenMs.remove(deviceId) // Move to end
        lastSeenMs[deviceId] = nowMs
        if (lastSeenMs.size > maxTracked) {
            val it = lastSeenMs.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        return lastSeenMs.size
    }

    @Synchronized
    fun getCount(nowMs: Long = System.currentTimeMillis()): Int {
        cleanup(nowMs)
        return lastSeenMs.size
    }

    private fun cleanup(nowMs: Long) {
        lastSeenMs.entries.removeIf { nowMs - it.value > ttlMs }
    }
}
