@file:OptIn(ExperimentalMaterial3Api::class)

package it.joesb.colormatch

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(store: Store, go: (Screen) -> Unit) {
    val data = store.data
    Scaffold(topBar = { Bar("ColorMatch", null) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (data.profile == null) {
                InfoCard("La cabina non è ancora calibrata. Fallo una volta prima di creare campioni e verificare.", Color(0xFF3A2F12)) {
                    Button(onClick = { go(Screen.Calibrate) }) { Text("Calibra cabina") }
                }
            }
            BigAction("Verifica pezzo", "Scegli tasto, materiale e colore e controlla se è conforme") { go(Screen.Verify) }
            val today = store.history.count { isSameDay(it.time, System.currentTimeMillis()) }
            BigAction("Storico verifiche", "${store.history.size} verifiche · $today oggi · esporta in Excel o PDF") { go(Screen.History) }
            BigAction("Campioni di riferimento", "${data.refs.size} campioni salvati") { go(Screen.Refs) }
            BigAction("Impostazioni", "Cabina, materiali e soglie ΔE, tipi di tasto") { go(Screen.Settings) }
        }
    }
}

@Composable
private fun BigAction(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

// ---------------------------------------------------------------- Verifica

@Composable
fun VerifyScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var type by remember { mutableStateOf<String?>(null) }
    var material by remember { mutableStateOf<String?>(null) }
    var refId by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Measurement?>(null) }
    var failed by remember { mutableStateOf(false) }
    var lot by remember { mutableStateOf("") }
    val controller = remember { MeasureController() }

    val refs = data.refs
    val types = refs.map { it.buttonType }.distinct().sorted()
    val materials = refs.filter { it.buttonType == type }.map { it.material }.distinct().sorted()
    val colors = refs.filter { it.buttonType == type && it.material == material }.sortedBy { it.color }
    val ref = refs.firstOrNull { it.id == refId }

    Scaffold(topBar = { Bar("Verifica pezzo", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (data.profile == null) {
                InfoCard("Prima calibra la cabina.", Color(0xFF3A2F12)) {
                    Button(onClick = { go(Screen.Calibrate) }) { Text("Calibra cabina") }
                }
                return@Column
            }
            if (refs.isEmpty()) {
                InfoCard("Non ci sono ancora campioni di riferimento.") {
                    Button(onClick = { go(Screen.NewRef) }) { Text("Crea il primo campione") }
                }
                return@Column
            }
            Picker("Tipo tasto", types, type, { type = it; material = null; refId = null; result = null })
            Picker("Materiale", materials, material, { material = it; refId = null; result = null })
            Picker("Colore", colors.map { it.color }, ref?.color, { c ->
                refId = colors.firstOrNull { it.color == c }?.id
                result = null
            })

            if (ref != null) {
                if (ref.profileId != data.profile.id) {
                    InfoCard(
                        "Questo campione è stato creato con una calibrazione cabina diversa: il confronto non è affidabile. Rifai il campione.",
                        Color(0xFF3A2F12),
                    )
                }
                CameraPermissionGate {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CameraPanel(controller, CamMode.PROFILE, data.profile)
                        ExposureStatus(controller)
                        LiveReadout(controller)
                        OutlinedTextField(
                            value = lot,
                            onValueChange = { lot = it },
                            label = { Text("Lotto / ordine (facoltativo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MeasureButton(controller) { m ->
                            result = m
                            failed = m == null
                            if (m != null) {
                                val threshold = data.thresholdFor(ref.material)
                                val de = ColorMath.deltaE2000(ref.lab, m.lab)
                                store.addCheck(
                                    Check(
                                        id = UUID.randomUUID().toString(),
                                        time = System.currentTimeMillis(),
                                        lot = lot.trim(),
                                        buttonType = ref.buttonType,
                                        material = ref.material,
                                        color = ref.color,
                                        refLab = ref.lab,
                                        lab = m.lab,
                                        deltaE = de,
                                        threshold = threshold,
                                        ok = de <= threshold,
                                    )
                                )
                            }
                        }
                    }
                }
                if (failed) {
                    InfoCard("Misura non riuscita: troppi riflessi o pezzo fuori dal riquadro. Riprova.", Color(0xFF3A2F12))
                }
                result?.let { ResultCard(ref, it, data.thresholdFor(ref.material)) }
            }
        }
    }
}

@Composable
private fun ResultCard(ref: Reference, m: Measurement, threshold: Double) {
    val de = ColorMath.deltaE2000(ref.lab, m.lab)
    val ok = de <= threshold
    Card(
        colors = CardDefaults.cardColors(containerColor = if (ok) OkGreen else BadRed),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (ok) "CONFORME" else "NON CONFORME",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                "ΔE00 = ${fmt(de, 2)}   (soglia ${fmt(threshold)})",
                fontSize = 18.sp,
                color = Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Swatch(ref.lab, 56.dp)
                    Text("Campione", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Swatch(m.lab, 56.dp)
                    Text("Pezzo", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            val hints = differenceHints(ref.lab, m.lab)
            if (hints.isNotEmpty()) {
                Text("Il pezzo è " + hints.joinToString(", "), color = Color.White, textAlign = TextAlign.Center)
            }
            val warn = buildList {
                if (m.spread > 0.8) add("misura instabile: tieni fermo il telefono")
                if (m.excluded > 0.5) add("molti riflessi esclusi: inclina un po' il pezzo")
                if (m.saturated > 0.05) add("zone bruciate nel riquadro")
            }
            if (warn.isNotEmpty()) {
                Text("⚠ " + warn.joinToString("; "), color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Text("Salvata nello storico", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------- Campioni

@Composable
fun RefsScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var toDelete by remember { mutableStateOf<Reference?>(null) }
    Scaffold(
        topBar = { Bar("Campioni di riferimento", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go(Screen.NewRef) }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuovo campione")
            }
        },
    ) { pad ->
        if (data.refs.isEmpty()) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                InfoCard("Nessun campione. Premi + e misura un pezzo buono per ogni combinazione tasto / materiale / colore.")
            }
        } else {
            val sorted = data.refs.sortedWith(compareBy({ it.buttonType }, { it.material }, { it.color }))
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.id }) { r ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Swatch(r.lab, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.color, fontWeight = FontWeight.SemiBold)
                                Text("${r.buttonType} · ${r.material}", style = MaterialTheme.typography.bodySmall)
                                val stale = data.profile != null && r.profileId != data.profile.id
                                Text(
                                    "${r.samples} misure · dispersione ΔE ${fmt(r.spread, 2)}" +
                                        if (stale) " · DA RIFARE (cabina ricalibrata)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (stale) WarnAmber else MaterialTheme.colorScheme.secondary,
                                )
                            }
                            IconButton(onClick = { toDelete = r }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare il campione?") },
            text = { Text("${r.color} — ${r.buttonType} · ${r.material}") },
            confirmButton = {
                TextButton(onClick = {
                    store.update { d -> d.copy(refs = d.refs.filterNot { it.id == r.id }) }
                    toDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
fun NewRefScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var type by remember { mutableStateOf(data.buttonTypes.firstOrNull()) }
    var material by remember { mutableStateOf(data.materials.firstOrNull()?.name) }
    var color by remember { mutableStateOf("") }
    val samples = remember { mutableStateListOf<Measurement>() }
    var failed by remember { mutableStateOf(false) }
    val controller = remember { MeasureController() }

    Scaffold(topBar = { Bar("Nuovo campione", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val profile = data.profile
            if (profile == null) {
                InfoCard("Prima calibra la cabina: i campioni devono essere misurati sempre con gli stessi parametri.", Color(0xFF3A2F12)) {
                    Button(onClick = { go(Screen.Calibrate) }) { Text("Calibra cabina") }
                }
                return@Column
            }
            Picker("Tipo tasto", data.buttonTypes, type, { type = it })
            Picker("Materiale", data.materials.map { it.name }, material, { material = it })
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Colore (es. Bianco RAL 9010, Antracite…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Metti in cabina un pezzo BUONO, inquadralo nel riquadro e premi Aggiungi misura. " +
                    "Meglio 3–5 misure, anche su pezzi diversi dello stesso lotto approvato.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            CameraPermissionGate {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CameraPanel(controller, CamMode.PROFILE, profile)
                    ExposureStatus(controller)
                    LiveReadout(controller)
                    MeasureButton(controller, label = "AGGIUNGI MISURA (${samples.size})") { m ->
                        failed = m == null
                        if (m != null) samples.add(m)
                    }
                }
            }
            if (failed) InfoCard("Misura non riuscita: troppi riflessi. Riprova.", Color(0xFF3A2F12))

            if (samples.isNotEmpty()) {
                val avg = ColorMath.average(samples.map { it.lab })
                val spread = samples.maxOf { ColorMath.deltaE2000(avg, it.lab) }
                InfoCard("Media di ${samples.size} misure: L ${fmt(avg.l)}  a ${fmt(avg.a)}  b ${fmt(avg.b)}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(avg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Dispersione max ΔE ${fmt(spread, 2)}" +
                                if (spread > data.thresholdFor(material ?: "") / 2) "  ⚠ misure poco coerenti" else "",
                        )
                    }
                    OutlinedButton(onClick = { samples.removeAt(samples.size - 1) }) { Text("Rimuovi ultima") }
                }
                val existing = data.refs.firstOrNull {
                    it.buttonType == type && it.material == material && it.color.equals(color.trim(), ignoreCase = true)
                }
                if (existing != null) {
                    Text("Esiste già questo campione: verrà sostituito.", color = WarnAmber, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = type != null && material != null && color.isNotBlank(),
                    onClick = {
                        val ref = Reference(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            buttonType = type!!,
                            material = material!!,
                            color = color.trim(),
                            lab = avg,
                            samples = samples.size,
                            spread = spread,
                            profileId = profile.id,
                            createdAt = System.currentTimeMillis(),
                        )
                        store.update { d -> d.copy(refs = d.refs.filterNot { it.id == ref.id } + ref) }
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("SALVA CAMPIONE", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ---------------------------------------------------------------- Calibrazione cabina

@Composable
fun CalibrateScreen(store: Store, onBack: () -> Unit) {
    val data = store.data
    val controller = remember { MeasureController() }
    val capture by controller.capture.collectAsState()
    val ready by controller.cameraReady.collectAsState()
    val manualSensor by controller.manualSensor.collectAsState()
    val manualPost by controller.manualPost.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    fun save() {
        val c = capture ?: return
        val exp = c.exposureNs ?: return
        val iso = c.iso ?: return
        store.update { d ->
            d.copy(profile = CameraProfile(System.currentTimeMillis(), exp, iso, c.gains, c.transform))
        }
        onBack()
    }

    Scaffold(topBar = { Bar("Calibra cabina", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Accendi la luce della cabina e appoggia un cartoncino grigio neutro (va bene anche un foglio bianco opaco) " +
                    "dove metterai i pezzi. Tieni il telefono alla distanza che userai sempre, aspetta che l'immagine si stabilizzi e salva.",
                style = MaterialTheme.typography.bodyMedium,
            )
            CameraPermissionGate {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CameraPanel(controller, CamMode.AUTO, null)
                    LiveReadout(controller)
                }
            }
            val c = capture
            if (c?.exposureNs != null && c.iso != null) {
                val denom = (1_000_000_000.0 / c.exposureNs).roundToInt()
                Text("Esposizione 1/$denom s · ISO ${c.iso}", style = MaterialTheme.typography.bodyMedium)
            }
            if (ready) {
                Text(
                    when {
                        manualSensor && manualPost -> "✓ Il telefono supporta esposizione e colore manuali: misure ripetibili."
                        manualSensor -> "✓ Esposizione manuale supportata, bilanciamento del bianco solo bloccabile."
                        else -> "⚠ Esposizione manuale non supportata: in verifica userai il blocco esposizione."
                    },
                    color = if (manualSensor) OkGreen else WarnAmber,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                enabled = c?.exposureNs != null && c.iso != null,
                onClick = { if (data.refs.isNotEmpty() && data.profile != null) confirm = true else save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("SALVA PROFILO CABINA", fontWeight = FontWeight.Bold) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Ricalibrare la cabina?") },
            text = { Text("I ${data.refs.size} campioni esistenti sono stati misurati con la calibrazione precedente e andranno rifatti.") },
            confirmButton = { TextButton(onClick = { confirm = false; save() }) { Text("Ricalibra") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
}

// ---------------------------------------------------------------- Impostazioni

@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var newMaterial by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("") }

    Scaffold(topBar = { Bar("Impostazioni", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Cabina luce")
            val p = data.profile
            Text(
                if (p == null) "Non calibrata."
                else "Calibrata · ISO ${p.iso} · 1/${(1_000_000_000.0 / p.exposureNs).roundToInt()} s",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { go(Screen.Calibrate) }) {
                Text(if (p == null) "Calibra cabina" else "Ricalibra cabina")
            }

            HorizontalDivider()
            Section("Materiali e soglia ΔE00")
            Text(
                "Più bassa la soglia, più severo il controllo. Indicativo: sotto 1 differenza quasi invisibile, " +
                    "1–2 visibile solo a un occhio esperto, oltre 3 evidente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            data.materials.forEach { m ->
                MaterialRow(m, onChange = { t ->
                    store.update { d -> d.copy(materials = d.materials.map { if (it.name == m.name) it.copy(threshold = t) else it }) }
                }, onDelete = {
                    store.update { d -> d.copy(materials = d.materials.filterNot { it.name == m.name }) }
                })
            }
            AddRow("Nuovo materiale", newMaterial, { newMaterial = it }) {
                val n = newMaterial.trim()
                if (n.isNotEmpty() && data.materials.none { it.name.equals(n, true) }) {
                    store.update { d -> d.copy(materials = d.materials + Material(n, 2.0)) }
                }
                newMaterial = ""
            }

            HorizontalDivider()
            Section("Tipi di tasto")
            data.buttonTypes.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, Modifier.weight(1f))
                    IconButton(onClick = { store.update { d -> d.copy(buttonTypes = d.buttonTypes - t) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                    }
                }
            }
            AddRow("Nuovo tipo di tasto", newType, { newType = it }) {
                val n = newType.trim()
                if (n.isNotEmpty() && data.buttonTypes.none { it.equals(n, true) }) {
                    store.update { d -> d.copy(buttonTypes = d.buttonTypes + n) }
                }
                newType = ""
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MaterialRow(m: Material, onChange: (Double) -> Unit, onDelete: () -> Unit) {
    var value by remember(m.name) { mutableStateOf(m.threshold.toFloat()) }
    LaunchedEffect(m.threshold) { value = m.threshold.toFloat() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("ΔE ≤ ${fmt(value.toDouble())}")
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Elimina") }
            }
            Slider(
                value = value,
                onValueChange = { value = (it * 10f).roundToInt() / 10f },
                onValueChangeFinished = { onChange(value.toDouble()) },
                valueRange = 0.3f..6f,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
    }
}

@Composable
private fun AddRow(label: String, value: String, onValue: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("Aggiungi") }
    }
}
