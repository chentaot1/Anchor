"""Verify Android ships its ARM64 runtime without model weights."""
import argparse
import json
import struct
from pathlib import Path
import zipfile
ROOT = Path(__file__).resolve().parents[1]


def verify_native_pages(data, name):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError(f"Invalid 64-bit native runtime: {name}")
    offset = struct.unpack_from("<Q", data, 32)[0]
    stride, count = struct.unpack_from("<HH", data, 54)
    if stride < 56 or offset + stride * count > len(data):
        raise ValueError(f"Invalid ELF program headers: {name}")
    loads = 0
    headers = [struct.unpack_from("<IIQQQQQQ", data, offset + index * stride) for index in range(count)]
    for kind, _, file_offset, virtual, _, _, memory_size, alignment in headers:
        if kind == 1:
            loads += 1
            if alignment < 16384 or (virtual - file_offset) % 16384:
                raise ValueError(f"Native LOAD segment lacks 16 KB alignment: {name}")
        if kind == 0x6474E552:
            end = virtual + memory_size
            # Small upstream libraries can leave a safe gap at the page boundary.
            # Reject padding that would make actual mutable LOAD data read-only.
            guards = ((virtual // 16384 * 16384, virtual), (end, (end + 16383) // 16384 * 16384))
            for segment in headers:
                if segment[0] == 1 and segment[1] & 2:
                    start, stop = segment[3], segment[3] + segment[6]
                    if any(max(start, left) < min(stop, right) for left, right in guards):
                        raise ValueError(f"Native RELRO protection overlaps writable LOAD data at 16 KB: {name}")
    if not loads:
        raise ValueError(f"Native runtime has no LOAD segment: {name}")


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
        for name in apk.namelist():
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
                verify_native_pages(apk.read(name), name)
        return {"apk": str(path.resolve()), "apk_bytes": path.stat().st_size,
                "model_bundled": False, "native_page_size": 16384,
                "arm64_runtime_bytes": native.file_size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    print(json.dumps(verify_apk(args.apk), indent=2))


if __name__ == "__main__":
    main()
