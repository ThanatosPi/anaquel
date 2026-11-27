package mx.anaquel.app

import mx.anaquel.app.data.BusinessParser
import mx.anaquel.app.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class Msg015RegressionTest {
    private val catalog = BusinessParser.catalog(javaClass.classLoader!!.getResource("catalogo.json")!!.readText())
    private val invalid = StockCount("old", "SIN-001", BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:00:00-06:00")
    private val valid = invalid.copy(id = "new", physical = BigDecimal("3"), countedAt = "2026-09-24T14:00:00-06:00")

    @Test fun latestCountDeterminesWhetherCorrectionIsPending() {
        assertEquals(0, catalog.productsNeedingCountCorrection(emptyList()))
        val corrected = listOf(invalid, valid)
        assertEquals(0, catalog.productsNeedingCountCorrection(corrected))
        assertEquals(listOf(invalid, valid), corrected)
        assertEquals(1, catalog.productsNeedingCountCorrection(listOf(valid, invalid)))
    }

    @Test fun pendingCorrectionsCountProductsAndRespectTheirUnits() {
        val meter = invalid.copy(id = "meter", sku = "CAB-205")
        assertEquals(0, catalog.productsNeedingCountCorrection(listOf(meter)))
        val repeated = listOf(invalid, meter, invalid.copy(id = "second-invalid"))
        assertEquals(1, catalog.productsNeedingCountCorrection(repeated))
        assertEquals(0, catalog.productsNeedingCountCorrection(repeated + valid))
    }
}
