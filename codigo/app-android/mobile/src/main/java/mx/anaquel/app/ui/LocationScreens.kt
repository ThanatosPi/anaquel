package mx.anaquel.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.anaquel.app.BusinessState
import mx.anaquel.app.data.PhoneLocation
import mx.anaquel.app.domain.*
import java.time.OffsetDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LocationSetup(state: BusinessState, onAskPermission: (() -> Unit) -> Unit,
    onSave: (BusinessLocation) -> Unit, onCancel: (() -> Unit)? = null) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var stage by rememberSaveable { mutableStateOf("intro") }
    // Desde 0.4.0 se elige el municipio del catálogo (municipio_zona_climatica) y la zona sale de él.
    var chosenState by rememberSaveable { mutableStateOf(state.preferences.location?.state.orEmpty()) }
    var chosenId by rememberSaveable { mutableLongStateOf(state.preferences.location?.municipalityId ?: -1L) }
    var candidateIndex by rememberSaveable { mutableIntStateOf(-1) }
    var candidateLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var candidateLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var explanation by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (stage == "intro") {
            stage = if (granted) "locating" else "manual"
            if (!granted) explanation = "Puedes elegir el municipio sin compartir tu ubicación."
        }
    }
    LaunchedEffect(stage) {
        if (stage == "manual") { candidateLatitude = null; candidateLongitude = null }
        if (stage == "locating") {
            val point = PhoneLocation.current(context)
            val municipality = point?.let { ZoneResolver.resolve(it.latitude, it.longitude, state.municipalities) }
            if (municipality == null) {
                explanation = "No pudimos ubicar el negocio en un municipio del catálogo. Elígelo a mano."
                stage = "manual"
            } else {
                candidateIndex = state.municipalities.indexOf(municipality)
                candidateLatitude = point.latitude
                candidateLongitude = point.longitude
                stage = "confirm"
            }
        }
    }
    fun cancel() {
        keyboard?.hide()
        if (onCancel != null) onCancel() else stage = "intro"
    }
    BackHandler(enabled = stage != "intro" || onCancel != null) { if (!state.saving) cancel() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(24.dp).testTag("ubicacion"),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Outlined.LocationOn, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text("¿Dónde está tu negocio?", style = MaterialTheme.typography.headlineLarge)
        if (state.preferences.locationNeedsConfirmation) Text("Confirma de nuevo la ubicación del negocio: el registro anterior no guardó las coordenadas. Tus conteos y decisiones se conservan.")
        if (state.preferences.locationNeedsMunicipality) Text("Ahora el negocio guarda su municipio, no sólo la zona. Elígelo de la lista; tus conteos y decisiones se conservan.")
        when (stage) {
            "intro" -> {
                Text("Lo uso para saber el clima de tu zona. Una chamarra no se vende igual en Mérida que en Toluca. Guardo la ubicación del negocio, no la tuya, y la puedes cambiar cuando quieras.")
                Text("Si confirmas la ubicación, guardo una sola vez las coordenadas aproximadas del negocio. Puedes borrarlas eligiendo el municipio a mano.")
                Button(onClick = {
                    if (PhoneLocation.permitted(context)) stage = "locating"
                    else if (state.preferences.locationPermissionAsked) {
                        explanation = "Puedes elegir el municipio sin compartir tu ubicación."; stage = "manual"
                    } else onAskPermission { if (stage == "intro") permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("usar_ubicacion")) { Text("Usar mi ubicación") }
                OutlinedButton(onClick = { stage = "manual" }, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("zona_manual")) { Text("Elegir el municipio a mano") }
            }
            "locating" -> {
                CircularProgressIndicator()
                Text("Buscando la ubicación del negocio…")
                TextButton(onClick = { stage = "manual" }) { Text("Elegir el municipio a mano") }
            }
            "confirm" -> {
                val candidate = state.municipalities.getOrNull(candidateIndex)
                if (candidate != null && candidateLatitude != null && candidateLongitude != null) {
                    Text("Tu negocio está en ${candidate.name}, ${candidate.state} — zona ${candidate.zone.label}",
                        style = MaterialTheme.typography.titleLarge)
                    Text("Confirma que ésta es la ubicación de tu negocio.")
                    Button(onClick = { onSave(BusinessLocation(candidate.zone, candidate.name, candidate.state, "GPS", OffsetDateTime.now().toString(), candidateLatitude, candidateLongitude, candidate.id)) },
                        enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("confirmar_ubicacion")) { Text("Está bien") }
                    TextButton(onClick = { stage = "manual" }, enabled = !state.saving) { Text("Elegir otro municipio") }
                } else TextButton(onClick = { stage = "manual" }) { Text("Elegir el municipio a mano") }
            }
            "manual" -> {
                if (explanation.isNotBlank()) Text(explanation)
                Text("¿En qué estado está?", style = MaterialTheme.typography.titleLarge)
                val states = remember(state.municipalities) { state.municipalities.map { it.state }.distinct().sorted() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    states.forEach { name ->
                        FilterChip(chosenState == name, { if (chosenState != name) { chosenState = name; chosenId = -1L } },
                            label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("entidad_$name"))
                    }
                }
                if (chosenState.isNotEmpty()) {
                    Text("¿En qué municipio o alcaldía?", style = MaterialTheme.typography.titleLarge)
                    state.municipalities.filter { it.state == chosenState }.sortedBy { it.name }.forEach { city ->
                        OutlinedCard(onClick = { chosenId = city.id }, modifier = Modifier.fillMaxWidth().testTag("municipio_${city.id}")) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(chosenId == city.id, onClick = null)
                                Column { Text(city.name, style = MaterialTheme.typography.titleMedium); Text("Clima ${city.zone.label.lowercase()} · ${city.zone.help}") }
                            }
                        }
                    }
                }
                val chosen = state.municipalities.find { it.id == chosenId && it.state == chosenState }
                Button(onClick = { keyboard?.hide(); chosen?.let { onSave(BusinessLocation(it.zone, it.name, it.state, "MANUAL", OffsetDateTime.now().toString(), municipalityId = it.id)) } },
                    enabled = chosen != null && !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("guardar_zona")) { Text("Guardar y continuar") }
            }
        }
        state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (onCancel != null) TextButton(onClick = ::cancel, enabled = !state.saving) { Text("Cancelar") }
    }
}

