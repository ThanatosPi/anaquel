package mx.anaquel.app

import mx.anaquel.app.data.BusinessParser
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.data.RunParser
import mx.anaquel.app.domain.*
import mx.anaquel.app.ui.stockAmount
import mx.anaquel.app.ui.recommendationHint
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Msg013RegressionTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun legacyGpsWithoutCoordinatesRequiresConfirmation() {
        val legacy = """{"zona":"TROPICAL","municipio":"Mérida","estado":"Yucatán","origen":"GPS","guardada_en":"2026-09-23T14:00:00-06:00"}"""
        assertNull(BusinessRepository.decodeLocation(legacy))
    }

    @Test fun catalogRejectsFractionalPieces() {
        val root = JSONObject(fixture("catalogo.json"))
        root.getJSONArray("productos").getJSONObject(0).getJSONObject("conciliacion_vigente").put("existencia_contada", "2.125")
        assertThrows(IllegalArgumentException::class.java) { BusinessParser.catalog(root.toString()) }
    }

    @Test fun runRejectsFractionalPieces() {
        val root = JSONObject(fixture("corrida.json"))
        root.getJSONArray("recomendaciones").getJSONObject(0).getJSONObject("conciliacion").put("existencia_fisica", "2.125")
        assertThrows(IllegalArgumentException::class.java) { RunParser.parse(root.toString()) }
    }

    @Test fun gpsRejectsIncompleteNonFiniteAndOutOfRangePoints() {
        val base = BusinessLocation(ClimateZone.TROPICAL, "Mérida", "Yucatán", "GPS", "2026-09-23T14:00:00-06:00", 20.9672, -89.6247)
        listOf(base.copy(latitude = null), base.copy(longitude = null), base.copy(latitude = Double.NaN),
            base.copy(longitude = Double.POSITIVE_INFINITY), base.copy(latitude = 90.001), base.copy(longitude = -180.001)).forEach {
            assertThrows(IllegalArgumentException::class.java) { BusinessRepository.encodeLocation(it) }
        }
        listOf(base.copy(latitude = -90.0, longitude = -180.0), base.copy(latitude = 90.0, longitude = 180.0)).forEach {
            assertEquals(it, BusinessRepository.decodeLocation(BusinessRepository.encodeLocation(it)))
        }
    }

    @Test fun unitContractLabelsAndFractionRulesCoverAllFourUnits() {
        val catalog = BusinessParser.catalog(fixture("catalogo.json"))
        assertEquals(5, catalog.products.count { it.unit == MeasureUnit.METRO })
        assertNull(CountRules.parse("2.125", MeasureUnit.PIEZA.allowsFraction))
        assertNotNull(CountRules.parse("2.000", MeasureUnit.PIEZA.allowsFraction))
        MeasureUnit.entries.forEach { unit ->
            assertEquals("1 ${unit.singular}", stockAmount(BigDecimal.ONE, unit))
            assertEquals("0 ${unit.plural}", stockAmount(BigDecimal.ZERO, unit))
            assertEquals("-1 ${unit.singular}", stockAmount(BigDecimal.ONE.negate(), unit))
            if (unit.allowsFraction) {
                assertEquals(BigDecimal("12.5"), CountRules.parse("12,5", unit.allowsFraction))
                assertEquals("12.5 ${unit.plural}", stockAmount(BigDecimal("12.5"), unit))
            }
        }
    }

    @Test fun contradictoryOrMissingUnitFieldsCannotDefaultToPieces() {
        val root = JSONObject(fixture("catalogo.json"))
        val units = root.getJSONArray("unidades")
        (0 until units.length()).map(units::getJSONObject).single { it.getString("codigo_unidad") == "PIEZA" }
            .put("permite_decimales", true)
        assertThrows(IllegalArgumentException::class.java) { BusinessParser.catalog(root.toString()) }
        root.getJSONArray("productos").getJSONObject(0).remove("codigo_unidad")
        assertThrows(Exception::class.java) { BusinessParser.catalog(root.toString()) }
    }

    @Test fun restockAndCampaignQuantitiesAlsoRespectUnit() {
        val root = JSONObject(fixture("corrida.json"))
        val rows = root.getJSONArray("recomendaciones")
        val restock = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("salida") == "RESURTIR" }
        restock.getJSONObject("resurtir").put("cantidad_sugerida", "12.5")
        assertThrows(IllegalArgumentException::class.java) { RunParser.parse(root.toString()) }
        restock.getJSONObject("producto").put("unidad", "METRO").put("admite_fraccion", true)
        val meters = RunParser.parse(root.toString()).recommendations.single { it.output == Output.RESURTIR }
        assertEquals("Surtir 12.5 metros", recommendationHint(meters))
        val second = JSONObject(fixture("corrida_2.json"))
        second.getJSONArray("recomendaciones").getJSONObject(0).getJSONObject("antecedente").put("unidades_vendidas", "0.5")
        assertThrows(IllegalArgumentException::class.java) { RunParser.parse(second.toString()) }
    }

    @Test fun invalidOldCountIsPreservedButNeverUsedAsCurrentQuantity() {
        val product = BusinessParser.catalog(fixture("catalogo.json")).products.single { it.sku == "SIN-001" }
        val old = StockCount("old", product.sku, BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:00:00-06:00")
        val counts = BusinessRepository.decodeCounts(BusinessRepository.encodeCounts(listOf(old)))
        assertEquals(listOf(old), counts)
        assertNull(product.confirmedQuantity(counts))
        assertEquals(BigDecimal("3"), product.confirmedQuantity(counts + old.copy(id = "new", physical = BigDecimal("3"))))
    }
}
