package com.orionxs.suivi.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orionxs.suivi.BuildConfig
import com.orionxs.suivi.ble.VictronDecoder
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.Prefs
import com.orionxs.suivi.data.SystemChecks
import com.orionxs.suivi.service.MonitorService

@Composable
fun HelpScreen(fix: FixActions) {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    val now = rememberNow(1500)
    val h = remember(now) { SystemChecks.check(ctx) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Aide")

        SectionCard {
            Text("Un souci ?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Appuyez sur le bouton vert, puis sur « Envoyer » dans WhatsApp. La personne qui vous aide recevra toutes les informations utiles.",
                style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            WhatsAppButton()
        }

        SectionCard {
            Text("Vérifications", style = MaterialTheme.typography.titleLarge)
            Text(if (h.allGood) "Tout est en ordre ✔" else "Appuyez sur « Corriger » pour chaque point rouge.",
                style = MaterialTheme.typography.bodyMedium, color = if (h.allGood) c.ok else c.muted)
            Spacer(Modifier.height(8.dp))
            CheckRow(h.scanPerm, "Accès au Bluetooth", "Autorisation « Appareils à proximité »", onFix = fix.requestPermissions)
            CheckRow(h.bluetoothOn, "Bluetooth allumé", onFix = fix.enableBluetooth)
            if (h.locationNeeded) CheckRow(h.locationOn, "Localisation activée", "Nécessaire au Bluetooth sur ce téléphone", onFix = fix.openLocationSettings)
            if (h.bgLocationNeeded) CheckRow(h.bgLocation, "Localisation « Toujours autoriser »", onFix = fix.requestBackgroundLocation)
            CheckRow(h.batteryUnrestricted && !h.backgroundRestricted, "Fonctionne écran éteint",
                "Batterie : choisir « Non restreinte »",
                onFix = if (h.backgroundRestricted) fix.openAppSettings else fix.requestBattery)
            if (h.notifNeeded) CheckRow(h.notif, "Notification de suivi", onFix = fix.requestPermissions)
            if (h.storageNeeded) CheckRow(h.storage, "Enregistrement des fichiers", onFix = fix.requestPermissions)
            CheckRow(h.exactAlarms, "Réveils réguliers", onFix = fix.openAlarmSettings)
            CheckRow(h.keyOk, "Clé du chargeur", onFix = null)
            CheckRow(LiveState.serviceRunning, "Surveillance en marche", fixLabel = "Relancer", onFix = { MonitorService.start(ctx) })
        }

        SectionCard {
            Text("Clé du chargeur", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            KeyEditor()
        }
        SectionCard {
            KeyWhereToFind()
        }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Masquer les réglages avancés" else "Réglages avancés", style = MaterialTheme.typography.labelLarge)
        }
        if (showAdvanced) AdvancedCard()

        Text("Orion XS • version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = c.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
    }
}

/** Saisie de la clé : le plus simple est de copier le message reçu sur WhatsApp puis « Coller ». */
@Composable
fun KeyEditor(onSaved: () -> Unit = {}) {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember { Prefs(ctx) }
    var current by remember { mutableStateOf(prefs.keyHex) }
    var text by remember { mutableStateOf("") }
    val parsed = Prefs.extractKey(text)

    if (VictronDecoder.parseKey(current) != null) {
        Text("Clé enregistrée : ${current.take(4)}••••••••${current.takeLast(4)}", style = MaterialTheme.typography.bodyLarge, color = c.ok)
    } else {
        Text("Aucune clé enregistrée.", style = MaterialTheme.typography.bodyLarge, color = c.bad)
    }
    Spacer(Modifier.height(4.dp))
    Text("Collez ou tapez la clé de 32 caractères (chiffres et lettres de a à f).",
        style = MaterialTheme.typography.bodyMedium, color = c.muted)
    Spacer(Modifier.height(12.dp))
    BigButton("Coller la clé", Icons.Filled.ContentPaste) {
        val clip = clipboard.getText()?.text
        val k = Prefs.extractKey(clip)
        if (k == null) Toast.makeText(ctx, "Aucune clé trouvée dans le presse-papiers", Toast.LENGTH_LONG).show()
        text = k ?: (clip ?: "")
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        label = { Text("ou tapez la clé ici") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (text.isNotBlank()) Text(if (parsed != null) "Clé valide ✔" else "Il faut 32 caractères (0-9, a-f)",
                color = if (parsed != null) c.ok else c.bad)
        },
    )
    Spacer(Modifier.height(8.dp))
    BigButton("Enregistrer la clé", enabled = parsed != null) {
        prefs.keyHex = parsed!!
        current = parsed
        text = ""
        LogBook.log(ctx, "Nouvelle clé enregistrée (${parsed.take(2)}…${parsed.takeLast(2)})")
        MonitorService.start(ctx, MonitorService.ACTION_RELOAD)
        Toast.makeText(ctx, "Clé enregistrée", Toast.LENGTH_SHORT).show()
        onSaved()
    }
}

@Composable
private fun AdvancedCard() {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    val prefs = remember { Prefs(ctx) }
    var phone by remember { mutableStateOf(prefs.supportPhone) }
    SectionCard {
        Text("Réglages avancés", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = phone, onValueChange = { phone = it.filter { ch -> ch.isDigit() } },
            label = { Text("N° WhatsApp de l'aidant (ex. 33612345678)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SmallAction("Enregistrer le numéro") {
            prefs.supportPhone = phone
            Toast.makeText(ctx, "Numéro enregistré", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(14.dp))
        Text("Dernier chargeur : ${prefs.lastDevice ?: "aucun"}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Text("Appareils Victron vus : ${LiveState.devices.size}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        LiveState.devices.values.forEach {
            Row { Spacer(Modifier.width(8.dp)); Text("• ${it.address} – ${it.status}", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        }
    }
}

/** Où trouver la clé dans l'application VictronConnect. */
@Composable
fun KeyWhereToFind() {
    Text("Où trouver la clé ?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text("Elle se trouve dans l'application VictronConnect :", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    listOf(
        "Ouvrez VictronConnect, près du chargeur, et touchez votre Orion XS.",
        "Touchez la roue dentée ⚙ en haut à droite, puis les 3 petits points ⋮, puis « Info produit ».",
        "Vérifiez que « Lecture instantanée via Bluetooth » est activée.",
        "À la ligne « Détails de la lecture instantanée », touchez « Afficher » : la clé apparaît (32 caractères).",
        "Copiez-la ou recopiez-la, puis revenez ici.",
    ).forEachIndexed { i, step ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.width(28.dp))
            Text(step, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
