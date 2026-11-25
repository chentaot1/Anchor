# Anchor Unified Cross-Platform Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign Anchor across both **Windows** (`desktopApp`) and **Android** (`app`) onto one shared Compose Multiplatform **Warm Harbor Paper-Soft with Soul** identity owned in `:shared`, achieving full Morning Harbor (Light) + Night Harbor (Dark) parity, renaming AI to **Captain**, dropping `GardenScene` in favor of real focus tide data, and enforcing Hallmark App-Screen Floor rules via an automated Compose static lint test.

**Architecture:** All visual tokens (`AnchorColors`, `AnchorTokens`, `AnchorColorSchemes`, `AnchorTypography`, `AnchorSpacing`, `AnchorShapes`, `AnchorMotion`, `AnchorTheme`), domain appearance state (`AppearanceMode`), and core UI primitives (`AnchorBeaconPlate`, `AnchorFocalPlate`, `AnchorWell`, `AnchorListRow`, `AnchorSectionHeader`, `AnchorWeeklyTide`, `AnchorTodayTideStrip`, `HarborCtaButton`, `AnchorQuietButton`, `AnchorChip`, `HarborEmptyState`, `HarborModalSheet`, Living Tide `FocusCanvas`) live in `:shared`. Android (`:app`) and Windows (`:desktopApp`) keep thin platform adapters (font loading, adaptive Android `enableEdgeToEdge`, Windows JNA `AppsUseLightTheme` / `SPI_GETCLIENTAREAANIMATION` detection, and platform-native navigation shells) and consume `:shared` exclusively across every screen.

**Tech Stack:** Kotlin Multiplatform (JVM 17), Jetpack Compose / Compose Multiplatform + Material 3, AndroidX DataStore Preferences, JNA 5.14.0 (Windows), Room (Android), SQLite JDBC (Desktop), Gradle 8.14, JUnit / `kotlin.test`, ktlint 1.3.1, OFL `Newsreader` + `Atkinson Hyperlegible` TTF fonts.

## Global Constraints

- **Locked design system:** Every hex color, radius, type role, and Floor ban MUST match [`DESIGN.md`](../../../DESIGN.md) and [`docs/superpowers/specs/2026-11-25-unified-harbor-redesign-design.md`](../specs/2026-11-25-unified-harbor-redesign-design.md) verbatim.
- **No schema or backup regressions:** Room migrations, SQLite table schemas, `GroveLevel`, `DesktopHarborState`, and JSON cross-platform backup import/export MUST remain backward compatible.
- **Preserve all platform invariants:** Keep desktop shortcuts (`Ctrl+1–7`, `Ctrl+K`, `Ctrl+I`, `Space`), compact window usability (`640×450 dp`), single-instance Win32 `FindWindow` title consistency in `Main.kt`, and Android `48.dp` minimum touch targets.
- **AI identity:** User-facing AI labels are **`Captain`** (nav/tab) and **`Harbor Captain`** (header/contextual cards), styled strictly with `tokens.captain` / `tokens.captainSoft`.
- **Floor bans (verified by `AnchorDesignSystemLintTest`):** Zero `Color(0x...)` / `Color.White` / `Color.Black` outside `ui/theme/`; zero tracked-caps labels (`letterSpacing` on labels/headers); zero cards inside cards; zero dark-mode glows/sheens (`beaconGlow`, `harborGlow`, `topSheen`); zero emoji UI icons; zero `harborEntrance` staggered rises; zero `GardenScene` / `DesktopHarborScene`; zero unguarded `rememberInfiniteTransition` (only `FocusCanvas` tide wave, gated on `!LocalReducedMotion.current`).
- **Verification commands:** Run `./gradlew :shared:desktopTest`, `./gradlew :desktopApp:test :desktopApp:ktlintCheck`, and `./gradlew :app:testDebugUnitTest` after each task that touches the corresponding module.

---

## File Structure Map

- **Locked system & licenses:** `DESIGN.md`, `licenses/OFL-1.1.txt`, `licenses/THIRD_PARTY_NOTICES.txt`, `docs/license-audit.md`, `docs/illustration-style.md`
- **Font resources:** `app/src/main/res/font/newsreader_medium.ttf`, `newsreader_semibold.ttf`, `desktopApp/src/main/resources/font/newsreader_medium.ttf`, `newsreader_semibold.ttf` (retire `nunito_*.ttf` in Task 11)
- **`:shared` domain:** `shared/src/commonMain/kotlin/com/anchor/adhd/domain/AppearanceMode.kt` (keep `GroveLevel.kt` intact)
- **`:shared` theme:** `AnchorColors.kt`, `AnchorTokens.kt`, `AnchorColorSchemes.kt`, `AnchorTypography.kt`, `AnchorSpacing.kt`, `AnchorShapes.kt`, `AnchorMotion.kt`, `AnchorTheme.kt` (delete `AnchorGradients.kt`, `AnchorDepth.kt` in Task 11)
- **`:shared` components:** `AnchorBeaconPlate.kt`, `AnchorFocalPlate.kt`, `AnchorWell.kt`, `AnchorListRow.kt`, `AnchorSectionHeader.kt`, `AnchorWeeklyTide.kt`, `HarborCtaButton.kt`, `AnchorQuietButton.kt`, `AnchorChip.kt`, `HarborEmptyState.kt`, `HarborModalSheet.kt`, `FocusCanvas.kt` (delete `GardenScene.kt` in Task 6)
- **`:shared` tests:** `AnchorTokensContrastTest.kt`, `AnchorDesignSystemLintTest.kt`, `SharedDomainTest.kt`
- **`:app` theme & entry:** `MainActivity.kt`, `ui/theme/Theme.kt`, `AnchorFonts.kt`, `Type.kt`, `data/prefs/UserPreferences.kt`, `ui/vm/AnchorViewModel.kt`, `ui/AnchorShell.kt`, `ui/AnchorRoot.kt`, `ui/copy/AppCopy.kt`
- **`:app` screens:** `ui/grove/GroveHomeScreen.kt`, `ui/grove/FunLinksStrip.kt`, `ui/focus/FocusScreen.kt`, `ui/focus/AppBlockerScreen.kt`, `ui/focus/ShieldRescueOverlay.kt`, `ui/plan/PlanScreen.kt`, `ui/ai/AiScreen.kt`, `ui/chat/ChatScreen.kt`, `ui/components/AirlockSheet.kt`, `ui/components/AnchorComponents.kt`, `ui/components/DayTimeline.kt`, `ui/components/WorkloadBanner.kt`, `ui/components/VitalityBar.kt`, `ui/more/MoreScreen.kt`, `ui/grow/HabitsScreen.kt`, `ui/grow/HabitHeatmap.kt`, `ui/grow/StrategiesScreen.kt`, `ui/settings/SettingsScreen.kt`, `debug/.../DesignPreviews.kt`
- **`:desktopApp` platform, prefs & theme:** `platform/DesktopSystemAppearance.kt`, `prefs/DesktopUserPreferences.kt`, `theme/AnchorTheme.kt`, `theme/DesktopFonts.kt`, `db/AnchorDesktopDatabase.kt`, `Main.kt`
- **`:desktopApp` screens & tests:** `ui/DesktopNavigationSidebar.kt`, `ui/DesktopHomeDeck.kt`, `ui/DesktopNowCard.kt`, `ui/DesktopFocusScreen.kt`, `ui/DesktopFloatingIsland.kt`, `ui/DesktopPlanScreen.kt`, `ui/DesktopSyllabusScreen.kt`, `ui/DesktopAiScreen.kt`, `ui/DesktopAirlockDialog.kt`, `ui/DesktopSmartOmniBar.kt`, `ui/DesktopBlockerScreen.kt`, `blocker/DesktopRescueWindow.kt`, `ui/DesktopSettingsScreen.kt`, `ui/DesktopFunLinks.kt` (delete `DesktopHarborScene.kt` in Task 6), `test/.../DesktopRedesignSnapshotTest.kt`

