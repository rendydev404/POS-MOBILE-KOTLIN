package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.R
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderSource

@Composable
fun OrderSourceBadge(source: String, channel: String?, modifier: Modifier = Modifier) {
    val (label, color, iconContent) = getOrderSourceInfo(source, channel)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            iconContent(color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun normalizeChannelId(id: String?): String {
    return id?.lowercase()?.replace(" ", "") ?: ""
}

@Composable
fun getOrderSourceInfo(source: String, channel: String?): Triple<String, Color, @Composable (Color) -> Unit> {
    // Endorse disimpan sebagai source POS dengan channel "endorse", jadi dicek lebih dulu
    // agar tidak jatuh ke badge "POS Kasir".
    if (normalizeChannelId(channel) == "endorse") {
        return Triple("Endorse", Color(0xFFDB2777), { c -> Icon(Icons.Rounded.ThumbUp, null, tint = c, modifier = Modifier.size(12.dp)) })
    }
    if (source.uppercase() == "ONLINE") {
        if (channel != null) {
            val normalizedChannel = normalizeChannelId(channel)
            when (normalizedChannel) {
                "gofood" -> return Triple("GoFood", Color(0xFF00AA13), { Image(painterResource(R.drawable.ic_gofood), null, modifier = Modifier.size(12.dp)) })
                "shopeefood" -> return Triple("ShopeeFood", Color(0xFFEE4D2D), { Image(painterResource(R.drawable.ic_shopeefood), null, modifier = Modifier.size(12.dp)) })
                "grabfood" -> return Triple("GrabFood", Color(0xFF00B14F), { Image(painterResource(R.drawable.ic_grabfood), null, modifier = Modifier.size(12.dp)) })
                "tiktokgo" -> return Triple("TikTok Go", Color(0xFF000000), { Image(painterResource(R.drawable.ic_tiktokgo), null, modifier = Modifier.size(12.dp)) })
                "website" -> return Triple("Website Online", Color(0xFF2563EB), { c -> Icon(Icons.Rounded.Language, null, tint = c, modifier = Modifier.size(12.dp)) })
            }
        }
        return Triple("Website Online", Color(0xFF2563EB), { c -> Icon(Icons.Rounded.Language, null, tint = c, modifier = Modifier.size(12.dp)) })
    } else if (source.uppercase() == "KIOSK") {
        return Triple("Kiosk", Color(0xFF8B5CF6), { c -> Icon(Icons.Rounded.PointOfSale, null, tint = c, modifier = Modifier.size(12.dp)) })
    } else {
        return Triple("POS Kasir", Color(0xFF4B5563), { c -> Icon(Icons.Rounded.PointOfSale, null, tint = c, modifier = Modifier.size(12.dp)) })
    }
}
