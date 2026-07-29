package com.sukashawarma.pos.presentation.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class CrewBonusItem(
    val crewName: String,
    val role: String,
    val daysTargetAchieved: Int,
    val bonusAmountPerCrew: Double
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    val currentOutletId = MutableStateFlow("")
    val totalSalesToday = MutableStateFlow(0.0)
    val totalOrdersCount = MutableStateFlow(0)
    val cashSales = MutableStateFlow(0.0)
    val qrisSales = MutableStateFlow(0.0)
    val cardSales = MutableStateFlow(0.0)

    val dailyTargetAmount = MutableStateFlow(2000000.0)
    val dailyBonusPool = MutableStateFlow(150000.0)

    val crewBonusList = MutableStateFlow<List<CrewBonusItem>>(emptyList())
    val hourlySales = MutableStateFlow<Map<String, Double>>(emptyMap())
    val bestSellers = MutableStateFlow<List<Pair<String, Int>>>(emptyList())

    val isLoading = MutableStateFlow(false)

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
        loadRealReportData()
    }

    fun loadRealReportData() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // 1. Fetch real orders today from Supabase
                // Using select=*,order_items(*) to get order items for best seller
                val ordersRes = api.getOrders(mapOf("outlet_id" to "eq.${currentOutletId.value}"))
                if (ordersRes.isSuccessful && ordersRes.body() != null) {
                    val orders = ordersRes.body()!!
                    totalOrdersCount.value = orders.size
                    totalSalesToday.value = orders.sumOf { it.totalAmount }

                    cashSales.value = orders.filter { it.paymentMethod.equals("cash", ignoreCase = true) }.sumOf { it.totalAmount }
                    qrisSales.value = orders.filter { it.paymentMethod.equals("qris", ignoreCase = true) }.sumOf { it.totalAmount }
                    cardSales.value = orders.filter { it.paymentMethod.equals("card", ignoreCase = true) || it.paymentMethod.equals("edc", ignoreCase = true) }.sumOf { it.totalAmount }

                    // Hourly Sales
                    val hourly = mutableMapOf<String, Double>()
                    orders.forEach { order ->
                        // assuming createdAt is ISO string like "2023-10-25T14:30:00"
                        val hour = order.createdAt.substringAfter("T").take(2)
                        hourly[hour] = (hourly[hour] ?: 0.0) + order.totalAmount
                    }
                    hourlySales.value = hourly.toSortedMap()

                    // Best Sellers
                    val itemCounts = mutableMapOf<String, Int>()
                    orders.forEach { order ->
                        order.orderItems?.forEach { item ->
                            itemCounts[item.menuItemName] = (itemCounts[item.menuItemName] ?: 0) + item.quantity
                        }
                    }
                    bestSellers.value = itemCounts.toList().sortedByDescending { it.second }.take(5)
                }

                // Target omset harian real (RPC get_my_target_progress)
                val targetRes = api.getMyTargetProgress()
                if (targetRes.isSuccessful && !targetRes.body().isNullOrEmpty()) {
                    dailyTargetAmount.value = targetRes.body()!!.first().targetAmount
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }
    
    fun exportToPdf(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint()
                
                paint.textSize = 24f
                paint.isFakeBoldText = true
                canvas.drawText("Laporan Penjualan POS Kasir", 50f, 50f, paint)
                
                paint.textSize = 16f
                paint.isFakeBoldText = false
                canvas.drawText("Total Omset: Rp ${totalSalesToday.value}", 50f, 100f, paint)
                canvas.drawText("Total Order: ${totalOrdersCount.value}", 50f, 130f, paint)
                canvas.drawText("Tunai: Rp ${cashSales.value}", 50f, 160f, paint)
                canvas.drawText("QRIS: Rp ${qrisSales.value}", 50f, 190f, paint)
                canvas.drawText("Card: Rp ${cardSales.value}", 50f, 220f, paint)
                
                pdfDocument.finishPage(page)
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, "Laporan_POS_${System.currentTimeMillis()}.pdf")
                pdfDocument.writeTo(java.io.FileOutputStream(file))
                pdfDocument.close()
                
                android.widget.Toast.makeText(context, "PDF berhasil diexport ke Downloads", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Gagal export PDF", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
