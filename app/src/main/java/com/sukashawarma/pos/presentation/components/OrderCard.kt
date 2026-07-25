package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        OrderStatus.CANCELLED -> Color.Gray
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Queue Number + Source Badge + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                    ) {
                        Text(
                            text = "#${order.orderNumber}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sourceBadge = when (order.source) {
                        OrderSource.POS -> "Kasir"
                        OrderSource.KIOSK -> "Kiosk"
                        OrderSource.ONLINE -> "Online"
                    }
                    Text(
                        text = sourceBadge,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberPrimary,
                        modifier = Modifier
                            .background(AmberPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = SlateBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Order Items List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (item in order.items) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Rp ${String.format("%,.0f", item.subtotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    if (item.note.isNotBlank()) {
                        Text(
                            text = "- ${item.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberLight,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = SlateBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Total & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Pembayaran",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Text(
                        text = "Rp ${String.format("%,.0f", order.totalAmount)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AmberPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Print Button
                    IconButton(
                        onClick = { onPrintReceipt(order) },
                        modifier = Modifier.background(SlateSurface, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Struk",
                            tint = TextPrimary
                        )
                    }

                    // Action Button based on Status
                    when (order.status) {
                        OrderStatus.PENDING -> {
                            Button(
                                onClick = { onStatusChange(order, OrderStatus.PREPARING) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mulai Masak")
                            }
                        }
                        OrderStatus.PREPARING -> {
                            Button(
                                onClick = { onStatusChange(order, OrderStatus.COMPLETED) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Selesai")
                            }
                        }
                        OrderStatus.COMPLETED -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusCompleted.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "LUNAS",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StatusCompleted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
