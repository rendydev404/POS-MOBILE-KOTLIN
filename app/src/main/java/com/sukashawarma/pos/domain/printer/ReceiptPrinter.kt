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

        builder.alignCenter()
            .newline()
            .bold(true).size(2, 2).textLine("No. ${order.orderNumber}")
            .bold(false).size(1, 1).newline()

        builder.alignLeft()
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
                if (item.isChild) {
                    val cleanName = if (item.name.uppercase().startsWith("EXTRA")) item.name.substring(5).trim() else item.name
                    builder.textLine("    |- EXTRA $cleanName")
                } else {
                    builder.bold(true).size(2, 2)
                        .textLine("${item.quantity}x ${item.name}")
                        .bold(false).size(1, 1)
                }
                
                if (item.note.isNotBlank()) {
                    builder.textLine("   Catatan: ${item.note}")
                }
            } else {
                // Customer receipt
                val priceStr = formatCurrency(item.subtotal)
                val lineStr = if (item.isChild) {
                    val cleanName = if (item.name.uppercase().startsWith("EXTRA")) item.name.substring(5).trim() else item.name
                    val prefix = "    |- EXTRA "
                    val nameSpace = 32 - prefix.length - priceStr.length - 1
                    val safeName = if (cleanName.length > nameSpace) cleanName.substring(0, maxOf(0, nameSpace)) else padRight(cleanName, maxOf(0, nameSpace))
                    prefix + safeName + " " + padLeft(priceStr, priceStr.length)
                } else {
                    val qtyStr = "${item.quantity}x"
                    val nameSpace = 32 - 4 - priceStr.length - 1
                    val safeName = if (item.name.length > nameSpace) item.name.substring(0, maxOf(0, nameSpace)) else padRight(item.name, maxOf(0, nameSpace))
                    padRight(qtyStr, 4) + safeName + " " + padLeft(priceStr, priceStr.length)
                }
                
                builder.textLine(lineStr)
                
                if (item.note.isNotBlank()) {
                    builder.textLine("  - ${item.note}")
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
