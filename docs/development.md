# Developing and installing Anchor

On `codex/vendor-integrations`, read [integration setup](integrations.md) first. Android builds require a locally obtained Samsung AAR, and original Anchor code carries the additional permission in `licenses/ANDROID_SDK_EXCEPTION.txt`. Windows-only builds do not require Samsung's AAR. The SDK-free release inventory/checks and existing release links below describe the `main` snapshot, not a cleared integration APK.

For a product overview and code entry points, start with the [README](../README.md).

## Prerequisites

- **JDK 17** and the included Gradle wrapper.
- **Android SDK platform 36**, matching `compileSdk` in the Android modules. Android requires API 28 or newer at runtime; the current target SDK is 35.
- For an Android native build: **NDK 27.0.12077973** and **CMake 3.22.1**.
- **Python 3** for Android model staging and APK verification.
- **Windows** for the desktop Win32 blocking and OCR integrations.

The project configures all three modules (`app`, `shared`, and `desktopApp`), so configure the Android SDK even when using desktop Gradle tasks. Create an ignored `local.properties` at the repository root using your SDK location, for example:

```properties
sdk.dir=C:/Android/Sdk
```

Gradle dependencies, native sources, and model weights may need network access on the first build. Model downloads are separate from local inference. Calendar occupancy reads calendars synced to the device; Health Connect is an optional read-only integration.

## Desktop development

```powershell
.\gradlew.bat :desktopApp:test
.\gradlew.bat :desktopApp:run
```

Desktop unit tests do not require GGUF weights. In Settings, configure a compatible `llama-server.exe` and a local GGUF file to enable model inference. The client starts a background process and communicates with it over localhost.

For a disposable Windows UI check, use:

```powershell
.\gradlew.bat :desktopApp:runUiSmoke
```

This development-only entry point opens the production windows without registering Windows startup. It redirects app data, preferences, and model discovery to the ignored `desktopApp/build/ui-smoke-profile` directory, disables local AI and seeds disabled protection rules, and runs outside the Git checkout so automatic source-update checks cannot fetch the repository. The profile remains available for restart/recovery checks. Startup controls and browser links still target Windows/the browser if clicked; use ordinary task and timer controls for isolated checks. The smoke launcher is test source and is excluded from packaged releases.

For repeatable window-layout checks, use `:desktopApp:runUiSmoke -PuiSmokeWindow=900x600` (dimensions in Compose dp). This opens the same production UI in a floating window at the selected size. Omit the property for the usual fullscreen startup. Run one smoke instance at a time.

Close a smoke instance before rebuilding its source or JARs. The development process loads classes from shared Gradle output; replacing that output while it is running can mix old outer classes with newly loaded coroutine classes. Finish the build before reopening the fixture. Read-only checks and documentation edits can run while a fixture is open.

For an isolated Rescue thought-save check, add `-PuiSmokeRescue=true`. This test-source fixture displays the production Rescue overlay with its application-owned capture controller and disposable SQLite database. Return to Focus dismisses the fixture and Reopen rescue restores it; the acknowledgement count increments only after a successful save. The fixture does not close browser tabs, manipulate other windows, activate protection rules, or grant actual passes. It is excluded from packaged releases.

Home uses one page scrollbar, including expanded router results. Its action and summary columns stack when the available content width is below 880 dp, and its preset/stat tiles choose column counts from their card widths. Headers wrap secondary labels onto another line when needed. Tile widths are allocated in whole display pixels so rounding cannot push the last tile into an extra row.

Home thought capture keeps its draft in application state across layout changes and navigation. It clears the field only after a successful save and preserves it with retry feedback on failure. Repeated submissions are blocked while saving. A stable capture identity and transactional task acknowledgement prevent retries from duplicating committed thoughts; intentionally capturing identical text again creates a new task. Reset, restore, and restart are gated while saving. Unsaved drafts are held in memory and do not survive application exit or a process crash.

