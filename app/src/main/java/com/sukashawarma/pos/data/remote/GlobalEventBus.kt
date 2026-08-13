package com.sukashawarma.pos.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

// DROP_OLDEST: tryEmit tidak pernah gagal walau collector masih sibuk fetch.
// Dengan SUSPEND (default) event realtime yang datang beruntun ikut terbuang
// dan layar tidak jadi ter-refresh.
private fun event() = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

object GlobalEventBus {
    val ownerMessageRefreshEvent = event()
    val targetRefreshEvent = event()
    val orderSyncEvent = event()
    val isRealtimeConnected = MutableStateFlow(false)
    val bypassRequestEvent = event()
    val gateRefreshEvent = event()
    val pettyCashEvent = event()
    val stockEvent = event()
}
