package mx.anaquel.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import mx.anaquel.app.domain.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

// Las facturas recibidas en este teléfono (factura_proveedor con sus renglones).
@Composable fun InvoiceList(invoices: List<InvoiceSummary>, onReceive: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("facturas"), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
            if (invoices.isEmpty()) item {
                Text("Todavía no recibes facturas en este teléfono. Al recibir una, cada cantidad se suma a la existencia de su producto.")
            }
            items(invoices, key = { it.id }) { invoice ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("factura_${invoice.id}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(invoice.supplier ?: "Proveedor sin nombre", style = MaterialTheme.typography.titleLarge)
                        Text(listOfNotNull(invoice.folio?.let { "Folio $it" }, invoice.issuedOn?.let { "Emitida el $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        invoice.lines.forEach { line ->
                            Text("${line.sku} · ${stockAmount(line.quantity, line.unit)} × ${money(line.unitCost ?: BigDecimal.ZERO)}")
                        }
                        Text("Total ${money(invoice.total)} · ${if (invoice.photoPath != null) "con foto" else "sin foto"} · Pendiente de enviar",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = onReceive, icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null) }, text = { Text("Recibir factura") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("recibir_factura"))
    }
}

// Recibir una factura: encabezado, foto opcional y renglones emparejados con su producto. Las reglas
// son las de InvoiceRules (las de factura_proveedor y detalle_factura_proveedor en la v2); al guardar,
// cada cantidad se suma a la existencia y el costo vigente del producto no cambia.
@Composable fun InvoiceFormScreen(form: InvoiceForm, products: List<InventoryProduct>, today: LocalDate, saving: Boolean,
    error: String?, onChange: ((InvoiceForm) -> InvoiceForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tried by rememberSaveable(form.key) { mutableStateOf(false) }
    var search by rememberSaveable(form.key) { mutableStateOf("") }
    var notice by rememberSaveable(form.key) { mutableStateOf("") }
    val errors = InvoiceRules.check(form, today)
    fun shown(field: String) = if (tried) errors[field] else null
    val photo = remember(form.key) { File(File(context.filesDir, "facturas"), "${form.key}.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photo.isFile && photo.length() > 0) { onChange { it.copy(photoPath = "facturas/${it.key}.jpg") }; notice = "" }
        else { if (form.photoPath == null) photo.delete(); notice = "No se guardó la foto. La factura se puede guardar sin ella." }
    }
    val matches = remember(search, products) {
        val text = search.trim()
        if (text.isEmpty()) emptyList() else products.filter { it.name.contains(text, true) || it.sku.contains(text, true) }.take(5)
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("factura"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(form.supplier, { value -> onChange { it.copy(supplier = value.take(120)) } }, label = { Text("Proveedor") },
            enabled = !saving, singleLine = true, isError = shown("proveedor") != null, supportingText = shown("proveedor")?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().testTag("factura_proveedor"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(form.folio, { value -> onChange { it.copy(folio = value.take(40)) } }, label = { Text("Folio (opcional)") },
                enabled = !saving, singleLine = true, modifier = Modifier.weight(1f).testTag("factura_folio"))
            OutlinedTextField(form.issuedOn, { value -> onChange { it.copy(issuedOn = value.take(10)) } }, label = { Text("Fecha (AAAA-MM-DD)") },
                enabled = !saving, singleLine = true, isError = shown("fecha") != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).testTag("factura_fecha"))
        }
        shown("fecha")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (form.photoPath == null) OutlinedButton(onClick = {
            keyboard?.hide()
            try {
                photo.parentFile?.mkdirs()
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", photo))
            } catch (_: Exception) { notice = "No pudimos abrir la cámara. La factura se puede guardar sin foto." }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("foto_factura")) { Text("Fotografiar la factura (opcional)") }
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Foto de la factura guardada en este teléfono.", Modifier.weight(1f))
            TextButton(onClick = { photo.delete(); onChange { it.copy(photoPath = null) } }, enabled = !saving) { Text("Quitar foto") }
        }
        if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall)

        SectionTitle("Productos de la factura", "Empareja cada renglón con su producto. Un producto va una sola vez.")
        OutlinedTextField(search, { search = it }, label = { Text("Busca el producto por nombre o SKU") }, enabled = !saving,
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("buscar_producto_factura"))
        matches.forEach { product ->
            val free = InvoiceRules.canAdd(form, product.sku)
            OutlinedCard(onClick = {
                onChange { it.copy(lines = it.lines + InvoiceLineForm(product.sku, product.name, product.unit)) }; search = ""
            }, enabled = free && !saving, modifier = Modifier.fillMaxWidth().testTag("resultado_${product.sku}")) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (free) "SKU ${product.sku} · se cuenta en ${product.unit.plural}" else "SKU ${product.sku} · ya está en la factura",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        shown("renglones")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        form.lines.forEach { line ->
            Panel(Modifier.testTag("renglon_${line.sku}")) {
                Text(line.name, style = MaterialTheme.typography.titleMedium)
                Text("SKU ${line.sku}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(line.printed, { value -> onChange { f -> f.copy(lines = f.lines.map { if (it.sku == line.sku) it.copy(printed = value.take(200)) else it }) } },
                    label = { Text("Como viene en la factura (opcional)") }, enabled = !saving, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("impreso_${line.sku}"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(line.quantity, { value -> onChange { f -> f.copy(lines = f.lines.map { if (it.sku == line.sku) it.copy(quantity = value.take(13)) else it }) } },
                        label = { Text(line.unit.plural.replaceFirstChar(Char::titlecase)) }, enabled = !saving, singleLine = true,
                        isError = shown("cantidad_${line.sku}") != null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (line.unit.allowsFraction) KeyboardType.Decimal else KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("cantidad_${line.sku}"))
                    OutlinedTextField(line.unitCost, { value -> onChange { f -> f.copy(lines = f.lines.map { if (it.sku == line.sku) it.copy(unitCost = value.take(13)) else it }) } },
                        label = { Text("Costo c/u ($)") }, enabled = !saving, singleLine = true,
                        isError = shown("costo_${line.sku}") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("costo_${line.sku}"))
                }
                listOfNotNull(shown("cantidad_${line.sku}"), shown("costo_${line.sku}")).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { onChange { f -> f.copy(lines = f.lines.filterNot { it.sku == line.sku }) } }, enabled = !saving,
                    modifier = Modifier.testTag("quitar_${line.sku}")) { Text("Quitar renglón") }
            }
        }
        if (form.lines.isNotEmpty()) {
            val total = form.lines.fold(BigDecimal.ZERO) { sum, line ->
                val quantity = CountRules.parse(line.quantity, line.unit.allowsFraction) ?: BigDecimal.ZERO
                val cost = DecisionRules.parsePrice(line.unitCost) ?: BigDecimal.ZERO
                sum + quantity.multiply(cost).setScale(2, java.math.RoundingMode.HALF_UP)
            }
            Text("Total de la factura: ${money(total)}", style = MaterialTheme.typography.titleLarge)
        }
        Text("Al guardar, cada cantidad se suma a la existencia de su producto. El costo de la factura queda como histórico: el costo vigente del producto no cambia.",
            style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (tried && errors.isNotEmpty()) Text("Revisa los campos marcados.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { keyboard?.hide(); tried = true; if (errors.isEmpty()) onSave() }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("guardar_factura")) { Text("Guardar factura") }
        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
