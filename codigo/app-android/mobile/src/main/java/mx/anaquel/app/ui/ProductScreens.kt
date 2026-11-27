package mx.anaquel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.*

fun cycleLabel(cycle: String) = when (cycle) { "ESTACIONAL" -> "De temporada"; "PERECEDERO" -> "Caduca"; else -> "Todo el año" }
fun affinityLabel(affinity: String) = when (affinity) { "ALTA" -> "Alta"; "MEDIA" -> "Media"; "BAJA" -> "Baja"; else -> "No aplica" }

// Alta y edición de un renglón de producto (v2). Las reglas son las de ProductRules, que repiten los
// CHECK de producto en db/schema.sql; los errores se muestran junto al campo después del primer intento.
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProductFormScreen(initial: ProductForm, categories: List<Category>, seasons: List<Season>, zone: ClimateZone,
    systemStock: String?, takenSkus: Set<String>, saving: Boolean, error: String?,
    onSave: (ProductForm) -> Unit, onCancel: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val key = initial.id ?: -1L
    var sku by rememberSaveable(key) { mutableStateOf(initial.sku) }
    var name by rememberSaveable(key) { mutableStateOf(initial.name) }
    var categoryId by rememberSaveable(key) { mutableLongStateOf(initial.categoryId ?: -1L) }
    var unit by rememberSaveable(key) { mutableStateOf(initial.unit.name) }
    var cycle by rememberSaveable(key) { mutableStateOf(initial.cycle) }
    var seasonId by rememberSaveable(key) { mutableLongStateOf(initial.seasonId ?: -1L) }
    var expiresOn by rememberSaveable(key) { mutableStateOf(initial.expiresOn) }
    var affinity by rememberSaveable(key) { mutableStateOf(initial.affinity.orEmpty()) }
    var price by rememberSaveable(key) { mutableStateOf(initial.price) }
    var cost by rememberSaveable(key) { mutableStateOf(initial.cost) }
    var minStock by rememberSaveable(key) { mutableStateOf(initial.minStock) }
    var maxStock by rememberSaveable(key) { mutableStateOf(initial.maxStock) }
    var initialStock by rememberSaveable(key) { mutableStateOf(initial.initialStock) }
    var active by rememberSaveable(key) { mutableStateOf(initial.active) }
    var tried by rememberSaveable(key) { mutableStateOf(false) }
    val measure = MeasureUnit.valueOf(unit)
    val form = ProductForm(initial.id, sku, name, categoryId.takeIf { it >= 0 }, measure, cycle, seasonId.takeIf { it >= 0 },
        expiresOn, affinity.ifEmpty { null }, price, cost, minStock, maxStock, initialStock, active)
    val errors = ProductRules.check(form, takenSkus)
    fun shown(field: String) = if (tried) errors[field] else null
    val creating = initial.id == null

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("producto"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(sku, { sku = it.take(40) }, label = { Text("Código (SKU)") }, enabled = creating && !saving,
            singleLine = true, isError = shown("sku") != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = { Text(shown("sku") ?: if (creating) "Letras, números y guiones." else "El código no cambia: con él se reconoce el producto.") },
            modifier = Modifier.fillMaxWidth().testTag("producto_sku"))
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nombre") }, enabled = !saving, singleLine = true,
            isError = shown("nombre") != null, supportingText = shown("nombre")?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().testTag("producto_nombre"))

        SectionTitle("Categoría")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { item ->
                FilterChip(categoryId == item.id, { categoryId = item.id }, label = { Text(item.name) }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("categoria_${item.id}"))
            }
        }
        shown("categoria")?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        SectionTitle("Se cuenta en")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasureUnit.entries.forEach { item ->
                FilterChip(unit == item.name, { unit = item.name }, label = { Text(item.plural.replaceFirstChar(Char::titlecase)) },
                    enabled = !saving, modifier = Modifier.heightIn(min = 48.dp).testTag("unidad_${item.name}"))
            }
        }

        SectionTitle("¿Cuándo se vende?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CYCLES.forEach { item ->
                FilterChip(cycle == item, { cycle = item }, label = { Text(cycleLabel(item)) }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("ciclo_$item"))
            }
        }
        if (cycle == "ESTACIONAL") {
            Text("Temporada", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seasons.forEach { item ->
                    FilterChip(seasonId == item.id, { seasonId = item.id }, label = { Text(item.label) }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("temporada_${item.id}"))
                }
            }
            shown("temporada")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (cycle == "PERECEDERO") OutlinedTextField(expiresOn, { expiresOn = it.take(10) }, label = { Text("Caducidad (AAAA-MM-DD)") },
            enabled = !saving, singleLine = true, isError = shown("caducidad") != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text(shown("caducidad") ?: "Contra esta fecha mide su rotación el agente.") },
            modifier = Modifier.fillMaxWidth().testTag("producto_caducidad"))

        OutlinedTextField(price, { price = it.take(13) }, label = { Text("Precio de venta regular ($)") }, enabled = !saving,
            singleLine = true, isError = shown("precio") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = shown("precio")?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("producto_precio"))
        OutlinedTextField(cost, { cost = it.take(13) }, label = { Text("Costo de compra ($, opcional)") }, enabled = !saving,
            singleLine = true, isError = shown("costo") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text(shown("costo") ?: "Sin costo, el agente no propone liquidar este producto.") },
            modifier = Modifier.fillMaxWidth().testTag("producto_costo"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(minStock, { minStock = it.take(7) }, label = { Text("Stock mínimo") }, enabled = !saving, singleLine = true,
                isError = shown("stock") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("producto_minimo"))
            OutlinedTextField(maxStock, { maxStock = it.take(7) }, label = { Text("Stock máximo") }, enabled = !saving, singleLine = true,
                isError = shown("stock") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("producto_maximo"))
        }
        Text(shown("stock") ?: "Opcionales. Al llegar al mínimo, el agente puede proponer resurtir.",
            style = MaterialTheme.typography.bodySmall, color = if (shown("stock") != null) MaterialTheme.colorScheme.error else LocalContentColor.current)
        if (creating) OutlinedTextField(initialStock, { initialStock = it.take(13) }, label = { Text("Existencia inicial (${measure.plural})") },
            enabled = !saving, singleLine = true, isError = shown("existencia") != null,
            keyboardOptions = KeyboardOptions(keyboardType = if (measure.allowsFraction) KeyboardType.Decimal else KeyboardType.Number),
            supportingText = { Text(shown("existencia") ?: "Lo que hay hoy. Después la cambian los conteos, las facturas y las ventas.") },
            modifier = Modifier.fillMaxWidth().testTag("producto_existencia"))
        else Panel {
            Text("Existencia en el sistema: ${systemStock.orEmpty()}", style = MaterialTheme.typography.titleMedium)
            Text("Cambia con los conteos, las facturas y las ventas; aquí no se edita.", style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle("Afinidad con el clima", "Opcional. Se declara para la zona ${zone.label.lowercase()} de tu negocio; si no eliges, manda la de su categoría.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(affinity.isEmpty(), { affinity = "" }, label = { Text("La de su categoría") }, enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("afinidad_categoria"))
            AFFINITIES.forEach { item ->
                FilterChip(affinity == item, { affinity = item }, label = { Text(affinityLabel(item)) }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("afinidad_$item"))
            }
        }
        if (!creating) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Producto activo", style = MaterialTheme.typography.titleMedium)
                Text("Uno descontinuado sale del inventario y de las corridas; no se borra y lo puedes reactivar.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(active, { active = it }, enabled = !saving, modifier = Modifier.testTag("producto_activo"))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (tried && errors.isNotEmpty()) Text("Revisa los campos marcados.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { keyboard?.hide(); tried = true; if (errors.isEmpty()) onSave(form) }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("guardar_producto")) { Text("Guardar producto") }
        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
