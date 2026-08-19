import re

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\dashboard\DashboardScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add EqualHeightRow at the end of the file
equal_height_row = '''
@Composable
fun EqualHeightRow(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    spacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        
        val initialPlaceables = measurables.map {
            it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        }
        
        val maxHeight = initialPlaceables.maxOfOrNull { it.height } ?: 0
        
        val finalPlaceables = measurables.map {
            it.measure(constraints.copy(minHeight = maxHeight, maxHeight = maxHeight))
        }
        
        val totalWidth = finalPlaceables.sumOf { it.width } + kotlin.math.max(0, finalPlaceables.size - 1) * spacingPx
        val actualWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else totalWidth
        
        layout(actualWidth, maxHeight) {
            var x = 0
            finalPlaceables.forEach { placeable ->
                placeable.placeRelative(x = x, y = 0)
                x += placeable.width + spacingPx
            }
        }
    }
}
'''
if "fun EqualHeightRow" not in text:
    text += equal_height_row

# Replace Row with EqualHeightRow
old_row = '''                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .verticalScroll(scrollState)
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {'''
new_row = '''                    EqualHeightRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .verticalScroll(scrollState),
                        spacing = 12.dp
                    ) {'''
text = text.replace(old_row, new_row)

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\dashboard\DashboardScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)

