package it.joesb.colormatch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tolleranze della guida di posizione. */
private const val TILT_OK_DEG = 2.5
private const val TILT_WARN_DEG = 5.0
private const val DIST_OK = 0.06
private const val DIST_WARN = 0.12

/** Vettore gravità normalizzato del telefono, aggiornato in tempo reale (filtrato). */
@Composable
fun rememberGravity(): State<FloatArray?> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val filtered = FloatArray(3)
        var init = false
        var lastPush = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val k = 0.15f
                for (i in 0..2) {
                    filtered[i] = if (!init) e.values[i] else filtered[i] + k * (e.values[i] - filtered[i])
                }
                init = true
                val now = System.currentTimeMillis()
                if (now - lastPush > 100) {
                    lastPush = now
                    state.value = normalize(filtered)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

fun normalize(v: FloatArray): FloatArray? {
    val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if (n < 1e-3f) return null
    return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
}

/** Angolo in gradi tra due vettori gravità normalizzati. */
fun angleDeg(a: FloatArray, b: FloatArray): Double {
    val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).toDouble().coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(dot))
}

private enum class Level(val color: Color) { Ok(OkGreen), Warn(WarnAmber), Bad(BadRed), Na(Color(0xFF6B7380)) }

/**
 * Guida per rimettere il telefono nella stessa posizione della calibrazione:
 * inclinazione (sensore di gravità) e distanza (messa a fuoco dell'obiettivo).
 */
@Composable
fun PositionGuide(controller: MeasureController, profile: CameraProfile) {
    val gravity by rememberGravity()
    val capture by controller.capture.collectAsState()

    // --- inclinazione
    val refG = profile.gravity
    val g = gravity
    val tiltLevel: Level
    val tiltText: String
    if (refG == null || refG.size != 3) {
        tiltLevel = Level.Na
        tiltText = "Inclinazione: ricalibra la cabina per attivare la guida"
    } else if (g == null) {
        tiltLevel = Level.Na
        tiltText = "Inclinazione: sensore non disponibile"
    } else {
        val ang = angleDeg(g, refG)
        tiltLevel = when {
            ang <= TILT_OK_DEG -> Level.Ok
            ang <= TILT_WARN_DEG -> Level.Warn
            else -> Level.Bad
        }
        tiltText = if (tiltLevel == Level.Ok) {
            "Inclinazione OK (${fmt(ang)}°)"
        } else {
            // La gravità "punta in alto" nel sistema del telefono: componente x maggiore = lato destro più alto.
            val dx = g[0] - refG[0]
            val dy = g[1] - refG[1]
            val hint = if (abs(dx) >= abs(dy)) {
                if (dx > 0) "lato destro più alto del solito" else "lato sinistro più alto del solito"
            } else {
                if (dy > 0) "lato in alto più sollevato del solito" else "lato in basso più sollevato del solito"
            }
            "Inclinazione ${fmt(ang)}°: $hint"
        }
    }

    // --- distanza
    val refD = profile.focusDiopters
    val c = capture
    val curD = c?.focusDiopters
    val distLevel: Level
    val distText: String
    if (refD == null || refD <= 0.05f) {
        distLevel = Level.Na
        distText = if (refD == null) "Distanza: ricalibra la cabina per attivare la guida"
        else "Distanza: non rilevabile con questo telefono"
    } else if (curD == null || curD <= 0.05f || c?.focused != true) {
        distLevel = Level.Na
        distText = "Distanza: in attesa della messa a fuoco…"
    } else {
        // distanza ∝ 1/diottrie
        val ratio = refD / curD - 1.0
        distLevel = when {
            abs(ratio) <= DIST_OK -> Level.Ok
            abs(ratio) <= DIST_WARN -> Level.Warn
            else -> Level.Bad
        }
        val cm = 100.0 / curD
        distText = when {
            distLevel == Level.Ok -> "Distanza OK (~${cm.roundToInt()} cm)"
            ratio > 0 -> "Troppo lontano: avvicina di circa ${(ratio * 100).roundToInt()}%"
            else -> "Troppo vicino: allontana di circa ${(-ratio * 100).roundToInt()}%"
        }
    }

    val allOk = (tiltLevel == Level.Ok && (distLevel == Level.Ok || distLevel == Level.Na)) ||
        (distLevel == Level.Ok && tiltLevel == Level.Na)

    Column(
        Modifier
            .fillMaxWidth()
            .panel(16.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (allOk) "Posizione come in calibrazione ✓" else "Posizione telefono",
            fontWeight = FontWeight.SemiBold,
            color = if (allOk) OkGreen else MaterialTheme.colorScheme.onSurface,
        )
        GuideLine(tiltLevel, tiltText)
        GuideLine(distLevel, distText)
    }
}

@Composable
private fun GuideLine(level: Level, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
