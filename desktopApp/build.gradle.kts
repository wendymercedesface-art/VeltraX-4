import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    // JavaFX native media backend. Windows distribution includes Windows binaries.
    implementation("org.openjfx:javafx-base:21.0.2:win")
    implementation("org.openjfx:javafx-graphics:21.0.2:win")
    implementation("org.openjfx:javafx-media:21.0.2:win")
    implementation("org.openjfx:javafx-swing:21.0.2:win")
}
compose.desktop {
    application {
        mainClass = "com.veltrax.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "VeltraX"
            packageVersion = "2.0.0"
            description = "Reproductor musical local y gestor de descargas directas"
            vendor = "VeltraX"
        }
    }
}
