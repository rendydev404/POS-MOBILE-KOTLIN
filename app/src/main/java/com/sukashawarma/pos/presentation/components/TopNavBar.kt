package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun TopNavBar(
    outletName: String = "SUKA SHAWARMA JATIWARINGIN",
    cashierName: String = "Kasir Operasional",
    isOnline: Boolean = true,
    pendingSyncCount: Int = 0,
    onLogoutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = SlateSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Outlet & Cashier Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = "Outlet",
                        tint = AmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = outletName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp),
                    color = SlateBorder
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Kasir",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cashierName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            // Network & Sync Badge + Logout Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NetworkBadge(
                    isOnline = isOnline,
                    pendingSyncCount = pendingSyncCount
                )

                onLogoutClick?.let { onLogout ->
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.background(SlateCard, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout / Ganti Outlet",
                            tint = StatusPending
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkBadge(
    isOnline: Boolean,
    pendingSyncCount: Int
) {
    val backgroundColor = if (isOnline) StatusCompleted.copy(alpha = 0.15f) else StatusPending.copy(alpha = 0.15f)
    val contentColor = if (isOnline) StatusCompleted else StatusPending

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = if (isOnline) "ONLINE" else "OFFLINE",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )

            if (pendingSyncCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusPending
                ) {
                    Text(
                        text = "$pendingSyncCount Sync",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
