package com.sukashawarma.pos.presentation.guide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.SystemGuideDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GuideViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    private val _guides = MutableStateFlow<List<SystemGuideDto>>(emptyList())
    val guides = _guides.asStateFlow()
    val selectedCategory = MutableStateFlow("")
    val isLoading = MutableStateFlow(true)
    val errorMessage = MutableStateFlow<String?>(null)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        isLoading.value = true
        errorMessage.value = null
        try {
            val response = api.getSystemGuides()
            if (!response.isSuccessful) {
                errorMessage.value = "Panduan belum dapat dimuat. Coba lagi."
                return@launch
            }
            _guides.value = response.body().orEmpty().sortedWith(
                compareBy<SystemGuideDto> { it.category }.thenBy { it.sortOrder ?: Int.MAX_VALUE }
            )
            if (selectedCategory.value.isBlank()) {
                selectedCategory.value = _guides.value.firstOrNull()?.category.orEmpty()
            }
        } catch (_: Exception) {
            errorMessage.value = "Tidak dapat menghubungi server panduan."
        } finally {
            isLoading.value = false
        }
    }

    fun selectCategory(category: String) { selectedCategory.value = category }
}
