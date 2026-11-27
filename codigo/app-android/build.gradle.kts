plugins {
    id("com.android.application") version "9.3.2" apply false
    // Actualiza el compilador integrado de AGP; el módulo no aplica kotlin.android.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // Room genera su código con KSP; desde 2.3.0 KSP ya no va atado a la versión de Kotlin.
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
