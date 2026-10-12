package com.anchor.adhd.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import kotlinx.serialization.json.*
import java.sql.DriverManager
import java.util.UUID
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File

/** Development entry point: real production UI, disposable files, no Windows startup changes. */
object DesktopUiSmokeLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 || args.size == 2)
        val root = File(args.first()).canonicalFile
        check(root.name == "ui-smoke-profile" && File(root, ".anchor-ui-smoke").isFile) {
            "Launch through :desktopApp:runUiSmoke with a marked disposable profile."
        }
        check(File(System.getenv("APPDATA")).canonicalFile == root)
        check(File(System.getProperty("user.home")).canonicalFile == root)
        var directory: File? = File(System.getProperty("user.dir")).canonicalFile
        while (directory != null) {
            check(!File(directory, ".git").exists() && !File(directory, "settings.gradle.kts").exists()) {
                "The smoke process must run outside all repository directories."
            }
            directory = directory.parentFile
        }
        val windowSize = args.getOrNull(1)?.let { size ->
            require(size.matches(Regex("[0-9]+x[0-9]+"))) { "Use -PuiSmokeWindow=WIDTHxHEIGHT" }
            val (width, height) = size.split('x').map(String::toInt)
            require(width in 640..3840 && height in 400..2160) { "Smoke window dimensions are out of range" }
            DpSize(width.dp, height.dp)
        }
        withSingleInstance(root.toPath()) {
            val undoFixture = java.lang.Boolean.getBoolean("anchor.smoke.undo")
            val verifyUndo = java.lang.Boolean.getBoolean("anchor.smoke.undo.verify")
            check(!(undoFixture && verifyUndo))
            check(!(java.lang.Boolean.getBoolean("anchor.smoke.rescue") && (undoFixture || verifyUndo)))
            var initialDeletion: DesktopTaskDeletion? = null
            runBlocking {
                val prefs = DesktopUserPreferences()
                prefs.setAutostartOnBoot(false)
                prefs.setStartMinimized(false)
                prefs.setAiEnabled(false)
                prefs.setStandingShieldEnabled(false)
                prefs.setScopeSentinelEnabled(false)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val db = AnchorDesktopDatabase(scope)
                db.initialize()
                db.blockRules.value.forEach { db.setBlockRuleEnabled(it.target, false) }
                if (verifyUndo) {
                    val fixture = Json.parseToJsonElement(File(root, "capture-undo-fixture.json").readText()).jsonObject
                    val key = fixture.getValue("key").jsonPrimitive.content
                    val title = fixture.getValue("title").jsonPrimitive.content
                    val id = fixture.getValue("id").jsonPrimitive.long
                    val minutes = fixture.getValue("minutes").jsonPrimitive.int
                    check(UUID.fromString(key).toString() == key && title.startsWith("Windows Undo capture fixture "))
                    db.captureThought(key, title, 27)
                    val saved = db.tasks.value.single { it.title == title }
                    check(saved.id == id && saved.durationMinutes == minutes)
                    check(db.tasks.value.size == fixture.getValue("beforeTasks").jsonPrimitive.int + 1)
                    check(captureCount(root) == fixture.getValue("beforeCaptures").jsonPrimitive.int + 1)
                    println("UNDO RETRY VERIFIED: task $id reused at $minutes minutes; no duplicate task or acknowledgement")
                } else if (undoFixture) {
                    val key = UUID.randomUUID().toString()
                    val title = "Windows Undo capture fixture ${key.take(8)}"
                    val beforeTasks = db.tasks.value.size
                    val beforeCaptures = captureCount(root)
                    db.captureThought(key, title, 13)
                    val saved = db.tasks.value.single { it.title == title }
                    initialDeletion = db.deleteTask(saved.id)
                    check(initialDeletion!!.captures.single().key == key)
                    check(db.tasks.value.size == beforeTasks && captureCount(root) == beforeCaptures)
                    File(root, "capture-undo-fixture.json").writeText(buildJsonObject {
                        put("key", key); put("title", title); put("id", saved.id); put("minutes", 13)
                        put("beforeTasks", beforeTasks); put("beforeCaptures", beforeCaptures)
                    }.toString())
                }
            }
            if (verifyUndo) return@withSingleInstance
            if (java.lang.Boolean.getBoolean("anchor.smoke.rescue")) launchRescueScribeSmoke(windowSize ?: DpSize(900.dp, 600.dp))
            else if (windowSize == null) launchDesktopApplication(shouldStartMinimized = false, initialTaskDeletions = listOfNotNull(initialDeletion))
            else launchDesktopApplication(shouldStartMinimized = false, initialWindowSize = windowSize, startFullscreen = false,
                initialTaskDeletions = listOfNotNull(initialDeletion))
        }
    }
    private fun captureCount(root: File): Int = DriverManager.getConnection("jdbc:sqlite:${File(root, "Anchor/anchor.db").absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT COUNT(*) FROM task_captures").use { it.next(); it.getInt(1) } }
    }

}
