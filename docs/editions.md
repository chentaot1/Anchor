# Editions and branches

Both editions have public source in this repository. Branches in a public repository are public; the integration branch is not a privacy boundary.

| Branch | Android connections | License for original Anchor code | Maintenance |
| --- | --- | --- | --- |
| `main` | Health Connect and device Calendar Provider | AGPL-3.0-only | Unmaintained snapshot |
| `codex/vendor-integrations` | Direct Samsung Health Data and Google authentication/calendar source | AGPLv3 with a scoped SDK linking permission | Active development edition |

Plant models and the forest stay removed from the current source of both editions. Recent planning, focus and Windows fixes remain. Legacy backup/database fields stay readable.

## SDK-free snapshot

The public repository contains the AGPL-3.0-only Android and Windows snapshot. Android uses AndroidX Health Connect and the device Calendar Provider. Samsung Health can share data through Health Connect; a Google calendar can be read when synced to the device calendar. No Samsung SDK or Google Play Services SDK is required.

The SDK-free edition is an unmaintained snapshot. Current public branches start from clean source snapshots. Pre-GPL releases and their tags are removed. Android preview 5 and Windows 1.0.6 remain GPLv3 downloads with matching source archives and parentless GPL source tags; their binaries are not relabeled AGPL. Notices and build instructions remain available. Third-party components retain their own licenses.

## Integration source

The integration branch publishes original Anchor integration source, not Samsung's SDK binary. Follow that branch's `docs/integrations.md` to obtain the Samsung SDK directly under its own terms and supply the AAR locally. Google authentication dependencies are resolved by Gradle. Vendor SDKs retain their own terms; an AGPL linking permission does not grant vendor distribution rights. Review vendor requirements before distributing an integration APK.

The retained local private checkout is optional for personal development and testing. It has its own Android application ID and no push remote. Personal defaults, credentials, signing keys, SDK binaries and local app data must stay out of new public commits on either branch.

## Moving changes between editions

1. Develop direct SDK integrations on `codex/vendor-integrations`. Never merge the whole integration branch into the SDK-free snapshot.
2. For an SDK-independent fix suitable for the snapshot, review and copy or cherry-pick only that change into `main`.
3. Run `python tools/verify_public_source.py` on `main`. It rejects vendor namespaces in application/build source, SDK AARs, unreviewed JARs, nested checkout paths, plant models and common personal-data artifacts.
4. On the integration branch, use `python tools/verify_public_source.py --edition integrations`. Only the reviewed integration source paths may reference vendor namespaces; SDK binaries and personal-data artifacts remain rejected.
5. Source screening does not verify a binary release. Resolve and review dependencies, preserve notices and matching source, and verify packaged component/privacy checks before distributing new binaries. The SDK-free dependency inventory and release verifier do not cover the integration branch's additional SDKs.

Before declaring the public repository clean, separately audit all reachable branches/tags, older releases and CI artifacts. Deleting a file from the latest commit leaves earlier Git objects and published downloads available. Removing public references does not recall downloaded copies or guarantee removal of GitHub caches.
