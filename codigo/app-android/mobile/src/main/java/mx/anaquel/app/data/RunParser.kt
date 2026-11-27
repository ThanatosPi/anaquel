package mx.anaquel.app.data

import mx.anaquel.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

object RunParser {
    fun parse(text: String): AnalysisRun {
        val root = JSONObject(text)
        val business = root.getJSONObject("comercio")
        val recommendations = root.getJSONArray("recomendaciones").objects().map(::recommendation)
        require(recommendations.map { it.id }.distinct().size == recommendations.size) {
            "Identificador de recomendación repetido"
        }
        return AnalysisRun(
            root.getLong("corrida_id"), business.getString("nombre"),
            business.getString("zona_climatica"), business.getString("origen_zona"),
            OffsetDateTime.parse(root.getString("iniciada_en")),
            root.nullableString("terminada_en")?.let(OffsetDateTime::parse),
            root.getInt("productos_evaluados"), recommendations
        )
    }

    private fun recommendation(j: JSONObject): Recommendation {
        val p = j.getJSONObject("producto")
        val output = Output.valueOf(j.getString("salida"))
        val quote = j.optJSONObject("precio")?.let {
            PriceQuote(it.decimal("precio_original"), it.decimal("piso_economico"),
                it.decimal("piso_comercial"), it.decimal("precio_minimo"),
                it.decimal("precio_propuesto"), it.getInt("escalon"),
                it.optBoolean("sin_contraste_de_mercado", false))
        }
        val con = j.optJSONObject("conciliacion")?.let {
            Reconciliation(it.decimal("existencia_fisica"), it.decimal("diferencia"),
                it.getString("conciliada_en"), it.getString("origen"))
        }
        val prior = j.optJSONObject("antecedente")?.let {
            Antecedent(it.getLong("recomendacion_id"), it.getInt("escalon"),
                it.decimal("precio_propuesto"), it.getString("decision"),
                it.nullableString("campana_termino")?.let(LocalDate::parse), it.optionalDecimal("unidades_vendidas"),
                it.optionalDecimal("existencia_inicial"), it.nullableString("motivo"))
        }
        val imported = j.optJSONObject("autorizacion")?.let {
            require(it.getString("decision") in setOf("APROBADA", "RECHAZADA"))
            require(it.getString("decision") != "RECHAZADA" || !it.nullableString("motivo").isNullOrBlank())
            ImportedDecision(it.getString("decision"), it.getString("decidida_en"),
                it.nullableString("motivo"))
        }
        val rec = Recommendation(
            j.getLong("recomendacion_id"), j.optString("escenario"),
            Product(p.getString("sku"), p.getString("nombre"), p.getString("ciclo"),
                p.getString("categoria"), p.decimal("precio_original"), p.optionalDecimal("costo_unitario"),
                p.nullableString("fecha_caducidad")?.let(LocalDate::parse), p.measureUnit()),
            output, j.getString("justificacion"),
            if (j.isNull("dias_sin_venta")) null else j.getInt("dias_sin_venta"),
            j.optionalDecimal("capital_detenido"), j.nullableString("afinidad_clima"), quote,
            j.optJSONObject("retener")?.getString("fecha_reactivacion")?.let(LocalDate::parse),
            j.optJSONObject("resurtir")?.decimal("cantidad_sugerida"),
            j.optJSONObject("abstencion")?.getString("motivo"), con,
            (j.optJSONArray("referencias") ?: JSONArray()).objects().map {
                MarketReference(it.getString("fuente"), it.decimal("precio"),
                    LocalDate.parse(it.getString("capturada_en")),
                    LocalDate.parse(it.getString("vigente_hasta")), it.getBoolean("vigente"))
            }, prior, imported
        )
        require(rec.justification.isNotBlank())
        val allowsFraction = rec.product.unit.allowsFraction
        listOfNotNull(rec.reconciliation?.quantity, rec.quantitySuggested, prior?.unitsSold, prior?.initialQuantity).forEach {
            require(CountRules.valid(it, allowsFraction)) { "Cantidad incompatible con la unidad recibida" }
        }
        rec.reconciliation?.difference?.let { require(CountRules.valid(it.abs(), allowsFraction)) }
        when (output) {
            Output.LIQUIDAR -> {
                require(quote != null && quote.step in 1..9 && DecisionRules.validPrice(quote.proposed, quote))
                require(quote.minimum.compareTo(quote.economicFloor.max(quote.commercialFloor)) == 0) {
                    "El mínimo no coincide con el mayor de los pisos recibidos"
                }
            }
            Output.RETENER -> require(rec.reactivate != null && quote == null)
            Output.RESURTIR -> require(rec.quantitySuggested != null && rec.quantitySuggested > BigDecimal.ZERO && quote == null)
            Output.ABSTENERSE -> require(!rec.abstention.isNullOrBlank() && quote == null)
        }
        if (prior != null) {
            require(prior.id != rec.id) { "Una recomendación no puede ser su propio antecedente" }
            when (prior.decision) {
                "APROBADA" -> {
                    require(prior.ended != null && prior.initialQuantity != null && prior.unitsSold != null) {
                        "La campaña debe indicar cuándo terminó, existencia inicial y unidades vendidas"
                    }
                    require(prior.unitsSold < prior.initialQuantity) { "La campaña ya vendió toda la existencia" }
                    if (output == Output.LIQUIDAR) require(quote!!.step == prior.step + 1) {
                        "Tras una campaña con existencia restante se baja un escalón"
                    }
                }
                "RECHAZADA" -> {
                    require(!prior.reason.isNullOrBlank()) { "El rechazo anterior necesita motivo" }
                    if (output == Output.LIQUIDAR) require(quote!!.step in setOf(prior.step, prior.step - 1)) {
                        "Tras un rechazo se conserva el escalón o se sube uno en precio"
                    }
                }
                else -> error("Decisión de antecedente desconocida")
            }
        }
        return rec
    }
}

internal fun JSONObject.decimal(key: String): BigDecimal = get(key).toString().toBigDecimal()
internal fun JSONObject.measureUnit(): MeasureUnit = MeasureUnit.valueOf(getString("unidad")).also {
    require(getBoolean("admite_fraccion") == it.allowsFraction) { "Unidad y admite_fraccion no coinciden con BIT-017" }
}
internal fun JSONObject.optionalDecimal(key: String): BigDecimal? =
    if (isNull(key)) null else decimal(key)
internal fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
