package mx.anaquel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mx.anaquel.app.data.local.AnaquelDb
import mx.anaquel.app.data.local.LocalStore
import mx.anaquel.app.data.local.Seed
import java.time.OffsetDateTime

// Deja lista la base local una sola vez por proceso, aunque las dos pantallas la pidan a la vez:
// la siembra con catalogo.json y municipios.json si está vacía y migra lo que 0.3.0 guardaba en
// DataStore. Las llaves de 0.3.0 no se borran (BIT-023): si algo saliera mal, siguen ahí.
object LocalSetup {
    private val mutex = Mutex()
    @Volatile private var store: LocalStore? = null
    internal val legacyLocation = stringPreferencesKey("negocio_ubicacion_v1")
    internal val legacyCounts = stringPreferencesKey("conteos_locales_v1")
    internal val legacyDecisions = stringPreferencesKey("decisiones_v1")
    private val migrated = booleanPreferencesKey("migracion_room_v1")
    internal val pendingLocation = stringPreferencesKey("ubicacion_por_confirmar_v2")

    suspend fun ready(context: Context): LocalStore = store ?: mutex.withLock {
        store ?: withContext(Dispatchers.IO) {
            val app = context.applicationContext
            fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
            val local = LocalStore(AnaquelDb.get(app))
            if (!local.isSeeded()) local.seed(Seed.parse(asset("catalogo.json"), asset("municipios.json"),
                OffsetDateTime.now().toString()))
            if (app.decisionStore.data.first()[migrated] != true) migrate(app, local)
            local.also { store = it }
        }
    }

    private suspend fun migrate(app: Context, local: LocalStore) {
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        val preferences = app.decisionStore.data.first()
        val runs = listOf("corrida.json", "corrida_2.json").map { RunParser.parse(asset(it)) }
        val reason = local.migrate(preferences[legacyLocation], preferences[legacyCounts], preferences[legacyDecisions], runs)
        app.decisionStore.edit {
            it[migrated] = true
            if (reason != null) it[pendingLocation] = reason
        }
    }

    // Para las pruebas instrumentadas: vacía la base local y la vuelve a sembrar, como recién instalada.
    internal suspend fun resetForTests(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        val db = AnaquelDb.get(app)
        db.clearAllTables()
        LocalStore(db).seed(Seed.parse(asset("catalogo.json"), asset("municipios.json"), OffsetDateTime.now().toString()))
    }

    // Para las pruebas que preparan llaves de 0.3.0 con la app ya abierta: migra lo que haya ahora.
    internal suspend fun migrateNow(context: Context) = withContext(Dispatchers.IO) {
        migrate(context.applicationContext, ready(context))
    }
}
