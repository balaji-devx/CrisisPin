package com.helios.crisispin.utils

import org.junit.Assert.*
import org.junit.Test

class CrisisMessageCodecTest {

    @Test
    fun v1_decode_still_works() {
        val raw = "MED|ab1234|xy9012|0|ab1234"
        val msg = CrisisMessage.decode(raw)
        assertNotNull(msg)
        assertEquals("MED", msg!!.type)
        assertEquals("ab1234", msg.originId)
        assertEquals("xy9012", msg.msgId)
        assertEquals(0, msg.hop)
        assertTrue(msg.visited.contains("ab1234"))
    }

    @Test
    fun ble_v2_round_trip_works() {
        val msg = CrisisMessage(
            type = "PANIC",
            originId = "ab1234",
            msgId = "xy9012",
            hop = 1,
            visited = setOf("ab1234")
        )
        val bytes = CrisisMessage.encodeForBle(msg)
        assertNotNull("Encoding should not return null", bytes)
        bytes!!
        
        assertTrue(bytes.size <= CrisisMessage.BLE_MAX_BYTES)
        assertEquals('C'.code.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals(0x02.toByte(), bytes[2])

        val decoded = CrisisMessage.decodeFromBle(bytes)
        assertNotNull(decoded)
        assertEquals("PANIC", decoded!!.type)
        assertEquals("ab1234", decoded.originId)
        assertEquals(1, decoded.hop)
        assertTrue(decoded.visited.contains("ab1234"))
    }

    @Test
    fun ble_payload_never_exceeds_20_bytes_for_known_types() {
        val myId = "ab1234"
        val types = listOf("SOS", "MED", "FIRE", "PANIC", "HELP")

        types.forEach { t ->
            val msg = CrisisMessage.newAlert(t, myId)
            val bytes = CrisisMessage.encodeForBle(msg)
            assertNotNull("Encoding failed for type $t", bytes)
            assertTrue("Type=$t bytes=${bytes!!.size}", bytes.size in 1..CrisisMessage.BLE_MAX_BYTES)
            assertNotNull(CrisisMessage.decodeFromBle(bytes))
        }
    }

    @Test
    fun ble_decoder_falls_back_to_v1_utf8_string() {
        val v1 = "SOS|ab1234|xy9012|0|".toByteArray(Charsets.UTF_8)
        val decoded = CrisisMessage.decodeFromBle(v1)
        assertNotNull(decoded)
        assertEquals("SOS", decoded!!.type)
        assertEquals("ab1234", decoded.originId)
        assertEquals("xy9012", decoded.msgId)
    }
}
