package mx.anaquel.app.domain

// Las reglas de categoria y temporada en db/schema.sql, dichas antes de guardar: el nombre es único
// dentro del comercio (categoria_unica, temporada_unica), la afinidad siempre va con su zona
// (categoria_afinidad_con_zona) y el día tiene que existir en su mes (BIT-015; el 29 de febrero sí,
// y en año no bisiesto el motor lo lee como 28). Una temporada puede cruzar el año (fin de año).
object CatalogRules {
    fun lastDay(month: Int) = when (month) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }

    private fun sameName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    // takenNames: los nombres de las OTRAS categorías del comercio (sin la que se edita).
    fun checkCategory(name: String, takenNames: List<String>): Map<String, String> = buildMap {
        when {
            name.isBlank() -> put("nombre", "Escribe el nombre de la categoría.")
            name.trim().length > 60 -> put("nombre", "El nombre es muy largo (hasta 60).")
            takenNames.any { sameName(it, name) } -> put("nombre", "Ya hay una categoría con ese nombre.")
        }
    }

    fun checkSeason(name: String, startDay: String, startMonth: String, endDay: String, endMonth: String,
        takenNames: List<String>): Map<String, String> = buildMap {
        when {
            name.isBlank() -> put("nombre", "Escribe el nombre de la temporada.")
            name.trim().length > 60 -> put("nombre", "El nombre es muy largo (hasta 60).")
            takenNames.any { sameName(it, name) } -> put("nombre", "Ya hay una temporada con ese nombre.")
        }
        listOf("inicio" to (startDay to startMonth), "fin" to (endDay to endMonth)).forEach { (field, value) ->
            val month = value.second.trim().toIntOrNull()
            val day = value.first.trim().toIntOrNull()
            when {
                month == null || month !in 1..12 -> put(field, "El mes va del 1 al 12.")
                day == null || day !in 1..lastDay(month) -> put(field, "Ese día no existe en ${Season.MONTHS[month - 1]} (hasta ${lastDay(month)}).")
            }
        }
    }
}
