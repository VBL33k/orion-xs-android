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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orionxs.suivi.ble.VictronTexts
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.SystemChecks

private enum class Level { OK, WARN, BAD }

@Composable
fun LiveScreen(fix: FixActions, onOpenHelp: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    val now = rememberNow()
    val health = remember(now / 2000) { SystemChecks.check(ctx) }
    val r = LiveState.reading
    val age = r?.let { now - it.timestamp }
    val fresh = age != null && age < 120_000
    val keyRefused = now - LiveState.lastKeyMismatchAt < 90_000 && !fresh
    val searchingLong = LiveState.serviceStartedAt > 0 && now - LiveState.serviceStartedAt > 120_000

    val (level, title, subtitle) = when {
        !health.keyOk -> Triple(Level.BAD, "Clé du chargeur manquante", "Allez dans l'onglet Aide")
        !health.scanPerm -> Triple(Level.BAD, "Autorisation Bluetooth manquante", null)
        !health.bluetoothOn -> Triple(Level.BAD, "Bluetooth éteint", null)
        health.locationNeeded && !health.locationOn -> Triple(Level.BAD, "Localisation désactivée", null)
        fresh -> Triple(Level.OK, "En direct", "Mis à jour " + agoText(r!!.timestamp, now))
        keyRefused -> Triple(Level.BAD, "Clé incorrecte", "Le chargeur est vu mais la clé ne correspond pas")
        r != null -> Triple(Level.WARN, "Chargeur hors de portée", "Dernière mesure " + agoText(r.timestamp, now))
        else -> Triple(Level.WARN, "Recherche du chargeur…", "Restez près du chargeur")
    }
    val levelColor = when (level) { Level.OK -> c.ok; Level.WARN -> c.warn; Level.BAD -> c.bad }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Chargeur Orion XS")

        // Pastille d'état
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(levelColor.copy(alpha = 0.12f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(levelColor, 18)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = levelColor)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val dim = !fresh
        VoltageCard("Tension d'entrée", r?.inputVoltage, c.input, c.inputSoft, Icons.Filled.ElectricCar, dim)

        // Liaison entrée -> sortie avec l'état de charge
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowDownward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r != null) {
                Spacer(Modifier.width(12.dp))
                val charging = VictronTexts.isCharging(r.state)
                Text(
                    VictronTexts.state(r.state) + (r.outputCurrent?.takeIf { charging }?.let { " • ${fmtA(it)} A" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (charging) c.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        VoltageCard("Tension de sortie", r?.outputVoltage, c.output, c.outputSoft, Icons.Filled.BatteryChargingFull, dim)

        // Erreur ou raison d'arrêt du chargeur
        if (r != null) {
            VictronTexts.error(r.error)?.let { InfoBanner(Icons.Filled.Warning, "Alarme du chargeur : $it", c.bad) }
            if (r.state == 0) VictronTexts.offReason(r.offReason)?.let {
                InfoBanner(Icons.Filled.Warning, "Chargeur à l'arrêt : $it", c.warn)
            }
        }

        // Problèmes et solutions
        when {
            !health.keyOk -> ProblemCard("La clé du chargeur n'est pas encore enregistrée.") { BigButton("Ouvrir l'aide", onClick = onOpenHelp) }
            !health.scanPerm -> ProblemCard("L'application a besoin de l'autorisation « Appareils à proximité ».") {
                BigButton("Autoriser", Icons.Filled.Bluetooth, onClick = fix.requestPermissions)
            }
            !health.bluetoothOn -> ProblemCard("Le Bluetooth du téléphone est éteint.") {
                BigButton("Allumer le Bluetooth", Icons.Filled.Bluetooth, onClick = fix.enableBluetooth)
            }
            health.locationNeeded && !health.locationOn -> ProblemCard("Sur ce téléphone, la localisation doit être activée pour le Bluetooth.") {
                BigButton("Activer la localisation", onClick = fix.openLocationSettings)
            }
            keyRefused || (!fresh && searchingLong) -> ProblemCard(
                if (keyRefused) "Le chargeur est trouvé, mais la clé ne correspond pas. Envoyez un diagnostic."
                else "Le chargeur ne répond pas. Approchez-vous du chargeur. Si cela dure, envoyez un diagnostic."
            ) { WhatsAppButton() }
            !health.batteryUnrestricted || health.backgroundRestricted -> ProblemCard(
                "Pour que les mesures continuent écran éteint, autorisez l'application à fonctionner en arrière-plan.",
                color = c.warn,
            ) { BigButton("Autoriser l'arrière-plan", onClick = if (health.backgroundRestricted) fix.openAppSettings else fix.requestBattery) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VoltageCard(label: String, value: Double?, accent: Color, soft: Color, icon: ImageVector, dim: Boolean) {
    SectionCard(container = soft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        val color = if (dim) accent.copy(alpha = 0.45f) else accent
        Text(
            buildAnnotatedString {
                append(fmtV(value))
                withStyle(SpanStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold)) { append(" V") }
            },
            style = BigNumber, color = color,
        )
    }
}

@Composable
private fun InfoBanner(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.12f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProblemCard(text: String, color: Color = LocalOrion.current.bad, action: @Composable () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(14.dp))
        action()
    }
}
