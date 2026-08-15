package com.sukashawarma.pos.presentation.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen, non-dismissible equivalent of the web ShiftBlockerMount. */
@Composable
fun ShiftBlockerOverlay(
    viewModel: ShiftViewModel,
    isOnline: Boolean,
    onLogout: () -> Unit
) {
    val openShiftInput by viewModel.openShiftInput.collectAsState()
    val pettyCashLocked by viewModel.pettyCashLocked.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // This sibling fills the whole layer beneath the modal card. It consumes
        // every gesture outside the card so touches never reach POS navigation
        // or content rendered behind the blocker.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        )

        Surface(
            modifier = Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3F4F6))
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Buka Shift Kasir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Anda harus membuka shift terlebih dahulu sebelum dapat mengakses pos kasir dan melakukan transaksi.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEE2E2))
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFB91C1C),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Saldo Awal Petty Cash (Uang Kembalian)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color(0xFF374151)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = if (pettyCashLocked) formatRupiah(openShiftInput.toDoubleOrNull() ?: 0.0) else openShiftInput,
                    onValueChange = { if (!pettyCashLocked) viewModel.openShiftInput.value = it },
                    readOnly = pettyCashLocked,
                    enabled = !isLoading,
                    leadingIcon = if (pettyCashLocked) null else ({
                        Text("Rp", fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                    }),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium),
                    placeholder = { Text("0") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD97706),
                        focusedLabelColor = Color(0xFFD97706),
                        disabledContainerColor = Color(0xFFF9FAFB),
                        disabledBorderColor = Color(0xFFE5E7EB),
                        disabledTextColor = Color(0xFF111827)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (pettyCashLocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Mengikuti sisa saldo petty cash dari closing shift sebelumnya. Hubungi SPV/Admin bila nominal ini perlu diubah.",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.openShift(isOnline) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (isLoading) "Membuka Shift..." else "Buka Shift Sekarang", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(24.dp))
                Surface(color = Color(0xFFF3F4F6), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF6B7280)
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Keluar Akun (Logout)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
