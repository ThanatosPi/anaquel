package mx.anaquel.app

import mx.anaquel.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

// Las reglas de recibir una factura: las de factura_proveedor y detalle_factura_proveedor en la v2.
class InvoiceRulesTest {
    private val today = LocalDate.parse("2026-10-21")
    private val llave = InvoiceLineForm("LLA-014", "Llave Stillson 14\"", MeasureUnit.PIEZA, quantity = "3", unitCost = "390.00")
    private val cable = InvoiceLineForm("CAB-205", "Cable calibre 12", MeasureUnit.METRO, quantity = "12.5", unitCost = "14")
    private val valid = InvoiceForm("borrador", supplier = "Distribuidora del Sur", issuedOn = "2026-10-20", lines = listOf(llave, cable))
    private fun errors(form: InvoiceForm) = InvoiceRules.check(form, today).keys

    @Test fun aCompleteInvoiceHasNoErrorsAndBecomesADraft() {
        assertTrue(errors(valid).isEmpty())
        val draft = InvoiceRules.toDraft(valid.copy(folio = "  ", lines = listOf(llave.copy(printed = " LLAVE ST 14 "))))
        assertNull(draft.folio); assertEquals("LLAVE ST 14", draft.lines.single().printed)
        assertEquals(BigDecimal("3"), draft.lines.single().quantity); assertEquals(BigDecimal("390.00"), draft.lines.single().unitCost)
    }

    @Test fun supplierDateAndAtLeastOneLineAreRequired() {
        assertEquals(setOf("proveedor", "fecha", "renglones"), errors(InvoiceForm("b")))
        assertEquals(setOf("fecha"), errors(valid.copy(issuedOn = "2026-10-22")))     // no puede ser futura
        assertEquals(setOf("fecha"), errors(valid.copy(issuedOn = "20/10/2026")))
    }

    @Test fun eachProductGoesOnlyOnce() {
        assertFalse(InvoiceRules.canAdd(valid, "LLA-014")); assertTrue(InvoiceRules.canAdd(valid, "SEL-007"))
        assertEquals(setOf("renglones"), errors(valid.copy(lines = listOf(llave, llave))))
    }

    @Test fun quantityIsPositiveAndFollowsTheUnitCostIsRequired() {
        assertEquals(setOf("cantidad_LLA-014"), errors(valid.copy(lines = listOf(llave.copy(quantity = "0")))))
        assertEquals(setOf("cantidad_LLA-014"), errors(valid.copy(lines = listOf(llave.copy(quantity = "2.5")))))
        assertTrue(errors(valid.copy(lines = listOf(cable.copy(quantity = "0.125")))).isEmpty())
        assertEquals(setOf("costo_CAB-205"), errors(valid.copy(lines = listOf(cable.copy(unitCost = "")))))
        assertEquals(setOf("costo_CAB-205"), errors(valid.copy(lines = listOf(cable.copy(unitCost = "-1")))))
    }

    @Test fun totalRoundsEachLineToCents() {
        val summary = InvoiceSummary(1, "Proveedor", null, "2026-10-20", null, listOf(
            InvoiceLineSummary("LLA-014", "Llave", MeasureUnit.PIEZA, BigDecimal("3"), BigDecimal("390.00")),
            InvoiceLineSummary("CAB-205", "Cable", MeasureUnit.METRO, BigDecimal("12.5"), BigDecimal("14.33"))))
        assertEquals(BigDecimal("1349.13"), summary.total)   // 1170.00 + 179.125 → 179.13
    }
}
