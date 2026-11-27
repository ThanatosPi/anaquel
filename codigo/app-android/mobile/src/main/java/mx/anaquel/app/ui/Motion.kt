package mx.anaquel.app.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** La misma comprobación que ya usaba AnaquelApp: escala de animación del sistema en 0. */
@Composable fun animationsOff(): Boolean = Settings.Global.getFloat(
    LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Las entradas de Inicio se animan una sola vez por corrida mientras viva el proceso. */
object Intro {
    private val played = mutableSetOf<String>()
    fun firstTime(key: String): Boolean = played.add(key)
}
