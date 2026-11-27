package mx.anaquel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import mx.anaquel.app.data.local.LocalStore
import mx.anaquel.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

// Desde 0.4.0 el negocio, su catálogo y los conteos viven en la base local (Room, tablas de la v2).
// DataStore se queda sólo con el estado de la interfaz: si ya se pidió el permiso de ubicación.
class BusinessRepository(private val context: Context) {
    private val permissionKey = booleanPreferencesKey("ubicacion_permiso_solicitado_v1")

    val preferences: Flow<BusinessPreferences> = flow {
        val store = LocalSetup.ready(context)
        emitAll(combine(store.location(), store.localCounts(), context.decisionStore.data) { location, counts, it ->
            val pending = it[LocalSetup.pendingLocation]
            BusinessPreferences(location, counts, it[permissionKey] ?: false,
                locationNeedsConfirmation = location == null && pending == LocalStore.LOCATION_GPS,
                locationNeedsMunicipality = location == null && pending == LocalStore.LOCATION_MANUAL)
        })
    }

    val catalog: Flow<InventoryCatalog> = flow { emitAll(LocalSetup.ready(context).catalog()) }

    suspend fun municipalities(): List<Municipality> = LocalSetup.ready(context).municipalities()

    val categories: Flow<List<Category>> = flow { emitAll(LocalSetup.ready(context).categories()) }
    val seasons: Flow<List<Season>> = flow { emitAll(LocalSetup.ready(context).seasons()) }

    suspend fun productDraft(sku: String): ProductDraft? = LocalSetup.ready(context).productDraft(sku)

    val invoices: Flow<List<InvoiceSummary>> = flow { emitAll(LocalSetup.ready(context).invoices()) }
    val sales: Flow<List<SaleSummary>> = flow { emitAll(LocalSetup.ready(context).sales()) }

    suspend fun recordSale(draft: SaleDraft) = LocalSetup.ready(context).recordSale(draft,
        draft.soldAt.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString(), OffsetDateTime.now().toString())

    suspend fun saveCategory(id: Long?, name: String, affinity: String?, zone: ClimateZone?) =
        LocalSetup.ready(context).saveCategory(id, name, affinity, zone)

    suspend fun saveSeason(id: Long?, name: String, startDay: Int, startMonth: Int, endDay: Int, endMonth: Int) =
        LocalSetup.ready(context).saveSeason(id, name, startDay, startMonth, endDay, endMonth)

    suspend fun receiveInvoice(draft: InvoiceDraft) =
        LocalSetup.ready(context).receiveInvoice(draft, OffsetDateTime.now().toString())

    suspend fun saveProduct(draft: ProductDraft) =
        LocalSetup.ready(context).saveProduct(draft, java.time.LocalDate.now().toString())

    suspend fun markPermissionAsked() { context.decisionStore.edit { it[permissionKey] = true } }

    suspend fun saveLocation(location: BusinessLocation) {
        LocalSetup.ready(context).saveLocation(location)
        context.decisionStore.edit { it.remove(LocalSetup.pendingLocation) }
    }

    suspend fun count(id: String, sku: String, quantity: BigDecimal) =
        LocalSetup.ready(context).count(id, sku, quantity, OffsetDateTime.now().toString())

    // El formato de 0.3.0 en DataStore. Sólo lo lee la migración (LocalStore.migrate) y las pruebas
    // que preparan datos de esa versión; la app ya no escribe aquí.
    companion object {
        internal fun encodeLocation(location: BusinessLocation): String {
            val stored = if (location.origin == "MANUAL") location.copy(latitude = null, longitude = null) else location
            require(stored.valid) { "La ubicación GPS requiere ambas coordenadas válidas." }
            OffsetDateTime.parse(stored.savedAt)
            return JSONObject().apply {
                put("zona", stored.zone.name); put("municipio", stored.municipality)
                put("estado", stored.state); put("origen", stored.origin); put("guardada_en", stored.savedAt)
                if (stored.origin == "GPS") { put("latitud", stored.latitude); put("longitud", stored.longitude) }
            }.toString()
        }
        internal fun decodeLocation(text: String): BusinessLocation? = JSONObject(text).let {
            BusinessLocation(ClimateZone.valueOf(it.getString("zona")), it.getString("municipio"), it.getString("estado"),
                it.getString("origen"), it.getString("guardada_en"),
                if (it.isNull("latitud")) null else it.getDouble("latitud"),
                if (it.isNull("longitud")) null else it.getDouble("longitud")).takeIf { location -> location.valid }
        }
        internal fun encodeCounts(counts: List<StockCount>): String = JSONArray().apply {
            counts.forEach { entry -> put(JSONObject().apply {
                put("id", entry.id); put("sku", entry.sku); put("existencia_fisica", entry.physical.toPlainString())
                put("origen", entry.origin); put("confirmada", entry.confirmed); put("contada_en", entry.countedAt)
            }) }
        }.toString()
        internal fun decodeCounts(text: String?): List<StockCount> = if (text == null) emptyList() else
            JSONArray(text).objects().map {
                StockCount(it.getString("id"), it.getString("sku"), it.decimal("existencia_fisica"),
                    it.getString("origen"), it.getBoolean("confirmada"), it.getString("contada_en"))
            }
    }
}
