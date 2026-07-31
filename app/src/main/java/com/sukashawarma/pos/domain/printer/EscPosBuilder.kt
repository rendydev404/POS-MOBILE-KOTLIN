package com.sukashawarma.pos.domain.printer

import java.nio.charset.Charset

class EscPosBuilder {
    private val buffer = mutableListOf<Byte>()
    private val charset = Charset.forName("CP437") // Default for many thermal printers

    fun init(): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x40.toByte()))
        return this
    }

    fun alignLeft(): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x61.toByte(), 0x00.toByte()))
        return this
    }

    fun alignCenter(): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x61.toByte(), 0x01.toByte()))
        return this
    }

    fun alignRight(): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x61.toByte(), 0x02.toByte()))
        return this
    }

    fun bold(enable: Boolean): EscPosBuilder {
        val arg: Byte = if (enable) 0x01 else 0x00
        buffer.addAll(arrayOf(0x1B.toByte(), 0x45.toByte(), arg))
        return this
    }

    fun size(width: Int, height: Int): EscPosBuilder {
        // widths and heights 1-8 mapped to 0-7
        val w = (width - 1).coerceIn(0, 7)
        val h = (height - 1).coerceIn(0, 7)
        val arg = ((w shl 4) or h).toByte()
        buffer.addAll(arrayOf(0x1D.toByte(), 0x21.toByte(), arg))
        return this
    }

    fun text(txt: String): EscPosBuilder {
        buffer.addAll(txt.toByteArray(charset).toList())
        return this
    }

    fun textLine(txt: String): EscPosBuilder {
        text(txt)
        newline()
        return this
    }

    fun newline(): EscPosBuilder {
        buffer.add(0x0A.toByte())
        return this
    }

    fun feed(lines: Int): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x64.toByte(), lines.toByte()))
        return this
    }

    fun row(left: String, right: String, separator: String, width: Int): EscPosBuilder {
        if (right.isEmpty()) {
            return textLine(left)
        }
        val maxLeft = width - right.length - separator.length
        val safeLeft = if (left.length > maxLeft) left.substring(0, maxLeft) else left.padEnd(maxLeft, ' ')
        return textLine("$safeLeft$separator$right")
    }

    fun lineSpacing(spacing: Int): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x33.toByte(), spacing.toByte()))
        return this
    }

    fun defaultLineSpacing(): EscPosBuilder {
        buffer.addAll(arrayOf(0x1B.toByte(), 0x32.toByte()))
        return this
    }

    fun cut(): EscPosBuilder {
        // Full cut: GS V 0
        buffer.addAll(arrayOf(0x1D.toByte(), 0x56.toByte(), 0x00.toByte()))
        return this
    }
    
    fun separator(charWidth: Int = 32): EscPosBuilder {
        return textLine("-".repeat(charWidth))
    }

    fun bitmap(bitmap: android.graphics.Bitmap): EscPosBuilder {
        val width = bitmap.width
        val height = bitmap.height
        
        val widthBytes = (width + 7) / 8
        val xL = widthBytes and 0xFF
        val xH = (widthBytes shr 8) and 0xFF
        val yL = height and 0xFF
        val yH = (height shr 8) and 0xFF
        
        // Command GS v 0 0 xL xH yL yH
        buffer.addAll(arrayOf(0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte()))
        buffer.add(xL.toByte())
        buffer.add(xH.toByte())
        buffer.add(yL.toByte())
        buffer.add(yH.toByte())
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var byteValue = 0
                for (b in 0..7) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val blue = pixel and 0xFF
                        val alpha = (pixel shr 24) and 0xFF
                        
                        // Treat transparent as white
                        if (alpha > 128) {
                            val brightness = (r + g + blue) / 3
                            // Threshold: if darker than 128, set bit to 1 (black)
                            if (brightness < 128) {
                                byteValue = byteValue or (1 shl (7 - b))
                            }
                        }
                    }
                }
                buffer.add(byteValue.toByte())
            }
        }
        return this
    }

    fun getBytes(): ByteArray {
        return buffer.toByteArray()
    }
}
