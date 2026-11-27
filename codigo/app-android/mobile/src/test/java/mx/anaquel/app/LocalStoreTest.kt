package mx.anaquel.app

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mx.anaquel.app.data.BusinessParser
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.data.DecisionRepository
import mx.anaquel.app.data.RunParser
import mx.anaquel.app.data.local.AnaquelDb
import mx.anaquel.app.data.local.LocalStore
import mx.anaquel.app.data.local.Seed
import mx.anaquel.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

// La base local de 0.4.0 en la JVM, sin teléfono (SQLite empaquetado). Lo que aquí se prueba es lo
// que el teléfono guarda con la forma de la v2 (BIT-023).
class LocalStoreTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private lateinit var db: AnaquelDb
    private lateinit var store: LocalStore
    private val now = "2026-10-21T10:00:00-06:00"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), AnaquelDb::class.java)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        store = LocalStore(db)
        store.seed(Seed.parse(fixture("catalogo.json"), fixture("municipios.json"), now))
    }
    @After fun tearDown() = db.close()

    private suspend fun product(sku: String) = store.catalog().first().products.single { it.sku == sku }

    @Test fun seedMirrorsTheCatalogWithoutInventingALocation() = runBlocking {
        assertTrue(store.isSeeded())
        val catalog = store.catalog().first()
        assertEquals(60, catalog.products.size)
        assertEquals(BusinessParser.catalog(fixture("catalogo.json")).products, catalog.products)
        assertEquals(21, store.municipalities().size)
        assertEquals((1L..21L).toList(), store.municipalities().map { it.id })
        assertNull(store.location().first())
    }

    // Lo que encontraron las pruebas instrumentadas: con la base vacía (recién instalada, o vaciada
    // por resetForTests mientras la pantalla ya observaba), el catálogo tronaba en vez de esperar.
    @Test fun catalogWaitsForTheSeedInsteadOfFailingOnAnEmptyDatabase() = runBlocking {
        val empty = Room.inMemoryDatabaseBuilder(ContextWrapper(null), AnaquelDb::class.java)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val fresh = LocalStore(empty)
        val waiting = async(Dispatchers.IO) { fresh.catalog().first() }
        assertNull(withTimeoutOrNull(500) { waiting.await() })
        fresh.seed(Seed.parse(fixture("catalogo.json"), fixture("municipios.json"), now))
        assertEquals(60, waiting.await().products.size)
        empty.close()
    }

    private val newProduct = ProductDraft(null, "TOR-900", "Tornillo de prueba", 1, MeasureUnit.PIEZA, "PERMANENTE",
        null, null, null, null, BigDecimal("12.50"), BigDecimal("6.00"), 2, 20, BigDecimal("15"), true)

    @Test fun aNewProductJoinsTheInventoryAndItsSkuIsUnique() = runBlocking {
        store.saveProduct(newProduct, "2026-10-21")
        val added = product("TOR-900")
        assertEquals(61, store.catalog().first().products.size)
        assertEquals(0, added.theoretical.compareTo(BigDecimal("15"))); assertNull(added.physical)
        assertEquals("Herramienta manual", added.category)
        val again = runCatching { store.saveProduct(newProduct.copy(name = "Otro"), "2026-10-21") }.exceptionOrNull()
        assertEquals("Ya hay un producto con ese código.", again?.message)
        val taken = runCatching { store.saveProduct(newProduct.copy(sku = "LLA-014"), "2026-10-21") }.exceptionOrNull()
        assertTrue(taken is IllegalArgumentException)
    }

    @Test fun editingChangesTheProductButNeverItsStockOrCode() = runBlocking {
        store.count("c-1", "LLA-014", BigDecimal("5"), now)
        val draft = store.productDraft("LLA-014")!!
        store.saveProduct(draft.copy(name = "Llave Stillson de 14 pulgadas", price = BigDecimal("499.00"),
            initialStock = BigDecimal("999"), cycle = "ESTACIONAL", seasonId = 1), "2026-10-21")
        val edited = store.productDraft("LLA-014")!!
        assertEquals("Llave Stillson de 14 pulgadas", edited.name); assertEquals(BigDecimal("499.00"), edited.price)
        assertEquals("ESTACIONAL", edited.cycle); assertEquals(1L, edited.seasonId)
        assertEquals(0, edited.initialStock.compareTo(BigDecimal("5")))   // la existencia la puso el conteo
    }

    @Test fun aUnitThatCannotHoldTheCurrentStockIsRefused() = runBlocking {
        val cable = store.catalog().first().products.first { it.unit == MeasureUnit.METRO }
        store.count("c-2", cable.sku, BigDecimal("12.5"), now)
        val error = runCatching { store.saveProduct(store.productDraft(cable.sku)!!.copy(unit = MeasureUnit.PIEZA), "2026-10-21") }
            .exceptionOrNull()
        assertTrue(error?.message.orEmpty().startsWith("La existencia actual (12.5) no cabe"))
    }

    @Test fun discontinuingHidesWithoutDeletingAndCanBeUndone() = runBlocking {
        store.count("c-3", "LLA-014", BigDecimal("6"), now)
        store.saveProduct(store.productDraft("LLA-014")!!.copy(active = false), "2026-10-21")
        val catalog = store.catalog().first()
        assertEquals(59, catalog.products.size); assertEquals(listOf("LLA-014"), catalog.discontinued.map { it.sku })
        assertEquals(1, store.localCounts().first().size)                  // su historial se conserva
        store.saveProduct(store.productDraft("LLA-014")!!.copy(active = true), "2026-10-21")
        assertEquals(60, store.catalog().first().products.size)
    }

    @Test fun seasonsAndCategoriesComeFromTheBusiness() = runBlocking {
        assertEquals(6, store.categories().first().size)
        assertEquals(listOf("Lluvias", "Fin de año", "Frentes fríos"), store.seasons().first().map { it.name })
        assertEquals("Lluvias · 1 jun – 30 sep", store.seasons().first().first().label)
        val foreign = runCatching { store.saveProduct(newProduct.copy(categoryId = 99), "2026-10-21") }.exceptionOrNull()
        assertTrue(foreign is IllegalArgumentException)
    }

    private val invoice = InvoiceDraft("factura-1", "Distribuidora del Sur", "F-100", "2026-10-20", null, listOf(
        InvoiceLine("LLA-014", "LLAVE ST 14", BigDecimal("3"), BigDecimal("380.00")),
        InvoiceLine("CAB-205", null, BigDecimal("12.5"), BigDecimal("14.00"))))

    @Test fun receivingAnInvoiceAddsToStockButLeavesTheProductCostAlone() = runBlocking {
        val llave = product("LLA-014").theoretical
        val cable = product("CAB-205").theoretical
        val cost = store.productDraft("LLA-014")!!.cost
        store.receiveInvoice(invoice, now)
        assertEquals(0, product("LLA-014").theoretical.compareTo(llave + BigDecimal("3")))
        assertEquals(0, product("CAB-205").theoretical.compareTo(cable + BigDecimal("12.5")))
        assertEquals(cost, store.productDraft("LLA-014")!!.cost)               // el costo de la factura es histórico
        val saved = store.invoices().first().single()
        assertEquals("F-100", saved.folio); assertEquals(2, saved.lines.size)
        assertEquals(BigDecimal("1315.00"), saved.total)
    }

    @Test fun theSameDraftSavedTwiceIsOneInvoice() = runBlocking {
        val before = product("LLA-014").theoretical
        store.receiveInvoice(invoice, now); store.receiveInvoice(invoice, now)
        assertEquals(1, store.invoices().first().size)
        assertEquals(0, product("LLA-014").theoretical.compareTo(before + BigDecimal("3")))
    }

    @Test fun anInvoiceWithAnInvalidLineSavesNothing() = runBlocking {
        val before = product("LLA-014").theoretical
        listOf(invoice.copy(lines = invoice.lines + invoice.lines.first()),                          // producto repetido
            invoice.copy(lines = listOf(invoice.lines.first().copy(quantity = BigDecimal("2.5")))),  // fracción de pieza
            invoice.copy(lines = invoice.lines + InvoiceLine("NO-EXISTE", null, BigDecimal.ONE, BigDecimal.ONE)),
            invoice.copy(lines = emptyList())).forEach { bad ->
            assertTrue(runCatching { store.receiveInvoice(bad, now) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(store.invoices().first().isEmpty())
        assertEquals(0, product("LLA-014").theoretical.compareTo(before))
    }

    @Test fun categoriesAreAddedRenamedAndKeepTheirAffinityZone() = runBlocking {
        store.saveCategory(null, "Plomería", "ALTA", ClimateZone.TEMPLADO)
        val added = store.categories().first().single { it.name == "Plomería" }
        assertEquals("ALTA", added.affinity); assertEquals("TEMPLADO", added.affinityZone)
        store.saveCategory(added.id, "Plomería y gas", null, ClimateZone.TEMPLADO)
        val renamed = store.categories().first().single { it.id == added.id }
        assertEquals("Plomería y gas", renamed.name); assertNull(renamed.affinity); assertNull(renamed.affinityZone)
        val duplicate = runCatching { store.saveCategory(null, " pintura ", null, null) }.exceptionOrNull()
        assertEquals("Ya hay una categoría con ese nombre.", duplicate?.message)
        assertTrue(runCatching { store.saveCategory(null, "Sin zona", "BAJA", null) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(7, store.categories().first().size)
    }

    @Test fun seasonsFollowBit015AndCanBeEdited() = runBlocking {
        store.saveSeason(null, "Regreso a clases", 15, 7, 31, 8)
        assertTrue(runCatching { store.saveSeason(null, "Imposible", 31, 2, 15, 3) }.exceptionOrNull() is IllegalArgumentException)
        store.saveSeason(null, "Bisiesto", 29, 2, 31, 3)
        val school = store.seasons().first().single { it.name == "Regreso a clases" }
        store.saveSeason(school.id, "Regreso a clases", 1, 8, 15, 9)
        assertEquals("Regreso a clases · 1 ago – 15 sep", store.seasons().first().single { it.id == school.id }.label)
        assertEquals(5, store.seasons().first().size)
    }

    private val sale = SaleDraft("venta-1", "T-001", java.time.LocalDateTime.parse("2026-10-21T17:30"), listOf(
        SaleLine("LLA-014", BigDecimal("2"), BigDecimal("520.00")), SaleLine("CAB-205", BigDecimal("3.5"), BigDecimal("14"))))

    @Test fun aSaleSubtractsFromStockAndKeepsItsSubtotals() = runBlocking {
        val llave = product("LLA-014").theoretical
        val cable = product("CAB-205").theoretical
        store.recordSale(sale, "2026-10-21T17:30-06:00", now)
        assertEquals(0, product("LLA-014").theoretical.compareTo(llave - BigDecimal("2")))
        assertEquals(0, product("CAB-205").theoretical.compareTo(cable - BigDecimal("3.5")))
        val saved = store.sales().first().single()
        assertEquals("T-001", saved.folio); assertEquals(BigDecimal("1089.00"), saved.total)
        assertEquals(listOf(BigDecimal("49.00"), BigDecimal("1040.00")), saved.lines.map { it.subtotal })
    }

    @Test fun theSameDraftIsOneSaleAndAFolioCannotRepeat() = runBlocking {
        store.recordSale(sale, "2026-10-21T17:30-06:00", now); store.recordSale(sale, "2026-10-21T17:30-06:00", now)
        assertEquals(1, store.sales().first().size)
        val repeated = runCatching { store.recordSale(sale.copy(key = "venta-2"), "2026-10-21T17:40-06:00", now) }.exceptionOrNull()
        assertEquals("Ya hay una venta con ese folio.", repeated?.message)
        store.recordSale(sale.copy(key = "venta-3", folio = null), "2026-10-21T17:45-06:00", now)
        store.recordSale(sale.copy(key = "venta-4", folio = null), "2026-10-21T17:50-06:00", now)   // sin folio no chocan
        assertEquals(3, store.sales().first().size)
    }

    @Test fun sellingMoreThanTheSystemHasSavesNothing() = runBlocking {
        val llave = product("LLA-014").theoretical
        val tooMany = sale.copy(lines = listOf(SaleLine("LLA-014", llave + BigDecimal.ONE, BigDecimal("520.00")),
            SaleLine("CAB-205", BigDecimal("1"), BigDecimal("14"))))
        val error = runCatching { store.recordSale(tooMany, "2026-10-21T17:30-06:00", now) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().endsWith("Cuenta el anaquel primero."))
        assertTrue(store.sales().first().isEmpty())
        assertEquals(0, product("LLA-014").theoretical.compareTo(llave))
        assertTrue(runCatching { store.recordSale(sale.copy(lines = listOf(SaleLine("LLA-014", BigDecimal("1.5"), BigDecimal.ONE))),
            "2026-10-21T17:30-06:00", now) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun countKeepsTheSnapshotAndBecomesTheSystemStock() = runBlocking {
        val before = product("LLA-014")
        store.count("borrador-1", "LLA-014", BigDecimal("5"), now)
        val count = store.localCounts().first().single()
        assertEquals(0, count.systemAtCount!!.compareTo(before.theoretical))
        assertEquals(0, count.physical.compareTo(BigDecimal("5")))
        assertEquals("MANUAL", count.origin); assertTrue(count.confirmed)
        val after = product("LLA-014")
        assertEquals(0, after.theoretical.compareTo(BigDecimal("5")))
        assertEquals(before.physical, after.physical)   // el conteo recibido no se toca
    }

    @Test fun repeatedDraftIsIdempotentButCannotChangeItsValue() = runBlocking {
        store.count("borrador-1", "LLA-014", BigDecimal("5"), now)
        store.count("borrador-1", "LLA-014", BigDecimal("5.000"), now)
        assertEquals(1, store.localCounts().first().size)
        val error = runCatching { store.count("borrador-1", "LLA-014", BigDecimal("4"), now) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(1, store.localCounts().first().size)
    }

    @Test fun piecesAreWholeAndMetersAcceptDecimals() = runBlocking {
        val pieces = runCatching { store.count("a", "LLA-014", BigDecimal("2.125"), now) }.exceptionOrNull()
        assertTrue(pieces is IllegalArgumentException)
        val cable = store.catalog().first().products.first { it.unit == MeasureUnit.METRO }
        store.count("b", cable.sku, BigDecimal("12.5"), now)
        assertEquals(0, product(cable.sku).theoretical.compareTo(BigDecimal("12.5")))
    }

    @Test fun locationStoresTheCatalogMunicipalityAndManualDropsCoordinates() = runBlocking {
        val merida = store.municipalities().single { it.name == "Mérida" }
        store.saveLocation(BusinessLocation(merida.zone, merida.name, merida.state, "GPS", now, 20.97, -89.62, merida.id))
        assertEquals(merida.id, store.location().first()!!.municipalityId)
        assertEquals(20.97, store.location().first()!!.latitude!!, 0.0)
        val tlalpan = store.municipalities().single { it.name == "Tlalpan" }
        store.saveLocation(BusinessLocation(tlalpan.zone, tlalpan.name, tlalpan.state, "MANUAL", now, 19.2, -99.2, tlalpan.id))
        val saved = store.location().first()!!
        assertEquals(ClimateZone.FRIO, saved.zone); assertEquals("Ciudad de México", saved.state)
        assertNull(saved.latitude); assertNull(saved.longitude)
        listOf(saved.copy(municipalityId = 999), saved.copy(municipalityId = null),
            saved.copy(origin = "GPS", latitude = null, longitude = null)).forEach {
            assertTrue(runCatching { store.saveLocation(it) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun decisionsAreRecommendationRowsSignedByTheOwnerAndUndoRemovesThem() = runBlocking {
        val run = RunParser.parse(fixture("corrida.json"))
        val rec = run.recommendations.first { it.actionable && it.output == Output.LIQUIDAR && !it.marketWarning }
        val price = rec.quote!!.minimum + BigDecimal("1.00")
        store.decide(run, setOf(rec.id), "APROBADA", price, null, false, now)
        val saved = store.decisions().first().single()
        assertEquals(rec.id, saved.recommendationId); assertEquals("APROBADA", saved.decision)
        assertEquals(0, saved.price!!.compareTo(price)); assertEquals("1", saved.actorId)
        val again = runCatching { store.decide(run, setOf(rec.id), "RECHAZADA", null, "No", false, now) }.exceptionOrNull()
        assertTrue(again is IllegalArgumentException)
        store.undo(setOf("${run.id}:${rec.id}"))
        assertTrue(store.decisions().first().isEmpty())
    }

    @Test fun authorizedPriceKeepsBothLimitsStrictAndRiskNeedsAcceptance() = runBlocking {
        val run = RunParser.parse(fixture("corrida.json"))
        val rec = run.recommendations.first { it.actionable && it.output == Output.LIQUIDAR && !it.marketWarning }
        listOf(rec.quote!!.minimum, rec.quote!!.original).forEach { limit ->
            assertTrue(runCatching { store.decide(run, setOf(rec.id), "APROBADA", limit, null, false, now) }
                .exceptionOrNull() is IllegalArgumentException)
        }
        val risky = run.recommendations.firstOrNull { it.actionable && it.marketWarning }
            ?: RunParser.parse(fixture("corrida_2.json")).recommendations.first { it.actionable && it.marketWarning }
        val riskyRun = if (risky in run.recommendations) run else RunParser.parse(fixture("corrida_2.json"))
        assertTrue(runCatching { store.decide(riskyRun, setOf(risky.id), "APROBADA", null, null, false, now) }
            .exceptionOrNull() is IllegalArgumentException)
        store.decide(riskyRun, setOf(risky.id), "APROBADA", null, null, true, now)
        assertTrue(store.decisions().first().single().marketRiskAccepted)
        assertTrue(store.decisions().first().isNotEmpty())
    }

    @Test fun migrationFromDataStoreKeepsHistoryResolvesGpsAndCanRunTwice() = runBlocking {
        val run = RunParser.parse(fixture("corrida.json"))
        val rec = run.recommendations.first { it.actionable && it.output == Output.RESURTIR }
        val location = BusinessRepository.encodeLocation(BusinessLocation(ClimateZone.TROPICAL, "Mérida", "Yucatán",
            "GPS", "2026-09-23T14:00:00-06:00", 20.9672, -89.6247))
        val counts = BusinessRepository.encodeCounts(listOf(
            StockCount("viejo-1", "LLA-014", BigDecimal("6"), "MANUAL", true, "2026-09-23T14:00:00-06:00"),
            StockCount("viejo-2", "LLA-014", BigDecimal("2.125"), "MANUAL", true, "2026-09-23T14:05:00-06:00")))
        val decisions = DecisionRepository.encode(listOf(LocalDecision(run.id, rec.id, "RECHAZADA", null, "Todavía hay",
            "2026-09-23T15:00:00-06:00", "dueno-demo-prueba", false)))
        val before = product("LLA-014").theoretical
        repeat(2) { assertNull(store.migrate(location, counts, decisions, listOf(run))) }
        assertEquals(1L, store.location().first()!!.municipalityId)
        val migrated = store.localCounts().first()
        assertEquals(listOf("viejo-1", "viejo-2"), migrated.map { it.id })
        val product = product("LLA-014")
        assertEquals(0, product.theoretical.compareTo(before))         // los conteos de 0.3.0 no mueven la existencia
        assertNull(product.confirmedQuantity(migrated))               // el último (2.125 piezas) sigue pidiendo corrección
        assertEquals("Todavía hay", store.decisions().first().single().reason)
    }

    @Test fun legacyLocationsWithoutMunicipalityAskTheOwnerToChoose() = runBlocking {
        val manual = """{"zona":"FRIO","municipio":"Toluca","estado":"","origen":"MANUAL","guardada_en":"2026-09-24T10:00:00-06:00"}"""
        assertEquals(LocalStore.LOCATION_MANUAL, store.migrate(manual, null, null, emptyList()))
        val gpsWithoutCoordinates = """{"zona":"TROPICAL","municipio":"Mérida","estado":"Yucatán","origen":"GPS","guardada_en":"2026-09-23T14:00:00-06:00"}"""
        assertEquals(LocalStore.LOCATION_GPS, store.migrate(gpsWithoutCoordinates, null, null, emptyList()))
        assertNull(store.location().first())
    }
}
