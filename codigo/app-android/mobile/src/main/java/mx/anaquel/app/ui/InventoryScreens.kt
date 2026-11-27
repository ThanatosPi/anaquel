package mx.anaquel.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import mx.anaquel.app.domain.*
import java.io.File
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable fun InventoryScreen(catalog: InventoryCatalog, counts: List<StockCount>, onOpen: (String) -> Unit,
    onAdd: () -> Unit = {}, onEdit: (String) -> Unit = {}) {
    var search by rememberSaveable { mutableStateOf("") }
    var unconfirmedOnly by rememberSaveable { mutableStateOf(false) }
    var showDiscontinued by rememberSaveable { mutableStateOf(false) }
    val products = remember(catalog, counts, search, unconfirmedOnly) {
        catalog.products.filter { product ->
            (product.name.contains(search.trim(), ignoreCase = true) || product.sku.contains(search.trim(), ignoreCase = true)) &&
                (!unconfirmedOnly || product.confirmedQuantity(counts) == null)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(search, { search = it }, label = { Text("Busca por nombre o SKU") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("buscar_inventario"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(unconfirmedOnly, { unconfirmedOnly = !unconfirmedOnly; showDiscontinued = false },
                label = { Text("Sin confirmar") }, modifier = Modifier.testTag("filtro_sin_confirmar"))
            // Un producto descontinuado no se borra (estado_producto): este filtro lo muestra, solo, para
            // editarlo o reactivarlo. Es un filtro y no un bloque arriba de la lista: con llaves, la lista
            // conserva el primer renglón visible y lo insertado arriba quedaba fuera de la pantalla.
            if (catalog.discontinued.isNotEmpty() || showDiscontinued) FilterChip(showDiscontinued,
                { showDiscontinued = !showDiscontinued; unconfirmedOnly = false },
                label = { Text("Descontinuados (${catalog.discontinued.size})") }, modifier = Modifier.testTag("filtro_descontinuados"))
        }
        val retired = catalog.discontinued.filter { it.name.contains(search.trim(), true) || it.sku.contains(search.trim(), true) }
        Text(if (showDiscontinued) "${retired.size} de ${catalog.discontinued.size} descontinuados"
            else "${products.size} de ${catalog.products.size} productos", style = MaterialTheme.typography.bodySmall)
        // Espacio abajo para que el botón flotante no tape el último producto.
        LazyColumn(Modifier.weight(1f).testTag("inventario"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (showDiscontinued) items(retired, key = { "baja_" + it.sku }) { product ->
                OutlinedCard(onClick = { onEdit(product.sku) }, modifier = Modifier.fillMaxWidth().testTag("descontinuado_${product.sku}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleLarge)
                        Text("SKU ${product.sku} · Descontinuado · Toca para editarlo o reactivarlo", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if ((if (showDiscontinued) retired else products).isEmpty()) item { Text("No hay productos con esta búsqueda.") }
            if (!showDiscontinued) items(products, key = { it.sku }) { product ->
                val latest = counts.lastOrNull { it.sku == product.sku }
                val quantity = product.confirmedQuantity(counts)
                OutlinedCard(onClick = { onOpen(product.sku) }, modifier = Modifier.fillMaxWidth().testTag("producto_${product.sku}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleLarge)
                        Text("SKU ${product.sku} · ${product.category}", style = MaterialTheme.typography.bodySmall)
                        Text("Sistema ${stockAmount(product.theoretical, product.unit)} · Contado ${quantity?.let { stockAmount(it, product.unit) } ?: "Sin confirmar"}")
                        // Desde 0.4.0 un conteo fija la existencia del sistema, así que la diferencia es la que
                        // había al contar (existencia_sistema_al_contar − existencia_contada), como en la base.
                        val gap = if (latest != null) latest.systemAtCount?.let { CountRules.difference(it, latest.physical) } else product.difference
                        if (quantity != null && gap != null) Text("Diferencia (sistema − contado): ${stockAmount(gap, product.unit)}")
                        Text(when {
                            latest != null && quantity == null -> "El registro anterior requiere un nuevo conteo"
                            latest != null -> "Confirmado en este teléfono · Pendiente de enviar"
                            product.countedAt != null -> "Conciliado el ${product.countedAt}"
                            else -> "Sin confirmar"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Agregar producto") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("agregar_producto"))
    }
}

@Composable fun InventoryDetail(product: InventoryProduct, counts: List<StockCount>, saving: Boolean,
    error: String?, onEdit: () -> Unit = {}, onSave: (String, BigDecimal, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val history = counts.filter { it.sku == product.sku }
    val latestQuantity = product.confirmedQuantity(counts)
    var text by rememberSaveable(product.sku) { mutableStateOf(latestQuantity?.let(::amount).orEmpty()) }
    var draftId by rememberSaveable(product.sku) { mutableStateOf(UUID.randomUUID().toString()) }
    var pendingPhoto by rememberSaveable(product.sku) { mutableStateOf("") }
    var notice by rememberSaveable(product.sku) { mutableStateOf("") }
    val directory = remember(context) { File(context.cacheDir, "counts") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPhoto.takeIf { it.startsWith("count-") && '/' !in it && '\\' !in it }?.let { File(directory, it) }
        val captured = success && file?.isFile == true && file.length() > 0
        file?.delete()
        pendingPhoto = ""
        if (captured) {
            text = ""; draftId = UUID.randomUUID().toString()
            notice = "No pude contar bien en esta foto. Escribe el número tú."
        } else notice = "No se guardó ninguna foto ni conteo. Puedes escribir el número tú."
    }
    val parsed = CountRules.parse(text, product.unit.allowsFraction)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("conteo"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(product.name, style = MaterialTheme.typography.headlineLarge)
        Text("SKU ${product.sku} · ${product.category}")
        OutlinedButton(onClick = onEdit, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp).testTag("editar_producto")) {
            Text("Editar producto")
        }
        Panel {
            Text("Sistema: ${stockAmount(product.theoretical, product.unit)}", style = MaterialTheme.typography.titleLarge)
            Text("Último conteo válido: ${stockAmount(latestQuantity, product.unit)}")
            if (latestQuantity == null) Text("Sin confirmar")
            if (history.isNotEmpty() && latestQuantity == null) Text("El registro anterior no es válido para esta unidad. Confirma un nuevo conteo; el anterior se conserva.")
        }
        Text("¿Qué cantidad tienes?", style = MaterialTheme.typography.titleLarge)
        if (notice.isNotBlank()) Text(notice, modifier = Modifier.testTag("aviso_conteo"))
        OutlinedTextField(text, { text = it; notice = "" }, label = { Text("Existencia física (${product.unit.plural})") },
            enabled = !saving && pendingPhoto.isEmpty(), keyboardOptions = KeyboardOptions(keyboardType = if (product.unit.allowsFraction) KeyboardType.Decimal else KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            isError = text.isNotBlank() && parsed == null,
            supportingText = { Text(if (text.isNotBlank() && parsed == null) {
                if (product.unit.allowsFraction) "Escribe una cantidad no negativa con hasta tres decimales."
                else "Esta unidad requiere una cantidad entera no negativa."
            } else "Puedes confirmar cero ${product.unit.plural}.") },
            modifier = Modifier.fillMaxWidth().testTag("cantidad_conteo"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { text = amount((parsed ?: BigDecimal.ZERO).subtract(BigDecimal.ONE).max(BigDecimal.ZERO)) },
                enabled = !saving && pendingPhoto.isEmpty() && parsed != null && parsed > BigDecimal.ZERO,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("restar_conteo")) { Text("− 1 ${product.unit.singular}") }
            OutlinedButton(onClick = { text = amount((parsed ?: BigDecimal.ZERO) + BigDecimal.ONE) },
                enabled = !saving && pendingPhoto.isEmpty() && (text.isBlank() || parsed != null) && (parsed ?: BigDecimal.ZERO) <= BigDecimal("999999998.999"),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("sumar_conteo")) { Text("+ 1 ${product.unit.singular}") }
        }
        OutlinedButton(onClick = {
            keyboard?.hide()
            try {
                directory.mkdirs()
                val file = File.createTempFile("count-", ".jpg", directory)
                pendingPhoto = file.name
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
            } catch (_: Exception) {
                pendingPhoto.takeIf(String::isNotBlank)?.let { File(directory, it).delete() }
                pendingPhoto = ""; notice = "No pudimos abrir la cámara. Escribe el número tú."
            }
        }, enabled = !saving && pendingPhoto.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("contar_foto")) { Text("Contar con foto") }
        Button(onClick = {
            keyboard?.hide()
            onSave(draftId, requireNotNull(parsed)) {
                text = ""; draftId = UUID.randomUUID().toString(); notice = "Conteo confirmado y guardado en este teléfono."
            }
        }, enabled = parsed != null && !saving && pendingPhoto.isEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("confirmar_conteo")) {
            Text(if (parsed == null) "Confirmar conteo" else "Confirmar ${stockAmount(parsed, product.unit)}")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Confirmar agrega un conteo manual al historial. La corrida que ya recibiste conserva sus datos originales.", style = MaterialTheme.typography.bodySmall)
        SectionTitle("Historial de conteos", "Los conteos anteriores se conservan.")
        history.asReversed().forEach { entry ->
            val valid = entry.confirmed && CountRules.valid(entry.physical, product.unit.allowsFraction)
            Panel {
                Text("${stockAmount(entry.physical, product.unit)} · ${if (valid) "Confirmado" else "Registro anterior inválido"}", modifier = Modifier.testTag("conteo_${entry.id}"), style = MaterialTheme.typography.titleLarge)
                Text("${OffsetDateTime.parse(entry.countedAt).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-MX")))} · Manual", style = MaterialTheme.typography.bodySmall)
                Text(if (valid) "Pendiente de enviar" else "Se conserva como antecedente; requiere un nuevo conteo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (product.physical != null) Panel {
            Text("${stockAmount(product.physical, product.unit)} · Conteo recibido")
            Text("${product.countedAt} · ${product.origin}", style = MaterialTheme.typography.bodySmall)
        } else if (history.isEmpty()) Text("Todavía no hay un conteo confirmado.")
    }
}
