package com.sukashawarma.pos.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object GlobalEventBus {
    val ownerMessageRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val targetRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val orderSyncEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val isRealtimeConnected = MutableStateFlow(false)
    val bypassRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
