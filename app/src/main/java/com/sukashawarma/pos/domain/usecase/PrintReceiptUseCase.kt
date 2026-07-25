package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.data.bluetooth.ESCPosEncoder
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.PaymentMethod
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintReceiptUseCase {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun generateCustomerReceiptBytes(
        order: Order,
        outletName: String,
        cashierName: String
    ): ByteArray {
        val encoder = ESCPosEncoder()
        encoder.init()

        // Header
        encoder.alignCenter()
        encoder.boldOn()
        encoder.text("SUKA SHAWARMA\n")
        encoder.boldOff()
        encoder.text("Cabang $outletName\n")
        encoder.text("========================================\n")

        // Metadata
        encoder.alignLeft()
        val dateStr = dateFormat.format(Date(order.createdAt))
        val paymentStr = when (order.paymentMethod) {
            PaymentMethod.CASH -> "TUNAI"
            PaymentMethod.QRIS -> "QRIS"
            PaymentMethod.CARD -> "CARD/EDC"
        }
        encoder.text(encoder.formatTwoColumns(dateStr, paymentStr, 40) + "\n")
        encoder.text("Pelanggan: ${order.customerName}\n")
        encoder.text("Kasir: $cashierName\n")
        encoder.text("----------------------------------------\n")

        // Double Height Nomor Antrean
        encoder.alignCenter()
        encoder.doubleHeightOn()
        encoder.text("No. ${order.orderNumber}\n")
        encoder.doubleHeightOff()
        encoder.text("----------------------------------------\n")

        // Item List
        encoder.alignLeft()
        for (item in order.items) {
            val itemLine = "${item.quantity}x ${item.name}"
            val priceStr = formatRupiah(item.subtotal)
            encoder.text(encoder.formatTwoColumns(itemLine, priceStr, 40) + "\n")
            if (item.note.isNotBlank()) {
                encoder.text("   - ${item.note}\n")
            }
        }
        encoder.text("----------------------------------------\n")

        // Totals
        encoder.text(encoder.formatTwoColumns("Subtotal", formatRupiah(order.subtotal), 40) + "\n")
        if (order.discountAmount > 0) {
            encoder.text(encoder.formatTwoColumns("Diskon", "-${formatRupiah(order.discountAmount)}", 40) + "\n")
        }
        encoder.boldOn()
        encoder.text(encoder.formatTwoColumns("TOTAL", formatRupiah(order.totalAmount), 40) + "\n")
        encoder.boldOff()

        if (order.paymentMethod == PaymentMethod.CASH) {
            encoder.text(encoder.formatTwoColumns("Tunai", formatRupiah(order.amountReceived), 40) + "\n")
            encoder.text(encoder.formatTwoColumns("Kembalian", formatRupiah(order.changeAmount), 40) + "\n")
        }
        encoder.text("----------------------------------------\n")

        // Footer
        encoder.alignCenter()
        encoder.text("Terima kasih & selamat menikmati!\n\n\n")

        // Paper Cut
        encoder.paperCut()

        return encoder.getReceiptBytes()
    }

    fun generateKitchenReceiptBytes(order: Order): ByteArray {
        val encoder = ESCPosEncoder()
        encoder.init()

        // Header
        encoder.alignCenter()
        encoder.boldOn()
        encoder.text("STRUK DAPUR\n")
        encoder.boldOff()
        encoder.text("----------------------------------------\n")

        // Metadata
        encoder.alignLeft()
        val dateStr = dateFormat.format(Date(order.createdAt))
        encoder.text("$dateStr\n")
        encoder.text("Pelanggan: ${order.customerName}\n")
        encoder.text("----------------------------------------\n")

        // Double Height Nomor Antrean
        encoder.alignCenter()
        encoder.doubleHeightOn()
        encoder.text("No. ${order.orderNumber}\n")
        encoder.doubleHeightOff()
        encoder.text("----------------------------------------\n")

        // Items
        encoder.alignLeft()
        for (item in order.items) {
            encoder.boldOn()
            encoder.text("${item.quantity}x ${item.name}\n")
            encoder.boldOff()
            if (item.note.isNotBlank()) {
                encoder.text("   - ${item.note}\n")
            }
        }
        encoder.text("========================================\n\n\n")

        // Paper Cut
        encoder.paperCut()

        return encoder.getReceiptBytes()
    }

    private fun formatRupiah(amount: Double): String {
        return "Rp ${String.format("%,.0f", amount).replace(',', '.')}"
    }
}
