package mx.anaquel.app.ui

import mx.anaquel.app.domain.MarketReference
import mx.anaquel.app.domain.PriceQuote
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Geometría del control de precio. Ningún precio que devuelve iguala el mínimo ni el original
 * (BIT-003); los escalones son los de 10 % que caben estrictamente en el rango (BIT-011).
 */
class PriceScale(quote: PriceQuote) {
    private val half = BigDecimal("0.50")
    val min: BigDecimal = quote.minimum
    val max: BigDecimal = quote.original
    val steps: List<BigDecimal> = (1..9)
        .map { k -> max.multiply(BigDecimal(10 - k)).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP) }
        .filter { it > min && it < max }
    /** El precio de $0.50 más bajo y el más alto que el control puede elegir. */
    val low: BigDecimal = min.divide(half, 0, RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(half).setScale(2)
    val high: BigDecimal = max.divide(half, 0, RoundingMode.CEILING).subtract(BigDecimal.ONE).multiply(half).setScale(2)
    val usable: Boolean = low <= high

    fun fraction(price: BigDecimal): Float =
        price.subtract(min).divide(max.subtract(min), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)

    fun priceAt(fraction: Float): BigDecimal =
        min.add(max.subtract(min).multiply(BigDecimal(fraction.coerceIn(0f, 1f).toDouble())))

    /** Se pega al escalón si queda a menos de 3 % del rango; si no, redondea a $0.50. */
    fun snap(raw: BigDecimal): BigDecimal {
        val tolerance = max.subtract(min).multiply(BigDecimal("0.03"))
        val near = steps.minByOrNull { it.subtract(raw).abs() }
        if (near != null && near.subtract(raw).abs() <= tolerance) return near
        return raw.divide(half, 0, RoundingMode.HALF_UP).multiply(half).setScale(2).coerceIn(low, high)
    }

    fun next(price: BigDecimal): BigDecimal = steps.filter { it > price }.minOrNull() ?: if (price < high) high else price
    fun previous(price: BigDecimal): BigDecimal = steps.filter { it < price }.maxOrNull() ?: if (price > low) low else price
}

fun discountPercent(price: BigDecimal, original: BigDecimal): String =
    BigDecimal.ONE.subtract(price.divide(original, 6, RoundingMode.HALF_UP)).multiply(BigDecimal(100))
        .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** El precio público vigente más reciente, si hay uno (BIT-002). */
fun currentMarket(references: List<MarketReference>): BigDecimal? =
    references.filter { it.valid }.maxByOrNull { it.captured }?.price

fun priceNote(quote: PriceQuote, market: BigDecimal?, price: BigDecimal): String = when {
    price.compareTo(quote.proposed) == 0 -> "Es lo que propongo: escalón de ${quote.step * 10} %."
    price < quote.proposed -> "Más abajo de lo que propongo. El mínimo es un piso, no una meta."
    market != null && price > market -> "Arriba del precio público de referencia (${money(market)}). Puede tardar más en venderse."
    else -> "Más arriba de lo que propongo, todavía dentro del rango."
}
