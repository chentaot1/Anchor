# Direct Samsung Health and Google integrations

This public branch contains original Anchor integration source. The SDK-free
snapshot remains on `main`. Plant models, the forest gallery and their rendering
dependencies stay removed. The optional local private checkout remains separate.

## Build inputs

Clone this source branch alone:

```powershell
git clone --single-branch --branch codex/vendor-integrations https://github.com/chentaot1/Anchor.git Anchor-integrations
```

Its new history starts from a source snapshot without vendor SDK binaries. The
public branches also use clean snapshots. Only the latest release, Windows 1.0.6, and
its GPLv3 source archives remain available; older release downloads are removed.

Use JDK 17, Android SDK 36 and NDK 27.0.12077973 as documented in development.md.
Download Samsung Health Data SDK **1.1.0** directly from Samsung and accept its
terms. Supply the AAR locally; it is not included in this source branch:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin "-PanchorSamsungSdk=C:/SDKs/samsung-health-data-api-1.1.0.aar"
```

The default ignored input is `.private-sdk/samsung-health-data-api-1.1.0.aar`.
Google Play Services authentication **21.3.0** is resolved through Gradle.
Windows-only build tasks do not require Samsung's AAR.
Do not commit SDK binaries, personal defaults, credentials or signing keys.

The Android app is **Anchor Integrations**, package `com.anchor.adhd.integrations`,
version `1.1.0-preview.5-integrations.1`. The UI preview uses
`com.anchor.adhd.integrations.uipreview`. This separate identity can coexist with
the public snapshot and local private app. Review supported backups before
transferring data. Google OAuth configuration and Samsung's app registration
must authorize the relevant package and signing identity for live integration.

For a local APK, stage the model with `tools/fetch_desktop_model.py`, then run
`:app:assembleDebug` with the Samsung AAR supplied as above. CI checks source
privacy and edition boundaries only; it neither uploads an SDK nor builds or
publishes an integration APK. Local host tests do not verify phone permissions,
watch/phone data access or Google sign-in.

## License and distribution

Original Anchor code uses AGPLv3 with the additional SDK linking permission in
`licenses/ANDROID_SDK_EXCEPTION.txt`. The standard AGPL text is unchanged. The
permission covers the named Android integrations; third-party code and SDKs
retain their own terms. It does not authorize SDK redistribution or replace
Samsung's partnership/key-signing requirements before app distribution.

Samsung process: https://developer.samsung.com/health/data/process.html
Samsung SDK: https://developer.samsung.com/health/data/overview.html
GPL section 7 linking permission template (also used for AGPL section 7): https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs

The existing dependency inventory and `verify_release_components.py` describe
the SDK-free edition and deliberately reject these SDKs. They are not a release
verification for this branch. Review the additional runtime graph, notices,
corresponding source and vendor permissions before distributing a binary.

```powershell
python tools/verify_public_source.py --edition integrations
python -m unittest discover -s tools -p test_public_source_boundary.py
```

The default source check remains strict for `main`. Integration mode allows
vendor namespace references only in the reviewed bridge/build files; it still
rejects AARs, unreviewed JARs, plant models and personal-data artifacts.
