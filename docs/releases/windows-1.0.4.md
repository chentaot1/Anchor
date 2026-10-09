# Anchor Windows 1.0.4

Source update: October 9, 2026.

## Changes

- Import Android JSON backups: assignments become courses and syllabus items; focus-session minutes become seconds; companion progress maps to harbor statistics; fun links are restored.
- Export fun links, including disabled entries, alongside existing planning, syllabus, focus-history, and harbor data.
- Focus launches from Home, Plan, AI Tools, and Syllabus promote the selected task and use its intended duration. Existing incomplete tasks can be reused instead of creating duplicate entries.
- Ctrl+K opens Airlock, Ctrl+1–7 selects tabs in sidebar order, Ctrl+I toggles Floating Island, and Space starts/pauses the timer on Focus.
- Escape dismisses Airlock, the duration picker, or a rabbit-hole prompt before exiting fullscreen.
- Syllabus parsing handles Sept/Sept., explicit years, ISO/US hyphenated dates, and point/decimal grading weights while preserving chapter/page/minute ranges.
- Scanned-PDF OCR uses the correct Windows bitmap types and async bridge. Failed OCR processes no longer return error output as syllabus text.
- App protection refreshes across curfew and class transitions. The 4 AM quota reset preserves the night curfew until 7 AM, and curfew detections show the matching reason.
- Additional local-AI, task persistence, blocker, recovery, and platform refinements are included from the preceding development waves.

## Verification and availability

Desktop compilation and all 115 desktop tests passed, including the local scanned-syllabus OCR tests. All 8 shared tests also passed. Quota tests use explicit times, and scope tests reflect the existing 60-second grace period. Keyboard behavior and native blocking still need manual device checks; document integration tests depend on local fixture availability.

No 1.0.4 Windows installer or application image has been published in this update. Rebuild the desktop client from source to use these changes. Cross-platform backups transfer supported records and do not synchronize protection settings or every platform-specific field.
