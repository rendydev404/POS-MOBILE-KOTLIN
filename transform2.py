import re

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace Portal
old_portal = '''                    if (!isCollapsed) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Portal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
new_portal = '''                    Row(modifier = Modifier.graphicsLayer { alpha = textAlpha; clip = true }.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Portal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
text = text.replace(old_portal, new_portal)

# Replace Printer
old_printer = '''                    if (!isCollapsed) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Koneksi Printer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
new_printer = '''                    Row(modifier = Modifier.graphicsLayer { alpha = textAlpha; clip = true }.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Koneksi Printer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
text = text.replace(old_printer, new_printer)

# Replace Keluar
old_keluar = '''                    if (!isCollapsed) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Keluar", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA43C26), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
new_keluar = '''                    Row(modifier = Modifier.graphicsLayer { alpha = textAlpha; clip = true }.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Keluar", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA43C26), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }'''
text = text.replace(old_keluar, new_keluar)

# Check for any other if (!isCollapsed)
count = text.count("if (!isCollapsed)")
print(f"Remaining if (!isCollapsed): {count}")

with open(r'd:\PROJECT-APPS-NATIVE\POS\app\src\main\java\com\sukashawarma\pos\presentation\components\SideNavRail.kt', 'w', encoding='utf-8') as f:
    f.write(text)

