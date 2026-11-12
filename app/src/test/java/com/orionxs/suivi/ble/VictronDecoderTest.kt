package com.orionxs.suivi.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VictronDecoderTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Vecteur réel issu des tests de victron-ble (Orion Smart DC-DC). */
    @Test
    fun realDcDcVector() {
        val key = VictronDecoder.parseKey("64ba49f1a8562e45197a8e1fe50d7658")!!
        val res = VictronDecoder.decode(hex("1000c0a304121d64ca8d442b90bbdf6a8cba"), key)
        assertTrue(res.toString(), res is VictronDecoder.Result.Ok)
        val r = (res as VictronDecoder.Result.Ok).reading
        assertEquals(0, r.state)
        assertEquals(0, r.error)
        assertEquals(13.15, r.inputVoltage!!, 0.001)
        assertNull(r.outputVoltage)
        assertEquals(0x80L, r.offReason)
    }

    /** Orion XS : on chiffre une trame connue puis on vérifie le décodage complet. */
    @Test
    fun orionXsRoundTrip() {
        val keyHex = "0df4d0395b7d1a876c0db9ac1a94f8b1"
        val key = VictronDecoder.parseKey(keyHex)!!
        // état 3 (bulk), erreur 0, Vout 14.40 V, Iout 25.3 A, Vin 13.25 V, Iin 28.0 A, off 0
        val plain = byteArrayOf(
            3, 0,
            0xA0.toByte(), 0x05,  // 1440
            0xFD.toByte(), 0x00,  // 253
            0x2D, 0x05,           // 1325
            0x18, 0x01,           // 280
            0, 0, 0, 0,
        )
        val iv = 0x1234
        val enc = VictronDecoder.decrypt(key, iv, plain) // CTR : chiffrer == déchiffrer
        val frame = byteArrayOf(0x10, 0x00, 0xF0.toByte(), 0xA3.toByte(), 0x0F, 0x34, 0x12, key[0]) + enc
        val res = VictronDecoder.decode(frame, key, "AA:BB")
        val r = (res as VictronDecoder.Result.Ok).reading
        assertEquals(3, r.state)
        assertEquals(14.40, r.outputVoltage!!, 0.001)
        assertEquals(25.3, r.outputCurrent!!, 0.001)
        assertEquals(13.25, r.inputVoltage!!, 0.001)
        assertEquals(28.0, r.inputCurrent!!, 0.001)
        assertEquals(0xA3F0, r.modelId)
    }

    @Test
    fun wrongKeyDetected() {
        val key = VictronDecoder.parseKey("11ba49f1a8562e45197a8e1fe50d7658")!!
        val res = VictronDecoder.decode(hex("1000c0a304121d64ca8d442b90bbdf6a8cba"), key)
        assertTrue(res is VictronDecoder.Result.KeyMismatch)
    }

    @Test
    fun keyParsing() {
        assertNull(VictronDecoder.parseKey("abc"))
        assertTrue(VictronDecoder.parseKey(" 64BA49F1 A8562E45 197A8E1F E50D7658 ") != null)
    }
}
