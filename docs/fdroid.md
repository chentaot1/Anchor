# Android and F-Droid

An Android-only repository is not required. This repository's `app` Gradle module is the Android application; `shared` supplies common code. The Windows module can remain in the same repository. F-Droid build metadata can select Android build tasks and modules.

Use the SDK-free `main` edition for a possible submission. The `codex/vendor-integrations` branch includes proprietary Google Play Services and Samsung Health SDKs and is not a suitable F-Droid build. A linking exception does not change those SDK licenses.

## Optional model

New builds exclude all GGUF weights from the APK, even if a developer has staged a model locally. First-run onboarding offers equally accessible Download and Skip choices. Neither startup nor skipping requests a model. Planning, timers, habits and basic task breakdowns remain usable without it; tailored local inference requires the model.

The optional MiniCPM5 1B Q8_0 data file is downloaded from Hugging Face after an explicit choice. The screen discloses the 1.2 GB download, storage needs and Apache 2.0 model license. Downloading does not send task or conversation text. Size, GGUF magic and the pinned SHA-256 are checked before installation. Pause preserves partial bytes; retry resumes when the server supports ranges, including after process termination. Settings provides the same download later. Android may terminate a background process; this is not a foreground-service download.

The native inference runtime is compiled into the APK from pinned source. The downloaded GGUF is model data, not an executable plugin. The final build and dependency classification remain subject to F-Droid review.

## Before submitting

- Decide to maintain the SDK-free Android edition. Its current README labels it an unmaintained snapshot; F-Droid's inclusion policy requires active maintenance. Remove that label only after making that commitment.
- Prepare build metadata against an immutable AGPL source commit, with the Android SDK/NDK, Gradle and native-source versions pinned. Use `:app:assembleRelease`, not the GitHub debug APK signing workflow.
- Arrange for the pinned llama.cpp b9371 source archive to be fetched during F-Droid's source preparation, then supply it locally to CMake with `FETCHCONTENT_SOURCE_DIR_LLAMA`; the isolated build must not depend on a network fetch. Its archive SHA-256 is recorded in `app/src/main/cpp/CMakeLists.txt`.
- Review the complete resolved dependency graph, app ID, reproducibility, license notices and any anti-feature declarations with F-Droid maintainers. Optional model download alone does not establish eligibility.
- Verify the actual release APK contains no GGUF: `python tools/verify_android_apk.py app/build/outputs/apk/release/app-release-unsigned.apk`. This gate expects the default ARM64 inference runtime.

This document is preparation guidance, not a completed submission or acceptance claim. Previously published GPL preview 5 contains bundled weights; these delivery changes apply to new builds only.

References: [F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/), [build metadata reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).
