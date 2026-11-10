package it.joesb.colormatch

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Colore nello spazio CIE L*a*b* (D65). */
data class Lab(val l: Double, val a: Double, val b: Double)

object ColorMath {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val EPS = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    /** sRGB 0..255 -> lineare, precalcolato. */
    private val LIN = DoubleArray(256) { i ->
        val c = i / 255.0
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun f(t: Double): Double = if (t > EPS) cbrt(t) else (KAPPA * t + 16.0) / 116.0

    /** Converte un pixel sRGB e scrive L, a, b negli array all'indice [i]. */
    fun rgbToLab(r: Int, g: Int, b: Int, outL: FloatArray, outA: FloatArray, outB: FloatArray, i: Int) {
        val rl = LIN[r]
        val gl = LIN[g]
        val bl = LIN[b]
        val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / XN
        val y = (0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl) / YN
        val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / ZN
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        outL[i] = (116.0 * fy - 16.0).toFloat()
        outA[i] = (500.0 * (fx - fy)).toFloat()
        outB[i] = (200.0 * (fy - fz)).toFloat()
    }

    /** Lab -> colore ARGB per mostrare un'anteprima a schermo. */
    fun labToArgb(lab: Lab): Int {
        val fy = (lab.l + 16.0) / 116.0
        val fx = fy + lab.a / 500.0
        val fz = fy - lab.b / 200.0
        fun inv(t: Double): Double {
            val t3 = t * t * t
            return if (t3 > EPS) t3 else (116.0 * t - 16.0) / KAPPA
        }
        val x = inv(fx) * XN
        val y = inv(fy) * YN
        val z = inv(fz) * ZN
        val rl = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val gl = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        fun enc(c: Double): Int {
            val v = c.coerceIn(0.0, 1.0)
            val s = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
            return (s * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (enc(rl) shl 16) or (enc(gl) shl 8) or enc(bl)
    }

    private fun deg(rad: Double) = Math.toDegrees(rad)
    private fun rad(deg: Double) = Math.toRadians(deg)

    /** Differenza colore CIEDE2000 (ΔE00). */
    fun deltaE2000(c1: Lab, c2: Lab): Double {
        val l1 = c1.l; val a1 = c1.a; val b1 = c1.b
        val l2 = c2.l; val a2 = c2.a; val b2 = c2.b

        val cAvg = (hypot(a1, b1) + hypot(a2, b2)) / 2.0
        val c7 = cAvg.pow(7)
        val g = 0.5 * (1.0 - sqrt(c7 / (c7 + 25.0.pow(7))))
        val a1p = a1 * (1.0 + g)
        val a2p = a2 * (1.0 + g)
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)

        fun hue(b: Double, ap: Double): Double {
            if (b == 0.0 && ap == 0.0) return 0.0
            val h = deg(atan2(b, ap))
            return if (h < 0) h + 360.0 else h
        }
        val h1p = hue(b1, a1p)
        val h2p = hue(b2, a2p)

        val dLp = l2 - l1
        val dCp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180.0 -> h2p - h1p
            h2p - h1p > 180.0 -> h2p - h1p - 360.0
            else -> h2p - h1p + 360.0
        }
        val dHp = 2.0 * sqrt(c1p * c2p) * sin(rad(dhp / 2.0))

        val lAvgP = (l1 + l2) / 2.0
        val cAvgP = (c1p + c2p) / 2.0
        val hAvgP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) / 2.0
            h1p + h2p < 360.0 -> (h1p + h2p + 360.0) / 2.0
            else -> (h1p + h2p - 360.0) / 2.0
        }

        val t = 1.0 - 0.17 * cos(rad(hAvgP - 30.0)) + 0.24 * cos(rad(2.0 * hAvgP)) +
            0.32 * cos(rad(3.0 * hAvgP + 6.0)) - 0.20 * cos(rad(4.0 * hAvgP - 63.0))
        val dTheta = 30.0 * exp(-((hAvgP - 275.0) / 25.0).pow(2))
        val cAvgP7 = cAvgP.pow(7)
        val rc = 2.0 * sqrt(cAvgP7 / (cAvgP7 + 25.0.pow(7)))
        val lm50 = (lAvgP - 50.0).pow(2)
        val sl = 1.0 + 0.015 * lm50 / sqrt(20.0 + lm50)
        val sc = 1.0 + 0.045 * cAvgP
        val sh = 1.0 + 0.015 * cAvgP * t
        val rt = -sin(rad(2.0 * dTheta)) * rc

        val tl = dLp / sl
        val tc = dCp / sc
        val th = dHp / sh
        return sqrt(tl * tl + tc * tc + th * th + rt * tc * th)
    }

    fun average(list: List<Lab>): Lab = Lab(
        list.map { it.l }.average(),
        list.map { it.a }.average(),
        list.map { it.b }.average(),
    )
}

object Stats {
    fun median(values: FloatArray, n: Int): Float {
        val c = values.copyOf(n)
        c.sort()
        return if (n % 2 == 1) c[n / 2] else (c[n / 2 - 1] + c[n / 2]) / 2f
    }

    fun median(values: List<Double>): Double {
        val c = values.sorted()
        val n = c.size
        return if (n % 2 == 1) c[n / 2] else (c[n / 2 - 1] + c[n / 2]) / 2.0
    }

    /** Deviazione assoluta mediana. */
    fun mad(values: FloatArray, n: Int, med: Float): Float {
        val d = FloatArray(n) { abs(values[it] - med) }
        return median(d, n)
    }
}