---

## Phase 1: Foundation & Governance

### Task 1: Bundle OFL Newsreader Fonts, Update License Notices & Platform Font Loaders

**Files:**
- Create: `app/src/main/res/font/newsreader_medium.ttf`
- Create: `app/src/main/res/font/newsreader_semibold.ttf`
- Create: `desktopApp/src/main/resources/font/newsreader_medium.ttf`
- Create: `desktopApp/src/main/resources/font/newsreader_semibold.ttf`
- Create: `licenses/OFL-1.1.txt`
- Modify: `licenses/THIRD_PARTY_NOTICES.txt`
- Modify: `docs/license-audit.md`
- Modify: `app/src/main/java/com/anchor/adhd/ui/theme/AnchorFonts.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/theme/Type.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/DesktopFonts.kt`
- Test: `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopFontsTest.kt`

**Interfaces:**
- Produces: Android `NewsreaderFontFamily`, `AtkinsonFontFamily`, `DisplayFontFamily`, `BodyFontFamily` in `com.anchor.adhd.ui.theme`; Desktop `NewsreaderFontFamily`, `AtkinsonFontFamily` in `com.anchor.adhd.desktop.theme`.

- [ ] **Step 1: Download official OFL Newsreader Medium (500) and SemiBold (600) static TTFs and OFL-1.1 license text**

```bash
curl -fsSL "https://github.com/google/fonts/raw/main/ofl/newsreader/static/Newsreader_24pt-Medium.ttf" \
  -o app/src/main/res/font/newsreader_medium.ttf
curl -fsSL "https://github.com/google/fonts/raw/main/ofl/newsreader/static/Newsreader_24pt-SemiBold.ttf" \
  -o app/src/main/res/font/newsreader_semibold.ttf
cp app/src/main/res/font/newsreader_medium.ttf desktopApp/src/main/resources/font/newsreader_medium.ttf
cp app/src/main/res/font/newsreader_semibold.ttf desktopApp/src/main/resources/font/newsreader_semibold.ttf
curl -fsSL "https://github.com/google/fonts/raw/main/ofl/newsreader/OFL.txt" -o licenses/OFL-1.1.txt
```

Verify both TTF headers (`file app/src/main/res/font/newsreader_*.ttf` reports TrueType Font data, non-empty).

- [ ] **Step 2: Write failing font resource loading test on Desktop**

Create `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopFontsTest.kt`:

```kotlin
package com.anchor.adhd.desktop

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopFontsTest {
    @Test
    fun newsreaderAndAtkinsonFontResourcesExist() {
        val loader = DesktopFontsTest::class.java.classLoader
        val required = listOf(
            "font/newsreader_medium.ttf",
            "font/newsreader_semibold.ttf",
            "font/atkinson_hyperlegible_regular.ttf",
            "font/atkinson_hyperlegible_bold.ttf",
            "OFL-1.1.txt",
        )
        required.forEach { path ->
            val stream = loader.getResourceAsStream(path)
            assertNotNull("Missing bundled resource: $path", stream)
            assertTrue("Empty resource: $path", (stream?.readBytes()?.size ?: 0) > 100)
        }
    }
}
```

- [ ] **Step 3: Update `AnchorFonts.kt`, `Type.kt`, `DesktopFonts.kt`, `licenses/THIRD_PARTY_NOTICES.txt`, and `docs/license-audit.md`**

Update `app/src/main/java/com/anchor/adhd/ui/theme/AnchorFonts.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.anchor.adhd.R

val NewsreaderFontFamily = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.ExtraBold, FontStyle.Normal),
)

val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val DisplayFontFamily: FontFamily get() = NewsreaderFontFamily
val BodyFontFamily: FontFamily get() = AtkinsonFontFamily
```

Update `app/src/main/java/com/anchor/adhd/ui/theme/Type.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.material3.Typography

fun anchorTypography(base: Typography = Typography()) =
    anchorTypography(display = NewsreaderFontFamily, body = AtkinsonFontFamily, base = base)
```

Update `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/DesktopFonts.kt`:

```kotlin
package com.anchor.adhd.desktop.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

val NewsreaderFontFamily = FontFamily(
    Font(resource = "font/newsreader_medium.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resource = "font/newsreader_medium.ttf", weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.ExtraBold, style = FontStyle.Normal),
)

val AtkinsonFontFamily = FontFamily(
    Font(resource = "font/atkinson_hyperlegible_regular.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resource = "font/atkinson_hyperlegible_italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resource = "font/atkinson_hyperlegible_bold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resource = "font/atkinson_hyperlegible_bold_italic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
)
```

Add `Newsreader` (Production Type, SIL OFL 1.1) and `Atkinson Hyperlegible` (Braille Institute, SIL OFL 1.1) entries to `licenses/THIRD_PARTY_NOTICES.txt` (near top header) and `docs/license-audit.md`. Update `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt` to reference `NewsreaderFontFamily`.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :desktopApp:test --tests "com.anchor.adhd.desktop.DesktopFontsTest" :app:compileDebugKotlin --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/font/newsreader_*.ttf desktopApp/src/main/resources/font/newsreader_*.ttf \
  licenses/OFL-1.1.txt licenses/THIRD_PARTY_NOTICES.txt docs/license-audit.md \
  app/src/main/java/com/anchor/adhd/ui/theme/AnchorFonts.kt app/src/main/java/com/anchor/adhd/ui/theme/Type.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/DesktopFonts.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt \
  desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopFontsTest.kt
git commit -m "Bundle Newsreader OFL display fonts and wire platform font loaders"
```

---

### Task 2: Unified `:shared` Token Contract, Color Schemes, Typography, Motion & Shared `AnchorTheme`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorTokens.kt`
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorShapes.kt`
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorTheme.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorColors.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorColorSchemes.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorTypography.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorSpacing.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorMotion.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/theme/Theme.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt`
- Test: `shared/src/commonTest/kotlin/com/anchor/adhd/ui/theme/AnchorTokensContrastTest.kt`

**Interfaces:**
- Produces:
  - `AnchorTokens` + `MorningHarborTokens` + `NightHarborTokens` + `LocalAnchorTokens`
  - `AnchorShapes`
  - `LocalReducedMotion`
  - `AnchorTheme(darkTheme: Boolean, displayFont: FontFamily, bodyFont: FontFamily, reducedMotion: Boolean = false, content: @Composable () -> Unit)` in `com.anchor.adhd.ui.theme`
  - `anchorLightColorScheme()` / `anchorDarkColorScheme()` backed by `MorningHarborTokens` / `NightHarborTokens`
  - `anchorTypography(display: FontFamily, body: FontFamily, base: Typography = Typography())`

- [ ] **Step 1: Write failing WCAG contrast & token invariant test**

