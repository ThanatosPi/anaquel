package mx.anaquel.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.anaquel.app.data.DecisionRepository
import mx.anaquel.app.data.RunParser
import mx.anaquel.app.data.decisionStore
import mx.anaquel.app.data.BusinessRepository
import mx.anaquel.app.data.LocalSetup
import mx.anaquel.app.domain.BusinessLocation
import mx.anaquel.app.domain.ClimateZone
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun cleanLocalDecisions() {
        runBlocking {
            context.decisionStore.edit { it.clear() }
            LocalSetup.resetForTests(context)
            BusinessRepository(context).saveLocation(BusinessLocation(ClimateZone.TROPICAL, "Mérida", "Yucatán", "MANUAL",
                "2026-09-23T14:00:00-06:00", municipalityId = 1))
        }
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Revisar recomendaciones").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val suffix = if (context.resources.configuration.fontScale >= 1.9f) "-200" else ""
        // El runner desinstala la app al terminar: conservar evidencia fuera de sus datos.
        val commands = listOf("mkdir -p /sdcard/Download/anaquel-evidence",
            "screencap -p /sdcard/Download/anaquel-evidence/$name$suffix.png")
        commands.forEach { command ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
                FileInputStream(it.fileDescriptor).use { output ->
                    check(output.readBytes().isEmpty()) { "Falló la captura: $command" }
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat /sdcard/Download/anaquel-evidence/$name$suffix.png").use {
                val bytes = FileInputStream(it.fileDescriptor).use { image -> image.readBytes() }
                check(bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte()) {
                    "La evidencia no es un PNG: $name$suffix"
                }
            }
    }

    private fun openE06() {
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_4"))
        ui.onNodeWithTag("rec_4").performClick()
    }

    @Test fun priceApprovalPersistsAndBoundariesStayDisabled() {
        capture("01-inicio")
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        capture("02-revisar")
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_4"))
        ui.onNodeWithTag("rec_4").performClick()
        capture("03-detalle")
        ui.onNodeWithTag("autorizar_detalle").performClick()
        ui.onNodeWithTag("precio_autorizado").performScrollTo().performTextReplacement("43.40")
        ui.onNodeWithTag("autorizar_precio").assertIsNotEnabled()
        ui.onNodeWithText("Mínimo $43.40 · Original $95.00").assertIsDisplayed()
        ui.onNodeWithText("Tiene que ser mayor que el mínimo de $43.40.").assertExists()
        capture("04-precio-invalido")
        ui.onNodeWithTag("precio_autorizado").performTextReplacement("95.00")
        ui.onNodeWithTag("autorizar_precio").assertIsNotEnabled()
        ui.onNodeWithTag("precio_autorizado").performTextReplacement("85.50")
        ui.onNodeWithTag("autorizar_precio").assertIsEnabled().performClick()
        ui.onNodeWithText("¿Autorizas bajar el precio?").assertExists()
        ui.onNodeWithTag("confirmar_autorizacion").performClick()
        ui.waitUntil(10_000) { runBlocking { DecisionRepository(context).decisions.first().size == 1 } }
        ui.activityRule.scenario.recreate()
        val saved = runBlocking { DecisionRepository(context).decisions.first().single() }
        assertEquals("APROBADA", saved.decision)
        assertEquals("85.50".toBigDecimal(), saved.price)
        assertEquals("1", saved.actorId)   // el usuario_id del DUENO: sólo él decide (exigir_firma_del_dueno)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel es experimental en ui-test 1.12
    @Test fun priceRangeStepsWriteTheTypedPrice() {
        openE06()
        ui.onNodeWithTag("autorizar_detalle").performClick()
        ui.onNodeWithTag("rango_precio").performScrollTo().performCustomAccessibilityActionWithLabel("Bajar un escalón")
        ui.onNodeWithTag("precio_autorizado").assertTextContains("76.00")
        ui.onNodeWithTag("autorizar_precio").assertIsEnabled()
        capture("10-rango-precio")
    }

    @Test fun rejectWithoutReasonIsDisabled() {
        openE06()
        ui.onNodeWithTag("rechazar_detalle").performClick()
        ui.onNodeWithTag("confirmar_rechazo").assertIsNotEnabled()
        ui.onNodeWithTag("motivo_rechazo").performTextInput("   ")
        ui.onNodeWithTag("confirmar_rechazo").assertIsNotEnabled()
        ui.onNodeWithText("El conteo está mal").performClick()
        ui.onNodeWithTag("confirmar_rechazo").assertIsEnabled().performClick()
        ui.waitUntil(10_000) { runBlocking { DecisionRepository(context).decisions.first().size == 1 } }
        val saved = runBlocking { DecisionRepository(context).decisions.first().single() }
        assertEquals("RECHAZADA", saved.decision)
        assertEquals("El conteo está mal", saved.reason)
    }

    @Test fun importedApprovalCannotBeOverwritten() {
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_1"))
        ui.onNodeWithTag("rec_1").performClick()
        ui.onNodeWithTag("autorizar_detalle").assertDoesNotExist()
        ui.onNodeWithTag("rechazar_detalle").assertDoesNotExist()
    }

    @Test fun abstentionIsSeenWithoutCreatingADecision() {
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_7"))
        ui.onNodeWithTag("rec_7").performClick()
        ui.onNodeWithText("No te doy una recomendación").assertExists()
        ui.onNodeWithTag("autorizar_detalle").assertDoesNotExist()
        ui.onNodeWithTag("rechazar_detalle").assertDoesNotExist()
        ui.onNodeWithText("Entendido").performScrollTo().performClick()
        ui.waitUntil(10_000) { runBlocking { "1:7" in DecisionRepository(context).seen.first() } }
        assertTrue(runBlocking { DecisionRepository(context).decisions.first().isEmpty() })
        capture("06-abstencion")
    }

    @Test fun expiredMarketNeedsAcknowledgementBeforeApproval() {
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_10"))
        ui.onNodeWithTag("rec_10").performClick()
        ui.onNodeWithTag("autorizar_detalle").performClick()
        ui.onNodeWithText("Sin precio de mercado vigente").assertExists()
        ui.onNodeWithTag("autorizar_precio").performClick()
        ui.onNodeWithTag("confirmar_autorizacion").assertIsNotEnabled()
        ui.onNodeWithTag("aceptar_mercado").performScrollTo().performClick()
        ui.onNodeWithTag("confirmar_autorizacion").assertIsEnabled().performClick()
        ui.waitUntil(10_000) { runBlocking { DecisionRepository(context).decisions.first().size == 1 } }
        assertTrue(runBlocking { DecisionRepository(context).decisions.first().single().marketRiskAccepted })
        // Terminar la transición también debe funcionar si la siguiente propuesta no tiene precio.
        ui.waitForIdle()
        ui.onNodeWithTag("detalle").assertExists()
        ui.onNodeWithTag("precio").assertDoesNotExist()
    }

    @Test fun secondRunShowsPreviousCampaignBeforeNewPrice() {
        ui.onNodeWithContentDescription("Elegir corrida").performClick()
        ui.onNodeWithText("Corrida del 13 oct 2026").performClick()
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_11"))
        ui.onNodeWithTag("rec_11").performClick()
        ui.onNodeWithText("Lo que pasó antes").assertExists()
        ui.onNodeWithText("La campaña a $468.00 terminó el 6 oct 2026 sin vender. Ahora te propongo $416.00.").assertExists()
        capture("05-antecedente")
    }

    @Test fun secondRunShowsRejectedReasonInDetailAndPrice() {
        ui.onNodeWithContentDescription("Elegir corrida").performClick()
        ui.onNodeWithText("Corrida del 13 oct 2026").performClick()
        ui.onNodeWithText("Revisar recomendaciones").performClick()
        ui.onNodeWithTag("revisar").performScrollToNode(hasTestTag("rec_13"))
        ui.onNodeWithTag("rec_13").performClick()
        val reason = "Dijiste: «El precio es muy bajo; en diciembre sí se vende.»"
        val proposal = "Rechazaste el precio de $1,032.00. Ahora te propongo $1,161.00."
        ui.onNodeWithText(reason).assertExists()
        ui.onNodeWithText(proposal).performScrollTo().assertIsDisplayed()
        capture("08-antecedente-rechazo")
        ui.onNodeWithTag("autorizar_detalle").performClick()
        ui.onNodeWithText(reason).assertExists()
        ui.onNodeWithText(proposal).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("autorizar_precio").assertIsEnabled()
        capture("09-precio-repropuesto")
    }

    @Test fun duplicateAndMixedBatchAreRejectedAtomically() = runBlocking {
        val run = context.assets.open("corrida.json").bufferedReader().use { RunParser.parse(it.readText()) }
        val repo = DecisionRepository(context)
        repo.decide(run, setOf(4), "APROBADA", "85.50".toBigDecimal())
        try { repo.decide(run, setOf(4), "APROBADA", "85.50".toBigDecimal()); fail("Duplicado aceptado") }
        catch (_: IllegalArgumentException) { }
        try { repo.decide(run, setOf(2, 4), "RECHAZADA", reason = "Prueba"); fail("Lote mezclado aceptado") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.decisions.first().size)
        repo.undo(setOf("1:4"))
        assertTrue(repo.decisions.first().isEmpty())
    }
}
