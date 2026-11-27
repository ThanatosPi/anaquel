package mx.anaquel.app

import mx.anaquel.app.data.RunParser
import mx.anaquel.app.domain.Output
import mx.anaquel.app.ui.ShelfBox
import mx.anaquel.app.ui.ShelfLayout
import org.junit.Assert.*
import org.junit.Test

class ShelfLayoutTest {
    private val run = RunParser.parse(javaClass.classLoader!!.getResource("corrida.json")!!.readText())
    private fun sku(box: ShelfBox) = run.recommendations.single { it.id == box.id }.product.sku

    @Test fun oneBoxPerRecommendationGroupedByOutputAndCapital() {
        val boxes = ShelfLayout.boxes(run.recommendations, emptySet())
        assertEquals(run.recommendations.size, boxes.size)
        assertEquals(listOf(Output.LIQUIDAR, Output.RETENER, Output.RESURTIR, Output.ABSTENERSE), boxes.map { it.output }.distinct())
        assertEquals("CAL-030", sku(boxes.first()))
        assertEquals("IMP-003", sku(boxes.maxBy { it.height }))
        assertEquals(1f, boxes.maxOf { it.height }, 0f)
    }

    @Test fun missingCostIsDashedAndDecisionsFade() {
        val sel = run.recommendations.single { it.product.sku == "SEL-007" }.id
        val boxes = ShelfLayout.boxes(run.recommendations, setOf(sel))
        assertTrue(boxes.single { sku(it) == "NOC-001" }.noCapital)
        assertEquals(setOf("LLA-014", "CAL-030", "SEL-007"), boxes.filter { it.decided }.map(::sku).toSet())
    }

    @Test fun slotsFitTheWidthWithoutOverlapAndHitTheRightBox() {
        val boxes = ShelfLayout.boxes(run.recommendations, emptySet())
        val slots = ShelfLayout.slots(boxes, 300f, 4f, 12f, 22f, 8f)
        assertEquals(20f, slots.first().width, 0.01f)
        assertEquals(8f, slots.first().left, 0.01f)
        assertEquals(292f, slots.last().left + slots.last().width, 0.01f)
        slots.zipWithNext().forEach { (a, b) -> assertTrue(b.left >= a.left + a.width) }
        assertEquals(0, ShelfLayout.hit(slots, 9f, 4f))
        assertNull(ShelfLayout.hit(slots, 1f, 4f))
    }
}
