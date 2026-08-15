package com.sukashawarma.pos.presentation.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.sukashawarma.pos.presentation.theme.CreamBackground
import com.sukashawarma.pos.presentation.theme.ShawarmaOrange
import org.json.JSONArray

@Composable
fun GuideScreen(viewModel: GuideViewModel, modifier: Modifier = Modifier) {
    val guides by viewModel.guides.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val categories = guides.map { it.category }.distinct()
    val activeGuides = guides.filter { it.category == selectedCategory }

    Column(modifier.fillMaxSize().background(CreamBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = ShawarmaOrange.copy(alpha = .14f)) {
                Icon(Icons.Default.Book, null, tint = ShawarmaOrange, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Panduan", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Petunjuk operasional yang selalu sama dengan POS web", color = Color(0xFF6B7280), fontSize = 13.sp)
            }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Muat ulang panduan") }
        }
        Spacer(Modifier.height(18.dp))
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ShawarmaOrange) }
            error != null -> EmptyGuideState(error!!, viewModel::refresh)
            guides.isEmpty() -> EmptyGuideState("Belum ada panduan yang ditambahkan.", viewModel::refresh)
            else -> {
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = ShawarmaOrange,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    categories.forEach { category ->
                        Tab(selected = category == selectedCategory, onClick = { viewModel.selectCategory(category) }, text = { Text(category, maxLines = 1) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(selectedCategory, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827))
                    activeGuides.forEachIndexed { index, guide ->
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(99.dp), color = ShawarmaOrange.copy(alpha = .14f)) {
                                        Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = ShawarmaOrange, fontWeight = FontWeight.Black)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(guide.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    HtmlCompat.fromHtml(guide.contentHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim(),
                                    color = Color(0xFF4B5563),
                                    lineHeight = 21.sp
                                )
                                GuideImages(guide.imageUrl, guide.title)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideImages(rawValue: String?, title: String) {
    val urls = remember(rawValue) {
        if (rawValue.isNullOrBlank()) emptyList() else try {
            val array = JSONArray(rawValue)
            List(array.length()) { index -> array.optJSONObject(index)?.optString("url").orEmpty() }.filter { it.isNotBlank() }
        } catch (_: Exception) { listOf(rawValue) }
    }
    urls.forEach { url ->
        AsyncImage(
            model = url,
            contentDescription = "Ilustrasi $title",
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
        )
    }
}

@Composable
private fun EmptyGuideState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Book, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(48.dp))
            Text(message, color = Color(0xFF6B7280))
            OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
        }
    }
}
