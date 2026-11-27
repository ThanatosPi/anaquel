package mx.anaquel.app.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.data.DecisionRepository
import mx.anaquel.app.data.decimal
import mx.anaquel.app.data.nullableString
import mx.anaquel.app.data.objects
import mx.anaquel.app.data.optionalDecimal
import mx.anaquel.app.domain.*
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

// Lo que siembra la base local la primera vez: catalogo.json y municipios.json, que ya llevan
// los nombres y los ids de la v2 (BIT-022). Se lee completo, con ids, para que un producto del
// teléfono sea el mismo producto_id que en el servidor.
class Seed(val business: BusinessRow, val users: List<UserRow>, val municipalities: List<MunicipalityRow>,
    val units: List<UnitRow>, val categories: List<CategoryRow>, val seasons: List<SeasonRow>,
    val products: List<ProductRow>, val counts: List<CountRow>, val cutoff: String) {

    companion object {
        // Mismo texto que el INSERT de rol y de unidad_medida en db/schema.sql.
        val ROLES = listOf(
            RoleRow("DUENO", "Dueño del comercio. Es el único que aprueba o rechaza una recomendación."),
            RoleRow("ADMINISTRADOR", "Administra la plataforma: da de alta municipios y comercios. No pertenece a ningún comercio."),
            RoleRow("ANALISTA", "Consulta el comercio y prepara información. No decide."),
            RoleRow("LLM", "El agente de Anaquel. Propone recomendaciones y nunca las autoriza. Uno por comercio."))
        private val UNIT_TEXT = mapOf(
            "PIEZA" to "Se cuenta por unidades enteras.",
            "METRO" to "Se vende por longitud: cable, manguera a granel.",
            "KILO" to "Se vende por peso: clavos, tornillería a granel.",
            "LITRO" to "Se vende por volumen: pintura o solvente a granel.")

        fun parse(catalogText: String, municipalitiesText: String, now: String): Seed {
            val root = JSONObject(catalogText)
            val store = root.getJSONObject("comercio")
            val businessId = store.getLong("comercio_id")
            val cutoff = root.getString("corte")
            val units = root.getJSONArray("unidades").objects().map {
                val code = it.getString("codigo_unidad")
                UnitRow(code, requireNotNull(UNIT_TEXT[code]), it.getBoolean("permite_decimales")).also { row ->
                    require(MeasureUnit.valueOf(row.code).allowsFraction == row.allowsFraction)
                }
            }
            val municipalities = JSONObject(municipalitiesText).getJSONArray("municipios").objects().map {
                MunicipalityRow(it.getLong("municipio_zona_id"), it.getString("entidad_federativa"), it.getString("municipio"),
                    ClimateZone.valueOf(it.getString("zona_climatica")).name, it.getDouble("latitud_centro"),
                    it.getDouble("longitud_centro"), it.getDouble("radio_km"), it.getString("fuente_dato"), now)
            }
            val categories = root.getJSONArray("categorias").objects().map {
                CategoryRow(it.getLong("categoria_id"), businessId, it.getString("nombre_categoria"),
                    it.nullableString("afinidad_climatica"), it.nullableString("zona_climatica_afinidad"))
            }
            val seasons = root.getJSONArray("temporadas").objects().map {
                SeasonRow(it.getLong("temporada_id"), businessId, it.getString("nombre_temporada"), it.getInt("dia_inicio"),
                    it.getInt("mes_inicio"), it.getInt("dia_fin"), it.getInt("mes_fin"))
            }
            val products = mutableListOf<ProductRow>()
            val counts = mutableListOf<CountRow>()
            root.getJSONArray("productos").objects().forEach {
                val id = it.getLong("producto_id")
                products += ProductRow(id, businessId, it.getString("codigo_sku"), it.getLong("categoria_id"),
                    if (it.isNull("temporada_id")) null else it.getLong("temporada_id"), it.getString("codigo_unidad"),
                    it.getString("nombre_producto"), it.getString("ciclo_producto"), it.nullableString("afinidad_climatica_propia"),
                    it.nullableString("zona_climatica_afinidad_propia"), it.decimal("precio_venta_regular"),
                    it.optionalDecimal("costo_compra_unitario"), if (it.isNull("stock_minimo")) null else it.getInt("stock_minimo"),
                    if (it.isNull("stock_maximo")) null else it.getInt("stock_maximo"), it.decimal("existencia_sistema"),
                    it.nullableString("fecha_caducidad"), it.getString("estado_producto"), cutoff)
                it.optJSONObject("conciliacion_vigente")?.let { k ->
                    val method = k.getString("metodo_conteo")
                    // Como en el simulador: un conteo FOTO o MANUAL lo confirmó el dueño (conciliacion_foto_confirmada).
                    counts += CountRow(k.getLong("conciliacion_id"), businessId, id, if (method == "CARGA") null else OWNER_ID,
                        k.decimal("existencia_sistema_al_contar"), k.decimal("existencia_contada"), method, null, null,
                        k.getString("fecha_hora_conteo"), null)
                }
            }
            val business = BusinessRow(businessId, null, null, store.getString("nombre_comercio"),
                store.getString("giro_comercial"), store.getString("zona_horaria"), "MANUAL", null, null, cutoff)
            // El dueño y la cuenta del agente, como en el comercio sintético. Sólo DUENO decide.
            val users = listOf(
                UserRow(OWNER_ID, businessId, "DUENO", "dueno@ejemplo.invalid", "Dueño del comercio", "", true, cutoff),
                UserRow(OWNER_ID + 1, businessId, "LLM", "agente@ejemplo.invalid", "Agente Anaquel", "", true, cutoff))
            return Seed(business, users, municipalities, units, categories, seasons, products, counts, cutoff)
        }

        const val OWNER_ID = 1L
    }
}

