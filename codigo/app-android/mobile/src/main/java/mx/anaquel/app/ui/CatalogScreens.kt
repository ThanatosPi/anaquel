package mx.anaquel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.*

// Ajustes → Catálogo: las categorías (con su afinidad climática) y las temporadas del comercio. Se
// agregan y se editan; no se borran, porque hay productos que las citan.
@Composable fun CatalogScreen(categories: List<Category>, seasons: List<Season>, zone: ClimateZone, saving: Boolean, error: String?,
    onSaveCategory: (Long?, String, String?, () -> Unit) -> Unit,
    onSaveSeason: (Long?, String, Int, Int, Int, Int, () -> Unit) -> Unit, onDialogClosed: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf("") }   // "categoria:<id|nueva>" o "temporada:<id|nueva>"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("catalogo"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Categorías", "La afinidad con el clima se declara para la zona ${zone.label.lowercase()} de tu negocio.")
        categories.forEach { item ->
            OutlinedCard(onClick = { editing = "categoria:${item.id}" }, modifier = Modifier.fillMaxWidth().testTag("categoria_fila_${item.id}")) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.affinity?.let { "Afinidad ${affinityLabel(it).lowercase()} con la zona ${item.affinityZone?.let { z -> ClimateZone.valueOf(z).label.lowercase() }}" }
                        ?: "Sin afinidad declarada", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = { editing = "categoria:nueva" }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("agregar_categoria")) { Text("Agregar categoría") }

        Spacer(Modifier.height(8.dp))
        SectionTitle("Temporadas", "Día y mes, sin año: se repiten cada año. Una temporada puede cruzar de diciembre a enero.")
        seasons.forEach { item ->
            OutlinedCard(onClick = { editing = "temporada:${item.id}" }, modifier = Modifier.fillMaxWidth().testTag("temporada_fila_${item.id}")) {
                Text(item.label, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        OutlinedButton(onClick = { editing = "temporada:nueva" }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("agregar_temporada")) { Text("Agregar temporada") }
    }

    fun close() { editing = ""; onDialogClosed() }
    val (kind, id) = editing.split(':').let { it.getOrNull(0).orEmpty() to it.getOrNull(1)?.toLongOrNull() }
    if (kind == "categoria") {
        val current = categories.find { it.id == id }
        CategoryDialog(current, categories.filter { it.id != current?.id }.map { it.name }, saving, error,
            onSave = { name, affinity -> onSaveCategory(current?.id, name, affinity, ::close) }, onCancel = ::close)
    }
    if (kind == "temporada") {
        val current = seasons.find { it.id == id }
        SeasonDialog(current, seasons.filter { it.id != current?.id }.map { it.name }, saving, error,
            onSave = { name, sd, sm, ed, em -> onSaveSeason(current?.id, name, sd, sm, ed, em, ::close) }, onCancel = ::close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CategoryDialog(current: Category?, others: List<String>, saving: Boolean, error: String?,
    onSave: (String, String?) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var affinity by rememberSaveable(current?.id) { mutableStateOf(current?.affinity.orEmpty()) }
    var tried by rememberSaveable(current?.id) { mutableStateOf(false) }
    val errors = CatalogRules.checkCategory(name, others)
    AlertDialog(onDismissRequest = { if (!saving) onCancel() },
        title = { Text(if (current == null) "Nueva categoría" else "Editar categoría") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre") }, singleLine = true, enabled = !saving,
                isError = tried && errors["nombre"] != null, supportingText = (if (tried) errors["nombre"] else null)?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("categoria_nombre"))
            Text("Afinidad con el clima", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(affinity.isEmpty(), { affinity = "" }, label = { Text("Ninguna") }, enabled = !saving,
                    modifier = Modifier.testTag("categoria_afinidad_ninguna"))
                AFFINITIES.forEach { item ->
                    FilterChip(affinity == item, { affinity = item }, label = { Text(affinityLabel(item)) }, enabled = !saving,
                        modifier = Modifier.testTag("categoria_afinidad_$item"))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = { tried = true; if (errors.isEmpty()) onSave(name, affinity.ifEmpty { null }) },
            enabled = !saving, modifier = Modifier.testTag("guardar_categoria")) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !saving) { Text("Cancelar") } })
}

@Composable private fun SeasonDialog(current: Season?, others: List<String>, saving: Boolean, error: String?,
    onSave: (String, Int, Int, Int, Int) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var startDay by rememberSaveable(current?.id) { mutableStateOf(current?.startDay?.toString().orEmpty()) }
    var startMonth by rememberSaveable(current?.id) { mutableStateOf(current?.startMonth?.toString().orEmpty()) }
    var endDay by rememberSaveable(current?.id) { mutableStateOf(current?.endDay?.toString().orEmpty()) }
    var endMonth by rememberSaveable(current?.id) { mutableStateOf(current?.endMonth?.toString().orEmpty()) }
    var tried by rememberSaveable(current?.id) { mutableStateOf(false) }
    val errors = CatalogRules.checkSeason(name, startDay, startMonth, endDay, endMonth, others)
    @Composable fun dateRow(label: String, day: String, onDay: (String) -> Unit, month: String, onMonth: (String) -> Unit, tag: String) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(day, { onDay(it.take(2)) }, label = { Text("Día") }, singleLine = true, enabled = !saving,
                isError = tried && errors[tag] != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("temporada_dia_$tag"))
            OutlinedTextField(month, { onMonth(it.take(2)) }, label = { Text("Mes (1–12)") }, singleLine = true, enabled = !saving,
                isError = tried && errors[tag] != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("temporada_mes_$tag"))
        }
        if (tried) errors[tag]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    AlertDialog(onDismissRequest = { if (!saving) onCancel() },
        title = { Text(if (current == null) "Nueva temporada" else "Editar temporada") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre") }, singleLine = true, enabled = !saving,
                isError = tried && errors["nombre"] != null, supportingText = (if (tried) errors["nombre"] else null)?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("temporada_nombre"))
            dateRow("Empieza", startDay, { startDay = it }, startMonth, { startMonth = it }, "inicio")
            dateRow("Termina", endDay, { endDay = it }, endMonth, { endMonth = it }, "fin")
            if (errors.isEmpty()) Text(Season(0, name.trim(), startDay.trim().toInt(), startMonth.trim().toInt(), endDay.trim().toInt(),
                endMonth.trim().toInt()).label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("temporada_vista"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            tried = true
            if (errors.isEmpty()) onSave(name, startDay.trim().toInt(), startMonth.trim().toInt(), endDay.trim().toInt(), endMonth.trim().toInt())
        }, enabled = !saving, modifier = Modifier.testTag("guardar_temporada")) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !saving) { Text("Cancelar") } })
}