Create `shared/src/commonTest/kotlin/com/anchor/adhd/ui/theme/AnchorTokensContrastTest.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorTokensContrastTest {
    private fun relLuminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = relLuminance(fg)
        val l2 = relLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun morningAndNightTokensMeetWcagAndAvoidPureAbsolutes() {
        listOf(MorningHarborTokens, NightHarborTokens).forEach { t ->
            assertFalse(t.paper == Color.Black || t.paper == Color.White)
            assertFalse(t.ink == Color.Black || t.ink == Color.White)
            assertTrue(contrast(t.ink, t.paper) >= 10.0, "ink/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.muted, t.paper) >= 4.5, "muted/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.muted, t.paper2) >= 4.5, "muted/paper2 low in dark=${t.isDark}")
            assertTrue(contrast(t.beaconInk, t.beacon) >= 4.5, "beaconInk/beacon low in dark=${t.isDark}")
            assertTrue(contrast(t.captain, t.paper) >= 4.5, "captain/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.clayRose, t.paper) >= 4.5, "clayRose/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.fieldEdge, t.paper) >= 3.0, "fieldEdge/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.beaconPlateInk, t.beaconPlate) >= 10.0, "beaconPlateInk contrast")
            assertTrue(contrast(t.beaconInk, t.beaconPlateCta) >= 7.0, "beaconPlateCta contrast")
        }
        assertTrue(relLuminance(NightHarborTokens.paper2) > relLuminance(NightHarborTokens.paper))
        assertTrue(relLuminance(NightHarborTokens.paper3) > relLuminance(NightHarborTokens.paper2))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.anchor.adhd.ui.theme.AnchorTokensContrastTest" --console=plain`
Expected: FAIL (`Unresolved reference: MorningHarborTokens`).

- [ ] **Step 3: Implement `AnchorTokens.kt`, `AnchorShapes.kt`, `AnchorTheme.kt`, and update `AnchorColors.kt`, `AnchorColorSchemes.kt`, `AnchorTypography.kt`, `AnchorSpacing.kt`, `AnchorMotion.kt`**

Create `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorTokens.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AnchorTokens(
    val isDark: Boolean,
    val paper: Color,
    val surface: Color,
    val paper2: Color,
    val paper3: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val rule: Color,
    val fieldEdge: Color,
    val primary: Color,
    val primarySoft: Color,
    val beacon: Color,
    val beaconInk: Color,
    val captain: Color,
    val captainInk: Color,
    val captainSoft: Color,
    val sage: Color,
    val sageSoft: Color,
    val clayRose: Color,
    val clayRoseSoft: Color,
    val beaconPlate: Color,
    val beaconPlateBorder: Color,
    val beaconPlateInk: Color,
    val beaconPlateInk2: Color,
    val beaconPlatePill: Color,
    val beaconPlateCta: Color,
    val beaconPlateCaptain: Color,
    val tideBack: Color,
    val tideWave1: Color,
    val tideWave2: Color,
    val tideBreakWave1: Color,
    val tideBreakWave2: Color,
)

val MorningHarborTokens = AnchorTokens(
    isDark = false,
    paper = Color(0xFFF6F1E7),
    surface = Color(0xFFFBF8F1),
    paper2 = Color(0xFFECE5D6),
    paper3 = Color(0xFFE2D9C6),
    ink = Color(0xFF1B2A3A),
    ink2 = Color(0xFF3A4B5E),
    muted = Color(0xFF526375),
    rule = Color(0xFFD7CEBC),
    fieldEdge = Color(0xFF7D8B99),
    primary = Color(0xFF256280),
    primarySoft = Color(0xFFDCEAF2),
    beacon = Color(0xFFDF8A3B),
    beaconInk = Color(0xFF1A1206),
    captain = Color(0xFF0D7366),
    captainInk = Color(0xFF07302B),
    captainSoft = Color(0xFFD7EFEA),
    sage = Color(0xFF3E6E4E),
    sageSoft = Color(0xFFDCECE2),
    clayRose = Color(0xFFB35252),
    clayRoseSoft = Color(0xFFF4DDDD),
    beaconPlate = Color(0xFF15263B),
    beaconPlateBorder = Color(0xFF243A54),
    beaconPlateInk = Color(0xFFF6F1E7),
    beaconPlateInk2 = Color(0xFFC2D0DF),
    beaconPlatePill = Color(0xFF213752),
    beaconPlateCta = Color(0xFFF0A35E),
    beaconPlateCaptain = Color(0xFF67D8D4),
    tideBack = Color(0xFFEFE9DC),
    tideWave1 = Color(0xFFA9CBE0),
    tideWave2 = Color(0xFF5B8EAC),
    tideBreakWave1 = Color(0xFFB7DEC6),
    tideBreakWave2 = Color(0xFF5B9A73),
)

val NightHarborTokens = AnchorTokens(
    isDark = true,
    paper = Color(0xFF0E1624),
    surface = Color(0xFF152032),
    paper2 = Color(0xFF152032),
    paper3 = Color(0xFF1E2E46),
    ink = Color(0xFFE8EDF3),
    ink2 = Color(0xFFBDCAD8),
    muted = Color(0xFF91A3B8),
    rule = Color(0xFF25354B),
    fieldEdge = Color(0xFF586C85),
    primary = Color(0xFF8AB4E8),
    primarySoft = Color(0xFF1B2E47),
    beacon = Color(0xFFF0A35E),
    beaconInk = Color(0xFF1A1206),
    captain = Color(0xFF67D8D4),
    captainInk = Color(0xFF07302B),
    captainSoft = Color(0xFF113035),
    sage = Color(0xFF72C29B),
    sageSoft = Color(0xFF163326),
    clayRose = Color(0xFFE89B9B),
    clayRoseSoft = Color(0xFF3B1E22),
    beaconPlate = Color(0xFF182B44),
    beaconPlateBorder = Color(0xFF2D4769),
    beaconPlateInk = Color(0xFFF2F6FA),
    beaconPlateInk2 = Color(0xFFC4D2E0),
    beaconPlatePill = Color(0xFF243C5C),
    beaconPlateCta = Color(0xFFF0A35E),
    beaconPlateCaptain = Color(0xFF67D8D4),
    tideBack = Color(0xFF122033),
    tideWave1 = Color(0xFF1E4368),
    tideWave2 = Color(0xFF2A5B84),
    tideBreakWave1 = Color(0xFF1C4736),
    tideBreakWave2 = Color(0xFF2C6B50),
)

val LocalAnchorTokens = staticCompositionLocalOf { MorningHarborTokens }
```

Create `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorShapes.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AnchorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

Create `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorTheme.kt`:

```kotlin
package com.anchor.adhd.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SharedAnchorTheme(
    darkTheme: Boolean,
    displayFont: FontFamily,
    bodyFont: FontFamily,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
    val scheme = if (darkTheme) anchorDarkColorScheme() else anchorLightColorScheme()
    CompositionLocalProvider(
        LocalAnchorTokens provides tokens,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = anchorTypography(display = displayFont, body = bodyFont),
            shapes = AnchorShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides tokens.ink) {
                content()
            }
        }
    }
}
```

Update `AnchorColorSchemes.kt` to build `anchorLightColorScheme()` from `MorningHarborTokens` and `anchorDarkColorScheme()` from `NightHarborTokens`.
Update `AnchorTypography.kt` to match the exact `Newsreader` (500) + `Atkinson Hyperlegible` (400/700) fixed 1.15–1.2 app scale in `DESIGN.md` (with `tnum` on `displayLarge` `52.sp` and `displaySmall` `28.sp`, and `0.sp` letter spacing on all `label*` and `titleSmall`).
Update `AnchorSpacing.kt` with `radiusPlate = 14.dp`, `radiusWell = 14.dp`, `radiusButton = 12.dp`, `radiusChip = 10.dp`, `radiusSheet = 24.dp`, `radiusPill = 999.dp`, keeping `radiusCard = 14.dp` alias for smooth migration.
Update `AnchorMotion.kt` to define `MICRO_MS = 120`, `SHORT_MS = 220`, `SHEET_MS = 320`, `TIDE_MS = 10000`, `PRESS_SCALE = 0.985f`, and `val LocalReducedMotion = staticCompositionLocalOf { false }`.
Update `app/src/main/java/com/anchor/adhd/ui/theme/Theme.kt` and `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt` to delegate to `SharedAnchorTheme(...)` (preserving `LocalAnchorExtras` only until Task 11 removes legacy callers).

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :shared:desktopTest :desktopApp:test :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/ \
  shared/src/commonTest/kotlin/com/anchor/adhd/ui/theme/AnchorTokensContrastTest.kt \
  app/src/main/java/com/anchor/adhd/ui/theme/Theme.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt
git commit -m "Consolidate Morning and Night Harbor tokens, typography, and SharedAnchorTheme"
```

