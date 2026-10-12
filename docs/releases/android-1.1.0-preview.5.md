# Android 1.1.0-preview.5

Anchor's original code is now GPLv3 (version 3 only). Full license texts and third-party notices are bundled in the APK and available in Settings → Data. Earlier MIT grants remain valid.

- Removed every plant GLB, the forest gallery, plant catalog, plant renderers and SceneView/Filament dependencies. Focus completion still awards its usual rewards once; it no longer plants trees. Existing backup records remain readable.
- Removed the proprietary Samsung Health Data SDK and Google Play Services authentication SDKs. Health habits now read today's steps and exercise through the Apache-2.0 AndroidX Health Connect client. Allow both read permissions; Samsung Health users must enable sharing with Health Connect. Android 13 and earlier may require installing Health Connect.
- Calendar occupancy uses calendars synced to the Android Calendar Provider. Sync Google calendars in the device's calendar app before refreshing occupancy. The separate Google sign-in/import flow is removed.
- Removed the unverified harbor image assets; the harbor is drawn in Compose code.

The debug-signed ARM64 APK includes the same checksum-verified local GGUF model as preview 4 and retains its signing identity for upgrades. Physical-device Health Connect permission/data sharing, inference, Accessibility enforcement and Samsung battery handling still need device verification.

224 Android, 323 Windows and six shared unit tests passed. The APK's model checksum, ARM64 runtime, signing certificate, bundled license texts and absence of plant/forest payloads and removed SDKs were checked. Source and application payload screens found no known personal identifiers or local home paths.

Matching application source and native-source archives accompany the release. Build instructions are in `docs/development.md` in the application source; model download instructions and checksum are included there. Dependencies and weights retain their upstream licenses.
