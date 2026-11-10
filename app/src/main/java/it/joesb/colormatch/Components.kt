@file:OptIn(ExperimentalMaterial3Api::class)

package it.joesb.colormatch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

fun fmt(v: Double, decimals: Int = 1): String = String.format(Locale.ITALY, "%.${decimals}f", v)

@Composable
fun Bar(title: String, onBack: (() -> Unit)?) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            }
        },
    )
}

@Composable
fun Swatch(lab: Lab, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(ColorMath.labToArgb(lab)))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
    )
}

@Composable
fun Picker(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(selected ?: "Scegli…", style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun InfoCard(text: String, color: Color = Color(0xCC111A27), content: @Composable () -> Unit = {}) {
    val warn = color == WarnBg
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .ledGlow(18.dp, if (warn) WarnAmber else Cyan, if (warn) 0.5f else 0.72f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF18222F), Color(0xFF0F1722))))
            .background(if (warn) color else Color.Transparent)
            .border(1.dp, if (warn) Brush.verticalGradient(0f to WarnAmber.copy(alpha = 0.85f), 1f to WarnAmber.copy(alpha = 0.25f)) else BevelBrush, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (warn) {
            Text("ATTENZIONE", color = WarnAmber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

private fun hasCamera(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Mostra il contenuto solo se il permesso fotocamera è concesso. */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasCamera(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) {
        content()
    } else {
        InfoCard("Serve il permesso della fotocamera per misurare il colore.") {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Consenti fotocamera") }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun bindCamera(
    provider: ProcessCameraProvider,
    owner: LifecycleOwner,
    previewView: PreviewView,
    controller: MeasureController,
    executor: ExecutorService,
    mode: CamMode,
    profile: CameraProfile?,
) {
    val previewBuilder = Preview.Builder()
    Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                controller.onCaptureResult(result)
            }
        }
    )
    val preview = previewBuilder.build()
    preview.setSurfaceProvider(previewView.surfaceProvider)

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
    analysis.setAnalyzer(executor, controller.analyzer)

    provider.unbindAll()
    val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    controller.attach(camera, mode, profile)
}

/** Anteprima fotocamera con il riquadro di misura al centro. */
@Composable
fun CameraPanel(
    controller: MeasureController,
    mode: CamMode,
    profile: CameraProfile?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(owner, mode, profile?.id) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (!disposed) {
                try {
                    val p = future.get()
                    provider = p
                    bindCamera(p, owner, previewView, controller, executor, mode, profile)
                } catch (_: Exception) {
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
            controller.detach()
            executor.shutdown()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
            .border(1.dp, Color(0x4035D6FF), RoundedCornerShape(22.dp))
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val s = min(size.width, size.height) * ROI_FRACTION
            val tl = Offset((size.width - s) / 2f, (size.height - s) / 2f)
            drawRect(Color(0x33000000), topLeft = tl, size = Size(s, s), style = Stroke(1.dp.toPx()))
            drawBrackets(tl, s, Color(0x99000000), 6.dp.toPx())
            drawBrackets(tl, s, Cyan, 3.dp.toPx())
            val c = Offset(size.width / 2f, size.height / 2f)
            val k = 5.dp.toPx()
            drawLine(Color(0xCCFFFFFF), Offset(c.x - k, c.y), Offset(c.x + k, c.y), strokeWidth = 1.5.dp.toPx())
            drawLine(Color(0xCCFFFFFF), Offset(c.x, c.y - k), Offset(c.x, c.y + k), strokeWidth = 1.5.dp.toPx())
        }
    }
}

/** Valori in tempo reale + avvisi sotto l'anteprima. */
@Composable
fun LiveReadout(controller: MeasureController) {
    val live by controller.live.collectAsState()
    val st = live
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            st == null -> Text("Avvio fotocamera…", style = MaterialTheme.typography.bodySmall)
            st.lab == null -> Text(
                "Zona non misurabile: troppa luce o riflesso. Inclina leggermente il pezzo.",
                color = WarnAmber,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(st.lab, 22.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    "L ${fmt(st.lab.l)}  a ${fmt(st.lab.a)}  b ${fmt(st.lab.b)}  ·  riflessi esclusi ${(st.excluded * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (st != null && st.saturated > 0.05) {
            Text(
                "Attenzione: ${(st.saturated * 100).roundToInt()}% di pixel bruciati nel riquadro",
                color = WarnAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Nel modo PROFILE, avvisa se il telefono non supporta i parametri manuali e offre il blocco. */
@Composable
fun ExposureStatus(controller: MeasureController) {
    val ready by controller.cameraReady.collectAsState()
    val active by controller.profileActive.collectAsState()
    val locked by controller.locked.collectAsState()
    if (!ready) return
    if (active) {
        Text(
            "Profilo cabina applicato: esposizione e colore bloccati.",
            color = OkGreen,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        InfoCard(
            "Questo telefono non accetta l'esposizione manuale. Inquadra il cartoncino grigio (o il campione buono), " +
                "attiva il blocco, poi inquadra il pezzo senza muovere la luce.",
            color = WarnBg,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = locked, onCheckedChange = { controller.setLocked(it) })
                Spacer(Modifier.size(8.dp))
                Text(if (locked) "Esposizione bloccata" else "Esposizione libera")
            }
        }
    }
}

/** Pulsante MISURA con barra di avanzamento. */
@Composable
fun MeasureButton(
    controller: MeasureController,
    label: String = "MISURA",
    enabled: Boolean = true,
    onResult: (Measurement?) -> Unit,
) {
    val progress by controller.progress.collectAsState()
    val ready by controller.cameraReady.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        GradientButton(
            text = if (progress != null) "MISURO… TIENI FERMO" else label,
            onClick = { controller.measure(onResult) },
            enabled = enabled && ready && progress == null,
        )
        val p = progress
        if (p != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = Cyan, trackColor = Color(0x22FFFFFF))
        }
    }
}
