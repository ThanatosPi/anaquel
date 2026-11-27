package mx.anaquel.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

// Base local del teléfono (BIT-023): las tablas del esquema v2 que la app captura, con los MISMOS
// nombres de tabla y de columna que db/schema.sql. db/comparar_esquema.py lo comprueba contra el
// esquema que Room exporta en app/mobile/schemas. La app guarda menos columnas que la base (sólo
// lo que captura), nunca otras; la única columna propia del teléfono es clave_local, que hace
// idempotente un guardado repetido.
//
// Dinero y cantidades van como texto (BigDecimal sin redondeo, Converters); fechas en ISO-8601.
// Las reglas que la base cuida con CHECK y disparadores las repiten las reglas puras del dominio
// (CountRules, DecisionRules) antes de escribir: aquí sólo hay llaves, únicos y foráneas.

@Entity(tableName = "rol")
data class RoleRow(
    @PrimaryKey @ColumnInfo(name = "codigo_rol") val code: String,
    @ColumnInfo(name = "descripcion_rol") val description: String,
)

@Entity(tableName = "municipio_zona_climatica",
    indices = [Index(value = ["entidad_federativa", "municipio"], unique = true)])
data class MunicipalityRow(
    @PrimaryKey @ColumnInfo(name = "municipio_zona_id") val id: Long,
    @ColumnInfo(name = "entidad_federativa") val state: String,
    @ColumnInfo(name = "municipio") val name: String,
    @ColumnInfo(name = "zona_climatica") val zone: String,
    @ColumnInfo(name = "latitud_centro") val latitude: Double,
    @ColumnInfo(name = "longitud_centro") val longitude: Double,
    @ColumnInfo(name = "radio_km") val radiusKm: Double,
    @ColumnInfo(name = "fuente_dato") val source: String,
    @ColumnInfo(name = "fecha_hora_captura") val capturedAt: String,
)

@Entity(tableName = "comercio",
    foreignKeys = [ForeignKey(MunicipalityRow::class, ["municipio_zona_id"], ["municipio_zona_id"])],
    indices = [Index("municipio_zona_id")])
data class BusinessRow(
    @PrimaryKey @ColumnInfo(name = "comercio_id") val id: Long,
    @ColumnInfo(name = "municipio_zona_id") val municipalityId: Long?,
    @ColumnInfo(name = "rfc") val rfc: String?,
    @ColumnInfo(name = "nombre_comercio") val name: String,
    @ColumnInfo(name = "giro_comercial") val line: String,
    @ColumnInfo(name = "zona_horaria") val timeZone: String,
    @ColumnInfo(name = "origen_zona_climatica") val zoneOrigin: String,
    @ColumnInfo(name = "latitud") val latitude: Double?,
    @ColumnInfo(name = "longitud") val longitude: Double?,
    @ColumnInfo(name = "fecha_alta") val createdOn: String,
)

@Entity(tableName = "usuario",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"]),
        ForeignKey(RoleRow::class, ["codigo_rol"], ["codigo_rol"])],
    indices = [Index("comercio_id"), Index("codigo_rol"), Index(value = ["correo_electronico"], unique = true)])
data class UserRow(
    @PrimaryKey @ColumnInfo(name = "usuario_id") val id: Long,
    @ColumnInfo(name = "comercio_id") val businessId: Long?,
    @ColumnInfo(name = "codigo_rol") val role: String,
    @ColumnInfo(name = "correo_electronico") val email: String,
    @ColumnInfo(name = "nombre_completo") val name: String,
    // El teléfono no inicia sesión todavía: la contraseña vive sólo en el servidor.
    @ColumnInfo(name = "hash_contrasena") val passwordHash: String,
    @ColumnInfo(name = "esta_activo") val active: Boolean,
    @ColumnInfo(name = "fecha_alta") val createdOn: String,
)

@Entity(tableName = "unidad_medida")
data class UnitRow(
    @PrimaryKey @ColumnInfo(name = "codigo_unidad") val code: String,
    @ColumnInfo(name = "descripcion_unidad") val description: String,
    @ColumnInfo(name = "permite_decimales") val allowsFraction: Boolean,
)

@Entity(tableName = "categoria",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"])],
    indices = [Index(value = ["comercio_id", "nombre_categoria"], unique = true)])