Rescue's Scribe uses a separate application-owned draft with the same transactional capture and retry policy. Saving makes the field read-only, disables repeated Scribe submissions, and reports failures without clearing or dismissing the draft. Dismissing and reopening Rescue retains the draft and feedback. Returning after a save runs only after acknowledgement; failure to return cannot turn a committed capture into a failed save. The displayed detection is captured at submission, so a completed save cannot dismiss a later Rescue overlay, and native return actions run only while that original detection still belongs to the monitor. Reset, restore, restart, router actions, and Airlock saves wait for Scribe acknowledgement. Draft recovery across process exit is not provided.

The router uses a shorter, one-line placeholder and omits its decorative icon below 480 dp of card width to leave room for typing. Its result headline reserves space for Dismiss, and action buttons wrap onto additional rows. The capture placeholder also stays on one line. Expanded cards remain part of Home's page scroll, rather than consuming a fixed header above a shrinking content area.

Router state belongs to the application: its next draft and current result survive navigation away from Home. Each submission owns a request identity, so a cancelled enrichment cannot overwrite a newer result, clear its busy indicator, or report an old error. Dismiss and Escape in the focused router field preserve the next draft; Clear router explicitly clears both draft and result. Failed enrichment leaves the immediate result usable with feedback. Quick-note previews show the note and say it is ready to save; they do not write tasks until a save action is chosen. These states are held in memory and do not survive application exit.

Router save, focus, and shield actions reserve their displayed result and cancel its enrichment before calling asynchronous handlers. The preview remains visible until the handler acknowledges success; a failure retains it for retry, along with the next draft. Action buttons, routing, Dismiss, and Clear are blocked while an action runs. Task lists commit with their private acknowledgements in one transaction. Stable per-item identities let retries or alternate save actions on the same preview reuse committed items; a deliberately new query creates fresh tasks. Duplicate items within one list retain their separate occurrences. Focus preparation reads fresh database state and commits creation/duration/NOW selection together, avoiding duplicate tasks from a stale UI snapshot or a partially failed preparation. Reset, restore, and restart wait for router actions to finish. Once an action starts, its acknowledgement finishes even if its coroutine owner is cancelled; process-crash recovery of the preview is not provided.

Windows task durations, timer commands, and the custom picker share the one-to-240-minute bounds in `DesktopDurationLimits`. The shared picker accepts a platform maximum; its default remains 180 minutes and the Windows wrapper supplies 240. Countdown adjustments cap new remaining time at 240 minutes without shortening an already longer recovered timer. Subtraction below one minute leaves shorter remaining time intact rather than adding time, and a zero adjustment is a no-op. Picking a total duration continues to subtract time already worked; the remaining-time adjustment buttons preserve elapsed work separately.

Focus's `3m Spark` starts a new three-minute work session when no work session is open, including during a restorative break. Its countdown, duration chip, progress ring, and planned credit therefore agree. During an open work session the same control reads `3m remaining`: it sets an exact three-minute remaining-time target, preserves elapsed work, task attribution, planned duration, and save identity, and resumes a paused timer. It does not change the saved default duration. Setting the target updates the awake clock first instead of deriving a delta from the last rendered second.

Without timer recovery, Windows initializes its ready duration from the displayed incomplete task, falling back to the saved default when there is no task; either value is bounded to one through 240 minutes. Home starts the displayed ready duration, including a picker override, rather than substituting the task's stored duration after preparation. The override affects the session plan without rewriting that task's duration. A paused work or break timer uses Resume on Home and keeps the existing clock and work save identity; an active timer's Home action opens Focus. Home uses the session's task snapshot while work is open, and its primary action identifies work versus break and displays remaining time when paused. Recovery retains precedence over the initial ready duration.

Focus's +/-5m controls change the complete ready work duration when no work session is open. The countdown, duration chip, Home start label, planned session duration, and work duration restored after a break therefore agree; the one-minute/four-hour bounds apply to this ready plan. These adjustments do not write the task duration or saved default preference. Once work is open, or during a break, the controls keep using remaining-time adjustments and retain all elapsed time. Recovery, data operations, router actions, and Airlock saves gate these controls.

