# Anchor Android 1.1.0 preview 4

Public release: October 11, 2026. Version code: 5.

## Changes

- Shared app quota groups synchronize their allowances when a member is edited. Existing inconsistent groups use the lowest allowance, and edits cannot change a frozen member indirectly.
- Earned leisure purchases extend an active window. Near the local 4 AM reset, leisure and charges are capped at the remaining time, rounded up to a whole minute.
- App Blocker shows remaining 24-hour rule-freeze time and supports searching selected apps. Essential phone/system apps are excluded from the app picker.
- Scheduled blocking treats equal start/end times as all-day protection and safely clamps invalid hour/minute inputs.
- Plan supports pasting a syllabus and deleting assignments. Date parsing accepts Sept/Sept., explicit years, ISO and US hyphenated dates, and point/decimal weights while preserving chapter/page/minute ranges.
- Home's expanded Rest of today section includes later scheduled tasks with working Focus and Done actions.
- JSON backup import accepts Windows tasks, course/syllabus records, focus sessions in seconds, harbor progress, and fun links. Native Android backups include the broader local planning/history records.
- Additional planning, recovery, local-AI, habit, and focus-persistence refinements are included from the preceding development waves.

## Privacy cleanup

- New profiles and database upgrades no longer add developer-selected leisure sites or app blocking rules. Users choose their own links and protection settings.
- Local build paths are remapped during native compilation, including the bundled inference library. Generated PDF and DOCX test fixtures replace developer-local documents in the public source.
- Public source and APK payloads are screened for private paths and accidental user-data files. Existing local user records remain intact.

## Backup compatibility

Backup import is a manual transfer of supported records, not live synchronization. The two platforms retain separate local databases and protection settings. Some fields have no equivalent on the other platform, and seconds are converted to Android's whole-minute session fields. Review imported assignments and planning records after restoring.

## Verification and installation

All 223 Android unit tests passed, and Android application compilation passed. The shared desktop test run also passed all 8 tests. Physical-device protection checks remain pending.

The ARM64 APK bundles the verified MiniCPM5 Q8_0 model and uses the existing Android debug signing identity. Model and native payload checks, APK signature verification and the release privacy screen passed. The APK is about 1.24 GB; allow about 2.4 GB for the APK and extracted model, plus existing data.

Install the APK from this release on an ARM64 Android device running Android 9 or newer. It can update an installation using the same signing identity, including the preceding preview builds. Older installations signed with a different key cannot update in place. Preserve needed data before uninstalling; uninstalling clears local app data. Physical-device installation, inference and protection checks remain pending.