---

### Task 3: Shared UI Primitives & Living Tide `FocusCanvas`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/AnchorSurfaces.kt` (`AnchorBeaconPlate`, `AnchorFocalPlate`, `AnchorWell`, `AnchorListRow`, `AnchorHairline`, `AnchorSectionHeader`)
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/AnchorControls.kt` (`AnchorQuietButton`, `AnchorChip`)
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/AnchorTideVisuals.kt` (`WeeklyTideDay`, `AnchorWeeklyTide`, `AnchorTodayTideStrip`)
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/HarborCtaButton.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/HarborEmptyState.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/HarborModalSheet.kt`
- Modify: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/focus/FocusCanvas.kt`
- Test: `shared/src/commonTest/kotlin/com/anchor/adhd/ui/components/AnchorTideVisualsTest.kt`

**Interfaces:**
- Consumes: `LocalAnchorTokens`, `LocalReducedMotion`, `AnchorSpacing`, `AnchorMotion`, `GroveLevel`
- Produces:
  - `AnchorBeaconPlate(modifier, content)` — Deep-Harbor navy focal plate (`tokens.beaconPlate` + `1.dp` `tokens.beaconPlateBorder` + `2.dp` top `tokens.beaconPlateCta` ridge, `14.dp` radius)
  - `AnchorFocalPlate(modifier, content)` — `tokens.surface` + `1.dp` `tokens.rule` (`14.dp` radius)
  - `AnchorWell(modifier, tone: AnchorWellTone = AnchorWellTone.Neutral, content)` — borderless `tokens.paper2` or `tokens.captainSoft` (`14.dp` radius)
  - `AnchorHairline(modifier)` — `HorizontalDivider(thickness = 1.dp, color = tokens.rule)`
  - `AnchorListRow(title, subtitle, trailingText, onClick, leading, trailing, minHeight, showDivider)`
  - `AnchorSectionHeader(title, trailingLabel, onTrailingClick, modifier)`
  - `AnchorWeeklyTide(days: List<WeeklyTideDay>, stageLabel: String, totalMinutes: Int, todayMinutes: Int, deflectedCount: Int, modifier)`
  - `AnchorTodayTideStrip(todayMinutes: Int, goalMinutes: Int, stageLabel: String, modifier)`
  - `HarborCtaButton(onClick, modifier, shape, enabled, onBeaconPlate: Boolean = false, content)` — solid amber fill, `12.dp` radius, `0.985f` press scale at `120ms` when `!LocalReducedMotion.current`
  - `AnchorQuietButton(label, onClick, modifier, outlined: Boolean = true, tint: Color = ...)`
  - `AnchorChip(label, selected, onClick, modifier, captainAccent: Boolean = false)`
  - `HarborEmptyState(title, message, modifier, actionLabel, onAction)` — static `44.dp` geometric tide-ring + beacon-dot mark
  - `FocusCanvas(progress, modifier, isWorkPhase, remainingSeconds, taskTitle)` — Living Tide water waves inside circular ring using `LocalAnchorTokens` and respecting `LocalReducedMotion`

- [ ] **Step 1: Write failing unit test for Weekly Tide bar height normalization**

Create `shared/src/commonTest/kotlin/com/anchor/adhd/ui/components/AnchorTideVisualsTest.kt`:

```kotlin
package com.anchor.adhd.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnchorTideVisualsTest {
    @Test
    fun tideBarFractionsScaleRelativeToPeakWithMinimumFloor() {
        val days = listOf(
            WeeklyTideDay("M", 0, false),
            WeeklyTideDay("T", 30, false),
            WeeklyTideDay("W", 60, true),
        )
        val fractions = computeTideBarFractions(days, minScaleMinutes = 60)
        assertEquals(0f, fractions[0])
        assertEquals(0.5f, fractions[1], 0.001f)
        assertEquals(1.0f, fractions[2], 0.001f)
    }

    @Test
    fun formatTideDurationHandlesMinutesAndHours() {
        assertEquals("0m", formatTideDuration(0))
        assertEquals("45m", formatTideDuration(45))
        assertEquals("4h 10m", formatTideDuration(250))
        assertTrue(formatTideDuration(120) == "2h")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.anchor.adhd.ui.components.AnchorTideVisualsTest" --console=plain`
Expected: FAIL (`Unresolved reference: WeeklyTideDay`).

- [ ] **Step 3: Implement shared primitives and refactor `HarborCtaButton`, `HarborEmptyState`, `HarborModalSheet`, and Living Tide `FocusCanvas`**

Implement:
- `AnchorSurfaces.kt`: `AnchorBeaconPlate` draws a `Surface(color = tokens.beaconPlate, border = BorderStroke(1.dp, tokens.beaconPlateBorder), shape = RoundedCornerShape(14.dp))` with a `drawWithContent` `2.dp` top edge in `tokens.beaconPlateCta.copy(alpha = 0.65f)`. `AnchorFocalPlate` uses `tokens.surface` + `1.dp` `tokens.rule`. `AnchorWell` uses `tokens.paper2` (or `tokens.captainSoft` for `AnchorWellTone.Captain`), zero border. `AnchorSectionHeader` renders sentence-case `titleSmall` with `semantics { heading() }`.
- `AnchorControls.kt`: `AnchorQuietButton` and `AnchorChip` with `10.dp`/`12.dp` radii, `tnum` figures, crisp `tokens.rule` / `tokens.primary` / `tokens.captain` borders.
- `AnchorTideVisuals.kt`: `WeeklyTideDay`, `computeTideBarFractions()`, `formatTideDuration()`, `AnchorWeeklyTide` (7-bar chart with today highlighted in `tokens.beacon`, other days in `tokens.primary`, tabular footer), and `AnchorTodayTideStrip`.
- `HarborCtaButton.kt`: solid `if (onBeaconPlate) tokens.beaconPlateCta else tokens.beacon`, `tokens.beaconInk`, `MutableInteractionSource` press state scaling to `0.985f` via `animateFloatAsState(tween(AnchorMotion.MICRO_MS))` when `!LocalReducedMotion.current`; zero `beaconGlow`, zero `AnchorGradients`.
- `HarborEmptyState.kt`: static `44.dp` `Canvas` drawing two concentric `1.5.dp` hairline rings (`tokens.rule`) + a `4.dp` center `tokens.beacon` dot, plain title, one-sentence message, optional single button.
- `HarborModalSheet.kt`: `containerColor = tokens.surface`, `scrimColor = tokens.paper.copy(alpha = 0.65f)`, `36.dp × 4.dp` `tokens.rule` drag handle (zero hardcoded hex, zero gradient).
- `FocusCanvas.kt`: read `val tokens = LocalAnchorTokens.current` and `val reducedMotion = LocalReducedMotion.current`. Only run `rememberInfiniteTransition` when `!reducedMotion` (else static `phase = 0f` and flat horizon). Draw `tokens.paper3` outer track, solid `if (isWorkPhase) tokens.beacon else tokens.sage` progress arc, clipped circular `tokens.tideBack` interior with rising double-wave water paths (`tokens.tideWave1`/`tideWave2` on work, `tokens.tideBreakWave1`/`tideBreakWave2` on break) proportional to `clampedProgress`, centered `Newsreader` `displayLarge` (`tnum`) timer readout in `tokens.ink`. Remove outer breathing halo circles and hardcoded `#1E293B` / `#38BDF8` / `#F59E0B` literals.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :shared:desktopTest :desktopApp:test :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/ \
  shared/src/commonMain/kotlin/com/anchor/adhd/ui/focus/FocusCanvas.kt \
  shared/src/commonTest/kotlin/com/anchor/adhd/ui/components/AnchorTideVisualsTest.kt
