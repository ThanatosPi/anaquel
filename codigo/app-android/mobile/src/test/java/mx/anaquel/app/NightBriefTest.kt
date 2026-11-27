package mx.anaquel.app

import mx.anaquel.app.data.RunParser
import mx.anaquel.app.domain.Output
import mx.anaquel.app.ui.NightBrief
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class NightBriefTest {
    private val run = RunParser.parse(javaClass.classLoader!!.getResource("corrida.json")!!.readText())

    @Test fun aRunBeforeSixIsTheNightReport() {
        assertEquals("Mientras la tienda estaba cerrada, revisé 59 productos.", NightBrief.headline(run))
        assertEquals("3:10", NightBrief.clock(run.started))
        assertEquals("3:40", NightBrief.clock(run.ended!!))
        assertEquals("Terminé con 11 recomendaciones", NightBrief.finished(run))
    }

    @Test fun aDaytimeRunSaysItsTime() {
        val day = run.copy(started = run.started.withHour(14).withMinute(5))
        assertEquals("En la corrida de las 14:05 revisé 59 productos.", NightBrief.headline(day))
    }

    @Test fun aRunWhereNothingWasDecidedSaysSo() {
        val none = run.copy(recommendations = run.recommendations.filter { it.output == Output.ABSTENERSE })
        assertEquals("Esta noche no pude decidir nada", NightBrief.headline(none))
    }

    @Test fun greetingFollowsTheClock() {
        assertEquals("Buenos días", NightBrief.greeting(LocalTime.of(7, 5)))
        assertEquals("Buenas tardes", NightBrief.greeting(LocalTime.of(13, 0)))
        assertEquals("Buenas noches", NightBrief.greeting(LocalTime.of(21, 0)))
        assertEquals("Buenas noches", NightBrief.greeting(LocalTime.of(4, 0)))
    }
}
