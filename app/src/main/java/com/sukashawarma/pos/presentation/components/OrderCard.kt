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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderSource
import com.sukashawarma.pos.domain.model.OrderStatus
import com.sukashawarma.pos.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderCard(
    order: Order,
    onStatusChange: (Order, OrderStatus) -> Unit,
    onPrintReceipt: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(order.createdAt))

    val statusColor = when (order.status) {
        OrderStatus.PENDING -> StatusPending
        OrderStatus.PREPARING -> StatusPreparing
        OrderStatus.READY -> StatusCompleted
        OrderStatus.COMPLETED -> StatusCompleted
        OrderStatus.CANCELLED -> TwRed500
    }

    val statusText = when (order.status) {
        OrderStatus.PENDING -> "MENUNGGU"
        OrderStatus.PREPARING -> "DIPROSES"
        OrderStatus.READY -> "SELESAI"
        OrderStatus.COMPLETED -> "LUNAS"
        OrderStatus.CANCELLED -> "BATAL"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Queue Number + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "NOMOR ANTRIAN",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkMuted
                    )
                    Text(
                        text = "#${order.orderNumber}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "dipesan $formattedTime yang lalu", // Approximation
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextDarkMuted
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Customer Info Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CreamBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = TwAmber100,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TwAmber500, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = order.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sourceBadge = when (order.source) {
                                OrderSource.POS -> "KASIR"
                                OrderSource.KIOSK -> "KIOSK"
                                OrderSource.ONLINE -> "ONLINE"
                            }
                            Text(
                                text = "OFFLINE", // Assuming POS/Kiosk is offline, could be dynamic
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = TwGray200, modifier = Modifier.size(4.dp), shape = CircleShape) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sourceBadge,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TwGray100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Order Items List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (item in order.items) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${item.quantity}x",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary,
                            modifier = Modifier.width(28.dp).padding(top = 4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TwGray100,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    // Placeholder for image
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = TwGray400, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextDarkPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (item.note.isNotBlank()) {
                                Row(
                                    modifier = Modifier.padding(top = 8.dp, start = 18.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("└", color = TwGray300, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = "1x",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextDarkSecondary,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Surface(
                                        color = TwGray100,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "EXTRA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDarkSecondary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = item.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextDarkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TwGray100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Button (Batal / Cetak Struk)
                if (order.status != OrderStatus.COMPLETED) {
                    OutlinedButton(
                        onClick = { onStatusChange(order, OrderStatus.CANCELLED) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TwRed200),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TwRed500)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) // Icon batal? Using text is fine
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batal", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPrintReceipt(order) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkPrimary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak Struk", fontWeight = FontWeight.Bold)
                    }
                }

                // Right Button (Primary Action)
                when (order.status) {
                    OrderStatus.PENDING -> {
                        Button(
                            onClick = { onStatusChange(order, OrderStatus.PREPARING) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TwBlue600)
                        ) {
                            Text("Mulai Masak", fontWeight = FontWeight.Bold)
                        }
                    }
                    OrderStatus.PREPARING -> {
                        Button(
                            onClick = { onStatusChange(order, OrderStatus.COMPLETED) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TwEmerald500)
                        ) {
                            Text("Cetak Struk", fontWeight = FontWeight.Bold) // Often print receipt doubles as 'finish' in some POS, let's keep it Selesai
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
