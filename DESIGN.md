# Design: Anchor (Unified Harbor Paper-Soft)

The locked cross-platform design system for Anchor on **Android** (`app`) and **Windows** (`desktopApp`), derived from Hallmark App-Screen craft rules and owned in `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme`. Change this file deliberately; both clients share it.

## System

- **Scope:** App screens (Compose Multiplatform + Material 3)
- **Direction:** Warm Harbor Paper-Soft with Soul (editorial surface discipline + tactile warmth + Committed Deep-Harbor NOW beacon plate + Living Tide focus chronometer)
- **Posture:** Committed on the single `Next action` focal plate (`#15263B` light / `#182B44` dark); Restrained everywhere else
- **Modes:** `light+dark` parity (Morning Harbor light, Night Harbor dark, System default; never inverted)
- **Motion:** State-only (`120ms` micro press/color, `220ms` short state/tab, `320ms` sheet/dialog) plus the single calm 0.1 Hz rising water tide inside active `FocusCanvas` (frozen to a static horizon under reduced motion); zero decorative loops
- **Scene:** No drawn harbor landscape (`GardenScene` removed); real focus data drives the `Weekly Tide` / `Today's Tide` rhythm strip on Home and the rising water tide inside `FocusCanvas`
- **AI Identity:** **Captain** (`Harbor Captain`), styled exclusively in starboard teal

## Tokens

Source of truth: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorColors.kt`, `AnchorTokens.kt`, and `AnchorColorSchemes.kt`.

| Role | Morning Harbor (Light) | Night Harbor (Dark) | Usage |
| --- | --- | --- | --- |
| `paper` (`background`) | `#F6F1E7` | `#0E1624` | Screen canvas (never `#FFF` / `#000`) |
| `surface` (standard plate) | `#FBF8F1` | `#152032` | Secondary framed plates (+3% L on dark) |
| `paper2` (`surfaceVariant`) | `#ECE5D6` | `#152032` | Level 2 borderless tonal wells, sidebar, nav bar |
| `paper3` (`surfaceContainerHigh`) | `#E2D9C6` | `#1E2E46` | Active nav item, selected chips, duration pills |
| `ink` (`onSurface`) | `#1B2A3A` | `#E8EDF3` | Primary text and headings (one heading ink) |
| `ink2` | `#3A4B5E` | `#BDCAD8` | Secondary body copy and inactive nav |
| `muted` (`onSurfaceVariant`) | `#526375` | `#91A3B8` | Supporting labels & metadata (≥4.9:1 on paper & wells) |
| `rule` (`outlineVariant`) | `#D7CEBC` | `#25354B` | 1dp structural hairlines and borders |
| `fieldEdge` (`outline`) | `#7D8B99` | `#586C85` | Input field borders (≥3:1 contrast) |
| `primary` / `primarySoft` | `#256280` / `#DCEAF2` | `#8AB4E8` / `#1B2E47` | Information links, focus progress, selected states |
| `beacon` / `beaconInk` | `#DF8A3B` / `#1A1206` | `#F0A35E` / `#1A1206` | ONE primary next-action CTA per screen |
| `captain` / `captainInk` / `captainSoft` | `#0D7366` / `#07302B` / `#D7EFEA` | `#67D8D4` / `#07302B` / `#113035` | Captain AI surfaces, tabs, and cues only |
| `sage` / `sageSoft` | `#3E6E4E` / `#DCECE2` | `#72C29B` / `#163326` | Completed tasks, break tide, growth |
| `clayRose` / `clayRoseSoft` (`error`) | `#B35252` / `#F4DDDD` | `#E89B9B` / `#3B1E22` | No-shame errors and alerts (≥4.5:1) |
| `beaconPlate` / `beaconPlateBorder` | `#15263B` / `#243A54` | `#182B44` / `#2D4769` | Committed Deep-Harbor NOW plate (+ `2.dp` `#F0A35E` top ridge) |
| `beaconPlateInk` / `beaconPlateInk2` | `#F6F1E7` / `#C2D0DF` | `#F2F6FA` / `#C4D2E0` | Text on Committed Deep-Harbor NOW plate |
| `beaconPlatePill` / `beaconPlateCta` | `#213752` / `#F0A35E` | `#243C5C` / `#F0A35E` | Duration chip & amber CTA inside NOW plate |
| `tideBack` / `tideWave1` / `tideWave2` | `#EFE9DC` / `#A9CBE0` / `#5B8EAC` | `#122033` / `#1E4368` / `#2A5B84` | Living Tide water fill inside `FocusCanvas` |

## Typography

- **Display (`Newsreader`, Medium 500 / SemiBold 600, OFL):** Brand wordmark, screen titles (`26sp` / `22sp`, `-0.015em` tracking), Focus timer (`52sp` `tnum`), stat figures (`28sp` `tnum`).
- **UI & Body (`Atkinson Hyperlegible`, Regular 400 / Bold 700, OFL):** Task titles (`17sp` Bold), section headers (`14sp` Bold), body (`16sp` / `14sp` / `13sp`), buttons, chips, tabs, labels (`14sp` / `12.5sp` / `12sp`).
- **Invariants:** Sentence case and `0.sp` letter spacing on every label, chip, tab, and section header. Tabular numerals (`tnum`) on all timers, counts, durations, and dates.

## Surface Ladder & Controls

1. **Canvas (`paper`):** Flat background; no per-tab gradients.
2. **Hairline rows (`AnchorListRow`):** `1.dp` `rule` dividers for tasks, blocker rules, syllabus deliverables, and settings.
3. **Tonal well (`AnchorWell`):** Borderless `paper2` or `captainSoft` (`14.dp` radius) for secondary sections.
4. **Focal plate (`AnchorFocalPlate` / `AnchorBeaconPlate`):** `AnchorBeaconPlate` (Committed Deep-Harbor navy + `2.dp` top amber ridge) for the ONE primary `Next action` on Home; `AnchorFocalPlate` (`surface` + `1.dp` `rule`, `14.dp` radius) for single focal panels on other screens (such as upcoming deliverable or rescue prompt).

- **Radii:** `14.dp` plates/wells · `12.dp` buttons · `10.dp` chips/inputs/nav items · `24.dp` sheets/dialogs · `999.dp` count badges & Floating Island.
- **Primary control (`HarborCtaButton`):** Solid `beacon` fill, `beaconInk` text, `12.dp` radius, subtle `0.985f` press scale at `120ms` (off under reduced motion), 48dp touch height on Android / 40dp on desktop. Secondary controls are outlined (`1.dp` `rule`) or quiet text actions.
- **Empty states (`HarborEmptyState`):** Static `44.dp` geometric tide-ring + beacon-dot mark (`tokens.rule` + `tokens.beacon`), plain title, one-sentence message, at most one action.

## Floor Bans (Enforced by `AnchorDesignSystemLintTest`)

- No literal `Color(0x...)`, `Color.White`, or `Color.Black` outside `ui/theme/`.
- No tracked uppercase eyebrows or `letterSpacing` on labels/headers.
- No card inside a card, no colored vertical side-stripe headers, no drop shadows or glows on dark (`beaconGlow`, `harborGlow`, `topSheen`).
- No emoji glyphs as UI icons (Material Icons only; user-editable fun-link emoji presets excluded).
- No staggered entrance animations (`harborEntrance`), no `GardenScene` / `DesktopHarborScene`, and no unguarded `rememberInfiniteTransition` (only `FocusCanvas`'s tide wave, gated on `!LocalReducedMotion.current`).
