package mx.anaquel.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Un renglón de venta como lo captura el dueño: el producto, lo que dice el sistema que queda (para
// avisar antes de guardar), y todavía como texto la cantidad y el precio cobrado.
data class SaleLineForm(val sku: String, val name: String, val unit: MeasureUnit, val systemStock: BigDecimal,
    val quantity: String = "", val price: String = "")

// key: el borrador (clave_local). soldAt: fecha y hora locales, «AAAA-MM-DD HH:MM».
data class SaleForm(val key: String, val folio: String = "", val soldAt: String = "", val lines: List<SaleLineForm> = emptyList())

data class SaleLine(val sku: String, val quantity: BigDecimal, val unitPrice: BigDecimal) {
    // En la base, detalle_venta.subtotal es columna calculada con la misma fórmula.
    val subtotal: BigDecimal get() = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
}

data class SaleDraft(val key: String, val folio: String?, val soldAt: LocalDateTime, val lines: List<SaleLine>)

data class SaleLineSummary(val sku: String, val name: String, val unit: MeasureUnit, val quantity: BigDecimal,
    val unitPrice: BigDecimal, val subtotal: BigDecimal)

data class SaleSummary(val id: Long, val folio: String?, val soldAt: String, val lines: List<SaleLineSummary>) {
    val total: BigDecimal get() = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.subtotal }
}

// Las reglas de venta y detalle_venta en la v2, dichas antes de guardar: un renglón por producto (la
// llave es (venta_id, producto_id)), cantidad mayor que cero según la unidad (BIT-017), precio no
// negativo y fecha que no sea futura. Y la de BIT-023: una venta resta de la existencia del sistema,
// así que no puede vender más de lo que el sistema dice que hay.
object SaleRules {
    val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun canAdd(form: SaleForm, sku: String) = form.lines.none { it.sku == sku }

    fun outOfStock(quantity: BigDecimal, systemStock: BigDecimal) = quantity > systemStock

    fun stockMessage(systemStock: BigDecimal, unit: MeasureUnit): String {
        val one = systemStock.compareTo(BigDecimal.ONE) == 0
        return "El sistema dice que ${if (one) "queda" else "quedan"} ${systemStock.stripTrailingZeros().toPlainString()} " +
            "${if (one) unit.singular else unit.plural}. Cuenta el anaquel primero."
    }

    fun check(form: SaleForm, now: LocalDateTime): Map<String, String> = buildMap {
        if (form.folio.isNotBlank() && form.folio.trim().length > 40) put("folio", "El folio es muy largo (hasta 40).")
        val sold = runCatching { LocalDateTime.parse(form.soldAt.trim(), FORMAT) }.getOrNull()
        when {
            sold == null -> put("fecha", "Escribe la fecha y hora como AAAA-MM-DD HH:MM.")
            sold.isAfter(now) -> put("fecha", "La venta no puede ser en el futuro.")
        }
        if (form.lines.isEmpty()) put("renglones", "Agrega al menos un producto vendido.")
        if (form.lines.map { it.sku }.distinct().size != form.lines.size) put("renglones", "Cada producto va una sola vez.")
        form.lines.forEach { line ->
            val quantity = CountRules.parse(line.quantity, line.unit.allowsFraction)
            when {
                quantity == null || quantity.signum() <= 0 -> put("cantidad_${line.sku}",
                    if (line.unit.allowsFraction) "Cantidad mayor que cero, con hasta tres decimales."
                    else "Cantidad entera mayor que cero (${line.unit.plural}).")
                outOfStock(quantity, line.systemStock) -> put("cantidad_${line.sku}", stockMessage(line.systemStock, line.unit))
            }
            if (DecisionRules.parsePrice(line.price) == null) put("precio_${line.sku}", "Escribe el precio cobrado, con hasta dos decimales.")
        }
    }

    // Sólo se llama si check() no encontró errores.
    fun toDraft(form: SaleForm): SaleDraft = SaleDraft(form.key, form.folio.trim().ifEmpty { null },
        LocalDateTime.parse(form.soldAt.trim(), FORMAT), form.lines.map {
            SaleLine(it.sku, requireNotNull(CountRules.parse(it.quantity, it.unit.allowsFraction)), requireNotNull(DecisionRules.parsePrice(it.price)))
        })
}
