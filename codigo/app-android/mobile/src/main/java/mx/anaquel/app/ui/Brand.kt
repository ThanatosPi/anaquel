package mx.anaquel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LogoNavy = Color(0xFF10265C)
private val LogoRed = Color(0xFFD0202F)

/** El logo oficial (img/logo/anaquel_icono.svg) dibujado sobre su cuadrícula de 512. */
@Composable fun AnaquelMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 512f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        drawRoundRect(LogoNavy, cornerRadius = CornerRadius(112 * u))
        drawLine(Color.White, p(256f, 96f), p(136f, 412f), strokeWidth = 36 * u, cap = StrokeCap.Round)
        drawLine(Color.White, p(256f, 96f), p(376f, 412f), strokeWidth = 36 * u, cap = StrokeCap.Round)
        drawLine(Color.White, p(168f, 376f), p(344f, 376f), strokeWidth = 12 * u)
        val r = CornerRadius(5 * u)
        drawRoundRect(Color.White, p(191f, 328f), Size(38 * u, 42 * u), r)
        drawRoundRect(Color.White, p(237f, 338f), Size(38 * u, 32 * u), r)
        drawRoundRect(LogoRed, p(283f, 334f), Size(38 * u, 36 * u), r)
    }
}

@Composable fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnaquelMark(28.dp)
        Text("anaquel", style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp))
    }
}

/** Contador rojo: sólo significa «esto espera tu decisión». */
@Composable fun DecisionCount(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    // Box y no Surface: el Surface de M3 bloquea toques y dentro del botón se tragaría el clic.
    Box(modifier.background(decisionRed(), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text("$count", color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}
