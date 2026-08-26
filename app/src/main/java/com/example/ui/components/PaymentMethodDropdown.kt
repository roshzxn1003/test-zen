package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

val ZENITH_PAYMENT_METHODS = listOf(
    "UPI" to Icons.Default.QrCodeScanner,
    "Cash" to Icons.Default.Payments,
    "Credit Card" to Icons.Default.CreditCard,
    "Debit Card" to Icons.Default.AccountBalanceWallet,
    "Bank Transfer" to Icons.Default.AccountBalance,
    "Wallet" to Icons.Default.Wallet,
    "Other" to Icons.Default.MoreHoriz
)

@Composable
fun PaymentMethodDropdown(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Payment Method",
    testTag: String = "dropdown_payment_method"
) {
    var expanded by remember { mutableStateOf(false) }

    val currentIcon = remember(selectedMethod) {
        ZENITH_PAYMENT_METHODS.find { it.first.equals(selectedMethod, ignoreCase = true) }?.second
            ?: Icons.Default.Payment
    }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateDarkTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(EmeraldDarkPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = null,
                            tint = EmeraldDarkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (selectedMethod.isBlank()) "Select Payment Method" else selectedMethod,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedMethod.isBlank()) SlateDarkTextMuted else SlateDarkTextPrimary
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Open payment methods",
                    tint = SlateDarkTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Payment Method",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )

                        IconButton(
                            onClick = { expanded = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = SlateDarkTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ZENITH_PAYMENT_METHODS.forEach { (methodName, icon) ->
                            val isSelected = methodName.equals(selectedMethod, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) EmeraldDarkPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, EmeraldDarkPrimary.copy(alpha = 0.4f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onMethodSelected(methodName)
                                        expanded = false
                                    }
                                    .testTag("payment_option_$methodName")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                onMethodSelected(methodName)
                                                expanded = false
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = EmeraldDarkPrimary,
                                                unselectedColor = SlateDarkTextMuted
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) EmeraldDarkPrimary else SlateDarkTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Text(
                                            text = methodName,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) SlateDarkTextPrimary else SlateDarkTextSecondary
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = EmeraldDarkPrimary,
                                            modifier = Modifier.size(18.dp)
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
}
