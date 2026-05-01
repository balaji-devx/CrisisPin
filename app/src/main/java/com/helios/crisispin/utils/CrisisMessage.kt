package com.helios.crisispin.utils

import android.util.Log
import java.nio.ByteBuffer

/**
 * Hardened Wire protocol for CrisisPin BLE payloads.
 * Supports legacy V1 (String) and compact V2 (Binary).
 */
data class CrisisMessage(
    val type: String,
    val originId: String,
    val msgId: String,
    val hop: Int,
    val visited: Set<String>,
    val flags: Int = 0
) {
    companion object {
        const val MAX_HOPS = 10
        const val BLE_MAX_BYTES = 20
        
        const val V2_VER = 0x02.toByte()
        const val FLAG_ACK = 0x01
        const val FLAG_CANCEL = 0x02 // FIX 3: Cancel flag added

        /** Unified decoder with version detection */
        fun decodeFromBle(bytes: ByteArray): CrisisMessage? {
            // FIX 3 — STRICT V2 HEADER VALIDATION
            if (bytes.size >= 3 &&
                bytes[0] == 'C'.code.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 0x02.toByte()
            ) {
                return decodeV2(bytes)
            } else {
                return decode(String(bytes, Charsets.UTF_8))
            }
        }

        private fun decodeV2(bytes: ByteArray): CrisisMessage? {
            return try {
                val buffer = ByteBuffer.wrap(bytes)
                if (buffer.remaining() < 14) return null 
                buffer.position(3) // Skip header

                val typeCode = buffer.get().toInt()
                val type = when(typeCode) {
                    1 -> "SOS"; 2 -> "MED"; 3 -> "FIRE"; 4 -> "PANIC"; 5 -> "HELP"; else -> "SOS"
                }
                
                val originId = buffer.getInt().toUInt().toLong().toString(36).padStart(6, '0')
                val msgId = buffer.getInt().toUInt().toLong().toString(36).padStart(6, '0')
                
                val hop = buffer.get().toInt() and 0xFF
                val flags = buffer.get().toInt() and 0xFF

                val visited = mutableSetOf(originId)
                while (buffer.remaining() >= 4 && visited.size < 2) {
                    val vId = buffer.getInt().toUInt().toLong().toString(36).padStart(6, '0')
                    if (vId.length == 6) visited.add(vId)
                }

                CrisisMessage(type, originId, msgId, hop.coerceIn(0, MAX_HOPS), visited, flags)
            } catch (e: Exception) {
                Log.e("CrisisMessage", "V2 decode error", e)
                null
            }
        }

        private val TYPE_MAP = mapOf("SOS" to 1, "MED" to 2, "FIRE" to 3, "PANIC" to 4, "HELP" to 5)

        fun decode(raw: String): CrisisMessage? {
            return try {
                val parts = raw.split("|")
                if (parts.size < 4) return null
                val type = parts[0].uppercase().trim()
                if (!TYPE_MAP.containsKey(type)) return null
                
                val originId = parts[1].trim()
                val msgId = parts[2].trim()
                if (originId.length > 6 || msgId.length > 6) return null
                
                val hop = parts[3].trim().toIntOrNull() ?: 0
                val visited = if (parts.size >= 5 && parts[4].isNotBlank())
                    parts[4].split(",").map { it.trim() }.filter { it.length <= 6 }.toSet()
                else setOf(originId)

                CrisisMessage(type, originId, msgId, hop.coerceIn(0, MAX_HOPS), visited)
            } catch (e: Exception) { null }
        }

        /** Safe V2 Encoder (Strictly <= 20 bytes) */
        fun encodeForBle(msg: CrisisMessage): ByteArray? {
            try {
                val buffer = ByteBuffer.allocate(BLE_MAX_BYTES)
                buffer.put('C'.code.toByte())
                buffer.put('P'.code.toByte())
                buffer.put(V2_VER)
                
                buffer.put(TYPE_MAP[msg.type.uppercase()]?.toByte() ?: 1)
                
                val originLong = msg.originId.take(6).toLong(36) and 0xFFFFFFFFL
                val msgIdLong = msg.msgId.take(6).toLong(36) and 0xFFFFFFFFL
                
                buffer.putInt(originLong.toInt())
                buffer.putInt(msgIdLong.toInt())
                buffer.put(msg.hop.coerceIn(0, MAX_HOPS).toByte())
                buffer.put(msg.flags.toByte())

                msg.visited.filter { it != msg.originId }.take(1).forEach { id ->
                    val vidLong = id.take(6).toLong(36) and 0xFFFFFFFFL
                    buffer.putInt(vidLong.toInt())
                }
                
                val result = ByteArray(buffer.position())
                buffer.flip()
                buffer.get(result)
                return result
            } catch (e: Exception) {
                Log.e("CrisisMessage", "V2 encode failed, trying V1 fallback", e)
                val v1 = encodeV1(msg)
                val v1Bytes = v1.toByteArray(Charsets.UTF_8)
                return if (v1Bytes.size <= BLE_MAX_BYTES) v1Bytes else null
            }
        }

        private fun encodeV1(msg: CrisisMessage): String {
            // Minimal V1 for fallback - max 1 visited
            val visitedStr = msg.visited.filter { it != msg.originId }.take(1).joinToString(",")
            val base = "${msg.type}|${msg.originId}|${msg.msgId}|${msg.hop}"
            return if (visitedStr.isNotEmpty()) "$base|$visitedStr" else base
        }

        fun newAlert(type: String, myDeviceId: String): CrisisMessage {
            val msgId = (System.currentTimeMillis() % 2_176_782_336L).toString(36).padStart(6, '0')
            return CrisisMessage(type.uppercase(), myDeviceId, msgId, 0, setOf(myDeviceId))
        }

        fun createAck(msg: CrisisMessage, myDeviceId: String): CrisisMessage {
            return msg.copy(
                flags = msg.flags or FLAG_ACK,
                visited = setOf(myDeviceId)
            )
        }

        // FIX 3: Create Cancel message
        fun createCancel(msg: CrisisMessage, myDeviceId: String): CrisisMessage {
            return msg.copy(
                flags = msg.flags or FLAG_CANCEL,
                visited = setOf(myDeviceId)
            )
        }
    }
}
