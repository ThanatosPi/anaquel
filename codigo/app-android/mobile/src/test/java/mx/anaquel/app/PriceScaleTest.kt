package mx.anaquel.app

import mx.anaquel.app.data.RunParser
import mx.anaquel.app.domain.*
import mx.anaquel.app.ui.PriceScale
import mx.anaquel.app.ui.discountPercent
import mx.anaquel.app.ui.priceNote
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PriceScaleTest {
    private fun d(s: String) = BigDecimal(s)
    private val quote = PriceQuote(original = d("95.00"), economicFloor = d("43.40"), commercialFloor = d("28.50"),
        minimum = d("43.40"), proposed = d("85.50"), step = 1, withoutMarket = false)
    private val scale = PriceScale(quote)

    @Test fun stepsAreTenPercentRungsStrictlyInsideTheRange() {
        assertEquals(listOf("85.50", "76.00", "66.50", "57.00", "47.50").map(::d), scale.steps)
    }

    @Test fun theChoosableEndsSitStrictlyInside() {
        assertEquals(d("43.50"), scale.low)
        assertEquals(d("94.50"), scale.high)
        assertTrue(scale.usable)
    }

    @Test fun snapNeverReturnsAForbiddenPrice() {
        ((0..200).map { scale.priceAt(it / 200f) } + listOf(d("0"), d("43.40"), d("95.00"), d("500"))).forEach { raw ->
            assertTrue("snap($raw)", DecisionRules.validPrice(scale.snap(raw), quote))
        }
    }

    @Test fun snapPullsToANearbyStepAndOtherwiseRoundsToFiftyCents() {
        assertEquals(d("85.50"), scale.snap(d("84.20")))
        assertEquals(d("81.00"), scale.snap(d("81.10")))
    }

    @Test fun stepActionsWalkTheLadderAndStopAtTheEnds() {
        assertEquals(d("94.50"), scale.next(d("85.50")))
        assertEquals(d("76.00"), scale.previous(d("85.50")))
        assertEquals(d("43.50"), scale.previous(d("47.50")))
        assertEquals(d("43.50"), scale.previous(d("43.50")))
    }

    @Test fun aRangeNarrowerThanFiftyCentsIsNotUsable() {
        assertFalse(PriceScale(quote.copy(minimum = d("99.80"), original = d("100.00"), proposed = d("99.90"))).usable)
    }

    @Test fun everyLiquidationInBothRunsGetsAValidScale() {
        listOf("corrida.json", "corrida_2.json").forEach { name ->
            val run = RunParser.parse(javaClass.classLoader!!.getResource(name)!!.readText())
            run.recommendations.filter { it.output == Output.LIQUIDAR }.mapNotNull { it.quote }.forEach { q ->
                val s = PriceScale(q)
                assertTrue("${q.original}", s.usable)
                s.steps.forEach { assertTrue(DecisionRules.validPrice(it, q)) }
                assertTrue("propuesto ${q.proposed}", s.steps.any { it.compareTo(q.proposed) == 0 })
            }
        }
    }

    @Test fun notesAndDiscountReadTheChosenPrice() {
        assertEquals("10", discountPercent(d("85.50"), d("95.00")))
        assertEquals("15.8", discountPercent(d("80.00"), d("95.00")))
        assertEquals("Es lo que propongo: escalón de 10 %.", priceNote(quote, d("88.00"), d("85.50")))
        assertTrue(priceNote(quote, d("88.00"), d("76.00")).contains("piso, no una meta"))
        assertTrue(priceNote(quote, d("88.00"), d("90.00")).contains("$88.00"))
        assertEquals("Más arriba de lo que propongo, todavía dentro del rango.", priceNote(quote, d("88.00"), d("87.00")))
    }
}
