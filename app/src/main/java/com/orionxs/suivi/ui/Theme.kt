package com.orionxs.suivi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

@Immutable
data class OrionColors(
    val input: Color,
    val inputSoft: Color,
    val output: Color,
    val outputSoft: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val whatsapp: Color,
    val muted: Color,
)

private val LightOrion = OrionColors(
    input = Color(0xFF1D5FD1), inputSoft = Color(0xFFE6EEFC),
    output = Color(0xFFD9530B), outputSoft = Color(0xFFFCEDE3),
    ok = Color(0xFF16894D), warn = Color(0xFFB87503), bad = Color(0xFFC93636),
    whatsapp = Color(0xFF1DA851), muted = Color(0xFF5B6478),
)
private val DarkOrion = OrionColors(
    input = Color(0xFF7FAEFF), inputSoft = Color(0xFF1B2A47),
    output = Color(0xFFFF9A5C), outputSoft = Color(0xFF3A2414),
    ok = Color(0xFF4CD48A), warn = Color(0xFFF2B33D), bad = Color(0xFFFF7070),
    whatsapp = Color(0xFF25D366), muted = Color(0xFFA3ABBD),
)

val LocalOrion = staticCompositionLocalOf { LightOrion }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D5FD1), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FB), onPrimaryContainer = Color(0xFF0C2557),
    secondaryContainer = Color(0xFFDCE7FB), onSecondaryContainer = Color(0xFF0C2557),
    background = Color(0xFFF2F4F8), onBackground = Color(0xFF141B2D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF141B2D),
    surfaceVariant = Color(0xFFE8EBF2), onSurfaceVariant = Color(0xFF4A5368),
    surfaceContainer = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFFD9DEE8),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8DB6FF), onPrimary = Color(0xFF07214F),
    primaryContainer = Color(0xFF1C335E), onPrimaryContainer = Color(0xFFDCE7FB),
    secondaryContainer = Color(0xFF1C335E), onSecondaryContainer = Color(0xFFDCE7FB),
    background = Color(0xFF0E121B), onBackground = Color(0xFFE8ECF5),
    surface = Color(0xFF171C28), onSurface = Color(0xFFE8ECF5),
    surfaceVariant = Color(0xFF232A3A), onSurfaceVariant = Color(0xFFB4BCCD),
    surfaceContainer = Color(0xFF171C28),
    outlineVariant = Color(0xFF2E3647),
)

// Textes volontairement grands, pour une lecture confortable.
private val BigTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        bodyLarge = bodyLarge.copy(fontSize = 19.sp, lineHeight = 27.sp),
        bodyMedium = bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        labelLarge = labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 15.sp),
    )
}

val BigNumber = TextStyle(fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)

@Composable
fun OrionTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalOrion provides if (dark) DarkOrion else LightOrion) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = BigTypography, content = content)
    }
}

// ------------------------------------------------------------ Formatage

fun fmtV(v: Double?): String = v?.let { String.format(Locale.FRANCE, "%.2f", it) } ?: "--"
fun fmtA(v: Double?): String = v?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: "--"

fun agoText(t: Long, now: Long = System.currentTimeMillis()): String {
    if (t <= 0L) return "jamais"
    val s = ((now - t) / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> "à l'instant"
        s < 60 -> "il y a $s secondes"
        s < 120 -> "il y a 1 minute"
        s < 3600 -> "il y a ${s / 60} minutes"
        s < 7200 -> "il y a 1 heure"
        s < 86400 -> "il y a ${s / 3600} heures"
        else -> "il y a ${s / 86400} jour(s)"
    }
}
