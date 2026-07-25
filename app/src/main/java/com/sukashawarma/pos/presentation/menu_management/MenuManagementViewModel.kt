package com.sukashawarma.pos.presentation.menu_management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.domain.model.Category
import com.sukashawarma.pos.domain.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MenuManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val selectedCategoryId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val isLoading = MutableStateFlow(false)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val catRes = api.getCategories()
                if (catRes.isSuccessful && catRes.body() != null) {
                    categories.value = catRes.body()!!.map { Category(it.id, it.name) }
                }

                val itemRes = api.getMenuItems()
                if (itemRes.isSuccessful && itemRes.body() != null) {
                    menuItems.value = itemRes.body()!!.map { dto ->
                        MenuItem(
                            id = dto.id,
                            categoryId = dto.categoryId ?: "",
                            name = dto.name,
                            price = dto.price,
                            isAvailable = dto.isAvailable ?: true,
                            prepTimeMinutes = 10,
                            imageUrl = dto.imageUrl
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
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
