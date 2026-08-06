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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
    var parentId: String?,
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

    var showReprintDialog by remember { mutableStateOf(false) }

    if (showReprintDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showReprintDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFEFF6FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Print,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cetak Ulang Struk",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlateDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pilih struk yang ingin dicetak ulang",
                        fontSize = 14.sp,
                        color = TextSlate,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                showReprintDialog = false
                                onPrintKitchen(order)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TextBlue),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Dapur", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                showReprintDialog = false
                                onPrintCustomer(order)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TextEmerald),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Customer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
                        } else if (order.isSyncedFromOffline) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFD1FAE5), // emerald-100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "SYNC",
                                    color = Color(0xFF065F46), // emerald-800
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
                    var parentId: String? = null

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

                val rootItems = mutableListOf<ParsedOrderItem>()
                val childrenMap = mutableMapOf<String, MutableList<ParsedOrderItem>>()
                var lastRootId: String? = null

                parsedItems.forEach { i ->
                    val nameLower = i.name.lowercase()
                    val isImplicitExtra = i.parentId.isNullOrBlank() && 
                        (nameLower.startsWith("extra ") || nameLower.startsWith("toping ") || nameLower.startsWith("? extra") || nameLower.startsWith(" extra"))
                    
                    if (!i.parentId.isNullOrBlank()) {
                        // Explicit parent
                        childrenMap.getOrPut(i.parentId!!) { mutableListOf() }.add(i)
                    } else if (isImplicitExtra && lastRootId != null) {
                        // Implicit parent fallback for legacy/malformed orders
                        i.parentId = lastRootId
                        childrenMap.getOrPut(lastRootId!!) { mutableListOf() }.add(i)
                    } else {
                        // Root item
                        rootItems.add(i)
                        lastRootId = i.id
                    }
                }

                // Verify explicit parents exist, otherwise hoist to root
                val validRootIds = rootItems.map { it.id }.toSet()
                val orphanParentIds = childrenMap.keys.filter { !validRootIds.contains(it) }
                orphanParentIds.forEach { orphanParentId ->
                    childrenMap.remove(orphanParentId)?.let { rootItems.addAll(it) }
                }

                for (oi in rootItems) {
                    val children = childrenMap[oi.id] ?: emptyList()
                    val hasChildren = oi.note.isNotBlank() || children.isNotEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (hasChildren) {
                                    val topY = 24.dp.toPx()
                                    val bottomY = size.height - 12.dp.toPx()
                                    drawLine(
                                        color = Color(0xFFE2E8F0), // slate-200
                                        start = androidx.compose.ui.geometry.Offset(11.dp.toPx(), topY),
                                        end = androidx.compose.ui.geometry.Offset(11.dp.toPx(), bottomY),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
                                        Surface(color = Color(0xFFE2E8F0), modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9), // slate-100
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)) // slate-200
                                        ) {
                                            Text(
                                                text = oi.note,
                                                color = Color(0xFF475569), // slate-600
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                childrenMap[oi.id]?.let { childList ->
                                    for (child in childList) {
                                        Row(
                                            modifier = Modifier.padding(top = 6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(color = Color(0xFFE2E8F0), modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                            
                                            Text(
                                                text = "${child.quantity}x",
                                                color = Color(0xFF94A3B8), // slate-400
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(20.dp).padding(top = 1.dp)
                                            )
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(2.dp),
                                                        color = Color(0xFFF1F5F9) // slate-100
                                                    ) {
                                                        Text(
                                                            text = "EXTRA",
                                                            color = Color(0xFF64748B), // slate-500
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = child.name,
                                                        color = Color(0xFF334155), // slate-700
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }

                                                if (child.note.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFF8FAFC), // slate-50
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)), // slate-200
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = child.note,
                                                            color = Color(0xFF475569), // slate-600
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
            }

            // Actions Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                if (order.cancellationStatus == "pending_approval") {
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
                } else if (order.status == OrderStatus.PENDING || order.status == OrderStatus.PREPARING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (order.source == OrderSource.KIOSK && order.paymentMethod == PaymentMethod.QRIS && order.amountReceived < order.totalAmount) {
                            // KIOSK QRIS Belum Lunas -> Disable actions
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
                        } else {
                            Button(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)), // red-100
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, tint = TextRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Batal", color = TextRed, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            
                            Button(
                                onClick = { onStatusChange(order, OrderStatus.COMPLETED) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextEmerald),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Selesai", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            
                            OutlinedButton(
                                onClick = { onPrintCustomer(order) },
                                modifier = Modifier.width(48.dp).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DefaultBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSlateDark),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
