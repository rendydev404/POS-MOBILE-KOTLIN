package com.sukashawarma.pos.presentation.order_manual

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.CreateOrderItemPayload
import com.sukashawarma.pos.data.remote.dto.CreateOrderPayload
import com.sukashawarma.pos.domain.model.*
import com.sukashawarma.pos.domain.usecase.CalculateCartUseCase
import com.sukashawarma.pos.domain.usecase.CreateOrderUseCase
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class POSManualOrderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val calculateCartUseCase = CalculateCartUseCase()
    private val createOrderUseCase = CreateOrderUseCase(calculateCartUseCase)
    private val api = SupabaseClient.api
    private val repository = (application as POSApplication).menuRepository
    private val gson = Gson()

    val currentOutletId = MutableStateFlow("")
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val isLoading = MutableStateFlow(false)

    val selectedCategoryId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")

    val cartItems = MutableStateFlow<List<OrderItem>>(emptyList())
    val customerName = MutableStateFlow("")
    val activePromos = MutableStateFlow<List<Promo>>(emptyList())

    val isPaymentModalOpen = MutableStateFlow(false)
    val orderErrorMessage = MutableStateFlow<String?>(null)

    init {
        collectMenuSnapshot()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectMenuSnapshot() {
        viewModelScope.launch {
            currentOutletId.filter { it.isNotBlank() }
                .flatMapLatest { outletId -> repository.snapshot(outletId) }
                .collect { snapshot ->
                    categories.value = snapshot.categories
                    if (snapshot.categories.isNotEmpty() && selectedCategoryId.value.isEmpty()) {
                        selectedCategoryId.value = snapshot.categories.first().id
                    }
                    menuItems.value = snapshot.items
                    isLoading.value = false
                }
        }
    }

    fun addToCart(menuItem: MenuItem, note: String = "") {
        val currentList = cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.menuItemId == menuItem.id && it.note == note }

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val updated = existing.copy(
                quantity = existing.quantity + 1,
                subtotal = (existing.quantity + 1) * existing.unitPrice
            )
            currentList[existingIndex] = updated
        } else {
            currentList.add(
                OrderItem(
                    menuItemId = menuItem.id,
                    name = menuItem.name,
                    quantity = 1,
                    unitPrice = menuItem.price,
                    subtotal = menuItem.price,
                    note = note
                )
            )
        }
        cartItems.value = currentList
    }

    fun updateQuantity(item: OrderItem, delta: Int) {
        val currentList = cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val newQty = item.quantity + delta
            if (newQty <= 0) {
                currentList.removeAt(index)
            } else {
                currentList[index] = item.copy(
                    quantity = newQty,
                    subtotal = newQty * item.unitPrice
                )
            }
            cartItems.value = currentList
        }
    }

    fun clearCart() {
        cartItems.value = emptyList()
        customerName.value = ""
    }

    private fun isNetworkAvailable(): Boolean {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Application.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun processOrder(paymentMethod: PaymentMethod, amountReceived: Double) {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) {
            orderErrorMessage.value = "Outlet belum siap, silakan login ulang."
            return
        }

        viewModelScope.launch {
            val online = isNetworkAvailable()
            val maxOffline = orderDao.getMaxOfflineOrderNumber(outletId) ?: 9000
            val maxServer = orderDao.getMaxServerOrderNumber(outletId) ?: 0

            val order = createOrderUseCase.execute(
                outletId = outletId,
                customerName = customerName.value,
                items = cartItems.value,
                paymentMethod = paymentMethod,
                amountReceived = amountReceived,
                activePromos = activePromos.value,
                isOnline = online,
                lastServerOrderNumber = maxServer,
                lastOfflineOrderNumber = maxOffline
            )

            var pendingSync = !online
            var serverOrderNumber = order.orderNumber

            if (online) {
                try {
                    val createdAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(order.createdAt))
                    val payload = CreateOrderPayload(
                        id = order.id,
                        outletId = order.outletId,
                        customerName = order.customerName,
                        status = order.status.name.lowercase(),
                        source = order.source.name.lowercase(),
                        paymentMethod = order.paymentMethod.name.lowercase(),
                        discountAmount = order.discountAmount,
                        totalAmount = order.totalAmount,
                        amountReceived = order.amountReceived,
                        changeAmount = order.changeAmount,
                        createdAt = createdAtIso
                    )
                    val orderRes = api.createOrder(payload)
                    if (orderRes.isSuccessful && !orderRes.body().isNullOrEmpty()) {
                        serverOrderNumber = orderRes.body()!!.first().orderNumber

                        val itemPayloads = order.items.map { item ->
                            CreateOrderItemPayload(
                                orderId = order.id,
                                menuItemId = item.menuItemId,
                                menuItemName = item.name,
                                quantity = item.quantity,
                                unitPrice = item.unitPrice,
                                subtotal = item.subtotal
                            )
                        }
                        api.createOrderItems(itemPayloads)
                    } else {
                        pendingSync = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingSync = true
                }
            }

            val entity = LocalOrderEntity(
                id = order.id,
                outletId = order.outletId,
                orderNumber = serverOrderNumber,
                customerName = order.customerName,
                status = order.status.name,
                source = order.source.name,
                paymentMethod = order.paymentMethod.name,
                itemsJson = gson.toJson(order.items),
                subtotal = order.subtotal,
                discountAmount = order.discountAmount,
                totalAmount = order.totalAmount,
                amountReceived = order.amountReceived,
                changeAmount = order.changeAmount,
                kitchenReceiptPrinted = false,
                createdAt = order.createdAt,
                isPendingSync = pendingSync
            )

            orderDao.insertOrder(entity)
            orderErrorMessage.value = if (pendingSync) {
                "Order tersimpan lokal (offline) dengan nomor sementara #${entity.orderNumber}. " +
                    "Auto-sync ke server belum aktif di versi ini — sinkronkan manual saat online."
            } else {
                null
            }
            clearCart()
            isPaymentModalOpen.value = false
        }
    }
}
