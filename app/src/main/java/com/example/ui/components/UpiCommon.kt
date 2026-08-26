package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.ui.theme.*
import java.util.Locale

/**
 * Shared scope / family-member / category fields used by every UPI entry point.
 */
@Composable
fun UpiTransactionFields(
    selectedScope: FinanceScope,
    onScopeSelected: (FinanceScope) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    selectedMemberId: String?,
    onMemberSelected: (String?) -> Unit,
    purpose: String,
    familyName: String,
    familyMembers: List<FamilyMemberEntity>,
    currentUserName: String
) {
    val selectedMemberName = remember(selectedMemberId, familyMembers) {
        if (selectedMemberId == null) "Me (${currentUserName.ifBlank { "You" }})"
        else familyMembers.find { it.userId == selectedMemberId }?.name ?: "Me"
    }

    // --- Scope selector (Personal / Family) ---
    Text(
        text = "Transaction Scope",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = SlateDarkTextSecondary,
        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(SlateDarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isPersonal = selectedScope == FinanceScope.PERSONAL
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isPersonal) EmeraldDarkPrimary else Color.Transparent, RoundedCornerShape(9.dp))
                .clickable { onScopeSelected(FinanceScope.PERSONAL) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Personal",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isPersonal) Color.White else SlateDarkTextSecondary
            )
        }
        val isFamily = selectedScope == FinanceScope.FAMILY
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isFamily) Color(0xFF8B5CF6) else Color.Transparent, RoundedCornerShape(9.dp))
                .clickable { onScopeSelected(FinanceScope.FAMILY) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Family",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isFamily) Color.White else SlateDarkTextSecondary
            )
        }
    }

    // --- Family details when Family scope selected ---
    AnimatedVisibility(visible = selectedScope == FinanceScope.FAMILY) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Family: $familyName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE9D5FF)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Shared Vault", fontSize = 10.sp, color = Color(0xFFC4B5FD), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recorded For",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateDarkTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = selectedMemberName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SlateDarkTextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (familyMembers.isEmpty()) "Only you" else "Select below",
                        fontSize = 10.sp,
                        color = SlateDarkTextSecondary
                    )
                }
            }

            if (familyMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMemberId == null,
                        onClick = { onMemberSelected(null) },
                        label = { Text("Me (${currentUserName.ifBlank { "You" }})", fontSize = 11.sp) }
                    )
                    familyMembers.forEach { member ->
                        FilterChip(
                            selected = selectedMemberId == member.userId,
                            onClick = { onMemberSelected(member.userId) },
                            label = { Text(member.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // --- Category (suggested + editable) ---
    Text(
        text = "Category",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = SlateDarkTextSecondary,
        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
    )
    val suggestedCategory = remember(purpose) { suggestCategoryForMerchant(purpose) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Food & Dining", "Shopping", "Transportation", "Bills & Utilities", "Entertainment", "Healthcare", "Other").forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onCategorySelected(cat) },
                label = { Text(cat, fontSize = 11.sp) }
            )
        }
    }
    if (suggestedCategory != null && suggestedCategory != selectedCategory) {
        TextButton(
            onClick = { onCategorySelected(suggestedCategory) },
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Text(
                text = "Suggested: $suggestedCategory  ·  Apply",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF06B6D4)
            )
        }
    }
}

internal fun suggestCategoryForMerchant(purpose: String): String? {
    val text = purpose.lowercase(Locale.ROOT)
    val keywords = listOf(
        "food" to "Food & Dining", "swiggy" to "Food & Dining", "zomato" to "Food & Dining",
        "restaurant" to "Food & Dining", "dining" to "Food & Dining", "cafe" to "Food & Dining", "hotel" to "Food & Dining",
        "grocery" to "Food & Dining",
        "uber" to "Transportation", "ola" to "Transportation", "cab" to "Transportation", "taxi" to "Transportation",
        "fuel" to "Transportation", "petrol" to "Transportation", "transport" to "Transportation", "metro" to "Transportation",
        "amazon" to "Shopping", "flipkart" to "Shopping", "myntra" to "Shopping", "shop" to "Shopping", "mall" to "Shopping",
        "entertainment" to "Entertainment", "movie" to "Entertainment", "netflix" to "Entertainment", "spotify" to "Entertainment",
        "hospital" to "Healthcare", "pharmacy" to "Healthcare", "medical" to "Healthcare", "doctor" to "Healthcare",
        "rent" to "Housing & Rent",
        "electricity" to "Bills & Utilities", "water" to "Bills & Utilities", "internet" to "Bills & Utilities",
        "mobile" to "Bills & Utilities", "phone" to "Bills & Utilities", "subscription" to "Subscriptions"
    )
    return keywords.firstOrNull { (keyword, _) -> text.contains(keyword) }?.second
}
