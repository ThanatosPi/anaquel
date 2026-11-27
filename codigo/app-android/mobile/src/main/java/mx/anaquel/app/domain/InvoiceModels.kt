package mx.anaquel.app.domain

import java.math.BigDecimal
import java.time.LocalDate

// Un renglón de factura como lo captura quien la recibe: el producto ya emparejado (MSG-026 #3), lo
// que dice la factura y, todavía como texto, la cantidad y el costo.
data class InvoiceLineForm(val sku: String, val name: String, val unit: MeasureUnit,
    val printed: String = "", val quantity: String = "", val unitCost: String = "")

// clave: el borrador, para que guardar dos veces la misma factura no la duplique (clave_local).
data class InvoiceForm(val key: String, val supplier: String = "", val folio: String = "",
    val issuedOn: String = "", val photoPath: String? = null, val lines: List<InvoiceLineForm> = emptyList())

// sku: el producto ya emparejado; la base local lo convierte en producto_id al guardar.
data class InvoiceLine(val sku: String, val printed: String?, val quantity: BigDecimal, val unitCost: BigDecimal)

data class InvoiceDraft(val key: String, val supplier: String, val folio: String?, val issuedOn: String,
    val photoPath: String?, val lines: List<InvoiceLine>)

data class InvoiceLineSummary(val sku: String, val name: String, val unit: MeasureUnit, val quantity: BigDecimal, val unitCost: BigDecimal?)

data class InvoiceSummary(val id: Long, val supplier: String?, val folio: String?, val issuedOn: String?,
    val photoPath: String?, val lines: List<InvoiceLineSummary>) {
    val total: BigDecimal get() = lines.fold(BigDecimal.ZERO) { sum, line ->
        sum + (line.unitCost ?: BigDecimal.ZERO).multiply(line.quantity).setScale(2, java.math.RoundingMode.HALF_UP)
    }
}

// Lo que defienden factura_proveedor y detalle_factura_proveedor en db/schema.sql, dicho antes de
// guardar: un renglón por producto (la llave es (factura_id, producto_id)), cantidad mayor que cero
// según la unidad (BIT-017) y costo no negativo. Al recibir a mano se pide proveedor, fecha y costo;
// la base los deja vacíos sólo para lo que llegue por OCR sin leerse.
object InvoiceRules {
    fun canAdd(form: InvoiceForm, sku: String) = form.lines.none { it.sku == sku }

    fun check(form: InvoiceForm, today: LocalDate): Map<String, String> = buildMap {
        if (form.supplier.isBlank()) put("proveedor", "Escribe el nombre del proveedor.")
        val issued = runCatching { LocalDate.parse(form.issuedOn.trim()) }.getOrNull()
        when {
            issued == null -> put("fecha", "Escribe la fecha de la factura como AAAA-MM-DD.")
            issued.isAfter(today) -> put("fecha", "La fecha de la factura no puede ser futura.")
        }
        if (form.lines.isEmpty()) put("renglones", "Agrega al menos un producto de la factura.")
        if (form.lines.map { it.sku }.distinct().size != form.lines.size) put("renglones", "Cada producto va una sola vez.")
        form.lines.forEach { line ->
            val quantity = CountRules.parse(line.quantity, line.unit.allowsFraction)
            if (quantity == null || quantity.signum() <= 0) put("cantidad_${line.sku}",
                if (line.unit.allowsFraction) "Cantidad mayor que cero, con hasta tres decimales."
                else "Cantidad entera mayor que cero (${line.unit.plural}).")
            if (DecisionRules.parsePrice(line.unitCost) == null) put("costo_${line.sku}", "Escribe el costo unitario, con hasta dos decimales.")
        }
    }

    // Sólo se llama si check() no encontró errores.
    fun toDraft(form: InvoiceForm): InvoiceDraft = InvoiceDraft(form.key, form.supplier.trim(),
        form.folio.trim().ifEmpty { null }, form.issuedOn.trim(), form.photoPath, form.lines.map {
            InvoiceLine(it.sku, it.printed.trim().ifEmpty { null },
                requireNotNull(CountRules.parse(it.quantity, it.unit.allowsFraction)), requireNotNull(DecisionRules.parsePrice(it.unitCost)))
        })
}
