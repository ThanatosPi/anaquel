package mx.anaquel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable fun DetailScreen(run: AnalysisRun, rec: Recommendation, local: LocalDecision?, seen: Boolean, onSeen: () -> Unit) {
    var fullReason by rememberSaveable(rec.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("detalle"),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutputBadge(rec.output)
        Text(rec.product.name, style = MaterialTheme.typography.headlineLarge)
        Text("SKU ${rec.product.sku} · ${rec.product.category} · ${rec.product.cycle}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rec.output == Output.ABSTENERSE) {
            Panel {
                Text("No te doy una recomendación", style = MaterialTheme.typography.titleLarge, color = outputColor(Output.ABSTENERSE))
                Text(rec.abstention.orEmpty())
                HorizontalDivider()
                Text("Qué ayudaría", style = MaterialTheme.typography.titleMedium)
                Text("Revisa el dato que falta antes de la próxima corrida.")
                Button(onClick = onSeen, enabled = !seen, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(if (seen) "Ya la viste" else "Entendido")
                }
                Text("Verla no registra una autorización.", style = MaterialTheme.typography.bodySmall)
            }
        } else Text("El sistema sugiere ${rec.output.label.uppercase()}", style = MaterialTheme.typography.titleLarge)

        rec.antecedent?.let {
            Panel {
                SectionTitle("Lo que pasó antes")
                AntecedentSummary(rec)
            }
        }
        if (rec.marketWarning) MarketWarning(rec)
        Panel {
            val metrics = listOf("Precio original por ${rec.product.unit.singular}" to money(rec.product.originalPrice), "Costo por ${rec.product.unit.singular}" to money(rec.product.cost),
                "Días sin venta" to (rec.daysWithoutSale?.toString() ?: "Sin historial"), "Capital detenido" to money(rec.capital))
            metrics.chunked(if (LocalDensity.current.fontScale > 1.3f) 1 else 2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) -> Metric(label, value, Modifier.weight(1f)) }
                }
            }
            rec.product.expiry?.let { Text("Caduca el ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall) }
        }
        Panel {
            SectionTitle("Por qué lo sugiere")
            Text(rec.justification, maxLines = if (fullReason) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { fullReason = !fullReason }) { Text(if (fullReason) "Leer menos" else "Leer completo") }
        }
        Panel {
            SectionTitle("Existencias de esta corrida")
            if (rec.reconciliation == null) {
                Text("Primero confirma la cantidad en ${rec.product.unit.plural}")
            } else {
                Text("Conteo confirmado: ${stockAmount(rec.reconciliation.quantity, rec.product.unit)}")
                Text("Diferencia contra el sistema: ${stockAmount(rec.reconciliation.difference, rec.product.unit)}")
                Text("Conciliado el ${rec.reconciliation.date}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Panel {
            SectionTitle("El clima también cuenta")
            Text("Zona usada en esta corrida: ${zoneLabel(run.zone)} · Afinidad del producto: ${affinityLabel(rec.affinity)}")
            Text(if (run.zoneSource == "GPS") "Origen: ubicación del teléfono" else "Origen: lo elegiste tú",
                style = MaterialTheme.typography.bodySmall)
        }
        if (rec.references.isNotEmpty()) Panel {
            SectionTitle("Precios de mercado")
            rec.references.take(3).forEach { ref ->
                Text(ref.source, style = MaterialTheme.typography.titleMedium)
                Text("${money(ref.price)} por ${rec.product.unit.singular}", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                Text("Capturado el ${dateLabel(ref.captured)} · Vigente hasta ${dateLabel(ref.validUntil)}", style = MaterialTheme.typography.bodySmall)
                Text(if (ref.valid) "Vigente en esta corrida" else "Vencido", color = if (ref.valid) outputTextColor(Output.RESURTIR) else outputTextColor(Output.LIQUIDAR))
            }
        }
        val decided = local?.decision ?: rec.importedDecision?.decision
        if (decided != null) Panel {
            Text(if (decided == "APROBADA") "Esta recomendación ya está autorizada" else "Esta recomendación ya está rechazada",
                style = MaterialTheme.typography.titleLarge)
            local?.price?.let { Text("Precio autorizado: ${money(it)}") }
            (local?.reason ?: rec.importedDecision?.reason)?.let { Text("Motivo: $it") }
            Text(if (local != null) "Pendiente de enviar" else "Decisión recibida con la corrida", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable fun PriceScreen(rec: Recommendation, value: String, onValue: (String) -> Unit) {
    val quote = requireNotNull(rec.quote)
    val parsed = DecisionRules.parsePrice(value)
    val error = when {
        parsed == null -> "Escribe un precio con hasta dos decimales."
        parsed <= quote.minimum -> "Tiene que ser mayor que el mínimo de ${money(quote.minimum)}."
        parsed >= quote.original -> "Tiene que ser menos que el precio original de ${money(quote.original)}."
        else -> null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("precio"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("El precio lo decides tú.", style = MaterialTheme.typography.headlineLarge)
        Text(rec.product.name, style = MaterialTheme.typography.titleLarge)
        Text("Precios por ${rec.product.unit.singular}")
        rec.antecedent?.let { Panel {
            SectionTitle("Antes de este precio")
            AntecedentSummary(rec)
        } }
        if (rec.marketWarning) MarketWarning(rec)
        var lastValid by remember(rec.id) { mutableStateOf(quote.proposed) }
        val valid = parsed?.takeIf { DecisionRules.validPrice(it, quote) }
        LaunchedEffect(valid) { if (valid != null) lastValid = valid }
        val shown = valid ?: lastValid
        val market = currentMarket(rec.references)
        val big = LocalDensity.current.fontScale > 1.3f
        Panel {
            Text("Autorizas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(shown), style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge)
            Text(priceNote(quote, market, shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PriceRange(quote, market, shown, onPrice = { onValue(it.setScale(2, RoundingMode.HALF_UP).toPlainString()) },
                modifier = Modifier.padding(top = 8.dp))
            Text("El mínimo sale del piso económico (${money(quote.economicFloor)}) y del piso comercial (${money(quote.commercialFloor)}); manda el más alto.",
                style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            val facts = listOfNotNull("Rebaja" to "${discountPercent(shown, quote.original)} %",
                rec.reconciliation?.let { "Recuperas" to money(shown.multiply(it.quantity)) })
            facts.chunked(if (big) 1 else 2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) -> Metric(label, value, Modifier.weight(1f)) }
                }
            }
            rec.reconciliation?.let {
                Text("Si se venden ${stockAmount(it.quantity, rec.product.unit)} a ${money(shown)}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Propuesto: ${money(quote.proposed)} · escalón de ${quote.step * 10} %", style = MaterialTheme.typography.bodySmall)
            rec.product.cost?.let { cost ->
                if (shown < cost) Text("La rebaja queda ${money(cost - shown)} bajo el costo por ${rec.product.unit.singular}")
            }
        }
        OutlinedTextField(value, onValue, label = { Text("Precio que autorizas") },
            prefix = { Text("$") }, singleLine = true, isError = error != null,
            supportingText = { Text(error ?: "Está dentro de los límites permitidos.") },
            textStyle = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("precio_autorizado"))
        Text("Esto solo registra tu decisión. Ningún precio cambia automáticamente.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun AntecedentSummary(rec: Recommendation) {
    val previous = requireNotNull(rec.antecedent)
    val proposal = rec.quote?.let { money(it.proposed) } ?: recommendationHint(rec)
    if (previous.decision == "RECHAZADA") {
        Text("Dijiste: «${previous.reason}»")
        Text("Rechazaste el precio de ${money(previous.price)}. Ahora te propongo $proposal.")
    } else {
        val result = if (previous.unitsSold?.signum() == 0) "sin vender"
            else "con ventas de ${stockAmount(previous.unitsSold, rec.product.unit)} de un total de ${stockAmount(previous.initialQuantity, rec.product.unit)}"
        Text("La campaña a ${money(previous.price)} terminó el ${dateLabel(requireNotNull(previous.ended))} $result. Ahora te propongo $proposal.")
        Text("Autorización anterior: aprobada · Ventas: ${stockAmount(previous.unitsSold, rec.product.unit)}", style = MaterialTheme.typography.bodySmall)
    }
}

