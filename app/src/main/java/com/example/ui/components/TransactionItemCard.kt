package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.TransactionEntity
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    creatorName: String? = null,
    onClick: ((TransactionEntity) -> Unit)? = null,
    onLongClick: ((TransactionEntity) -> Unit)? = null
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val icon = getCategoryIcon(transaction.categoryIconName)
    val formattedDate = rememberFormattedDate(transaction.dateMillis)
    val hasReceiptAttached = !transaction.receiptImageUri.isNullOrBlank() || transaction.note.contains("Receipt", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SlateDarkSurface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { onClick?.invoke(transaction) },
                onLongClick = { onLongClick?.invoke(transaction) }
            )
            .testTag("transaction_card_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge (Pastel container)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isExpense) PastelRoseContainer else PastelGreenContainer
                    )
                    .border(
                        1.dp,
                        if (isExpense) PastelRose.copy(alpha = 0.35f) else PastelGreen.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = transaction.category,
                    tint = if (isExpense) PastelRose else PastelGreen,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details (Title, Category, Payment Method, Receipt attached tag)
            Column(modifier = Modifier.weight(1f, fill = true)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transaction.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (hasReceiptAttached) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PastelPurpleContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, PastelPurple.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "🧾 Receipt",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PastelPurple,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category Badge Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateDarkSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = transaction.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "• ${transaction.paymentMethod}",
                        fontSize = 11.sp,
                        color = SlateDarkTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (!creatorName.isNullOrBlank()) {
                        Text(
                            text = " • 👤 $creatorName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PastelCyan,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount, Sync Status, and Date
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 65.dp)
            ) {
                Text(
                    text = "${if (isExpense) "-" else "+"}$currencySymbol${String.format(Locale.US, "%,.2f", transaction.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isExpense) PastelRose else PastelGreen,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.syncStatus != "SYNCED") {
                        Text(
                            text = "⏳",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SlateDarkTextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun rememberFormattedDate(millis: Long): String {
    val date = Date(millis)
    val now = Calendar.getInstance()
    val txCal = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR) -> "Today"

        now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - txCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"

        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
    }
}

private fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "restaurant", "food", "dining" -> Icons.Default.Restaurant
        "shopping_bag", "shopping", "groceries" -> Icons.Default.ShoppingBag
        "directions_car", "transport", "petrol", "fuel" -> Icons.Default.DirectionsCar
        "home", "housing", "rent" -> Icons.Default.Home
        "bolt", "utilities", "bills" -> Icons.Default.Bolt
        "movie", "entertainment" -> Icons.Default.Movie
        "medical_services", "health", "healthcare" -> Icons.Default.MedicalServices
        "account_balance", "salary", "income", "payments" -> Icons.Default.AccountBalance
        "trending_up", "investments", "investment" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.Receipt
    }
}
