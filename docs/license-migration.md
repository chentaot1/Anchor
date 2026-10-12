# AGPLv3 source and retained latest GPLv3 release

As of 12 October 2026, original Anchor code and documentation on the two current
public branches use GNU Affero General Public License version 3 only
(`AGPL-3.0-only`). The standard license is in `LICENSE` and
`licenses/AGPL-3.0-only.txt`. The integration branch also grants the scoped
Android SDK linking permission in `licenses/ANDROID_SDK_EXCEPTION.txt`.
That permission does not waive AGPL section 13 or grant vendor SDK rights.

Under section 13, a modified version that supports remote network interaction
must prominently offer its corresponding source to those remote users as the
license specifies. The source change does not add a telemetry service or alter
Anchor's current local operation. Source and build instructions for each
edition are on its public branch.

Third-party libraries, runtimes and model weights retain their own terms and
notices. This change applies to original Anchor material, not upstream code.
Earlier MIT and GPLv3 grants remain valid. Deleting a release or rewriting Git
history does not revoke rights already granted or recall downloaded copies.

Only the latest public release, Windows `1.0.6`, remains available. Its GPLv3
binary assets and matching `anchor-gpl-source.zip`, third-party and runtime
source archives remain unchanged. Its tag points to a parentless snapshot with
the same tracked source tree as the original GPL release commit. Do not treat
those binaries as AGPL releases; future AGPL binaries need matching AGPL notices
and corresponding source.

Current public branches use new parentless snapshots. All earlier release
downloads and tags, including both Android previews, are removed. A local history backup is retained separately.
Existing clones need resynchronizing after the rewrite; GitHub caches and
downloaded copies may retain old commits or files.

References:
- https://www.gnu.org/licenses/agpl-3.0.html
- https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs
