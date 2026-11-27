package mx.anaquel.app

import mx.anaquel.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

// Las reglas del formulario de producto son las de producto en db/schema.sql, dichas antes de guardar.
class ProductRulesTest {
    private val valid = ProductForm(sku = " tor-900 ", name = "Tornillo de prueba", categoryId = 1, price = "12.50")
    private fun errors(form: ProductForm, taken: Set<String> = emptySet()) = ProductRules.check(form, taken).keys

    @Test fun aCompleteFormHasNoErrorsAndNormalizesTheSku() {
        assertTrue(errors(valid).isEmpty())
        val draft = ProductRules.toDraft(valid, ClimateZone.TEMPLADO)
        assertEquals("TOR-900", draft.sku); assertEquals(BigDecimal("12.50"), draft.price)
        assertNull(draft.affinityZone); assertTrue(draft.active)
    }

    @Test fun skuIsRequiredWellFormedAndUniqueOnlyWhenCreating() {
        assertEquals(setOf("sku"), errors(valid.copy(sku = "")))
        assertEquals(setOf("sku"), errors(valid.copy(sku = "tor 900")))
        assertEquals(setOf("sku"), errors(valid, setOf("TOR-900")))
        assertTrue(errors(valid.copy(id = 7), setOf("TOR-900")).isEmpty())   // al editar, el código no cambia
    }

    @Test fun nameAndCategoryAreRequired() {
        assertEquals(setOf("nombre", "categoria"), errors(valid.copy(name = " ", categoryId = null)))
    }

    @Test fun seasonalNeedsItsSeasonAndPerishableItsDate() {
        assertEquals(setOf("temporada"), errors(valid.copy(cycle = "ESTACIONAL")))
        assertTrue(errors(valid.copy(cycle = "ESTACIONAL", seasonId = 2)).isEmpty())
        assertEquals(setOf("caducidad"), errors(valid.copy(cycle = "PERECEDERO", expiresOn = "31/12/2026")))
        assertEquals(setOf("caducidad"), errors(valid.copy(cycle = "PERECEDERO", expiresOn = "2026-02-30")))
        assertTrue(errors(valid.copy(cycle = "PERECEDERO", expiresOn = "2027-02-28")).isEmpty())
    }

    @Test fun whatDoesNotApplyToTheCycleIsCleared() {
        val draft = ProductRules.toDraft(valid.copy(seasonId = 2, expiresOn = "2027-02-28"), ClimateZone.TEMPLADO)
        assertNull(draft.seasonId); assertNull(draft.expiresOn)
    }

    @Test fun priceIsPositiveCostOptionalAndStocksOrdered() {
        listOf("0", "-1", "12.345", "abc", "").forEach { assertTrue(it, "precio" in errors(valid.copy(price = it))) }
        assertTrue(errors(valid.copy(cost = "")).isEmpty())
        assertEquals(setOf("costo"), errors(valid.copy(cost = "1.234")))
        assertEquals(setOf("stock"), errors(valid.copy(minStock = "10", maxStock = "4")))
        assertEquals(setOf("stock"), errors(valid.copy(minStock = "2.5")))
        assertTrue(errors(valid.copy(minStock = "4", maxStock = "4")).isEmpty())
    }

    @Test fun initialStockFollowsTheUnitAndIsOnlyAskedWhenCreating() {
        assertEquals(setOf("existencia"), errors(valid.copy(initialStock = "2.125")))
        assertTrue(errors(valid.copy(unit = MeasureUnit.METRO, initialStock = "2.125")).isEmpty())
        assertEquals(setOf("existencia"), errors(valid.copy(initialStock = "-1")))
        assertTrue(errors(valid.copy(id = 7, initialStock = "2.125")).isEmpty())
    }

    @Test fun ownAffinityIsAlwaysDeclaredForTheBusinessZone() {
        val draft = ProductRules.toDraft(valid.copy(affinity = "ALTA"), ClimateZone.FRIO)
        assertEquals("ALTA", draft.affinity); assertEquals(ClimateZone.FRIO, draft.affinityZone)
    }

    @Test fun formRoundTripKeepsWhatTheOwnerTyped() {
        val draft = ProductRules.toDraft(valid.copy(cost = "8", minStock = "2", maxStock = "20", affinity = "BAJA"), ClimateZone.TEMPLADO)
        val again = ProductRules.toDraft(ProductRules.toForm(draft), ClimateZone.TEMPLADO)
        assertEquals(draft, again)
    }
}
