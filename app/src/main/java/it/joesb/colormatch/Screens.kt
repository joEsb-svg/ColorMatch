@file:OptIn(ExperimentalMaterial3Api::class)

package it.joesb.colormatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
    val today = store.history.count { isSameDay(it.time, System.currentTimeMillis()) }
    Scaffold(containerColor = Color.Transparent) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ColorMatch", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "CONTROLLO COLORE DI PRECISIONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan,
                        letterSpacing = 1.6.sp,
                    )
                }
            }

            // Stato del sistema, come un pannello di controllo
            Row(
                Modifier.fillMaxWidth().panel(18.dp).padding(vertical = 14.dp, horizontal = 8.dp),
            ) {
                StatusItem("CABINA", if (data.profile != null) "Calibrata" else "Da calibrare", if (data.profile != null) OkGreen else WarnAmber, Modifier.weight(1.4f))
                StatusItem("CAMPIONI", data.refs.size.toString(), Cyan, Modifier.weight(1f))
                StatusItem("OGGI", today.toString(), Violet, Modifier.weight(1f))
            }

            if (data.profile == null) {
                InfoCard("La cabina non è ancora calibrata. Fallo una volta prima di creare campioni e verificare.", WarnBg) {
                    GradientButton("CALIBRA CABINA", { go(Screen.Calibrate) }, height = 46.dp)
                }
            }

            // Azione principale
            Box(
                Modifier
                    .fillMaxWidth()
                    .ledGlow(24.dp, strength = 0.8f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AccentBrush)
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(0f to Color(0xB3FFFFFF), 0.4f to Color(0x22FFFFFF), 1f to Color(0x59000000)),
                        RoundedCornerShape(24.dp),
                    )
                    .clickable { go(Screen.Verify) }
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VERIFICA PEZZO", color = Color(0xFF04121B), fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.6.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Inquadra, misura e scopri subito se il colore è conforme",
                            color = Color(0xCC04121B),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x3304121B)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF04121B), modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Pulsantiera: quattro tasti quadrati su una placca, con la barra LED al centro
            Column(
                Modifier
                    .fillMaxWidth()
                    .ledGlow(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF263242), Color(0xFF121A25))))
                    .border(1.dp, BevelBrush, RoundedCornerShape(24.dp))
                    .padding(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeyButton("Storico", "${store.history.size} verifiche", Icons.Filled.DateRange, true, Modifier.weight(1f)) { go(Screen.History) }
                    KeyButton("Campioni", "${data.refs.size} salvati", Icons.Filled.Star, false, Modifier.weight(1f)) { go(Screen.Refs) }
                }
                LedBar()
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeyButton("Calibrazione", "Cabina luce", Icons.Filled.Build, true, Modifier.weight(1f)) { go(Screen.Calibrate) }
                    KeyButton("Impostazioni", "Materiali e soglie", Icons.Filled.Settings, false, Modifier.weight(1f)) { go(Screen.Settings) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA9B6C8), fontSize = 11.sp)
    }
}

