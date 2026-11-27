package mx.anaquel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.domain.*
import java.math.BigDecimal

data class BusinessState(val loading: Boolean = true, val storageReady: Boolean = false,
    val catalog: InventoryCatalog? = null, val municipalities: List<Municipality> = emptyList(),
    val preferences: BusinessPreferences = BusinessPreferences(), val saving: Boolean = false,
    val error: String? = null, val formError: String? = null,
    val categories: List<Category> = emptyList(), val seasons: List<Season> = emptyList(),
    val productForm: ProductForm? = null, val invoices: List<InvoiceSummary> = emptyList(),
    val invoiceForm: InvoiceForm? = null, val sales: List<SaleSummary> = emptyList(), val saleForm: SaleForm? = null)

class BusinessViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BusinessRepository(application)
    private val mutableState = MutableStateFlow(BusinessState())
    val state = mutableState.asStateFlow()
    private var storageJob: Job? = null
    private var loadJob: Job? = null
    private var listsJob: Job? = null
    private var salesJob: Job? = null
    init { load() }

    fun load() {
        storageJob?.cancel(); loadJob?.cancel(); listsJob?.cancel()
        mutableState.update { it.copy(loading = true, storageReady = false, error = null) }
        storageJob = viewModelScope.launch {
            repository.preferences.catch {
                mutableState.update { it.copy(error = "No pudimos leer los datos guardados del negocio.", storageReady = false) }
            }.collect { preferences -> mutableState.update { it.copy(preferences = preferences, storageReady = true) } }
        }
        salesJob?.cancel()
        salesJob = viewModelScope.launch {
            try { repository.sales.collect { sales -> mutableState.update { it.copy(sales = sales) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(error = "No pudimos cargar las ventas del negocio.") } }
        }
        listsJob = viewModelScope.launch {
            try {
                combine(repository.categories, repository.seasons, repository.invoices) { categories, seasons, invoices ->
                    Triple(categories, seasons, invoices)
                }.collect { (categories, seasons, invoices) ->
                    mutableState.update { it.copy(categories = categories, seasons = seasons, invoices = invoices) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(error = "No pudimos cargar las categorías del negocio.") } }
        }
        // Desde 0.4.0 el catálogo sale de la base local: un conteo cambia la existencia del sistema,
        // así que se observa en vez de leerse una vez.
        loadJob = viewModelScope.launch {
            try {
                val municipalities = repository.municipalities()
                repository.catalog.collect { catalog ->
                    mutableState.update { it.copy(loading = false, catalog = catalog, municipalities = municipalities) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "No pudimos cargar el catálogo del negocio.") } }
        }
    }

    // explain: si la operación rechaza con IllegalArgumentException, se muestra su motivo (p. ej. un
    // código repetido) en vez del aviso genérico.
    private fun save(onSaved: () -> Unit, explain: Boolean = false, operation: suspend () -> Unit) {
        if (mutableState.value.saving || !mutableState.value.storageReady) return
        mutableState.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try { operation(); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) {
                mutableState.update { it.copy(formError = if (explain) e.message else "No se guardó el cambio. Vuelve a intentarlo.") }
            }
            catch (_: Exception) { mutableState.update { it.copy(formError = "No se guardó el cambio. Vuelve a intentarlo.") } }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }

    fun askLocationPermission(onReady: () -> Unit) = save(onReady) { repository.markPermissionAsked() }
    fun saveLocation(location: BusinessLocation, onSaved: () -> Unit) = save(onSaved) { repository.saveLocation(location) }
    // sku nulo = producto nuevo. El formulario queda en el estado para sobrevivir a una rotación.
    fun openProduct(sku: String?, onReady: () -> Unit) {
        mutableState.update { it.copy(productForm = null, formError = null) }
        viewModelScope.launch {
            val form = if (sku == null) ProductForm() else repository.productDraft(sku)?.let(ProductRules::toForm)
            mutableState.update { it.copy(productForm = form) }
            if (form != null) onReady()
        }
    }

    fun saveProduct(form: ProductForm, onSaved: () -> Unit) {
        val zone = mutableState.value.preferences.location?.zone ?: return
        save(onSaved, explain = true) { repository.saveProduct(ProductRules.toDraft(form, zone)) }
    }

    fun saveCategory(id: Long?, name: String, affinity: String?, onSaved: () -> Unit) {
        val zone = mutableState.value.preferences.location?.zone
        save(onSaved, explain = true) { repository.saveCategory(id, name, affinity, zone) }
    }

    fun saveSeason(id: Long?, name: String, startDay: Int, startMonth: Int, endDay: Int, endMonth: Int, onSaved: () -> Unit) =
        save(onSaved, explain = true) { repository.saveSeason(id, name, startDay, startMonth, endDay, endMonth) }

    fun clearFormError() = mutableState.update { it.copy(formError = null) }

    // La factura en captura vive aquí (y no en la pantalla): sus renglones sobreviven a una rotación.
    fun startInvoice() = mutableState.update {
        it.copy(formError = null, invoiceForm = InvoiceForm(java.util.UUID.randomUUID().toString(), issuedOn = java.time.LocalDate.now().toString()))
    }

    fun editInvoice(change: (InvoiceForm) -> InvoiceForm) = mutableState.update { state ->
        state.copy(invoiceForm = state.invoiceForm?.let(change))
    }

    fun discardInvoice() {
        mutableState.value.invoiceForm?.photoPath?.let { java.io.File(getApplication<Application>().filesDir, it).delete() }
        mutableState.update { it.copy(invoiceForm = null, formError = null) }
    }

    fun receiveInvoice(onSaved: () -> Unit) {
        val form = mutableState.value.invoiceForm ?: return
        save({ mutableState.update { it.copy(invoiceForm = null) }; onSaved() }, explain = true) {
            repository.receiveInvoice(InvoiceRules.toDraft(form))
        }
    }

    fun startSale() = mutableState.update {
        it.copy(formError = null, saleForm = SaleForm(java.util.UUID.randomUUID().toString(),
            soldAt = java.time.LocalDateTime.now().format(SaleRules.FORMAT)))
    }

    fun editSale(change: (SaleForm) -> SaleForm) = mutableState.update { state -> state.copy(saleForm = state.saleForm?.let(change)) }

    fun discardSale() = mutableState.update { it.copy(saleForm = null, formError = null) }

    fun recordSale(onSaved: () -> Unit) {
        val form = mutableState.value.saleForm ?: return
        save({ mutableState.update { it.copy(saleForm = null) }; onSaved() }, explain = true) {
            repository.recordSale(SaleRules.toDraft(form))
        }
    }

    fun saveCount(id: String, sku: String, quantity: BigDecimal, onSaved: () -> Unit) {
        if (mutableState.value.catalog == null) return
        save(onSaved) { repository.count(id, sku, quantity) }
    }
}
