package mx.anaquel.app

import mx.anaquel.app.data.BusinessParser
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BusinessRulesTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val cities get() = BusinessParser.municipalities(fixture("municipios.json"))

    @Test fun everyCatalogCenterResolvesToItself() {
        assertEquals(21, cities.size)
        assertEquals((1L..21L).toList(), cities.map { it.id })
        cities.forEach { assertEquals(it, ZoneResolver.resolve(it.latitude, it.longitude, cities)) }
    }
    @Test fun outsideCoverageAndInvalidCoordinatesNeverGuessAZone() {
        assertNull(ZoneResolver.resolve(0.0, 0.0, cities))
        assertNull(ZoneResolver.resolve(Double.NaN, -99.0, cities))
        assertNull(ZoneResolver.resolve(91.0, -99.0, cities))
    }
    @Test fun nearestNonCoveringMunicipalityDoesNotHideACoveringOne() {
        val near = Municipality("Prueba", "Cerca", ClimateZone.FRIO, 0.0, 0.01, 0.5)
        val covering = near.copy(name = "Cubre", longitude = 0.02, radiusKm = 4.0)
        assertEquals(covering, ZoneResolver.resolve(0.0, 0.0, listOf(near, covering)))
    }
    @Test fun exactRadiusIsIncludedButOutsideIsExcluded() {
        val center = Municipality("Prueba", "Centro", ClimateZone.TROPICAL, 0.0, 0.0, 1.0)
        val radius = ZoneResolver.distanceKm(0.0, 0.1, center)
        val city = center.copy(radiusKm = radius)
        assertEquals(city, ZoneResolver.resolve(0.0, 0.1, listOf(city)))
        assertNull(ZoneResolver.resolve(0.0, 0.100001, listOf(city)))
    }
    @Test fun exactTiesPreserveInputOrderAndOverlapChoosesNearest() {
        val first = Municipality("Prueba", "Primero", ClimateZone.TROPICAL, 0.0, 0.01, 10.0)
        val second = first.copy(name = "Segundo")
        assertEquals(first, ZoneResolver.resolve(0.0, 0.0, listOf(first, second)))
        assertEquals(second, ZoneResolver.resolve(0.0, 0.0, listOf(first.copy(longitude = 0.02), second)))
    }
    @Test fun catalogHasSixtyProductsAndDoesNotInventMissingCount() {
        val catalog = BusinessParser.catalog(fixture("catalogo.json"))
        assertEquals(60, catalog.products.size)
        val missing = catalog.products.single { it.sku == "SIN-001" }
        assertNull(missing.physical); assertNull(missing.countedAt)
        val known = catalog.products.single { it.sku == "LLA-014" }
        assertEquals(0, known.difference!!.compareTo(CountRules.difference(known.theoretical, known.physical!!)))
    }
    @Test fun quantitiesAllowZeroAndThreeDecimalsWithoutRounding() {
        assertEquals(BigDecimal.ZERO, CountRules.parse("0"))
        assertEquals(BigDecimal("2.125"), CountRules.parse(" 2,125 "))
        assertTrue(CountRules.valid(BigDecimal("999999999.999")))
        listOf("", " ", "-1", "0.0001", "1e3", "1,000.000", "1000000000", "NaN").forEach { assertNull(it, CountRules.parse(it)) }
        assertFalse(CountRules.valid(BigDecimal("1.0001")))
        assertFalse(CountRules.valid(BigDecimal("-0.001")))
    }
    @Test fun catalogRejectsNegativeDuplicateOrUndatedCounts() {
        listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("productos").getJSONObject(0).getJSONObject("conciliacion_vigente").put("existencia_contada", -1) },
            { it.getJSONArray("productos").getJSONObject(1).put("codigo_sku", "LLA-014") },
            { it.getJSONArray("productos").getJSONObject(0).getJSONObject("conciliacion_vigente").put("fecha_hora_conteo", JSONObject.NULL) }
        ).forEach { mutate ->
            val root = JSONObject(fixture("catalogo.json")); mutate(root)
            assertThrows(IllegalArgumentException::class.java) { BusinessParser.catalog(root.toString()) }
        }
    }
    @Test fun serializedCountsPreserveHistoryIdentityQuantityAndConfirmation() {
        val first = StockCount("first", "CAB-205", BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:00:00-06:00")
        val second = first.copy(id = "second", physical = BigDecimal.ZERO, countedAt = "2026-09-23T14:01:00-06:00")
        assertEquals(listOf(first, second), BusinessRepository.decodeCounts(BusinessRepository.encodeCounts(listOf(first, second))))
    }
    @Test fun serializedGpsPreservesCoordinatesAndManualRemovesThem() {
        val location = BusinessLocation(ClimateZone.TROPICAL, "Mérida", "Yucatán", "GPS", "2026-09-23T14:00:00-06:00", 20.9672, -89.6247)
        val text = BusinessRepository.encodeLocation(location)
        assertEquals(location, BusinessRepository.decodeLocation(text))
        assertEquals(location.latitude!!, JSONObject(text).getDouble("latitud"), 0.0)
        assertEquals(location.longitude!!, JSONObject(text).getDouble("longitud"), 0.0)
        val manual = BusinessRepository.encodeLocation(location.copy(origin = "MANUAL"))
        assertFalse(JSONObject(manual).has("latitud")); assertFalse(JSONObject(manual).has("longitud"))
        assertEquals(location.copy(origin = "MANUAL", latitude = null, longitude = null), BusinessRepository.decodeLocation(manual))
    }
}
