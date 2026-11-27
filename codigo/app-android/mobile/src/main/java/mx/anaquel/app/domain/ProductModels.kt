package mx.anaquel.app.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Category(val id: Long, val name: String, val affinity: String?, val affinityZone: String?)

// BIT-015: día y mes sin año; la de fin de año cruza diciembre → enero.
data class Season(val id: Long, val name: String, val startDay: Int, val startMonth: Int, val endDay: Int, val endMonth: Int) {
    val label: String get() = "$name · $startDay ${MONTHS[startMonth - 1]} – $endDay ${MONTHS[endMonth - 1]}"
    companion object {
        val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    }
}

val CYCLES = listOf("PERMANENTE", "ESTACIONAL", "PERECEDERO")
val AFFINITIES = listOf("ALTA", "MEDIA", "BAJA", "NO_APLICA")

// Un renglón de producto tal como lo captura el dueño. id nulo = producto nuevo. La existencia sólo
// se captura al darlo de alta: después la mueven los conteos, las facturas y las ventas (BIT-023).
data class ProductDraft(
    val id: Long?, val sku: String, val name: String, val categoryId: Long?, val unit: MeasureUnit,
    val cycle: String, val seasonId: Long?, val expiresOn: String?, val affinity: String?,
    val affinityZone: ClimateZone?, val price: BigDecimal, val cost: BigDecimal?, val minStock: Int?,
    val maxStock: Int?, val initialStock: BigDecimal, val active: Boolean,
)

// El formulario como lo escribe la persona: todo texto hasta que pasa las reglas.
data class ProductForm(
    val id: Long? = null, val sku: String = "", val name: String = "", val categoryId: Long? = null,
    val unit: MeasureUnit = MeasureUnit.PIEZA, val cycle: String = "PERMANENTE", val seasonId: Long? = null,
    val expiresOn: String = "", val affinity: String? = null, val price: String = "", val cost: String = "",
    val minStock: String = "", val maxStock: String = "", val initialStock: String = "0", val active: Boolean = true,
)

// Las mismas reglas que defiende producto en db/schema.sql, para decirlas antes de guardar y junto
// al campo: producto_sku_unico, producto_precio_positivo, producto_costo_no_negativo,
// producto_stock_valido, producto_estacional_con_temporada, producto_perecedero_con_caducidad,
// producto_afinidad_con_zona y, por la unidad, BIT-017.
object ProductRules {
    private val SKU = Regex("[A-Z0-9][A-Z0-9-]{0,39}")

    fun normalizeSku(text: String) = text.trim().uppercase()

    fun parseWhole(text: String): Int? = text.trim().takeIf { Regex("\\d{1,7}").matches(it) }?.toInt()

    fun check(form: ProductForm, takenSkus: Set<String>): Map<String, String> = buildMap {
        val sku = normalizeSku(form.sku)
        if (form.id == null) when {
            sku.isEmpty() -> put("sku", "Escribe el código del producto.")
            !SKU.matches(sku) -> put("sku", "Usa letras, números y guiones; hasta 40.")
            sku in takenSkus -> put("sku", "Ya hay un producto con ese código.")
        }
        if (form.name.isBlank()) put("nombre", "Escribe el nombre del producto.")
        else if (form.name.trim().length > 120) put("nombre", "El nombre es muy largo (hasta 120).")
        if (form.categoryId == null) put("categoria", "Elige una categoría.")
        if (form.cycle == "ESTACIONAL" && form.seasonId == null) put("temporada", "Un producto de temporada necesita su temporada.")
        if (form.cycle == "PERECEDERO" && runCatching { LocalDate.parse(form.expiresOn.trim()) }.isFailure)
            put("caducidad", "Escribe la fecha de caducidad como AAAA-MM-DD.")
        val price = DecisionRules.parsePrice(form.price)
        if (price == null || price.signum() <= 0) put("precio", "Escribe un precio mayor que cero, con hasta dos decimales.")
        if (form.cost.isNotBlank() && DecisionRules.parsePrice(form.cost) == null) put("costo", "Escribe el costo con hasta dos decimales, o déjalo vacío.")
        val min = form.minStock.takeIf(String::isNotBlank)?.let { parseWhole(it) ?: -1 }
        val max = form.maxStock.takeIf(String::isNotBlank)?.let { parseWhole(it) ?: -1 }
        if (min == -1 || max == -1) put("stock", "Los stocks mínimo y máximo son números enteros.")
        else if (min != null && max != null && min > max) put("stock", "El mínimo no puede ser mayor que el máximo.")
        if (form.id == null && CountRules.parse(form.initialStock, form.unit.allowsFraction) == null)
            put("existencia", if (form.unit.allowsFraction) "Escribe una existencia no negativa con hasta tres decimales."
                else "Esta unidad requiere una existencia entera no negativa.")
    }

    // Sólo se llama si check() no encontró errores. Lo que no aplica al ciclo se limpia.
    fun toDraft(form: ProductForm, zone: ClimateZone): ProductDraft = ProductDraft(
        form.id, normalizeSku(form.sku), form.name.trim(), requireNotNull(form.categoryId), form.unit, form.cycle,
        form.seasonId.takeIf { form.cycle == "ESTACIONAL" }, form.expiresOn.trim().takeIf { form.cycle == "PERECEDERO" },
        form.affinity, zone.takeIf { form.affinity != null }, requireNotNull(DecisionRules.parsePrice(form.price)),
        form.cost.takeIf(String::isNotBlank)?.let(DecisionRules::parsePrice), form.minStock.takeIf(String::isNotBlank)?.let(::parseWhole),
        form.maxStock.takeIf(String::isNotBlank)?.let(::parseWhole),
        if (form.id == null) requireNotNull(CountRules.parse(form.initialStock, form.unit.allowsFraction)) else BigDecimal.ZERO,
        form.active)

    fun toForm(draft: ProductDraft): ProductForm = ProductForm(
        draft.id, draft.sku, draft.name, draft.categoryId, draft.unit, draft.cycle, draft.seasonId, draft.expiresOn.orEmpty(),
        draft.affinity, draft.price.toPlainString(), draft.cost?.toPlainString().orEmpty(), draft.minStock?.toString().orEmpty(),
        draft.maxStock?.toString().orEmpty(), draft.initialStock.stripTrailingZeros().toPlainString(), draft.active)
}
