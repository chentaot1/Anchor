package com.anchor.adhd.ai

import java.io.File
import java.security.MessageDigest

/** Untrusted downloads become installable only after size, format and checksum validation. */
object ModelFileVerifier {
    fun isGguf(file: File): Boolean = file.isFile && file.length() >= 4 &&
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }

    fun verify(file: File, expectedBytes: Long, expectedSha256: String) {
        check(file.length() == expectedBytes && isGguf(file)) { "Model file is incomplete or is not GGUF" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == expectedSha256) { "Model checksum mismatch. Please retry the download." }
    }
}
