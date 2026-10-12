"""Screen tracked publication files without printing matched private values.

This catches common accidental inclusions; a human must also review defaults,
fixtures, prose, media metadata and Git history before publishing.
"""

import re
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "literal home directory": re.compile(
        rb"(?:[A-Za-z]:[\\/]+Users[\\/]+|/(?:Users|home)/)[A-Za-z0-9_. -]+",
        re.IGNORECASE,
    ),
    "email address": re.compile(rb"[\w.+-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}"),
    "private key": re.compile(rb"-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----"),
    "credential token": re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{32,})"),
}
PRIVATE_EXTENSIONS = {
    ".db", ".sqlite", ".sqlite3", ".jks", ".keystore", ".pem", ".key",
    ".pdf", ".docx", ".apk", ".aab", ".msi", ".exe", ".gguf",
}
PRIVATE_NAMES = {"local.properties", "keystore.properties", "google-services.json"}
PROPRIETARY_NAMESPACES = (b"com.google.android.gms", b"com.samsung.android.sdk")
INTEGRATION_SOURCE_PATHS = {
    "app/build.gradle.kts",
    "app/src/main/java/com/anchor/adhd/calendar/GoogleSignInBridge.kt",
    "app/src/main/java/com/anchor/adhd/calendar/GoogleCalendarImporter.kt",
    "app/src/main/java/com/anchor/adhd/health/SamsungHealthBridge.kt",
}
UPSTREAM_NOTICES = json.loads((ROOT / "tools/upstream_notice_checksums.json").read_text(encoding="utf-8"))


def component_failures(relative, data, edition="opensource"):
    """Enforce the public edition boundary even for force-added ignored files."""
    failures = []
    if relative.suffix.lower() in {".aar", ".glb"}:
        failures.append("proprietary SDK or removed model artifact")
    if relative.suffix.lower() == ".jar" and relative.as_posix() != "gradle/wrapper/gradle-wrapper.jar":
        failures.append("unreviewed vendored library")
    if any(part in {".private-edition", ".integrations-edition", ".private-sdk"} for part in relative.parts):
        failures.append("private edition content")
    if relative.suffix.lower() in {".kt", ".kts", ".java", ".gradle"}:
        if any(namespace in data for namespace in PROPRIETARY_NAMESPACES):
            if edition != "integrations" or relative.as_posix() not in INTEGRATION_SOURCE_PATHS:
                failures.append("vendor integration source is outside the approved edition boundary")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", choices=("opensource", "integrations"), default="opensource")
    args = parser.parse_args()
    if args.edition == "integrations" and not all((ROOT / name).is_file() for name in (
        "licenses/ANDROID_SDK_EXCEPTION.txt", "docs/integrations.md"
    )):
        parser.error("Integration source publication requires the SDK exception and integration build documentation")
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    failures = []
    paths = [Path(p.decode("utf-8")) for p in tracked.split(b"\0") if p]
    for relative in paths:
        path = ROOT / relative
        if not path.is_file():
            continue
        if relative.suffix.lower() in PRIVATE_EXTENSIONS or relative.name in PRIVATE_NAMES or relative.name.startswith(".env") and relative.name != ".env.example":
            failures.append((relative, "private data or local artifact"))
        data = path.read_bytes()
        failures.extend((relative, label) for label in component_failures(relative, data, args.edition))
        verified_upstream_notice = relative.as_posix() in UPSTREAM_NOTICES and hashlib.sha256(data).hexdigest() == UPSTREAM_NOTICES[relative.as_posix()]
        for label, pattern in PATTERNS.items():
            matches = list(pattern.finditer(data))
            if label == "email address":
                # Copyright contact addresses in reviewed upstream notices must be retained.
                # Only exact, reviewed file contents qualify; other privacy checks still apply.
                if verified_upstream_notice:
                    continue
                # Raw font/image bytes can coincidentally resemble addresses.
                # Inspect actual asset metadata during the manual review.
                if b"\0" in data[:8192]:
                    continue
                matches = [match for match in matches if not (
                    relative.suffix == ".kt" and match.group().startswith(b"this@")
                    or match.group().split(b"@", 1)[1].lower() in {
                        b"example.com", b"example.org", b"example.net", b"example.invalid",
                    }
                )]
            if matches:
                failures.append((relative, label))
    for path, label in failures:
        print(f"FAIL: {path.as_posix()}: {label}")
    if failures:
        return 1
    print(f"Public source screen passed for {len(paths)} tracked files. Manual review still required.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
