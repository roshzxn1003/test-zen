package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.ZenithLogo
import com.example.ui.theme.*

data class SplashFeature(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val badge: String,
    val accentColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val features = remember {
        listOf(
            SplashFeature(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Next-Gen Wealth Ledger",
                description = "Track income, expenses, and savings in real time across multiple currencies with comprehensive analytics.",
                badge = "PERSONAL & FAMILY",
                accentColor = Color(0xFF6366F1) // Electric Indigo
            ),
            SplashFeature(
                icon = Icons.Default.ReceiptLong,
                title = "Smart Receipt AI & OCR",
                description = "Scan store, dining, or grocery bills with camera OCR to automatically extract full itemized lists, taxes, and discounts.",
                badge = "ITEMIZED RECOGNITION",
                accentColor = Color(0xFF06B6D4) // Cyan
            ),
            SplashFeature(
                icon = Icons.Default.Groups,
                title = "Collaborative Family Vaults",
                description = "Share household budgets, view individual member contributions, and manage shared goals effortlessly.",
                badge = "ROLE PERMISSIONS",
                accentColor = Color(0xFF10B981) // Emerald
            ),
            SplashFeature(
                icon = Icons.Default.Mic,
                title = "Voice Expense Assistant",
                description = "Speak your expenses naturally in English or regional languages. Zenith AI extracts the title, category, and amount.",
                badge = "MULTILINGUAL AI",
                accentColor = Color(0xFFF59E0B) // Amber
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { features.size })

    // Pulse animation for logo glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AmbientBackgroundBrush)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("zenith_splash_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BRANDING HEADER ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // Animated Glowing Logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(glowScale),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1).copy(alpha = 0.35f),
                                        Color(0xFF06B6D4).copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    ZenithLogo(size = 56.dp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ZENITH",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = SlateDarkTextPrimary,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "Intelligent Personal & Family Finance",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateDarkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- CENTER FEATURES CAROUSEL ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) { page ->
                    val feature = features[page]
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        backgroundColor = GlassCardBg,
                        borderColor = feature.accentColor.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = feature.accentColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, feature.accentColor.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = feature.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = feature.accentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(feature.accentColor.copy(alpha = 0.18f))
                                    .border(1.dp, feature.accentColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = null,
                                    tint = feature.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = feature.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = feature.description,
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(features.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = if (isSelected) 22.dp else 7.dp, height = 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) EmeraldDarkPrimary else SlateDarkTextMuted.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }

            // --- BOTTOM ACTIONS ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Get Started (Sign Up)
                Button(
                    onClick = onNavigateToRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("splash_btn_get_started"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldDarkPrimary
                    )
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sign In (Already have account)
                OutlinedButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("splash_btn_sign_in"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SlateDarkTextPrimary
                    )
                ) {
                    Text(
                        text = "I already have an account • Sign In",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Continue as Guest / Offline Vault
                TextButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier.testTag("splash_btn_guest")
                ) {
                    Text(
                        text = "Continue with Offline Vault (Guest) →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldAccent
                    )
                }
            }
        }
    }
}