git commit -m "Add shared Harbor primitives, Weekly Tide visuals, and Light/Dark Living Tide FocusCanvas"
```

---

### Task 4: `AppearanceMode`, Platform System Detection & Automated Compose Hallmark Lint Gate

**Files:**
- Create: `shared/src/commonMain/kotlin/com/anchor/adhd/domain/AppearanceMode.kt`
- Create: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/platform/DesktopSystemAppearance.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/prefs/DesktopUserPreferences.kt`
- Modify: `app/src/main/java/com/anchor/adhd/data/prefs/UserPreferences.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/vm/AnchorViewModel.kt`
- Modify: `app/src/main/java/com/anchor/adhd/MainActivity.kt`
- Create: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`
- Test: `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopAppearancePreferenceTest.kt`

**Interfaces:**
- Produces:
  - `enum class AppearanceMode { SYSTEM, LIGHT, DARK }` with `AppearanceMode.fromStorage(raw: String?): AppearanceMode` and `fun resolveDarkTheme(systemDark: Boolean): Boolean`
  - Android `UserPreferences.appearanceMode: Flow<AppearanceMode>` + `suspend fun setAppearanceMode(mode: AppearanceMode)`
  - Desktop `DesktopUserPreferences.appearanceModeFlow: Flow<AppearanceMode>` + `suspend fun setAppearanceMode(mode: AppearanceMode)`
  - `DesktopSystemAppearance.isSystemDarkTheme(): Boolean` and `DesktopSystemAppearance.isReducedMotionEnabled(): Boolean`
  - `AnchorDesignSystemLintTest` scanning all UI source trees with an explicit temporary `UNMIGRATED_ALLOWLIST` (to be emptied in Task 12)

- [ ] **Step 1: Write failing tests for `AppearanceMode` persistence and `AnchorDesignSystemLintTest`**

Create `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopAppearancePreferenceTest.kt`:

```kotlin
package com.anchor.adhd.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.domain.AppearanceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopAppearancePreferenceTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun appearanceModeDefaultsToSystemAndPersistsChanges() = runTest {
        val file = tempFolder.newFile("appearance.preferences_pb")
        val ds = PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() }
        val prefs = DesktopUserPreferences(ds)

        assertEquals(AppearanceMode.SYSTEM, prefs.appearanceModeFlow.first())
        assertTrue(AppearanceMode.SYSTEM.resolveDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.LIGHT.resolveDarkTheme(systemDark = true))
        assertTrue(AppearanceMode.DARK.resolveDarkTheme(systemDark = false))

        prefs.setAppearanceMode(AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, prefs.appearanceModeFlow.first())
    }
}
```

- [ ] **Step 2: Implement `AppearanceMode.kt`, `DesktopSystemAppearance.kt`, prefs wiring, adaptive `MainActivity.kt` system bars, and `AnchorDesignSystemLintTest.kt`**

Create `shared/src/commonMain/kotlin/com/anchor/adhd/domain/AppearanceMode.kt`:

```kotlin
package com.anchor.adhd.domain

enum class AppearanceMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Morning Harbor"),
    DARK("Night Harbor");

    fun resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): AppearanceMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}
```

Create `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/platform/DesktopSystemAppearance.kt`:

```kotlin
package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference

object DesktopSystemAppearance {
    private const val PERSONALIZE_KEY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val APPS_USE_LIGHT_THEME = "AppsUseLightTheme"
    private const val SPI_GETCLIENTAREAANIMATION = 0x1042

    fun isSystemDarkTheme(): Boolean {
        if (!Platform.isWindows()) return false
        return runCatching {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                PERSONALIZE_KEY,
                APPS_USE_LIGHT_THEME,
            ) == 0
        }.getOrDefault(false)
    }

    fun isReducedMotionEnabled(): Boolean {
        if (!Platform.isWindows()) return false
        return runCatching {
            val enabled = IntByReference(1)
            val ok = User32.INSTANCE.SystemParametersInfo(
                WinDef.UINT(SPI_GETCLIENTAREAANIMATION.toLong()),
                WinDef.UINT(0),
                enabled.pointer,
                WinDef.UINT(0),
            )
            ok && enabled.value == 0
        }.getOrDefault(false)
    }
}
```

Wire `APPEARANCE_MODE = stringPreferencesKey("appearance_mode")` in both `DesktopUserPreferences` and Android `UserPreferences` + `AnchorViewModel`.
In `MainActivity.kt`, collect `container.preferences.appearanceMode`, compute `darkTheme = mode.resolveDarkTheme(isSystemInDarkTheme())`, update `enableEdgeToEdge(...)` inside `DisposableEffect(darkTheme)` with `SystemBarStyle.light(TRANSPARENT, TRANSPARENT)` vs `SystemBarStyle.dark(TRANSPARENT)`, and pass `darkTheme` to `AnchorTheme(darkTheme = darkTheme)`.

Create `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt` scanning:
- `shared/src/commonMain/kotlin/com/anchor/adhd/ui`
- `app/src/main/java/com/anchor/adhd/ui`
- `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui`
- `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/blocker/DesktopRescueWindow.kt`
- `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt`

Checks (excluding `ui/theme/` for color literals and `FunLinksSettingsSection.kt` preset list for user emojis):
1. `Color(0x...)`, `Color.White`, `Color.Black`
2. `letterSpacing\s*=` outside `ui/theme/`
3. Banned chrome emojis (`⚓`, `🌱`, `🛡`, `🔥`, `⚡`, `✨`, `💡`, `🎯`, `✎`)
4. Legacy helpers (`AnchorGradients`, `beaconGlow`, `harborGlow`, `topSheen`, `harborEntrance`, `GardenScene`, `DesktopHarborScene`)
5. `rememberInfiniteTransition` outside `FocusCanvas.kt` (and `FocusCanvas.kt` must reference `LocalReducedMotion`)

Include an explicit `UNMIGRATED_ALLOWLIST` of files to be cleared one-by-one through Tasks 5–12 so any newly migrated file is guarded immediately.

- [ ] **Step 3: Run tests to verify pass**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/anchor/adhd/domain/AppearanceMode.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/platform/DesktopSystemAppearance.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/prefs/DesktopUserPreferences.kt \
  desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopAppearancePreferenceTest.kt \
  app/src/main/java/com/anchor/adhd/data/prefs/UserPreferences.kt \
  app/src/main/java/com/anchor/adhd/ui/vm/AnchorViewModel.kt \
  app/src/main/java/com/anchor/adhd/MainActivity.kt
git commit -m "Add AppearanceMode, Windows JNA appearance/motion detection, and Compose Hallmark lint gate"
```

---

## Phase 2: Shell, Home & Focus (Both Platforms)

