package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.export.ExportImportService
import com.example.data.models.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreModal(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    budgets: List<BudgetEntity>,
    savingsGoals: List<SavingsGoalEntity>,
    onDismiss: () -> Unit,
    onRestoreBackup: (
        restoredTransactions: List<TransactionEntity>,
        restoredBudgets: List<BudgetEntity>,
        restoredGoals: List<SavingsGoalEntity>
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: Backup, 1: Restore
    var isBackingUp by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<com.example.data.export.ExportResult?>(null) }

    // Restore preview state
    var restorePreview by remember { mutableStateOf<RestorePreviewData?>(null) }
    var isReadingRestoreFile by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isReadingRestoreFile = true
                val rawJson = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                }

                if (rawJson.isNotBlank()) {
                    try {
                        val root = JSONObject(rawJson)
                        val exportDate = root.optString("exportDate", "Recent")
                        val txArray = root.optJSONArray("transactions")
                        val budgetArray = root.optJSONArray("budgets")
                        val goalArray = root.optJSONArray("savingsGoals")

                        val parsedTx = mutableListOf<TransactionEntity>()
                        if (txArray != null) {
                            for (i in 0 until txArray.length()) {
                                val obj = txArray.optJSONObject(i) ?: continue
                                parsedTx.add(
                                    TransactionEntity(
                                        title = obj.optString("title", "Imported"),
                                        amount = obj.optDouble("amount", 0.0),
                                        type = if (obj.optString("type") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                                        category = obj.optString("category", "Food & Dining"),
                                        paymentMethod = obj.optString("paymentMethod", "UPI"),
                                        note = obj.optString("note", ""),
                                        dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                                        financeScope = if (obj.optString("financeScope") == "FAMILY") FinanceScope.FAMILY else FinanceScope.PERSONAL,
                                        syncStatus = "PENDING_CREATE"
                                    )
                                )
                            }
                        }

                        val parsedBudgets = mutableListOf<BudgetEntity>()
                        if (budgetArray != null) {
                            for (i in 0 until budgetArray.length()) {
                                val obj = budgetArray.optJSONObject(i) ?: continue
                                parsedBudgets.add(
                                    BudgetEntity(
                                        categoryName = obj.optString("categoryName", ""),
                                        monthlyLimit = obj.optDouble("monthlyLimit", 0.0),
                                        monthYear = obj.optString("monthYear", "2026-08"),
                                        periodType = obj.optString("periodType", "MONTHLY"),
                                        customPeriodName = obj.optString("customPeriodName", ""),
                                        financeScope = if (obj.optString("financeScope") == "FAMILY") FinanceScope.FAMILY else FinanceScope.PERSONAL
                                    )
                                )
                            }
                        }

                        val parsedGoals = mutableListOf<SavingsGoalEntity>()
                        if (goalArray != null) {
                            for (i in 0 until goalArray.length()) {
                                val obj = goalArray.optJSONObject(i) ?: continue
                                parsedGoals.add(
                                    SavingsGoalEntity(
                                        title = obj.optString("title", "Goal"),
                                        targetAmount = obj.optDouble("targetAmount", 0.0),
                                        currentAmount = obj.optDouble("currentAmount", 0.0),
                                        financeScope = if (obj.optString("financeScope") == "FAMILY") FinanceScope.FAMILY else FinanceScope.PERSONAL
                                    )
                                )
                            }
                        }

                        restorePreview = RestorePreviewData(
                            exportDate = exportDate,
                            transactions = parsedTx,
                            budgets = parsedBudgets,
                            goals = parsedGoals
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Invalid Zenith backup file", Toast.LENGTH_SHORT).show()
                    }
                }
                isReadingRestoreFile = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .testTag("backup_restore_modal")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Backup & Restore",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Preserve and recover your entire financial history.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).testTag("close_backup_modal")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Switcher (Backup vs Restore)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    val isBackup = selectedTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(if (isBackup) EmeraldDarkPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Create Backup", fontWeight = FontWeight.Bold, color = if (isBackup) Color.White else SlateDarkTextSecondary, fontSize = 13.sp)
                    }

                    val isRestore = selectedTab == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(if (isRestore) GoldAccent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Restore Backup", fontWeight = FontWeight.Bold, color = if (isRestore) Color.Black else SlateDarkTextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // --- CREATE BACKUP TAB ---
                    Text(
                        text = "BACKUP SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SlateDarkSurfaceVariant,
                        border = BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            BackupItemRow("Transactions", "${transactions.size} records")
                            BackupItemRow("Budgets", "${budgets.size} active limits")
                            BackupItemRow("Savings Goals", "${savingsGoals.size} targets")
                            BackupItemRow("Categories", "${categories.size} categories")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldDarkPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Full snapshots are securely stored on your device and exclude any account passwords.", fontSize = 11.sp, color = SlateDarkTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (backupResult == null) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isBackingUp = true
                                    val result = ExportImportService.exportJsonBackup(context, transactions, categories, budgets, savingsGoals)
                                    backupResult = result
                                    isBackingUp = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_create_full_backup"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating Snapshot...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Full Backup (.json)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Backup Created Successfully!", fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary, fontSize = 15.sp)
                            Text(backupResult?.fileName ?: "", fontSize = 12.sp, color = SlateDarkTextSecondary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { backupResult?.let { ExportImportService.shareFile(context, it) } },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { backupResult = null },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                } else {
                    // --- RESTORE BACKUP TAB ---
                    if (restorePreview == null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GlassCardBg,
                            border = BorderStroke(1.5.dp, GlassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filePickerLauncher.launch("application/json")
                                }
                                .testTag("btn_select_restore_file")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (isReadingRestoreFile) "Reading Backup..." else "Select Zenith JSON Backup",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary
                                )

                                Text(
                                    text = "Choose a zenith_backup_*.json file",
                                    fontSize = 12.sp,
                                    color = SlateDarkTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        val prev = restorePreview!!
                        Text(
                            text = "BACKUP DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SlateDarkSurfaceVariant,
                            border = BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                BackupItemRow("Backup Date", prev.exportDate)
                                BackupItemRow("Transactions", "${prev.transactions.size} entries")
                                BackupItemRow("Budgets", "${prev.budgets.size} limits")
                                BackupItemRow("Savings Goals", "${prev.goals.size} targets")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GoalAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoalAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GoalAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restoring will merge these records into your current ledger without overwriting unrelated data.", fontSize = 11.sp, color = GoalAmber)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { restorePreview = null },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    onRestoreBackup(prev.transactions, prev.budgets, prev.goals)
                                    Toast.makeText(context, "Restored ${prev.transactions.size} transactions and ${prev.budgets.size} budgets!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.3f).height(46.dp).testTag("btn_confirm_restore"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Data", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RestorePreviewData(
    val exportDate: String,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<SavingsGoalEntity>
)

@Composable
private fun BackupItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = SlateDarkTextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
    }
}
