package mx.anaquel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.anaquel.app.data.DecisionRepository
import mx.anaquel.app.data.RunParser
import mx.anaquel.app.domain.*
import java.math.BigDecimal

data class AppState(
    val loading: Boolean = true, val storageReady: Boolean = false,
    val runs: List<AnalysisRun> = emptyList(), val decisions: List<LocalDecision> = emptyList(),
    val seen: Set<String> = emptySet(), val error: String? = null, val busy: Boolean = false
)
data class DecisionEvent(val message: String, val keys: Set<String> = emptySet(), val decidedIds: Set<Long> = emptySet())

class AnaquelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DecisionRepository(application)
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<DecisionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var storageObserver: Job? = null

    init { loadRuns() }

    private fun observeStorage() {
        storageObserver?.cancel()
        storageObserver = viewModelScope.launch {
            combine(repository.decisions, repository.seen) { decisions, seen -> decisions to seen }
                .catch {
                    mutableState.update { state -> state.copy(error = "No pudimos leer tus decisiones guardadas.", storageReady = false) }
                }.collect { (decisions, seen) ->
                    mutableState.update { it.copy(decisions = decisions, seen = seen, storageReady = true) }
                }
        }
    }

    fun loadRuns() {
        mutableState.update { it.copy(loading = true, error = null, storageReady = false) }
        observeStorage()
        viewModelScope.launch {
            try {
                val runs = withContext(Dispatchers.IO) {
                    listOf("corrida.json", "corrida_2.json").map { name ->
                        getApplication<Application>().assets.open(name).bufferedReader().use { RunParser.parse(it.readText()) }
                    }
                }
                mutableState.update { it.copy(loading = false, runs = runs) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "No pudimos traer la corrida.") }
            }
        }
    }

    fun decide(runId: Long, ids: Set<Long>, decision: String, price: BigDecimal?, reason: String?, marketRisk: Boolean) {
        if (mutableState.value.busy || !mutableState.value.storageReady) return
        val run = mutableState.value.runs.singleOrNull { it.id == runId } ?: return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.decide(run, ids, decision, price, reason, marketRisk)
                eventChannel.send(DecisionEvent(
                    if (decision == "APROBADA") "Autorizada" else "Rechazada",
                    ids.map { "$runId:$it" }.toSet(), ids))
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) {
                eventChannel.send(DecisionEvent(e.message ?: "Revisa la decisión antes de continuar."))
            } catch (_: Exception) {
                eventChannel.send(DecisionEvent("No se guardó la decisión. Vuelve a intentarlo."))
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun undo(keys: Set<String>) = viewModelScope.launch {
        try { repository.undo(keys) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { eventChannel.send(DecisionEvent("No pudimos deshacer la decisión.")) }
    }

    fun markSeen(runId: Long, id: Long) = viewModelScope.launch {
        try { repository.markSeen(runId, id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { eventChannel.send(DecisionEvent("No pudimos guardar que ya la viste.")) }
    }
}
