package mx.anaquel.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.AnalysisRun
import mx.anaquel.app.domain.Output
import mx.anaquel.app.domain.Recommendation

/** La repisa (dirección B): cada producto marcado es una caja tan alta como su capital detenido. */
@Composable fun ShelfView(run: AnalysisRun, decidedIds: Set<Long>, onOpen: (Recommendation) -> Unit, modifier: Modifier = Modifier) {
    val boxes = remember(run, decidedIds) { ShelfLayout.boxes(run.recommendations, decidedIds) }
    if (boxes.isEmpty()) return
    val byId = remember(run) { run.recommendations.associateBy { it.id } }
    val tallest = byId.getValue(boxes.maxBy { it.height }.id)
    val firstTime = remember(run.id) { Intro.firstTime("repisa:${run.id}") }
    val play = firstTime && !animationsOff()
    val total = 600 + 25 * boxes.size
    val clock = remember(run.id) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(run.id) { if (clock.value < 1f) clock.animateTo(1f, tween(total, easing = LinearEasing)) }
    val colors = Output.entries.associateWith { outputColor(it) }
    val abstain = outputColor(Output.ABSTENERSE)
    val shelf = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum", color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.toPx() }
    val groupGap = with(density) { 12.dp.toPx() }
    val maxBox = with(density) { 24.dp.toPx() }
    val inset = with(density) { 8.dp.toPx() }
    val label = "${tallest.product.sku} · ${money(tallest.capital)}"
    Canvas(modifier.fillMaxWidth().height(156.dp)
        .semantics { contentDescription = "Repisa con ${boxes.size} productos marcados. El que más dinero detiene es ${tallest.product.name}, ${money(tallest.capital)}." }
        .pointerInput(boxes) {
            detectTapGestures { pos ->
                val slots = ShelfLayout.slots(boxes, size.width.toFloat(), gap, groupGap, maxBox, inset)
                ShelfLayout.hit(slots, pos.x, gap)?.let { onOpen(byId.getValue(boxes[it].id)) }
            }
        }) {
        val slots = ShelfLayout.slots(boxes, size.width, gap, groupGap, maxBox, inset)
        val plankY = size.height - 22.dp.toPx()
        val labelLayout = measurer.measure(label, labelStyle)
        val maxH = plankY - labelLayout.size.height - 8.dp.toPx()
        val elapsed = clock.value * total
        boxes.forEachIndexed { i, box ->
            val grow = EaseOutBack.transform(((elapsed - 25f * i) / 600f).coerceIn(0f, 1f))
            val slot = slots[i]
            val full = if (box.noCapital) 16.dp.toPx() else maxOf(box.height * maxH, 2.dp.toPx())
            val h = full * grow
            if (h <= 0f) return@forEachIndexed
            val topLeft = Offset(slot.left, plankY - h)
            val sz = Size(slot.width, h)
            val r = CornerRadius(3.dp.toPx())
            when {
                box.noCapital -> {
                    drawRoundRect(abstain, topLeft, sz, r, style = Stroke(1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                    val q = measurer.measure("?", labelStyle.copy(color = abstain))
                    drawText(q, topLeft = Offset(slot.left + (slot.width - q.size.width) / 2, plankY - h + (h - q.size.height) / 2))
                }
                box.output == Output.ABSTENERSE -> {
                    drawRoundRect(abstain.copy(alpha = .18f), topLeft, sz, r)
                    drawRoundRect(abstain, topLeft, sz, r, style = Stroke(1.5.dp.toPx()))
                }
                else -> drawRoundRect(colors.getValue(box.output).copy(alpha = if (box.decided) .32f else 1f), topLeft, sz, r)
            }
            if (box.id == tallest.id) {
                val cx = slot.left + slot.width / 2
                drawText(labelLayout, alpha = grow.coerceIn(0f, 1f), topLeft = Offset(
                    (cx - labelLayout.size.width / 2).coerceIn(0f, size.width - labelLayout.size.width),
                    plankY - h - labelLayout.size.height - 4.dp.toPx()))
            }
        }
        drawRoundRect(shelf, Offset(4.dp.toPx(), plankY), Size(size.width - 8.dp.toPx(), 5.dp.toPx()), CornerRadius(2.dp.toPx()))
        listOf(18.dp.toPx(), size.width - 22.dp.toPx()).forEach { lx ->
            drawRect(shelf, Offset(lx, plankY + 5.dp.toPx()), Size(4.dp.toPx(), 14.dp.toPx()))
        }
    }
}
