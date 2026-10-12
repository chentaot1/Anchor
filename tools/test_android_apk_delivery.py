"""Exercise the publication gate independently of local model staging."""
from pathlib import Path
import tempfile
import unittest
import zipfile
import struct
from verify_android_apk import verify_apk


class ApkDeliveryTest(unittest.TestCase):
    @staticmethod
    def native_fixture(alignment=16384, relro_end=16384, load_memory=16384):
        data = bytearray(176)
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 2)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 6, 0, 0, 0, 176, load_memory, alignment)
        struct.pack_into("<IIQQQQQQ", data, 120, 0x6474E552, 4, 0, 0, 0, 0, relro_end, 1)
        return bytes(data)

    def check_archive(self, weights=None, runtime=None, orphan_data=False):
        scratch = Path(__file__).resolve().parents[1] / "build"
        scratch.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as temp:
            path = Path(temp) / "app.apk"
            with zipfile.ZipFile(path, "w") as apk:
                apk.writestr("lib/arm64-v8a/libanchor_llama.so", self.native_fixture() if runtime is None else runtime)
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

    def test_four_kb_load_segments_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "LOAD"):
            self.check_archive(runtime=self.native_fixture(alignment=4096))

    def test_four_kb_relro_padding_is_rejected_even_with_aligned_loads(self):
        with self.assertRaisesRegex(ValueError, "RELRO"):
            self.check_archive(runtime=self.native_fixture(relro_end=4096))

    def test_small_library_with_no_writable_data_in_relro_padding_is_accepted(self):
        self.check_archive(runtime=self.native_fixture(relro_end=4096, load_memory=4096))