@Composable fun SettingsScreen(state: BusinessState, onEdit: () -> Unit, onCatalog: () -> Unit = {}) {
    val location = state.preferences.location
    // La versión se lee del paquete instalado: el texto fijo se quedó en «0.2.2» al publicar 0.3.0.
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("ajustes"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Tu negocio", style = MaterialTheme.typography.headlineLarge)
        Text(state.catalog?.business.orEmpty())
        Panel {
            SectionTitle("Ubicación del negocio")
            Text(location?.let { "${it.municipality}, ${it.state}" } ?: "Sin configurar", style = MaterialTheme.typography.titleLarge)
            location?.let { Text("Zona climática: ${it.zone.label}") }
            Text(if (location?.origin == "GPS") "Origen: ubicación del teléfono" else "Origen: lo elegiste tú")
            Text(if (location?.origin == "GPS") "Ubicación aproximada guardada. Cambiar a modo manual borra las coordenadas." else "No hay coordenadas guardadas.")
            OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp).testTag("editar_zona")) { Text("Cambiar municipio") }
        }
        Panel {
            SectionTitle("Catálogo del negocio", "${state.categories.size} categorías · ${state.seasons.size} temporadas")
            Text("Las categorías llevan la afinidad con el clima; las temporadas, la ventana del año en que se vende un producto estacional.")
            OutlinedButton(onClick = onCatalog, modifier = Modifier.heightIn(min = 56.dp).testTag("abrir_catalogo")) { Text("Categorías y temporadas") }
        }
        Panel {
            SectionTitle("Datos guardados en este teléfono")
            Text("El municipio, los conteos y las decisiones quedan en la base de este teléfono, con las tablas del esquema v2. Las corridas que ya recibiste conservan sus datos originales.")
            val count = state.preferences.counts.size
            Text(if (count == 1) "1 registro en el historial local" else "$count registros en el historial local")
            val invalid = state.catalog?.productsNeedingCountCorrection(state.preferences.counts) ?: 0
            if (invalid > 0) Text(
                (if (invalid == 1) "1 producto requiere" else "$invalid productos requieren") +
                    " corrección de cantidad. Los registros anteriores se conservan en el historial.")
            Text("Los conteos válidos están pendientes de enviar.")
        }
        Text("Anaquel · Demostración $version", style = MaterialTheme.typography.bodySmall)
    }
}
