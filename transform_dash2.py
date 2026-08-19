import re

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\dashboard\DashboardScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace weight(1f) in EqualHeightRow children
old_children = '''                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { column1() }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { column2() }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { column3() }
                    }'''
new_children = '''                    ) {
                        Box(modifier = Modifier.fillMaxHeight()) { column1() }
                        Box(modifier = Modifier.fillMaxHeight()) { column2() }
                        Box(modifier = Modifier.fillMaxHeight()) { column3() }
                    }'''
text = text.replace(old_children, new_children)

# Update EqualHeightRow to split width equally
old_layout = '''        val initialPlaceables = measurables.map {
            it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        }'''
new_layout = '''        // Split width equally
        val childWidth = if (constraints.hasBoundedWidth && measurables.isNotEmpty()) {
            (constraints.maxWidth - (measurables.size - 1) * spacingPx) / measurables.size
        } else {
            constraints.maxWidth
        }
        
        val childConstraints = constraints.copy(
            minWidth = childWidth,
            maxWidth = childWidth,
            minHeight = 0,
            maxHeight = androidx.compose.ui.unit.Constraints.Infinity
        )
        
        val initialPlaceables = measurables.map {
            it.measure(childConstraints)
        }'''
text = text.replace(old_layout, new_layout)

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\dashboard\DashboardScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)