class LocalStore(private val db: AnaquelDb) {
    private val catalog = db.catalog()
    private val countDao = db.counts()
    private val decisionDao = db.decisions()
    private val invoiceDao = db.invoices()
    private val saleDao = db.sales()
    val businessId = 1L

    suspend fun isSeeded(): Boolean = catalog.businessCount() > 0

    // Todo o nada. La API multiplataforma de Room, no withTransaction de room-ktx: ésa exige el
    // Looper de Android y no corre en las pruebas JVM con SQLite empaquetado.
    private suspend fun <T> transaction(block: suspend () -> T): T =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    suspend fun seed(seed: Seed) = transaction {
        catalog.roles(Seed.ROLES); catalog.units(seed.units); catalog.municipalities(seed.municipalities)
        catalog.business(seed.business); catalog.users(seed.users); catalog.categories(seed.categories)
        catalog.seasons(seed.seasons); catalog.products(seed.products); countDao.insertAll(seed.counts)
    }

    suspend fun municipalities(): List<Municipality> = catalog.allMunicipalities().map { it.toDomain() }

    fun location(): Flow<BusinessLocation?> = catalog.observeBusiness(businessId).map { row ->
        val municipality = row?.municipalityId?.let { catalog.municipality(it) } ?: return@map null
        BusinessLocation(ClimateZone.valueOf(municipality.zone), municipality.name, municipality.state, row.zoneOrigin,
            "", row.latitude, row.longitude, municipality.id)
    }

    // El catálogo del inventario, con el último conteo RECIBIDO de cada producto; los del teléfono van
    // aparte (localCounts), como en 0.3.0. La fecha es la del corte con que se sembró (fecha_alta).
    fun catalog(): Flow<InventoryCatalog> = catalog.observeProducts(businessId).mapNotNull { rows ->
        // Sin comercio todavía (base recién creada o vaciada): no se emite nada hasta que se siembre.
        val business = catalog.business(businessId) ?: return@mapNotNull null
        val all = rows.map { p -> p.estado_producto to
            InventoryProduct(p.codigo_sku, p.nombre_producto, p.nombre_categoria.orEmpty(), p.ciclo_producto,
                p.precio_venta_regular, p.existencia_sistema, p.existencia_contada,
                p.existencia_contada?.let { counted -> requireNotNull(p.existencia_sistema_al_contar) - counted },
                p.fecha_hora_conteo?.let { OffsetDateTime.parse(it).toLocalDate().toString() }, p.metodo_conteo,
                MeasureUnit.valueOf(p.codigo_unidad))
        }
        InventoryCatalog(business.name, business.createdOn, all.filter { it.first == "ACTIVO" }.map { it.second },
            all.filter { it.first != "ACTIVO" }.map { it.second })
    }

    fun categories(): Flow<List<Category>> = catalog.observeCategories(businessId).map { rows ->
        rows.map { Category(it.id, it.name, it.affinity, it.affinityZone) }
    }

