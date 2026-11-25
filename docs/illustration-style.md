# Harbor Visual Signatures & Data Rhythm

Anchor no longer draws a decorative harbor landscape (`GardenScene` and `DesktopHarborScene` were removed). No plant illustrations, forest scenes, external 3D models, or bitmap harbor artwork assets are included.

Instead, Anchor expresses its maritime identity across Windows and Android through real focus data and restrained geometry owned in `:shared`:

- **Weekly Tide (`AnchorWeeklyTide`) & Today's Tide (`AnchorTodayTideStrip`):** Real-data focus-minute rhythm strips on Home. On desktop, `AnchorWeeklyTide` charts the last 7 logical days (using Anchor's 4:00 AM daily reset boundary), lighting today's bar in amber `beacon` and prior days in harbor `primary`, paired with the current `GroveLevel` milestone label (`Seed`, `Sprout`, `Growing`, `Rooted`, `Flourishing`). On Android, `AnchorTodayTideStrip` displays today's progress toward the daily focus block.
- **Living Tide Chronometer (`FocusCanvas`):** A circular focus timer whose rising double-wave water basin fills proportionally to elapsed session time using Morning Harbor (Light) and Night Harbor (Dark) tide tokens (`tideBack`, `tideWave1`, `tideWave2`, and sage break variants). Under reduced motion (`LocalReducedMotion`), the water freezes to a calm static horizon with zero infinite transitions.
- **Empty-State Mark (`HarborEmptyState`):** A static `44.dp` geometric tide-ring and beacon-dot emblem rendered with `tokens.rule` and `tokens.beacon`, paired with plain-language copy and at most one quiet action.

Focus rewards, streaks, `GroveLevel` stages, and planning continue to operate independently of visual decoration.