Windows Airlock previews do not write tasks. Its draft, selected preview, and failed-save feedback belong to the application and survive closing/reopening the dialog. Decompose previews five-minute steps and retains separately parsed secondary thoughts as 15-minute tasks; Extract Tasks previews 15-minute tasks. Choosing a preview item saves the whole list and selects that exact occurrence for NOW; Save All to Inbox leaves NOW unchanged. These actions await one transaction for tasks, private capture acknowledgements, and optional NOW selection. A failed or ambiguous acknowledgement retains the frozen payload and save identity for an idempotent retry. Repeated identical items remain distinct. Saving disables editing, regeneration, dismissal, and replacement/restart operations; once begun, its acknowledgement survives owner cancellation. New generation, draft edits, dismissal, or saving invalidate old refinement, and changed refined content receives a new control identity. Saving an older preview preserves a separately edited next draft. The dialog scrolls as one bounded page with a visible scrollbar when needed. Draft and preview recovery across application exit is not provided.

Milestones' generated-step previews also belong to the application and retain their save identities across navigation, collapse/reopening, and failed acknowledgements. The preview shows every step; Add All saves the complete five-minute list in one transaction. Start Step 1 saves only the first occurrence, promotes that exact task ID for NOW in the same transaction, then starts it after acknowledgement. Alternate actions reuse the first step without duplicating it, and repeated identical steps within a list remain distinct. Saves reject a missing, completed, or renamed source task. Late refinement from an old task or a frozen save cannot replace the preview; changed refinement invalidates old controls. Saving reserves the action immediately and gates data replacement/restart, router and Airlock actions, and competing timer starts. Cancellation before launch retains the preview without writing; an in-flight write finishes after owner cancellation but does not start a new timer in the cancelled owner. Unsaved previews are not recovered across process exit.

Milestones scrolls its header, add form, feedback, task cards, and full previews in one lazy page. Compact card widths place task actions below the readable title; named task controls have 44 dp targets. Page headings, counters, and preview actions wrap, and the short add-field placeholder stays on one line. Small windows can scroll to every preview action instead of losing the list below a fixed form.

Manual Milestones entry has its own application-owned capture session, separate from Home and Rescue. Navigation retains unsaved text and failed-save feedback. Submission freezes the trimmed title, current ready duration, and save identity; retries keep that duration even if the ready timer changes. Editing a failed draft creates a new request using the current duration. Task insertion and private acknowledgement commit together, and retrying a committed identity neither inserts another task nor rewrites its stored duration. The field stays read-only and its action shows Saving while acknowledgement is pending. Competing task actions, captures, generated saves, router actions, new timer starts, and data replacement/restart wait. Cancellation before launch releases the shared reservation and retains the draft; after writing starts, acknowledgement finishes. Successful acknowledgement clears the draft and reports success through a polite live region. Unsaved drafts do not recover across process exit.

Ordinary NOW changes validate the exact incomplete task ID, clear the previous selection, promote the target, and read its fresh metadata inside one transaction. Failed, missing, completed, or silently ignored promotions cannot commit a cleared selection or changed ordering. The returned task snapshot is read from SQLite, independent of the previously published task flow. Airlock, generated Milestones steps, and named-focus preparation share the same promotion helper within their existing transactions. SQL selection failures retain their diagnostic cause but expose plain retry feedback.

Windows Settings offers exact default focus durations from one through 240 minutes, plus quick presets. Its custom dialog identifies the default preference separately from the current timer. The dialog body scrolls in small windows while its Save and Cancel actions remain reachable. Saving reserves an application-owned operation and persists the preference before updating the ready plan; an open work session or a break retains its countdown and receives the default for its next plan. Save feedback survives navigation. Work and break preference writes reject out-of-range minutes; legacy stored values are bounded on every timer read without rewriting the original file.