/** Barra luminosa orizzontale al centro della pulsantiera. */
@Composable
private fun LedBar() {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(9.dp)) {
        drawRoundRect(Color(0xFF04070B), cornerRadius = CornerRadius(2.dp.toPx()))
        val y = size.height / 2f
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.2f to Color(0xFF2F6BFF), 0.5f to Cyan, 0.8f to Color(0xFF2F6BFF), 1f to Color.Transparent,
        )
        drawLine(brush, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, alpha = 0.28f)
        drawLine(brush, Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Tasto quadrato in metallo spazzolato con la fessura LED, come i tasti di una pulsantiera. */
@Composable
private fun KeyButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    ledLeft: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(5.dp)
    val face = if (pressed) listOf(Color(0xFF18212D), Color(0xFF1D2835)) else listOf(Color(0xFF2B3747), Color(0xFF1A2330))
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(face))
            .drawBehind {
                // trama orizzontale del metallo spazzolato
                val gap = 2.5.dp.toPx()
                var y = gap
                var i = 0
                while (y < size.height) {
                    drawLine(
                        if (i % 2 == 0) Color(0x0DFFFFFF) else Color(0x12000000),
                        Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                    )
                    y += gap
                    i++
                }
            }
            .border(
                1.dp,
                if (pressed) Brush.verticalGradient(0f to Color(0x99000000), 1f to Color(0x30FFFFFF)) else BevelBrush,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        // fessura LED sul lato esterno del tasto
        Canvas(
            Modifier.align(if (ledLeft) Alignment.TopStart else Alignment.TopEnd).padding(top = 8.dp).width(30.dp).height(10.dp)
        ) {
            val y = size.height / 2f
            val glow = if (pressed) 0.75f else 0.4f
            drawLine(Cyan, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round, alpha = glow * 0.35f)
            drawLine(Cyan, Offset(2f, y), Offset(size.width - 2f, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round, alpha = glow * 0.6f)
            drawLine(Color.White, Offset(3f, y), Offset(size.width - 3f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF7F8EA3),
            modifier = Modifier.align(if (ledLeft) Alignment.TopEnd else Alignment.TopStart).size(20.dp),
        )
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.4.sp, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Verifica

@Composable
fun VerifyScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var material by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf<String?>(null) }
    /** null = riconoscimento automatico del tipo di tasto. */
    var manualType by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Measurement?>(null) }
    var resultRef by remember { mutableStateOf<Reference?>(null) }
    var failed by remember { mutableStateOf(false) }
    var lot by remember { mutableStateOf("") }
    val controller = remember { MeasureController() }
    val shape by controller.shape.collectAsState()

    val refs = data.refs
    val materials = refs.map { it.material }.distinct().sorted()
    if (material == null && materials.size == 1) material = materials.first()
    val colors = refs.filter { it.material == material }.map { it.color }.distinct().sorted()
    val candidates = refs.filter { it.material == material && it.color == color }
    val types = candidates.map { it.buttonType }.distinct().sorted()
    val auto = "Automatico"

    // Tipo effettivo: scelto a mano, oppure dedotto dalla forma vista dalla fotocamera
    val detectedType = shape?.let { sh -> types.firstOrNull { shapeOfType(it) == sh } }
    val effectiveType = manualType ?: detectedType ?: types.singleOrNull()
    val ref = candidates.firstOrNull { it.buttonType == effectiveType }
    val profile = data.profile

    Scaffold(containerColor = Color.Transparent, topBar = { Bar("Verifica pezzo", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile == null) {
                InfoCard("Prima calibra la cabina.", WarnBg) {
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
            Picker("Materiale", materials, material, { material = it; color = null; manualType = null; result = null })
            Picker("Colore", colors, color, { color = it; manualType = null; result = null })

            if (color != null && candidates.isNotEmpty()) {
                Picker("Tipo tasto", listOf(auto) + types, manualType ?: auto, {
                    manualType = if (it == auto) null else it
                    result = null
                })
                if (ref != null && ref.profileId != profile.id) {
                    InfoCard(
                        "Questo campione è stato creato con una calibrazione cabina diversa: il confronto non è affidabile. Rifai il campione.",
                        WarnBg,
                    )
                }
                CameraPermissionGate {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CameraPanel(controller, CamMode.PROFILE, profile)
                        if (manualType == null) {
                            val sh = shape
                            val (txt, col) = when {
                                detectedType != null -> "Tasto riconosciuto: $detectedType" to OkGreen
                                sh != null -> ("Riconosciuto un tasto " + (if (sh == KeyShape.SQUARE) "quadrato" else "rettangolare") +
                                    ", ma manca il campione per questo tipo e colore") to WarnAmber
                                types.size == 1 -> "Tasto non riconosciuto: uso l'unico campione disponibile (${types.first()})" to WarnAmber
                                else -> "Tasto non riconosciuto: inquadra un tasto intero al centro, oppure scegli il tipo a mano" to WarnAmber
                            }
                            Text(txt, color = col, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        PositionGuide(controller, profile)
                        ExposureStatus(controller)
                        LiveReadout(controller)
                        OutlinedTextField(
                            value = lot,
                            onValueChange = { lot = it },
                            label = { Text("Lotto / ordine (facoltativo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MeasureButton(controller, enabled = ref != null) { m ->
                            result = m
                            resultRef = ref
                            failed = m == null
                            if (m != null && ref != null) {
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
                    InfoCard("Misura non riuscita: troppi riflessi o pezzo fuori dal riquadro. Riprova.", WarnBg)
                }
                val r = result
                val rr = resultRef
                if (r != null && rr != null) {
                    Text("Confrontato con: ${rr.buttonType}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    ResultCard(rr, r, data.thresholdFor(rr.material))
                }
            }
        }
    }
}

@Composable
private fun ResultCard(ref: Reference, m: Measurement, threshold: Double) {
    val de = ColorMath.deltaE2000(ref.lab, m.lab)
    val ok = de <= threshold
    val tone = if (ok) OkGreen else BadRed
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.30f), Color(0xE6101824))))
            .border(1.5.dp, tone.copy(alpha = 0.8f), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (ok) "CONFORME" else "NON CONFORME",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = tone,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indicatore: l'arco si riempie fino alla soglia (metà scala) e oltre
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 11.dp.toPx()
                    val inset = sw / 2
                    val arcSize = Size(size.width - sw, size.height - sw)
                    val tl = Offset(inset, inset)
                    drawArc(Color(0x26FFFFFF), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                    val frac = (de / (threshold * 2.0)).coerceIn(0.0, 1.0).toFloat()
                    drawArc(tone, 135f, 270f * frac, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                    // tacca della soglia a metà arco (in alto)
                    val cx = size.width / 2f
                    drawLine(Color.White, Offset(cx, 0f), Offset(cx, sw + 3.dp.toPx()), strokeWidth = 2.5.dp.toPx())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(de, 2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("ΔE00", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Soglia ${fmt(threshold)}", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(ref.lab, 44.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Campione", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(m.lab, 44.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Pezzo", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val hints = differenceHints(ref.lab, m.lab)
        if (hints.isNotEmpty()) {
            Text("Il pezzo è " + hints.joinToString(", "), textAlign = TextAlign.Center)
        }
        val warn = buildList {
            if (m.spread > 0.8) add("misura instabile: tieni fermo il telefono")
            if (m.excluded > 0.5) add("molti riflessi esclusi: inclina un po' il pezzo")
            if (m.saturated > 0.05) add("zone bruciate nel riquadro")
        }
        if (warn.isNotEmpty()) {
            Text("⚠ " + warn.joinToString("; "), color = WarnAmber, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Text("SALVATA NELLO STORICO", color = TextDim, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------- Campioni

@Composable
fun RefsScreen(store: Store, onBack: () -> Unit, go: (Screen) -> Unit) {
    val data = store.data
    var toDelete by remember { mutableStateOf<Reference?>(null) }
    Scaffold(
        containerColor = Color.Transparent,
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

    Scaffold(containerColor = Color.Transparent, topBar = { Bar("Nuovo campione", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val profile = data.profile
            if (profile == null) {
                InfoCard("Prima calibra la cabina: i campioni devono essere misurati sempre con gli stessi parametri.", WarnBg) {
                    Button(onClick = { go(Screen.Calibrate) }) { Text("Calibra cabina") }
                }
                return@Column
            }
            Picker("Tipo tasto", data.buttonTypes, type, { type = it })
            Picker("Materiale", data.materials.map { it.name }, material, { material = it; color = "" })
            val presets = data.materials.firstOrNull { it.name == material }?.colors ?: emptyList()
            if (presets.isNotEmpty()) {
                Picker("Colore", presets, color.takeIf { it in presets }, { color = it })
            }
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text(if (presets.isNotEmpty()) "…oppure scrivi un altro colore" else "Colore (es. Bianco RAL 9010, Antracite…)") },
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
                    PositionGuide(controller, profile)
                    ExposureStatus(controller)
                    LiveReadout(controller)
                    MeasureButton(controller, label = "AGGIUNGI MISURA (${samples.size})") { m ->
                        failed = m == null
                        if (m != null) samples.add(m)
                    }
                }
            }
            if (failed) InfoCard("Misura non riuscita: troppi riflessi. Riprova.", WarnBg)

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
                ) { Text("SALVA CAMPIONE", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
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
    val gravity by rememberGravity()

    fun save() {
        val c = capture ?: return
        val exp = c.exposureNs ?: return
        val iso = c.iso ?: return
        store.update { d ->
            d.copy(
                profile = CameraProfile(
                    id = System.currentTimeMillis(),
                    exposureNs = exp,
                    iso = iso,
                    gains = c.gains,
                    transform = c.transform,
                    focusDiopters = c.focusDiopters?.takeIf { c.focused },
                    gravity = gravity,
                )
            )
        }
        onBack()
    }

    Scaffold(containerColor = Color.Transparent, topBar = { Bar("Calibra cabina", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Accendi la luce della cabina e appoggia un cartoncino grigio neutro (va bene anche un foglio bianco opaco) " +
                    "dove metterai i pezzi. Tieni il telefono nella posizione che userai sempre (meglio su un supporto), aspetta che sia a fuoco e salva: " +
                    "l'app ricorda anche inclinazione e distanza e in verifica ti guida a rimetterti uguale.",
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
                val fd = c.focusDiopters
                Text(
                    when {
                        fd == null || fd <= 0.05f -> "Distanza: non rilevabile con questo telefono (la guida userà solo l'inclinazione)"
                        !c.focused -> "Messa a fuoco in corso…"
                        else -> "A fuoco · distanza ~${(100.0 / fd).roundToInt()} cm"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
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
            ) { Text("SALVA PROFILO CABINA", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
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

    Scaffold(containerColor = Color.Transparent, topBar = { Bar("Impostazioni", onBack) }) { pad ->
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
                }, onColors = { list ->
                    store.update { d -> d.copy(materials = d.materials.map { if (it.name == m.name) it.copy(colors = list) else it }) }
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
private fun MaterialRow(m: Material, onChange: (Double) -> Unit, onColors: (List<String>) -> Unit, onDelete: () -> Unit) {
    var value by remember(m.name) { mutableStateOf(m.threshold.toFloat()) }
    var colorsText by remember(m.name) { mutableStateOf(m.colors.joinToString(", ")) }
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
            OutlinedTextField(
                value = colorsText,
                onValueChange = { t ->
                    colorsText = t
                    onColors(t.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct())
                },
                label = { Text("Colori (separati da virgola)") },
                modifier = Modifier.fillMaxWidth().padding(end = 10.dp, bottom = 8.dp),
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
