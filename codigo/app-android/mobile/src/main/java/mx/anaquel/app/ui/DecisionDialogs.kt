package mx.anaquel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.*
import java.math.BigDecimal

@Composable fun ApproveDialog(recs: List<Recommendation>, price: BigDecimal?, busy: Boolean, onCancel: () -> Unit, onConfirm: (Boolean) -> Unit) {
    val rec = recs.first()
    val multiple = recs.size > 1
    val warning = recs.any { it.marketWarning }
    var acknowledge by rememberSaveable { mutableStateOf(false) }
    val title = if (multiple) "¿Autorizas estas ${recs.size} propuestas?" else when (rec.output) {
        Output.LIQUIDAR -> "¿Autorizas bajar el precio?"
        Output.RESURTIR -> "¿Autorizas pedir ${stockAmount(rec.quantitySuggested, rec.product.unit)}?"
        Output.RETENER -> "¿Dejamos este producto guardado hasta el ${rec.reactivate?.let(::dateLabel)}?"
        Output.ABSTENERSE -> "No hay una propuesta que autorizar"
    }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recs.forEach { item ->
                    if (item.output == Output.LIQUIDAR) {
                        Text("${item.product.name} pasa de ${money(item.quote?.original)} a ${money(if (multiple) item.quote?.proposed else price)} por ${item.product.unit.singular}.")
                        if (multiple) Text("Mínimo ${money(item.quote?.minimum)} · Original ${money(item.quote?.original)}", style = MaterialTheme.typography.bodySmall)
                    } else if (multiple) Text("${item.product.name} · ${recommendationHint(item)}")
                }
                Text(when (rec.output) {
                    Output.LIQUIDAR -> "Nadie cambia el precio sin tu autorización; esto solo deja registrada tu decisión."
                    Output.RESURTIR -> "Se registra tu autorización para resurtir. No se hace ningún pedido automático."
                    Output.RETENER -> "Se registra tu decisión de retenerlo hasta su próxima temporada."
                    Output.ABSTENERSE -> ""
                })
                if (warning) {
                    Text("Hay una referencia de mercado vencida o sin contraste vigente.")
                    Row { Checkbox(acknowledge, { acknowledge = it }, modifier = Modifier.testTag("aceptar_mercado"))
                        Text("Autorizo bajo mi responsabilidad", Modifier.padding(top = 12.dp)) }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(acknowledge) }, enabled = !busy && (!warning || acknowledge),
            colors = authorizationButtonColors(rec.output), modifier = Modifier.testTag("confirmar_autorizacion")) {
            Text("Sí, autorizo")
        } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Todavía no") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RejectDialog(busy: Boolean, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var chip by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }
    val reason = if (chip == "Otro motivo") explanation.trim() else listOf(chip, explanation.trim()).filter(String::isNotBlank).joinToString(". ")
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("¿Por qué no?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("El precio es muy bajo", "Todavía se vende", "El conteo está mal", "Ya lo aparté para un cliente", "Es de temporada", "Otro motivo").forEach { text ->
                        FilterChip(chip == text, { chip = if (chip == text) "" else text }, label = { Text(text) })
                    }
                }
                OutlinedTextField(explanation, { explanation = it }, label = { Text("Cuéntame en tus palabras") },
                    modifier = Modifier.fillMaxWidth().testTag("motivo_rechazo"), minLines = 2)
                if (reason.isBlank()) Text("Necesito un motivo para aprender de tu decisión.")
            }
        },
        confirmButton = { Button(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank() && !busy,
            modifier = Modifier.testTag("confirmar_rechazo")) { Text("Rechazar") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancelar") } }
    )
}