    fun seasons(): Flow<List<Season>> = catalog.observeSeasons(businessId).map { rows ->
        rows.map { Season(it.id, it.name, it.startDay, it.startMonth, it.endDay, it.endMonth) }
    }

    suspend fun productDraft(sku: String): ProductDraft? = catalog.product(businessId, sku)?.let {
        ProductDraft(it.id, it.sku, it.name, it.categoryId, MeasureUnit.valueOf(it.unit), it.cycle, it.seasonId, it.expiresOn,
            it.affinity, it.affinityZone?.let(ClimateZone::valueOf), it.price, it.cost, it.minStock, it.maxStock,
            it.systemStock, it.status == "ACTIVO")
    }

    // Alta o edición de un producto. Repite lo que defienden los CHECK de producto en la base, por si
    // alguien llama sin pasar por ProductRules. La existencia sólo se captura al dar de alta; al editar
    // no se toca (la mueven conteos, facturas y ventas). Nunca se borra: se descontinúa.
    suspend fun saveProduct(d: ProductDraft, today: String) = transaction {
        require(d.sku.isNotBlank() && d.name.isNotBlank()) { "Faltan el código o el nombre." }
        require(d.price.signum() > 0) { "El precio tiene que ser mayor que cero." }
        require(d.cost == null || d.cost.signum() >= 0) { "El costo no puede ser negativo." }
        require(d.minStock == null || d.maxStock == null || d.minStock <= d.maxStock) { "El mínimo no puede ser mayor que el máximo." }
        require(d.cycle in CYCLES) { "El ciclo no es válido." }
        require(d.cycle != "ESTACIONAL" || d.seasonId != null) { "Un producto de temporada necesita su temporada." }
        require(d.cycle != "PERECEDERO" || d.expiresOn != null) { "Un perecedero necesita su fecha de caducidad." }
        require((d.affinity == null) == (d.affinityZone == null)) { "La afinidad se declara para una zona." }
        require(d.categoryId == null || catalog.category(d.categoryId)?.businessId == businessId) { "La categoría no es de este negocio." }
        require(d.seasonId == null || catalog.season(d.seasonId)?.businessId == businessId) { "La temporada no es de este negocio." }
        val status = if (d.active) "ACTIVO" else "DESCONTINUADO"
        if (d.id == null) {
            require(catalog.product(businessId, d.sku) == null) { "Ya hay un producto con ese código." }
            require(CountRules.valid(d.initialStock, d.unit.allowsFraction)) { "La existencia no es válida para esta unidad." }
            catalog.insertProduct(ProductRow(0, businessId, d.sku, d.categoryId, d.seasonId, d.unit.name, d.name, d.cycle,
                d.affinity, d.affinityZone?.name, d.price, d.cost, d.minStock, d.maxStock, d.initialStock, d.expiresOn, status, today))
        } else {
            val current = requireNotNull(catalog.productById(d.id)) { "El producto ya no existe." }
            require(current.businessId == businessId)
            require(CountRules.valid(current.systemStock, d.unit.allowsFraction)) {
                "La existencia actual (${current.systemStock.stripTrailingZeros().toPlainString()}) no cabe en esa unidad."
            }
            catalog.updateProduct(current.copy(categoryId = d.categoryId, seasonId = d.seasonId, unit = d.unit.name,
                name = d.name, cycle = d.cycle, affinity = d.affinity, affinityZone = d.affinityZone?.name, price = d.price,
                cost = d.cost, minStock = d.minStock, maxStock = d.maxStock, expiresOn = d.expiresOn, status = status))
        }
    }

    fun localCounts(): Flow<List<StockCount>> = countDao.observeLocal(businessId).map { rows ->
        rows.map { StockCount(it.clave_local, it.codigo_sku, it.existencia_contada, it.metodo_conteo,
            it.confirmado_por_usuario_id != null, it.fecha_hora_conteo, it.existencia_sistema_al_contar) }
    }

    // BIT-012/014: el comercio guarda su municipio; la zona sale de ahí. En modo manual se borran
    // las coordenadas (decisión del 24/sep; en la base, comercio_manual_sin_coordenadas).
    suspend fun saveLocation(location: BusinessLocation) {
        val municipalityId = requireNotNull(location.municipalityId) { "Elige un municipio." }
        requireNotNull(catalog.municipality(municipalityId)) { "Ese municipio no está en el catálogo." }
        val stored = if (location.origin == "MANUAL") location.copy(latitude = null, longitude = null) else location
        require(stored.valid) { "La ubicación GPS requiere ambas coordenadas válidas." }
        catalog.saveLocation(businessId, municipalityId, stored.origin, stored.latitude, stored.longitude)
    }

