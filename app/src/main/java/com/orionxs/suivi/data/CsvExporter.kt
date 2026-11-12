package com.orionxs.suivi.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.orionxs.suivi.ble.VictronTexts
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export CSV dans Téléchargements/OrionXS.
 * Séparateur « ; » et virgule décimale : s'ouvre directement dans Excel / LibreOffice en français.
 */
object CsvExporter {
    const val FOLDER = "OrionXS"
    const val AUTO_FILE = "orion_xs_historique.csv"
    private const val MIME = "text/csv"

    fun buildCsv(rows: List<HourRow>): String {
        val d = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val h = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val sb = StringBuilder("﻿") // BOM pour qu'Excel lise bien les accents
        sb.append("Date;Heure;Tension entrée (V);Tension sortie (V);Courant sortie (A);État;Erreur;Mesurée à\r\n")
        for (r in rows.sortedBy { it.hour }) {
            sb.append(d.format(Date(r.hour))).append(';')
            sb.append(h.format(Date(r.hour))).append(';')
            sb.append(num(r.vin)).append(';')
            sb.append(num(r.vout)).append(';')
            sb.append(num(r.iout, 1)).append(';')
            sb.append(if (r.vin == null && r.vout == null) "Pas de mesure" else VictronTexts.state(r.state)).append(';')
            sb.append(VictronTexts.error(r.error) ?: "").append(';')
            sb.append(r.measuredAt?.let { h.format(Date(it)) } ?: "").append("\r\n")
        }
        return sb.toString()
    }

    private fun num(v: Double?, decimals: Int = 2) =
        v?.let { String.format(Locale.FRANCE, "%.${decimals}f", it) } ?: ""

    /** Fichier tenu à jour automatiquement après chaque mesure horaire. */
    fun updateAutoFile(context: Context) {
        try {
            val prefs = Prefs(context)
            val csv = buildCsv(HistoryDb.get(context).all(descending = false))
            val uri = write(context, AUTO_FILE, csv, reuseUri = prefs.autoCsvUri)
            if (uri != null) prefs.autoCsvUri = uri
        } catch (e: Exception) {
            LogBook.log(context, "CSV auto : échec ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Écrit le fichier ; renvoie l'URI MediaStore (Android 10+) pour réutilisation. */
    private fun write(context: Context, name: String, content: String, reuseUri: String?): String? {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            // 1) réécrire notre propre fichier existant
            if (reuseUri != null) {
                val ok = runCatching {
                    resolver.openOutputStream(Uri.parse(reuseUri), "wt")!!.use { it.write(bytes) }
                }.isSuccess
                if (ok) return reuseUri
            }
            // 2) retrouver un fichier du même nom créé par l'app
            if (reuseUri != null || name == AUTO_FILE) {
                findOwn(context, name)?.let { uri ->
                    val ok = runCatching { resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }.isSuccess
                    if (ok) return uri.toString()
                }
            }
            // 3) créer
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Impossible de créer le fichier")
            resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            return uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            File(dir, name).outputStream().use { o: OutputStream -> o.write(bytes) }
            return null
        }
    }

    private fun findOwn(context: Context, name: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "%${Environment.DIRECTORY_DOWNLOADS}/$FOLDER%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
                else null
            }
        }.getOrNull()
    }
}
