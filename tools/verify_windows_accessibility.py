"""Check Access Bridge contents in a built Windows application image.

This checks the packaged runtime, not screen-reader interaction or user settings.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]


def verify(runtime, jimage):
    release = (runtime / "release").read_text(encoding="utf-8")
    modules = re.search(r'^MODULES="([^"]*)"$', release, re.MULTILINE)
    if not modules or "jdk.accessibility" not in modules.group(1).split():
        raise ValueError("Packaged runtime is missing jdk.accessibility")

    # Check actual runtime-image contents as well as its release manifest.
    listing = subprocess.run(
        [str(jimage), "list", str(runtime / "lib/modules")],
        check=True, capture_output=True, text=True, timeout=60,
    ).stdout
    module_contents = re.search(
        r"^Module: jdk\.accessibility\s*\n(.*?)(?=^Module: |\Z)",
        listing, re.MULTILINE | re.DOTALL,
    )
    required_class = "com/sun/java/accessibility/internal/AccessBridge.class"
    if not module_contents or required_class not in module_contents.group(1).split():
        raise ValueError("Access Bridge class is missing from the packaged runtime image")

    libraries = {}
    for name in ("javaaccessbridge.dll", "windowsaccessbridge-64.dll"):
        data = (runtime / "bin" / name).read_bytes()
        # Anchor's Windows packages currently target x64; reject wrong native binaries.
        if len(data) < 64 or data[:2] != b"MZ":
            raise ValueError(f"Invalid native Access Bridge library: {name}")
        pe = int.from_bytes(data[60:64], "little")
        if data[pe:pe + 4] != b"PE\0\0" or data[pe + 4:pe + 6] != b"\x64\x86":
            raise ValueError(f"Access Bridge library is not Windows x64: {name}")
        libraries[name] = len(data)
    return {"runtime": str(runtime.resolve()), "module": "jdk.accessibility", "libraries": libraries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "app", nargs="?", type=Path,
        default=ROOT / "desktopApp/build/compose/binaries/main/app/Anchor",
    )
    parser.add_argument("--jdk", type=Path, help="JDK containing the jimage tool (defaults to JAVA_HOME or PATH)")
    args = parser.parse_args()
    jdk = args.jdk or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    jimage = jdk / "bin/jimage.exe" if jdk else shutil.which("jimage")
    if not jimage or not Path(jimage).is_file():
        parser.error("Set JAVA_HOME or pass --jdk to a JDK containing jimage")
    print(json.dumps(verify(args.app / "runtime", jimage), indent=2))


if __name__ == "__main__":
    main()