    // Un conteo confirmado es un renglón de conciliacion_inventario: guarda lo que decía el sistema en
    // ese momento y, desde 0.4.0, lo que se contó pasa a ser la existencia del sistema (BIT-023). El
    // mismo borrador guardado dos veces no duplica el renglón (clave_local).
    suspend fun count(id: String, sku: String, quantity: BigDecimal, now: String) = transaction {
        require(id.isNotBlank())
        val product = requireNotNull(catalog.product(businessId, sku))
        require(CountRules.valid(quantity, MeasureUnit.valueOf(product.unit).allowsFraction)) {
            "La cantidad no es válida para esta unidad."
        }
        val existing = countDao.byKey(id)
        if (existing != null) {
            require(existing.productId == product.id && existing.counted.compareTo(quantity) == 0) { "El conteo ya se guardó con otro valor." }
        } else {
            countDao.insert(CountRow(0, businessId, product.id, catalog.ownerId(businessId), product.systemStock,
                quantity, "MANUAL", null, null, now, id))
            countDao.setSystemStock(product.id, quantity)
        }
    }

    fun decisions(): Flow<List<LocalDecision>> = decisionDao.observe().map { rows ->
        rows.map { LocalDecision(it.corrida_id, it.recomendacion_id, it.estado_decision, it.precio_autorizado,
            it.motivo_rechazo, it.fecha_hora_decision, it.decidido_por_usuario_id.toString(), it.acepto_riesgo_sin_contraste) }
    }

    // Sólo el DUENO decide (en la base, exigir_firma_del_dueno). Todo el lote o nada.
    suspend fun decide(run: AnalysisRun, ids: Set<Long>, decision: String, price: BigDecimal?,
        reason: String?, acceptMarketRisk: Boolean, now: String) = transaction {
        require(ids.isNotEmpty())
        val selected = ids.map { id -> run.recommendations.single { it.id == id } }
        require(selected.map { it.output }.distinct().size == 1) { "Selecciona un solo tipo de salida." }
        val decided = decisionDao.decidedIds().toSet()
        val owner = catalog.ownerId(businessId)
        decisionDao.insertAll(selected.map { rec ->
            require(rec.id !in decided) { "Esta recomendación ya estaba decidida." }
            val authorizedPrice = if (decision == "APROBADA" && rec.output == Output.LIQUIDAR) price ?: rec.quote?.proposed else null
            val error = DecisionRules.validate(rec, decision, authorizedPrice, reason, acceptMarketRisk)
            require(error == null) { error.orEmpty() }
            decisionRow(run.id, rec, owner, decision, authorizedPrice, if (decision == "RECHAZADA") reason?.trim() else null,
                decision == "APROBADA" && rec.marketWarning && acceptMarketRisk, now)
        })
    }

    fun invoices(): Flow<List<InvoiceSummary>> = combine(invoiceDao.observe(businessId), invoiceDao.observeLines()) { rows, lines ->
        val byInvoice = lines.groupBy { it.factura_id }
        rows.map { row ->
            InvoiceSummary(row.id, row.supplier, row.folio, row.issuedOn, row.imagePath, byInvoice[row.id].orEmpty().map {
                InvoiceLineSummary(it.codigo_sku, it.nombre_producto, MeasureUnit.valueOf(it.codigo_unidad),
                    it.cantidad_comprada ?: BigDecimal.ZERO, it.costo_compra_unitario)
            })
        }
    }

