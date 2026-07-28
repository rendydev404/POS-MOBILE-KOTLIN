package com.sukashawarma.pos.presentation.info_porsi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun InfoPorsiScreen(
    viewModel: InfoPorsiViewModel,
    modifier: Modifier = Modifier
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val porsiList by viewModel.porsiList.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, CreamBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Informasi Sisa Porsi Menu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Text("Halaman ini menampilkan estimasi sisa porsi untuk seluruh menu berdasarkan stok bahan baku saat ini.", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
                }
                IconButton(onClick = { viewModel.loadPorsi() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ShawarmaOrange)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShawarmaOrange)
                }
            } else if (!error.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: $error", color = Color.Red)
                }
            } else if (porsiList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, CreamBorder, RoundedCornerShape(12.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Belum Ada Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                        Text("Tidak ada data stok/resep yang dapat dihitung porsinya.", color = TextDarkSecondary)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Estimasi Porsi Tersedia:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(porsiList) { info ->
                                val (textColor, bgColor, borderColor, dotColor) = when {
                                    info.portions == 0 -> listOf(Color(0xFFB91C1C), Color(0xFFFEF2F2), Color(0xFFFECACA), Color(0xFFEF4444))
                                    info.portions < 7 -> listOf(Color(0xFFC2410C), Color(0xFFFFF7ED), Color(0xFFFED7AA), Color(0xFFF97316))
                                    else -> listOf(Color(0xFF15803D), Color(0xFFF0FDF4), Color(0xFFBBF7D0), Color(0xFF22C55E))
                                }

                                Row(
                                    modifier = Modifier
                                        .background(bgColor, RoundedCornerShape(8.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dotColor))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(info.menuName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor)
                                        Text(" porsi", style = MaterialTheme.typography.bodySmall, color = textColor, fontSize = 11.sp)
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
