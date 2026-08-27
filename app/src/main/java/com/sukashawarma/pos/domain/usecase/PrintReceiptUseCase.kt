package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.PaymentMethod
import com.sukashawarma.pos.domain.printer.EscPosBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintReceiptUseCase {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun generateCustomerReceiptBytes(
        context: android.content.Context,
        order: Order,
        outletName: String,
        cashierName: String,
        layout: com.sukashawarma.pos.data.remote.dto.CustomerLayoutDto? = null
    ): ByteArray {
        val builder = EscPosBuilder().init()
        
        val paperWidth = layout?.paperWidth ?: 58
        val charWidth = if (paperWidth == 80) 48 else 32
        val showLogo = layout?.showLogo ?: true
        val headerText = if (layout?.headerText.isNullOrBlank()) "SUKA SHAWARMA" else layout!!.headerText
        val footerText = if (layout?.footerText.isNullOrBlank()) "Terima Kasih!\nSilakan datang kembali" else layout!!.footerText
        val showCashier = layout?.showCashier ?: true
        val isBigFont = layout?.fontScale == "besar"
        val showItemNotes = layout?.showItemNotes ?: true

        // Print Logo
        if (showLogo) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, com.sukashawarma.pos.R.mipmap.ic_launcher)
                val targetWidth = if (paperWidth == 80) 170 else 140
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    targetWidth, 
                    (targetWidth * bitmap.height) / bitmap.width,
                    true
                )
                builder.alignCenter().bitmap(scaledBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Header
        builder.alignCenter().bold(true)
        if (isBigFont) builder.size(1, 2) else builder.size(2, 2)
        builder.textLine(headerText)
            .bold(false).size(1, 1).newline()
            
        if (layout?.headerText.isNullOrBlank()) {
            builder.textLine("Cabang $outletName")
                .textLine("Timur Tengah dalam Setiap Gigitan")
        }
        builder.separator(charWidth)

        // Metadata
        builder.alignLeft()
            .textLine("Waktu    : ${dateFormat.format(Date(order.createdAt))}")
            .textLine("Pelanggan: ${order.customerName}")
        
        if (showCashier) {
            builder.textLine("Kasir    : $cashierName")
        }
        
        val paymentStr = when (order.paymentMethod) {
            PaymentMethod.CASH -> "TUNAI"
            PaymentMethod.QRIS -> "QRIS"
            PaymentMethod.CARD -> "CARD/EDC"
            PaymentMethod.VA -> "VIRTUAL ACCOUNT"
        }
        builder.textLine("Metode   : $paymentStr")
            .separator(charWidth)

        // Double Height Nomor Antrean
        val queueNumberLabel = if (order.isOffline) "SEMENTARA ${order.orderNumber}" else "No. ${order.orderNumber}"
        builder.alignCenter()
            .size(1, 2).bold(true).textLine(queueNumberLabel)
            .size(1, 1).bold(false)
        if (order.isOffline) {
            builder.textLine("Belum mendapat nomor server")
        }
        builder
            .separator(charWidth)

        // Items
        builder.alignLeft()
        for (item in order.items) {
            buildItem(builder, item, charWidth, isBigFont, true, showItemNotes)
        }
        builder.separator(charWidth)

        // Totals
        builder.alignRight()
        builder.textLine("Subtotal: ${formatRupiah(order.subtotal)}")
        if (order.discountAmount > 0) {
            builder.textLine("Diskon: -${formatRupiah(order.discountAmount)}")
        }
        
        builder.bold(true).size(1, 2)
            .textLine("TOTAL: ${formatRupiah(order.totalAmount)}")
            .bold(false).size(1, 1)

        if (order.paymentMethod == PaymentMethod.CASH) {
            builder.textLine("Tunai: ${formatRupiah(order.amountReceived)}")
            builder.textLine("Kembali: ${formatRupiah(order.changeAmount)}")
        }
        builder.newline()
        
        // Footer
        builder.alignCenter()
        val footerLines = footerText.split("\n")
        for (line in footerLines) {
            builder.textLine(line)
        }
        builder.feed(4).cut()

        return builder.getBytes()
    }

    fun generateKitchenReceiptBytes(
        order: Order,
        outletName: String,
        cashierName: String,
        layout: com.sukashawarma.pos.data.remote.dto.KitchenLayoutDto? = null
    ): ByteArray {
        val builder = EscPosBuilder().init()

        val paperWidth = layout?.paperWidth ?: 58
        val charWidth = if (paperWidth == 80) 48 else 32
        val headerText = if (layout?.headerText.isNullOrBlank()) "STRUK DAPUR" else layout!!.headerText
        val showCustomer = layout?.showCustomer ?: true
        val isBigFont = true // Kitchen receipt items are always large

        // Header
        builder.alignCenter()
            .bold(true).size(1, 2).textLine(headerText)
            .bold(false).size(1, 1).newline()
            .separator(charWidth)

        // Metadata
        builder.alignLeft()
            .textLine(dateFormat.format(Date(order.createdAt)))
        
        if (showCustomer) {
            builder.textLine("Pelanggan: ${order.customerName}")
        }

        // Double Height Nomor Antrean
        val queueNumberLabel = if (order.isOffline) "SEMENTARA ${order.orderNumber}" else "No. ${order.orderNumber}"
        builder.alignCenter().newline()
            .size(1, 2).bold(true).textLine(queueNumberLabel)
            .size(1, 1).bold(false).newline()
        if (order.isOffline) {
            builder.textLine("BELUM MENDAPAT NOMOR SERVER")
        }
        builder
            .separator(charWidth)
            .alignLeft()

        // Items
        for (item in order.items) {
            buildItem(builder, item, charWidth, isBigFont, false, true)
        }
        builder.newline().newline()
        builder.alignCenter().textLine("--- BATAS POTONG ---")
        builder.feed(4).cut()

        return builder.getBytes()
    }

    private fun buildItem(
        builder: EscPosBuilder, 
        item: com.sukashawarma.pos.domain.model.OrderItem, 
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
        
        if (showItemNotes && item.note.isNotBlank()) {
            val notePrefix = if (item.isChild) "   \u2502  \u2514\u2500 " else "   \u2514\u2500 "
            builder.textLine("$notePrefix${item.note}")
        }
    }

    private fun formatRupiah(amount: Double): String {
        return currencyFormat.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    private fun padRight(str: String, n: Int): String {
        return if (str.length > n) str.substring(0, n) else str.padEnd(n, ' ')
    }

    private fun padLeft(str: String, n: Int): String {
        return if (str.length > n) str.substring(0, n) else str.padStart(n, ' ')
    }
}
