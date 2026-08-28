package com.example.ui.familyledger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.familyledger.FamilyVaultMember
import com.example.data.models.FamilyRole
import com.example.ui.theme.*

@Composable
fun FamilyLedgerMembersView(
    state: FamilyLedgerUiState,
    currencySymbol: String = "₹",
    onAddMember: (name: String, role: FamilyRole) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onJoinVault: (inviteCode: String) -> Unit
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val inviteCode = state.activeVault?.inviteCode?.takeIf { it.isNotBlank() }
        ?: ("FAM-" + (state.activeVault?.familyId?.take(6)?.uppercase() ?: "894201"))
    val vaultName = state.activeVault?.familyName ?: "Family Vault"

    val totalFamilyExpense = remember(state.transactions) {
        state.transactions.filter { it.type == com.example.data.models.TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val memberAvatarGradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
            Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))),
            Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
            Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
        )
    }

    fun shareInviteCode() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join our Family Vault on Zenith")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Join our shared Family Vault '$vaultName' on Zenith Finance!\n\nInvite Code: $inviteCode\n\nOpen Zenith > Family Ledger > Join Vault and enter $inviteCode to connect instantly."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Family Vault Invite").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. LUXURY INVITE CODE HERO CARD ---
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateDarkSurface,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF818CF8).copy(alpha = 0.6f),
                            Color(0xFF22D3EE).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VAULT INVITE CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PastelIndigo,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inviteCode,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFDE68A),
                                letterSpacing = 1.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldDarkPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Zenith Invite Code", inviteCode))
                                        Toast.makeText(context, "Invite Code copied!", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GlassCardBgElevated,
                                border = BorderStroke(1.dp, GlassBorderColor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { shareInviteCode() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = SlateDarkTextPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. ACTION BUTTONS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showAddMemberDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("btn_add_member_dialog")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Member", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SlateDarkSurface.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = PastelCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join Vault", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PastelCyan)
                }
            }
        }

        // --- 3. MEMBERS LIST HEADER ---
        item {
            Text(
                text = "FAMILY MEMBERS (${state.members.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateDarkTextMuted,
                letterSpacing = 1.sp
            )
        }

        // --- 4. MEMBER ROSTER ITEMS ---
        if (state.members.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateDarkSurface,
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No family members found in this vault.",
                            fontSize = 13.sp,
                            color = SlateDarkTextSecondary
                        )
                    }
                }
            }
        } else {
            items(state.members.size) { index ->
                val member = state.members[index]
                val isAdmin = member.role == FamilyRole.ADMIN
                val gradient = memberAvatarGradients[index % memberAvatarGradients.size]
                val sharePct = if (totalFamilyExpense > 0) ((member.totalPaid / totalFamilyExpense) * 100).toInt() else 0

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateDarkSurface,
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(gradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = member.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateDarkTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isAdmin) GoalAmberContainer.copy(alpha = 0.4f) else GlassCardBgElevated
                                        ) {
                                            Text(
                                                text = member.role.name,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAdmin) GoalAmber else SlateDarkTextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${member.transactionCount} transactions recorded",
                                        fontSize = 11.sp,
                                        color = SlateDarkTextMuted
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$currencySymbol%,.0f".format(member.totalPaid),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SlateDarkTextPrimary
                                    )
                                    Text(
                                        text = "$sharePct% of total",
                                        fontSize = 11.sp,
                                        color = SlateDarkTextSecondary
                                    )
                                }

                                if (!isAdmin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemoveMember(member.memberId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonRemove,
                                            contentDescription = "Remove",
                                            tint = PastelRose.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
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

    // --- ADD MEMBER DIALOG ---
    if (showAddMemberDialog) {
        var memberName by remember { mutableStateOf("") }
        var memberRole by remember { mutableStateOf(FamilyRole.MEMBER) }
        var nameError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showAddMemberDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add Family Member", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Text("Add a household member to track their expenses.", fontSize = 12.sp, color = SlateDarkTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = memberName,
                        onValueChange = {
                            memberName = it
                            if (nameError != null) nameError = null
                        },
                        label = { Text("Member Name") },
                        placeholder = { Text("e.g. Priya, Dad, Brother") },
                        isError = nameError != null,
                        supportingText = { if (nameError != null) Text(nameError!!, color = PastelRose, fontSize = 11.sp) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_new_member_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("ROLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextMuted, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = memberRole == FamilyRole.MEMBER,
                            onClick = { memberRole = FamilyRole.MEMBER },
                            label = { Text("Member") },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = memberRole == FamilyRole.ADMIN,
                            onClick = { memberRole = FamilyRole.ADMIN },
                            label = { Text("Admin") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (memberName.trim().isBlank()) {
                                nameError = "Please enter a name"
                            } else {
                                onAddMember(memberName.trim(), memberRole)
                                showAddMemberDialog = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Add to Family Vault", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // --- JOIN VAULT DIALOG ---
    if (showJoinDialog) {
        var inviteCodeInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showJoinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Join Family Vault", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Text("Enter the Invite Code / Vault ID to connect.", fontSize = 12.sp, color = SlateDarkTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("Invite Code") },
                        placeholder = { Text("e.g. FAM-X7K9P2") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_join_vault_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (inviteCodeInput.trim().isNotBlank()) {
                                onJoinVault(inviteCodeInput.trim())
                                showJoinDialog = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Connect & Sync", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
