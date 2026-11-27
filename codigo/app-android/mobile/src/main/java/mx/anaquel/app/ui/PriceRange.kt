package mx.anaquel.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.anaquel.app.domain.Output
import mx.anaquel.app.domain.PriceQuote
import java.math.BigDecimal

/**
 * Riel de precio con los extremos abiertos, los escalones de 10 % y el precio público de
 * referencia. Sólo propone precios válidos; el campo de texto sigue siendo la entrada completa.
 */
@Composable fun PriceRange(quote: PriceQuote, market: BigDecimal?, price: BigDecimal,
    onPrice: (BigDecimal) -> Unit, modifier: Modifier = Modifier) {
    val scale = remember(quote) { PriceScale(quote) }
    if (!scale.usable) return
    val animate = !animationsOff()
    val accent = outputColor(Output.LIQUIDAR)
    val accentText = outputTextColor(Output.LIQUIDAR)
    val rail = MaterialTheme.colorScheme.surfaceContainerHigh
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp,
        fontFeatureSettings = "tnum", color = muted)
    val measurer = rememberTextMeasurer()
    val currentPrice by rememberUpdatedState(price)
    val currentOnPrice by rememberUpdatedState(onPrice)
    var dragging by remember { mutableStateOf(false) }
    val thumb = remember(quote) { Animatable(scale.fraction(price)) }
    LaunchedEffect(price, dragging) {
        val target = scale.fraction(price)
        if (dragging || !animate) thumb.snapTo(target)
        else thumb.animateTo(target, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    val inset = with(LocalDensity.current) { 16.dp.toPx() }
    fun pick(x: Float, width: Int) {
        val f = ((x - inset) / (width - 2 * inset)).coerceIn(0f, 1f)
        currentOnPrice(scale.snap(scale.priceAt(f)))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(78.dp).testTag("rango_precio")
            .pointerInput(scale) { detectTapGestures { pick(it.x, size.width) } }
            .pointerInput(scale) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; pick(it.x, size.width) },
                    onDragEnd = { dragging = false }, onDragCancel = { dragging = false }
                ) { change, _ -> change.consume(); pick(change.position.x, size.width) }
            }
            .semantics {
                contentDescription = "Precio que autorizas"
                stateDescription = money(price)
                customActions = listOf(
                    CustomAccessibilityAction("Subir un escalón") { currentOnPrice(scale.next(currentPrice)); true },
                    CustomAccessibilityAction("Bajar un escalón") { currentOnPrice(scale.previous(currentPrice)); true })
            }) {
            val left = inset
            val right = size.width - inset
            fun x(f: Float) = left + (right - left) * f
            val railY = 38.dp.toPx()
            drawLine(rail, Offset(left, railY), Offset(right, railY), 6.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(left, railY), Offset(x(thumb.value), railY), 6.dp.toPx(), StrokeCap.Round)
            // Escalones con su etiqueta; la que chocaría con la anterior se omite, salvo la elegida.
            var lastRight = Float.NEGATIVE_INFINITY
            scale.steps.sorted().forEach { step ->
                val sx = x(scale.fraction(step))
                drawLine(muted, Offset(sx, railY + 7.dp.toPx()), Offset(sx, railY + 13.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                val hit = step.compareTo(price) == 0
                val layout = measurer.measure("−${discountPercent(step, scale.max)} %",
                    if (hit) labelStyle.copy(color = accentText, fontWeight = FontWeight.Bold) else labelStyle)
                val lx = (sx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                if (lx > lastRight + 4.dp.toPx() || hit) {
                    drawText(layout, topLeft = Offset(lx, railY + 16.dp.toPx()))
                    lastRight = lx + layout.size.width
                }
            }
            // Extremos abiertos: el mínimo y el original no se pueden elegir.
            listOf(left, right).forEach { ex ->
                drawCircle(ring, 6.dp.toPx(), Offset(ex, railY))
                drawCircle(muted, 6.dp.toPx(), Offset(ex, railY), style = Stroke(2.dp.toPx()))
            }
            market?.takeIf { it > scale.min && it < scale.max }?.let { m ->
                val mx = x(scale.fraction(m))
                val layout = measurer.measure("Mercado ${money(m)}", labelStyle)
                drawText(layout, topLeft = Offset((mx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), 0f))
                val tip = Path().apply {
                    moveTo(mx - 4.dp.toPx(), railY - 14.dp.toPx()); lineTo(mx + 4.dp.toPx(), railY - 14.dp.toPx())
                    lineTo(mx, railY - 9.dp.toPx()); close()
                }
                drawPath(tip, muted)
            }
            val tx = x(thumb.value)
            drawCircle(ring, (if (dragging) 17 else 15).dp.toPx(), Offset(tx, railY))
            drawCircle(accent, (if (dragging) 13 else 11).dp.toPx(), Offset(tx, railY))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Mínimo ${money(scale.min)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = muted)
            Text("Original ${money(scale.max)}", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Text("Los extremos quedan fuera: el precio va estrictamente entre el mínimo y el original.",
            style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
