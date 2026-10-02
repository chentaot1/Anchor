plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // JNA for Windows 11 native Win32 APIs (Process & Window monitoring)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Multiplatform DataStore Preferences Core
    implementation("androidx.datastore:datastore-preferences-core:1.1.1")

    // Room annotations for shared model entities
    implementation("androidx.room:room-common:2.6.1")

    // SQLite JDBC for Desktop Database persistence
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Apache PDFBox for on-device PDF syllabus text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.anchor.adhd.desktop.MainKt"
        nativeDistributions {
            modules("java.instrument", "java.sql", "jdk.unsupported", "java.naming", "java.net.http")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
            )
            packageName = "Anchor"
            packageVersion = "1.0.0"
            windows {
                menu = true
                shortcut = true
            }
        }
    }
}

ktlint {
    version.set("1.3.1")
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(true)
}
