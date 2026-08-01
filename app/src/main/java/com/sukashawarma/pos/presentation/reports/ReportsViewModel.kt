package com.sukashawarma.pos.presentation.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.ShiftDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DateRange(val label: String) {
    TODAY("Hari Ini"),
    YESTERDAY("Kemarin"),
    LAST_7_DAYS("7 Hari Terakhir"),
    LAST_30_DAYS("30 Hari Terakhir"),
    ALL_TIME("Semua Waktu"),
    CUSTOM("Kustom Tanggal")
}

data class PaymentStats(val count: Int, val revenue: Double)
data class ItemStats(val qty: Int, val revenue: Double)

data class AnalyticsData(
    val totalRevenue: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val netRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val totalItemsSold: Int = 0,
    val pendingCount: Int = 0,
    val canceledCount: Int = 0,
    val paymentBreakdown: Map<String, PaymentStats> = emptyMap(),
    val hourly: List<Int> = List(24) { 0 },
    val dailyEntries: List<Pair<String, Double>> = emptyList(),
    val bestSellers: List<Pair<String, ItemStats>> = emptyList(),
    val avgOrderValue: Double = 0.0,
    val totalCashVariance: Double = 0.0,
    val peakHour: Int? = null,
    val orders: List<OrderDto> = emptyList(),
    val shifts: List<ShiftDto> = emptyList()
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    private val _currentOutletId = MutableStateFlow("")

    val selectedRange = MutableStateFlow(DateRange.TODAY)
    val customStartDate = MutableStateFlow("")
    val customEndDate = MutableStateFlow("")

    val channelFilter = MutableStateFlow("all")
    val paymentFilter = MutableStateFlow("all")
    val statusFilter = MutableStateFlow("all")
    val searchQuery = MutableStateFlow("")

    private val _analyticsData = MutableStateFlow(AnalyticsData())
    val analyticsData = _analyticsData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun setOutlet(outletId: String) {
        _currentOutletId.value = outletId
        loadRealReportData()
    }

    fun updateRange(range: DateRange) {
        selectedRange.value = range
        if (range != DateRange.CUSTOM) loadRealReportData()
    }

    fun updateCustomDate(start: String, end: String) {
        customStartDate.value = start
        customEndDate.value = end
        if (start.isNotEmpty() && end.isNotEmpty()) loadRealReportData()
    }

    fun updateFilters(channel: String, payment: String, status: String) {
        channelFilter.value = channel
        paymentFilter.value = payment
        statusFilter.value = status
        loadRealReportData()
    }
    
    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun loadRealReportData() {
        val outletId = _currentOutletId.value
        if (outletId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filters = mutableMapOf<String, String>("outlet_id" to "eq.$outletId")
                val shiftFilters = mutableMapOf<String, String>("outlet_id" to "eq.$outletId", "status" to "eq.closed")

                val now = LocalDateTime.now(ZoneId.of("Asia/Jakarta"))
                var pStart: LocalDateTime = now
                var pEnd: LocalDateTime = now
                var useDateFilter = true

                when (selectedRange.value) {
                    DateRange.TODAY -> {
                        pStart = now.toLocalDate().atStartOfDay()
                    }
                    DateRange.YESTERDAY -> {
                        pStart = now.minusDays(1).toLocalDate().atStartOfDay()
                        pEnd = now.toLocalDate().atStartOfDay()
                    }
                    DateRange.LAST_7_DAYS -> {
                        pStart = now.minusDays(7).toLocalDate().atStartOfDay()
                    }
                    DateRange.LAST_30_DAYS -> {
                        pStart = now.minusDays(30).toLocalDate().atStartOfDay()
                    }
                    DateRange.ALL_TIME -> {
                        useDateFilter = false
                    }
                    DateRange.CUSTOM -> {
                        try {
                            pStart = LocalDate.parse(customStartDate.value).atStartOfDay()
                            pEnd = LocalDate.parse(customEndDate.value).atTime(23, 59, 59)
                        } catch(e: Exception) {
                            useDateFilter = false
                        }
                    }
                }

                if (useDateFilter) {
                    val startIso = pStart.format(DateTimeFormatter.ISO_DATE_TIME)
                    if (selectedRange.value == DateRange.TODAY || selectedRange.value == DateRange.LAST_7_DAYS || selectedRange.value == DateRange.LAST_30_DAYS) {
                        filters["created_at"] = "gte.$startIso"
                        shiftFilters["end_time"] = "gte.$startIso"
                    } else {
                        val endIso = pEnd.format(DateTimeFormatter.ISO_DATE_TIME)
                        filters["and"] = "(created_at.gte.$startIso,created_at.lte.$endIso)"
                        shiftFilters["and"] = "(end_time.gte.$startIso,end_time.lte.$endIso)"
                    }
                }

                if (statusFilter.value != "all") {
                    filters["status"] = "eq.${statusFilter.value}"
                }
                if (paymentFilter.value != "all") {
                    filters["payment_method"] = "eq.${paymentFilter.value}"
                }
                if (channelFilter.value != "all") {
                    when (channelFilter.value) {
                        "offline" -> filters["channel"] = "is.null"
                        "food_apps" -> filters["channel"] = "in.(gofood,grabfood,shopeefood,tiktokgo,tiktok,tiktok_go)"
                        "tiktokgo", "tiktok" -> filters["channel"] = "in.(tiktokgo,tiktok,tiktok_go)"
                        else -> filters["channel"] = "eq.${channelFilter.value}"
                    }
                }

                val ordersRes = api.getOrders(filters)
                val shiftsRes = api.getShifts(shiftFilters)

                val orders = ordersRes.body() ?: emptyList()
                val shifts = shiftsRes.body() ?: emptyList()

                val completedOrders = orders.filter { it.status == "completed" }
                val totalRevenue = completedOrders.sumOf { it.totalAmount }

                val totalDeductions = completedOrders.sumOf { o ->
                    val disc = o.discountAmount ?: 0.0
                    val promo = o.promoSubsidy ?: 0.0
                    if (disc > 0 || promo > 0) {
                        disc + promo
                    } else {
                        val itemSubtotal = o.orderItems?.sumOf { it.subtotal } ?: 0.0
                        if (itemSubtotal > o.totalAmount) itemSubtotal - o.totalAmount else 0.0
                    }
                }

                val netRevenue = maxOf(0.0, totalRevenue - totalDeductions)
                val totalOrders = completedOrders.size
                val pendingCount = orders.count { it.status == "pending" }
                val canceledCount = orders.count { it.status == "cancelled" }
                val avgOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

                val paymentBreakdown = mutableMapOf<String, PaymentStats>()
                val hourly = MutableList(24) { 0 }
                val itemMap = mutableMapOf<String, ItemStats>()
                val dailyMap = mutableMapOf<String, Double>()

                completedOrders.forEach { o ->
                    val pm = o.paymentMethod ?: "unknown"
                    val currentStats = paymentBreakdown[pm] ?: PaymentStats(0, 0.0)
                    paymentBreakdown[pm] = PaymentStats(currentStats.count + 1, currentStats.revenue + o.totalAmount)

                    try {
                        // Supposedly created_at is like 2023-11-20T10:00:00.000+00:00 or Z
                        val cleanDate = if(o.createdAt.contains("+")) o.createdAt.substringBefore("+") else o.createdAt.substringBefore("Z")
                        val d = LocalDateTime.parse(cleanDate)
                        val h = d.hour
                        hourly[h] += 1

                        val dateKey = d.toLocalDate().toString()
                        dailyMap[dateKey] = (dailyMap[dateKey] ?: 0.0) + o.totalAmount
                    } catch (e: Exception) {}

                    o.orderItems?.forEach { oi ->
                        val name = oi.menuItemName
                        val curr = itemMap[name] ?: ItemStats(0, 0.0)
                        itemMap[name] = ItemStats(curr.qty + oi.quantity, curr.revenue + oi.subtotal)
                    }
                }

                val bestSellers = itemMap.toList().sortedByDescending { it.second.qty }.take(10)
                val totalItemsSold = itemMap.values.sumOf { it.qty }

                val maxHourly = hourly.maxOrNull() ?: 0
                val peakHour = if (maxHourly > 0) hourly.indexOf(maxHourly) else null

                val dailyEntries = dailyMap.toList().sortedBy { it.first }
                val totalCashVariance = shifts.sumOf { it.variance ?: 0.0 }

                _analyticsData.value = AnalyticsData(
                    totalRevenue = totalRevenue,
                    totalDeductions = totalDeductions,
                    netRevenue = netRevenue,
                    totalOrders = totalOrders,
                    totalItemsSold = totalItemsSold,
                    pendingCount = pendingCount,
                    canceledCount = canceledCount,
                    paymentBreakdown = paymentBreakdown,
                    hourly = hourly,
                    dailyEntries = dailyEntries,
                    bestSellers = bestSellers,
                    avgOrderValue = avgOrderValue,
                    totalCashVariance = totalCashVariance,
                    peakHour = peakHour,
                    orders = orders,
                    shifts = shifts
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportToPdf(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val data = analyticsData.value
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint()

                paint.textSize = 24f
                paint.isFakeBoldText = true
                canvas.drawText("Laporan Analitik Performa", 50f, 50f, paint)

                paint.textSize = 16f
                paint.isFakeBoldText = false
                canvas.drawText("Omzet Kotor: Rp ${String.format("%,.0f", data.totalRevenue)}", 50f, 100f, paint)
                canvas.drawText("Pesanan Sukses: ${data.totalOrders}", 50f, 130f, paint)
                
                var y = 170f
                paint.isFakeBoldText = true
                canvas.drawText("Distribusi Pembayaran", 50f, y, paint)
                paint.isFakeBoldText = false
                y += 30f
                data.paymentBreakdown.forEach { (method, stats) ->
                    canvas.drawText("${method.uppercase()}: ${stats.count} pesanan (Rp ${String.format("%,.0f", stats.revenue)})", 50f, y, paint)
                    y += 30f
                }

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
