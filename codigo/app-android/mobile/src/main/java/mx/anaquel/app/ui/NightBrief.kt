package mx.anaquel.app.ui

import mx.anaquel.app.domain.AnalysisRun
import mx.anaquel.app.domain.Output
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Los textos del informe de la noche en Inicio. La hora de la corrida se lee en su propio huso. */
object NightBrief {
    private val clockFormat = DateTimeFormatter.ofPattern("H:mm")

    fun greeting(now: LocalTime): String = when (now.hour) {
        in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches"
    }

    fun clock(time: OffsetDateTime): String = time.format(clockFormat)

    fun headline(run: AnalysisRun): String {
        val products = if (run.evaluated == 1) "1 producto" else "${run.evaluated} productos"
        return when {
            run.recommendations.all { it.output == Output.ABSTENERSE } -> "Esta noche no pude decidir nada"
            run.started.hour < 6 -> "Mientras la tienda estaba cerrada, revisé $products."
            else -> "En la corrida de las ${clock(run.started)} revisé $products."
        }
    }

    fun finished(run: AnalysisRun): String = run.recommendations.size.let { n ->
        "Terminé con $n ${if (n == 1) "recomendación" else "recomendaciones"}"
    }
}
