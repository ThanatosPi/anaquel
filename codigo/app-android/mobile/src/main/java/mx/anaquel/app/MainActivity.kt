package mx.anaquel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import mx.anaquel.app.ui.AnaquelApp
import mx.anaquel.app.ui.AnaquelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Surface raíz: Ubicación y la pantalla de carga se dibujan fuera del Scaffold y, sin esto,
        // quedaban sobre el fondo blanco de la ventana aun en modo oscuro (lo vio Sebastián, 6/oct).
        setContent { AnaquelTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AnaquelApp() } } }
    }
}
