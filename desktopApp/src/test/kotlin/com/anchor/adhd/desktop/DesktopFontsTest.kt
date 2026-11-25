package com.anchor.adhd.desktop

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopFontsTest {
    @Test
    fun newsreaderAndAtkinsonFontResourcesExist() {
        val loader = DesktopFontsTest::class.java.classLoader
        val required =
            listOf(
                "font/newsreader_medium.ttf",
                "font/newsreader_semibold.ttf",
                "font/atkinson_hyperlegible_regular.ttf",
                "font/atkinson_hyperlegible_bold.ttf",
                "OFL-1.1.txt",
            )
        required.forEach { path ->
            val stream = loader.getResourceAsStream(path)
            assertNotNull("Missing bundled resource: $path", stream)
            val size = stream?.use { it.readBytes().size } ?: 0
            assertTrue("Empty resource: $path", size > 100)
        }
    }
}
