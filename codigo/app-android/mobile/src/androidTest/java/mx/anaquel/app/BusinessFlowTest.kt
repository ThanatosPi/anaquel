package mx.anaquel.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import mx.anaquel.app.data.*
import mx.anaquel.app.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class BusinessFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = BusinessRepository(context)

    // Desde 0.4.0 el negocio vive en la base local: se vacía y se vuelve a sembrar, como recién instalada.
    @Before fun resetBusiness() {
        runBlocking { context.decisionStore.edit { it.clear() }; LocalSetup.resetForTests(context) }
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("zona_manual").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val suffix = if (context.resources.configuration.fontScale >= 1.9f) "-200" else ""
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(350, 5_000)
        listOf("mkdir -p /sdcard/Download/anaquel-msg015",
            "screencap -p /sdcard/Download/anaquel-msg015/$name$suffix.png").forEach { command ->
            automation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).use { stream ->
                check(stream.readBytes().isEmpty())
            } }
        }
    }

    private fun manualSetup() {
        ui.onNodeWithTag("zona_manual").performScrollTo().performClick()
        ui.onNodeWithTag("guardar_zona").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("entidad_Yucatán").performScrollTo().performClick()
        ui.onNodeWithTag("municipio_1").performScrollTo().performClick()
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(350, 5_000)
        ui.onNodeWithTag("guardar_zona").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Revisar recomendaciones").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun manualOnboardingSettingsChangeAndCancelPreserveExplicitChoice() {
        capture("01-ubicacion")
        manualSetup()
        ui.onNodeWithTag("nav_ajustes").performClick()
        ui.onNodeWithText("Origen: lo elegiste tú").assertExists()
        ui.onNodeWithTag("editar_zona").performScrollTo().performClick()
        ui.onNodeWithTag("zona_manual").performScrollTo().performClick()
        ui.onNodeWithTag("entidad_Estado de México").performScrollTo().performClick()
        ui.onNodeWithTag("municipio_2").performScrollTo().performClick()
        ui.onNodeWithText("Cancelar").performScrollTo().performClick()
        assertEquals(ClimateZone.TROPICAL, runBlocking { repository.preferences.first().location!!.zone })
        ui.onNodeWithTag("editar_zona").performScrollTo().performClick()
        ui.onNodeWithTag("zona_manual").performScrollTo().performClick()
        ui.onNodeWithTag("entidad_Estado de México").performScrollTo().performClick()
        ui.onNodeWithTag("guardar_zona").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("municipio_2").performScrollTo().performClick()
        capture("02-selector-manual")
        ui.onNodeWithTag("guardar_zona").performScrollTo().performClick()
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().location?.zone == ClimateZone.FRIO } }
        assertEquals(2L, runBlocking { repository.preferences.first().location!!.municipalityId })
        ui.onNodeWithText("Toluca, Estado de México").assertExists()
        capture("03-ajustes")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("ajustes").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Zona climática: Frío").assertExists()
    }

    @Test fun pieceFractionsAreBlockedAndIntegerCountsKeepHistoryAcrossRecreation() {
        manualSetup()
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("buscar_inventario").performTextInput("SIN-001")
        ui.onNodeWithText("1 de 60 productos").assertExists()
        capture("04-inventario")
        ui.onNodeWithTag("producto_SIN-001").performClick()
        ui.onNodeWithText("Sin confirmar").assertExists()
        ui.onNodeWithTag("autorizar_detalle").assertDoesNotExist()
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("-1")
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsNotEnabled()
        capture("05-cantidad-invalida")
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("2.125")
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Esta unidad requiere una cantidad entera no negativa.").assertExists()
        capture("07-fraccion-piezas")
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("0")
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().counts.size == 1 } }
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("2")
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().performClick()
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().counts.size == 2 } }
        ui.onNodeWithText("0 piezas · Confirmado").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("2 piezas · Confirmado").assertExists()
        capture("06-historial")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("conteo").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(BigDecimal.ZERO, BigDecimal("2")), runBlocking { repository.preferences.first().counts.map { it.physical } })
    }

    @Test fun repeatedDraftIsAtomicAndDoesNotEraseDecisionsOrPreviousCounts() = runBlocking {
        val run = context.assets.open("corrida.json").bufferedReader().use { RunParser.parse(it.readText()) }
        DecisionRepository(context).decide(run, setOf(4), "APROBADA", BigDecimal("85.50"))
        coroutineScope { (1..8).map { async { repository.count("draft-one", "CAB-205", BigDecimal("2.5")) } }.awaitAll() }
        assertEquals(1, repository.preferences.first().counts.size)
        repository.count("draft-two", "SIN-001", BigDecimal.ZERO)
        assertEquals(2, repository.preferences.first().counts.size)
        assertTrue(repository.preferences.first().counts.all { it.confirmed && it.origin == "MANUAL" })
        assertEquals(1, DecisionRepository(context).decisions.first().size)
        try { repository.count("draft-one", "CAB-205", BigDecimal.ONE); fail("Cambió el mismo borrador") }
        catch (_: IllegalArgumentException) { }
        try { repository.count("invalid", "UNKNOWN", BigDecimal.ONE); fail("Aceptó SKU inexistente") }
        catch (_: IllegalArgumentException) { }
        try { repository.count("fraction-piece", "SIN-001", BigDecimal("2.125")); fail("Aceptó fracciones de pieza") }
        catch (_: IllegalArgumentException) { }
        assertEquals(2, repository.preferences.first().counts.size)
    }

    @Test fun meterFractionsShowTheirUnitAndPersist() {
        manualSetup()
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("buscar_inventario").performTextInput("CAB-205")
        ui.onNodeWithTag("producto_CAB-205").performClick()
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("12,5")
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsEnabled()
        ui.onNodeWithText("Confirmar 12.5 metros").assertExists()
        capture("08-confirmar-metros")
        ui.onNodeWithTag("confirmar_conteo").performClick()
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().counts.size == 1 } }
        ui.onNodeWithText("12.5 metros · Confirmado").performScrollTo().assertIsDisplayed()
        capture("09-historial-metros")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("conteo").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(BigDecimal("12.5"), runBlocking { repository.preferences.first().counts.single().physical })
    }

    @Test fun savedGpsSurvivesRecreationAndManualSettingsEraseCoordinates() {
        val gps = BusinessLocation(ClimateZone.TROPICAL, "Mérida", "Yucatán", "GPS", "2026-09-23T14:00:00-06:00", 20.9672, -89.6247, 1)
        runBlocking { repository.saveLocation(gps) }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("nav_ajustes").fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("nav_ajustes").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(gps.copy(savedAt = ""), runBlocking { repository.preferences.first().location })
        ui.onNodeWithTag("nav_ajustes").performClick()
        ui.onNodeWithText("Origen: ubicación del teléfono").assertExists()
        capture("10-gps-guardado")
        ui.onNodeWithTag("editar_zona").performScrollTo().performClick()
        ui.onNodeWithTag("zona_manual").performScrollTo().performClick()
        ui.onNodeWithTag("entidad_Estado de México").performScrollTo().performClick()
        ui.onNodeWithTag("municipio_2").performScrollTo().performClick()
        ui.onNodeWithTag("guardar_zona").performScrollTo().performClick()
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().location?.origin == "MANUAL" } }
        val manual = runBlocking { repository.preferences.first().location!! }
        assertNull(manual.latitude); assertNull(manual.longitude)
        assertEquals("Toluca", manual.municipality)
        ui.onNodeWithText("No hay coordenadas guardadas.").assertExists()
        capture("11-manual-sin-coordenadas")
        ui.activityRule.scenario.recreate()
        assertNull(runBlocking { repository.preferences.first().location!!.latitude })
    }

    @Test fun legacyGpsAndInvalidPieceCountRequireCorrectionWithoutErasingHistory() {
        val count = StockCount("legacy-count", "SIN-001", BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:00:00-06:00")
        runBlocking { context.decisionStore.edit {
            it[stringPreferencesKey("negocio_ubicacion_v1")] = """{"zona":"TROPICAL","municipio":"Mérida","estado":"Yucatán","origen":"GPS","guardada_en":"2026-09-23T14:00:00-06:00"}"""
            it[stringPreferencesKey("conteos_locales_v1")] = BusinessRepository.encodeCounts(listOf(count))
        }; LocalSetup.migrateNow(context) }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Confirma de nuevo la ubicación del negocio: el registro anterior no guardó las coordenadas. Tus conteos y decisiones se conservan.").fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { repository.preferences.first().location })
        manualSetup()
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("filtro_sin_confirmar").performClick()
        ui.onNodeWithTag("producto_SIN-001").performClick()
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("2.125 piezas · Registro anterior inválido").performScrollTo().assertIsDisplayed()
        capture("12-historial-anterior")
        ui.onNodeWithTag("cantidad_conteo").performScrollTo().performTextReplacement("3")
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(350, 5_000)
        ui.onNodeWithTag("confirmar_conteo").performScrollTo().assertIsEnabled().performClick()
        capture("12-conteo-corregido")
        ui.waitUntil(5_000) { runBlocking { repository.preferences.first().counts.size == 2 } }
        assertEquals(count, runBlocking { repository.preferences.first().counts.first().copy(systemAtCount = null) })
        ui.onNodeWithText("3 piezas · Confirmado").performScrollTo().assertIsDisplayed()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithTag("nav_ajustes").performClick()
        ui.onNodeWithText("2 registros en el historial local").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("corrección de cantidad", substring = true).assertDoesNotExist()
        capture("16-ajustes-conteo-corregido")
    }

    @Test fun settingsUseSingularForOneHistoryRecord() {
        manualSetup()
        val count = StockCount("legacy-count", "SIN-001", BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:00:00-06:00")
        runBlocking { context.decisionStore.edit {
            it[stringPreferencesKey("conteos_locales_v1")] = BusinessRepository.encodeCounts(listOf(count))
        }; LocalSetup.migrateNow(context) }
        ui.onNodeWithTag("nav_ajustes").performClick()
        ui.onNodeWithText("1 registro en el historial local").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("1 producto requiere corrección de cantidad. Los registros anteriores se conservan en el historial.").performScrollTo().assertIsDisplayed()
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        ui.onNodeWithText("Anaquel · Demostración $version").performScrollTo().assertIsDisplayed()
        capture("17-ajustes-un-pendiente")
    }

    // L3 · Productos: alta con las reglas de producto (v2), edición sin tocar la existencia, y baja
    // como DESCONTINUADO, que no borra el renglón.
    @Test fun productIsAddedEditedAndDiscontinuedWithoutDeleting() {
        manualSetup()
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("agregar_producto").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("producto").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("guardar_producto").performScrollTo().performClick()
        ui.onNodeWithText("Revisa los campos marcados.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("producto_sku").performScrollTo().performTextReplacement("lla-014")
        ui.onNodeWithTag("producto_nombre").performScrollTo().performTextReplacement("Tornillo de prueba")
        ui.onNodeWithTag("categoria_1").performScrollTo().performClick()
        ui.onNodeWithTag("producto_precio").performScrollTo().performTextReplacement("12.50")
        ui.onNodeWithTag("producto_existencia").performScrollTo().performTextReplacement("15")
        ui.onNodeWithText("Ya hay un producto con ese código.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("producto_sku").performScrollTo().performTextReplacement("tor-900")
        capture("20-producto-nuevo")
        ui.onNodeWithTag("guardar_producto").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("inventario").fetchSemanticsNodes().isNotEmpty() }
        val added = runBlocking { repository.catalog.first().products.single { it.sku == "TOR-900" } }
        assertEquals(0, added.theoretical.compareTo(BigDecimal("15")))
        ui.onNodeWithTag("buscar_inventario").performTextInput("TOR-900")
        ui.onNodeWithText("1 de 61 productos").assertExists()
        ui.onNodeWithTag("producto_TOR-900").performClick()
        ui.onNodeWithTag("editar_producto").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("producto").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("producto_sku").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("producto_existencia").assertDoesNotExist()
        ui.onNodeWithTag("producto_nombre").performScrollTo().performTextReplacement("Tornillo editado")
        ui.onNodeWithTag("producto_activo").performScrollTo().performClick()
        capture("21-producto-editar")
        ui.onNodeWithTag("guardar_producto").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("filtro_descontinuados").fetchSemanticsNodes().isNotEmpty() }
        val catalog = runBlocking { repository.catalog.first() }
        assertEquals(60, catalog.products.size)
        assertEquals("Tornillo editado", catalog.discontinued.single { it.sku == "TOR-900" }.name)
        assertEquals(0, catalog.discontinued.single().theoretical.compareTo(BigDecimal("15")))
        ui.onNodeWithTag("filtro_descontinuados").performClick()
        ui.onNodeWithText("1 de 1 descontinuados").assertExists()
        ui.onNodeWithTag("descontinuado_TOR-900").assertIsDisplayed()
        capture("22-descontinuados")
    }

    // L3 · Recibir factura: un renglón por producto, cantidades según la unidad, y al guardar cada
    // cantidad se suma a la existencia del sistema; el costo vigente del producto no cambia.
    @Test fun invoiceReceptionAddsEachLineToStockOnce() {
        manualSetup()
        val before = runBlocking { repository.catalog.first().products.associate { it.sku to it.theoretical } }
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("segmento_facturas").performClick()
        ui.onNodeWithTag("recibir_factura").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("factura").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("guardar_factura").performScrollTo().performClick()
        ui.onNodeWithText("Agrega al menos un producto de la factura.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("factura_proveedor").performScrollTo().performTextReplacement("Distribuidora del Sur")
        ui.onNodeWithTag("factura_folio").performScrollTo().performTextReplacement("F-100")
        ui.onNodeWithTag("buscar_producto_factura").performScrollTo().performTextReplacement("LLA-014")
        ui.onNodeWithTag("resultado_LLA-014").performScrollTo().performClick()
        ui.onNodeWithTag("buscar_producto_factura").performScrollTo().performTextReplacement("LLA-014")
        ui.onNodeWithTag("resultado_LLA-014").performScrollTo().assertIsNotEnabled()   // un producto va una sola vez
        ui.onNodeWithTag("buscar_producto_factura").performScrollTo().performTextReplacement("CAB-205")
        ui.onNodeWithTag("resultado_CAB-205").performScrollTo().performClick()
        ui.onNodeWithTag("cantidad_LLA-014").performScrollTo().performTextReplacement("2.5")
        ui.onNodeWithTag("costo_LLA-014").performScrollTo().performTextReplacement("380")
        ui.onNodeWithTag("cantidad_CAB-205").performScrollTo().performTextReplacement("12,5")
        ui.onNodeWithTag("costo_CAB-205").performScrollTo().performTextReplacement("14")
        ui.onNodeWithTag("guardar_factura").performScrollTo().performClick()
        ui.onNodeWithText("Cantidad entera mayor que cero (piezas).").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("cantidad_LLA-014").performScrollTo().performTextReplacement("3")
        capture("23-factura")
        ui.onNodeWithTag("guardar_factura").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("facturas").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Distribuidora del Sur").assertExists()
        ui.onNodeWithText("Total $1,315.00", substring = true).assertExists()
        capture("24-facturas")
        val after = runBlocking { repository.catalog.first().products.associate { it.sku to it.theoretical } }
        assertEquals(0, after.getValue("LLA-014").compareTo(before.getValue("LLA-014") + BigDecimal("3")))
        assertEquals(0, after.getValue("CAB-205").compareTo(before.getValue("CAB-205") + BigDecimal("12.5")))
        assertEquals(1, runBlocking { repository.invoices.first().size })
    }

    // L3 · Categorías y temporadas: nombre único, afinidad con la zona del negocio y BIT-015.
    @Test fun categoriesAndSeasonsAreAddedFromSettings() {
        manualSetup()
        ui.onNodeWithTag("nav_ajustes").performClick()
        ui.onNodeWithTag("abrir_catalogo").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("catalogo").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("agregar_categoria").performScrollTo().performClick()
        ui.onNodeWithTag("categoria_nombre").performTextReplacement("pintura")
        ui.onNodeWithTag("guardar_categoria").performClick()
        ui.onNodeWithText("Ya hay una categoría con ese nombre.").assertIsDisplayed()
        ui.onNodeWithTag("categoria_nombre").performTextReplacement("Plomería")
        ui.onNodeWithTag("categoria_afinidad_ALTA").performClick()
        capture("25-categoria")
        ui.onNodeWithTag("guardar_categoria").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Plomería").fetchSemanticsNodes().isNotEmpty() }
        // Jardinería ya tiene «Afinidad alta con la zona tropical»: se comprueba el renglón guardado.
        val plomeria = runBlocking { repository.categories.first().single { it.name == "Plomería" } }
        assertEquals("ALTA", plomeria.affinity); assertEquals("TROPICAL", plomeria.affinityZone)
        ui.onNodeWithTag("categoria_fila_${plomeria.id}").assertTextContains("Afinidad alta con la zona tropical", substring = true)
        ui.onNodeWithTag("agregar_temporada").performScrollTo().performClick()
        ui.onNodeWithTag("temporada_nombre").performTextReplacement("Regreso a clases")
        ui.onNodeWithTag("temporada_dia_inicio").performTextReplacement("31")
        ui.onNodeWithTag("temporada_mes_inicio").performTextReplacement("2")
        ui.onNodeWithTag("temporada_dia_fin").performTextReplacement("31")
        ui.onNodeWithTag("temporada_mes_fin").performTextReplacement("8")
        ui.onNodeWithTag("guardar_temporada").performClick()
        ui.onNodeWithText("Ese día no existe en feb (hasta 29).").assertIsDisplayed()
        ui.onNodeWithTag("temporada_dia_inicio").performTextReplacement("15")
        ui.onNodeWithTag("temporada_mes_inicio").performTextReplacement("7")
        ui.onNodeWithTag("temporada_vista").assertTextEquals("Regreso a clases · 15 jul – 31 ago")
        capture("26-temporada")
        ui.onNodeWithTag("guardar_temporada").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Regreso a clases · 15 jul – 31 ago").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(7, runBlocking { repository.categories.first().size })
        assertEquals(4, runBlocking { repository.seasons.first().size })
        capture("27-catalogo")
    }

    // L3 · Registrar venta: un renglón por producto, cantidades según la unidad, no más de lo que el
    // sistema dice que hay (BIT-023), y al guardar cada cantidad se resta de la existencia.
    @Test fun manualSaleSubtractsFromStockAndRefusesMoreThanTheSystemHas() {
        manualSetup()
        val before = runBlocking { repository.catalog.first().products.associate { it.sku to it.theoretical } }
        ui.onNodeWithTag("nav_inventario").performClick()
        ui.onNodeWithTag("segmento_ventas").performClick()
        ui.onNodeWithTag("registrar_venta").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("venta").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("guardar_venta").performScrollTo().performClick()
        ui.onNodeWithText("Agrega al menos un producto vendido.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("venta_folio").performScrollTo().performTextReplacement("T-001")
        ui.onNodeWithTag("buscar_producto_venta").performScrollTo().performTextReplacement("LLA-014")
        ui.onNodeWithTag("resultado_venta_LLA-014").performScrollTo().performClick()
        val llave = before.getValue("LLA-014")
        ui.onNodeWithTag("cantidad_venta_LLA-014").performScrollTo().performTextReplacement((llave + BigDecimal.ONE).toPlainString())
        ui.onNodeWithTag("guardar_venta").performScrollTo().performClick()
        ui.onNodeWithText(SaleRules.stockMessage(llave, MeasureUnit.PIEZA)).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("cantidad_venta_LLA-014").performScrollTo().performTextReplacement("2")
        ui.onNodeWithTag("buscar_producto_venta").performScrollTo().performTextReplacement("CAB-205")
        ui.onNodeWithTag("resultado_venta_CAB-205").performScrollTo().performClick()
        ui.onNodeWithTag("cantidad_venta_CAB-205").performScrollTo().performTextReplacement("3,5")
        capture("28-venta")
        ui.onNodeWithTag("guardar_venta").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("ventas").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Ticket T-001", substring = true).assertExists()
        capture("29-ventas")
        val after = runBlocking { repository.catalog.first().products.associate { it.sku to it.theoretical } }
        assertEquals(0, after.getValue("LLA-014").compareTo(llave - BigDecimal("2")))
        assertEquals(0, after.getValue("CAB-205").compareTo(before.getValue("CAB-205") - BigDecimal("3.5")))
        assertEquals(1, runBlocking { repository.sales.first().size })
    }

    @Test fun previousPermissionAttemptGoesToManualWithoutRequestingAgain() {
        assertFalse(PhoneLocation.permitted(context))
        runBlocking { repository.markPermissionAsked() }
        ui.waitForIdle()
        ui.onNodeWithTag("usar_ubicacion").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("Puedes elegir el municipio sin compartir tu ubicación.").assertExists()
        ui.onNodeWithTag("guardar_zona").performScrollTo().assertIsNotEnabled()
        assertNull(runBlocking { repository.preferences.first().location })
    }
}
