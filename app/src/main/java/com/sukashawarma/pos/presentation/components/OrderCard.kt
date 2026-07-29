package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderSource
import com.sukashawarma.pos.domain.model.OrderStatus
import com.sukashawarma.pos.presentation.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Colors matching Web App
private val WebGreen = Color(0xFF0A7D2C)
private val WebGreenLight = Color(0x0D0A7D2C) // 5% alpha
private val WebGreenLighter = Color(0x1A0A7D2C) // 10% alpha
private val WebGreenBorder = Color(0x1A0A7D2C) // 10% alpha
private val DashedLineColor = Color(0xFFD9C2B2)
private val BadgeGrayBg = Color(0xFFE5E7EB)
private val BadgeGrayText = Color(0xFF4B5563)
private val BadgeAmberBg = Color(0x0D701604) // 5% alpha of #701604
private val BadgeAmberText = Color(0xCC1E293B) // slate-800/80
private val NoteLineColor = Color(0x1A701604) // 10% of #701604
private val NoteBgColor = Color(0xFFFFF8F1)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate500 = Color(0xFF64748B)
private val Slate800 = Color(0xFF1E293B)

@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = DashedLineColor, thickness: Float = 3f) {
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = thickness,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        )
    }
}

data class ParsedOrderItem(
    val id: String,
    val name: String,
    val note: String,
    val parentId: String?,
    val quantity: Int
)

@Composable
fun OrderCard(
    order: Order,
    onStatusChange: (Order, OrderStatus) -> Unit,
    onPrintReceipt: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH.mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(order.createdAt))
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }
    val formattedPrice = currencyFormat.format(order.totalAmount).replace("Rp", "Rp ")

    // Estimate time ago
    val diffMs = System.currentTimeMillis() - order.createdAt
    val diffMins = diffMs / (60 * 1000)
    val timeAgoStr = when {
        diffMins < 60 -> "$diffMins menit yang lalu"
        diffMins < 24 * 60 -> "${diffMins / 60} jam yang lalu"
        else -> "${diffMins / (24 * 60)} hari yang lalu"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate50.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    // Queue Number Box
                    Column(
                        modifier = Modifier
                            .background(WebGreenLight, RoundedCornerShape(12.dp))
                            .border(1.dp, WebGreenBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .sizeIn(minWidth = 56.dp, minHeight = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ANTRIAN",
                            color = WebGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "#${order.orderNumber}",
                            color = WebGreen,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Customer Info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.customerName.ifBlank { "Pelanggan" },
                                color = Slate800,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedPrice,
                                color = WebGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = timeAgoStr,
                                color = WebGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(color = DashedLineColor, modifier = Modifier.size(4.dp), shape = CircleShape) {}
                            Text(
                                text = formattedTime,
                                color = Slate500,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(color = DashedLineColor, modifier = Modifier.size(4.dp), shape = CircleShape) {}
                            
                            // Source Badge
                            val sourceBadgeText = when (order.source) {
                                OrderSource.ONLINE -> "ONLINE"
                                else -> "OFFLINE"
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BadgeGrayBg
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = sourceBadgeText,
                                        color = BadgeGrayText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            
                            // Payment Method Badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BadgeAmberBg
                            ) {
                                Text(
                                    text = order.paymentMethod.name.uppercase(),
                                    color = BadgeAmberText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Checkmark button (if in completed status, acts as indicator, if in other status, could be an action)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WebGreenLighter,
                    modifier = Modifier.size(28.dp),
                    onClick = {
                        // In web, checkmark might move it to next stage
                        if (order.status == OrderStatus.PREPARING) {
                            onStatusChange(order, OrderStatus.COMPLETED)
                        } else if (order.status == OrderStatus.PENDING) {
                            onStatusChange(order, OrderStatus.PREPARING)
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = WebGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            DashedDivider(modifier = Modifier.padding(bottom = 12.dp))

            // Order Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Parse items using identical logic to web frontend
                val parsedItems = order.items.map { oi ->
                    var name = oi.name
                    var note = oi.note
                    var id = oi.id
                    var parentId: String? = if (oi.isChild) "placeholder" else null // We will use exact parsing if needed

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
                        rootItems.add(i) // Orphan child treated as root
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
                                color = Slate800,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp).padding(top = 2.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = oi.name,
                                    color = Slate800.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (oi.note.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(color = NoteLineColor, modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = NoteBgColor,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, NoteLineColor)
                                        ) {
                                            Text(
                                                text = oi.note,
                                                color = Slate800,
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
                                            Surface(color = NoteLineColor, modifier = Modifier.padding(top = 8.dp, end = 8.dp).size(width = 12.dp, height = 2.dp)) {}
                                            Text(
                                                text = "${child.quantity}x",
                                                color = Slate800.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(24.dp).padding(top = 2.dp)
                                            )
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(2.dp),
                                                        color = BadgeAmberBg
                                                    ) {
                                                        Text(
                                                            text = "EXTRA",
                                                            color = BadgeAmberText,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = child.name,
                                                        color = Slate800.copy(alpha = 0.6f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                if (child.note.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = NoteBgColor,
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, NoteLineColor),
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = child.note,
                                                            color = Slate800,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Action Buttons
            if (order.status != OrderStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                DashedDivider(modifier = Modifier.padding(bottom = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onStatusChange(order, OrderStatus.CANCELLED) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)), // Red-300
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)) // Red-500
                    ) {
                        Text("Batal", fontWeight = FontWeight.Bold)
                    }

                    if (order.status == OrderStatus.PENDING) {
                        Button(
                            onClick = { onStatusChange(order, OrderStatus.PREPARING) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)) // Blue-600
                        ) {
                            Text("Mulai Masak", fontWeight = FontWeight.Bold)
                        }
                    } else if (order.status == OrderStatus.PREPARING) {
                        Button(
                            onClick = { onStatusChange(order, OrderStatus.COMPLETED) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WebGreen)
                        ) {
                            Text("Selesai", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                DashedDivider(modifier = Modifier.padding(bottom = 12.dp))
                OutlinedButton(
                    onClick = { onPrintReceipt(order) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cetak Struk", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
