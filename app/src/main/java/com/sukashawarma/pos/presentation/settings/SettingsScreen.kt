package com.sukashawarma.pos.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sukashawarma.pos.presentation.theme.AmberPrimary
import com.sukashawarma.pos.presentation.theme.SlateBorder
import com.sukashawarma.pos.presentation.theme.SlateCard
import com.sukashawarma.pos.presentation.theme.SlateSurface
import com.sukashawarma.pos.presentation.theme.TextMuted
import com.sukashawarma.pos.presentation.theme.TextPrimary
import com.sukashawarma.pos.presentation.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val coverUrl by viewModel.coverUrl.collectAsState()
    val isUploadingCover by viewModel.isUploadingCover.collectAsState()
    val coverMessage by viewModel.coverMessage.collectAsState()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadCover)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(AmberPrimary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tablet,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Tampilan Layar Kiosk",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(24.dp))

            // Main Content Area constrained in width for better tablet reading
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp) // Prevent infinite stretching on tablets
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Gambar Cover Cabang",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "Cover tampil fullscreen pada kiosk cabang ini. File JPG, PNG, atau WebP (maks. 5 MB). Rasio 16:9 sangat direkomendasikan agar tidak terpotong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 16:9 Image Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUrl.isNullOrBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Belum ada gambar cover",
                                color = TextSecondary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Preview cover kiosk",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Button centered
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { coverPicker.launch("image/*") },
                        enabled = !isUploadingCover,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isUploadingCover) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("MENGUNGGAH...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("UPLOAD GAMBAR COVER", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Error / Success Message
                coverMessage?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = it,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
