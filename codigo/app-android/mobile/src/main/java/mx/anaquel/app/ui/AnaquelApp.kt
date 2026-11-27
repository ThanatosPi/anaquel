package mx.anaquel.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import mx.anaquel.app.AnaquelViewModel
import mx.anaquel.app.BusinessViewModel
import mx.anaquel.app.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AnaquelApp(vm: AnaquelViewModel = viewModel(), businessVm: BusinessViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val business by businessVm.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("inicio") }
    var runIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentId by rememberSaveable { mutableLongStateOf(-1) }
    var currentSku by rememberSaveable { mutableStateOf("") }
    var editSku by rememberSaveable { mutableStateOf("") }   // vacío = producto nuevo
    var inventorySegment by rememberSaveable { mutableStateOf("productos") }   // productos · facturas · ventas
    var filterName by rememberSaveable { mutableStateOf("") }
    var marketOnly by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var targetIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var runMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val run = state.runs.getOrNull(runIndex)
    val rec = run?.recommendations?.find { it.id == currentId }
    val decisions = state.decisions.filter { it.runId == run?.id }
    val local = decisions.find { it.recommendationId == currentId }
    val latestState by rememberUpdatedState(state)
    val latestRun by rememberUpdatedState(run)
    val latestScreen by rememberUpdatedState(screen)
    val latestId by rememberUpdatedState(currentId)
    fun open(item: Recommendation) { currentId = item.id; screen = "detalle" }
    fun back() {
        dialog = ""
        if (screen == "factura") businessVm.discardInvoice()
        if (screen == "venta") businessVm.discardSale()
        screen = when (screen) { "precio" -> "detalle"; "detalle" -> "revisar"; "conteo" -> "inventario"; "zona" -> "ajustes"
            "producto" -> if (editSku.isNotEmpty() && business.catalog?.products?.any { it.sku == editSku } == true) "conteo" else "inventario"
            "factura", "venta" -> "inventario"; "catalogo" -> "ajustes"
            else -> "inicio" }
        selectedIds = longArrayOf()
    }
    BackHandler(enabled = screen != "inicio" || selectedIds.isNotEmpty()) {
        if (!business.saving) { if (selectedIds.isNotEmpty()) selectedIds = longArrayOf() else back() }
    }

    if (business.error != null || business.loading || !business.storageReady) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Anaquel", style = MaterialTheme.typography.headlineLarge)
            if (business.error != null) {
                Text(business.error.orEmpty()); Button(onClick = businessVm::load) { Text("Reintentar") }
            } else { CircularProgressIndicator(); Text("Cargando los datos del negocio…") }
        }
        return
    }
    if (business.preferences.location == null || screen == "zona") {
        LocationSetup(business, onAskPermission = businessVm::askLocationPermission,
            onSave = { location -> businessVm.saveLocation(location) { screen = if (screen == "zona") "ajustes" else "inicio" } },
            onCancel = if (business.preferences.location != null) { { screen = "ajustes" } } else null)
        return
    }

    LaunchedEffect(vm) {
        vm.events.collectLatest { event ->
            if (event.keys.isNotEmpty()) {
                dialog = ""; selectedIds = longArrayOf()
                if (latestScreen == "detalle" || latestScreen == "precio") {
                    val activeRun = latestRun
                    val items = activeRun?.recommendations.orEmpty()
                    val after = items.indexOfFirst { it.id == latestId } + 1
                    val ordered = items.drop(after) + items.take(after)
                    val next = ordered.firstOrNull { item -> item.actionable && item.id !in event.decidedIds &&
                        latestState.decisions.none { it.runId == activeRun?.id && it.recommendationId == item.id } }
                    if (next == null) screen = "resumen" else { currentId = next.id; screen = "detalle" }
                }
            }
            val result = withTimeoutOrNull(6000) {
                snackbar.showSnackbar(event.message, actionLabel = if (event.keys.isNotEmpty()) "Deshacer" else null,
                    withDismissAction = true, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) vm.undo(event.keys)
        }
    }

    val pendingCount = run?.recommendations?.count { rec -> rec.actionable && decisions.none { it.recommendationId == rec.id } } ?: 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { if (screen == "inicio") Wordmark() else Text(when (screen) {
                    "revisar" -> "Revisar"; "detalle" -> "Recomendación"; "precio" -> "Autorizar precio"; "resumen" -> "Autorizaciones"
                    "inventario" -> "Inventario"; "conteo" -> "Confirmar conteo"; "ajustes" -> "Ajustes"
                    "producto" -> if (editSku.isEmpty()) "Nuevo producto" else "Editar producto"; "factura" -> "Recibir factura"; "venta" -> "Registrar venta"; "catalogo" -> "Catálogo"
                    else -> "Anaquel"
                }, style = MaterialTheme.typography.titleLarge)
                    if (screen in listOf("inicio", "revisar", "resumen")) run?.let {
                        Text("Corrida del ${dateLabel(it.started.toLocalDate())}", style = MaterialTheme.typography.bodySmall)
                    }
                } },
                navigationIcon = {
                    if (screen in listOf("detalle", "precio", "conteo", "producto", "factura", "venta", "catalogo")) IconButton(onClick = ::back, enabled = !business.saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (screen in listOf("inicio", "revisar", "resumen")) Box {
                        IconButton(onClick = { runMenu = true }, enabled = !state.busy) { Icon(Icons.Outlined.MoreVert, "Elegir corrida") }
                        DropdownMenu(runMenu, { runMenu = false }) {
                            state.runs.forEachIndexed { index, item -> DropdownMenuItem(text = { Text("Corrida del ${dateLabel(item.started.toLocalDate())}") }, onClick = {
                                runIndex = index; runMenu = false; currentId = -1; selectedIds = longArrayOf(); marketOnly = false; filterName = ""; screen = "inicio"
                            }) }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            when {
                screen in listOf("inicio", "revisar", "resumen", "inventario", "ajustes") && selectedIds.isEmpty() -> NavigationBar {
                    listOf(Triple("inicio", "Inicio", Icons.Outlined.Home), Triple("revisar", "Revisar", Icons.Outlined.Checklist),
                        Triple("inventario", "Inventario", Icons.Outlined.Inventory2), Triple("ajustes", "Ajustes", Icons.Outlined.Settings)).forEach { (route, label, icon) ->
                        NavigationBarItem(screen == route, { screen = route; if (route == "revisar") { filterName = ""; marketOnly = false } },
                            icon = { if (route == "revisar" && pendingCount > 0) BadgedBox(badge = { Badge(containerColor = decisionRed(), contentColor = Color.White) { Text("$pendingCount") } }) { Icon(icon, null) } else Icon(icon, null) },
                            label = { Text(label) }, modifier = Modifier.testTag("nav_$route"))
                    }
                }
                screen == "revisar" && selectedIds.isNotEmpty() -> Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selectedIds.size} seleccionadas", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { targetIds = selectedIds; dialog = "aprobar" }, enabled = !state.busy && state.storageReady,
                            colors = authorizationButtonColors(run?.recommendations?.firstOrNull { it.id in selectedIds }?.output ?: Output.LIQUIDAR),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Autorizar seleccionadas") }
                        OutlinedButton(onClick = { targetIds = selectedIds; dialog = "rechazar" }, modifier = Modifier.fillMaxWidth()) { Text("Rechazar seleccionadas") }
                        TextButton(onClick = { selectedIds = longArrayOf() }) { Text("Cancelar selección") }
                    }
                }
                screen == "detalle" && rec?.actionable == true && local == null -> Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (rec.output == Output.LIQUIDAR) { priceText = rec.quote!!.proposed.toPlainString(); screen = "precio" }
                            else { targetIds = longArrayOf(rec.id); dialog = "aprobar" }
                        }, enabled = !state.busy && state.storageReady, colors = authorizationButtonColors(rec.output),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("autorizar_detalle")) {
                            Text(if (rec.output == Output.LIQUIDAR) "Revisar precio y autorizar" else "Autorizar")
                        }
                        OutlinedButton(onClick = { targetIds = longArrayOf(rec.id); dialog = "rechazar" }, enabled = !state.busy && state.storageReady,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rechazar_detalle")) { Text("Rechazar") }
                    }
                }
                screen == "precio" && rec?.quote != null -> Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mínimo ${money(rec.quote.minimum)} · Original ${money(rec.quote.original)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
                        Button(onClick = { targetIds = longArrayOf(rec.id); dialog = "aprobar" },
                            colors = authorizationButtonColors(rec.output),
                            enabled = !state.busy && state.storageReady && rec.actionable && local == null && DecisionRules.validPrice(DecisionRules.parsePrice(priceText), rec.quote),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("autorizar_precio")) {
                            Text(if (rec.marketWarning) "Autorizar bajo mi responsabilidad" else "Autorizar este precio")
                        }
                        TextButton(onClick = { screen = "detalle" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    }
                }
            }
        }
    ) { padding ->
        // consumeWindowInsets: sin esto, imePadding() de Inventario sumaba el teclado encima del
        // relleno de la barra inferior y a 200 % la lista quedaba con 0 px (diagnóstico del 6/oct).
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                screen == "inventario" -> Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        listOf("productos" to "Productos", "facturas" to "Facturas", "ventas" to "Ventas").forEachIndexed { index, (key, label) ->
                            SegmentedButton(inventorySegment == key, { inventorySegment = key }, SegmentedButtonDefaults.itemShape(index, 3),
                                modifier = Modifier.testTag("segmento_$key")) { Text(label) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        if (inventorySegment == "facturas") InvoiceList(business.invoices) { businessVm.startInvoice(); screen = "factura" }
                        else if (inventorySegment == "ventas") SaleList(business.sales) { businessVm.startSale(); screen = "venta" }
                        else business.catalog?.let { InventoryScreen(it, business.preferences.counts,
                            onOpen = { sku -> currentSku = sku; screen = "conteo" },
                            onAdd = { editSku = ""; businessVm.openProduct(null) { screen = "producto" } },
                            onEdit = { sku -> editSku = sku; currentSku = sku; businessVm.openProduct(sku) { screen = "producto" } }) }
                    }
                }
                screen == "venta" -> {
                    val form = business.saleForm
                    val catalog = business.catalog
                    if (form == null || catalog == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else SaleFormScreen(form, catalog.products, java.time.LocalDateTime.now(), business.saving, business.formError,
                        onChange = businessVm::editSale,
                        onSave = { businessVm.recordSale {
                            inventorySegment = "ventas"; screen = "inventario"
                            scope.launch { snackbar.showSnackbar("Venta registrada: se restó de la existencia.") }
                        } }, onCancel = ::back)
                }
                screen == "factura" -> {
                    val form = business.invoiceForm
                    val catalog = business.catalog
                    if (form == null || catalog == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else InvoiceFormScreen(form, catalog.products, java.time.LocalDate.now(), business.saving, business.formError,
                        onChange = businessVm::editInvoice,
                        onSave = { businessVm.receiveInvoice {
                            val lines = form.lines.size
                            inventorySegment = "facturas"; screen = "inventario"
                            scope.launch { snackbar.showSnackbar(if (lines == 1) "Factura guardada: se sumó 1 renglón a la existencia."
                                else "Factura guardada: se sumaron $lines renglones a la existencia.") }
                        } }, onCancel = ::back)
                }
                screen == "producto" -> {
                    val catalog = business.catalog
                    val form = business.productForm
                    val zone = business.preferences.location?.zone
                    if (catalog == null || form == null || zone == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else {
                        val current = (catalog.products + catalog.discontinued).find { it.sku == editSku }
                        ProductFormScreen(form, business.categories, business.seasons, zone,
                            current?.let { stockAmount(it.theoretical, it.unit) },
                            (catalog.products + catalog.discontinued).map { it.sku }.toSet(), business.saving, business.formError,
                            // A dónde volver lo decide lo que se acaba de guardar, no el catálogo: éste todavía
                            // no se actualiza, y un producto recién descontinuado dejaba la pantalla en blanco.
                            onSave = { saved -> businessVm.saveProduct(saved) {
                                screen = if (editSku.isNotEmpty() && saved.active) "conteo" else "inventario"
                                scope.launch { snackbar.showSnackbar(if (editSku.isEmpty()) "Producto agregado." else "Producto guardado.") }
                            } }, onCancel = ::back)
                    }
                }
                screen == "ajustes" -> SettingsScreen(business, onEdit = { screen = "zona" }, onCatalog = { businessVm.clearFormError(); screen = "catalogo" })
                screen == "catalogo" -> business.preferences.location?.let { location ->
                    CatalogScreen(business.categories, business.seasons, location.zone, business.saving, business.formError,
                        onSaveCategory = { id, name, affinity, done -> businessVm.saveCategory(id, name, affinity, done) },
                        onSaveSeason = { id, name, sd, sm, ed, em, done -> businessVm.saveSeason(id, name, sd, sm, ed, em, done) },
                        onDialogClosed = businessVm::clearFormError)
                }
                screen == "conteo" -> business.catalog?.products?.find { it.sku == currentSku }?.let { product ->
                    InventoryDetail(product, business.preferences.counts, business.saving, business.formError,
                        onEdit = { editSku = product.sku; businessVm.openProduct(product.sku) { screen = "producto" } }) { id, quantity, onSaved ->
                        businessVm.saveCount(id, product.sku, quantity, onSaved)
                    }
                } ?: LaunchedEffect(currentSku) { screen = "inventario" }   // ya no está activo: de vuelta al inventario
                state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Trayendo la corrida de anoche…") }
                state.error != null -> Column(Modifier.align(Alignment.Center).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.error.orEmpty()); Button(onClick = vm::loadRuns) { Text("Reintentar") }
                }
                run == null || run.recommendations.isEmpty() -> Column(Modifier.padding(20.dp)) {
                    Text("Todavía no hay corrida", style = MaterialTheme.typography.headlineLarge)
                    Text("El análisis corre de madrugada. La primera corrida aparece al día siguiente.")
                }
                else -> {
                    val off = animationsOff()
                    // La pantalla saliente conserva su recomendación durante la transición.
                    AnimatedContent(targetState = screen to rec, transitionSpec = {
                        if (off) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        else {
                            val sign = if (routeDepth(targetState.first) >= routeDepth(initialState.first)) 1 else -1
                            (fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                                slideInHorizontally(tween(220, easing = LinearOutSlowInEasing)) { sign * it / 12 }) togetherWith
                                fadeOut(tween(120))
                        }
                    }, label = "pantalla") { (route, routeRec) ->
                        when (route) {
                            "inicio" -> HomeScreen(run, decisions, onReview = { output, market -> filterName = output?.name.orEmpty(); marketOnly = market; screen = "revisar" }, onSummary = { screen = "resumen" },
                                unconfirmed = business.catalog?.products?.count { product -> product.confirmedQuantity(business.preferences.counts) == null } ?: 0,
                                onInventory = { screen = "inventario" }, onOpen = ::open)
                            "revisar" -> ReviewScreen(run, decisions, Output.entries.find { it.name == filterName }, marketOnly,
                                onFilter = { filterName = it?.name.orEmpty(); selectedIds = longArrayOf() }, clearMarket = { marketOnly = false }, onOpen = ::open,
                                selected = selectedIds.toSet(), onSelect = { item ->
                                    val first = run.recommendations.find { it.id == selectedIds.firstOrNull() }
                                    if (item.actionable && decisions.none { it.recommendationId == item.id } && (first == null || first.output == item.output)) {
                                        selectedIds = if (item.id in selectedIds) selectedIds.filterNot { it == item.id }.toLongArray() else selectedIds + item.id
                                    } else scope.launch { snackbar.showSnackbar("Selecciona propuestas pendientes del mismo tipo.") }
                                })
                            "resumen" -> SummaryScreen(run, decisions, onUndo = { vm.undo(setOf(it)) }, onOpen = ::open)
                            "detalle" -> routeRec?.let { item -> DetailScreen(run, item, decisions.find { it.recommendationId == item.id }, "${run.id}:${item.id}" in state.seen) { vm.markSeen(run.id, item.id) } }
                            "precio" -> routeRec?.let { PriceScreen(it, priceText) { value -> priceText = value } }
                        }
                    }
                }
            }
        }
    }
    if (dialog.isNotEmpty() && run != null) {
        val targets = run.recommendations.filter { it.id in targetIds }
        if (targets.isNotEmpty()) {
            if (dialog == "aprobar") ApproveDialog(targets,
                if (screen == "precio") DecisionRules.parsePrice(priceText) else targets.first().quote?.proposed,
                state.busy, onCancel = { dialog = "" }, onConfirm = { risk ->
                    vm.decide(run.id, targetIds.toSet(), "APROBADA", if (screen == "precio") DecisionRules.parsePrice(priceText) else null, null, risk)
                })
            else RejectDialog(state.busy, onCancel = { dialog = "" }, onConfirm = { reason ->
                vm.decide(run.id, targetIds.toSet(), "RECHAZADA", null, reason, false)
            })
        }
    }
}

private fun routeDepth(route: String) = when (route) { "inicio" -> 0; "detalle" -> 2; "precio" -> 3; else -> 1 }
