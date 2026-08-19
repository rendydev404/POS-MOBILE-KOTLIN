import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import kotlin.math.max

@Composable
fun EqualHeightRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        
        // 1. Measure all children with infinite height
        val initialPlaceables = measurables.map {
            it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        }
        
        // 2. Find the max height
        val maxHeight = initialPlaceables.maxOfOrNull { it.height } ?: 0
        
        // 3. Remeasure all children with the exact max height
        val finalPlaceables = measurables.map {
            it.measure(constraints.copy(minHeight = maxHeight, maxHeight = maxHeight))
        }
        
        // 4. Calculate total width
        val totalWidth = finalPlaceables.sumOf { it.width } + (finalPlaceables.size - 1) * spacingPx
        val actualWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else totalWidth
        
        // 5. Place children
        layout(actualWidth, maxHeight) {
            var x = 0
            finalPlaceables.forEach { placeable ->
                placeable.placeRelative(x = x, y = 0)
                x += placeable.width + spacingPx
            }
        }
    }
}
