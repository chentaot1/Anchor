# Public publication checks

Public source must contain no personal coursework, timetables, local document paths, private browsing preferences, credentials, databases, backups or test output. Use fictional examples and generated fixtures. New profiles should obtain courses, schedules, leisure links and protection rules from the user rather than a developer's saved setup. Keep existing local user records intact during upgrades.

Before committing or pushing, run `python tools/verify_public_source.py`, inspect the staged diff, and review defaults, fixtures, prose and media metadata. The script checks tracked files for common private paths, credentials and private artifact types, and checks text files for email addresses. Binary paths and credentials are screened too; media metadata needs separate review. It is a screening check, not proof that every personal detail is absent.

Review the destination branch, tags, commit author metadata, release assets and published history separately. GitHub repository URLs and the required copyright attribution are public project identifiers. Do not copy a personal branch or its history into the public repository.

Deleting a detail from current source does not erase it from earlier commits, existing releases, forks or downloaded copies. Prepare and review any history replacement before force-pushing it; check the remote head again and use an explicit lease. GitHub may retain unreachable commits and cached views after a history replacement.

Release binaries need their own audit; source screening cannot detect compiler paths embedded in native libraries. Rebuild from sanitized source, run `tools/verify_release_privacy.py` against the APK and Windows application image, verify the model/signatures, and inspect installer metadata. Native builds remap source and NDK paths. Release tags must point only to sanitized commits; upload checksums and verify GitHub's asset digests before publishing.