NOW selection supports the complete imported integer sort range. If decrementing the lowest rank would overflow, it rebases the other tasks in their existing sort order with the newest-ID tie break, then puts the selected task ahead. Rebase writes, previous-selection clearing, final promotion, and validation share the existing task transaction. Final acknowledgement verifies every expected rank and exactly one selected task; ignored or rewritten updates cannot commit a partial order. Task metadata and private save identities stay intact.

Home Make NOW and Milestones' ordinary focus button reserve an application-owned operation and await promotion. A failure leaves the timer unchanged and reports feedback instead of throwing an unhandled UI exception. Milestones starts the freshly read task with its stored duration; Home still starts its displayed ready duration while using fresh attribution. Cancellation of the application owner prevents a later acknowledgement from opening a new timer. Pending operations block competing task controls and repeated focus preparation.

Ordinary task changes also use the application-owned operation reservation and feedback. Milestones completion, NOW selection, deletion, Clear Completed, and Undo no longer keep busy/error state inside the screen. Home completion and the Focus completion-banner action use the same acknowledged path. UI completion requests set the displayed desired state explicitly rather than toggling whatever state happens to be stored at execution time. The banner remains until completion is acknowledged. Once an operation begins, its database acknowledgement and app-owned Undo bookkeeping finish despite owner cancellation; cancellation before launch writes nothing. Task completion, deletion snapshots, Undo insertion, and published task state share the task transaction boundary. Missing completion targets and ignored or partially applied writes cannot acknowledge success. SQL failures show plain feedback while retaining their diagnostic cause. Undo retains its existing ID-conflict and NOW policy. Deletion captures both task rows and their private save acknowledgements in a typed batch before cascading removal. Undo restores both in one transaction, preserving original capture text independently of edited task titles. A later capture-key conflict or failed/ignored acknowledgement insertion rolls back every restored row and identity. History keeps the complete batch across navigation and failed retries, preventing original capture retries from duplicating a restored task.

Normal startup and the smoke launcher acquire an exclusive `%APPDATA%\Anchor\anchor.lock` lease before opening preferences, recovery records, or the database. A second launch brings the existing window forward, restoring it only if minimized so an already visible window keeps its layout. If the lock cannot be opened or acquired because of an IO failure, Anchor stops startup and shows a scrollable, selectable error with the affected path. It does not continue into startup registration or app data access. A leftover lock file alone does not prevent startup; ownership is an operating-system file lock and is released when its process exits.

For an isolated startup-error dialog check, run `:desktopApp:runStartupErrorSmoke`. It puts an obstructing file in an ignored fixture folder and opens the production error dialog, without opening personal app data or changing Windows startup. Dismiss with OK or Enter. The child exits with code 1 and Gradle reports this verification task as failed; that nonzero result is expected for this deliberate error case. This launcher is test source and is excluded from releases.

Build a Windows application image:

```powershell
.\gradlew.bat :desktopApp:createDistributable
```

`Launch-Anchor.bat` uses `desktopApp/build/compose/binaries/main/app/Anchor/Anchor.exe` when it exists, and otherwise launches through Gradle. Rebuild that image to pick up source changes in an existing installation.

Build Windows installers with `:desktopApp:packageMsi :desktopApp:packageExe`. They are written to `desktopApp/build/compose/binaries/main/msi` and `desktopApp/build/compose/binaries/main/exe`. These are local development builds using the configured 1.0.5 package version; building does not install or publish them.

