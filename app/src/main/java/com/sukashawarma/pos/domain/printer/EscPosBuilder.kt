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
    
    fun separator(): EscPosBuilder {
        return textLine("--------------------------------")
    }

    fun getBytes(): ByteArray {
        return buffer.toByteArray()
    }
}
