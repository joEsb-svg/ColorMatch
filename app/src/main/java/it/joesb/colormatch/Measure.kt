package it.joesb.colormatch

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Handler
import android.os.Looper
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Risultato di un singolo fotogramma. lab = null se la zona non è misurabile. */
data class FrameStats(val lab: Lab?, val excluded: Double, val saturated: Double)

/** Forma del tasto riconosciuta dall'immagine. */
enum class KeyShape { SQUARE, RECT }

/** Deduce la forma attesa dal nome del tipo di tasto (es. "4 canali (tasto quadrato)"). */
fun shapeOfType(name: String): KeyShape? {
    val n = name.lowercase()
    return when {
        "quadrat" in n -> KeyShape.SQUARE
        "rettangol" in n -> KeyShape.RECT
        Regex("(^|\\D)4(\\D|$)").containsMatchIn(n) -> KeyShape.SQUARE
        Regex("(^|\\D)8(\\D|$)").containsMatchIn(n) -> KeyShape.RECT
        else -> null
    }
}

/** Risultato di una misura completa (mediana di più fotogrammi). */
data class Measurement(val lab: Lab, val spread: Double, val excluded: Double, val saturated: Double)

/** Parametri della fotocamera letti dall'ultimo scatto. */
data class CaptureInfo(
    val exposureNs: Long?,
    val iso: Int?,
    val gains: FloatArray?,
    val transform: IntArray?,
    /** Distanza di messa a fuoco in diottrie (1/metri); null se non disponibile. */
    val focusDiopters: Float?,
    /** true quando l'autofocus è a fuoco. */
    val focused: Boolean,
)

enum class CamMode {
    /** Esposizione e bilanciamento automatici (per calibrare la cabina). */
    AUTO,
    /** Parametri fissi del profilo cabina (per campioni e verifiche). */
    PROFILE,
}

/** Frazione del lato corto dell'immagine usata come zona di misura (riquadro centrale). */
const val ROI_FRACTION = 0.3f

/**
 * Analizza il riquadro centrale: converte i pixel in Lab, scarta quelli bruciati
 * e quelli troppo lontani dalla luminosità mediana (riflessi speculari, ombre, polvere),
 * poi prende la mediana. Così i riflessi di Fenix e metallo non falsano il colore.
 */
