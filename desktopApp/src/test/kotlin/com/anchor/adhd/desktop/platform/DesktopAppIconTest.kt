package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinUser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class DesktopAppIconTest {
    @get:Rule val folder = TemporaryFolder()
    private fun asset() = javaClass.getResourceAsStream("/anchor.ico")!!.use { it.readBytes() }

    @Test fun packagedIconHasEveryRuntimeSizeAndMatchesTheSharedDrawing() {
        val bytes = asset()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, header.short.toInt())
        assertEquals(1, header.short.toInt())
        assertEquals(DesktopAppIcon.sizes.size, header.short.toInt())
        DesktopAppIcon.sizes.forEach { size ->
            val width = header.get().toInt() and 255
            val height = header.get().toInt() and 255
            assertEquals(size, if (width == 0) 256 else width)
            assertEquals(size, if (height == 0) 256 else height)
            header.get(); header.get()
            assertEquals(1, header.short.toInt())
            assertEquals(32, header.short.toInt())
            val length = header.int
            val offset = header.int
            assertTrue(length > 0 && offset >= 6 + 16 * DesktopAppIcon.sizes.size && offset + length <= bytes.size)
            val decoded = ImageIO.read(ByteArrayInputStream(bytes, offset, length))
            assertNotNull(decoded)
            assertEquals(size, decoded.width)
            assertEquals(size, decoded.height)
            val expected = DesktopAppIcon.image(size)
            assertArrayEquals(expected.getRGB(0, 0, size, size, null, 0, size), decoded.getRGB(0, 0, size, size, null, 0, size))
            assertTrue("Rounded corners must retain transparency", (decoded.getRGB(0, 0) ushr 24) < 255)
        }
    }

    @Test fun windowsLoadsAllPackagedIconSizes() {
        if (!Platform.isWindows()) return
        val file = folder.newFile("Anchor.ico").apply { writeBytes(asset()) }
        DesktopAppIcon.sizes.forEach { size ->
            val handle = User32.INSTANCE.LoadImage(null, file.absolutePath, WinUser.IMAGE_ICON, size, size, WinUser.LR_LOADFROMFILE)
            assertNotNull("Windows must load the ${size}px icon", handle)
            assertTrue(User32.INSTANCE.DestroyIcon(HICON(handle.pointer)))
        }
    }
}
