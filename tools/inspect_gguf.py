"""Inspect model identity and tokenizer metadata without loading the weights."""
import json
import struct
import sys


def inspect(path):
    with open(path, "rb") as stream:
        def number(fmt):
            size = struct.calcsize(fmt)
            return struct.unpack(fmt, stream.read(size))[0]

        def string(keep=True):
            size = number("<Q")
            if keep:
                return stream.read(size).decode("utf-8")
            stream.seek(size, 1)

        def value(kind, keep):
            formats = {0: "<B", 1: "<b", 2: "<H", 3: "<h", 4: "<I", 5: "<i", 6: "<f", 7: "<?", 10: "<Q", 11: "<q", 12: "<d"}
            if kind == 8:
                return string(keep)
            if kind == 9:
                element = number("<I")
                count = number("<Q")
                if element in formats and not keep:
                    stream.seek(count * struct.calcsize(formats[element]), 1)
                    return
                result = []
                for _ in range(count):
                    item = value(element, keep)
                    if keep:
                        result.append(item)
                return result if keep else None
            return number(formats[kind])

        assert stream.read(4) == b"GGUF", "Invalid GGUF magic"
        version = number("<I")
        tensors = number("<Q")
        count = number("<Q")
        selected = {"general.architecture", "general.name", "general.file_type", "tokenizer.ggml.model", "tokenizer.ggml.pre", "tokenizer.chat_template", "tokenizer.ggml.bos_token_id", "tokenizer.ggml.eos_token_id", "tokenizer.ggml.add_bos_token", "llama.context_length"}
        result = {"path": path, "version": version, "tensors": tensors}
        for _ in range(count):
            key = string()
            kind = number("<I")
            item = value(kind, key in selected)
            if key in selected:
                result[key] = item
        return result


if __name__ == "__main__":
    print(json.dumps(inspect(sys.argv[1]), indent=2, ensure_ascii=False))
