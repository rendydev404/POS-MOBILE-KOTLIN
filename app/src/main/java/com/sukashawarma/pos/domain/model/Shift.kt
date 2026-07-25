package com.sukashawarma.pos.domain.model

data class Shift(
    val id: String = java.util.UUID.randomUUID().toString(),
    val outletId: String,
    val cashierName: String,
    val initialCash: Double, // Modal Awal Laci
    val totalCashSales: Double = 0.0,
    val totalQrisSales: Double = 0.0,
    val totalCardSales: Double = 0.0,
    val totalPettyCashOut: Double = 0.0,
    val expectedCash: Double = initialCash + totalCashSales - totalPettyCashOut,
    val actualCash: Double? = null, // Uang Fisik Hasil Hitung
    val difference: Double? = null, // Selisih
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isOpen: Boolean = true
)

data class PettyCash(
    val id: String = java.util.UUID.randomUUID().toString(),
    val shiftId: String,
    val outletId: String,
    val amount: Double,
    val category: String,
    val notes: String,
    val receiptImageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
