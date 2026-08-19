import re

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Line 283: Logo text
old_logo = '''                if (!isCollapsed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SUKA SHAWARMA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MarqueeRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }'''
new_logo = '''                Column(modifier = Modifier.graphicsLayer { alpha = textAlpha }.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SUKA SHAWARMA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MarqueeRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }'''
text = text.replace(old_logo, new_logo)

# Line 433: SubItems (in accordion or dropdown?)
old_subitem = '''                                if (!isCollapsed) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = subItem.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (subItem.tab == activeTab) FontWeight.Bold else FontWeight.Medium,
                                        color = if (subItem.tab == activeTab) ActiveTextColor else InactiveTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }'''
new_subitem = '''                                Row(modifier = Modifier.graphicsLayer { alpha = textAlpha; clip = true }.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = subItem.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (subItem.tab == activeTab) FontWeight.Bold else FontWeight.Medium,
                                        color = if (subItem.tab == activeTab) ActiveTextColor else InactiveTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }'''
text = text.replace(old_subitem, new_subitem)

# Line 675: Stok Outlet button
old_stok = '''                        if (!isCollapsed) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Stok Outlet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }'''
new_stok = '''                        Row(modifier = Modifier.graphicsLayer { alpha = textAlpha; clip = true }.fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Stok Outlet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }'''
text = text.replace(old_stok, new_stok)


with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'w', encoding='utf-8') as f:
    f.write(text)
