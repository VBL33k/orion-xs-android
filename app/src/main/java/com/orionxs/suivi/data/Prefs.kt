package com.orionxs.suivi.data

import android.content.Context
import com.orionxs.suivi.BuildConfig
import com.orionxs.suivi.ble.VictronDecoder

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("orionxs", Context.MODE_PRIVATE)

    /** Clé saisie dans l'app, sinon clé éventuellement intégrée à l'APK. */
    var keyHex: String
        get() = sp.getString("key", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_KEY
        set(v) = sp.edit().putString("key", v).apply()

    val keyBytes: ByteArray? get() = VictronDecoder.parseKey(keyHex)

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

    var supportPhone: String
        get() = sp.getString("supportPhone", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPPORT_PHONE
        set(v) = sp.edit().putString("supportPhone", v.filter { it.isDigit() }).apply()

    var lastRecordedHour: Long
        get() = sp.getLong("lastRecordedHour", 0L)
        set(v) = sp.edit().putLong("lastRecordedHour", v).apply()

    /** Début de la surveillance (pour ne pas inventer de « trous » avant l'installation). */
    var monitorSince: Long
        get() = sp.getLong("monitorSince", 0L)
        set(v) = sp.edit().putLong("monitorSince", v).apply()

    var autoCsvUri: String?
        get() = sp.getString("autoCsvUri", null)
        set(v) = sp.edit().putString("autoCsvUri", v).apply()

    var lastDevice: String?
        get() = sp.getString("lastDevice", null)
        set(v) = sp.edit().putString("lastDevice", v).apply()

    companion object {
        /** Extrait une clé de 32 caractères hexadécimaux d'un texte quelconque (ex. message WhatsApp). */
        fun extractKey(text: String?): String? {
            if (text == null) return null
            // 1) 32 caractères hexadécimaux d'un seul bloc, entourés de non-lettres/chiffres
            Regex("(?<![0-9A-Za-z])[0-9a-fA-F]{32}(?![0-9A-Za-z])").find(text)?.let { return it.value.lowercase() }
            // 2) clé découpée en groupes (espaces, « : » ou « - ») — exactement 32 caractères,
            //    sans avaler les lettres a-f des mots voisins (« clé », « voici »…)
            val grouped = Regex("(?<![0-9A-Za-z])(?:[0-9a-fA-F][\\s:\\-]*){31}[0-9a-fA-F](?![0-9A-Za-z])")
            return grouped.find(text)?.value?.filter { it.isLetterOrDigit() }?.lowercase()
        }
    }
}
