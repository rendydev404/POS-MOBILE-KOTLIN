package com.sukashawarma.pos.data.bluetooth

import java.io.ByteArrayOutputStream

class ESCPosEncoder {
    private val buffer = ByteArrayOutputStream()

    fun init(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x40)) // ESC @
        return this
    }

    fun alignLeft(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x00))
        return this
    }

    fun alignCenter(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x01))
        return this
    }

    fun alignRight(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x02))
        return this
    }

    fun boldOn(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x45, 0x01))
        return this
    }

    fun boldOff(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1B, 0x45, 0x00))
        return this
    }

    fun doubleHeightOn(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double width & height
        return this
    }

    fun doubleHeightOff(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x00))
        return this
    }

    fun paperCut(): ESCPosEncoder {
        buffer.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00)) // GS V A 0
        return this
    }

    fun text(content: String): ESCPosEncoder {
        try {
            buffer.write(content.toByteArray(charset("GBK")))
        } catch (e: Exception) {
            buffer.write(content.toByteArray())
        }
        return this
    }

    fun formatTwoColumns(left: String, right: String, totalWidth: Int = 40): String {
        val maxLeftWidth = totalWidth - right.length - 1
        val safeLeft = if (left.length > maxLeftWidth) left.substring(0, maxLeftWidth) else left
        val spacesCount = totalWidth - safeLeft.length - right.length
        val spaces = " ".repeat(maxOf(1, spacesCount))
        return "$safeLeft$spaces$right"
    }

    fun getReceiptBytes(): ByteArray {
        return buffer.toByteArray()
    }
}