### Task 5: Unified Navigation Shells (`AnchorShell` & `DesktopNavigationSidebar` Compact Rail)

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/AnchorShell.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/AnchorRoot.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopNavigationSidebar.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt` (remove migrated files from allowlist)

**Interfaces:**
- Consumes: `LocalAnchorTokens`, `AppearanceMode`, `DesktopSystemAppearance`, `AnchorHairline`
- Produces: Unified destination labels (`Home`, `Plan`, `Focus`, `Blocker`, `Syllabus`, `Captain`, `Settings`, `More`), compact `<760.dp` desktop icon rail, theme-aware desktop root window wired to `DesktopUserPreferences.appearanceModeFlow`.

- [ ] **Step 1: Update `AnchorShell.kt` and `AnchorRoot.kt`**

- Change `ANCHOR_TAB_ITEMS` labels & icons to: `Home` (`Icons.Outlined.Home` / `Icons.Filled.Home`), `Plan` (`CalendarMonth`), `Focus` (`Timer`), `Captain` (`AutoAwesome`), `More` (`Apps`).
- Replace per-tab `tabBackground` gradient brush with flat `LocalAnchorTokens.current.paper` scaffold background.
- Style `AnchorBottomBar` with `tokens.paper2` container, `1.dp` top `AnchorHairline`, and `tokens.primarySoft` active indicator (with `tokens.captain` tint on the `Captain` tab).

- [ ] **Step 2: Update `DesktopNavigationSidebar.kt` and `Main.kt`**

- Update `DESKTOP_NAV_ITEMS` to: `Home`, `Plan`, `Focus`, `Blocker`, `Syllabus`, `Captain`, `Settings`.
- Add `compact: Boolean` parameter driven by `BoxWithConstraints` (`maxWidth < 760.dp`) in `Main.kt`:
  - Expanded: `200.dp` width, `tokens.paper2` background, `1.dp` right `tokens.rule` hairline, `Newsreader` `"Anchor"` wordmark + static `8.dp` `tokens.beacon` dot (delete `rememberInfiniteTransition` pulse!), `tokens.paper3` active item pill, quiet footer for `Airlock reset` (`Ctrl K`) and `Floating Island` (`Ctrl I`).
  - Compact (`< 760.dp`): `64.dp` icon rail with active `tokens.paper3` pill and accessible `contentDescription` / tooltip labels.
- In `Main.kt`, collect `prefs.appearanceModeFlow` and pass `darkTheme = appearanceMode.resolveDarkTheme(DesktopSystemAppearance.isSystemDarkTheme())` and `reducedMotion = DesktopSystemAppearance.isReducedMotionEnabled()` to `AnchorTheme`. Keep the single-instance Win32 window title constant in sync with `restoreExistingAnchorWindow()`. Replace all hardcoded dialog colors in `Main.kt` with `LocalAnchorTokens.current`.
- Remove `AnchorShell.kt`, `AnchorRoot.kt`, `DesktopNavigationSidebar.kt`, and `Main.kt` from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 3: Verify tests and ktlint**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/anchor/adhd/ui/AnchorShell.kt app/src/main/java/com/anchor/adhd/ui/AnchorRoot.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopNavigationSidebar.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt
git commit -m "Unify Android and Windows navigation chrome, Captain naming, and compact desktop rail"
```

---

### Task 6: Full Home Redesign, Weekly/Today Tide Data, and `GardenScene` Removal

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/db/AnchorDesktopDatabase.kt` (add `weeklyFocusDays: StateFlow<List<WeeklyTideDay>>` computed from `focus_sessions` on 4 AM logical day boundaries)
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopNowCard.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopHomeDeck.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt` (pass `weeklyFocusDays` to `DesktopHomeDeck`)
- Modify: `app/src/main/java/com/anchor/adhd/ui/grove/GroveHomeScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/copy/AppCopy.kt`
- Delete: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/grove/GardenScene.kt`
- Delete: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopHarborScene.kt`
- Modify: `shared/src/commonTest/kotlin/com/anchor/adhd/domain/SharedDomainTest.kt` (remove `calculateHarborPalette` assertion, keep `GroveLevel` and `TimeOfDay`)
- Modify: `app/src/debug/java/com/anchor/adhd/ui/preview/DesignPreviews.kt` (replace `GardenScene` preview with `AnchorBeaconPlate` + `FocusCanvas` Light/Dark previews)
- Modify: `docs/illustration-style.md` (document removal of drawn scene and adoption of Weekly Tide + Living Tide `FocusCanvas`)
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`
- Test: `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopWeeklyTideTest.kt`

**Interfaces:**
- Consumes: `AnchorBeaconPlate`, `AnchorWell`, `AnchorListRow`, `AnchorSectionHeader`, `AnchorWeeklyTide`, `AnchorTodayTideStrip`, `HarborCtaButton`, `AnchorQuietButton`, `AnchorChip`, `GroveLevel`, `LocalAnchorTokens`
- Produces: `AnchorDesktopDatabase.getLast7LogicalDaysFocusMinutes(nowMillis: Long): List<WeeklyTideDay>`

- [ ] **Step 1: Write failing test for 7-day logical focus aggregation in `AnchorDesktopDatabase`**

Create `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopWeeklyTideTest.kt` testing that `AnchorDesktopDatabase` aggregates completed `focus_sessions` into 7 `WeeklyTideDay` entries ending on today's 4 AM logical date, summing multiple sessions on the same day and marking only the final entry `isToday = true`.

- [ ] **Step 2: Implement 7-day focus aggregation in `AnchorDesktopDatabase.kt`**

Add a pure query method querying `focus_sessions` (`WHERE endedAtMillis >= sevenDaysStartMillis`), bucketed by Anchor's existing 4 AM logical date helper into 7 ordered `WeeklyTideDay(shortLabel, minutes, isToday)` entries, exposed via `weeklyFocusDays: StateFlow<List<WeeklyTideDay>>` and refreshed alongside `todayFocusMinutes`.

- [ ] **Step 3: Redesign `DesktopNowCard.kt`, `DesktopHomeDeck.kt`, `GroveHomeScreen.kt`, and `AppCopy.kt`, and remove `GardenScene`**

- **`DesktopNowCard.kt`:** Wrap in `AnchorBeaconPlate`. Remove `rememberInfiniteTransition` pulse, `beaconGlow`, `AnchorGradients.harborHero`, `topSheen`, `letterSpacing = 1.sp`, and `✎` glyph. Display sentence-case `"Next action"` (`tokens.beaconPlateCta`), tabular duration chip (`tokens.beaconPlatePill`), bold `Atkinson` task title (`tokens.beaconPlateInk`), optional first step (`tokens.beaconPlateInk2`), `HarborCtaButton(onBeaconPlate = true)` `"Start X min focus"`, and quiet `tokens.beaconPlateCaptain` secondary action.
- **`DesktopHomeDeck.kt`:** Remove `DesktopHarborScene` and all `harborEntrance` / tracked uppercase headers (`"MOMENTUM SPARKS"`, `"INSTANT THOUGHT CAPTURE"`) / `⚓` emoji. Layout: top `Newsreader` greeting + factual summary + `DesktopSmartOmniBar`; `>= 860.dp` 2-column grid (left: `DesktopNowCard`, `AnchorWell` Quick start chips, `AnchorSectionHeader("Today's plan")` + `AnchorListRow` rows; right: `AnchorWeeklyTide`, `AnchorWell` Quick capture, `AnchorWell(Captain)` contextual Harbour Captain note, optional Fun Links); stacks vertically under `860.dp`.
- **`GroveHomeScreen.kt`:** Remove hardcoded `0xFF141F32` / `0xFF0D1522` / `Color.White` dark box, `beaconGlow`, `topSheen`, `harborEntrance`, `"FOCUS ACTIVE"` / `"ANCHOR BEACON"` tracked caps. Open with `Newsreader` greeting + `AnchorTodayTideStrip` + `AnchorBeaconPlate` NOW card, followed by `AnchorWell` / `AnchorListRow` bands for Quick start, Later today, Check-in, Daily strategy, and Fun Links—working seamlessly in both Morning Harbor Light and Night Harbor Dark!
- **`AppCopy.kt`:** Clean repeated middle-dot / arrow joins (`"Plan → Replan"` → `"Open Replan in Plan"`, `"End early · still logged"` → `"End early (still logged)"`).
- Delete `GardenScene.kt` and `DesktopHarborScene.kt`, update `SharedDomainTest.kt`, `DesignPreviews.kt`, `docs/illustration-style.md`, and remove migrated files from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 4: Run all test suites**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Redesign Windows and Android Home with Committed NOW plate, Weekly Tide, and drop GardenScene"
```