data class CategoryRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "categoria_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "nombre_categoria") val name: String,
    @ColumnInfo(name = "afinidad_climatica") val affinity: String?,
    @ColumnInfo(name = "zona_climatica_afinidad") val affinityZone: String?,
)

@Entity(tableName = "temporada",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"])],
    indices = [Index(value = ["comercio_id", "nombre_temporada"], unique = true)])
data class SeasonRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "temporada_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "nombre_temporada") val name: String,
    @ColumnInfo(name = "dia_inicio") val startDay: Int,
    @ColumnInfo(name = "mes_inicio") val startMonth: Int,
    @ColumnInfo(name = "dia_fin") val endDay: Int,
    @ColumnInfo(name = "mes_fin") val endMonth: Int,
)

@Entity(tableName = "producto",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"]),
        ForeignKey(CategoryRow::class, ["categoria_id"], ["categoria_id"]),
        ForeignKey(SeasonRow::class, ["temporada_id"], ["temporada_id"]),
        ForeignKey(UnitRow::class, ["codigo_unidad"], ["codigo_unidad"])],
    indices = [Index(value = ["comercio_id", "codigo_sku"], unique = true), Index("categoria_id"),
        Index("temporada_id"), Index("codigo_unidad")])
data class ProductRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "producto_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "codigo_sku") val sku: String,
    @ColumnInfo(name = "categoria_id") val categoryId: Long?,
    @ColumnInfo(name = "temporada_id") val seasonId: Long?,
    @ColumnInfo(name = "codigo_unidad") val unit: String,
    @ColumnInfo(name = "nombre_producto") val name: String,
    @ColumnInfo(name = "ciclo_producto") val cycle: String,
    @ColumnInfo(name = "afinidad_climatica_propia") val affinity: String?,
    @ColumnInfo(name = "zona_climatica_afinidad_propia") val affinityZone: String?,
    @ColumnInfo(name = "precio_venta_regular") val price: BigDecimal,
    @ColumnInfo(name = "costo_compra_unitario") val cost: BigDecimal?,
    @ColumnInfo(name = "stock_minimo") val minStock: Int?,
    @ColumnInfo(name = "stock_maximo") val maxStock: Int?,
    @ColumnInfo(name = "existencia_sistema") val systemStock: BigDecimal,
    @ColumnInfo(name = "fecha_caducidad") val expiresOn: String?,
    @ColumnInfo(name = "estado_producto") val status: String,
    @ColumnInfo(name = "fecha_alta") val createdOn: String,
)

@Entity(tableName = "conciliacion_inventario",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"]),
        ForeignKey(ProductRow::class, ["producto_id"], ["producto_id"]),
        ForeignKey(UserRow::class, ["usuario_id"], ["confirmado_por_usuario_id"])],
    indices = [Index("comercio_id"), Index("producto_id"), Index("confirmado_por_usuario_id"),
        Index(value = ["clave_local"], unique = true)])
data class CountRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "conciliacion_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "producto_id") val productId: Long,
    @ColumnInfo(name = "confirmado_por_usuario_id") val confirmedBy: Long?,
    @ColumnInfo(name = "existencia_sistema_al_contar") val systemStockAtCount: BigDecimal,
    @ColumnInfo(name = "existencia_contada") val counted: BigDecimal,
    @ColumnInfo(name = "metodo_conteo") val method: String,
    @ColumnInfo(name = "ruta_foto_anaquel") val photoPath: String?,
    @ColumnInfo(name = "confianza_vision") val visionConfidence: BigDecimal?,
    @ColumnInfo(name = "fecha_hora_conteo") val countedAt: String,
    // Sólo del teléfono (APP_SOLO en comparar_esquema.py): nulo en lo que viene del servidor.
    @ColumnInfo(name = "clave_local") val localKey: String?,
)

@Entity(tableName = "factura_proveedor",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"]),
        ForeignKey(UserRow::class, ["usuario_id"], ["recibida_por_usuario_id"])],
    indices = [Index("comercio_id"), Index("recibida_por_usuario_id"), Index(value = ["clave_local"], unique = true)])
data class InvoiceRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "factura_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "recibida_por_usuario_id") val receivedBy: Long?,
    @ColumnInfo(name = "nombre_proveedor") val supplier: String?,
    @ColumnInfo(name = "folio_factura") val folio: String?,
    @ColumnInfo(name = "fecha_emision") val issuedOn: String?,
    @ColumnInfo(name = "ruta_imagen_factura") val imagePath: String?,
    @ColumnInfo(name = "fecha_hora_captura") val capturedAt: String,
    @ColumnInfo(name = "clave_local") val localKey: String?,
)

