package com.orionxs.suivi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orionxs.suivi.data.CsvExporter
import com.orionxs.suivi.data.HistoryDb
import com.orionxs.suivi.data.HourRow
import com.orionxs.suivi.data.LiveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RANGES = listOf("24 h" to 24, "7 jours" to 24 * 7, "30 jours" to 24 * 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val c = LocalOrion.current
    val version by LiveState.historyVersion.collectAsState()
    var rangeIdx by remember { mutableIntStateOf(0) }
    val hours = RANGES[rangeIdx].second
    val allRows by produceState(emptyList<HourRow>(), version) {
        value = withContext(Dispatchers.IO) { HistoryDb.get(ctx).all(descending = true) }
    }
    val chartStart = System.currentTimeMillis() - hours * 3_600_000L
    val chartRows = remember(allRows, hours) { allRows.filter { it.hour >= chartStart }.sortedBy { it.hour } }
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRANCE) }
    val hourFmt = remember { SimpleDateFormat("HH 'h'", Locale.FRANCE) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Historique", "Une mesure enregistrée chaque heure") }
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RANGES.forEachIndexed { i, (label, _) ->
                        FilterChip(selected = i == rangeIdx, onClick = { rangeIdx = i },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Legend("Entrée", c.input); Legend("Sortie", c.output)
                }
                Spacer(Modifier.height(8.dp))
                VoltageChart(chartRows, chartStart, c.input, c.output)
            }
        }
        item {
            Text(
                "Le fichier « ${CsvExporter.AUTO_FILE} » est mis à jour automatiquement à chaque mesure, dans Téléchargements/OrionXS.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (allRows.isEmpty()) {
            item {
                SectionCard { Text("Aucune mesure pour l'instant. La première sera enregistrée dès que le chargeur sera détecté.", style = MaterialTheme.typography.bodyLarge) }
            }
        }
        val byDay = allRows.groupBy { dayFmt.format(Date(it.hour)) }
        byDay.forEach { (day, rows) ->
            item(key = "d$day") {
                Text(day.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
            item(key = "c$day") {
                SectionCard {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HistoryRow(hourFmt.format(Date(row.hour)), row)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(time: String, row: HourRow) {
    val c = LocalOrion.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        if (row.vin == null && row.vout == null) {
            Text("pas de mesure", style = MaterialTheme.typography.bodyLarge, color = c.muted)
        } else {
            Text("${fmtV(row.vin)} V", style = MaterialTheme.typography.bodyLarge, color = c.input, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("→", color = c.muted, modifier = Modifier.padding(horizontal = 6.dp))
            Text("${fmtV(row.vout)} V", style = MaterialTheme.typography.bodyLarge, color = c.output, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).height(5.dp).padding(0.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawRect(color) }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VoltageChart(rows: List<HourRow>, start: Long, inColor: Color, outColor: Color) {
    val measurer = rememberTextMeasurer()
    val muted = LocalOrion.current.muted
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = rows.flatMap { listOfNotNull(it.vin, it.vout) }
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Pas encore de données sur cette période", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        return
    }
    var lo = values.min(); var hi = values.max()
    if (hi - lo < 1.0) { val mid = (hi + lo) / 2; lo = mid - 0.5; hi = mid + 0.5 }
    val pad = (hi - lo) * 0.1; lo -= pad; hi += pad
    val end = System.currentTimeMillis()
    val labelStyle = TextStyle(fontSize = 13.sp, color = muted)
    val single = rows.count { it.vin != null || it.vout != null } == 1

    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val left = 56f; val bottom = size.height - 8f; val top = 8f
        val w = size.width - left
        fun x(t: Long) = left + w * ((t - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
        fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val txt = measurer.measure(String.format(Locale.FRANCE, "%.1f", v), labelStyle)
            drawText(txt, topLeft = Offset(0f, (yy - txt.size.height / 2).coerceIn(0f, size.height - txt.size.height)))
        }
        fun series(sel: (HourRow) -> Double?, color: Color) {
            val path = Path(); var prev: Long? = null
            rows.forEach { r ->
                val v = sel(r)
                if (v == null) { prev = null; return@forEach }
                val px = x(r.hour); val py = y(v)
                if (prev == null || r.hour - prev!! > 3 * 3_600_000L) path.moveTo(px, py) else path.lineTo(px, py)
                if (single || rows.size < 40) drawCircle(color, 5f, Offset(px, py))
                prev = r.hour
            }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        series({ it.vin }, inColor)
        series({ it.vout }, outColor)
    }
}
