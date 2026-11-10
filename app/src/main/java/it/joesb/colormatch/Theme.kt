package it.joesb.colormatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Palette: nero-blu profondo, accento ciano → viola (pannello smart home)
val Ink = Color(0xFF070B11)
val Panel = Color(0xFF0F1622)
val PanelHi = Color(0xFF151F2E)
val Line = Color(0x1FFFFFFF)
val Cyan = Color(0xFF35D6FF)
val Violet = Color(0xFF7B6CFF)
val TextDim = Color(0xFF8C9AAE)

val OkGreen = Color(0xFF22C58B)
val BadRed = Color(0xFFFF5263)
val WarnAmber = Color(0xFFFFB84D)
val WarnBg = Color(0x24FFB84D)

val AccentBrush = Brush.linearGradient(listOf(Cyan, Violet))

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00202B),
    primaryContainer = Color(0xFF0E3A4A),
    onPrimaryContainer = Color(0xFFBDEFFF),
    secondary = TextDim,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF16344A),
    onSecondaryContainer = Color(0xFFD6F4FF),
    tertiary = Violet,
    background = Ink,
    onBackground = Color(0xFFE8EEF6),
    surface = Panel,
    onSurface = Color(0xFFE8EEF6),
    surfaceVariant = PanelHi,
    onSurfaceVariant = Color(0xFFB4C0D0),
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHi,
    surfaceContainerHighest = PanelHi,
    surfaceContainerLow = Panel,
    outline = Color(0xFF3A4A5E),
    outlineVariant = Color(0xFF233041),
    error = BadRed,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()
private val type = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.8.sp),
)

@Composable
fun ColorMatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = type, content = content)
}

/** Sfondo dell'app: gradiente profondo, alone ciano/viola e una trama di punti da pannello tecnico. */
@Composable
fun TechBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B121C), Ink)))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x3335D6FF), Color.Transparent),
                    center = Offset(size.width * 0.9f, 0f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.9f, 0f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x267B6CFF), Color.Transparent),
                    center = Offset(0f, size.height * 0.55f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(0f, size.height * 0.55f),
            )
            val step = 22.dp.toPx()
            val dot = Color(0x0FFFFFFF)
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(dot, radius = 1.1f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

/** Modificatore "pannello": fondo scuro semitrasparente con bordo sottile. */
fun Modifier.panel(radius: Dp = 18.dp, fill: Color = Color(0xCC111A27)): Modifier =
    this.clip(RoundedCornerShape(radius))
        .background(fill)
        .border(1.dp, Line, RoundedCornerShape(radius))

/** Pulsante principale con gradiente d'accento. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(if (enabled) AccentBrush else Brush.linearGradient(listOf(Color(0xFF1C2735), Color(0xFF1C2735))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF04121B) else Color(0xFF5E6B7C),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

/** Angoli a "mirino" attorno a un riquadro. */
fun DrawScope.drawBrackets(topLeft: Offset, side: Float, color: Color, stroke: Float, arm: Float = side * 0.22f) {
    val x0 = topLeft.x
    val y0 = topLeft.y
    val x1 = x0 + side
    val y1 = y0 + side
    fun l(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
    l(Offset(x0, y0 + arm), Offset(x0, y0)); l(Offset(x0, y0), Offset(x0 + arm, y0))
    l(Offset(x1 - arm, y0), Offset(x1, y0)); l(Offset(x1, y0), Offset(x1, y0 + arm))
    l(Offset(x1, y1 - arm), Offset(x1, y1)); l(Offset(x1, y1), Offset(x1 - arm, y1))
    l(Offset(x0 + arm, y1), Offset(x0, y1)); l(Offset(x0, y1), Offset(x0, y1 - arm))
}

/** Marchio dell'app: due tasselli di colore quasi uguali dentro un mirino. */
@Composable
fun LogoMark(size: Dp = 48.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF12202F), Color(0xFF0C1420))),
            cornerRadius = CornerRadius(s * 0.26f),
        )
        drawRoundRect(
            AccentBrush,
            cornerRadius = CornerRadius(s * 0.26f),
            style = Stroke(s * 0.035f),
        )
        val inner = s * 0.44f
        val tl = Offset((s - inner) / 2, (s - inner) / 2)
        drawRect(Cyan, topLeft = tl, size = Size(inner / 2, inner))
        drawRect(Color(0xFF6C8DFF), topLeft = Offset(tl.x + inner / 2, tl.y), size = Size(inner / 2, inner))
        val b = s * 0.62f
        drawBrackets(Offset((s - b) / 2, (s - b) / 2), b, Color(0xCCFFFFFF), s * 0.035f, arm = b * 0.2f)
    }
}
