"""Stage the exact desktop model as an APK asset (weights stay out of Git)."""
import argparse
import hashlib
from pathlib import Path
import shutil
import urllib.request

FILENAME = "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"
SIZE = 1_153_529_792
SHA256 = "8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c"
URL = "https://huggingface.co/GnLOLot/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-GGUF/resolve/main/" + FILENAME
ROOT = Path(__file__).resolve().parents[1]


def verify(path):
    if path.stat().st_size != SIZE:
        raise ValueError(f"Incorrect model size: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        if stream.read(4) != b"GGUF":
            raise ValueError("Model is not GGUF")
        stream.seek(0)
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != SHA256:
        raise ValueError("Model checksum does not match desktop weights")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, help="Copy verified local desktop weights instead of downloading")
    args = parser.parse_args()
    target = ROOT / "app/src/main/assets/models" / FILENAME
    if target.exists():
        try:
            verify(target)
            print(f"Verified existing asset: {target}")
            return
        except ValueError:
            pass
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".gguf.staging")
    try:
        if args.source:
            verify(args.source)
            shutil.copyfile(args.source, temporary)
        else:
            request = urllib.request.Request(URL, headers={"User-Agent": "Anchor-model-bundler/1"})
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
        verify(temporary)
        temporary.replace(target)
        print(f"Bundled desktop model: {target} ({SIZE} bytes, SHA256 {SHA256})")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
