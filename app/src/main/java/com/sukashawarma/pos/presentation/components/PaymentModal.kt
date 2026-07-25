package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sukashawarma.pos.domain.model.PaymentMethod
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun PaymentModal(
    totalAmount: Double,
    onConfirmPayment: (PaymentMethod, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashReceivedInput by remember { mutableStateOf(totalAmount.toInt().toString()) }

    val amountReceived = cashReceivedInput.toDoubleOrNull() ?: 0.0
    val changeAmount = maxOf(0.0, amountReceived - totalAmount)
    val isCashValid = selectedMethod != PaymentMethod.CASH || amountReceived >= totalAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = "Pembayaran Order",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display Total Amount
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = SlateCard
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL HARUS DIBAYAR", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Text(
                            text = "Rp ${String.format("%,.0f", totalAmount)}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Payment Method Selector
                Text("Pilih Metode Pembayaran", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethodOption(
                        title = "Tunai",
                        icon = Icons.Default.Money,
                        selected = selectedMethod == PaymentMethod.CASH,
                        onClick = { selectedMethod = PaymentMethod.CASH },
                        modifier = Modifier.weight(1f)
                    )
                    PaymentMethodOption(
                        title = "QRIS",
                        icon = Icons.Default.QrCode,
                        selected = selectedMethod == PaymentMethod.QRIS,
                        onClick = { selectedMethod = PaymentMethod.QRIS },
                        modifier = Modifier.weight(1f)
                    )
                    PaymentMethodOption(
                        title = "EDC / Card",
                        icon = Icons.Default.CreditCard,
                        selected = selectedMethod == PaymentMethod.CARD,
                        onClick = { selectedMethod = PaymentMethod.CARD },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Specific Input Panel
                when (selectedMethod) {
                    PaymentMethod.CASH -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cashReceivedInput,
                                onValueChange = { cashReceivedInput = it.filter { char -> char.isDigit() } },
                                label = { Text("Uang Diterima (Rp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Quick cash buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(totalAmount, 50000.0, 100000.0).forEach { amount ->
                                    OutlinedButton(
                                        onClick = { cashReceivedInput = amount.toInt().toString() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Rp ${String.format("%,.0f", amount)}")
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = StatusCompleted.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Kembalian", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = "Rp ${String.format("%,.0f", changeAmount)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = StatusCompleted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    PaymentMethod.QRIS -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "[ QRIS STATIS TOKO ]\n(Tampilkan QR Code)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    PaymentMethod.CARD -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = SlateCard,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Gesek / Tap kartu pelanggan pada mesin EDC. Tekan Konfirmasi jika transaksi di mesin EDC berhasil.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPayment(selectedMethod, amountReceived) },
                enabled = isCashValid,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Text("Konfirmasi & Bayar", color = SlateBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextMuted)
            }
        }
    )
}

@Composable
private fun PaymentMethodOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) AmberPrimary else SlateBorder
    val bgColor = if (selected) AmberPrimary.copy(alpha = 0.15f) else SlateCard

    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = title, tint = if (selected) AmberPrimary else TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = if (selected) AmberPrimary else TextSecondary)
        }
    }
}
