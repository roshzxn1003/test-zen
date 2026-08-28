package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// =========================================================================
// ZENITH CLEAN MINIMALIST DESIGN SYSTEM & COLOR PALETTE
// =========================================================================

// --- Dark Surface Architecture (Apple OLED & Carbon) ---
val SlateDarkBackground = Color(0xFF090D16)      // Deep ambient slate base
val SlateDarkSurface = Color(0xFF0F172A)         // Primary card surface
val SlateDarkSurfaceVariant = Color(0xFF1E293B)  // Elevated container / input fields
val SlateDarkBorder = Color(0xFF334155)          // Clean outline border
val SlateDarkBorderSubtle = Color(0x2E94A3B8)    // 18% subtle glass border

// --- Typography Tokens ---
val SlateDarkTextPrimary = Color(0xFFF8FAFC)     // Ultra-crisp primary text
val SlateDarkTextSecondary = Color(0xFF94A3B8)   // Muted slate subtitle
val SlateDarkTextMuted = Color(0xFF64748B)       // Tertiary captions & placeholders

// --- Minimalist Glass & Elevation Tokens ---
val GlassCardBg = Color(0x18FFFFFF)              // 9.5% clean translucent glass
val GlassCardBgElevated = Color(0x24FFFFFF)      // 14% elevated glass
val GlassCardBgHover = Color(0x2EFFFFFF)         // 18% hover
val GlassBorderColor = Color(0x2BFFFFFF)         // 17% crisp border
val GlassBorderHighlight = Color(0x50818CF8)     // Soft pastel indigo glow
val GlassSurfaceDark = Color(0xCC0F172A)         // 80% surface glass

// --- Brand Primary (Clean Indigo & Soft Lavender) ---
val EmeraldDarkPrimary = Color(0xFF6366F1)       // Electric Indigo primary
val EmeraldDarkContainer = Color(0xFF1E1B4B)     // Deep indigo container
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldOnContainer = Color(0xFFE0E7FF)
val PastelIndigo = Color(0xFF818CF8)
val PastelIndigoContainer = Color(0x26818CF8)

// --- Secondary Accent (Mint Cyan & Soft Teal) ---
val GoldAccent = Color(0xFF06B6D4)               // Clean Cyan accent
val CyanDarkSecondary = Color(0xFF06B6D4)
val GoldContainer = Color(0xFF083344)
val GoldOnContainer = Color(0xFFCFFAFE)
val PastelCyan = Color(0xFF22D3EE)
val PastelCyanContainer = Color(0x2622D3EE)

// --- Semantic Financial Indicators (Clean Pastels) ---
val IncomeGreen = Color(0xFF10B981)              // Mint Emerald
val IncomeGreenContainer = Color(0xFF064E3B)
val IncomeGreenOnContainer = Color(0xFFA7F3D0)
val PastelGreen = Color(0xFF34D399)
val PastelGreenContainer = Color(0x2634D399)

val ExpenseRed = Color(0xFFF43F5E)               // Coral Rose Red
val ExpenseRedContainer = Color(0xFF881337)
val ExpenseRedOnContainer = Color(0xFFFECDD3)
val PastelRose = Color(0xFFFB7185)
val PastelRoseContainer = Color(0x26FB7185)

val GoalAmber = Color(0xFFF59E0B)                // Warm Sunset Amber
val GoalAmberContainer = Color(0xFF78350F)
val GoalAmberOnContainer = Color(0xFFFEF3C7)
val PastelAmber = Color(0xFFFBBF24)
val PastelAmberContainer = Color(0x26FBBF24)

val PastelPurple = Color(0xFFA78BFA)             // Soft Purple for AI & Analytics
val PastelPurpleContainer = Color(0x26A78BFA)

// --- Premium Ambient & Gradient Brushes ---
val AmbientBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0E1322), // Soft ambient indigo zenith
        Color(0xFF090D16), // Slate carbon base
        Color(0xFF060910)  // Deep floor
    )
)

val HeroCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E1B4B), // Electric Indigo core
        Color(0xFF0F172A), // Slate Navy
        Color(0xFF090D16)  // Base dark
    )
)

val FamilyHeroCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0E3A4A), // Deep Cyan Teal
        Color(0xFF0F172A), // Slate Navy
        Color(0xFF090D16)  // Base dark
    )
)

val AiCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2E1065), // Deep Violet
        Color(0xFF1E1B4B), // Indigo
        Color(0xFF0E7490)  // Cyan
    )
)

val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.28f),
        Color(0xFF818CF8).copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.08f)
    )
)
