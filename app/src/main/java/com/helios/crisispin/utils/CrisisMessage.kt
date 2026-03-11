package com.helios.crisispin.utils

/**
 * Wire protocol for CrisisPin BLE payloads.
 *
 * Format (pipe-delimited to avoid conflicts with base36 IDs):
 *   TYPE|ORIGIN_ID|MSG_ID|HOP|VISITED
 *
 * Fields:
 *   TYPE       — alert type string: SOS, MED, FIRE, PANIC, HELP
 *   ORIGIN_ID  — 6-char base36 ID of the device that ORIGINATED the alert (never changes on relay)
 *   MSG_ID     — 6-char base36 random ID unique to this send session
 *   HOP        — integer relay hop count. Starts at 0, incremented each relay. Max = MAX_HOPS.
 *   VISITED    — comma-separated list of device IDs that have seen this exact message.
 *                Prevents the same device from receiving or relaying the same packet twice.
 *
 * Example (fresh send from device "ab1234"):
 *   MED|ab1234|xy9012|0|ab1234
 *
 * Example (after relay by device "cd5678"):
 *   MED|ab1234|xy9012|1|ab1234,cd5678
 *
 * Max payload estimate:
 *   "PANIC|ab1234|xy9012|3|ab1234,cd5678,ef9012,gh3456" = 50 chars
 *   BLE ServiceData max = 20 bytes after UUID overhead — we cap VISITED at 4 IDs.
 *   At 6 chars + comma = 7 bytes each, 4 IDs = 28 chars. Full packet ~ 44 chars. ✓
 */
data class CrisisMessage(
    val type: String,        // e.g. "MED"
    val originId: String,    // 6-char base36 device ID of originator
    val msgId: String,       // 6-char base36 random session ID
    val hop: Int,            // relay hop count
    val visited: Set<String> // device IDs that have processed this message
) {
    companion object {
        const val MAX_HOPS    = 3     // prevent infinite relay loops
        const val MAX_VISITED = 6     // keep payload small

        fun encode(msg: CrisisMessage): String {
            val visitedStr = msg.visited.take(MAX_VISITED).joinToString(",")
            return "${msg.type}|${msg.originId}|${msg.msgId}|${msg.hop}|$visitedStr"
        }

        fun decode(raw: String): CrisisMessage? {
            return try {
                val parts = raw.split("|")
                if (parts.size < 4) return null
                val type     = parts[0].uppercase().trim()
                val originId = parts[1].trim()
                val msgId    = parts[2].trim()
                val hop      = parts[3].trim().toIntOrNull() ?: 0
                val visited  = if (parts.size >= 5 && parts[4].isNotBlank())
                    parts[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                else
                    setOf(originId)
                if (type.isEmpty() || originId.isEmpty() || msgId.isEmpty()) null
                else CrisisMessage(type, originId, msgId, hop, visited)
            } catch (e: Exception) {
                null
            }
        }

        /** Create a fresh message for sending */
        fun newAlert(type: String, myDeviceId: String): CrisisMessage {
            val msgId = (Math.abs(java.util.UUID.randomUUID().toString()
                .replace("-", "").take(8).toLong(16)) % 2_176_782_336L).toString(36).padStart(6, '0')
            return CrisisMessage(
                type     = type.uppercase(),
                originId = myDeviceId,
                msgId    = msgId,
                hop      = 0,
                visited  = setOf(myDeviceId)
            )
        }

        /** Create a relay copy with this device added to visited and hop incremented */
        fun relay(msg: CrisisMessage, myDeviceId: String): CrisisMessage {
            return msg.copy(
                hop     = msg.hop + 1,
                visited = (msg.visited + myDeviceId).take(MAX_VISITED).toSet()
            )
        }
    }
}