---

### Task 7: Full Focus Screen & Floating Island Redesign (Both Platforms)

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/focus/FocusScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopFocusScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopFloatingIsland.kt`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`

- [ ] **Step 1: Redesign `FocusScreen.kt` and `DesktopFocusScreen.kt`**

- Center Living Tide `FocusCanvas` with generous breathing room, `Atkinson` task title, single `HarborCtaButton` Start/Pause CTA, and quiet `AnchorQuietButton` secondary controls (End early, +5m, Duration, Fullscreen/Island, Airlock).
- Replace `"⚓ Anchor Session Complete! Harbor Vitality Earned"` and tracked-caps headers (`letterSpacing = 1.5.sp` / `1.2.sp`) with calm sentence-case typography and a Material `Icons.Default.CheckCircle` tinted in `tokens.sage`.
- Render micro-step checklists as `AnchorListRow` items inside a borderless `AnchorWell`. Eliminate all `Color(0x...)` and `Color.White` literals.

- [ ] **Step 2: Redesign `DesktopFloatingIsland.kt`**

- Pass resolved `darkTheme` + `reducedMotion` into `DesktopFloatingIslandWindow` from `Main.kt`.
- Style the pill with `tokens.paper2` surface, `1.dp` border (`tokens.sage` when focus active, `tokens.rule` when paused/idle), `tnum` `MM:SS` text in `tokens.ink`, and clean Material icon buttons.
- Remove `FocusScreen.kt`, `DesktopFocusScreen.kt`, and `DesktopFloatingIsland.kt` from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 3: Run tests and ktlint**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/anchor/adhd/ui/focus/FocusScreen.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopFocusScreen.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopFloatingIsland.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt
git commit -m "Redesign Android and Windows Focus screens and theme-aware Floating Island"
```

---

## Phase 3: Plan, Syllabus, Captain & Airlock (Both Platforms)

### Task 8: Full Plan & Syllabus Redesign (`PlanScreen`, `DesktopPlanScreen`, `DesktopSyllabusScreen`)

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/plan/PlanScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/components/DayTimeline.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/components/WorkloadBanner.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopPlanScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopSyllabusScreen.kt`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`

- [ ] **Step 1: Redesign Android `PlanScreen.kt`, `DayTimeline.kt`, and `WorkloadBanner.kt`**

- Replace stacked `AnchorCard` lists with `AnchorListRow` + `AnchorHairline`, use `AnchorChip` for view/tab controls, `AnchorWell` for quick-add and capacity/workload banners, and `HarborEmptyState` for empty lists.
- Replace raw `Color(0x...)` in `DayTimeline.kt` with `LocalAnchorTokens.current` (`primary`, `primarySoft`, `paper2`, `rule`, `muted`).

- [ ] **Step 2: Redesign `DesktopPlanScreen.kt` and `DesktopSyllabusScreen.kt`**

- **`DesktopPlanScreen.kt`:** Single `AnchorWell` milestone entry bar, hairline-separated `AnchorListRow` milestones with inline check ring, Captain micro-step expansion (`tokens.captainSoft` well), delete with undo bar, and `"Start focus"` action. Strip all `Color(0x...)`, `Color.White`, and `letterSpacing = 1.2.sp`.
- **`DesktopSyllabusScreen.kt`:** Single `AnchorFocalPlate` for the nearest upcoming major deliverable (with one `HarborCtaButton` `"Start focus"`), `AnchorChip` course filter strip, and hairline-divided deliverable rows with expandable backward-chained prep steps. Strip all 24 hardcoded `Color(0x...)` literals and remove `🌱` / `🛡` emoji headers.
- Remove migrated files from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 3: Run tests and ktlint**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/anchor/adhd/ui/plan/PlanScreen.kt \
  app/src/main/java/com/anchor/adhd/ui/components/DayTimeline.kt \
  app/src/main/java/com/anchor/adhd/ui/components/WorkloadBanner.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopPlanScreen.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopSyllabusScreen.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt
git commit -m "Redesign Plan and Syllabus screens onto hairline rows and unified Harbor tokens"
```

---

### Task 9: Full Captain (AI) & Airlock Redesign (Both Platforms)

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/ai/AiScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/components/AirlockSheet.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopAiScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopAirlockDialog.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopSmartOmniBar.kt`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`

- [ ] **Step 1: Redesign Android `AiScreen.kt`, `ChatScreen.kt`, and `AirlockSheet.kt`**

- Rename `"Harbor Pilot"` → **`"Harbor Captain"`** (screen title in `Newsreader`, offline model status in `tokens.captain`).
- Style the 5 tool chips (`Break Down`, `Brain Dump`, `Triage`, `Replan`, `Ask Anchor`) with `AnchorChip(captainAccent = true)`.
- Style user messages in `AnchorWell` (`paper2`) and Captain replies in `AnchorWell(AnchorWellTone.Captain)` (`captainSoft`) with zero heavy bordered cards.
- Clean `AirlockSheet.kt`: remove `letterSpacing = 1.1.sp` / `0.8.sp` tracked labels and wrap cleanly in `HarborModalSheet` + `HarborCtaButton`.

- [ ] **Step 2: Redesign Desktop `DesktopAiScreen.kt`, `DesktopAirlockDialog.kt`, and `DesktopSmartOmniBar.kt`**

- Rename `"HARBOR PILOT"` → **`"Harbor Captain"`** (sentence-case `Newsreader` title; remove all 5 `letterSpacing = 1.1.sp` tracked labels).
- Replace `Color(0x...)` and `Color.White` across `DesktopAiScreen.kt`, `DesktopAirlockDialog.kt`, and `DesktopSmartOmniBar.kt` (16 hardcoded hex values) with `LocalAnchorTokens.current` (`captain`, `captainInk`, `captainSoft`, `paper2`, `surface`, `rule`).
- Remove migrated files from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 3: Run tests and ktlint**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/anchor/adhd/ui/ai/AiScreen.kt \
  app/src/main/java/com/anchor/adhd/ui/chat/ChatScreen.kt \
  app/src/main/java/com/anchor/adhd/ui/components/AirlockSheet.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopAiScreen.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopAirlockDialog.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopSmartOmniBar.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt
