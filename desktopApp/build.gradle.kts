import java.nio.file.Files

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

sourceSets["main"].resources.srcDir(rootProject.file("licenses"))

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
            // Windows screen readers need Java Access Bridge in the bundled runtime.
            modules("java.instrument", "java.sql", "jdk.unsupported", "java.naming", "java.net.http", "jdk.accessibility")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
            )
            packageName = "Anchor"
            packageVersion = "1.0.6"
            licenseFile.set(rootProject.file("LICENSE"))
            windows {
                iconFile.set(project.file("src/main/resources/anchor.ico"))
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

tasks.withType<Test>().configureEach {
    // The recovery test starts and forcibly terminates a separate JVM using the actual runtime.
    systemProperty("anchor.test.classpath", sourceSets["test"].runtimeClasspath.asPath)
}

tasks.register<JavaExec>("generateWindowsIcon") {
    group = "verification"
    description = "Regenerates the Windows ICO and preview from Anchor's shared icon drawing."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.anchor.adhd.desktop.platform.DesktopIconAssetGenerator")
    args(project.file("src/main/resources/anchor.ico").absolutePath, layout.buildDirectory.file("icon-preview.png").get().asFile.absolutePath)
}

// Keeps UI smoke checks away from personal data, startup registration, and repository update checks.
tasks.register<JavaExec>("runUiSmoke") {
    group = "verification"
    description = "Opens the production Windows UI with a disposable development profile."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.anchor.adhd.desktop.DesktopUiSmokeLauncher")
    val profile = layout.buildDirectory.dir("ui-smoke-profile")
    val windowSize = providers.gradleProperty("uiSmokeWindow")
    doFirst {
        val root = profile.get().asFile
        root.mkdirs()
        root.resolve(".anchor-ui-smoke").writeText("Disposable Anchor UI smoke profile")
        val runtimeDirectory = Files.createTempDirectory("anchor-ui-smoke-").toFile()
        workingDir(runtimeDirectory)
        environment("APPDATA", root.absolutePath)
        environment("LOCALAPPDATA", root.absolutePath)
        systemProperty("user.home", root.absolutePath)
        systemProperty("anchor.smoke.rescue", providers.gradleProperty("uiSmokeRescue").orNull == "true")
        systemProperty("anchor.smoke.undo", providers.gradleProperty("uiSmokeUndo").orNull == "true")
        systemProperty("anchor.smoke.undo.verify", providers.gradleProperty("uiSmokeUndoVerify").orNull == "true")
        setArgs(listOfNotNull(root.absolutePath, windowSize.orNull))
    }
}

tasks.register<JavaExec>("runStartupErrorSmoke") {
    group = "verification"
    description = "Shows the production startup error for a disposable obstructed data folder (exits with code 1)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.anchor.adhd.desktop.platform.InstanceLockChild")
    args(layout.buildDirectory.dir("startup-error-profile").get().asFile.absolutePath, "error-dialog")
}
