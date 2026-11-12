package com.orionxs.suivi.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import com.orionxs.suivi.BuildConfig
import com.orionxs.suivi.ble.VictronTexts
import com.orionxs.suivi.data.HistoryDb
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.Prefs
import com.orionxs.suivi.data.SystemChecks
import com.orionxs.suivi.ui.fmtV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rapport de diagnostic envoyé par WhatsApp à la personne qui aide. */
object Diagnostics {
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)

    private fun ago(t: Long): String {
        if (t <= 0L) return "jamais"
        val s = (System.currentTimeMillis() - t) / 1000
        return when {
            s < 120 -> "il y a $s s"
            s < 7200 -> "il y a ${s / 60} min"
            else -> "il y a ${s / 3600} h"
        }
    }

    private fun yn(b: Boolean) = if (b) "✅" else "❌"

    fun build(context: Context): String {
        val h = SystemChecks.check(context)
        val p = Prefs(context)
        val sb = StringBuilder()
        sb.appendLine("🔧 *Diagnostic Orion XS* — ${fmt.format(Date())}")
        sb.appendLine("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine()
        sb.appendLine("*Vérifications*")
        sb.appendLine("${yn(h.scanPerm)} Autorisation Bluetooth")
        sb.appendLine("${yn(h.bluetoothOn)} Bluetooth allumé")
        if (h.locationNeeded) sb.appendLine("${yn(h.locationOn)} Localisation activée")
        if (h.bgLocationNeeded) sb.appendLine("${yn(h.bgLocation)} Localisation « toujours »")
        sb.appendLine("${yn(h.batteryUnrestricted)} Batterie non restreinte")
        sb.appendLine("${yn(!h.backgroundRestricted)} Arrière-plan autorisé")
        if (h.notifNeeded) sb.appendLine("${yn(h.notif)} Notifications")
        if (h.storageNeeded) sb.appendLine("${yn(h.storage)} Stockage")
        sb.appendLine("${yn(h.exactAlarms)} Alarmes exactes")
        val key = p.keyHex
        sb.appendLine("${yn(h.keyOk)} Clé (${if (key.length >= 4) key.take(2) + "…" + key.takeLast(2) + ", ${key.length} car." else "absente"})")
        sb.appendLine()
        sb.appendLine("*Service*")
        sb.appendLine("En marche : ${yn(LiveState.serviceRunning)} depuis ${ago(LiveState.serviceStartedAt)}")
        sb.appendLine("Scan : ${if (LiveState.scanning) "actif" else "arrêté"} (${if (LiveState.scanFiltered) "filtré" else "complet"}, lancé ${ago(LiveState.scanStartedAt)})")
        LiveState.lastScanError?.let { sb.appendLine("Erreur scan : $it") }
        sb.appendLine("Appareils BLE reçus : ${LiveState.bleSeen.get()} • annonces Victron : ${LiveState.victronAdverts.get()}")
        sb.appendLine("Dernière donnée décodée : ${ago(LiveState.lastVictronAt)}")
        if (LiveState.lastKeyMismatchAt > 0) sb.appendLine("⚠️ Clé refusée ${ago(LiveState.lastKeyMismatchAt)}")
        LiveState.reading?.let { r ->
            sb.appendLine("Mesure : entrée ${fmtV(r.inputVoltage)} V, sortie ${fmtV(r.outputVoltage)} V, " +
                "${VictronTexts.state(r.state)}${VictronTexts.error(r.error)?.let { ", $it" } ?: ""}" +
                "${VictronTexts.offReason(r.offReason)?.let { ", arrêt : $it" } ?: ""}")
        }
        sb.appendLine()
        sb.appendLine("*Appareils Victron vus*")
        if (LiveState.devices.isEmpty()) sb.appendLine("Aucun")
        LiveState.devices.values.sortedByDescending { it.lastSeen }.take(8).forEach {
            sb.appendLine("• ${it.address} ${VictronTexts.recordType(it.recordType)} 0x${it.modelId.toString(16)} " +
                "→ ${it.status}, ${it.rssi} dBm, ${it.count}×, ${ago(it.lastSeen)}")
        }
        sb.appendLine()
        val db = HistoryDb.get(context)
        val last = db.all(descending = true).firstOrNull { it.vin != null }
        sb.appendLine("*Historique* : ${db.countValid()} mesures / ${db.count()} heures • dernière : ${last?.let { fmt.format(Date(it.hour)) } ?: "aucune"}")
        sb.appendLine()
        sb.appendLine("*Journal*")
        LogBook.tail(context, 60).forEach { sb.appendLine(it) }
        return sb.toString().take(30_000)
    }

    /** Ouvre WhatsApp avec le rapport (directement dans la conversation si un numéro est connu). */
    fun shareViaWhatsApp(context: Context) {
        LogBook.log(context, "Envoi d'un diagnostic")
        val text = build(context)
        val phone = Prefs(context).supportPhone.filter { it.isDigit() }
        fun intent(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (pkg != null) setPackage(pkg)
            if (pkg != null && phone.isNotEmpty()) putExtra("jid", "$phone@s.whatsapp.net")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try { context.startActivity(intent(pkg)); return } catch (_: ActivityNotFoundException) {}
        }
        // WhatsApp absent : choix libre (SMS, e-mail…)
        context.startActivity(Intent.createChooser(intent(null), "Envoyer le diagnostic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
