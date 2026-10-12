"""Exercise the publication gate independently of local model staging."""
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_android_apk import verify_apk


class ApkDeliveryTest(unittest.TestCase):
    def check_archive(self, weights=None, runtime=b"runtime", orphan_data=False):
        scratch = Path(__file__).resolve().parents[1] / "build"
        scratch.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as temp:
            path = Path(temp) / "app.apk"
            with zipfile.ZipFile(path, "w") as apk:
                apk.writestr("lib/arm64-v8a/libanchor_llama.so", runtime)
                if weights:
                    apk.writestr(weights, b"GGUFweights")
            if orphan_data:
                # A valid self-extracting ZIP still must not conceal old model bytes.
                path.write_bytes(b"\0" * (17 * 1024 * 1024) + path.read_bytes())
            return verify_apk(path)

    def test_model_free_apk_is_accepted(self):
        self.assertFalse(self.check_archive()["model_bundled"])

    def test_staged_weights_are_rejected_even_with_uppercase_extension(self):
        for name in ("assets/models/model.gguf", "assets/Model.GGUF", "assets/models/model.gguf.part"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.check_archive(weights=name)

    def test_missing_runtime_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check_archive(runtime=b"")

    def test_deleted_model_bytes_outside_zip_directory_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "unused ZIP data"):
            self.check_archive(orphan_data=True)
