package mx.anaquel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.anaquel.app.domain.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

// Las ventas registradas a mano en este teléfono (venta con sus renglones de detalle_venta). Cómo
// cargan las tiendas sus ventas reales (exportación del punto de venta o a mano) sigue pendiente;
// esta pantalla no lo decide.
@Composable fun SaleList(sales: List<SaleSummary>, onRecord: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("ventas"), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
            if (sales.isEmpty()) item {
                Text("Todavía no registras ventas en este teléfono. Al registrar una, cada cantidad se resta de la existencia de su producto.")
            }
            items(sales, key = { it.id }) { sale ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("venta_${sale.id}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(money(sale.total), style = MaterialTheme.typography.titleLarge)
                        val time = runCatching { OffsetDateTime.parse(sale.soldAt).toLocalDateTime().format(SaleRules.FORMAT) }.getOrDefault(sale.soldAt)
                        Text(listOfNotNull(sale.folio?.let { "Ticket $it" }, "Vendida el $time").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        sale.lines.forEach { line ->
                            Text("${line.sku} · ${stockAmount(line.quantity, line.unit)} × ${money(line.unitPrice)} = ${money(line.subtotal)}")
                        }
                        Text("Pendiente de enviar", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = onRecord, icon = { Icon(Icons.Outlined.PointOfSale, null) }, text = { Text("Registrar venta") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("registrar_venta"))
    }
}

// Registrar una venta a mano. Las reglas son las de SaleRules (venta y detalle_venta en la v2, más
// BIT-023): al guardar, cada cantidad se resta de la existencia, y no se vende más de lo que el
// sistema dice que hay.
@Composable fun SaleFormScreen(form: SaleForm, products: List<InventoryProduct>, now: LocalDateTime, saving: Boolean,
    error: String?, onChange: ((SaleForm) -> SaleForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var tried by rememberSaveable(form.key) { mutableStateOf(false) }
    var search by rememberSaveable(form.key) { mutableStateOf("") }
    val errors = SaleRules.check(form, now)
    fun shown(field: String) = if (tried) errors[field] else null
    val matches = remember(search, products) {
        val text = search.trim()
        if (text.isEmpty()) emptyList() else products.filter { it.name.contains(text, true) || it.sku.contains(text, true) }.take(5)
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("venta"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(form.folio, { value -> onChange { it.copy(folio = value.take(40)) } }, label = { Text("Ticket (opcional)") },
                enabled = !saving, singleLine = true, isError = shown("folio") != null, modifier = Modifier.weight(1f).testTag("venta_folio"))
            OutlinedTextField(form.soldAt, { value -> onChange { it.copy(soldAt = value.take(16)) } }, label = { Text("Fecha y hora") },
                enabled = !saving, singleLine = true, isError = shown("fecha") != null, modifier = Modifier.weight(1.4f).testTag("venta_fecha"))
        }
        listOfNotNull(shown("folio"), shown("fecha")).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Si el ticket trae folio, escríbelo: dos ventas con el mismo folio no se registran.", style = MaterialTheme.typography.bodySmall)

        SectionTitle("Productos vendidos", "Un producto va una sola vez.")
        OutlinedTextField(search, { search = it }, label = { Text("Busca el producto por nombre o SKU") }, enabled = !saving,
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("buscar_producto_venta"))
        matches.forEach { product ->
            val free = SaleRules.canAdd(form, product.sku)
            OutlinedCard(onClick = {
                onChange { it.copy(lines = it.lines + SaleLineForm(product.sku, product.name, product.unit, product.theoretical,
                    price = product.price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())) }
                search = ""
            }, enabled = free && !saving, modifier = Modifier.fillMaxWidth().testTag("resultado_venta_${product.sku}")) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (free) "SKU ${product.sku} · el sistema tiene ${stockAmount(product.theoretical, product.unit)}"
                        else "SKU ${product.sku} · ya está en la venta", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        shown("renglones")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        form.lines.forEach { line ->
            Panel(Modifier.testTag("renglon_venta_${line.sku}")) {
                Text(line.name, style = MaterialTheme.typography.titleMedium)
                Text("SKU ${line.sku} · el sistema tiene ${stockAmount(line.systemStock, line.unit)}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(line.quantity, { value -> onChange { f -> f.copy(lines = f.lines.map { if (it.sku == line.sku) it.copy(quantity = value.take(13)) else it }) } },
                        label = { Text(line.unit.plural.replaceFirstChar(Char::titlecase)) }, enabled = !saving, singleLine = true,
                        isError = shown("cantidad_${line.sku}") != null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (line.unit.allowsFraction) KeyboardType.Decimal else KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("cantidad_venta_${line.sku}"))
                    OutlinedTextField(line.price, { value -> onChange { f -> f.copy(lines = f.lines.map { if (it.sku == line.sku) it.copy(price = value.take(13)) else it }) } },
                        label = { Text("Precio c/u ($)") }, enabled = !saving, singleLine = true,
                        isError = shown("precio_${line.sku}") != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("precio_venta_${line.sku}"))
                }
                listOfNotNull(shown("cantidad_${line.sku}"), shown("precio_${line.sku}")).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                val quantity = CountRules.parse(line.quantity, line.unit.allowsFraction)
                val price = DecisionRules.parsePrice(line.price)
                if (quantity != null && price != null) Text("Subtotal ${money(SaleLine(line.sku, quantity, price).subtotal)}")
                TextButton(onClick = { onChange { f -> f.copy(lines = f.lines.filterNot { it.sku == line.sku }) } }, enabled = !saving,
                    modifier = Modifier.testTag("quitar_venta_${line.sku}")) { Text("Quitar renglón") }
            }
        }
        if (form.lines.isNotEmpty()) {
            val total = form.lines.fold(BigDecimal.ZERO) { sum, line ->
                val quantity = CountRules.parse(line.quantity, line.unit.allowsFraction) ?: BigDecimal.ZERO
                val price = DecisionRules.parsePrice(line.price) ?: BigDecimal.ZERO
                sum + SaleLine(line.sku, quantity, price).subtotal
            }
            Text("Total de la venta: ${money(total)}", style = MaterialTheme.typography.titleLarge)
        }
        Text("Al guardar, cada cantidad se resta de la existencia de su producto en el sistema.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (tried && errors.isNotEmpty()) Text("Revisa los campos marcados.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { keyboard?.hide(); tried = true; if (errors.isEmpty()) onSave() }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("guardar_venta")) { Text("Guardar venta") }
        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
