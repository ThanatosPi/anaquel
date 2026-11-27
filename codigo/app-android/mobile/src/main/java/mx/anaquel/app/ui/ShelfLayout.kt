package mx.anaquel.app.ui

import mx.anaquel.app.domain.Output
import mx.anaquel.app.domain.Recommendation
import java.math.BigDecimal
import java.math.RoundingMode

data class ShelfBox(val id: Long, val output: Output, val height: Float, val decided: Boolean, val noCapital: Boolean)
data class ShelfSlot(val left: Float, val width: Float)

/** La repisa de Inicio: una caja por recomendación, tan alta como su capital detenido. */
object ShelfLayout {
    private val order = listOf(Output.LIQUIDAR, Output.RETENER, Output.RESURTIR, Output.ABSTENERSE)

    fun boxes(recs: List<Recommendation>, decidedIds: Set<Long>): List<ShelfBox> {
        val max = recs.mapNotNull { it.capital }.maxOrNull()?.takeIf { it.signum() > 0 }
        return order.flatMap { output -> recs.filter { it.output == output }.sortedByDescending { it.capital ?: BigDecimal.ZERO } }
            .map { rec ->
                ShelfBox(rec.id, rec.output,
                    height = if (rec.capital == null || max == null) 0f else rec.capital.divide(max, 4, RoundingMode.HALF_UP).toFloat(),
                    decided = rec.importedDecision != null || rec.id in decidedIds,
                    noCapital = rec.capital == null)
            }
    }

    /** Posición de cada caja: separación chica dentro de un grupo y grande entre salidas. */
    fun slots(boxes: List<ShelfBox>, width: Float, gap: Float, groupGap: Float, maxBox: Float, inset: Float): List<ShelfSlot> {
        if (boxes.isEmpty()) return emptyList()
        val switches = boxes.zipWithNext().count { (a, b) -> a.output != b.output }
        val gaps = gap * (boxes.size - 1 - switches) + groupGap * switches
        val box = ((width - 2 * inset - gaps) / boxes.size).coerceIn(1f, maxBox)
        var x = (width - (box * boxes.size + gaps)) / 2
        return boxes.mapIndexed { i, b ->
            if (i > 0) x += if (boxes[i - 1].output != b.output) groupGap else gap
            ShelfSlot(x, box).also { x += box }
        }
    }

    fun hit(slots: List<ShelfSlot>, x: Float, gap: Float): Int? =
        slots.indexOfFirst { x >= it.left - gap / 2 && x <= it.left + it.width + gap / 2 }.takeIf { it >= 0 }
}
