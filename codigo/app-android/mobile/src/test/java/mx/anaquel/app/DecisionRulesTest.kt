package mx.anaquel.app

import mx.anaquel.app.data.RunParser
import mx.anaquel.app.data.DecisionRepository
import mx.anaquel.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DecisionRulesTest {
    private fun fixture(name: String = "corrida.json") = javaClass.classLoader!!.getResource(name)!!.readText()
    private val run get() = RunParser.parse(fixture())
    private val rec get() = run.recommendations.single { it.scenario == "E06" }

    @Test fun bothPriceBoundariesAreStrict() {
        val quote = rec.quote!!
        assertFalse(DecisionRules.validPrice(quote.minimum, quote))
        assertFalse(DecisionRules.validPrice(quote.original, quote))
        assertFalse(DecisionRules.validPrice("43.39".toBigDecimal(), quote))
        assertFalse(DecisionRules.validPrice("95.01".toBigDecimal(), quote))
        assertTrue(DecisionRules.validPrice("43.41".toBigDecimal(), quote))
        assertTrue(DecisionRules.validPrice("94.99".toBigDecimal(), quote))
    }

    @Test fun localeInputNeverRoundsAnInvalidPriceIntoValidity() {
        assertEquals("85.50".toBigDecimal(), DecisionRules.parsePrice(" 85,50 "))
        listOf("", " ", "43.401", "1,000.00", "NaN", "Infinity", "1e2", "-1", "85.").forEach {
            assertNull(it, DecisionRules.parsePrice(it))
        }
    }

    @Test fun rejectionAlwaysNeedsAReason() {
        assertNotNull(DecisionRules.validate(rec, "RECHAZADA", null, "  ", false))
        assertNull(DecisionRules.validate(rec, "RECHAZADA", null, "El conteo está mal", false))
    }

    @Test fun approvalDoesNotAskForReasonButChecksPrice() {
        assertNull(DecisionRules.validate(rec, "APROBADA", "85.50".toBigDecimal(), null, false))
        assertNotNull(DecisionRules.validate(rec, "APROBADA", rec.quote!!.minimum, null, false))
    }

    @Test fun missingReconciliationAbstentionAndImportedDecisionAreNotActionable() {
        assertNotNull(DecisionRules.validate(rec.copy(reconciliation = null), "RECHAZADA", null, "Conteo", false))
        val abstention = run.recommendations.first { it.output == Output.ABSTENERSE }
        assertNotNull(DecisionRules.validate(abstention, "APROBADA", null, null, false))
        val imported = run.recommendations.single { it.scenario == "E01" }
        assertNotNull(DecisionRules.validate(imported, "APROBADA", imported.quote!!.proposed, null, false))
    }

    @Test fun expiredMarketReferenceNeedsExplicitAcknowledgement() {
        val expired = run.recommendations.single { it.scenario == "E16" }
        assertNotNull(DecisionRules.validate(expired, "APROBADA", expired.quote!!.proposed, null, false))
        assertNull(DecisionRules.validate(expired, "APROBADA", expired.quote!!.proposed, null, true))
    }

    @Test fun bothSimulatorRunsParseWithoutLosingJustificationOrPreviousCampaign() {
        assertEquals(11, run.recommendations.size)
        val root = JSONObject(fixture())
        assertEquals(root.getJSONArray("recomendaciones").getJSONObject(0).getString("justificacion"), run.recommendations.first().justification)
        val second = RunParser.parse(fixture("corrida_2.json"))
        assertEquals(2L, second.id)
        assertEquals(2, second.recommendations.size)
        val repeat = second.recommendations.single { it.scenario == "E17" }
        assertEquals(2, repeat.quote!!.step)
        assertEquals(1, repeat.antecedent!!.step)
        assertEquals("468.0".toBigDecimal(), repeat.antecedent.price)
    }

    private fun changedAntecedent(scenario: String, change: (JSONObject, JSONObject) -> Unit): String {
        val root = JSONObject(fixture("corrida_2.json"))
        val rows = root.getJSONArray("recomendaciones")
        val item = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("escenario") == scenario }
        change(item, item.getJSONObject("antecedente"))
        return root.toString()
    }

    @Test fun rejectedProposalPreservesItsReasonAndCanStayOrRaisePriceOneStep() {
        val proposal = RunParser.parse(fixture("corrida_2.json")).recommendations.single { it.scenario == "E20" }
        assertEquals("RECHAZADA", proposal.antecedent!!.decision)
        assertEquals("El precio es muy bajo; en diciembre sí se vende.", proposal.antecedent.reason)
        assertNull(proposal.antecedent.ended)
        assertEquals(proposal.antecedent.step - 1, proposal.quote!!.step)
        assertTrue(proposal.actionable)
        val sameStep = changedAntecedent("E20") { item, prior ->
            item.getJSONObject("precio").put("escalon", prior.getInt("escalon")).put("precio_propuesto", 1032)
        }
        assertEquals(2, RunParser.parse(sameStep).recommendations.single { it.scenario == "E20" }.quote!!.step)
    }

    @Test fun partialCampaignSalesPermitExactlyOneDeeperStep() {
        val json = changedAntecedent("E17") { _, prior -> prior.put("unidades_vendidas", 2) }
        val proposal = RunParser.parse(json).recommendations.single { it.scenario == "E17" }
        assertEquals(0, proposal.antecedent!!.unitsSold!!.compareTo("2".toBigDecimal()))
        assertEquals(2, proposal.quote!!.step)
    }

    @Test fun soldOutOrIncompleteCampaignCannotBeReproposed() {
        listOf(6, 7).forEach { sold ->
            assertThrows(IllegalArgumentException::class.java) {
                RunParser.parse(changedAntecedent("E17") { _, prior -> prior.put("unidades_vendidas", sold) })
            }
        }
        listOf("campana_termino", "existencia_inicial", "unidades_vendidas").forEach { missing ->
            assertThrows(IllegalArgumentException::class.java) {
                RunParser.parse(changedAntecedent("E17") { _, prior -> prior.remove(missing) })
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunParser.parse(changedAntecedent("E17") { item, _ -> item.getJSONObject("precio").put("escalon", 1) })
        }
    }

    @Test fun rejectionCannotLowerPriceSkipAStepOrLoseItsReason() {
        listOf(0, 3, 4).forEach { step ->
            assertThrows(IllegalArgumentException::class.java) {
                RunParser.parse(changedAntecedent("E20") { item, _ -> item.getJSONObject("precio").put("escalon", step) })
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunParser.parse(changedAntecedent("E20") { _, prior -> prior.put("motivo", "  ") })
        }
    }

    @Test fun selfReferencingAndUnknownAntecedentsFailTheWholeRun() {
        assertThrows(IllegalArgumentException::class.java) {
            RunParser.parse(changedAntecedent("E20") { item, prior -> prior.put("recomendacion_id", item.getLong("recomendacion_id")) })
        }
        assertThrows(IllegalStateException::class.java) {
            RunParser.parse(changedAntecedent("E20") { _, prior -> prior.put("decision", "OTRA") })
        }
    }

    @Test fun receivedMinimumMustMatchMaximumFloor() {
        assertThrows(IllegalArgumentException::class.java) {
            RunParser.parse(changedAntecedent("E17") { item, _ -> item.getJSONObject("precio").put("precio_minimo", 260) })
        }
    }

    @Test fun importedRejectionAlsoRequiresAReason() {
        val root = JSONObject(fixture())
        val rows = root.getJSONArray("recomendaciones")
        val item = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("escenario") == "E10" }
        item.getJSONObject("autorizacion").put("motivo", " ")
        assertThrows(IllegalArgumentException::class.java) { RunParser.parse(root.toString()) }
    }

    @Test fun malformedContractCannotBecomeAnActionableProposal() {
        val root = JSONObject(fixture())
        root.getJSONArray("recomendaciones").getJSONObject(0).put("salida", "UNKNOWN")
        assertThrows(Exception::class.java) { RunParser.parse(root.toString()) }
        val jump = JSONObject(fixture("corrida_2.json"))
        jump.getJSONArray("recomendaciones").getJSONObject(0).getJSONObject("precio").put("escalon", 3)
        assertThrows(IllegalArgumentException::class.java) { RunParser.parse(jump.toString()) }
    }

    @Test fun localDecisionRoundTripPreservesPriceActorAndMarketAcknowledgement() {
        val d = LocalDecision(1, 4, "APROBADA", "85.50".toBigDecimal(), null,
            "2026-09-23T08:30:00-06:00", "dueno-demo-prueba", true)
        assertEquals(listOf(d), DecisionRepository.decode(DecisionRepository.encode(listOf(d))))
        assertNotEquals(d.key, d.copy(runId = 2).key)
    }
}
