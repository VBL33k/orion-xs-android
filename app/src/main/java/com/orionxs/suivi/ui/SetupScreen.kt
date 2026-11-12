package com.orionxs.suivi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orionxs.suivi.data.SystemChecks

/** Assistant en 3 étapes au premier lancement. */
@Composable
fun SetupScreen(fix: FixActions, initialStep: Int = 0, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    val now = rememberNow(1000)
    val h = remember(now) { SystemChecks.check(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Indicateur d'étapes
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        when (step) {
            0 -> {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(70.dp))
                    }
                }
                Text("Bienvenue !", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Cette application affiche les tensions de votre chargeur Victron Orion XS et garde un historique heure par heure.\n\nNous allons la préparer en 3 petites étapes.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                BigButton("Commencer") { step = 1 }
            }
            1 -> {
                Text("Étape 1 : autorisations", style = MaterialTheme.typography.headlineMedium)
                Text("Appuyez sur chaque bouton bleu et répondez « Autoriser » à chaque question du téléphone.",
                    style = MaterialTheme.typography.bodyLarge)
                SetupItem(h.scanPerm && (!h.notifNeeded || h.notif) && (!h.storageNeeded || h.storage),
                    "Bluetooth et notifications", "Autoriser", fix.requestPermissions)
                if (h.bgLocationNeeded) SetupItem(h.bgLocation, "Localisation : choisir « Toujours autoriser »", "Ouvrir", fix.requestBackgroundLocation)
                SetupItem(h.bluetoothOn, "Allumer le Bluetooth", "Allumer", fix.enableBluetooth)
                if (h.locationNeeded) SetupItem(h.locationOn, "Activer la localisation", "Ouvrir", fix.openLocationSettings)
                SetupItem(h.batteryUnrestricted, "Fonctionner écran éteint (batterie « Non restreinte »)", "Autoriser", fix.requestBattery)
                Spacer(Modifier.height(4.dp))
                BigButton("Continuer", enabled = h.scanPerm) { step = 2 }
                if (!h.scanPerm) Text("Le bouton « Continuer » s'active quand l'accès au Bluetooth est autorisé.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            else -> {
                Text("Étape 2 : clé du chargeur", style = MaterialTheme.typography.headlineMedium)
                if (h.keyOk) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = c.ok, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("La clé est enregistrée.", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Étape 3 : c'est prêt ! Placez le téléphone près du chargeur.", style = MaterialTheme.typography.bodyLarge)
                    BigButton("Terminer", color = c.ok, onClick = onDone)
                } else {
                    SectionCard { KeyWhereToFind() }
                    SectionCard { KeyEditor() }
                }
                TextButton(onClick = { step = 1 }) { Text("← Retour", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun SetupItem(done: Boolean, label: String, action: String, onClick: () -> Unit) {
    val c = LocalOrion.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) Icon(Icons.Filled.CheckCircle, null, tint = c.ok, modifier = Modifier.size(30.dp))
            else StatusDot(c.warn, 22)
            Spacer(Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (!done) { Spacer(Modifier.height(12.dp)); BigButton(action, onClick = onClick) }
    }
}
