package mx.anaquel.app.data

import mx.anaquel.app.domain.*
import org.json.JSONObject
import java.time.OffsetDateTime

// municipios.json y catalogo.json llevan los nombres de las columnas del esquema v2
// (municipio_zona_climatica, producto, conciliacion_inventario; BIT-022).
object BusinessParser {
    fun municipalities(text: String): List<Municipality> =
        JSONObject(text).getJSONArray("municipios").objects().map {
            Municipality(it.getString("entidad_federativa"), it.getString("municipio"),
                ClimateZone.valueOf(it.getString("zona_climatica")), it.getDouble("latitud_centro"),
                it.getDouble("longitud_centro"), it.getDouble("radio_km"), it.getLong("municipio_zona_id")).also { city ->
                require(city.latitude.isFinite() && city.latitude in -90.0..90.0)
                require(city.longitude.isFinite() && city.longitude in -180.0..180.0)
                require(city.radiusKm.isFinite() && city.radiusKm > 0)
            }
        }.also { cities -> require(cities.isNotEmpty() && cities.map { it.id }.distinct().size == cities.size) }

    fun catalog(text: String): InventoryCatalog {
        val root = JSONObject(text)
        // BIT-017: el catálogo unidad_medida de la base y el de la app dicen lo mismo.
        val units = root.getJSONArray("unidades").objects()
            .associate { it.getString("codigo_unidad") to it.getBoolean("permite_decimales") }
        MeasureUnit.entries.forEach { require(units[it.name] == it.allowsFraction) }
        val categories = root.getJSONArray("categorias").objects()
            .associate { it.getLong("categoria_id") to it.getString("nombre_categoria") }
        val products = root.getJSONArray("productos").objects().map { item ->
            val count = item.optJSONObject("conciliacion_vigente")
            InventoryProduct(item.getString("codigo_sku"), item.getString("nombre_producto"),
                requireNotNull(categories[item.getLong("categoria_id")]), item.getString("ciclo_producto"),
                item.decimal("precio_venta_regular"), item.decimal("existencia_sistema"),
                count?.decimal("existencia_contada"),
                count?.let { it.decimal("existencia_sistema_al_contar") - it.decimal("existencia_contada") },
                count?.let { OffsetDateTime.parse(requireNotNull(it.nullableString("fecha_hora_conteo"))).toLocalDate().toString() },
                count?.getString("metodo_conteo"), MeasureUnit.valueOf(item.getString("codigo_unidad"))).also {
                require(it.sku.isNotBlank() && it.name.isNotBlank())
                require(CountRules.valid(it.theoretical, it.unit.allowsFraction))
                require(it.physical == null || CountRules.valid(it.physical, it.unit.allowsFraction))
                require(it.difference == null || CountRules.valid(it.difference.abs(), it.unit.allowsFraction))
                require((it.physical == null) == (it.countedAt == null))
                require(it.origin in setOf(null, "MANUAL", "FOTO", "CARGA"))
            }
        }
        require(products.isNotEmpty() && products.map { it.sku }.distinct().size == products.size)
        return InventoryCatalog(root.getJSONObject("comercio").getString("nombre_comercio"), root.getString("corte"), products)
    }
}
