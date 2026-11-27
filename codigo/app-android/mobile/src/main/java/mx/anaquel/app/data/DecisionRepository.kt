package mx.anaquel.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import mx.anaquel.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

val Context.decisionStore by preferencesDataStore(name = "anaquel_decisiones")

// Desde 0.4.0 las decisiones son renglones de recomendacion en la base local (columnas de la
// decisión y de MSG-018 H2). DataStore se queda con qué abstenciones ya vio el dueño.
class DecisionRepository(private val context: Context) {
    private val seenKey = stringPreferencesKey("abstenciones_vistas_v1")
    val decisions: Flow<List<LocalDecision>> = flow { emitAll(LocalSetup.ready(context).decisions()) }
    val seen = context.decisionStore.data.map { it[seenKey]?.split('|')?.toSet() ?: emptySet() }

    suspend fun decide(
        run: AnalysisRun, ids: Set<Long>, decision: String,
        price: BigDecimal? = null, reason: String? = null, acceptMarketRisk: Boolean = false
    ) = LocalSetup.ready(context).decide(run, ids, decision, price, reason, acceptMarketRisk, OffsetDateTime.now().toString())

    suspend fun undo(keys: Set<String>) = LocalSetup.ready(context).undo(keys)

    suspend fun markSeen(runId: Long, recommendationId: Long) {
        context.decisionStore.edit {
            val keys = it[seenKey]?.split('|')?.filter(String::isNotEmpty)?.toSet() ?: emptySet()
            it[seenKey] = (keys + "$runId:$recommendationId").joinToString("|")
        }
    }

    // El formato de 0.3.0 en DataStore; sólo lo leen la migración y las pruebas.
    companion object {
        internal fun encode(decisions: List<LocalDecision>): String = JSONArray().apply {
            decisions.forEach { d -> put(JSONObject().apply {
                put("corrida_id", d.runId); put("recomendacion_id", d.recommendationId)
                put("decision", d.decision); put("precio", d.price?.toPlainString() ?: JSONObject.NULL)
                put("motivo", d.reason ?: JSONObject.NULL); put("fecha", d.date)
                put("actor_local", d.actorId); put("acepta_mercado_vencido", d.marketRiskAccepted)
            }) }
        }.toString()

        internal fun decode(text: String?): List<LocalDecision> =
            if (text == null) emptyList() else JSONArray(text).objects().map {
                LocalDecision(it.getLong("corrida_id"), it.getLong("recomendacion_id"),
                    it.getString("decision"), it.optionalDecimal("precio"), it.nullableString("motivo"),
                    it.getString("fecha"), it.getString("actor_local"), it.getBoolean("acepta_mercado_vencido"))
            }
    }
}
