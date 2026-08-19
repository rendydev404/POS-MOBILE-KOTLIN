import re
with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add textAlpha
text = text.replace(
    'val width by animateDpAsState(targetValue = if (isCollapsed) 80.dp else 256.dp, animationSpec = tween(200), label = "width")',
    'val width by animateDpAsState(targetValue = if (isCollapsed) 80.dp else 256.dp, animationSpec = tween(200), label = "width")\n    val textAlpha by animateFloatAsState(targetValue = if (isCollapsed) 0f else 1f, animationSpec = tween(200), label = "alpha")'
)

# 2. Replace import androidx.compose.animation.AnimatedVisibility with graphicsLayer
if 'import androidx.compose.ui.graphics.graphicsLayer' not in text:
    text = text.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer')

# 3. Handle MenuItem list
old_menu_item = '''                                if (!isCollapsed) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = menuItem.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) ActiveTextColor else InactiveTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }'''

new_menu_item = '''                                Row(modifier = Modifier.graphicsLayer { alpha = textAlpha }.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = menuItem.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) ActiveTextColor else InactiveTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }'''
text = text.replace(old_menu_item, new_menu_item)

# 4. Handle MenuItem Dropdown arrow
old_dropdown = '''                            if (!isCollapsed && hasSubItems) {
                                val rotation by animateFloatAsState(if (isDropdownExpanded) 180f else 0f, label = "rotate")
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = InactiveTextColor,
                                    modifier = Modifier.size(14.dp).rotate(rotation)
                                )
                            }'''
new_dropdown = '''                            if (hasSubItems) {
                                val rotation by animateFloatAsState(if (isDropdownExpanded) 180f else 0f, label = "rotate")
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = InactiveTextColor,
                                    modifier = Modifier.size(14.dp).rotate(rotation).graphicsLayer { alpha = textAlpha }
                                )
                            }'''
text = text.replace(old_dropdown, new_dropdown)

# 5. Bottom items (Portal, Printer, Keluar)
def replace_bottom_item(label, color="InactiveTextColor", bold="False"):
    global text
    weight = "FontWeight.Bold" if bold == "True" else "FontWeight.Medium"
    old = f'''                    if (!isCollapsed) {{
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("{label}", style = MaterialTheme.typography.bodyMedium, color = {color}, fontWeight = {weight}, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }}'''
    new = f'''                    Row(modifier = Modifier.graphicsLayer {{ alpha = textAlpha }}.fillMaxWidth()) {{
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("{label}", style = MaterialTheme.typography.bodyMedium, color = {color}, fontWeight = {weight}, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }}'''
    text = text.replace(old, new)

replace_bottom_item("Portal")
replace_bottom_item("Printer")
replace_bottom_item("Keluar", color="Color(0xFFA43C26)", bold="True")

# 6. Profile section
old_profile = '''                    if (isCollapsed) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Green, CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }
                if (!isCollapsed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Kasir Utama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ActiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Shift Pagi", style = MaterialTheme.typography.bodySmall, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }'''

new_profile = '''                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Green, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd)
                            .graphicsLayer { alpha = 1f - textAlpha }
                    )
                }
                Column(modifier = Modifier.graphicsLayer { alpha = textAlpha }.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Kasir Utama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ActiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Shift Pagi", style = MaterialTheme.typography.bodySmall, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }'''
text = text.replace(old_profile, new_profile)


# 7. AnimatedVisibility for Expanded Mode Accordion
# This one SHOULD remain AnimatedVisibility because it's a dropdown expansion (height animation),
# but let's check if it's nested inside if (!isCollapsed)
old_accordion = '''                    if (!isCollapsed && hasSubItems) {
                        AnimatedVisibility(visible = isDropdownExpanded) {'''
new_accordion = '''                    if (hasSubItems) {
                        AnimatedVisibility(visible = isDropdownExpanded) {'''
text = text.replace(old_accordion, new_accordion)
# Wait, if hasSubItems and isDropdownExpanded, but isCollapsed goes to true, we should hide it?
# The original logic hid it entirely using if (!isCollapsed && hasSubItems). If we remove isCollapsed, it'll stay visible.
# But we added Row wrapper with 	extAlpha to other things. For the accordion, we can just add .graphicsLayer { alpha = textAlpha } to the Column inside.
old_accordion_col = '''                            Column(modifier = Modifier.padding(start = 44.dp, end = 8.dp)) {'''
new_accordion_col = '''                            Column(modifier = Modifier.padding(start = 44.dp, end = 8.dp).graphicsLayer { alpha = textAlpha }) {'''
text = text.replace(old_accordion_col, new_accordion_col)


with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'w', encoding='utf-8') as f:
    f.write(text)

