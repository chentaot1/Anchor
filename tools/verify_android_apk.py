"""Verify Android ships its ARM64 runtime without model weights."""
import argparse
import json
from pathlib import Path
import zipfile
ROOT = Path(__file__).resolve().parents[1]


def verify_apk(path):
    with zipfile.ZipFile(path) as apk:
        # Incremental APK updates can leave deleted weights outside the ZIP directory.
        if path.stat().st_size > sum(info.compress_size for info in apk.infolist()) + 16 * 1024 * 1024:
            raise ValueError("APK contains excessive unused ZIP data; rebuild its packaging from scratch")
        weights = [name for name in apk.namelist() if name.lower().endswith(".gguf")
                   or name.lower().startswith("assets/models/")]
        if weights:
            raise ValueError("APK must not contain model weights: " + ", ".join(weights))
        native = apk.getinfo("lib/arm64-v8a/libanchor_llama.so")
        if not native.file_size:
            raise ValueError("ARM64 inference runtime is empty")
        return {"apk": str(path.resolve()), "apk_bytes": path.stat().st_size,
                "model_bundled": False, "arm64_runtime_bytes": native.file_size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    print(json.dumps(verify_apk(args.apk), indent=2))


if __name__ == "__main__":
    main()
