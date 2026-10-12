# Integration branch license scope

This branch adds Samsung Health Data SDK 1.1.0 as a manually supplied local AAR,
and Google Play Services authentication 21.3.0 plus its required dependencies.
Original Anchor code carries AGPLv3 with the additional permission in
`licenses/ANDROID_SDK_EXCEPTION.txt`; vendor and third-party terms remain separate.
The standard AGPL text is unchanged. Prior MIT and GPLv3 grants remain valid.
The linking permission does not waive AGPL section 13. Third-party SDK licenses
are not changed by relicensing original Anchor code.

The resolved SDK-free inventory in `licenses/dependency-inventory.json` is a
baseline, not a claim that this branch has no proprietary runtime components.
Existing upstream notices remain intact. The SDK-free release verifier rejects
this edition intentionally and must not be used as evidence that a resulting
integration APK is cleared for distribution.

See [integration setup and distribution requirements](integrations.md) before
building. Current CI screens public integration source only; local compilation
and unit tests do not establish vendor approvals or physical-device behavior.
