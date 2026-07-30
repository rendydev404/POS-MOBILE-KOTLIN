package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderSource
import com.sukashawarma.pos.domain.model.OrderStatus
import com.sukashawarma.pos.domain.model.PaymentMethod
import com.sukashawarma.pos.presentation.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colors matching Web
private val PendingBg = Color(0xFFFFFBEB) // amber-50
private val PendingBorder = Color(0xFFFEF3C7) // amber-100
private val PreparingBg = Color(0xFFEFF6FF) // blue-50
private val PreparingBorder = Color(0xFFDBEAFE) // blue-100
private val DefaultBg = Color.White
private val DefaultBorder = Color(0xFFE2E8F0) // slate-200

private val TextAmber = Color(0xFFD97706) // amber-600
private val TextBlue = Color(0xFF2563EB) // blue-600
private val TextEmerald = Color(0xFF10B981) // emerald-500
private val TextRed = Color(0xFFDC2626) // red-600
private val TextSlate = Color(0xFF475569) // slate-600
private val TextSlateDark = Color(0xFF1E293B) // slate-800

private val BadgeAmberBg = Color(0xFFFEF3C7)
private val BadgeBlueBg = Color(0xFFDBEAFE)

data class ParsedOrderItem(
    val id: String,
    val name: String,
    val note: String,
    val parentId: String?,
    val quantity: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCard(
    order: Order,
    onStatusChange: (Order, OrderStatus) -> Unit,
    onCancelOrder: (Order, String) -> Unit,
    onPrintKitchen: (Order) -> Unit,
    onPrintCustomer: (Order) -> Unit,
    onReprint: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH.mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(order.createdAt))
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }
    val formattedPrice = currencyFormat.format(order.totalAmount).replace("Rp", "Rp ")

    val diffMs = System.currentTimeMillis() - order.createdAt
    val diffMins = diffMs / (60 * 1000)
    val timeAgoStr = when {
        diffMins < 60 -> "$diffMins menit yang lalu"
        diffMins < 24 * 60 -> "${diffMins / 60} jam yang lalu"
        else -> "${diffMins / (24 * 60)} hari yang lalu"
    }

    val cardBg = when (order.status) {
        OrderStatus.PENDING -> PendingBg
        OrderStatus.PREPARING -> PreparingBg
        else -> DefaultBg
    }
    
    val cardBorder = when (order.status) {
        OrderStatus.PENDING -> PendingBorder
        OrderStatus.PREPARING -> PreparingBorder
        else -> DefaultBorder
    }

    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        var cancelReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Batalkan Pesanan") },
            text = {
                Column {
                    Text("Alasan pembatalan (wajib):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cancelReason.isNotBlank()) {
                            onCancelOrder(order, cancelReason)
                            showCancelDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextRed)
                ) {
                    Text("Batalkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Tutup", color = TextSlate)
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row (Top Padding)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Nomor Antrian #${order.orderNumber}",
                        color = TextSlateDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "dipesan $timeAgoStr",
                            color = TextSlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (order.isOffline) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFEE2E2), // red-100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "OFFLINE",
                                    color = Color(0xFF991B1B), // red-800
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Status Badge
                if (order.status == OrderStatus.PENDING) {
                    Surface(
                        color = BadgeAmberBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PendingBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Timer, contentDescription = null, tint = TextAmber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MENUNGGU", color = TextAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (order.status == OrderStatus.PREPARING) {
                    Surface(
                        color = BadgeBlueBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PreparingBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = TextBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DIPROSES", color = TextBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))

            // Customer Info Row (bg-[#fff8f1])
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF8F1))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFFFEDD5), // orange-100
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.customerName.ifBlank { "Pelanggan" },
                            color = TextSlateDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                color = Color(0xFFF1F5F9), // slate-100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = (if (order.source == OrderSource.ONLINE) "ONLINE" else "KASIR").uppercase(),
                                    color = TextSlate,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFE0E7FF), // indigo-100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = order.paymentMethod.name.uppercase(),
                                    color = Color(0xFF4338CA), // indigo-700
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = formattedPrice,
                        color = TextEmerald,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))

            // Order Items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val parsedItems = order.items.map { oi ->
                    var name = oi.name
                    var note = oi.note
                    var id = oi.id
                    var parentId: String? = if (oi.isChild) "placeholder" else null

                    val noteSplit = name.split("|NOTE|")
                    if (noteSplit.size > 1) {
                        note = noteSplit[1]
                        name = noteSplit[0]
                    }

                    val parentSplit = name.split("|PARENT|")
                    if (parentSplit.size > 1) {
                        parentId = parentSplit[1]
                        name = parentSplit[0]
                    }

                    val idSplit = name.split("|ID|")
                    if (idSplit.size > 1) {
                        id = idSplit[1]
                        name = idSplit[0]
                    }

                    ParsedOrderItem(id, name, note, parentId, oi.quantity)
                }

                val rootItems = parsedItems.filter { it.parentId.isNullOrBlank() }.toMutableList()
                val validRootIds = rootItems.map { it.id }.toSet()

                val childrenMap = mutableMapOf<String, MutableList<ParsedOrderItem>>()
                parsedItems.filter { !it.parentId.isNullOrBlank() }.forEach { i ->
                    if (!validRootIds.contains(i.parentId)) {
                        rootItems.add(i) // Orphan child
                    } else {
                        childrenMap.getOrPut(i.parentId!!) { mutableListOf() }.add(i)
                    }
                }

                for (oi in rootItems) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${oi.quantity}x",
                                color = TextSlateDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp).padding(top = 2.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = oi.name,
                                    color = TextSlateDark.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (oi.note.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(color = Color(0xFFCBD5E1), modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF2F2), // red-50
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)) // red-300
                                        ) {
                                            Text(
                                                text = oi.note,
                                                color = Color(0xFF991B1B), // red-800
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                childrenMap[oi.id]?.let { children ->
                                    for (child in children) {
                                        Row(
                                            modifier = Modifier.padding(top = 6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(color = Color(0xFFCBD5E1), modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                            Text(
                                                text = "${child.quantity}x",
                                                color = TextSlate,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(24.dp).padding(top = 2.dp)
                                            )
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFF1F5F9)
                                                    ) {
                                                        Text(
                                                            text = "EXTRA",
                                                            color = TextSlate,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = child.name,
                                                        color = TextSlateDark.copy(alpha = 0.8f),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Actions Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (order.status == OrderStatus.PENDING) {
                        if (order.paymentMethod == PaymentMethod.QRIS) {
                            // Tunggu QRIS
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEFF6FF), // blue-50
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextBlue, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tunggu QRIS", color = TextBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (order.cancellationStatus == "pending_approval") {
                            // Menunggu Persetujuan Batal
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEFCE8), // yellow-50
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF08A)) // yellow-200
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFCA8A04), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menunggu Persetujuan Batal", color = Color(0xFFCA8A04), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Normal PENDING actions
                            Button(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)) // red-100
                            ) {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, tint = TextRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Batal", color = TextRed, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onStatusChange(order, OrderStatus.PREPARING) },
                                modifier = Modifier.weight(2f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextBlue)
                            ) {
                                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mulai Masak", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (order.status == OrderStatus.PREPARING) {
                        Button(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Icon(Icons.Outlined.Cancel, contentDescription = null, tint = TextRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Batal", color = TextRed, fontWeight = FontWeight.Bold)
                        }

                        if (!order.kitchenReceiptPrinted) {
                            Button(
                                onClick = { 
                                    onPrintKitchen(order)
                                    onStatusChange(order, OrderStatus.PREPARING) // Web handles state changes internally, but we can do it via ViewModel 
                                },
                                modifier = Modifier.weight(2f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextBlue)
                            ) {
                                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mulai Masak", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else if (!order.customerReceiptPrinted) {
                            Button(
                                onClick = { onPrintCustomer(order) },
                                modifier = Modifier.weight(2f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextEmerald)
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cetak Struk", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Completed / Printed both
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onStatusChange(order, OrderStatus.COMPLETED) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TextEmerald)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Selesai", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onReprint(order) },
                                    modifier = Modifier.width(56.dp).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, DefaultBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSlate)
                                ) {
                                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    } else if (order.status == OrderStatus.COMPLETED) {
                        OutlinedButton(
                            onClick = { onReprint(order) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DefaultBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSlateDark)
                        ) {
                            Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Struk", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
