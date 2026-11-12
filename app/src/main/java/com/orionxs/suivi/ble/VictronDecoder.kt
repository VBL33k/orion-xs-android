package com.orionxs.suivi.ble

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Décodage des annonces Bluetooth « Instant Readout » de Victron.
 *
 * Format (d'après la bibliothèque open source victron-ble, données constructeur 0x02E1) :
 *   [0..1]  préfixe (0x10 = annonce produit)
 *   [2..3]  identifiant de modèle (little endian)
 *   [4]     type d'enregistrement (0x0F = Orion XS, 0x04 = convertisseur DC-DC)
 *   [5..6]  compteur / IV (little endian)
 *   [7]     premier octet de la clé (contrôle)
 *   [8..]   données chiffrées AES-128-CTR (compteur 128 bits little endian démarrant à IV)
 */
object VictronDecoder {
    const val VICTRON_COMPANY_ID = 0x02E1
    const val RECORD_DCDC = 0x04
    const val RECORD_ORION_XS = 0x0F

    class Header(
        val modelId: Int,
        val recordType: Int,
        val iv: Int,
        val keyCheck: Int,
        val encrypted: ByteArray,
    )

    sealed class Result {
        data class Ok(val reading: Reading) : Result()
        data object NoKey : Result()
        data object KeyMismatch : Result()
        data class Unsupported(val recordType: Int) : Result()
        data class Invalid(val why: String) : Result()
    }

    fun parseHeader(data: ByteArray): Header? {
        if (data.size < 9) return null
        if (data[0] != 0x10.toByte()) return null
        val modelId = u8(data, 2) or (u8(data, 3) shl 8)
        val recordType = u8(data, 4)
        val iv = u8(data, 5) or (u8(data, 6) shl 8)
        return Header(modelId, recordType, iv, u8(data, 7), data.copyOfRange(8, data.size))
    }

    /** Clé hexadécimale (32 caractères) -> 16 octets, ou null si invalide. */
    fun parseKey(hex: String?): ByteArray? {
        val clean = hex?.filter { !it.isWhitespace() }?.lowercase() ?: return null
        if (clean.length != 32 || clean.any { it !in "0123456789abcdef" }) return null
        return ByteArray(16) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Déchiffrement AES-CTR avec compteur little endian (identique à victron-ble). */
    fun decrypt(key: ByteArray, iv: Int, encrypted: ByteArray): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) // CTR = chiffrement du compteur
        val out = ByteArray(encrypted.size)
        var block = 0
        while (block * 16 < encrypted.size) {
            val counter = ByteArray(16)
            var value = iv.toLong() + block // pas de dépassement possible avec des messages courts
            for (i in 0 until 8) {
                counter[i] = (value and 0xFF).toByte(); value = value ushr 8
            }
            val stream = aes.doFinal(counter)
            for (i in 0 until 16) {
                val idx = block * 16 + i
                if (idx >= encrypted.size) break
                out[idx] = (encrypted[idx].toInt() xor stream[i].toInt()).toByte()
            }
            block++
        }
        return out
    }

    fun decode(data: ByteArray, key: ByteArray?, address: String = "", now: Long = System.currentTimeMillis()): Result {
        val h = parseHeader(data) ?: return Result.Invalid("en-tête")
        if (h.recordType != RECORD_ORION_XS && h.recordType != RECORD_DCDC) return Result.Unsupported(h.recordType)
        if (key == null) return Result.NoKey
        if (h.keyCheck != (key[0].toInt() and 0xFF)) return Result.KeyMismatch
        val plain = decrypt(key, h.iv, h.encrypted)
        val r = BitReader(plain)
        val reading = when (h.recordType) {
            RECORD_ORION_XS -> {
                if (plain.size < 14) return Result.Invalid("trop court (${plain.size})")
                val state = r.u(8).na(0xFF)?.toInt()
                val error = r.u(8).na(0xFF)?.toInt()
                val vout = r.u(16).na(0xFFFF)?.div(100.0)
                val iout = r.u(16).na(0xFFFF)?.div(10.0)
                val vin = r.u(16).na(0xFFFF)?.div(100.0)
                val iin = r.u(16).na(0xFFFF)?.div(10.0)
                val off = r.u(32)
                Reading(now, address, h.modelId, h.recordType, state, error, vin, vout, iin, iout, off)
            }
            else -> { // Convertisseur DC-DC (Orion Smart)
                if (plain.size < 10) return Result.Invalid("trop court (${plain.size})")
                val state = r.u(8).na(0xFF)?.toInt()
                val error = r.u(8).na(0xFF)?.toInt()
                val vin = r.u(16).na(0xFFFF)?.div(100.0)
                val rawOut = r.u(16)
                val vout = if (rawOut == 0x7FFFL) null else BitReader.signed(rawOut, 16) / 100.0
                val off = r.u(32)
                Reading(now, address, h.modelId, h.recordType, state, error, vin, vout, null, null, off)
            }
        }
        // Garde-fou : une clé fausse dont le 1er octet coïncide donne des valeurs absurdes.
        val plausible = listOfNotNull(reading.inputVoltage, reading.outputVoltage).all { it in 0.0..80.0 }
        if (!plausible) return Result.KeyMismatch
        return Result.Ok(reading)
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun Long.na(sentinel: Int): Long? = if (this == sentinel.toLong()) null else this
}

class BitReader(private val data: ByteArray) {
    private var index = 0
    fun u(bits: Int): Long {
        var v = 0L
        for (p in 0 until bits) {
            val byte = index shr 3
            val bit = if (byte < data.size) (data[byte].toInt() shr (index and 7)) and 1 else 0
            v = v or (bit.toLong() shl p)
            index++
        }
        return v
    }

    companion object {
        fun signed(v: Long, bits: Int): Long = if (v and (1L shl (bits - 1)) != 0L) v - (1L shl bits) else v
    }
}

data class Reading(
    val timestamp: Long,
    val address: String,
    val modelId: Int,
    val recordType: Int,
    val state: Int?,
    val error: Int?,
    val inputVoltage: Double?,
    val outputVoltage: Double?,
    val inputCurrent: Double?,
    val outputCurrent: Double?,
    val offReason: Long,
)
