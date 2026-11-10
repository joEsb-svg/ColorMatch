@file:OptIn(ExperimentalMaterial3Api::class)

package it.joesb.colormatch

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private enum class Period(val label: String, val days: Int?) {
    Today("Oggi", 0), Week("7 giorni", 6), Month("30 giorni", 29), All("Tutto", null)
}

private enum class Outcome(val label: String) { All("Tutte"), Ok("Conformi"), Bad("Non conformi") }

private val rowDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

@Composable
fun HistoryScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var period by remember { mutableStateOf(Period.Week) }
    var outcome by remember { mutableStateOf(Outcome.All) }
    var toDelete by remember { mutableStateOf<Check?>(null) }

    val from = period.days?.let { startOfToday() - it * 24L * 3600L * 1000L } ?: 0L
    val inPeriod = store.history.filter { it.time >= from }
    val shown = inPeriod.filter {
        when (outcome) {
            Outcome.All -> true
            Outcome.Ok -> it.ok
            Outcome.Bad -> !it.ok
        }
    }
    val okCount = inPeriod.count { it.ok }

    fun export(pdf: Boolean) {
        try {
            val label = period.label + if (outcome != Outcome.All) " · " + outcome.label.lowercase() else ""
            val f = if (pdf) Export.pdf(ctx, shown, label) else Export.csv(ctx, shown)
            Export.share(ctx, f, if (pdf) "application/pdf" else "text/csv")
        } catch (e: Exception) {
            Toast.makeText(ctx, "Esportazione non riuscita: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(containerColor = Color.Transparent, topBar = { Bar("Storico verifiche", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Outcome.entries.forEach { o ->
                        FilterChip(selected = outcome == o, onClick = { outcome = o }, label = { Text(o.label) })
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Stat("Verifiche", inPeriod.size.toString())
                            Stat("Conformi", okCount.toString())
                            Stat("Non conformi", (inPeriod.size - okCount).toString())
                            Stat(
                                "Conformità",
                                if (inPeriod.isEmpty()) "–" else fmt(okCount * 100.0 / inPeriod.size, 0) + "%",
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradientButton("EXCEL (CSV)", { export(false) }, Modifier.weight(1f), enabled = shown.isNotEmpty(), height = 44.dp)
                            OutlinedButton(onClick = { export(true) }, enabled = shown.isNotEmpty(), modifier = Modifier.weight(1f).height(44.dp)) {
                                Text("Report PDF")
                            }
                        }
                        Text(
                            "Si esportano le ${shown.size} verifiche mostrate qui sotto.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Nessuna verifica in questo periodo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { c -> CheckRow(c) { toDelete = c } }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare questa verifica?") },
            text = { Text("${c.color} — ${rowDate.format(Date(c.time))}") },
            confirmButton = {
                TextButton(onClick = { store.deleteChecks(setOf(c.id)); toDelete = null }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun CheckRow(c: Check, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (c.ok) OkGreen else BadRed))
            Spacer(Modifier.width(10.dp))
            Swatch(c.lab, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.color, fontWeight = FontWeight.SemiBold)
                Text("${c.buttonType} · ${c.material}", style = MaterialTheme.typography.bodySmall)
                Text(
                    rowDate.format(Date(c.time)) + if (c.lot.isNotBlank()) " · lotto ${c.lot}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ΔE ${fmt(c.deltaE, 2)}", fontWeight = FontWeight.Bold, color = if (c.ok) OkGreen else BadRed)
                Text(if (c.ok) "conforme" else "non conforme", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Elimina") }
        }
    }
}
