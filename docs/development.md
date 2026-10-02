# Developing and installing Anchor

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

Gradle dependencies, native sources, and model weights may need network access on the first build. Model downloads are separate from local inference. Google Calendar import is an optional network integration.

## Desktop development

```powershell
.\gradlew.bat :desktopApp:test
.\gradlew.bat :desktopApp:run
```

Desktop unit tests do not require GGUF weights. In Settings, configure a compatible `llama-server.exe` and a local GGUF file to enable model inference. The client starts a background process and communicates with it over localhost.

Build a Windows application image:

```powershell
.\gradlew.bat :desktopApp:createDistributable
```

`Launch-Anchor.bat` uses `desktopApp/build/compose/binaries/main/app/Anchor/Anchor.exe` when it exists, and otherwise launches through Gradle. Rebuild that image to pick up source changes in an existing installation.

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

This portfolio repository contains source and release notes; APK release assets are not included.

1. Build the APK using the commands above.
2. Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone.
3. Open the file and allow installation from the file manager when Android prompts.
4. Let Settings finish preparing the bundled model. Anchor copies it into private storage, verifies it, and activates it; old weight files are preserved.
5. Enable Anchor in **Android Accessibility settings** for app protection. On Samsung, set its battery mode to **Unrestricted**.
6. Optional: configure Google Calendar import with your own Google Cloud OAuth client for package `com.anchor.adhd`.

Read the [Android blocking rules](mobile-blocking.md) before enabling protection. Native Accessibility behavior and ARM64 inference need verification on the intended physical device; successful host checks alone do not establish those results.

## Tests and CI

```powershell
.\gradlew.bat :desktopApp:test
.\gradlew.bat :app:testDebugUnitTest
```

The desktop suite covers routing, structured breakdown validation, academic context, blocker policies, database behavior, and platform helpers. Android tests cover scheduling, focus and replan behavior, and app-protection policies and service logic.

The [Android CI workflow](../.github/workflows/android-build.yml) runs unit tests on configured branches, pull requests, and manual runs. It does not run desktop tests. An `android-v*` tag can additionally stage the model, build and verify the APK, and publish it when `ANCHOR_ANDROID_CI_RELEASES` is enabled.

## Android signing and updates

Android tags are independent of desktop releases. Automated Android publication is opt-in and requires the persistent `ANCHOR_ANDROID_KEYSTORE_BASE64` signing secret. The workflow refuses to publish without that key. Locally signed APKs can be published without exporting the key.

The 1.1.0 preview established a persistent signing identity. Older GitHub APKs used different debug keys, so Android cannot update those installations in place with this preview. Check the relevant [release notes](releases) before replacing an installation.

Preserve needed data before uninstalling, which clears app data. The current Settings backup includes tasks, assignments, and calendar events; it does not include every setting or record. Releases using the same persistent signing identity can update in place.

## Local files

Keep `local.properties`, credentials, signing keys, runtime executables, weights, databases, and generated build outputs out of commits. The repository's `.gitignore` excludes these categories. Personal app data belongs to each platform's local installation; Android and desktop currently have separate data and settings with no cross-device sync.
