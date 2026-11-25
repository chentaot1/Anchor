# Design: Anchor (Unified Harbor Paper-Soft)

The locked cross-platform design system for Anchor on **Android** (`app`) and **Windows** (`desktopApp`), derived from Hallmark App-Screen craft rules and owned in `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme`. Change this file deliberately; both clients share it.

## System

- **Scope:** App screens (Compose Multiplatform + Material 3)
- **Direction:** Warm Harbor Paper-Soft (editorial surface discipline + tactile ADHD-safe warmth)
- **Modes:** `light+dark` parity (Morning Harbor light, Night Harbor dark, System default; never inverted)
- **Motion:** State-only (`120ms` micro, `220ms` short, `320ms` sheet/dialog); zero decorative loops; honors system reduced motion on both Android and Windows
- **Scene:** No drawn harbor illustration (`GardenScene` removed); identity comes from warm paper/slate surfaces, typography, and real focus/schedule data

## Tokens

Source of truth: `shared/src/commonMain/kotlin/com/anchor/adhd/ui/theme/AnchorColors.kt` and `AnchorColorSchemes.kt`.

| Role | Morning Harbor (Light) | Night Harbor (Dark) | Usage |
| --- | --- | --- | --- |
| `paper` (`background`) | `#F6F1E7` | `#0E1624` | Screen canvas (never `#FFF` / `#000`) |
| `surface` (focal plate) | `#FBF8F1` | `#152032` | Level 3 primary focus container (+3% L on dark) |
| `paper2` (`surfaceVariant`) | `#ECE5D6` | `#152032` | Level 2 borderless tonal wells, sidebar, nav bar |
| `paper3` (`surfaceContainerHigh`) | `#E2D9C6` | `#1E2E46` | Active nav item, selected chips, duration pills |
| `ink` (`onSurface`) | `#1B2A3A` | `#E8EDF3` | Primary text and headings (one heading ink) |
| `ink2` | `#3A4B5E` | `#BDCAD8` | Secondary body copy and inactive nav |
| `muted` (`onSurfaceVariant`) | `#526375` | `#91A3B8` | Supporting labels & metadata (≥4.9:1 on paper & wells) |
| `rule` (`outlineVariant`) | `#D7CEBC` | `#25354B` | 1dp structural hairlines and focal-plate border |
| `fieldEdge` (`outline`) | `#7D8B99` | `#586C85` | Input field borders (≥3:1 contrast) |
| `primary` / `primarySoft` | `#256280` / `#DCEAF2` | `#8AB4E8` / `#1B2E47` | Information links, focus progress, selected states |
| `beacon` / `beaconInk` | `#DF8A3B` / `#1A1206` | `#F0A35E` / `#1A1206` | ONE primary next-action CTA per screen |
| `pilot` / `pilotInk` / `pilotSoft` | `#0D7366` / `#07302B` / `#D7EFEA` | `#67D8D4` / `#07302B` / `#113035` | Harbor Pilot AI surfaces, tabs, and cues only |
| `sage` / `sageSoft` | `#3E6E4E` / `#DCECE2` | `#72C29B` / `#163326` | Completed tasks, focus progress, growth |
| `clayRose` / `clayRoseSoft` (`error`) | `#B35252` / `#F4DDDD` | `#E89B9B` / `#3B1E22` | No-shame errors and alerts (≥4.5:1) |

## Typography

- **Display (`Newsreader`, Medium 500 / SemiBold 600, OFL):** Brand wordmark, screen titles (`26sp` / `22sp`, `-0.015em` tracking), Focus timer (`52sp` `tnum`), stat figures (`28sp` `tnum`).
- **UI & Body (`Atkinson Hyperlegible`, Regular 400 / Bold 700, OFL):** Task titles (`17sp` Bold), section headers (`14sp` Bold), body (`16sp` / `14sp` / `13sp`), buttons, chips, tabs, labels (`14sp` / `12.5sp` / `12sp`).
- **Invariants:** Sentence case and `0.sp` letter spacing on every label, chip, tab, and section header. Tabular numerals (`tnum`) on all timers, counts, durations, and dates.

## Surface Ladder & Controls

1. **Canvas (`paper`):** Flat background; no per-tab gradients.
2. **Hairline rows (`AnchorListRow`):** `1.dp` `rule` dividers for tasks, blocker rules, syllabus deliverables, and settings.
3. **Tonal well (`AnchorWell`):** Borderless `paper2` or `pilotSoft` (`14.dp` radius) for secondary sections.
4. **Focal plate (`AnchorFocalPlate`):** `surface` + `1.dp` `rule` (`14.dp` radius), at most one primary focal plate per screen.

- **Radii:** `14.dp` plates/wells · `12.dp` buttons · `10.dp` chips/inputs/nav items · `24.dp` sheets/dialogs · `999.dp` count badges & Floating Island.
- **Primary control (`HarborCtaButton`):** Solid `beacon` fill, `beaconInk` text, `12.dp` radius, 48dp touch height on Android / 40dp on desktop. Secondary controls are outlined (`1.dp` `rule`) or quiet text actions.

## Floor Bans (Enforced by `AnchorDesignSystemLintTest`)

- No literal `Color(0x...)`, `Color.White`, or `Color.Black` outside `ui/theme/`.
- No tracked uppercase eyebrows or `letterSpacing` on labels/headers.
- No card inside a card, no colored vertical side-stripe headers, no drop shadows or glows on dark (`beaconGlow`, `harborGlow`, `topSheen`).
- No emoji glyphs as UI icons (Material Icons only; user-editable fun-link emoji presets excluded).
- No staggered entrance animations (`harborEntrance`) and no unguarded `rememberInfiniteTransition`.