class ColorAnalyzer(
    private val onFrame: (FrameStats) -> Unit,
    private val onShape: (KeyShape?) -> Unit,
) : ImageAnalysis.Analyzer {
    private var frameNo = 0
    private var gCap = 0
    private var gl = FloatArray(0)
    private var ga = FloatArray(0)
    private var gb = FloatArray(0)
    private var seen = BooleanArray(0)
    private var queue = IntArray(0)

    private var cap = 0
    private var ls = FloatArray(0)
    private var aa = FloatArray(0)
    private var bb = FloatArray(0)
    private var fl = FloatArray(0)
    private var fa = FloatArray(0)
    private var fb = FloatArray(0)

    private fun ensure(n: Int) {
        if (n <= cap) return
        cap = n
        ls = FloatArray(n); aa = FloatArray(n); bb = FloatArray(n)
        fl = FloatArray(n); fa = FloatArray(n); fb = FloatArray(n)
    }

    override fun analyze(image: ImageProxy) {
        try {
            onFrame(process(image))
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun process(image: ImageProxy): FrameStats {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val px = plane.pixelStride
        val w = image.width
        val h = image.height
        val side = (min(w, h) * ROI_FRACTION).toInt().coerceAtLeast(8)
        val x0 = (w - side) / 2
        val y0 = (h - side) / 2
        val step = max(1, side / 100)
        val perRow = (side + step - 1) / step
        ensure(perRow * perRow + 4)

        var n = 0
        var total = 0
        var sat = 0
        var y = y0
        while (y < y0 + side) {
            val base = y * rowStride
            var x = x0
            while (x < x0 + side) {
                val i = base + x * px
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                total++
                if (r >= 250 || g >= 250 || b >= 250) {
                    sat++
                } else {
                    ColorMath.rgbToLab(r, g, b, ls, aa, bb, n)
                    n++
                }
                x += step
            }
            y += step
        }
        val satFrac = if (total == 0) 1.0 else sat.toDouble() / total
        if (n < 30) return FrameStats(null, 1.0, satFrac)

        val medL = Stats.median(ls, n)
        val mad = Stats.mad(ls, n, medL)
        val tol = max(3f * 1.4826f * mad, 1.0f)
        var k = 0
        for (j in 0 until n) {
            if (abs(ls[j] - medL) <= tol) {
                fl[k] = ls[j]; fa[k] = aa[j]; fb[k] = bb[j]
                k++
            }
        }
        if (k < 30) return FrameStats(null, 1.0, satFrac)
        val lab = Lab(
            Stats.median(fl, k).toDouble(),
            Stats.median(fa, k).toDouble(),
            Stats.median(fb, k).toDouble(),
        )
        frameNo++
        if (frameNo % 3 == 0) {
            onShape(detectShape(buffer, rowStride, px, w, h, lab))
        }
        return FrameStats(lab, 1.0 - k.toDouble() / total, satFrac)
    }

    /**
     * Riconosce la forma del tasto inquadrato al centro: parte dal centro e si allarga sui pixel
     * di colore simile e senza salti bruschi (il bordo del tasto fa da barriera), poi dalle
     * proporzioni della zona trovata distingue quadrato (4 canali) e rettangolo (8 canali).
     * Usa i momenti della zona, quindi funziona anche se il tasto è ruotato.
     */
    private fun detectShape(
        buffer: java.nio.ByteBuffer, rowStride: Int, px: Int, w: Int, h: Int, seed: Lab,
    ): KeyShape? {
        val step = max(2, min(w, h) / 160)
        val gw = w / step
        val gh = h / step
        val n = gw * gh
        if (n > gCap) {
            gCap = n
            gl = FloatArray(n); ga = FloatArray(n); gb = FloatArray(n)
            seen = BooleanArray(n); queue = IntArray(n)
        }
        for (gy in 0 until gh) {
            val base = gy * step * rowStride
            for (gx in 0 until gw) {
                val i = base + gx * step * px
                ColorMath.rgbToLab(
                    buffer.get(i).toInt() and 0xFF,
                    buffer.get(i + 1).toInt() and 0xFF,
                    buffer.get(i + 2).toInt() and 0xFF,
                    gl, ga, gb, gy * gw + gx,
                )
            }
        }
        java.util.Arrays.fill(seen, 0, n, false)
        val sl = seed.l.toFloat(); val sa = seed.a.toFloat(); val sb = seed.b.toFloat()
        val start = (gh / 2) * gw + gw / 2
        var head = 0
        var tail = 0
        queue[tail++] = start
        seen[start] = true
        var count = 0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        var border = false
        while (head < tail) {
            val c = queue[head++]
            val cx = c % gw
            val cy = c / gw
            count++
            sx += cx; sy += cy; sxx += cx.toDouble() * cx; syy += cy.toDouble() * cy; sxy += cx.toDouble() * cy
            if (cx == 0 || cy == 0 || cx == gw - 1 || cy == gh - 1) { border = true; continue }
            val cl = gl[c]; val ca = ga[c]; val cb = gb[c]
            for (d in 0 until 4) {
                val nb = when (d) { 0 -> c - 1; 1 -> c + 1; 2 -> c - gw; else -> c + gw }
                if (seen[nb]) continue
                val l = gl[nb]; val a = ga[nb]; val b = gb[nb]
                if (abs(a - sa) < 7f && abs(b - sb) < 7f && abs(l - sl) < 24f &&
                    abs(l - cl) < 6f && abs(a - ca) < 3.5f && abs(b - cb) < 3.5f
                ) {
                    seen[nb] = true
                    queue[tail++] = nb
                }
            }
        }
        if (border || count < n * 0.015) return null
        val mx = sx / count
        val my = sy / count
        val vxx = sxx / count - mx * mx
        val vyy = syy / count - my * my
        val vxy = sxy / count - mx * my
        val tr = vxx + vyy
        val det = vxx * vyy - vxy * vxy
        val disc = sqrt(max(0.0, tr * tr / 4.0 - det))
        val l1 = tr / 2.0 + disc
        val l2 = tr / 2.0 - disc
        if (l2 <= 0.5) return null
        val sideA = sqrt(12.0 * l1)
        val sideB = sqrt(12.0 * l2)
        val fill = count / (sideA * sideB)
        if (fill < 0.72 || fill > 1.2) return null
        val ratio = sideA / sideB
        return when {
            ratio < 1.35 -> KeyShape.SQUARE
            ratio in 1.6..2.6 -> KeyShape.RECT
            else -> null
        }
    }
}

/** Gestisce fotocamera, parametri bloccati e raccolta dei fotogrammi di una misura. */
class MeasureController {
    val live = MutableStateFlow<FrameStats?>(null)
    val capture = MutableStateFlow<CaptureInfo?>(null)
    /** null = nessuna misura in corso, altrimenti 0..1. */
    val progress = MutableStateFlow<Float?>(null)
    val cameraReady = MutableStateFlow(false)
    val manualSensor = MutableStateFlow(false)
    val manualPost = MutableStateFlow(false)
    /** true se il profilo cabina è applicato in manuale. */
    val profileActive = MutableStateFlow(false)
    /** Blocco AE/AWB (usato quando il telefono non supporta il manuale). */
    val locked = MutableStateFlow(false)

    private val main = Handler(Looper.getMainLooper())
    private val sync = Any()
    private val frames = ArrayList<FrameStats>()
    private var attempts = 0
    private var callback: ((Measurement?) -> Unit)? = null
    private var camera: Camera? = null
    private var captureCount = 0

    /** Forma del tasto riconosciuta (stabile su più fotogrammi); null = non riconosciuta. */
    val shape = MutableStateFlow<KeyShape?>(null)
    private val recentShapes = ArrayDeque<KeyShape?>()

    val analyzer = ColorAnalyzer(
        onFrame = { st ->
            live.value = st
            if (st.lab == null) pushShape(null)
            onFrame(st)
        },
        onShape = { pushShape(it) },
    )

    private fun pushShape(s: KeyShape?) {
        recentShapes.addLast(s)
        while (recentShapes.size > 9) recentShapes.removeFirst()
        val sq = recentShapes.count { it == KeyShape.SQUARE }
        val re = recentShapes.count { it == KeyShape.RECT }
        shape.value = when {
            sq >= 5 -> KeyShape.SQUARE
            re >= 5 -> KeyShape.RECT
            else -> null
        }
    }

    /** Avvia una misura su [FRAMES] fotogrammi. Il risultato arriva sul thread principale (null = fallita). */
    fun measure(onResult: (Measurement?) -> Unit) {
        synchronized(sync) {
            frames.clear()
            attempts = 0
            callback = onResult
        }
        progress.value = 0f
    }

    private fun onFrame(st: FrameStats) {
        var cb: ((Measurement?) -> Unit)? = null
        var result: Measurement? = null
        var finished = false
        synchronized(sync) {
            val current = callback ?: return
            attempts++
            if (st.lab != null) frames.add(st)
            progress.value = frames.size.toFloat() / FRAMES
            if (frames.size >= FRAMES) {
                result = aggregate(frames)
                finished = true
            } else if (attempts > FRAMES * 5) {
                result = null
                finished = true
            }
            if (finished) {
                cb = current
                callback = null
                frames.clear()
            }
        }
        if (finished) {
            progress.value = null
            val r = result
            val c = cb
            main.post { c?.invoke(r) }
        }
    }

    private fun aggregate(list: List<FrameStats>): Measurement {
        val labs = list.mapNotNull { it.lab }
        val med = Lab(
            Stats.median(labs.map { it.l }),
            Stats.median(labs.map { it.a }),
            Stats.median(labs.map { it.b }),
        )
        val rms = sqrt(labs.map { val d = ColorMath.deltaE2000(med, it); d * d }.average())
        return Measurement(
            lab = med,
            spread = rms,
            excluded = list.map { it.excluded }.average(),
            saturated = list.map { it.saturated }.average(),
        )
    }

    fun onCaptureResult(result: TotalCaptureResult) {
        captureCount++
        if (captureCount % 5 != 0) return
        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
            floatArrayOf(it.red, it.greenEven, it.greenOdd, it.blue)
        }
        val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let {
            val arr = IntArray(18)
            it.copyElements(arr, 0)
            arr
        }
        capture.value = CaptureInfo(
            exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            gains = gains,
            transform = transform,
            focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            focused = when (result.get(CaptureResult.CONTROL_AF_STATE)) {
                CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> true
                else -> false
            },
        )
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun attach(camera: Camera, mode: CamMode, profile: CameraProfile?) {
        this.camera = camera
        val caps = try {
            Camera2CameraInfo.from(camera.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        } catch (_: Exception) {
            null
        }
        manualSensor.value =
            caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        manualPost.value =
            caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) == true
        locked.value = false
        profileActive.value = false

        val builder = CaptureRequestOptions.Builder()
        if (mode == CamMode.PROFILE && profile != null && manualSensor.value) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, profile.exposureNs)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, profile.iso)
            val gains = profile.gains
            val tr = profile.transform
            if (manualPost.value && gains != null && gains.size == 4 && tr != null && tr.size == 18) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                builder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(gains[0], gains[1], gains[2], gains[3]),
                )
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorSpaceTransform(tr))
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            }
            profileActive.value = true
        }
        apply(builder.build())
        cameraReady.value = true
    }

    /** Blocca/sblocca esposizione e bilanciamento del bianco automatici. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setLocked(lock: Boolean) {
        val opts = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
            .build()
        apply(opts)
        locked.value = lock
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun apply(opts: CaptureRequestOptions) {
        val cam = camera ?: return
        try {
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(opts)
        } catch (_: Exception) {
        }
    }

    fun detach() {
        camera = null
        cameraReady.value = false
        profileActive.value = false
        locked.value = false
        live.value = null
        shape.value = null
        synchronized(sync) {
            callback = null
            frames.clear()
        }
        progress.value = null
    }

    companion object {
        const val FRAMES = 24
    }
}
