package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class ZenithNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Default.Home, Icons.Outlined.Home, "nav_home"),
    TRANSACTIONS("Activity", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_transactions"),
    BUDGETS("Budgets", Icons.Default.PieChart, Icons.Outlined.PieChart, "nav_budgets"),
    ANALYTICS("Analytics", Icons.Default.BarChart, Icons.Outlined.BarChart, "nav_analytics"),
    PROFILE("Profile", Icons.Default.Person, Icons.Outlined.Person, "nav_profile")
}

@Composable
fun ZenithFloatingNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Floating Capsule Container — completely transparent backdrop, zero solid bar backgrounds
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Frosted Glass Capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0x99000000),
                    ambientColor = Color(0x40000000)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xEE101726), // Ultra-dense luxury obsidian navy
                            Color(0xF6090D16)  // Midnight carbon floor
                        )
                    )
                )
                .border(
                    BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f), // Crisp specular top light
                                Color(0x33818CF8),              // Subtle electric indigo refraction
                                Color.White.copy(alpha = 0.06f)  // Base reflection
                            )
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZenithNavTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    val interactionSource = remember(index) { MutableInteractionSource() }

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tab_scale"
                    )

                    val activeContentColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFFE0E7FF) else SlateDarkTextSecondary,
                        label = "tab_content_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(animatedScale)
                            .clip(RoundedCornerShape(22.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFF6366F1).copy(alpha = 0.26f),
                                                    Color(0xFF4F46E5).copy(alpha = 0.12f)
                                                )
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            Color(0xFF818CF8).copy(alpha = 0.40f),
                                            RoundedCornerShape(22.dp)
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true, radius = 28.dp)
                            ) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onTabSelected(index)
                            }
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                tint = activeContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = activeContentColor,
                                maxLines = 1
                            )
                            // Glowing micro-indicator bar under active tab
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 2.5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF818CF8),
                                                        Color(0xFF38BDF8)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
