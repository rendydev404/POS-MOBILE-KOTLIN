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
        order: Order,
        outletName: String,
        cashierName: String
    ): ByteArray {
        val builder = EscPosBuilder().init()

        // Header
        builder.alignCenter()
            .bold(true).size(2, 2).textLine("SUKA SHAWARMA")
            .bold(false).size(1, 1).newline()
            .textLine("Cabang $outletName")
            .textLine("Timur Tengah dalam Setiap Gigitan")
            .separator()

        // Metadata
        builder.alignLeft()
            .textLine("Waktu    : ${dateFormat.format(Date(order.createdAt))}")
            .textLine("Pelanggan: ${order.customerName}")
            .textLine("Kasir    : $cashierName")
        
        val paymentStr = when (order.paymentMethod) {
            PaymentMethod.CASH -> "TUNAI"
            PaymentMethod.QRIS -> "QRIS"
            PaymentMethod.CARD -> "CARD/EDC"
            PaymentMethod.VA -> "VIRTUAL ACCOUNT"
        }
        builder.textLine("Metode   : $paymentStr")
            .separator()

        // Double Height Nomor Antrean
        builder.alignCenter()
            .size(1, 2).bold(true).textLine("No. ${order.orderNumber}")
            .size(1, 1).bold(false)
            .separator()

        // Items (32 chars for 58mm printer)
        builder.alignLeft()
        for (item in order.items) {
            val qtyStr = "${item.quantity}x"
            val priceStr = formatRupiah(item.subtotal)
            val nameSpace = 32 - qtyStr.length - priceStr.length - 1
            val safeName = padRight(item.name, nameSpace)
            
            val line = "$qtyStr $safeName${padLeft(priceStr, priceStr.length)}"
            builder.textLine(line)
            
            if (item.note.isNotBlank()) {
                builder.textLine("   Note: ${item.note}")
            }
        }
        builder.separator()

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
            .textLine("Terima Kasih!")
            .textLine("Silakan datang kembali")
            .feed(4).cut()

        return builder.getBytes()
    }

    fun generateKitchenReceiptBytes(order: Order): ByteArray {
        val builder = EscPosBuilder().init()

        // Header
        builder.alignCenter()
            .bold(true).size(2, 2).textLine("STRUK DAPUR")
            .bold(false).size(1, 1).newline()
            .separator()

        // Metadata
        builder.alignLeft()
            .textLine(dateFormat.format(Date(order.createdAt)))
            .textLine("Pelanggan: ${order.customerName}")
            .separator()

        // Double Height Nomor Antrean
        builder.alignCenter()
            .size(1, 2).bold(true).textLine("No. ${order.orderNumber}")
            .size(1, 1).bold(false)
            .separator()

        // Items
        builder.alignLeft()
        for (item in order.items) {
            builder.bold(true).size(2, 2)
                .textLine("${item.quantity}x ${item.name}")
                .bold(false).size(1, 1)
            
            if (item.note.isNotBlank()) {
                builder.textLine("   - ${item.note}")
            }
        }
        builder.newline().newline()
        builder.alignCenter().textLine("--- BATAS POTONG ---")
        builder.feed(4).cut()

        return builder.getBytes()
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
