package mx.anaquel.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.anaquel.app.R
import mx.anaquel.app.domain.Output

// Red Hat Display y Red Hat Text son variables (OFL); Font() fija el eje wght con el peso pedido.
// Ojo: Font(res, peso) sin variationSettings usa la sobrecarga con ajustes vacíos y dibuja la
// instancia por defecto de la fuente variable en todos los pesos (se vio en el teléfono, 6/oct).
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
private val Display = FontFamily(listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    .map { variable(R.font.red_hat_display, it) })
private val Body = FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    .map { variable(R.font.red_hat_text, it) })

// Dirección C «Madrugada»: tinta índigo sobre un fondo apenas azulado; de noche, azul profundo.
private val Light = lightColorScheme(
    primary = Color(0xFF2A3E9E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE3F8), onPrimaryContainer = Color(0xFF101633),
    secondaryContainer = Color(0xFFDEE3F8), onSecondaryContainer = Color(0xFF101633),
    background = Color(0xFFF3F4FA), onBackground = Color(0xFF101633),
    surface = Color(0xFFF3F4FA), onSurface = Color(0xFF101633),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDEFF8), surfaceContainerHigh = Color(0xFFE6E9F5),
    surfaceContainerHighest = Color(0xFFDFE3F2),
    onSurfaceVariant = Color(0xFF5A6187), outline = Color(0xFF8A90B0), outlineVariant = Color(0xFFDDE0EE),
    error = Color(0xFFBA1A1A)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FA6FF), onPrimary = Color(0xFF0A0F2A),
    primaryContainer = Color(0xFF1F2A5E), onPrimaryContainer = Color(0xFFDEE3FF),
    secondaryContainer = Color(0xFF1F2A5E), onSecondaryContainer = Color(0xFFDEE3FF),
    background = Color(0xFF070B1F), onBackground = Color(0xFFEEF1FB),
    surface = Color(0xFF070B1F), onSurface = Color(0xFFEEF1FB),
    surfaceContainerLowest = Color(0xFF0B1030), surfaceContainerLow = Color(0xFF10163A),
    surfaceContainer = Color(0xFF141B44), surfaceContainerHigh = Color(0xFF1B2350),
    surfaceContainerHighest = Color(0xFF222B5A),
    onSurfaceVariant = Color(0xFF8F9AC0), outline = Color(0xFF5C6690), outlineVariant = Color(0xFF232C5A),
    error = Color(0xFFFFB4AB)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnaquelTheme(content: @Composable () -> Unit) {
    val body = TextStyle(fontFamily = Body, fontSize = 17.sp, lineHeight = 25.sp)
    val label = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    fun display(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = Display, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            displayLarge = display(44, 50, FontWeight.ExtraBold).copy(letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
            displayMedium = display(36, 42, FontWeight.ExtraBold),
            displaySmall = display(32, 38, FontWeight.ExtraBold),
            headlineLarge = display(28, 34, FontWeight.ExtraBold).copy(letterSpacing = (-0.2).sp),
            headlineMedium = display(24, 30, FontWeight.Bold),
            headlineSmall = display(22, 28, FontWeight.Bold),
            titleLarge = display(20, 26, FontWeight.Bold),
            titleMedium = display(17, 24, FontWeight.Bold),
            titleSmall = label.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = body, bodyMedium = body,
            bodySmall = body.copy(fontSize = 15.sp, lineHeight = 22.sp),
            labelLarge = label.copy(fontWeight = FontWeight.SemiBold),
            labelMedium = label, labelSmall = label
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}

@Composable fun outputColor(output: Output): Color {
    val dark = isSystemInDarkTheme()
    return when (output) {
        Output.LIQUIDAR -> Color(if (dark) 0xFFFFB877 else 0xFFB4610A)
        Output.RESURTIR -> Color(if (dark) 0xFF7ED99A else 0xFF1E6B3A)
        Output.RETENER -> Color(if (dark) 0xFF8FC4F5 else 0xFF1B5E9E)
        Output.ABSTENERSE -> Color(if (dark) 0xFFC7B6F2 else 0xFF5B4A8A)
    }
}

// El ámbar del contrato permanece en acentos; su texto sobre el contenedor claro
// usa esta variante para cumplir el contraste mínimo de 4.5:1.
@Composable fun outputTextColor(output: Output): Color =
    if (output == Output.LIQUIDAR && !isSystemInDarkTheme()) Color(0xFF854604) else outputColor(output)

@Composable fun outputContainerColor(output: Output): Color =
    outputColor(output).copy(alpha = if (isSystemInDarkTheme()) .18f else .13f)

@Composable fun authorizationButtonColors(output: Output): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = outputColor(output),
    contentColor = if (isSystemInDarkTheme()) Color(0xFF16181D) else Color.White
)

/** Rojo del logo: significa una sola cosa, «esto espera tu decisión». */
@Composable fun decisionRed(): Color = if (isSystemInDarkTheme()) Color(0xFFFF5A66) else Color(0xFFD0202F)

fun outputIcon(output: Output): ImageVector = when (output) {
    Output.LIQUIDAR -> Icons.Outlined.Sell
    Output.RETENER -> Icons.Outlined.Event
    Output.RESURTIR -> Icons.Outlined.Autorenew
    Output.ABSTENERSE -> Icons.AutoMirrored.Outlined.HelpOutline
}
