package com.sukashawarma.pos.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OwnerMessageDto
import com.sukashawarma.pos.data.remote.dto.TargetProgressDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.animation.core.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class BriefingViewModel : ViewModel() {
    private val api = SupabaseClient.api

    private val _progress = MutableStateFlow<TargetProgressDto?>(null)
    val progress: StateFlow<TargetProgressDto?> = _progress.asStateFlow()

    private val _messages = MutableStateFlow<List<OwnerMessageDto>>(emptyList())
    val messages: StateFlow<List<OwnerMessageDto>> = _messages.asStateFlow()

    private var currentOutletId: String = ""

    fun setOutlet(outletId: String) {
        if (currentOutletId != outletId) {
            currentOutletId = outletId
            startPolling()
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive && currentOutletId.isNotEmpty()) {
                fetchData()
                delay(60_000) // Poll every 60 seconds
            }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.ownerMessageRefreshEvent.collect {
                if (currentOutletId.isNotEmpty()) {
                    fetchData()
                }
            }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.collect {
                if (currentOutletId.isNotEmpty()) {
                    fetchData()
                }
            }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect {
                if (currentOutletId.isNotEmpty()) {
                    fetchData()
                }
            }
        }
    }

    private suspend fun fetchData() {
        try {
            val progressRes = api.getMyTargetProgress()
            if (progressRes.isSuccessful) {
                _progress.value = progressRes.body()?.firstOrNull()
            }

            val messagesRes = api.getMyActiveMessages()
            if (messagesRes.isSuccessful) {
                _messages.value = messagesRes.body() ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun formatRupiahCompact(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 0
    val formatted = formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
    return if (amount >= 1_000_000) {
        "Rp " + String.format(Locale("id", "ID"), "%.1f", amount / 1_000_000).replace(".0", "") + " Jt"
    } else if (amount >= 1_000) {
        "Rp " + String.format(Locale("id", "ID"), "%.1f", amount / 1_000).replace(".0", "") + " Rb"
    } else {
        formatted
    }
}

@Composable
fun BriefingBanner(
    outletId: String,
    viewModel: BriefingViewModel = viewModel()
) {
    LaunchedEffect(outletId) {
        viewModel.setOutlet(outletId)
    }

    val progress by viewModel.progress.collectAsState()
    val messages by viewModel.messages.collectAsState()

    var showConfetti by remember { mutableStateOf(false) }
    var wasDone by remember { mutableStateOf(false) }

    progress?.let { target ->
        val isDone = target.omzetToday >= target.targetAmount && target.targetAmount > 0
        if (isDone && !wasDone) {
            wasDone = true
            showConfetti = true
        } else if (!isDone && wasDone) {
            wasDone = false
        }
    }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            kotlinx.coroutines.delay(5000)
            showConfetti = false
        }
    }

    if (showConfetti) {
        Popup(properties = PopupProperties(focusable = false, clippingEnabled = false)) {
            val party = Party(
                speed = 30f,
                maxSpeed = 50f,
                damping = 0.9f,
                spread = 360,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 5, TimeUnit.SECONDS).perSecond(50),
                position = Position.Relative(0.5, 0.2)
            )
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(party)
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Target Harian Banner
        progress?.let { target ->
            if (target.targetAmount > 0) {
                val pct = (target.omzetToday / target.targetAmount * 100).coerceAtMost(100.0)
                val pctRaw = (target.omzetToday / target.targetAmount * 100).roundToInt()
                val isDone = target.omzetToday >= target.targetAmount
                val isGreen = pctRaw >= 100
                val isYellow = pctRaw in 30..99

                val bgColor = if (isDone || isGreen) Color(0xFFEAFAEF) else if (isYellow) Color(0xFFFFFAF4) else Color(0xFFFEF2F2)
                val borderColor = if (isDone || isGreen) Color(0xFFBFE6C9) else if (isYellow) Color(0xFFECDCC9) else Color(0xFFFECACA)
                val barColor = if (isDone || isGreen) Color(0xFF0A7D2C) else if (isYellow) Color(0xFFF29744) else Color(0xFFEF4444)
                val amountColor = if (isDone || isGreen) Color(0xFF0A7D2C) else if (isYellow) Color(0xFF643400) else Color(0xFFB91C1C)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .border(1.dp, borderColor)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.Star else Icons.Default.Info,
                        contentDescription = "Target",
                        tint = amountColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDone) "TARGET TERCAPAI!" else "TARGET HARI INI",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = amountColor,
                        modifier = Modifier.width(130.dp)
                    )
                    
                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (pct / 100).toFloat())
                                .clip(CircleShape)
                                .background(barColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatRupiahCompact(target.omzetToday),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = amountColor
                            )
                            Text(
                                text = " / ${formatRupiahCompact(target.targetAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFA98B73)
                            )
                        }
                        Text(
                            text = "$pctRaw%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = amountColor
                        )
                    }
                }
                
                if (showConfetti) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val dy by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -10f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bounce"
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEAFAEF))
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = dy.dp)
                                .background(Color(0xFF0A7D2C), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🎉 Selamat! Target hari ini tercapai 🎉",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Pesan Owner
        messages.forEach { msg ->
            val isInfo = msg.kind == "info"
            val isWarning = msg.kind == "peringatan"
            val bgColor = if (isInfo) Color(0xFFEAFAEF) else if (isWarning) Color(0xFFFDEAEA) else Color(0xFFFFF3E6)
            val iconColor = if (isInfo) Color(0xFF0A7D2C) else if (isWarning) Color(0xFFDC2626) else Color(0xFFF29744)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .border(1.dp, iconColor.copy(alpha = 0.2f))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWarning) Icons.Default.Warning else if (isInfo) Icons.Default.Info else Icons.Default.Star,
                        contentDescription = "Pesan",
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pesan dari Owner" + if (!msg.title.isNullOrBlank()) " — ${msg.title}" else "",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E1B15)
                    )
                    Text(
                        text = msg.body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF3A322B)
                    )
                }
            }
        }
    }
}
