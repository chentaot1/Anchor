# Anchor Windows 1.0.4

Source update: October 11, 2026. This extends the October 9 source publication.

## October 11 improvements

- Awake-time focus and break countdowns exclude sleep and hibernation. Active sessions checkpoint and recover paused, retaining fractional elapsed time, the original task, and the save identity. Durable, retried focus credits prevent duplicate history and rewards.
- Pending Home/Rescue captures, routed actions, Airlock drafts, generated steps, manual Milestones entry, and ordinary task changes belong to the application and survive screen navigation. Failed saves retain feedback and a stable retry identity. Drafts and Undo history do not survive process exit.
- Task insertion, private save acknowledgements, NOW promotion, completion, deletion, and Undo use acknowledged transactions. Undo restores captured-task identities, and NOW ordering supports imported integer extremes while preserving the other tasks' relative order.
- Ready timer controls and default preferences support exact durations from one through 240 minutes. Changing the default preserves open work and breaks. Home resumes paused work; Spark has an explicit three-minute work policy. Compact pages and duration-dialog bodies scroll to expose their actions.
- Backup replacement validates supported data, writes a safety backup before replacement, and rolls back partial writes. Focus statistics use the local 4 AM logical day and preserve fractional work. Pending saves and checkpoint failures guard data replacement and restart.
- Single-instance startup handles lock failures and restores the existing window. Updater processes handle cancellation, timeouts, child-process cleanup, and fast-forward failures. Native icon sizes and packaged Java Access Bridge contents are verified.

[Detailed implementation and verification history](../windows-improvements.md) records the tested behavior and remaining limits.

## October 9 changes

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

Desktop compilation and all **323 desktop tests** pass, alongside **8 shared tests**, with no failures, errors, or skipped checks. Coverage includes real SQLite rollback and retry, task/capture identity, imported sort limits, real preference-file reopening, controlled cancellation, timer recovery, daily statistics, local scanned-syllabus OCR, and platform helpers.

Isolated native Windows checks exercised workflows at **900 by 600 and 640 by 450 dp**, including full one- and three-minute sessions, retained drafts, rejected writes and retry, exact task/duration acknowledgement, unchanged paused timer identity, compact scrolling, and single-instance activation. Checks did not establish physical sleep/hibernation behavior, native app blocking on every target device, actual screen-reader interaction, or all display scaling configurations. Document integration tests depend on locally available fixtures.

The application image and both local 1.0.4 installers rebuilt successfully. Source-build and packaged JAR hashes match, and the packaged Access Bridge module/classes and x64 libraries pass verification.

No 1.0.4 Windows installer or application image has been uploaded as a release asset in this update. Rebuild the desktop client from source to use these changes. Cross-platform backups transfer supported records and do not synchronize protection settings or every platform-specific field.

## Public-source privacy cleanup (October 11)

Removed developer-specific courses, timetables, saved leisure sites, protection defaults and local document paths. New profiles start with user-supplied academic and protection data. Scope decisions follow the current task across academic subjects; local model results are cached per task. PDF, scanned PDF and DOCX integration tests generate fictional fixtures. Public-source screening runs in Android CI and is documented in `docs/publication-privacy.md`.

The cleanup passed 323 desktop, 8 shared and 223 Android unit tests. The sanitized Windows application image, MSI and EXE were rebuilt locally. Existing local records are retained. Public source cleanup does not erase earlier Git history or previously downloaded copies.
