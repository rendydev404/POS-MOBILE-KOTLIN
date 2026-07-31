package com.sukashawarma.pos.presentation.printer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.presentation.order_manual.OrderSuccessInfo
import com.sukashawarma.pos.domain.printer.EscPosBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptGenerator {
    private val gson = Gson()
    private val currencyFormat = NumberFormat.getNumberInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    fun generateCustomerReceipt(context: android.content.Context, info: OrderSuccessInfo, cashierName: String): ByteArray {
        val entity = info.orderEntity
        val builder = EscPosBuilder()
        val width = 32 // Assuming 58mm printer

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(entity.createdAt))

        builder.init()
        
        try {
            var bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, com.sukashawarma.pos.R.mipmap.ic_launcher)
            if (bitmap != null) {
                val targetWidth = 140
                val ratio = targetWidth.toFloat() / bitmap.width
                val targetHeight = (bitmap.height * ratio).toInt()
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                
                builder.alignCenter()
                builder.bitmap(scaledBitmap)
                builder.newline()
                
                if (bitmap != scaledBitmap) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        builder.alignCenter().bold(true)
        builder.size(2, 2).textLine("SUKA SHAWARMA").size(1, 1).newline()
        builder.textLine("Cabang ${info.outletName}")
        builder.textLine("Timur Tengah dalam Setiap Gigitan")
        
        builder.alignLeft().textLine("-".repeat(width))
        
        val methodStr = if (entity.paymentMethod.equals("CASH", true)) "TUNAI" else "QRIS"
        builder.textLine(formatTwoColumns("Waktu    : $dateStr", "", width))
        if (entity.customerName.isNotBlank()) {
            builder.textLine("Pelanggan: ${entity.customerName}")
        }
        builder.textLine("Kasir    : $cashierName")
        builder.textLine("Metode   : $methodStr")
        
        builder.alignCenter().newline().size(1, 2).textLine("No. ${entity.orderNumber}").size(1, 1).newline()
        builder.alignLeft().textLine("-".repeat(width))
        
        val listType = object : TypeToken<List<OrderItem>>() {}.type
        val items: List<OrderItem> = gson.fromJson(entity.itemsJson, listType)
        
        items.forEach { item ->
            buildItem(builder, item, width, false, true, true)
        }
        
        builder.textLine("-".repeat(width))
        
        builder.textLine(formatTwoColumns("Subtotal", formatRupiah(entity.subtotal), width))
        if (entity.discountAmount > 0) {
            builder.textLine(formatTwoColumns("Diskon", "-" + formatRupiah(entity.discountAmount), width))
        }
        builder.size(1, 2).bold(true).textLine(formatTwoColumns("TOTAL", formatRupiah(entity.totalAmount), width)).bold(false).size(1, 1)
        
        if (entity.paymentMethod.equals("CASH", true)) {
            builder.textLine(formatTwoColumns("Tunai", formatRupiah(entity.amountReceived), width))
            builder.textLine(formatTwoColumns("Kembalian", formatRupiah(entity.changeAmount), width))
        }
        
        builder.alignCenter().newline()
        builder.textLine("Terima Kasih!")
        builder.textLine("Silakan datang kembali")
        builder.feed(4).cut()
        
        return builder.getBytes()
    }

    fun generateKitchenReceipt(info: OrderSuccessInfo, cashierName: String): ByteArray {
        val entity = info.orderEntity
        val builder = EscPosBuilder()
        val width = 32

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(entity.createdAt))

        builder.init()
        builder.alignCenter().bold(true)
        builder.size(1, 2).textLine("STRUK DAPUR").size(1, 1).newline()
        
        builder.alignLeft().textLine("-".repeat(width))
        
        builder.textLine(dateStr)
        if (entity.customerName.isNotBlank()) {
            builder.textLine("Pelanggan: ${entity.customerName}")
        }
        
        builder.alignCenter().newline().size(1, 2).textLine("No. ${entity.orderNumber}").size(1, 1).newline()
        builder.alignLeft().textLine("-".repeat(width))
        
        val listType = object : TypeToken<List<OrderItem>>() {}.type
        val items: List<OrderItem> = gson.fromJson(entity.itemsJson, listType)
        
        items.forEach { item ->
            buildItem(builder, item, width, true, false, true)
        }
        
        builder.newline().newline()
        builder.alignCenter().textLine("--- BATAS POTONG ---")
        builder.feed(4).cut()
        
        return builder.getBytes()
    }

    private fun buildItem(
        builder: EscPosBuilder, 
        item: OrderItem, 
        charWidth: Int, 
        isBigFont: Boolean, 
        showPrice: Boolean,
        showItemNotes: Boolean
    ) {
        val qtyStr = if (item.isChild) "  ${item.quantity}x" else "${item.quantity}x"
        val priceStr = if (showPrice) formatRupiah(item.subtotal) else ""
        
        val cleanName = if (item.isChild && item.name.uppercase().startsWith("EXTRA")) {
            item.name.substring(5).trim()
        } else {
            item.name
        }

        val prefix = if (item.isChild) "\u251C\u2500 EXTRA " else ""
        val namePart = prefix + cleanName
        
        val maxNameLen = if (priceStr.isEmpty()) {
            charWidth - qtyStr.length - 1
        } else {
            charWidth - qtyStr.length - priceStr.length - 2
        }

        val safeName = if (namePart.length > maxNameLen) namePart.substring(0, maxNameLen) else namePart.padEnd(maxNameLen, ' ')
        
        if (isBigFont) builder.size(1, 2).bold(true)
        if (priceStr.isEmpty()) {
            builder.textLine("$qtyStr $safeName")
        } else {
            builder.textLine("$qtyStr $safeName $priceStr")
        }
        if (isBigFont) builder.size(1, 1).bold(false)
        
        if (showItemNotes && !item.note.isNullOrBlank()) {
            val notePrefix = if (item.isChild) "   \u2502  \u2514\u2500 " else "   \u2514\u2500 "
            builder.textLine("$notePrefix${item.note}")
        }
    }

    private fun formatTwoColumns(left: String, right: String, totalWidth: Int): String {
        val maxLeftWidth = totalWidth - right.length - 1
        val safeLeft = if (left.length > maxLeftWidth) left.substring(0, maxLeftWidth) else left
        val spacesCount = totalWidth - safeLeft.length - right.length
        val spaces = " ".repeat(maxOf(1, spacesCount))
        return "$safeLeft$spaces$right"
    }

    private fun formatRupiah(amount: Double): String {
        return currencyFormat.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }
}
