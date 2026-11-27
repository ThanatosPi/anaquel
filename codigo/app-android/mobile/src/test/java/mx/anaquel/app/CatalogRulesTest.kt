package mx.anaquel.app

import mx.anaquel.app.domain.CatalogRules
import org.junit.Assert.*
import org.junit.Test

// categoria_unica, temporada_unica y BIT-015, dichas antes de guardar.
class CatalogRulesTest {
    @Test fun categoryNameIsRequiredAndUniqueIgnoringCase() {
        assertTrue(CatalogRules.checkCategory("Plomería", listOf("Pintura")).isEmpty())
        assertEquals(setOf("nombre"), CatalogRules.checkCategory(" ", emptyList()).keys)
        assertEquals("Ya hay una categoría con ese nombre.", CatalogRules.checkCategory(" pintura ", listOf("Pintura"))["nombre"])
    }

    @Test fun seasonDaysMustExistInTheirMonth() {
        assertTrue(CatalogRules.checkSeason("Regreso a clases", "15", "7", "31", "8", emptyList()).isEmpty())
        assertEquals("Ese día no existe en feb (hasta 29).", CatalogRules.checkSeason("X", "31", "2", "15", "3", emptyList())["inicio"])
        assertEquals(setOf("fin"), CatalogRules.checkSeason("X", "1", "3", "31", "4", emptyList()).keys)
        assertTrue(CatalogRules.checkSeason("Bisiesto", "29", "2", "31", "3", emptyList()).isEmpty())
        assertEquals(setOf("inicio"), CatalogRules.checkSeason("X", "1", "13", "1", "1", emptyList()).keys)
        assertEquals(setOf("inicio"), CatalogRules.checkSeason("X", "", "1", "1", "1", emptyList()).keys)
    }

    @Test fun aSeasonMayCrossTheYear() {
        assertTrue(CatalogRules.checkSeason("Fin de año", "1", "11", "15", "1", emptyList()).isEmpty())
    }

    @Test fun seasonNamesAreUnique() {
        assertEquals(setOf("nombre"), CatalogRules.checkSeason("lluvias", "1", "6", "30", "9", listOf("Lluvias")).keys)
    }
}
