"""Verify the shipped APK contains the exact desktop weights and ARM64 runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
from fetch_desktop_model import FILENAME, SHA256, SIZE, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        info = apk.getinfo("assets/models/" + FILENAME)
        if info.file_size != SIZE or info.compress_type != zipfile.ZIP_STORED:
            raise ValueError("Missing, incomplete or compressed desktop model asset")
        digest = hashlib.sha256()
        with apk.open(info) as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != SHA256:
            raise ValueError("Packaged weights differ from desktop weights")
        native = apk.getinfo("lib/arm64-v8a/libanchor_llama.so")
        if not native.file_size:
            raise ValueError("ARM64 inference runtime is empty")
        print(json.dumps({"apk": str(args.apk.resolve()), "apk_bytes": args.apk.stat().st_size,
                          "model": FILENAME, "model_bytes": info.file_size,
                          "model_sha256": digest.hexdigest(), "model_compression": "stored",
                          "arm64_runtime_bytes": native.file_size}, indent=2))


if __name__ == "__main__":
    main()
