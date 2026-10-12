package com.anchor.adhd.desktop.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

/** One scalable anchor glyph for tray, native windows, and the generated Windows package icon. */
internal object DesktopAppIcon {
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val painter by lazy { BitmapPainter(image(256).toComposeImageBitmap()) }

    fun image(size: Int): BufferedImage {
        require(size in 16..1024)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.scale(size / 32.0, size / 32.0)
            graphics.color = Color(18, 38, 56)
            graphics.fillRoundRect(0, 0, 32, 32, 10, 10)
            graphics.color = Color(133, 208, 229)
            graphics.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            graphics.drawOval(13, 4, 6, 6)
            graphics.drawLine(16, 10, 16, 26)
            graphics.drawLine(9, 14, 23, 14)
            graphics.drawArc(6, 9, 20, 18, 180, 180)
            graphics.drawLine(6, 18, 6, 22)
            graphics.drawLine(26, 18, 26, 22)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
