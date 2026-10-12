"""Check reviewed dependencies, removed assets and legal texts in final release payloads."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = ("com.google.android.gms:", "com.samsung.android.sdk.health", "io.github.sceneview:", "com.google.android.filament:")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True, help="Resolved Gradle runtime inventory")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--desktop-image", type=Path, required=True)
    args = parser.parse_args()
    reviewed = {row["coordinate"] for row in json.loads((ROOT / "licenses/dependency-inventory.json").read_text())}
    inventory = json.loads(args.inventory.read_text())
    for row in inventory:
        for component in row["components"]:
            if any(value in component for value in FORBIDDEN):
                raise ValueError("Removed SDK returned in dependency graph: " + component)
        for artifact in row["artifacts"]:
            if artifact["coordinate"] not in reviewed:
                raise ValueError("Dependency requires license review: " + artifact["coordinate"])

    def payload(archive, prefix):
        for name in archive.namelist():
            lower = name.lower()
            if lower.endswith(".glb") or "grove_harbor" in lower or "grove_living_harbor" in lower:
                raise ValueError("Removed visual asset is packaged: " + name)
            if any(removed in lower for removed in ("samsunghealth", "googlesignin", "forestgallery", "plantcatalog", "plantspecies", "plantmodelview")):
                raise ValueError("Removed feature is packaged: " + name)
        for file in (ROOT / "licenses").iterdir():
            if file.is_file() and archive.read(prefix + file.name) != file.read_bytes():
                raise ValueError("Missing or stale packaged license: " + file.name)

    with zipfile.ZipFile(args.apk) as apk:
        payload(apk, "assets/")
        for entry in apk.infolist():
            if entry.filename.endswith(".dex"):
                data = apk.read(entry)
                for descriptor in (b"Lcom/samsung/android/sdk/health/data/", b"Lcom/google/android/gms/", b"Lio/github/sceneview/", b"Lcom/google/android/filament/", b"/ForestGalleryScreen", b"/PlantCatalog", b"/PlantSpecies"):
                    if descriptor in data:
                        raise ValueError("Removed SDK or feature found in DEX: " + descriptor.decode())
    jars = list((args.desktop_image / "app").glob("desktopApp-*.jar"))
    if len(jars) != 1:
        raise ValueError("Expected one desktop application JAR")
    with zipfile.ZipFile(jars[0]) as jar:
        payload(jar, "")
    shared = list((args.desktop_image / "app").glob("shared-desktop-*.jar"))
    if len(shared) != 1:
        raise ValueError("Expected one shared JAR")
    with zipfile.ZipFile(shared[0]) as jar:
        if any("PlantCatalog" in n or "PlantSpecies" in n for n in jar.namelist()):
            raise ValueError("Removed shared plant code is packaged")
    legal = args.desktop_image / "runtime/legal"
    if not (legal / "java.base/LICENSE").is_file() or not (legal / "java.base/ASSEMBLY_EXCEPTION").is_file():
        raise ValueError("Bundled OpenJDK legal material missing")
    print("Release component checks passed: reviewed dependency graph, no plant/forest payload, exact legal texts and OpenJDK notices.")


if __name__ == "__main__":
    main()
