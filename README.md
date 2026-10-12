# Anchor — direct integration source

Android and Windows planning, local AI and focus tools, with direct Samsung
Health Data and Google authentication/calendar integrations in Android.

This is the public **codex/vendor-integrations** branch. Original Anchor code
uses **AGPLv3 with a scoped Android SDK linking permission**. Samsung's SDK is a
locally supplied build input; vendor terms remain separate. The SDK-free AGPL
snapshot remains on [main](https://github.com/chentaot1/Anchor/tree/main).

Plant models and the forest stay removed. Recent planning, focus and Windows
fixes remain. Personal data, defaults, credentials, signing keys and vendor SDK
binaries stay out of new commits. Current public branches use clean source
snapshots. The prior GPLv3 releases
and their corresponding source remain available; pre-GPL downloads are removed.

Read [integration setup](docs/integrations.md), [edition boundaries](docs/editions.md)
and the [SDK linking permission](licenses/ANDROID_SDK_EXCEPTION.txt).

**Maintenance status:** Active development focuses on this integration branch.
The SDK-free main branch is an unmaintained snapshot. Existing downloadable
releases are prior builds; this branch does not publish an integration APK.

See the [AGPL migration and retained GPL releases](docs/license-migration.md).

New Android builds exclude model weights and offer an optional download during
onboarding or later in Settings. Existing installed weights survive upgrades.
For a possible F-Droid submission, use the SDK-free main edition and read
[F-Droid preparation](docs/fdroid.md). No Android-only repository is required.

Downloads: [Android preview 5](https://github.com/chentaot1/Anchor/releases/tag/android-v1.1.0-preview.5) and [Windows 1.0.6](https://github.com/chentaot1/Anchor/releases/tag/windows-v1.0.6). These retained GPLv3 builds use the SDK-free edition; current branch source uses AGPLv3.
