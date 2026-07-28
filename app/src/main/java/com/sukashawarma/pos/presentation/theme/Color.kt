package com.sukashawarma.pos.presentation.theme

import androidx.compose.ui.graphics.Color

// Web Exact Warm Color Palette (Matching pos.sukashawarma.com/kasir)
val ShawarmaOrange = Color(0xFFE67E22)
val ShawarmaOrangeDark = Color(0xFFD35400)
val ShawarmaOrangeLight = Color(0xFFFFF3E0)

val CreamBackground = Color(0xFFFAF7F2)
val CreamSurface = Color(0xFFFFFFFF)
val CreamCard = Color(0xFFFFFFFF)
val CreamBorder = Color(0xFFE8E3DA)

val MarqueeRed = Color(0xFFDC2626)
val TargetPink = Color(0xFFFFF1F2)

val StatusPending = Color(0xFFEF4444)
val StatusPreparing = Color(0xFF3B82F6)
val StatusCompleted = Color(0xFF10B981)

val TextDarkPrimary = Color(0xFF1C1917)
val TextDarkSecondary = Color(0xFF78716C)
val TextDarkMuted = Color(0xFFA8A29E)

// Backward compatibility alias variables
val AmberPrimary = ShawarmaOrange
val AmberDark = ShawarmaOrangeDark
val AmberLight = ShawarmaOrangeLight

val SlateBackground = CreamBackground
val SlateSurface = CreamSurface
val SlateCard = CreamCard
val SlateBorder = CreamBorder
val TextPrimary = TextDarkPrimary
val TextSecondary = TextDarkSecondary
val TextMuted = TextDarkMuted

// Vanilla Tailwind palette (order-manual/page.tsx, WalkInCartPanel.tsx, QrisPaymentModal.tsx use
// plain `amber-500`/`blue-500`/`emerald-500`/`purple-500`/`gray-*` utility classes — a different,
// un-themed palette from the custom brand colors above, which the rest of the app's screens use).
val TwAmber50 = Color(0xFFFFFBEB)
val TwAmber100 = Color(0xFFFEF3C7)
val TwAmber400 = Color(0xFFFBBF24)
val TwAmber500 = Color(0xFFF59E0B)
val TwAmber600 = Color(0xFFD97706)

val TwBlue50 = Color(0xFFEFF6FF)
val TwBlue100 = Color(0xFFDBEAFE)
val TwBlue400 = Color(0xFF60A5FA)
val TwBlue500 = Color(0xFF3B82F6)
val TwBlue600 = Color(0xFF2563EB)
val TwBlue900 = Color(0xFF1E3A8A)

val TwEmerald50 = Color(0xFFECFDF5)
val TwEmerald100 = Color(0xFFD1FAE5)
val TwEmerald500 = Color(0xFF10B981)
val TwEmerald600 = Color(0xFF059669)
val TwEmerald700 = Color(0xFF047857)

val TwPurple50 = Color(0xFFFAF5FF)
val TwPurple500 = Color(0xFFA855F7)
val TwPurple600 = Color(0xFF9333EA)
val TwPurple700 = Color(0xFF7E22CE)

val TwRed50 = Color(0xFFFEF2F2)
val TwRed100 = Color(0xFFFEE2E2)
val TwRed200 = Color(0xFFFECACA)
val TwRed500 = Color(0xFFEF4444)
val TwRed600 = Color(0xFFDC2626)

val TwGray50 = Color(0xFFF9FAFB)
val TwGray100 = Color(0xFFF3F4F6)
val TwGray200 = Color(0xFFE5E7EB)
val TwGray300 = Color(0xFFD1D5DB)
val TwGray400 = Color(0xFF9CA3AF)
val TwGray500 = Color(0xFF6B7280)
val TwGray600 = Color(0xFF4B5563)
val TwGray700 = Color(0xFF374151)
val TwGray800 = Color(0xFF1F2937)
val TwGray900 = Color(0xFF111827)
