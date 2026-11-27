package mx.anaquel.app

import mx.anaquel.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

// Las reglas de registrar una venta a mano: las de venta y detalle_venta en la v2, más la de
// BIT-023 (no se vende más de lo que el sistema dice que hay).
class SaleRulesTest {
    private val now = LocalDateTime.parse("2026-10-21T18:00")
    private val llave = SaleLineForm("LLA-014", "Llave Stillson 14\"", MeasureUnit.PIEZA, BigDecimal("7"), quantity = "2", price = "520.00")
    private val cable = SaleLineForm("CAB-205", "Cable calibre 12", MeasureUnit.METRO, BigDecimal("40.5"), quantity = "3.5", price = "14")
    private val valid = SaleForm("venta", folio = " T-001 ", soldAt = "2026-10-21 17:30", lines = listOf(llave, cable))
    private fun errors(form: SaleForm) = SaleRules.check(form, now).keys

    @Test fun aCompleteSaleHasNoErrorsAndComputesSubtotals() {
        assertTrue(errors(valid).isEmpty())
        val draft = SaleRules.toDraft(valid)
        assertEquals("T-001", draft.folio)
        assertEquals(BigDecimal("1040.00"), draft.lines[0].subtotal)
        assertEquals(BigDecimal("49.00"), draft.lines[1].subtotal)
        assertNull(SaleRules.toDraft(valid.copy(folio = "  ")).folio)
    }

    @Test fun dateIsRequiredWellFormedAndNotInTheFuture() {
        assertEquals(setOf("fecha"), errors(valid.copy(soldAt = "21/10/2026 17:30")))
        assertEquals(setOf("fecha"), errors(valid.copy(soldAt = "2026-10-21 18:01")))
        assertTrue(errors(valid.copy(soldAt = "2026-10-21 18:00")).isEmpty())
    }

    @Test fun atLeastOneLineAndEachProductOnce() {
        assertEquals(setOf("renglones"), errors(valid.copy(lines = emptyList())))
        assertEquals(setOf("renglones"), errors(valid.copy(lines = listOf(llave, llave))))
        assertFalse(SaleRules.canAdd(valid, "LLA-014")); assertTrue(SaleRules.canAdd(valid, "SEL-007"))
    }

    @Test fun quantityFollowsTheUnitAndNeverExceedsTheSystemStock() {
        assertEquals(setOf("cantidad_LLA-014"), errors(valid.copy(lines = listOf(llave.copy(quantity = "1.5")))))
        assertEquals(setOf("cantidad_LLA-014"), errors(valid.copy(lines = listOf(llave.copy(quantity = "0")))))
        val tooMany = SaleRules.check(valid.copy(lines = listOf(llave.copy(quantity = "8"))), now)
        assertEquals("El sistema dice que quedan 7 piezas. Cuenta el anaquel primero.", tooMany["cantidad_LLA-014"])
        assertTrue(errors(valid.copy(lines = listOf(llave.copy(quantity = "7")))).isEmpty())
        assertEquals("El sistema dice que queda 1 pieza. Cuenta el anaquel primero.",
            SaleRules.stockMessage(BigDecimal.ONE, MeasureUnit.PIEZA))
    }

    @Test fun priceIsRequiredAndNotNegative() {
        assertEquals(setOf("precio_CAB-205"), errors(valid.copy(lines = listOf(cable.copy(price = "")))))
        assertEquals(setOf("precio_CAB-205"), errors(valid.copy(lines = listOf(cable.copy(price = "-3")))))
        assertTrue(errors(valid.copy(lines = listOf(cable.copy(price = "0")))).isEmpty())   // un regalo cuenta como venta
    }
}
