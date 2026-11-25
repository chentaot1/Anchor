package com.anchor.adhd.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Automated Compose Hallmark Floor-ban lint gate shared across `:shared`, `:app`, and `:desktopApp`. */
class AnchorDesignSystemLintTest {
    private enum class LintRule(val id: String) {
        COLOR_LITERAL("color-literal"),
        LETTER_SPACING("letter-spacing"),
        BANNED_EMOJI("banned-emoji"),
        LEGACY_HELPER("legacy-helper"),
        INFINITE_TRANSITION("infinite-transition"),
    }

    private data class LintViolation(
        val relativePath: String,
        val line: Int,
        val rule: LintRule,
        val match: String,
    ) {
        fun format(): String = "$relativePath:$line [${rule.id}] $match"
    }

    @Test
    fun allUiSourcesComplyWithDesignSystemFloorBans() {
        val repoRoot = resolveRepoRoot()

        val scannedFiles = collectScannedFiles(repoRoot)
        assertTrue(scannedFiles.isNotEmpty(), "Expected UI Kotlin files to scan under $repoRoot")

        val focusCanvasFile = scannedFiles[FOCUS_CANVAS_PATH]
            ?: fail("Expected FocusCanvas.kt at $FOCUS_CANVAS_PATH in scanned UI tree")
        val focusCanvasContent = focusCanvasFile.readText()
        assertTrue(
            REDUCED_MOTION_REGEX.containsMatchIn(focusCanvasContent),
            "$FOCUS_CANVAS_PATH must gate its tide animation on LocalReducedMotion",
        )

        val violationsByPath = linkedMapOf<String, List<LintViolation>>()
        for ((relativePath, file) in scannedFiles) {
            val violations = lintSource(relativePath, file.readText())
            if (violations.isNotEmpty()) {
                violationsByPath[relativePath] = violations
            }
        }

        val violations = violationsByPath.values.flatten()
        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Design system Floor-ban violations in guarded files (${violations.size}):")
                violations.forEach { appendLine("  - ${it.format()}") }
            }
            fail(message.trimEnd())
        }
    }

    @Test
    fun everyFloorBanRuleFiresOnViolatingSnippetsAndHonorsScopedExclusions() {
        val samplePath = "app/src/main/java/com/anchor/adhd/ui/sample/SampleScreen.kt"
        val themePath = "shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/SampleTheme.kt"
        val funLinksPath = "app/src/main/java/com/anchor/adhd/ui/settings/FunLinksSettingsSection.kt"

        // Rule 1: Color(0x...), Color.White, Color.Black
        val colorViolations = lintSource(
            samplePath,
            """
            val a = Color(0xFF15263B)
            val b = Color.White.copy(alpha = 0.5f)
            val c = Color.Black
            """.trimIndent(),
        )
        assertEquals(3, colorViolations.count { it.rule == LintRule.COLOR_LITERAL })
        val namedHexViolations = lintSource(samplePath, "val edge = Color(color = 0xFF15263B)")
        assertEquals(1, namedHexViolations.count { it.rule == LintRule.COLOR_LITERAL })
        assertTrue(
            lintSource(themePath, "val a = Color(0xFF15263B)\nval b = Color.White\nval c = Color.Black").isEmpty(),
            "ui/theme/ must be exempt from color-literal rule",
        )

        // Rule 2: letterSpacing =
        val spacingViolations = lintSource(samplePath, "Text(\"NOW\", letterSpacing = 1.2.sp)")
        assertEquals(listOf(LintRule.LETTER_SPACING), spacingViolations.map { it.rule })
        assertTrue(
            lintSource(themePath, "val style = TextStyle(letterSpacing = (-0.3).sp)").isEmpty(),
            "ui/theme/ must be exempt from letter-spacing rule",
        )

        // Rule 3: Banned chrome emojis + FunLinksSettingsSection preset list exclusion
        for (emoji in BANNED_EMOJIS) {
            val hits = lintSource(samplePath, "Text(\"$emoji Heading\")")
            assertTrue(
                hits.any { it.rule == LintRule.BANNED_EMOJI },
                "Expected banned-emoji rule to fire on $emoji",
            )
        }
        val funLinksPresetOnly = """
            val presetEmojis = listOf("🟩", "🟪", "♟️", "🧩", "🎮", "🎯", "📖", "☕", "🌊", "✨")
            Text("Clean title")
        """.trimIndent()
        assertTrue(
            lintSource(funLinksPath, funLinksPresetOnly).isEmpty(),
            "FunLinksSettingsSection presetEmojis list must be exempt from banned-emoji rule",
        )
        val funLinksChromeEmoji = """
            val presetEmojis = listOf("🎯", "✨")
            Text("⚓ Chrome header")
        """.trimIndent()
        assertEquals(
            listOf(LintRule.BANNED_EMOJI),
            lintSource(funLinksPath, funLinksChromeEmoji).map { it.rule },
            "Chrome emojis outside presetEmojis in FunLinksSettingsSection must still be caught",
        )

        // Rule 4: Legacy helpers (caught everywhere, including ui/theme/)
        for (helper in LEGACY_HELPERS) {
            val hits = lintSource(samplePath, "val x = $helper")
            assertTrue(
                hits.any { it.rule == LintRule.LEGACY_HELPER },
                "Expected legacy-helper rule to fire on $helper",
            )
        }
        assertTrue(
            lintSource(themePath, "fun Modifier.beaconGlow() = this").any { it.rule == LintRule.LEGACY_HELPER },
            "Legacy helper declarations inside ui/theme/ must still be caught until deleted",
        )

        // Rule 5: rememberInfiniteTransition outside FocusCanvas.kt, and LocalReducedMotion check inside FocusCanvas.kt
        val infiniteOutside = lintSource(samplePath, "val t = rememberInfiniteTransition(label = \"pulse\")")
        assertEquals(listOf(LintRule.INFINITE_TRANSITION), infiniteOutside.map { it.rule })

        val focusCanvasUnguarded = lintSource(
            FOCUS_CANVAS_PATH,
            "val t = rememberInfiniteTransition(label = \"livingTide\")",
        )
        assertEquals(
            listOf(LintRule.INFINITE_TRANSITION),
            focusCanvasUnguarded.map { it.rule },
            "FocusCanvas.kt must fail if rememberInfiniteTransition is not paired with LocalReducedMotion",
        )

        val focusCanvasGuarded = lintSource(
            FOCUS_CANVAS_PATH,
            """
            val reducedMotion = LocalReducedMotion.current
            if (!reducedMotion) {
                rememberInfiniteTransition(label = "livingTide")
            }
            """.trimIndent(),
        )
        assertFalse(
            focusCanvasGuarded.any { it.rule == LintRule.INFINITE_TRANSITION },
            "FocusCanvas.kt with LocalReducedMotion guard should pass infinite-transition rule",
        )
    }

    private fun resolveRepoRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").isFile && File(current, "DESIGN.md").isFile) {
                return current
            }
            current = current.parentFile
        }
        fail(
            "Unable to resolve Anchor repository root (expected settings.gradle.kts and DESIGN.md) " +
                "walking upward from ${System.getProperty("user.dir")}",
        )
    }

    private fun collectScannedFiles(repoRoot: File): Map<String, File> {
        val result = sortedMapOf<String, File>()
        for (target in SCAN_TARGETS) {
            val path = File(repoRoot, target)
            assertTrue(path.exists(), "Configured lint scan target does not exist: $target")
            if (path.isFile) {
                assertTrue(path.extension == "kt", "Configured lint scan file must be .kt: $target")
                val rel = path.relativeTo(repoRoot).invariantSeparatorsPath
                result[rel] = path
            } else {
                val ktFiles = path.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
                assertTrue(ktFiles.isNotEmpty(), "Expected at least one .kt file under $target")
                for (file in ktFiles) {
                    val rel = file.relativeTo(repoRoot).invariantSeparatorsPath
                    result[rel] = file
                }
            }
        }
        return result
    }

    private fun lintSource(relativePath: String, content: String): List<LintViolation> {
        val normalizedPath = relativePath.replace('\\', '/')
        val isThemeFile = normalizedPath.contains("/ui/theme/")
        val isFocusCanvas = normalizedPath == FOCUS_CANVAS_PATH
        val isFunLinksSettings = normalizedPath.endsWith("/FunLinksSettingsSection.kt")
        val violations = mutableListOf<LintViolation>()

        if (!isThemeFile) {
            for (match in COLOR_HEX_REGEX.findAll(content)) {
                violations += LintViolation(
                    relativePath = normalizedPath,
                    line = lineNumberAt(content, match.range.first),
                    rule = LintRule.COLOR_LITERAL,
                    match = match.value,
                )
            }
            for (match in COLOR_NAMED_REGEX.findAll(content)) {
                violations += LintViolation(
                    relativePath = normalizedPath,
                    line = lineNumberAt(content, match.range.first),
                    rule = LintRule.COLOR_LITERAL,
                    match = match.value,
                )
            }
            for (match in LETTER_SPACING_REGEX.findAll(content)) {
                violations += LintViolation(
                    relativePath = normalizedPath,
                    line = lineNumberAt(content, match.range.first),
                    rule = LintRule.LETTER_SPACING,
                    match = match.value,
                )
            }
        }

        val emojiScanText = if (isFunLinksSettings) {
            FUN_LINKS_PRESET_REGEX.replace(content) { match ->
                buildString(match.value.length) {
                    for (ch in match.value) {
                        append(if (ch == '\n') '\n' else ' ')
                    }
                }
            }
        } else {
            content
        }
        for (match in BANNED_EMOJI_REGEX.findAll(emojiScanText)) {
            violations += LintViolation(
                relativePath = normalizedPath,
                line = lineNumberAt(emojiScanText, match.range.first),
                rule = LintRule.BANNED_EMOJI,
                match = match.value,
            )
        }

        for (match in LEGACY_HELPER_REGEX.findAll(content)) {
            violations += LintViolation(
                relativePath = normalizedPath,
                line = lineNumberAt(content, match.range.first),
                rule = LintRule.LEGACY_HELPER,
                match = match.value,
            )
        }

        val infiniteMatches = INFINITE_TRANSITION_REGEX.findAll(content).toList()
        if (infiniteMatches.isNotEmpty()) {
            if (!isFocusCanvas) {
                for (match in infiniteMatches) {
                    violations += LintViolation(
                        relativePath = normalizedPath,
                        line = lineNumberAt(content, match.range.first),
                        rule = LintRule.INFINITE_TRANSITION,
                        match = match.value,
                    )
                }
            } else if (!REDUCED_MOTION_REGEX.containsMatchIn(content)) {
                val firstMatch = infiniteMatches.first()
                violations += LintViolation(
                    relativePath = normalizedPath,
                    line = lineNumberAt(content, firstMatch.range.first),
                    rule = LintRule.INFINITE_TRANSITION,
                    match = "rememberInfiniteTransition without LocalReducedMotion guard",
                )
            }
        }

        return violations
    }

    private fun lineNumberAt(text: String, offset: Int): Int {
        var line = 1
        for (i in 0 until offset.coerceAtMost(text.length)) {
            if (text[i] == '\n') line++
        }
        return line
    }

    companion object {
        private const val FOCUS_CANVAS_PATH =
            "shared/src/commonMain/kotlin/com/anchor/adhd/ui/focus/FocusCanvas.kt"

        private val SCAN_TARGETS = listOf(
            "shared/src/commonMain/kotlin/com/anchor/adhd/ui",
            "app/src/main/java/com/anchor/adhd/ui",
            "desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui",
            "desktopApp/src/main/kotlin/com/anchor/adhd/desktop/blocker/DesktopRescueWindow.kt",
            "desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt",
        )

        private val BANNED_EMOJIS =
            listOf(
                "⚓",
                "🌱",
                "🛡",
                "🔥",
                "⚡",
                "✨",
                "💡",
                "🎯",
                "✎",
                "📅",
                "🔒",
                "🔓",
                "⏱",
                "⚠",
                "✅",
                "❌",
            )
        private val LEGACY_HELPERS = listOf(
            "AnchorGradients",
            "beaconGlow",
            "harborGlow",
            "topSheen",
            "harborEntrance",
            "GardenScene",
            "DesktopHarborScene",
        )

        private val COLOR_HEX_REGEX = Regex("""\bColor\s*\(\s*(?:color\s*=\s*)?0[xX][0-9a-fA-F_]+""")
        private val COLOR_NAMED_REGEX = Regex("""\bColor\.(White|Black)\b""")
        private val LETTER_SPACING_REGEX = Regex("""\bletterSpacing\s*=""")
        private val BANNED_EMOJI_REGEX = Regex(BANNED_EMOJIS.joinToString("|") { Regex.escape(it) })
        private val FUN_LINKS_PRESET_REGEX =
            Regex("""val\s+presetEmojis\s*=\s*listOf\s*\([^)]*\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val LEGACY_HELPER_REGEX =
            Regex("""\b(${LEGACY_HELPERS.joinToString("|") { Regex.escape(it) }})\b""")
        private val INFINITE_TRANSITION_REGEX = Regex("""\brememberInfiniteTransition\b""")
        private val REDUCED_MOTION_REGEX = Regex("""\bLocalReducedMotion\b""")
    }
}
