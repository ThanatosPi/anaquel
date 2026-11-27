plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

abstract class SimulatorAssetsTask : DefaultTask() {
    @get:InputDirectory abstract val inputDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun copyAssets() {
        val destination = outputDirectory.get().asFile
        destination.mkdirs()
        listOf("corrida.json", "corrida_2.json", "municipios.json", "catalogo.json").forEach { name ->
            inputDirectory.file(name).get().asFile.copyTo(destination.resolve(name), overwrite = true)
        }
    }
}
val syncSimulator by tasks.registering(SimulatorAssetsTask::class) {
    inputDirectory.set(rootProject.layout.projectDirectory.dir("../simulador/salida"))
    outputDirectory.set(layout.buildDirectory.dir("generated/simulatorAssets"))
}

android {
    namespace = "mx.anaquel.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "mx.anaquel.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets["test"].resources.srcDir(rootProject.file("../simulador/salida"))
}
kotlin { jvmToolchain(21) }
// El esquema que exporta Room lo compara db/comparar_esquema.py contra la v2.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(syncSimulator, SimulatorAssetsTask::outputDirectory)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.animation:animation:1.12.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha20")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    // Room en las pruebas JVM, sin teléfono: SQLite empaquetado para la JVM de la computadora.
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.6.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0")
}
