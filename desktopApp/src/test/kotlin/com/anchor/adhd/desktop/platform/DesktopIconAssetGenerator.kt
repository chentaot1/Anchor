package com.anchor.adhd.desktop.platform

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/** Test/development source only: generates a Windows ICO from the production vector drawing. */
object DesktopIconAssetGenerator {
    fun bytes(): ByteArray {
        val images = DesktopAppIcon.sizes.map { size ->
            ByteArrayOutputStream().use { output ->
                check(ImageIO.write(DesktopAppIcon.image(size), "png", output))
                output.toByteArray()
            }
        }
        val headerSize = 6 + 16 * images.size
        val buffer = ByteBuffer.allocate(headerSize + images.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = headerSize
        DesktopAppIcon.sizes.zip(images).forEach { (size, png) ->
            buffer.put((if (size == 256) 0 else size).toByte())
            buffer.put((if (size == 256) 0 else size).toByte())
            buffer.put(0).put(0).putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        images.forEach { buffer.put(it) }
        return buffer.array()
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val icon = File(args[0])
        icon.parentFile.mkdirs()
        icon.writeBytes(bytes())
        val preview = File(args[1])
        preview.parentFile.mkdirs()
        check(ImageIO.write(DesktopAppIcon.image(256), "png", preview))
        println("Generated ${icon.absolutePath} and ${preview.absolutePath}")
    }
}
