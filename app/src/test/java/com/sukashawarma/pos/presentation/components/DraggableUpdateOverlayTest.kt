package com.sukashawarma.pos.presentation.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class DraggableUpdateOverlayTest {
    @Test
    fun `position is clamped inside every screen edge`() {
        val travel = overlayTravel(1000, 600, 200, 100, 12f)

        assertEquals(Offset(12f, 12f), clampOverlayPosition(Offset(-50f, -10f), travel, 12f))
        assertEquals(
            Offset(788f, 488f),
            clampOverlayPosition(Offset(5000f, 5000f), travel, 12f)
        )
    }

    @Test
    fun `normalized position survives screen size changes`() {
        val normalized = NormalizedOverlayPosition(0.25f, 0.75f)
        val firstTravel = overlayTravel(1000, 600, 200, 100, 12f)
        val first = denormalizeOverlayPosition(normalized, firstTravel, 12f)
        assertEquals(normalized, normalizeOverlayPosition(first, firstTravel, 12f))

        val secondTravel = overlayTravel(1400, 900, 260, 120, 12f)
        val second = denormalizeOverlayPosition(normalized, secondTravel, 12f)
        assertEquals(normalized, normalizeOverlayPosition(second, secondTravel, 12f))
    }
}
