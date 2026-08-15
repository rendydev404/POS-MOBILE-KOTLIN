package com.sukashawarma.pos.presentation.components

import android.content.Context
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class NormalizedOverlayPosition(val x: Float, val y: Float) {
    fun clamped() = NormalizedOverlayPosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

internal data class OverlayTravel(val maxX: Float, val maxY: Float)

internal fun overlayTravel(
    containerWidth: Int,
    containerHeight: Int,
    childWidth: Int,
    childHeight: Int,
    edgeMargin: Float
) = OverlayTravel(
    maxX = (containerWidth - childWidth - edgeMargin * 2).coerceAtLeast(0f),
    maxY = (containerHeight - childHeight - edgeMargin * 2).coerceAtLeast(0f)
)

internal fun denormalizeOverlayPosition(
    normalized: NormalizedOverlayPosition,
    travel: OverlayTravel,
    edgeMargin: Float
): Offset {
    val safe = normalized.clamped()
    return Offset(edgeMargin + safe.x * travel.maxX, edgeMargin + safe.y * travel.maxY)
}

internal fun normalizeOverlayPosition(
    position: Offset,
    travel: OverlayTravel,
    edgeMargin: Float
): NormalizedOverlayPosition = NormalizedOverlayPosition(
    x = if (travel.maxX == 0f) 0f else (position.x - edgeMargin) / travel.maxX,
    y = if (travel.maxY == 0f) 0f else (position.y - edgeMargin) / travel.maxY
).clamped()

internal fun clampOverlayPosition(
    position: Offset,
    travel: OverlayTravel,
    edgeMargin: Float
): Offset = Offset(
    x = position.x.coerceIn(edgeMargin, edgeMargin + travel.maxX),
    y = position.y.coerceIn(edgeMargin, edgeMargin + travel.maxY)
)

private object UpdateOverlayPositionStore {
    private const val PREFS = "update_indicator_position"
    private const val X = "normalized_x"
    private const val Y = "normalized_y"

    fun load(context: Context): NormalizedOverlayPosition {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NormalizedOverlayPosition(
            x = prefs.getFloat(X, 1f),
            y = prefs.getFloat(Y, 1f)
        ).clamped()
    }

    fun save(context: Context, position: NormalizedOverlayPosition) {
        val safe = position.clamped()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(X, safe.x)
            .putFloat(Y, safe.y)
            .apply()
    }
}

/**
 * Full-screen positioning layer whose empty area remains non-interactive. Only
 * the update pill owns drag gestures, so the POS beneath it stays usable.
 */
@Composable
fun DraggableUpdateOverlay(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val edgeMargin = with(LocalDensity.current) { 12.dp.toPx() }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var childSize by remember { mutableStateOf(IntSize.Zero) }
    var normalized by remember { mutableStateOf(UpdateOverlayPositionStore.load(context)) }
    var position by remember { mutableStateOf(Offset.Zero) }

    val travel = overlayTravel(
        containerWidth = containerSize.width,
        containerHeight = containerSize.height,
        childWidth = childSize.width,
        childHeight = childSize.height,
        edgeMargin = edgeMargin
    )

    LaunchedEffect(containerSize, childSize, normalized, edgeMargin) {
        if (containerSize != IntSize.Zero && childSize != IntSize.Zero) {
            position = denormalizeOverlayPosition(normalized, travel, edgeMargin)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }
                .onSizeChanged { childSize = it }
                .pointerInput(containerSize, childSize, edgeMargin) {
                    detectDragGestures(
                        onDragEnd = {
                            normalized = normalizeOverlayPosition(position, travel, edgeMargin)
                            UpdateOverlayPositionStore.save(context, normalized)
                        },
                        onDragCancel = {
                            normalized = normalizeOverlayPosition(position, travel, edgeMargin)
                            UpdateOverlayPositionStore.save(context, normalized)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        position = clampOverlayPosition(position + dragAmount, travel, edgeMargin)
                    }
                },
            content = content
        )
    }
}