git commit -m "Rename AI to Harbor Captain and redesign AI, OmniBar, and Airlock across platforms"
```

---

## Phase 4: Blocker, Settings, Visual Snapshot Harness & Zero-Allowlist Sweep

### Task 10: Full App Blocker & Rescue Overlays Redesign (Both Platforms)

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/focus/AppBlockerScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/focus/ShieldRescueOverlay.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopBlockerScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/blocker/DesktopRescueWindow.kt`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`

- [ ] **Step 1: Redesign `AppBlockerScreen.kt` and `DesktopBlockerScreen.kt`**

- Replace nested `Surface`-inside-`Surface` rule columns and all 50 hardcoded `Color(0x...)` literals in `DesktopBlockerScreen.kt` with:
  1. One top protection-status `AnchorWell` (connected/active status, 4 AM reset note, standing shield / lockdown controls).
  2. Filter `AnchorChip` row (`All`, `Apps`, `Websites`) and clean add-rule bar.
  3. Flat hairline-divided `AnchorListRow` rule entries with tabular quota progress bars (`used / limit`), inline schedule/freeze status, and zero tracked-caps headers (`letterSpacing = 1.2.sp` removed).
- Match the exact same status well + flat hairline rule-list layout in Android `AppBlockerScreen.kt` (replacing `ElevatedCard` stacks).

- [ ] **Step 2: Redesign `ShieldRescueOverlay.kt` and `DesktopRescueWindow.kt`**

- Unify both overlays on `tokens.paper` background + centered `AnchorFocalPlate`, showing the calm distraction notice, current focus task & remaining tabular minutes, one `"Return to focus"` `HarborCtaButton`, and the quiet continuous-hold emergency pass control (zero alarm chrome or hardcoded colors).
- Remove migrated files from `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST`.

- [ ] **Step 3: Run tests and ktlint**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/anchor/adhd/ui/focus/AppBlockerScreen.kt \
  app/src/main/java/com/anchor/adhd/ui/focus/ShieldRescueOverlay.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopBlockerScreen.kt \
  desktopApp/src/main/kotlin/com/anchor/adhd/desktop/blocker/DesktopRescueWindow.kt \
  shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt
git commit -m "Redesign App Blocker and Rescue Overlays without nested cards or hardcoded colors"
```

---

### Task 11: Settings (with Appearance Switcher), Android Secondary Screens & Legacy Cleanup

**Files:**
- Modify: `app/src/main/java/com/anchor/adhd/ui/settings/SettingsScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopSettingsScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/more/MoreScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/components/AnchorComponents.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/grow/HabitsScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/grow/HabitHeatmap.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/grow/StrategiesScreen.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/components/VitalityBar.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/grove/FunLinksStrip.kt`
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui/DesktopFunLinks.kt`
- Modify: `app/src/main/java/com/anchor/adhd/ui/theme/Theme.kt` (remove unused `AnchorExtras` / `LocalAnchorExtras`)
- Modify: `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt` (remove unused `AnchorExtras` / `LocalAnchorExtras` / duplicated `AnchorColors` & `AnchorSpacing` wrappers)
- Delete: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorGradients.kt`
- Delete: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorDepth.kt`
- Delete: `app/src/main/res/font/nunito_variable.ttf`, `app/src/main/res/font/nunito_italic.ttf`, `desktopApp/src/main/resources/font/nunito_variable.ttf`, `desktopApp/src/main/resources/font/nunito_italic.ttf`
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt`

- [ ] **Step 1: Redesign `SettingsScreen.kt` and `DesktopSettingsScreen.kt` with Appearance Switcher**

- Add an **Appearance** section at the top of both Settings screens with three `AnchorChip` options: `System` · `Morning Harbor` · `Night Harbor`, bound directly to `UserPreferences.setAppearanceMode` (Android) and `DesktopUserPreferences.setAppearanceMode` (Windows).
- Convert remaining Settings sections (`Focus`, `Local AI`, `Backup & Data`, `Licenses & About`) into hairline-separated `AnchorListRow` entries and quiet `AnchorWell` groups, removing all 41 hardcoded desktop colors and `letterSpacing = 1.sp` labels.

- [ ] **Step 2: Redesign `MoreScreen.kt`, `AnchorComponents.kt`, `HabitsScreen.kt`, `HabitHeatmap.kt`, `VitalityBar.kt`, `StrategiesScreen.kt`, `FunLinksStrip.kt`, and `DesktopFunLinks.kt`**

- Convert `MoreHub` from `AnchorCard` + `harborEntrance` tiles to hairline-separated `AnchorListRow` entries.
- Remove the `3.dp` vertical colored side-stripe in `AnchorComponents.kt`'s `SectionHeader` and delegate `AnchorCard` cleanly to `AnchorWell` / `AnchorFocalPlate` (zero `topSheen` / `heroGradient`).
- Re-tokenize `HabitHeatmap.kt` (4 stepped `tokens.sage` opacity levels on `tokens.paper3` empty cells), `VitalityBar.kt`, `FunLinksStrip.kt`, `DesktopFunLinks.kt`, `ModelOnboarding.kt`, and any remaining secondary Android sheets/dialogs (`MorningRitualSheet.kt`, `ShutdownSheet.kt`, `TaskDetailSheet.kt`, `PostFocusDialog.kt`, `RoutineRunnerSheet.kt`, `CalendarEventDetailSheet.kt`, `CustomDurationDialog.kt`) so zero `Color(0x...)`, `Color.White`, or tracked-caps labels remain.

- [ ] **Step 3: Delete retired files (`AnchorGradients.kt`, `AnchorDepth.kt`, unused `nunito_*.ttf`, and leftover `LocalAnchorExtras`)**

Remove any remaining references across `app`, `desktopApp`, and `shared`, then verify zero broken imports.

- [ ] **Step 4: Run all test suites**

Run: `./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Redesign Settings with Appearance switcher, modernize secondary screens, and retire legacy theme files"
```

---

### Task 12: Zero-Allowlist Sweep, Compose Desktop Visual Snapshot Harness & Full Verification

**Files:**
- Modify: `shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt` (empty `UNMIGRATED_ALLOWLIST` completely)
- Create: `desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopRedesignSnapshotTest.kt`

- [ ] **Step 1: Empty `AnchorDesignSystemLintTest.UNMIGRATED_ALLOWLIST` and fix any remaining findings**

Ensure `UNMIGRATED_ALLOWLIST` is completely removed from `AnchorDesignSystemLintTest.kt`, then run:

Run: `./gradlew :shared:desktopTest --tests "com.anchor.adhd.ui.theme.AnchorDesignSystemLintTest" --console=plain`
Expected: PASS with 0 violations across all `:shared`, `:app`, and `:desktopApp` UI files.

- [ ] **Step 2: Create headless Compose Desktop snapshot runner (`DesktopRedesignSnapshotTest.kt`)**

Use Compose Desktop's `ImageComposeScene` (`org.jetbrains.skia.EncodedImageFormat.PNG`) to render `Home`, `Focus`, `Plan`, `Blocker`, `Syllabus`, `Captain`, and `Settings` at `1200×800` and `640×450` in both `Morning Harbor (Light)` and `Night Harbor (Dark)` into `desktopApp/build/redesign-snapshots/` (and assert every rendered PNG is non-empty and free of Compose composition exceptions).

- [ ] **Step 3: Run full cross-module test, lint, and snapshot verification**

Run:
```bash
./gradlew :shared:desktopTest :desktopApp:test :desktopApp:ktlintCheck :app:testDebugUnitTest :app:lintDebug --console=plain
```
Expected: BUILD SUCCESSFUL across all three modules, zero ktlint errors, zero Android lint errors, and rendered Light + Dark snapshots at `1200×800` and `640×450` ready for fresh-context visual inspection against `DESIGN.md`.

- [ ] **Step 4: Visually inspect rendered snapshots in fresh context, fix any visual defects, and commit**

Inspect captured PNGs from `desktopApp/build/redesign-snapshots/` (copying final walkthrough screenshots to `/opt/cursor/artifacts/`), verify Hallmark Floor/Reflex gates (contrast, alignment, compact `640×450` overflow, dark lightness steps, zero eyebrows/glows/nested cards), fix any defect found, re-run the full Gradle command, and commit:

```bash
git add shared/src/desktopTest/kotlin/com/anchor/adhd/ui/theme/AnchorDesignSystemLintTest.kt \
  desktopApp/src/test/kotlin/com/anchor/adhd/desktop/DesktopRedesignSnapshotTest.kt
git commit -m "Enforce zero-allowlist Hallmark Compose lint and add Light/Dark desktop snapshot verification"
```
