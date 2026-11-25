# Anchor Unified Cross-Platform Redesign Specification

**Date:** 2026-11-25
**Scope:** Full redesign across `shared`, `app` (Android), and `desktopApp` (Windows)
**Locked Design System:** [`DESIGN.md`](../../../DESIGN.md)
**Method:** Adapted from [Nutlope/hallmark](https://github.com/Nutlope/hallmark) v2 App-Screen & Redesign rules

---

## 1. Goals & Constraints

### Goals
1. **One unmistakable identity across Windows and Android:** Both clients share one token contract, typography pairing (`Newsreader` + `Atkinson Hyperlegible`), 4-rung surface ladder, destination vocabulary (with the AI assistant named **Captain** / **Harbor Captain**), and Light/Dark parity from `:shared`.
2. **True Morning Harbor (Light) + Night Harbor (Dark) parity on both platforms:** Remove desktop's dark-only lock and hardcoded white-on-dark surfaces, and fix Android's hardcoded dark NOW card and forced dark system bars so both modes are first-class everywhere.
3. **Eliminate AI-generated UI tells while preserving real Harbor character:** Apply Hallmark's App-Screen Floor and Reflex rules—removing hardcoded hex literals outside theme files, tracked-caps eyebrow headers, nested bordered cards, dark-mode glows/sheens, emoji icons, and decorative infinite motion—while concentrating Anchor's character into four intentional signatures:
   - The **Committed Deep-Harbor `Next action` Plate** (`#15263B` light / `#182B44` dark with a `2.dp` warm amber top edge) so the user's eye locks onto the ONE task immediately.
   - The **Living Tide `FocusCanvas`** rising water chronometer, re-tokenized for Light + Dark mode and pausing cleanly under reduced motion.
   - The real-data **Weekly Tide / Today's Tide** focus rhythm strip on Home (replacing decorative `GardenScene` with actual daily minutes and `GroveLevel` progress).
   - A minimal static `44.dp` geometric tide-ring + beacon-dot mark in `HarborEmptyState` and subtle `120ms` press feedback (`0.985f` scale) on `HarborCtaButton`.
4. **Drop the drawn harbor landscape cleanly:** Remove `GardenScene` and `DesktopHarborScene` from both clients while keeping `GroveLevel`, `DesktopHarborState`, and JSON backup compatibility untouched.
5. **Preserve 100% of existing product wiring, data, and tests:** Keep all local Room/SQLite schemas, JSON backup cross-import compatibility, Android/Windows blocker enforcement, llama.cpp inference, syllabus parsing, keyboard shortcuts (`Ctrl+1–7`, `Ctrl+K`, `Ctrl+I`, `Space`), compact desktop layout support (`640×450 dp`), and single-instance Windows restore behavior.

### Non-Goals
- Changing database schemas, Room migrations, SQLite tables, or JSON backup serialization formats.
- Changing AI inference engines, grammars, routing logic, or blocker enforcement policies.
- Adding new cloud sync or network features.

---

## 2. Shared Design System Architecture (`:shared`)

### 2.1 Single Theme Contract
Consolidate all theme tokens in `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/`:

- **`AnchorColors.kt`:** Single palette source with Morning Harbor (Light) and Night Harbor (Dark) semantic tokens defined in `DESIGN.md`. Every pairing meets WCAG AA (≥4.5:1 body/muted text, ≥3:1 large text, icons, focus outlines, and input field edges).
  - Light `clayRose` error token moves from `#C97F7F` to `#B35252` so error messages clear 4.5:1 on warm paper (`#F6F1E7`) and wells (`#ECE5D6`).
  - Dark mode steps *lighter* by ~3% luminance per elevation layer (`#0E1624` → `#152032` → `#1E2E46`) with zero drop shadows.
  - Committed Deep-Harbor NOW Plate tokens (`beaconPlate`, `beaconPlateBorder`, `beaconPlateInk`, `beaconPlateInk2`, `beaconPlatePill`, `beaconPlateCta`, `beaconPlateCaptain`) work cohesively in both Light (`#15263B`) and Dark (`#182B44`).
  - Living Tide Focus water tokens (`tideBack`, `tideWave1`, `tideWave2`, `tideBreakWave1`, `tideBreakWave2`) adapt cleanly between Morning Light and Night Dark.
- **`AnchorTokens.kt`:** `@Immutable data class AnchorTokens` provided via `LocalAnchorTokens` holding every semantic token in `DESIGN.md` plus `isDark: Boolean`.
- **`AnchorColorSchemes.kt`:** `anchorLightColorScheme()` and `anchorDarkColorScheme()` mapped directly from `AnchorTokens` so standard Material 3 widgets inherit exact Harbor colors automatically.
- **`AnchorTypography.kt`:** Fixed app-screen type scale (1.15–1.2 ratio):
  - `displayLarge` (`Newsreader` 500, `52.sp`, `tnum`) — Focus countdown.
  - `displaySmall` (`Newsreader` 500, `28.sp`, `tnum`) — Stat figures.
  - `headlineMedium` (`Newsreader` 500, `26.sp`, `-0.3.sp`) — Screen titles & greeting.
  - `titleLarge` (`Newsreader` 500, `22.sp`, `-0.2.sp`) — Sub-screen titles & brand wordmark.
  - `titleMedium` (`Atkinson Hyperlegible` 700, `17.sp`) — Task titles, focal plate titles.
  - `titleSmall` (`Atkinson Hyperlegible` 700, `14.sp`) — Section headers (sentence case, `0.sp` tracking).
  - `bodyLarge` (`Atkinson Hyperlegible` 400, `16.sp`), `bodyMedium` (`14.sp`), `bodySmall` (`13.sp`).
  - `labelLarge` (`Atkinson Hyperlegible` 700, `14.sp`, `0.sp`), `labelMedium` (`12.5.sp`, `0.sp`), `labelSmall` (`12.sp`, `0.sp`).
- **`AnchorSpacing.kt` & `AnchorShapes.kt`:** One shared spacing & shape scale (`screenHorizontal = 20.dp` mobile / `24.dp` desktop, `sectionGap = 20.dp`, `itemGap = 12.dp`; radii `14.dp` plate/well, `12.dp` button, `10.dp` chip/input/nav, `24.dp` sheet/dialog, `999.dp` badge/island).
- **`AnchorMotion.kt`:** State-only durations (`MICRO_MS = 120`, `SHORT_MS = 220`, `SHEET_MS = 320`, `TIDE_MS = 10000`), tactile `pressScale` (`0.985f`), and `LocalReducedMotion = staticCompositionLocalOf { false }`.
- **`AnchorTheme.kt` (in `:shared`):** Unified composable `AnchorTheme(darkTheme, displayFont, bodyFont, reducedMotion, content)` providing `LocalAnchorTokens`, `LocalReducedMotion`, and `MaterialTheme`.
- **Platform wrappers:**
  - `app/src/main/java/com/anchor/adhd/ui/theme/Theme.kt`, `AnchorFonts.kt`, and `Type.kt` load Android font resources (`Newsreader` + `Atkinson Hyperlegible`), read `rememberReducedMotion()`, and delegate directly to shared `AnchorTheme`.
  - `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/theme/AnchorTheme.kt` and `DesktopFonts.kt` load desktop font resources, read Windows reduced motion, and delegate directly to shared `AnchorTheme`. All duplicated desktop `AnchorSpacing`, `AnchorExtras`, `AnchorShapes`, and `LocalAnchorExtras` definitions are deleted.
- **Retired theme files:** Delete `AnchorGradients.kt` and `AnchorDepth.kt` (`beaconGlow`, `harborGlow`, `topSheen`, `harborEntrance`).

### 2.2 Font Bundling & Licensing
- Bundle OFL-1.1 **`Newsreader`** TTF files (`newsreader_medium.ttf` weight 500, `newsreader_semibold.ttf` weight 600) in `app/src/main/res/font/` and `desktopApp/src/main/resources/font/`.
- Retain `Atkinson Hyperlegible` (`regular`, `bold`, `italic`, `bold_italic`). Remove unused `nunito_*.ttf` font binaries once migration completes.
- Add `licenses/OFL-1.1.txt` and document `Newsreader` and `Atkinson Hyperlegible` attributions in `licenses/THIRD_PARTY_NOTICES.txt` and `docs/license-audit.md`.

### 2.3 Appearance Mode & Platform Detection
- Add `enum class AppearanceMode { SYSTEM, LIGHT, DARK }` in `shared/src/commonMain/kotlin/com/anchor/adhd/domain/AppearanceMode.kt`.
- Persist `appearance_mode` string key (default `SYSTEM`) in both Android `UserPreferences` and Desktop `DesktopUserPreferences`.
- **Android resolution:** `SYSTEM` follows `isSystemInDarkTheme()`. `MainActivity` updates `enableEdgeToEdge(...)` dynamically via `DisposableEffect(darkTheme)` using `SystemBarStyle.light(TRANSPARENT, TRANSPARENT)` when light and `SystemBarStyle.dark(TRANSPARENT)` when dark.
- **Windows resolution (`DesktopSystemAppearance.kt`):**
  - System theme: JNA `Advapi32Util.registryGetIntValue(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme") == 0` → dark, otherwise light; non-Windows or missing key falls back safely to light.
  - Reduced motion: JNA `User32.SystemParametersInfo(SPI_GETCLIENTAREAANIMATION = 0x1042, ...)` → `false` means reduced motion enabled; non-Windows falls back to `false`.

---

## 3. Shared UI Primitives (`:shared`)

Located in `shared/src/commonMain/kotlin/com/anchor/adhd/ui/components/`:

| Component | Surface Rung | Behavior & Visual Rules |
| --- | --- | --- |
| `AnchorBeaconPlate` | Level 3 (Committed) | Deep-Harbor navy focal plate (`tokens.beaconPlate` + `1.dp` `tokens.beaconPlateBorder` + `2.dp` top `tokens.beaconPlateCta` ridge, `14.dp` radius, zero drop shadow). Reserved exclusively for the ONE `Next action` focal card on Home. |
| `AnchorFocalPlate` | Level 3 (Restrained) | `tokens.surface` + `1.dp` `tokens.rule` border, `14.dp` radius, zero shadow/glow. Used for single focal panels on secondary screens (e.g. upcoming deliverable, rescue prompt). |
| `AnchorWell` | Level 2 | Borderless tonal fill (`tokens.paper2`, or `tokens.captainSoft` for Captain AI cues), `14.dp` radius, zero border. |
| `AnchorListRow` | Level 1 | Row with optional leading check/icon, `Atkinson` title + optional subtitle, tabular trailing metadata/action, separated by `AnchorHairline` (`1.dp` `tokens.rule`). Touch min-height `48.dp` on Android, `40.dp` on desktop. |
| `AnchorSectionHeader` | Structural | Sentence-case `titleSmall` heading (`semantics { heading() }`) + optional quiet trailing count/link. Zero tracked caps, zero colored side-stripes. |
| `AnchorWeeklyTide` / `AnchorTodayTideStrip` | Data Signature | Real-data 7-day focus-minute bar chart (`Weekly tide` + `GroveLevel` milestone + tabular total, today lit in `beacon`, other days in `primary`) for desktop Home, and compact `Today's tide` horizontal progress bar for Android Home. |
| `HarborCtaButton` | Primary Action | Solid `tokens.beacon` (or `tokens.beaconPlateCta` on `AnchorBeaconPlate`) fill, `tokens.beaconInk` text, `12.dp` radius, zero gradient/glow, subtle `0.985f` press scale at `120ms` when `!LocalReducedMotion.current`, disabled 3-channel state (`0.45f` alpha). |
| `AnchorQuietButton` | Secondary Action | Outlined (`1.dp` `tokens.rule`, `12.dp` radius) or plain text button (`tokens.primary` / `tokens.captain`). Never louder than `HarborCtaButton`. |
| `AnchorChip` | Selection | `10.dp` radius, tabular numerals, `paper` + `rule` border unselected, `primarySoft` + `primary` border selected (`captainSoft` + `captain` for Captain tools). |
| `HarborEmptyState` | Empty State | Static `44.dp` geometric tide-ring + beacon-dot mark (`tokens.rule` + `tokens.beacon`), plain title (`titleMedium`), one-sentence message (`bodyMedium` in `muted`), and at most one action button. |
| `HarborModalSheet` | Sheet | `tokens.surface` background, `24.dp` top radius, tokenized scrim (`tokens.paper.copy(alpha = 0.65f)`), subtle `36×4.dp` `tokens.rule` drag handle (no gradient bar). |
| `FocusCanvas` | Signature Control | Living Tide circular chronometer: `tokens.paper3` track ring, solid `tokens.beacon` (work) or `tokens.sage` (break) progress arc, animated rising double-wave water interior (`tokens.tideWave1` / `tokens.tideWave2` on `tokens.tideBack`; sage variants on break) clipped cleanly inside the circle, crisp `Newsreader` `52.sp` `tnum` timer readout centered cleanly. Drops muddy outer glow halos and dark-only `#1E293B` track; freezes to a calm flat water horizon when `LocalReducedMotion.current` is true. |

**Removed components:** `GardenScene.kt` (shared) and `DesktopHarborScene.kt` (desktop). `GroveLevel.kt` and `DesktopHarborState` stay intact so weekly focus milestones, widget text (`AnchorWidget.kt`), and cross-platform JSON backups continue without schema changes. Update `docs/illustration-style.md` and `DesignPreviews.kt` accordingly.

---

## 4. Unified Navigation & Screen Architecture

### 4.1 Destination Parity

| Destination | Android (`AnchorShell.kt`) | Windows (`DesktopNavigationSidebar.kt`) | Icon (Outlined / Filled) |
| --- | --- | --- | --- |
| `Home` | Tab 0 | Item 1 (`Ctrl+1`) | `Home` |
| `Plan` | Tab 1 (with replan badge) | Item 2 (`Ctrl+2`, with pending count) | `CalendarMonth` |
| `Focus` | Tab 2 | Item 3 (`Ctrl+3`) | `Timer` |
| `Blocker` | Inside `More` | Item 4 (`Ctrl+4`) | `Shield` |
| `Syllabus` | `Plan → Assignments` | Item 5 (`Ctrl+5`) | `School` |
| `Captain` | Tab 3 (`captain` teal accent) | Item 6 (`Ctrl+6`, `captain` teal accent) | `AutoAwesome` |
| `Settings` | Inside `More` + Home top-right icon | Item 7 (`Ctrl+7`) | `Settings` |
| `More` | Tab 4 (`Blocker`, `Habits`, `Strategies`, `Settings`) | N/A (all exposed top-level) | `Apps` |

- **Android shell (`AnchorShell.kt`):** Flat `tokens.paper` scaffold background; `tokens.paper2` bottom bar with `1.dp` top `tokens.rule` hairline and `tokens.primarySoft` active pill indicator.
- **Windows shell (`DesktopNavigationSidebar.kt`, `Main.kt`):**
  - Expanded (`width >= 760.dp`): `200.dp` `tokens.paper2` sidebar separated by `1.dp` right `tokens.rule`, `Newsreader` `"Anchor"` wordmark with static `8.dp` amber beacon dot (zero pulse), `tokens.paper3` active item pill, quiet footer for `Airlock reset` (`Ctrl K`) and `Floating Island` (`Ctrl I`).
  - Compact (`width < 760.dp`, covering `640×450 dp`): collapses to a `64.dp` icon rail with clear active highlight and semantic labels/tooltips so the workspace keeps full breathing room.
  - Retain the single-instance Win32 window title constant shared between `Window(title = ...)` and `restoreExistingAnchorWindow()` in `Main.kt`.

### 4.2 Screen-by-Screen Composition

1. **Home (`GroveHomeScreen.kt` + `DesktopHomeDeck.kt` + `DesktopNowCard.kt`):**
   - Header: `Newsreader` time-of-day greeting (`Good morning` / `Good afternoon` / `Good evening`) + one factual line (`"2 sessions today, 45 min focused"`), plus desktop `DesktopSmartOmniBar` inline on the top row (`"Ask Captain or capture a task…"`).
   - Primary focus object: Single `AnchorBeaconPlate` (`Next action` label in amber, duration chip in `beaconPlatePill`, `Atkinson` bold task title, optional first physical step, single `HarborCtaButton` `"Start X min focus"`, optional secondary `"Break down with Captain"`).
   - Quick start: Borderless `AnchorWell` with `AnchorChip` duration presets (`3m`, `15m`, `25m`, `50m`, custom).
   - Remaining work: `AnchorSectionHeader("Today's plan")` + `AnchorListRow` items separated by hairlines (no repeated boxes).
   - Desktop right rail (`width >= 860.dp`; stacks below on `< 860.dp`): `AnchorWeeklyTide` (7-day focus minutes + `GroveLevel` stage + today/blocked counts), quick thought capture well, and a `captainSoft` **Harbor Captain** contextual note.
   - Android inline sections: Compact `AnchorTodayTideStrip` above `AnchorBeaconPlate`, followed by quiet `AnchorWell` / `AnchorListRow` bands for check-in, daily strategy, upcoming tasks, and fun links—removing all dark-only `Color(0xFF141F32)` / `Color.White` hardcodes.
   - Clean copy updates in `AppCopy.kt`: replace middle-dot chains and glued arrows (`"Plan → Replan"`, `"End early · still logged"`) with plain sentences or structured layout columns.

2. **Focus (`FocusScreen.kt` + `DesktopFocusScreen.kt` + `DesktopFloatingIsland.kt`):**
   - Centered Living Tide `FocusCanvas` + current task title + single primary Start/Pause `HarborCtaButton` + quiet secondary controls (Add time, End early, Fullscreen/Island, Airlock).
   - Micro-step checklist rendered as `AnchorListRow` items inside a quiet well; zero emojis (`⚓` removed from completion banner).
   - `DesktopFloatingIsland`: Theme-aware `tokens.paper2` pill with `1.dp` `tokens.rule` border (accented with `tokens.sage` border when active focus runs), tabular `MM:SS` readout, Play/Pause, Expand, and Close controls.

3. **Plan (`PlanScreen.kt` + `DesktopPlanScreen.kt`):**
   - Clean horizontal tab strip (`Timeline`, `Inbox`, `Replan`, `Assignments` on Android; filter/status controls on Windows), single `AnchorWell` quick-add input bar, and `AnchorListRow` task lists with inline completion ring, tabular duration/due metadata, and expandable Captain micro-steps.
   - `DayTimeline` and `WorkloadBanner` re-tokenized to `AnchorTokens` (zero hardcoded `Color(0x...)`).

4. **Syllabus (`DesktopSyllabusScreen.kt` + Android `Plan → Assignments`):**
   - Single `AnchorFocalPlate` for the nearest upcoming major deliverable (with one `"Start focus"` CTA), `AnchorChip` course filters, and hairline-divided deliverable rows with expandable backward-chained prep steps. Remove all emoji prefixes (`🌱`, `🛡`) and hardcoded dark hex codes.

5. **Captain (`AiScreen.kt` + `ChatScreen.kt` + `DesktopAiScreen.kt`) & Airlock (`AirlockSheet.kt` + `DesktopAirlockDialog.kt`):**
   - Rename user-facing `"Pilot"` / `"Harbor Pilot"` / `"HARBOR PILOT"` to `"Captain"` / `"Harbor Captain"` across both Android and Windows.
   - `captain` teal exclusively marks AI tool selector chips (`Break Down`, `Brain Dump`, `Triage`, `Replan`, `Ask Anchor`), offline model status indicator, and assistant response wells (`captainSoft`).
   - Remove tracked uppercase mode headers and heavy bordered message boxes; user messages sit in `paper2`, Captain messages in `captainSoft`.
   - Airlock on both platforms shares the same clean 3-step recovery rhythm inside `HarborModalSheet` / themed dialog with zero tracked-caps labels.

6. **App Blocker (`AppBlockerScreen.kt` + `DesktopBlockerScreen.kt`) & Rescue Overlays (`ShieldRescueOverlay.kt` + `DesktopRescueWindow.kt`):**
   - Top status `AnchorWell` (protection active/setup status, daily 4 AM reset note, freeze/lockdown indicator), filter `AnchorChip` row (`All`, `Apps`, `Websites` on desktop; filter input on Android), and flat hairline-divided `AnchorListRow` entries with clean tabular quota bars—eliminating nested cards-in-cards and all 50 hardcoded desktop hex literals.
   - Rescue overlays use calm `tokens.paper` background with a centered `AnchorFocalPlate`, one `"Return to focus"` `HarborCtaButton`, and a quiet hold-to-override control (zero alarm chrome or dark-only hardcodes).

7. **Settings & Android Secondary Screens (`SettingsScreen.kt`, `DesktopSettingsScreen.kt`, `MoreScreen.kt`, `HabitsScreen.kt`, `StrategiesScreen.kt`, `FunLinks`):**
   - Hairline-grouped sections (`Appearance`, `Focus`, `Local AI`, `Backup & Data`, `Licenses & About`) with an **Appearance** segmented chip group (`System` · `Light` · `Dark`) on both platforms.
   - `HabitHeatmap.kt`, `VitalityBar.kt`, and `FunLinksStrip.kt` consume `AnchorTokens` instead of literal `Color(0x...)` values (user-editable fun-link emoji presets remain user content, not UI chrome).

---

## 5. Verification, Error Handling & Automated Quality Gates

### 5.1 Automated Compose Hallmark Static Gate (`AnchorDesignSystemLintTest`)
A fast JUnit test in `:shared` (running under `:shared:desktopTest`) scans UI Kotlin source files across `shared/src/commonMain/kotlin/com/anchor/adhd/ui`, `app/src/main/java/com/anchor/adhd/ui`, `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/ui`, `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/blocker/DesktopRescueWindow.kt`, and `desktopApp/src/main/kotlin/com/anchor/adhd/desktop/Main.kt` and fails on:
1. Raw `Color(0x...)`, `Color.White`, or `Color.Black` outside `ui/theme/`.
2. Tracked uppercase labels (`letterSpacing =` on `label*` text or all-caps section headers).
3. Banned UI chrome emojis (`⚓`, `🌱`, `🛡`, `🔥`, `⚡`, `✨`, `💡`, `🎯`, `✎`) in UI strings (excluding user-customizable fun-link preset lists).
4. References to removed `AnchorGradients`, `beaconGlow`, `harborGlow`, `topSheen`, `harborEntrance`, `GardenScene`, or `DesktopHarborScene`.
5. Unguarded `rememberInfiniteTransition` calls outside `FocusCanvas.kt` (where it must check `!LocalReducedMotion.current`).

During Phases 1–3, a small explicit file allowlist tracks unmigrated screens; Phase 4 empties the allowlist to zero so regressions cannot slip back in.

### 5.2 Regression & Runtime Verification
- **Unit & lint suites at every phase:**
  - `./gradlew :shared:desktopTest`
  - `./gradlew :desktopApp:test :desktopApp:ktlintCheck`
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`
- **Visual screenshot verification:** Add a headless Compose Desktop snapshot runner (`ImageComposeScene`) that renders `Home`, `Focus`, `Plan`, `Blocker`, `Syllabus`, `Captain`, and `Settings` at both `1200×800` and compact `640×450` in **Morning Harbor (Light)** and **Night Harbor (Dark)**, saving PNGs for a fresh-context visual inspection against `DESIGN.md` and Hallmark Floor/Reflex gates.
- **Error & empty states:** Every data-backed region (`Home` empty next-action, `Plan` empty lists, `Blocker` zero rules, `Syllabus` no imports, `Captain` model unavailable, `Settings` backup/restore errors) renders an explicit calm state using `HarborEmptyState` or `clayRose` inline feedback—never blank screens or silent failures.

---

## 6. Phased Delivery Plan

| Phase | Scope | Verification Gate |
| --- | --- | --- |
| **Phase 1: Foundation** | Bundle OFL `Newsreader` fonts + license notices; refactor `:shared` theme (`AnchorColors`, `AnchorTokens`, `AnchorColorSchemes`, `AnchorTypography`, `AnchorSpacing`, `AnchorShapes`, `AnchorMotion`, `AnchorTheme`) and shared primitives (`AnchorBeaconPlate`, `AnchorFocalPlate`, `AnchorWell`, `AnchorListRow`, `AnchorSectionHeader`, `AnchorWeeklyTide`, `AnchorTodayTideStrip`, `HarborCtaButton`, `AnchorQuietButton`, `AnchorChip`, `HarborEmptyState`, `HarborModalSheet`, `FocusCanvas`); add `AppearanceMode` + Windows JNA appearance/reduced-motion helpers; add `AnchorDesignSystemLintTest` with temporary allowlist. | `:shared:desktopTest`, `:desktopApp:test`, `:app:testDebugUnitTest` green. |
| **Phase 2: Shell, Home & Focus** | Unified navigation (`AnchorShell`, `MainActivity` adaptive bars, `DesktopNavigationSidebar` compact rail, `Captain` tab/nav rename); full redesign of `GroveHomeScreen`, `DesktopHomeDeck`, `DesktopNowCard` (Committed Deep-Harbor NOW plate + Weekly/Today Tide + dropping `GardenScene` & `DesktopHarborScene`); full redesign of `FocusScreen`, `DesktopFocusScreen`, `DesktopFloatingIsland`; update `AppCopy.kt`. | All tests green; Home & Focus desktop renders verified Light + Dark at `1200×800` & `640×450`. |
| **Phase 3: Plan, Syllabus, Captain & Airlock** | Full redesign of `PlanScreen`, `DesktopPlanScreen`, `DayTimeline`, `WorkloadBanner`, `DesktopSyllabusScreen`, `AiScreen`, `ChatScreen`, `DesktopAiScreen`, `AirlockSheet`, `DesktopAirlockDialog`, `DesktopSmartOmniBar`. | All tests green; Plan, Syllabus & Captain renders verified Light + Dark. |
| **Phase 4: Blocker, Settings & Zero-Allowlist Sweep** | Full redesign of `AppBlockerScreen`, `DesktopBlockerScreen`, `ShieldRescueOverlay`, `DesktopRescueWindow`, `SettingsScreen`, `DesktopSettingsScreen` (with Appearance switcher), `MoreScreen`, `HabitsScreen`, `HabitHeatmap`, `VitalityBar`, `StrategiesScreen`, `FunLinks`; delete `AnchorGradients.kt`, `AnchorDepth.kt`, unused `nunito_*.ttf`; empty `AnchorDesignSystemLintTest` allowlist. | Zero lint allowlist entries; `:shared:desktopTest`, `:desktopApp:test`, `:desktopApp:ktlintCheck`, `:app:testDebugUnitTest`, `:app:lintDebug` all green; fresh-context visual review of all rendered screens passes. |
