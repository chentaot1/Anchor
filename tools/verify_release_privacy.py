"""Screen application payloads for private paths and accidentally bundled user data.

Use alongside verify_public_source.py, model/signature checks, and a manual review.
An optional ignored pattern file can supply private identifiers without publishing them.
"""

import argparse
import re
import zipfile
from pathlib import Path

HOME_PATH = rb"(?:[A-Za-z]:[\\/]+Users[\\/]+|/(?:Users|home)/)[A-Za-z0-9_. -]+"
PRIVATE_SUFFIXES = {".db", ".sqlite", ".sqlite3", ".preferences_pb", ".keystore", ".jks", ".pdf", ".docx", ".bundle", ".hprof"}
PRIVATE_NAMES = {"local.properties", "keystore.properties", "google-services.json"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--desktop-image", type=Path)
    parser.add_argument("--patterns", type=Path, help="Ignored UTF-8 file with additional private regular expressions")
    args = parser.parse_args()
    if not args.apk and not args.desktop_image:
        parser.error("Specify an APK or desktop application image")
    patterns = [re.compile(HOME_PATH, re.IGNORECASE)]
    if args.patterns:
        patterns.extend(re.compile(line.encode(), re.IGNORECASE) for line in args.patterns.read_text(encoding="utf-8-sig").splitlines() if line.strip())
    failures = []
    scanned = 0

    def check_name(name):
        path = Path(name)
        if path.suffix.lower() in PRIVATE_SUFFIXES or path.name in PRIVATE_NAMES or path.name.startswith(".env"):
            failures.append((name, "private data file"))

    def scan_stream(name, stream):
        nonlocal scanned
        scanned += 1
        tail = b""
        while chunk := stream.read(1024 * 1024):
            data = tail + chunk
            # Inspect both ordinary strings and UTF-16 strings in native metadata.
            if any(pattern.search(data) or pattern.search(data.replace(b"\0", b"")) for pattern in patterns):
                failures.append((name, "private path or supplied identifier"))
                return
            tail = data[-1024:]

    def scan_archive(path):
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                check_name(entry.filename)
                # Upstream model weights are independently checksum-verified, not user data.
                if entry.filename.endswith(".gguf"):
                    continue
                with archive.open(entry) as stream:
                    scan_stream(entry.filename, stream)

    if args.apk:
        scan_archive(args.apk)
    if args.desktop_image:
        jars = []
        for path in args.desktop_image.rglob("*"):
            if not path.is_file():
                continue
            name = path.relative_to(args.desktop_image).as_posix()
            check_name(name)
            if path.suffix == ".jar":
                if path.name.startswith(("desktopApp-", "shared-desktop-")):
                    jars.append(path)
            elif path.suffix == ".cfg" or name == "Anchor.exe":
                with path.open("rb") as stream:
                    scan_stream(name, stream)
        if len(jars) != 2:
            raise ValueError("Expected the application and shared source JARs")
        for path in jars:
            scan_archive(path)
    if failures:
        for name, reason in failures:
            print(f"FAIL: {name}: {reason}")
        raise SystemExit(1)
    print(f"Release payload screen passed for {scanned} entries. Signature, model and manual metadata checks remain separate.")


if __name__ == "__main__":
    main()
