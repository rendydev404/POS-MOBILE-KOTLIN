package com.sukashawarma.pos.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.StockAlert
import com.sukashawarma.pos.presentation.theme.MarqueeRed

/**
 * Pita merah "STOK KRITIS/HABIS" di atas dashboard, padanan StockMarquee.tsx di
 * POS web.
 *
 * Dua perilaku penting yang dulu tidak ada:
 * - **Hilang total kalau tidak ada bahan kritis.** Sebelumnya pita selalu
 *   tampil, jadi kasir melihat "STOK KRITIS/HABIS:" tanpa isi sepanjang hari
 *   dan berhenti mempercayainya.
 * - **Berjalan (marquee).** Daftar bahan bisa jauh lebih panjang dari layar;
 *   tanpa animasi, nama-nama di belakang tidak pernah terbaca.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockMarquee(
    alerts: List<StockAlert>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = alerts.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        // Ikon berkedip pelan — penanda "ini masih hidup", sama seperti
        // `animate-pulse` di web. Teks tidak ikut berkedip supaya tetap terbaca.
        val pulse by rememberInfiniteTransition(label = "stok-kritis").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "kedip"
        )

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MarqueeRed)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).alpha(pulse)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "STOK KRITIS/HABIS",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )

            // Jumlah bahan: kasir bisa menilai gawat/tidaknya tanpa menunggu
            // seluruh teks berjalan lewat.
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = alerts.size.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MarqueeRed,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = alerts.joinToString("   •   ") { it.marqueeText },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .weight(1f)
                    // Jalan terus tanpa jeda awal; kecepatan dipelankan dari
                    // bawaan 30.dp/s supaya nama bahan sempat terbaca.
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        delayMillis = 0,
                        initialDelayMillis = 0,
                        velocity = 24.dp
                    )
            )
        }
    }
}
