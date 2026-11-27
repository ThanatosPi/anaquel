package mx.anaquel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.anaquel.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-MX")
fun money(value: BigDecimal?): String = value?.let {
    NumberFormat.getCurrencyInstance(spanish).format(it)
} ?: "Sin dato"
fun amount(value: BigDecimal?): String = value?.stripTrailingZeros()?.toPlainString() ?: "Sin dato"
fun stockAmount(value: BigDecimal?, unit: MeasureUnit): String = value?.let { "${amount(it)} ${unit.label(it)}" } ?: "Sin dato"
fun dateLabel(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("d MMM yyyy", spanish))
fun zoneLabel(value: String): String = when (value) {
    "TROPICAL" -> "Tropical"; "FRIO" -> "Frío"; "TEMPLADO" -> "Templado"
    "ARIDO" -> "Árido"; "SEMIARIDO" -> "Semiárido"; else -> value
}
fun affinityLabel(value: String?): String = when (value) {
    "ALTA" -> "Alta"; "MEDIA" -> "Media"; "BAJA" -> "Baja"; "NO_APLICA" -> "No aplica"; else -> "Sin declarar"
}

@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun OutputBadge(output: Output) {
    Surface(color = outputContainerColor(output), shape = CircleShape) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(outputIcon(output), null, Modifier.size(18.dp), tint = outputTextColor(output))
            Text(output.label.uppercase(spanish), color = outputTextColor(output),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.8.sp))
        }
    }
}

@Composable fun OutputIcon(output: Output, selected: Boolean = false) {
    Surface(Modifier.size(40.dp), shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else outputContainerColor(output)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (selected) Icons.Outlined.Check else outputIcon(output), null, Modifier.size(22.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else outputTextColor(output))
        }
    }
}

@Composable fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable fun MarketWarning(rec: Recommendation) {
    val color = outputColor(Output.LIQUIDAR)
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, tint = color)
                Text("Sin precio de mercado vigente", style = MaterialTheme.typography.titleMedium)
            }
            val expired = rec.references.filterNot { it.valid }.maxByOrNull { it.validUntil }
            Text(expired?.let { "Precio de mercado vencido el ${dateLabel(it.validUntil)}. Puedes autorizar bajo tu responsabilidad." }
                ?: "Esta propuesta no tiene contraste de mercado. Puedes autorizar bajo tu responsabilidad.")
        }
    }
}

fun recommendationHint(rec: Recommendation): String = when (rec.output) {
    Output.LIQUIDAR -> "Precio propuesto ${money(rec.quote?.proposed)} por ${rec.product.unit.singular}"
    Output.RETENER -> "Reactivar el ${rec.reactivate?.let(::dateLabel)}"
    Output.RESURTIR -> "Surtir ${stockAmount(rec.quantitySuggested, rec.product.unit)}"
    Output.ABSTENERSE -> rec.abstention.orEmpty()
}

@Composable fun RecommendationCard(rec: Recommendation, decided: String?, onClick: () -> Unit,
    onLongClick: () -> Unit = {}, selected: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.large,
        color = if (rec.reconciliation == null) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().testTag("rec_${rec.id}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutputIcon(rec.output, selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rec.product.name, style = MaterialTheme.typography.titleMedium)
                // El monto va en su propio renglón: a la derecha apretaba el nombre (visto en el teléfono, 6/oct).
                Text("${money(rec.capital)} detenidos", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                Text("SKU ${rec.product.sku} · ${rec.daysWithoutSale?.let { "$it días sin venta" } ?: "Sin historial"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recommendationHint(rec), color = outputTextColor(rec.output),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                when {
                    rec.reconciliation == null -> Text("Primero confirma la cantidad en ${rec.product.unit.plural}", fontWeight = FontWeight.SemiBold)
                    decided != null -> Text(if (decided == "APROBADA") "Autorizada" else "Rechazada", fontWeight = FontWeight.SemiBold)
                    rec.marketWarning -> Text("Referencia de mercado vencida", color = outputTextColor(rec.output), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