    // Recibir una factura: la factura, sus renglones ya emparejados con su producto y la suma a la
    // existencia de cada producto, todo o nada (BIT-023, decisión de Sebastián del 20/oct). El costo
    // vigente del producto NO se toca: cuál manda entre factura y punto de venta sigue pendiente desde
    // la Clase 07; el de cada compra queda en detalle_factura_proveedor. El mismo borrador guardado
    // dos veces no duplica la factura (clave_local).
    suspend fun receiveInvoice(d: InvoiceDraft, now: String) = transaction {
        if (invoiceDao.byKey(d.key) != null) return@transaction
        require(d.supplier.isNotBlank()) { "Escribe el nombre del proveedor." }
        require(d.lines.isNotEmpty()) { "Agrega al menos un producto de la factura." }
        require(d.lines.map { it.sku }.distinct().size == d.lines.size) { "Cada producto va una sola vez." }
        val products = d.lines.map { line ->
            val product = requireNotNull(catalog.product(businessId, line.sku)) { "El producto ${line.sku} no es de este negocio." }
            require(line.quantity.signum() > 0 && CountRules.valid(line.quantity, MeasureUnit.valueOf(product.unit).allowsFraction)) {
                "La cantidad de ${line.sku} no es válida para su unidad."
            }
            require(line.unitCost.signum() >= 0) { "El costo de ${line.sku} no puede ser negativo." }
            product
        }
        val id = invoiceDao.insert(InvoiceRow(0, businessId, catalog.ownerId(businessId), d.supplier, d.folio, d.issuedOn,
            d.photoPath, now, d.key))
        invoiceDao.insertLines(d.lines.zip(products).map { (line, product) ->
            InvoiceLineRow(id, product.id, line.printed, line.quantity, line.unitCost, null)
        })
        d.lines.zip(products).forEach { (line, product) -> countDao.setSystemStock(product.id, product.systemStock + line.quantity) }
    }

    fun sales(): Flow<List<SaleSummary>> = combine(saleDao.observe(businessId), saleDao.observeLines()) { rows, lines ->
        val bySale = lines.groupBy { it.venta_id }
        rows.map { row ->
            SaleSummary(row.id, row.ticketFolio, row.soldAt, bySale[row.id].orEmpty().map {
                SaleLineSummary(it.codigo_sku, it.nombre_producto, MeasureUnit.valueOf(it.codigo_unidad), it.cantidad_vendida,
                    it.precio_unitario_cobrado, it.subtotal)
            })
        }
    }

    // Registrar una venta a mano: la venta, sus renglones y la resta de cada cantidad a la
    // existencia del sistema, todo o nada (BIT-023). No se vende más de lo que el sistema dice que
    // hay: si no cuadra, primero se cuenta el anaquel. El folio del ticket es único dentro del
    // comercio (venta_folio_unico) y el mismo borrador guardado dos veces no duplica la venta.
    suspend fun recordSale(d: SaleDraft, soldAt: String, now: String) = transaction {
        if (saleDao.byKey(d.key) != null) return@transaction
        require(d.lines.isNotEmpty()) { "Agrega al menos un producto vendido." }
        require(d.lines.map { it.sku }.distinct().size == d.lines.size) { "Cada producto va una sola vez." }
        require(d.folio == null || saleDao.byFolio(businessId, d.folio) == null) { "Ya hay una venta con ese folio." }
        val products = d.lines.map { line ->
            val product = requireNotNull(catalog.product(businessId, line.sku)) { "El producto ${line.sku} no es de este negocio." }
            val unit = MeasureUnit.valueOf(product.unit)
            require(line.quantity.signum() > 0 && CountRules.valid(line.quantity, unit.allowsFraction)) {
                "La cantidad de ${line.sku} no es válida para su unidad."
            }
            require(line.unitPrice.signum() >= 0) { "El precio de ${line.sku} no puede ser negativo." }
            require(line.quantity <= product.systemStock) { "${line.sku}: ${SaleRules.stockMessage(product.systemStock, unit)}" }
            product
        }
        val id = saleDao.insert(SaleRow(0, businessId, d.folio, soldAt, now, d.key))
        saleDao.insertLines(d.lines.zip(products).map { (line, product) ->
            SaleLineRow(id, product.id, line.quantity, line.unitPrice, line.subtotal)
        })
        d.lines.zip(products).forEach { (line, product) -> countDao.setSystemStock(product.id, product.systemStock - line.quantity) }
    }

    // Alta o edición de una categoría. La afinidad se declara para la zona del negocio
    // (categoria_afinidad_con_zona). No se borra: hay productos que la citan.
    suspend fun saveCategory(id: Long?, name: String, affinity: String?, zone: ClimateZone?) = transaction {
        val others = catalog.allCategories(businessId).filter { it.id != id }.map { it.name }
        val errors = CatalogRules.checkCategory(name, others)
        require(errors.isEmpty()) { errors.values.first() }
        require(affinity == null || (affinity in AFFINITIES && zone != null)) { "La afinidad se declara para una zona." }
        val row = CategoryRow(id ?: 0, businessId, name.trim(), affinity, zone?.name?.takeIf { affinity != null })
        if (id == null) catalog.insertCategory(row)
        else {
            require(catalog.category(id)?.businessId == businessId) { "La categoría ya no existe." }
            catalog.updateCategory(row)
        }
    }

