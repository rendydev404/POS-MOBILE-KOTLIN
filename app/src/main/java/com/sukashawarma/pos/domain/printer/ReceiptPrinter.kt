package com.sukashawarma.pos.domain.printer

import com.sukashawarma.pos.domain.model.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object ReceiptPrinter {

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun padRight(str: String, n: Int): String {
        return if (str.length > n) str.substring(0, n) else str.padEnd(n, ' ')
    }

    private fun padLeft(str: String, n: Int): String {
        return if (str.length > n) str.substring(0, n) else str.padStart(n, ' ')
    }

    fun generateReceiptBytes(order: Order, isKitchen: Boolean, cashierName: String, outletName: String): ByteArray {
        val builder = EscPosBuilder().init()

        // Header
        builder.alignCenter()
            .bold(true).size(2, 2).textLine(if (isKitchen) "TIKET DAPUR" else "SUKA SHAWARMA")
            .bold(false).size(1, 1).newline()

        if (!isKitchen) {
            builder.textLine(outletName)
            builder.textLine("Timur Tengah dalam Setiap Gigitan")
            builder.newline()
        }

        builder.alignLeft()
            .textLine("No. Order: #${order.orderNumber}")
            .textLine("Pelanggan: ${order.customerName}")
            .textLine("Waktu    : ${formatDate(order.createdAt)}")
            .textLine("Kasir    : $cashierName")
        
        if (!isKitchen) {
            builder.textLine("Metode   : ${order.paymentMethod.name}")
        }
        
        builder.separator()

        // Items
        // 32 chars per line (Standard 58mm printer)
        // format: [Qty] [Name] [Total]
        for (item in order.items) {
            if (isKitchen) {
                // Kitchen receipt: larger font, no price
                builder.bold(true).size(2, 2)
                    .textLine("${item.quantity}x ${item.name}")
                    .bold(false).size(1, 1)
                
                if (item.note.isNotBlank()) {
                    builder.textLine("   Catatan: ${item.note}")
                }
            } else {
                // Customer receipt
                val qtyStr = "${item.quantity}x"
                val priceStr = formatCurrency(item.subtotal)
                // 32 - 4 (qty) - length(price) - 1 = available for name
                val nameSpace = 32 - 4 - priceStr.length - 1
                
                val safeName = if (item.name.length > nameSpace) item.name.substring(0, nameSpace) else padRight(item.name, nameSpace)
                val line = padRight(qtyStr, 4) + safeName + " " + padLeft(priceStr, priceStr.length)
                
                builder.textLine(line)
                
                if (item.note.isNotBlank()) {
                    builder.textLine("   Note: ${item.note}")
                }
            }
        }

        builder.separator()

        if (!isKitchen) {
            // Totals
            builder.alignRight()
            if (order.discountAmount > 0) {
                builder.textLine("Subtotal: ${formatCurrency(order.subtotal)}")
                builder.textLine("Diskon: -${formatCurrency(order.discountAmount)}")
            }
            
            builder.bold(true).size(1, 2)
                .textLine("TOTAL: ${formatCurrency(order.totalAmount)}")
                .bold(false).size(1, 1)

            builder.textLine("Bayar: ${formatCurrency(order.amountReceived)}")
            builder.textLine("Kembali: ${formatCurrency(order.changeAmount)}")
            
            builder.newline()
            builder.alignCenter()
                .textLine("Terima Kasih!")
                .textLine("Silakan datang kembali")
        } else {
            builder.newline()
            builder.newline()
            builder.alignCenter().textLine("--- BATAS POTONG ---")
        }

        builder.feed(4).cut()

        return builder.getBytes()
    }
}
