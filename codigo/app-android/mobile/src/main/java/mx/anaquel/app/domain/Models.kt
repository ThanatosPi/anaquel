package mx.anaquel.app.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class Output(val label: String) {
    LIQUIDAR("Liquidar"), RESURTIR("Resurtir"), RETENER("Retener"), ABSTENERSE("No decido")
}

enum class MeasureUnit(val singular: String, val plural: String, val allowsFraction: Boolean) {
    PIEZA("pieza", "piezas", false), METRO("metro", "metros", true),
    KILO("kilo", "kilos", true), LITRO("litro", "litros", true);

    fun label(quantity: BigDecimal) = if (quantity.abs().compareTo(BigDecimal.ONE) == 0) singular else plural
}

data class Product(
    val sku: String, val name: String, val cycle: String, val category: String,
    val originalPrice: BigDecimal, val cost: BigDecimal?, val expiry: LocalDate?, val unit: MeasureUnit
)

data class PriceQuote(
    val original: BigDecimal, val economicFloor: BigDecimal, val commercialFloor: BigDecimal,
    val minimum: BigDecimal, val proposed: BigDecimal, val step: Int, val withoutMarket: Boolean
)

data class Reconciliation(
    val quantity: BigDecimal, val difference: BigDecimal, val date: String, val source: String
)

data class MarketReference(
    val source: String, val price: BigDecimal, val captured: LocalDate,
    val validUntil: LocalDate, val valid: Boolean
)

data class Antecedent(
    val id: Long, val step: Int, val price: BigDecimal, val decision: String,
    val ended: LocalDate?, val unitsSold: BigDecimal?,
    val initialQuantity: BigDecimal?, val reason: String?
)

data class ImportedDecision(val decision: String, val date: String, val reason: String?)

data class Recommendation(
    val id: Long, val scenario: String, val product: Product, val output: Output,
    val justification: String, val daysWithoutSale: Int?, val capital: BigDecimal?,
    val affinity: String?, val quote: PriceQuote?, val reactivate: LocalDate?,
    val quantitySuggested: BigDecimal?, val abstention: String?,
    val reconciliation: Reconciliation?, val references: List<MarketReference>,
    val antecedent: Antecedent?, val importedDecision: ImportedDecision?
) {
    val actionable: Boolean
        get() = reconciliation != null && output != Output.ABSTENERSE && importedDecision == null
    val marketWarning: Boolean
        get() = output == Output.LIQUIDAR &&
            (quote?.withoutMarket == true || references.any { !it.valid })
}

data class AnalysisRun(
    val id: Long, val business: String, val zone: String, val zoneSource: String,
    val started: OffsetDateTime, val ended: OffsetDateTime?, val evaluated: Int,
    val recommendations: List<Recommendation>
)

data class LocalDecision(
    val runId: Long, val recommendationId: Long, val decision: String,
    val price: BigDecimal?, val reason: String?, val date: String,
    val actorId: String, val marketRiskAccepted: Boolean
) {
    val key: String get() = "$runId:$recommendationId"
}

object DecisionRules {
    fun parsePrice(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (!Regex("\\d{1,10}([.,]\\d{1,2})?").matches(trimmed)) return null
        return trimmed.replace(',', '.').toBigDecimalOrNull()
    }

    fun validPrice(price: BigDecimal?, quote: PriceQuote): Boolean =
        price != null && price > quote.minimum && price < quote.original

    fun validate(
        rec: Recommendation, decision: String, price: BigDecimal?,
        reason: String?, marketRiskAccepted: Boolean
    ): String? = when {
        !rec.actionable -> "Esta recomendación no admite una decisión."
        decision !in setOf("APROBADA", "RECHAZADA") -> "La decisión no es válida."
        decision == "RECHAZADA" && reason.isNullOrBlank() ->
            "Necesito un motivo para aprender de tu decisión."
        decision == "APROBADA" && rec.output == Output.LIQUIDAR &&
            (rec.quote == null || !validPrice(price, rec.quote)) ->
            "El precio debe ser mayor que el mínimo y menor que el original."
        decision == "APROBADA" && rec.marketWarning && !marketRiskAccepted ->
            "Confirma que autorizas bajo tu responsabilidad."
        else -> null
    }
}