The Windows runtime includes `jdk.accessibility` for [Compose's Java Access Bridge support](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html). After building the application image, run `python tools/verify_windows_accessibility.py` with `JAVA_HOME` set to the development JDK, or pass `--jdk "C:/path/to/jdk-17"`. It checks the runtime's module manifest, actual Access Bridge class, and both x64 native libraries. An optional positional argument selects a different application-image directory.

Java Access Bridge must also be enabled for the user, and a compatible screen reader is needed for interaction testing; see [Oracle's enablement and testing instructions](https://docs.oracle.com/en/java/javase/17/access/enabling-and-testing-java-access-bridge.html). Packaging and this checker do not change those Windows/user settings or establish screen-reader behavior. On Windows, Compose recommends NVDA's Speech Viewer or JAWS's Show Speech History for manual verification.

The tray, Compose windows, and Windows package icon share the drawing in `DesktopAppIcon.kt`. After changing it, run `:desktopApp:generateWindowsIcon`, inspect `desktopApp/build/icon-preview.png`, then run tests and rebuild the application/installers. The generated `src/main/resources/anchor.ico` contains seven sizes from 16 to 256 pixels and belongs in source control. Tests verify its decoded pixels against the shared drawing and load every size through Windows. Packaging uses the [Compose native-distribution icon configuration](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

The in-app source updater checks a fast-forward pull and desktop compilation before reporting success. Its restart waits for the existing instance to exit, then launches the updated source through Gradle. `Update-Anchor.bat` likewise checks the pull and build results and launches source; close a running instance first. Both need the development prerequisites above. Neither refreshes an existing standalone application image. A standalone installation without a Git checkout cannot check source updates. See the [Windows improvement log](windows-improvements.md) for current development changes.

Windows Settings previews backup sections before restoring them. Only listed sections are replaced; omitted Windows data stays intact. Restore and reset actions save an `anchor_auto_backup_*.json` recovery copy in the app data folder before deleting records and stop if that copy cannot be written. Finish or reset the current timer before replacing data. Exports read one database snapshot and use a temporary file so concurrent saves cannot mix sections and a failed write does not truncate an existing backup. Focus history and its progress totals are committed together.

Windows startup registration uses the current user's registry through native APIs. Settings reads the registration off the UI thread, reports failures, and saves the requested preference only after the registry change succeeds. Desktop registry tests use disposable keys and do not change the real Anchor startup entry.

Finished focus work is written to `%APPDATA%\Anchor\pending-focus` before it is added to the database. Anchor retries those records on startup and offers Retry when saving fails. Save identities prevent duplicate history or rewards after an interrupted cleanup, and recovery retains each session's original completion date. If writing a recovery record fails, the banner asks you to keep Anchor open while retrying. Reset, restore, and in-app update restart wait until pending focus records and the latest timer checkpoint are saved. Malformed recovery records are kept with a visible file path for repair rather than silently discarded.

Active focus sessions and restorative breaks checkpoint to `%APPDATA%\Anchor\timer-recovery.json` every five seconds and after timer controls change state. After unexpected termination, Anchor restores the last successful checkpoint paused, preserves fractional elapsed time and the original task, and excludes time while the process was closed. The checkpoint also retains unfinished history-save handoffs; already saved session identities prevent stale checkpoints from reopening completed work. Invalid checkpoint files are preserved. The Archive action keeps their exact bytes in a separate file before enabling new checkpoints. Recovery banners scroll within bounded areas, and a tray notification makes paused recovery visible after background launch. Changes since the last successful checkpoint can be lost in a forced shutdown.

Windows focus and break countdowns measure time while the device is awake using [QueryUnbiasedInterruptTimePrecise](https://learn.microsoft.com/en-us/windows/win32/api/realtimeapiset/nf-realtimeapiset-queryunbiasedinterrupttimeprecise). Sleep and hibernation do not advance either timer or earn focus minutes; a running timer continues when Windows wakes. UI delays while Windows is awake still count, and changing the system clock does not alter elapsed work. The native binding uses the Windows 10+ realtime [API contract](https://learn.microsoft.com/en-us/windows/win32/apiindex/windows-apisets), since the precise function is not directly exported by Kernel32 on every installation. Other desktop operating systems retain Java's monotonic clock. Checkpoint files store elapsed and remaining durations, so the clock change does not require a recovery-format migration.

Backup reads/exports, restore, reset, and asynchronous focus preparation use an app-owned operation reservation. Progress remains visible until the operation completes, competing operations cannot start, and results stay available across tabs until dismissed. Restore and reset recheck timer/recovery preconditions when confirmed. Database replacement reserves access across recovery-backup creation, the transaction, and state refresh; ordinary in-app connections wait until replacement ends. Writes arriving during replacement are then applied to the resulting database instead of disappearing between its recovery copy and deletion. Native dialog/keyboard interaction still requires manual Windows verification.

Fresh Windows profiles and cleared focus history start with zero streak days. The first completed focus session earns Day 1. Current-streak displays in Home and Settings expire after a missed logical day, using the 4 AM boundary and refreshing every 30 seconds; stored historical progress remains available for export. Undated legacy/imported streak values are retained because their age is unknown. Restoring focus history without aggregate statistics derives its streak from distinct completed logical days. New saves carry fractional minutes across sessions, and history-only imports sum seconds before converting to minutes. Explicit imported aggregate statistics remain authoritative.

Today's Windows focus minutes count saved work by its completion timestamp, matching streak attribution and recovered sessions. A session crossing 4 AM is credited to the logical day on which it ends; timestamps are not used to infer work during pauses. The daily query excludes future timestamps, uses the current time zone's local 4 AM boundary across daylight-saving changes, and sums fractional seconds before converting to minutes. Its end-time index supports periodic refresh as history grows. Today's minutes refresh every 30 seconds even with protection monitoring disabled, and after a focus save or data replacement.

## Android model and APK

The default bundled model is **MiniCPM5-1B-Claude-Opus-Fable5-Thinking Q8_0**. This is a local GGUF artifact; its name does not imply a hosted Claude API integration. The pinned weight file is 1,153,529,792 bytes. Model weights stay out of Git.

Stage the verified asset, test, build, and inspect the resulting APK:

```powershell
python tools/fetch_desktop_model.py
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
python tools/verify_android_apk.py
```

If you already have the exact model locally, stage it without downloading another copy:

```powershell
python tools/fetch_desktop_model.py --source "C:/Models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"
```

Both paths verify the model size and SHA-256. The APK verifier checks the packaged model bytes, asset compression, and the ARM64 JNI library. Native inference uses a pinned, hash-checked llama.cpp b9371 source archive; the default Android build targets ARM64.

The Android APK is approximately **1.24 GB** because it bundles the model. Allow around **2.4 GB** for the APK and extracted weights, plus existing data and old models. Build intermediates and emulator disks can require substantially more development storage.

See [Android inference implementation](desktop-model-on-android.md) for the model hash, runtime settings, and validation details.

## Install on Android without ADB

Download the latest APK from the [public Android release](https://github.com/chentaot1/Anchor/releases/tag/android-v1.1.0-preview.5), or build it locally using the commands above.

1. Build the APK using the commands above.
2. Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone.
3. Open the file and allow installation from the file manager when Android prompts.
4. Let Settings finish preparing the bundled model. Anchor copies it into private storage, verifies it, and activates it; old weight files are preserved.
5. Enable Anchor in **Android Accessibility settings** for app protection. On Samsung, set its battery mode to **Unrestricted**.
6. Optional: sync Google calendars in the device calendar app, then allow Anchor calendar access. For step/exercise habits, connect Health Connect in Settings and allow both read permissions. Enable Samsung Health sharing with Health Connect if that is your data source. Android 13 and earlier may need the Health Connect app installed.

Read the [Android blocking rules](mobile-blocking.md) before enabling protection. Native Accessibility behavior and ARM64 inference need verification on the intended physical device; successful host checks alone do not establish those results.

## License and release checks

The root `LICENSE` applies AGPLv3 (version 3 only) to original Anchor code. Package `licenses/` with both clients and preserve Windows `runtime/legal`. Update dependency notices before adding or upgrading libraries; third-party code retains its own terms.

```powershell
.\gradlew.bat -I tools/license_inventory.init.gradle anchorLicenseInventory
python tools/verify_release_components.py --inventory desktopApp/build/license-inventory.json --apk app/build/outputs/apk/debug/app-debug.apk --desktop-image desktopApp/build/compose/binaries/main/app/Anchor
python tools/verify_public_source.py
python tools/verify_release_privacy.py --apk app/build/outputs/apk/debug/app-debug.apk --desktop-image desktopApp/build/compose/binaries/main/app/Anchor
```

Publish the exact application source/build scripts alongside installers and the APK. Android native llama.cpp b9371 is pinned in CMake. For Windows built with Microsoft OpenJDK 17.0.20.1+1-LTS, distribute its matching source archive from commit `3a8c0bd1eefc84208d0d3abd89d01ef0a63a5002` and preserve the runtime's legal directory. Runtime source includes its build instructions in `doc/building.md`. The third-party source bundle includes 151 matching Maven source JARs and upstream Skiko, Skia, JNA and SQLite JDBC source archives, with their own build scripts/instructions and a URL/checksum inventory. Guava's conflict-avoidance `listenablefuture` placeholder contains no classes.

## Tests and CI

```powershell
.\gradlew.bat :desktopApp:test
.\gradlew.bat :app:testDebugUnitTest
```

On Windows, run `powershell -NoProfile -File tools/verify_windows_updater.ps1` to verify the one-click updater using disposable local Git repositories. It checks pull/build failures, detached branches, protected folders, and a fast-forward update that preserves both a local edit and a pre-existing stash. It does not contact a remote service or modify the project's Git checkout.

The desktop suite covers routing, structured breakdown validation, academic context, blocker policies, database behavior, and platform helpers. Android tests cover scheduling, focus and replan behavior, and app-protection policies and service logic.

The [Android CI workflow](../.github/workflows/android-build.yml) runs unit tests on configured branches, pull requests, and manual runs. It does not run desktop tests. An `android-v*` tag can additionally stage the model, build and verify the APK, and publish it when `ANCHOR_ANDROID_CI_RELEASES` is enabled.

## Android signing and updates

Android tags are independent of desktop releases. Automated Android publication is opt-in and requires the persistent `ANCHOR_ANDROID_KEYSTORE_BASE64` signing secret. The workflow refuses to publish without that key. Locally signed APKs can be published without exporting the key.

The 1.1.0 preview established a persistent signing identity. Older GitHub APKs used different debug keys, so Android cannot update those installations in place with this preview. Check the relevant [release notes](releases) before replacing an installation.

Preserve needed data before uninstalling, which clears app data. The current Settings backup includes tasks, assignments, and calendar events; it does not include every setting or record. Releases using the same persistent signing identity can update in place.

## Local files

Keep `local.properties`, credentials, signing keys, runtime executables, weights, databases, generated build outputs, and temporary test files out of commits. The repository's `.gitignore` excludes these categories. Personal app data belongs to each platform's local installation. JSON backup import transfers supported records between Android and desktop; there is no live cross-device synchronization, and platform-specific protection settings remain separate.

For isolated native Undo checks, `:desktopApp:runUiSmoke -PuiSmokeUndo=true` creates and deletes a dedicated captured fixture through the database, then seeds its complete Undo batch into the production UI. `-PuiSmokeUndoVerify=true` (after closing that instance) retries the fixture's original capture identity through the production database API and exits after checking the exact task ID, original duration, and unchanged counts. Both modes require the same marked disposable profile; normal launches start with an empty Undo history.

## Release privacy verification

Run `python tools/verify_public_source.py` before committing. After building, run:

```powershell
python tools/verify_release_privacy.py --apk app/build/outputs/apk/debug/app-debug.apk --desktop-image desktopApp/build/compose/binaries/main/app/Anchor
python tools/verify_android_apk.py
python tools/verify_windows_accessibility.py --jdk "C:/path/to/jdk-17"
```

The payload screen checks application/native strings and accidental user-data files. Optional `--patterns` reads additional private regular expressions from an ignored local file; never commit that file. Review installer metadata and signing certificates separately. Android native compilation remaps repository and NDK paths so local build usernames do not appear in the shipped inference library. Publish only verified artifacts with SHA-256 checksum assets.
