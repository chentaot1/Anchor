# Anchor Android 1.1.0 preview 4

Source update: October 9, 2026. Version code: 5.

## Changes

- Shared app quota groups synchronize their allowances when a member is edited. Existing inconsistent groups use the lowest allowance, and edits cannot change a frozen member indirectly.
- Earned leisure purchases extend an active window. Near the local 4 AM reset, leisure and charges are capped at the remaining time, rounded up to a whole minute.
- App Blocker shows remaining 24-hour rule-freeze time and supports searching selected apps. Essential phone/system apps are excluded from the app picker.
- Scheduled blocking treats equal start/end times as all-day protection and safely clamps invalid hour/minute inputs.
- Plan supports pasting a syllabus and deleting assignments. Date parsing accepts Sept/Sept., explicit years, ISO and US hyphenated dates, and point/decimal weights while preserving chapter/page/minute ranges.
- Home's expanded Rest of today section includes later scheduled tasks with working Focus and Done actions.
- JSON backup import accepts Windows tasks, course/syllabus records, focus sessions in seconds, harbor progress, and fun links. Native Android backups include the broader local planning/history records.
- Additional planning, recovery, local-AI, habit, and focus-persistence refinements are included from the preceding development waves.

## Backup compatibility

Backup import is a manual transfer of supported records, not live synchronization. The two platforms retain separate local databases and protection settings. Some fields have no equivalent on the other platform, and seconds are converted to Android's whole-minute session fields. Review imported assignments and planning records after restoring.

## Verification and installation

All 223 Android unit tests passed, and Android application compilation passed. The shared desktop test run also passed all 8 tests. Physical-device protection checks remain pending.

No preview 4 APK has been packaged, signed, installed on a physical device, or uploaded as part of this source publication. Existing model selection and signing configuration are unchanged. Building an installable update still requires the verified model assets and the same signing identity as the installed app.
