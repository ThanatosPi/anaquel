package mx.anaquel.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class Converters {
    @TypeConverter fun fromDecimal(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun toDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}

// El último conteo de cada producto, como v_conciliacion_vigente: el más reciente y, a la misma
// hora, el último en guardarse (MSG-018 H3).
data class ProductWithCount(
    val producto_id: Long, val codigo_sku: String, val nombre_producto: String, val nombre_categoria: String?,
    val ciclo_producto: String, val codigo_unidad: String, val precio_venta_regular: BigDecimal,
    val existencia_sistema: BigDecimal, val existencia_contada: BigDecimal?,
    val existencia_sistema_al_contar: BigDecimal?, val fecha_hora_conteo: String?, val metodo_conteo: String?,
    val estado_producto: String,
)

data class LocalCount(val clave_local: String, val codigo_sku: String, val existencia_contada: BigDecimal,
    val existencia_sistema_al_contar: BigDecimal, val metodo_conteo: String, val confirmado_por_usuario_id: Long?,
    val fecha_hora_conteo: String)

data class StoredDecision(val recomendacion_id: Long, val corrida_id: Long, val estado_decision: String,
    val precio_autorizado: BigDecimal?, val motivo_rechazo: String?, val fecha_hora_decision: String,
    val decidido_por_usuario_id: Long, val acepto_riesgo_sin_contraste: Boolean)

@Dao interface CatalogDao {
    @Insert suspend fun roles(rows: List<RoleRow>)
    @Insert suspend fun units(rows: List<UnitRow>)
    @Insert suspend fun municipalities(rows: List<MunicipalityRow>)
    @Insert suspend fun business(row: BusinessRow)
    @Insert suspend fun users(rows: List<UserRow>)
    @Insert suspend fun categories(rows: List<CategoryRow>)
    @Insert suspend fun seasons(rows: List<SeasonRow>)
    @Insert suspend fun products(rows: List<ProductRow>)

    @Query("SELECT count(*) FROM comercio") suspend fun businessCount(): Int
    @Query("SELECT * FROM municipio_zona_climatica ORDER BY municipio_zona_id") suspend fun allMunicipalities(): List<MunicipalityRow>
    @Query("SELECT * FROM comercio WHERE comercio_id = :id") fun observeBusiness(id: Long): Flow<BusinessRow?>
    @Query("SELECT * FROM comercio WHERE comercio_id = :id") suspend fun business(id: Long): BusinessRow?
    @Query("SELECT * FROM municipio_zona_climatica WHERE municipio_zona_id = :id") suspend fun municipality(id: Long): MunicipalityRow?
    @Query("""UPDATE comercio SET municipio_zona_id = :municipalityId, origen_zona_climatica = :origin,
              latitud = :latitude, longitud = :longitude WHERE comercio_id = :businessId""")
    suspend fun saveLocation(businessId: Long, municipalityId: Long, origin: String, latitude: Double?, longitude: Double?)
    @Query("SELECT usuario_id FROM usuario WHERE comercio_id = :businessId AND codigo_rol = 'DUENO' ORDER BY usuario_id LIMIT 1")
    suspend fun ownerId(businessId: Long): Long
    @Query("SELECT * FROM producto WHERE comercio_id = :businessId AND codigo_sku = :sku")
    suspend fun product(businessId: Long, sku: String): ProductRow?

    @Query("""SELECT p.producto_id, p.codigo_sku, p.nombre_producto, c.nombre_categoria, p.ciclo_producto,
                     p.codigo_unidad, p.precio_venta_regular, p.existencia_sistema,
                     k.existencia_contada, k.existencia_sistema_al_contar, k.fecha_hora_conteo, k.metodo_conteo,
                     p.estado_producto
              FROM producto p
              LEFT JOIN categoria c ON c.categoria_id = p.categoria_id
              LEFT JOIN conciliacion_inventario k ON k.conciliacion_id = (
                  SELECT k2.conciliacion_id FROM conciliacion_inventario k2 WHERE k2.producto_id = p.producto_id AND k2.clave_local IS NULL
                  ORDER BY k2.fecha_hora_conteo DESC, k2.conciliacion_id DESC LIMIT 1)
              WHERE p.comercio_id = :businessId
              ORDER BY p.producto_id""")
    fun observeProducts(businessId: Long): Flow<List<ProductWithCount>>

    // Un producto nuevo del teléfono recibe un producto_id local; al sincronizar, el servidor lo
    // reconoce por (comercio_id, codigo_sku), que es único (producto_sku_unico).
    @Insert suspend fun insertProduct(row: ProductRow): Long
    @Update suspend fun updateProduct(row: ProductRow)
    @Query("SELECT * FROM producto WHERE producto_id = :id") suspend fun productById(id: Long): ProductRow?
    @Query("SELECT * FROM categoria WHERE comercio_id = :businessId ORDER BY nombre_categoria")
    fun observeCategories(businessId: Long): Flow<List<CategoryRow>>
    @Query("SELECT * FROM temporada WHERE comercio_id = :businessId ORDER BY mes_inicio, dia_inicio")
    fun observeSeasons(businessId: Long): Flow<List<SeasonRow>>
    @Query("SELECT * FROM categoria WHERE categoria_id = :id") suspend fun category(id: Long): CategoryRow?
    @Insert suspend fun insertCategory(row: CategoryRow): Long
    @Update suspend fun updateCategory(row: CategoryRow)
    @Insert suspend fun insertSeason(row: SeasonRow): Long
    @Update suspend fun updateSeason(row: SeasonRow)
    @Query("SELECT * FROM categoria WHERE comercio_id = :businessId") suspend fun allCategories(businessId: Long): List<CategoryRow>
    @Query("SELECT * FROM temporada WHERE comercio_id = :businessId") suspend fun allSeasons(businessId: Long): List<SeasonRow>
    @Query("SELECT * FROM temporada WHERE temporada_id = :id") suspend fun season(id: Long): SeasonRow?
}

@Dao interface CountDao {
    @Insert suspend fun insert(row: CountRow): Long
    @Insert suspend fun insertAll(rows: List<CountRow>)
    @Query("SELECT * FROM conciliacion_inventario WHERE clave_local = :key") suspend fun byKey(key: String): CountRow?
    @Query("UPDATE producto SET existencia_sistema = :stock WHERE producto_id = :productId")
    suspend fun setSystemStock(productId: Long, stock: BigDecimal)
    // Los conteos capturados en el teléfono, en el orden en que se hicieron.
    @Query("""SELECT k.clave_local, p.codigo_sku, k.existencia_contada, k.existencia_sistema_al_contar,
                     k.metodo_conteo, k.confirmado_por_usuario_id,
                     k.fecha_hora_conteo
              FROM conciliacion_inventario k JOIN producto p ON p.producto_id = k.producto_id
              WHERE k.comercio_id = :businessId AND k.clave_local IS NOT NULL ORDER BY k.conciliacion_id""")
    fun observeLocal(businessId: Long): Flow<List<LocalCount>>
}

data class InvoiceLineView(val factura_id: Long, val codigo_sku: String, val nombre_producto: String,
    val codigo_unidad: String, val cantidad_comprada: BigDecimal?, val costo_compra_unitario: BigDecimal?)

@Dao interface InvoiceDao {
    @Insert suspend fun insert(row: InvoiceRow): Long
    @Insert suspend fun insertLines(rows: List<InvoiceLineRow>)
    @Query("SELECT * FROM factura_proveedor WHERE clave_local = :key") suspend fun byKey(key: String): InvoiceRow?
    @Query("SELECT * FROM factura_proveedor WHERE comercio_id = :businessId ORDER BY fecha_hora_captura DESC, factura_id DESC")
    fun observe(businessId: Long): Flow<List<InvoiceRow>>
    @Query("""SELECT d.factura_id, p.codigo_sku, p.nombre_producto, p.codigo_unidad, d.cantidad_comprada, d.costo_compra_unitario
              FROM detalle_factura_proveedor d JOIN producto p ON p.producto_id = d.producto_id ORDER BY p.codigo_sku""")
    fun observeLines(): Flow<List<InvoiceLineView>>
}

data class SaleLineView(val venta_id: Long, val codigo_sku: String, val nombre_producto: String, val codigo_unidad: String,
    val cantidad_vendida: BigDecimal, val precio_unitario_cobrado: BigDecimal, val subtotal: BigDecimal)

@Dao interface SaleDao {
    @Insert suspend fun insert(row: SaleRow): Long
    @Insert suspend fun insertLines(rows: List<SaleLineRow>)
    @Query("SELECT * FROM venta WHERE clave_local = :key") suspend fun byKey(key: String): SaleRow?
    @Query("SELECT * FROM venta WHERE comercio_id = :businessId AND folio_ticket_pos = :folio")
    suspend fun byFolio(businessId: Long, folio: String): SaleRow?
    @Query("SELECT * FROM venta WHERE comercio_id = :businessId ORDER BY fecha_hora_venta DESC, venta_id DESC")
    fun observe(businessId: Long): Flow<List<SaleRow>>
    @Query("""SELECT d.venta_id, p.codigo_sku, p.nombre_producto, p.codigo_unidad, d.cantidad_vendida,
                     d.precio_unitario_cobrado, d.subtotal
              FROM detalle_venta d JOIN producto p ON p.producto_id = d.producto_id ORDER BY p.codigo_sku""")
    fun observeLines(): Flow<List<SaleLineView>>
}

@Dao interface DecisionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<DecisionRow>)
    @Query("SELECT recomendacion_id FROM recomendacion") suspend fun decidedIds(): List<Long>
    @Query("DELETE FROM recomendacion WHERE recomendacion_id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("""SELECT recomendacion_id, corrida_id, estado_decision, precio_autorizado, motivo_rechazo,
                     fecha_hora_decision, decidido_por_usuario_id, acepto_riesgo_sin_contraste
              FROM recomendacion ORDER BY fecha_hora_decision, recomendacion_id""")
    fun observe(): Flow<List<StoredDecision>>
}

@Database(version = 1, exportSchema = true, entities = [
    RoleRow::class, MunicipalityRow::class, BusinessRow::class, UserRow::class, UnitRow::class,
    CategoryRow::class, SeasonRow::class, ProductRow::class, CountRow::class, InvoiceRow::class,
    InvoiceLineRow::class, SaleRow::class, SaleLineRow::class, DecisionRow::class])
@TypeConverters(Converters::class)
abstract class AnaquelDb : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun counts(): CountDao
    abstract fun decisions(): DecisionDao
    abstract fun invoices(): InvoiceDao
    abstract fun sales(): SaleDao

    companion object {
        @Volatile private var instance: AnaquelDb? = null
        fun get(context: Context): AnaquelDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AnaquelDb::class.java, "anaquel.db")
                .build().also { instance = it }
        }
    }
}
