package com.sukashawarma.pos.presentation.menu_management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.domain.model.Category
import com.sukashawarma.pos.domain.model.MenuItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MenuManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private val repository = (application as POSApplication).menuRepository

    private val currentOutletId = MutableStateFlow("")

    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
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
            val newStatus = !item.isAvailable
            // Optimistic local update
            val currentList = menuItems.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                currentList[index] = item.copy(isAvailable = newStatus)
                menuItems.value = currentList
            }

            try {
                api.updateMenuItemAvailability("eq.${item.id}", mapOf("is_available" to newStatus))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
