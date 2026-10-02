# Anchor Android 1.1.0 preview 2

Removes the home illustration, reorganizes Settings and fixes the phone's unreadable text and cramped timer controls. Common AI tools now follow the current desktop model, prompts, grammars and fast command routing.

## Changes

- Home opens with the current task and a full-width focus action. Games and the daily strategy follow it. Duration and Done controls no longer squeeze the primary button.
- Settings uses Focus, AI, Planning, Reminders, Connections, Data and Advanced categories. App protection is a primary Focus setting; AI shows the installed desktop model and a single installation action when needed. Removed duplicate reset dialogs.
- Fixed black text on transparent dark screens and thin variable-font rendering. App Blocker groups its controls, puts selected apps first and scrolls its rule editor.
- Focus uses readable duration chips that flow onto another row when necessary. Task text sits outside the dial, controls are closer together, and the page scrolls at larger text sizes. Navigation and selected duration survive activity recreation.
- Removed stale Qwen wording and unused startup 3D initialization. Loading AI no longer recreates the entire screen.
- Breakdown, brain dump, triage, replan and chat use the current desktop system prompts. The four shared JSON grammars match desktop, including empty defer lists. Breakdown uses the desktop default across all phone entry points.
- Chat includes recent user/assistant turns and goes directly to conversational inference after deterministic commands. Natural timer commands preserve their requested duration; blocker commands match installed apps and respect focus, lockdown and frozen rules. Resets still require review in Settings.
- Fixed JNI Unicode conversion and token delivery so emoji and split UTF-8 pieces are preserved.

## Model and installation

The APK bundles the same `MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf` as preview 1: 1,153,529,792 bytes, SHA-256 `8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c`. It prepares offline on first launch. CPU, context, cache, sampling and reasoning settings retain the verified desktop profile.

This APK updates **preview 1 in place** using the same signing certificate, package `com.anchor.adhd`, version code 3. Download the APK on your phone and install it over preview 1. It is about 1.24 GB because it includes the model. Existing installed model files are reused after verification.

Older v1.0.3 APKs used a different signing key and cannot update in place. Do not uninstall an older build until you preserve needed data; uninstalling clears it. Settings → Data → Export backup JSON covers tasks, assignments and calendar events.

## Verification

- 183 Android unit tests passed, including blocking safeguards, model migration and natural timer commands.
- Source checks verified all five common AI system prompts and four JSON grammars against the current desktop source.
- Actual Android x86_64 inference using the exact weights produced valid task-breakdown JSON with the 2048-token CPU profile and passed a forced emoji round-trip test.
- Visually inspected Home, Focus, Settings, AI settings and App Blocker at 1440 × 3120, including 130% Android text. The actual chat command `start a 10m timer` offered a 10-minute action and started a countdown near 10:00.
- Final ARM64 APK compilation, embedded model SHA-256 and signing certificate were verified before upload.

The emulator checks do not verify physical S24 Ultra inference performance, Samsung background behavior or Accessibility enforcement. Those device checks remain pending. Desktop-only website/window filters, Scope Sentinel and cross-device synchronization remain outside this Android update.
