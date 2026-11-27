package mx.anaquel.app.domain

import java.math.BigDecimal
import kotlin.math.*

enum class ClimateZone(val label: String, val help: String) {
    TROPICAL("Tropical", "Calor y humedad todo el año"),
    TEMPLADO("Templado", "Temperaturas moderadas, con estaciones"),
    FRIO("Frío", "Temperaturas bajas durante buena parte del año"),
    ARIDO("Árido", "Calor seco y muy poca lluvia"),
    SEMIARIDO("Semiárido", "Poca lluvia y cambios de temperatura")
}

data class Municipality(val state: String, val name: String, val zone: ClimateZone,
    val latitude: Double, val longitude: Double, val radiusKm: Double, val id: Long = 0)

object ZoneResolver {
    fun distanceKm(lat: Double, lon: Double, municipality: Municipality): Double {
        val a = Math.toRadians(lat)
        val b = Math.toRadians(municipality.latitude)
        val dLat = b - a
        val dLon = Math.toRadians(municipality.longitude - lon)
        val h = sin(dLat / 2).pow(2) + cos(a) * cos(b) * sin(dLon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun resolve(lat: Double, lon: Double, municipalities: List<Municipality>): Municipality? {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        // En empate exacto, conservar el orden recibido; no redondear distancias.
        return municipalities.map { it to distanceKm(lat, lon, it) }
            .filter { (city, distance) -> distance <= city.radiusKm }
            .minByOrNull { it.second }?.first
    }
}

// municipalityId es el municipio_zona_id del catálogo (v2); vacío sólo en el registro de 0.3.0.
data class BusinessLocation(val zone: ClimateZone, val municipality: String, val state: String,
    val origin: String, val savedAt: String, val latitude: Double? = null, val longitude: Double? = null,
    val municipalityId: Long? = null) {
    val valid: Boolean get() = when (origin) {
        "GPS" -> municipality.isNotBlank() && state.isNotBlank() && latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
        "MANUAL" -> latitude == null && longitude == null
        else -> false
    }
}

data class InventoryProduct(val sku: String, val name: String, val category: String,
    val cycle: String, val price: BigDecimal, val theoretical: BigDecimal,
    val physical: BigDecimal?, val difference: BigDecimal?, val countedAt: String?, val origin: String?, val unit: MeasureUnit) {
    fun confirmedQuantity(counts: List<StockCount>): BigDecimal? {
        val latest = counts.lastOrNull { it.sku == sku } ?: return physical
        return latest.physical.takeIf { latest.confirmed && CountRules.valid(it, unit.allowsFraction) }
    }
}

// products son los ACTIVOS; los descontinuados se guardan aparte y no cuentan en el inventario.
data class InventoryCatalog(val business: String, val date: String, val products: List<InventoryProduct>,
    val discontinued: List<InventoryProduct> = emptyList()) {
    fun productsNeedingCountCorrection(counts: List<StockCount>): Int {
        val productsBySku = products.associateBy { it.sku }
        return counts.groupBy { it.sku }.count { (sku, history) ->
            productsBySku[sku]?.confirmedQuantity(history) == null
        }
    }
}

// systemAtCount: lo que decía el sistema al contar (existencia_sistema_al_contar); vacío en 0.3.0.
data class StockCount(val id: String, val sku: String, val physical: BigDecimal,
    val origin: String, val confirmed: Boolean, val countedAt: String, val systemAtCount: BigDecimal? = null)

data class BusinessPreferences(val location: BusinessLocation? = null,
    val counts: List<StockCount> = emptyList(), val locationPermissionAsked: Boolean = false,
    val locationNeedsConfirmation: Boolean = false, val locationNeedsMunicipality: Boolean = false)

object CountRules {
    fun parse(text: String, allowsFraction: Boolean = true): BigDecimal? = text.trim().takeIf {
        Regex("\\d{1,9}([.,]\\d{1,3})?").matches(it)
    }?.replace(',', '.')?.toBigDecimalOrNull()?.takeIf { valid(it, allowsFraction) }

    fun valid(quantity: BigDecimal, allowsFraction: Boolean = true): Boolean = quantity >= BigDecimal.ZERO &&
        quantity <= BigDecimal("999999999.999") && quantity.stripTrailingZeros().scale() <= if (allowsFraction) 3 else 0

    // Igual que el catálogo: existencia teórica menos existencia física.
    fun difference(theoretical: BigDecimal, physical: BigDecimal): BigDecimal = theoretical - physical
}
