package com.sukashawarma.pos.presentation.menu_management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.domain.menu.KioskSettings
import com.sukashawarma.pos.domain.model.Category
import com.sukashawarma.pos.domain.model.MenuItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MenuManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as POSApplication).menuRepository

    private val currentOutletId = MutableStateFlow("")

    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val kioskSettings = MutableStateFlow(KioskSettings.EMPTY)
    val selectedCategoryId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val isLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            currentOutletId.filter { it.isNotBlank() }
                .flatMapLatest { outletId -> repository.snapshot(outletId) }
                .collect { snapshot ->
                    categories.value = snapshot.categories
                    menuItems.value = snapshot.items
                    kioskSettings.value = snapshot.settings
                    isLoading.value = false
                }
        }
    }

    fun setOutlet(outletId: String) {
        isLoading.value = outletId.isNotBlank() && categories.value.isEmpty()
        currentOutletId.value = outletId
    }

    fun toggleAvailability(item: MenuItem) {
        viewModelScope.launch {
            repository.toggleAvailability(item, currentOutletId.value, kioskSettings.value.unavailableIds)
        }
    }

    fun toggleSettingMembership(key: String, itemId: String) {
        viewModelScope.launch {
            val currentIds = when (key) {
                "bestseller_ids" -> kioskSettings.value.bestsellers
                "upsell_ids" -> kioskSettings.value.upsells
                "recommendation_ids" -> kioskSettings.value.recommendations
                "force_available_menu_ids" -> kioskSettings.value.forceAvailableIds
                else -> emptySet()
            }
            repository.toggleSettingMembership(key, itemId, currentOutletId.value, currentIds)
        }
    }
}
