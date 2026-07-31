package com.sukashawarma.pos.presentation.attendance

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AttendanceOverlay(viewModel: AttendanceViewModel) {
    val isLocked by viewModel.isLocked.collectAsState()
    val lockReason by viewModel.lockReason.collectAsState()
    val bypassStatus by viewModel.bypassStatus.collectAsState()
    val spvPhone by viewModel.spvPhone.collectAsState()
    val context = LocalContext.current
    var reason by remember { mutableStateOf("") }
    var showReasonInput by remember { mutableStateOf(false) }

    if (isLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(32.dp).fillMaxWidth(0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Akses POS Terkunci",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lockReason,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (bypassStatus == "pending") {
                        Text(
                            text = "Menunggu persetujuan Regional Manager...",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (showReasonInput) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Alasan Bypass") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { showReasonInput = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Batal")
                            }
                            Button(
                                onClick = {
                                    if (reason.isNotBlank()) {
                                        viewModel.requestBypass(reason) { waText ->
                                            val message = android.net.Uri.encode(waText)
                                            val phone = if (spvPhone.isNotBlank()) spvPhone else "6285218446637"
                                            val uriString = "whatsapp://send?phone=$phone&text=$message"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "WhatsApp tidak terpasang di perangkat ini", android.widget.Toast.LENGTH_LONG).show()
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                },
                                enabled = reason.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Kirim Pengajuan")
                            }
                        }
                    } else {
                        Button(
                            onClick = { showReasonInput = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Minta Bypass ke Regional Manager")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.checkLockStatus() }
                    ) {
                        Text("Refresh Status")
                    }
                }
            }
        }
    }
}
