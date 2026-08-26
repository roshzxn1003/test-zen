package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Zenith Premium Dark Glassmorphism Palette
val SlateDarkBackground = Color(0xFF070B14)      // Deep ambient navy/near-black
val SlateDarkSurface = Color(0xFF0F172A)         // Primary surface base
val SlateDarkSurfaceVariant = Color(0xFF1E293B)  // Container elevation
val SlateDarkBorder = Color(0xFF1E293B)          // Glass border base

val SlateDarkTextPrimary = Color(0xFFF8FAFC)     // Crisp white text
val SlateDarkTextSecondary = Color(0xFF94A3B8)   // Muted blue-gray
val SlateDarkTextMuted = Color(0xFF64748B)       // Tertiary captions

// Glassmorphism Token Specifications
val GlassCardBg = Color(0x14FFFFFF)              // 8% white glass
val GlassCardBgHover = Color(0x1FFFFFFF)         // 12% white glass hover
val GlassBorderColor = Color(0x26FFFFFF)         // 15% subtle border
val GlassBorderHighlight = Color(0x406366F1)     // Electric indigo highlight
val GlassSurfaceDark = Color(0x990F172A)         // 60% surface glass

// Primary Brand Colors - Electric Indigo & Cyan
val EmeraldDarkPrimary = Color(0xFF6366F1)       // Electric Indigo primary
val EmeraldDarkContainer = Color(0xFF1E1B4B)     // Deep indigo container
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldOnContainer = Color(0xFFE0E7FF)

val GoldAccent = Color(0xFF06B6D4)               // Electric Cyan accent
val CyanDarkSecondary = Color(0xFF06B6D4)        // Electric Cyan secondary
val GoldContainer = Color(0xFF083344)
val GoldOnContainer = Color(0xFFCFFAFE)

// Semantic Financial Indicators
val IncomeGreen = Color(0xFF10B981)             // Emerald Green
val IncomeGreenContainer = Color(0xFF064E3B)    // Dark green container
val IncomeGreenOnContainer = Color(0xFFA7F3D0)

val ExpenseRed = Color(0xFFF43F5E)              // Vibrant Rose Red
val ExpenseRedContainer = Color(0xFF881337)     // Dark red container
val ExpenseRedOnContainer = Color(0xFFFECDD3)

val GoalAmber = Color(0xFFF59E0B)               // Amber Gold for savings & warnings
val GoalAmberContainer = Color(0xFF78350F)
val GoalAmberOnContainer = Color(0xFFFEF3C7)

// Premium Ambient & Glass Gradient Brushes
val AmbientBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0B1120), // Deep ambient indigo glow
        Color(0xFF070B14), // Near-black navy
        Color(0xFF05080F)  // Deep floor
    )
)

val HeroCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E1B4B), // Electric Indigo
        Color(0xFF0F172A), // Slate Navy
        Color(0xFF070B14)  // Base glass dark
    )
)

val AiCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF311B92), // Purple Glow
        Color(0xFF1A237E), // Deep Indigo
        Color(0xFF0E7490)  // Cyan horizon
    )
)

val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.25f),
        Color(0xFF6366F1).copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.08f)
    )
)