    // Alta o edición de una temporada (BIT-015). No se borra: hay productos que la citan.
    suspend fun saveSeason(id: Long?, name: String, startDay: Int, startMonth: Int, endDay: Int, endMonth: Int) = transaction {
        val others = catalog.allSeasons(businessId).filter { it.id != id }.map { it.name }
        val errors = CatalogRules.checkSeason(name, "$startDay", "$startMonth", "$endDay", "$endMonth", others)
        require(errors.isEmpty()) { errors.values.first() }
        val row = SeasonRow(id ?: 0, businessId, name.trim(), startDay, startMonth, endDay, endMonth)
        if (id == null) catalog.insertSeason(row)
        else {
            require(catalog.season(id)?.businessId == businessId) { "La temporada ya no existe." }
            catalog.updateSeason(row)
        }
    }

    // «Deshacer» antes de enviar: la decisión nunca salió del teléfono, así que se borra.
    suspend fun undo(keys: Set<String>) {
        decisionDao.delete(keys.map { it.substringAfter(':').toLong() })
    }

    // De 0.3.0 (DataStore) a la base local. No borra nada de DataStore y se puede repetir: lo que ya
    // está no se vuelve a insertar. Los conteos viejos se conservan como historial y no mueven la
    // existencia del sistema (se hicieron con la regla anterior). Devuelve el motivo si la ubicación
    // anterior no alcanza para saber el municipio: el dueño la tiene que elegir.
    suspend fun migrate(location: String?, counts: String?, decisions: String?, runs: List<AnalysisRun>): String? =
        transaction {
            val owner = catalog.ownerId(businessId)
            var reason: String? = null
            if (location != null) {
                val legacy = runCatching { BusinessRepository.decodeLocation(location) }.getOrNull()
                val resolved = legacy?.takeIf { it.origin == "GPS" }?.let {
                    ZoneResolver.resolve(requireNotNull(it.latitude), requireNotNull(it.longitude), municipalities())
                }
                when {
                    resolved != null -> catalog.saveLocation(businessId, resolved.id, "GPS", legacy.latitude, legacy.longitude)
                    legacy?.origin == "MANUAL" -> reason = LOCATION_MANUAL
                    else -> reason = LOCATION_GPS
                }
            }
            BusinessRepository.decodeCounts(counts).forEach { entry ->
                val product = catalog.product(businessId, entry.sku) ?: return@forEach
                if (countDao.byKey(entry.id) == null) countDao.insert(CountRow(0, businessId, product.id,
                    if (entry.confirmed) owner else null, product.systemStock, entry.physical, entry.origin, null, null,
                    entry.countedAt, entry.id))
            }
            val decided = decisionDao.decidedIds().toMutableSet()
            decisionDao.insertAll(DecisionRepository.decode(decisions).mapNotNull { d ->
                val rec = runs.find { it.id == d.runId }?.recommendations?.find { it.id == d.recommendationId }
                    ?: return@mapNotNull null
                if (!decided.add(rec.id)) return@mapNotNull null
                decisionRow(d.runId, rec, owner, d.decision, d.price, d.reason, d.marketRiskAccepted, d.date)
            })
            reason
        }

    private suspend fun decisionRow(runId: Long, rec: Recommendation, owner: Long, decision: String,
        price: BigDecimal?, reason: String?, riskAccepted: Boolean, at: String): DecisionRow {
        val product = requireNotNull(catalog.product(businessId, rec.product.sku))
        return DecisionRow(rec.id, runId, product.id, rec.output.name, rec.quote?.withoutMarket == true, owner,
            decision, at, reason, price, riskAccepted)
    }

    companion object {
        const val LOCATION_GPS = "GPS_SIN_COORDENADAS"
        const val LOCATION_MANUAL = "MANUAL_SIN_MUNICIPIO"
    }
}

fun MunicipalityRow.toDomain() = Municipality(state, name, ClimateZone.valueOf(zone), latitude, longitude, radiusKm, id)