@Entity(tableName = "detalle_factura_proveedor", primaryKeys = ["factura_id", "producto_id"],
    foreignKeys = [ForeignKey(InvoiceRow::class, ["factura_id"], ["factura_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductRow::class, ["producto_id"], ["producto_id"])],
    indices = [Index("producto_id")])
data class InvoiceLineRow(
    @ColumnInfo(name = "factura_id") val invoiceId: Long,
    @ColumnInfo(name = "producto_id") val productId: Long,
    @ColumnInfo(name = "descripcion_leida_ocr") val printedDescription: String?,
    @ColumnInfo(name = "cantidad_comprada") val quantity: BigDecimal?,
    @ColumnInfo(name = "costo_compra_unitario") val unitCost: BigDecimal?,
    @ColumnInfo(name = "confianza_ocr") val ocrConfidence: BigDecimal?,
)

@Entity(tableName = "venta",
    foreignKeys = [ForeignKey(BusinessRow::class, ["comercio_id"], ["comercio_id"])],
    indices = [Index(value = ["comercio_id", "folio_ticket_pos"], unique = true), Index(value = ["clave_local"], unique = true)])
data class SaleRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "venta_id") val id: Long = 0,
    @ColumnInfo(name = "comercio_id") val businessId: Long,
    @ColumnInfo(name = "folio_ticket_pos") val ticketFolio: String?,
    @ColumnInfo(name = "fecha_hora_venta") val soldAt: String,
    @ColumnInfo(name = "fecha_hora_carga") val loadedAt: String,
    @ColumnInfo(name = "clave_local") val localKey: String?,
)

@Entity(tableName = "detalle_venta", primaryKeys = ["venta_id", "producto_id"],
    foreignKeys = [ForeignKey(SaleRow::class, ["venta_id"], ["venta_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductRow::class, ["producto_id"], ["producto_id"])],
    indices = [Index("producto_id")])
data class SaleLineRow(
    @ColumnInfo(name = "venta_id") val saleId: Long,
    @ColumnInfo(name = "producto_id") val productId: Long,
    @ColumnInfo(name = "cantidad_vendida") val quantity: BigDecimal,
    @ColumnInfo(name = "precio_unitario_cobrado") val unitPrice: BigDecimal,
    // En la base es columna calculada; SQLite de minSdk 26 no las tiene, así que se guarda ya calculada.
    @ColumnInfo(name = "subtotal") val subtotal: BigDecimal,
)

// La parte de recomendacion que decide el dueño en el teléfono. El resto del renglón (precios,
// justificación, conciliación) es salida del motor y llega en corrida.json, cuyo contrato no
// cambia. Un renglón existe sólo mientras la decisión espera a enviarse: «Deshacer» lo borra.
// En la base, una vez recibida, la decisión ya no cambia (decision_inmutable).
@Entity(tableName = "recomendacion",
    foreignKeys = [ForeignKey(ProductRow::class, ["producto_id"], ["producto_id"]),
        ForeignKey(UserRow::class, ["usuario_id"], ["decidido_por_usuario_id"])],
    indices = [Index("producto_id"), Index("decidido_por_usuario_id"),
        Index(value = ["corrida_id", "producto_id"], unique = true)])
data class DecisionRow(
    @PrimaryKey @ColumnInfo(name = "recomendacion_id") val recommendationId: Long,
    @ColumnInfo(name = "corrida_id") val runId: Long,
    @ColumnInfo(name = "producto_id") val productId: Long,
    @ColumnInfo(name = "accion_recomendada") val output: String,
    @ColumnInfo(name = "sin_contraste_de_mercado") val withoutMarket: Boolean,
    @ColumnInfo(name = "decidido_por_usuario_id") val decidedBy: Long,
    @ColumnInfo(name = "estado_decision") val decision: String,
    @ColumnInfo(name = "fecha_hora_decision") val decidedAt: String,
    @ColumnInfo(name = "motivo_rechazo") val reason: String?,
    @ColumnInfo(name = "precio_autorizado") val authorizedPrice: BigDecimal?,
    @ColumnInfo(name = "acepto_riesgo_sin_contraste") val riskAccepted: Boolean,
)
