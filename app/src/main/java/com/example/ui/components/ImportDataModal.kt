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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.export.ImportValidationResult
import com.example.data.models.TransactionEntity
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ImportDataModal(
    existingTransactions: List<TransactionEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onImportConfirmed: (List<TransactionEntity>) -> Unit,
    onNavigateToActivity: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var validationResult by remember { mutableStateOf<ImportValidationResult?>(null) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var isValidating by remember { mutableStateOf(false) }
    var isImportComplete by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }
    var duplicatesSkippedCount by remember { mutableStateOf(0) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isValidating = true
                selectedFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "selected_file"
                val rawContent = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                }

                if (rawContent.isNotBlank()) {
                    val result = withContext(Dispatchers.Default) {
                        ExportImportService.validateAndParseImport(rawContent, existingTransactions)
                    }
                    validationResult = result
                } else {
                    Toast.makeText(context, "Could not read file content", Toast.LENGTH_SHORT).show()
                }
                isValidating = false
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
                .testTag("import_data_modal")
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
                            text = "Import Transactions",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Bring your existing financial records into Zenith.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).testTag("close_import_modal")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isImportComplete) {
                    if (validationResult == null) {
                        // --- 1. UPLOAD BOX ---
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GlassCardBg,
                            border = BorderStroke(1.5.dp, GlassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filePickerLauncher.launch("*/*")
                                }
                                .testTag("btn_select_import_file")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldDarkPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = EmeraldDarkPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (isValidating) "Analyzing File..." else "Choose CSV, Excel or JSON",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary
                                )

                                Text(
                                    text = "Supports standard transaction formats",
                                    fontSize = 12.sp,
                                    color = SlateDarkTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                if (isValidating) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    CircularProgressIndicator(color = EmeraldDarkPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 2. DOWNLOAD TEMPLATES ---
                        Text(
                            text = "IMPORT TEMPLATES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val tFile = ExportImportService.generateCsvTemplate(context)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tFile)
                                    val res = com.example.data.export.ExportResult(tFile, tFile.name, "CSV Template", 0, "1 KB", uri)
                                    ExportImportService.shareFile(context, res)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV Template", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val tFile = ExportImportService.generateExcelTemplate(context)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tFile)
                                    val res = com.example.data.export.ExportResult(tFile, tFile.name, "Excel Template", 0, "2 KB", uri)
                                    ExportImportService.shareFile(context, res)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel Template", fontSize = 11.sp)
                            }
                        }
                    } else {
                        // --- 3. PREVIEW & VALIDATION RESULTS ---
                        val result = validationResult!!

                        Text(
                            text = "IMPORT PREVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats Summary Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatBox("FOUND", "${result.totalFound}", SlateDarkTextPrimary, Modifier.weight(1f))
                            StatBox("VALID", "${result.validTransactions.size}", IncomeGreen, Modifier.weight(1f))
                            StatBox("DUPLICATES", "${result.duplicateTransactions.size}", GoalAmber, Modifier.weight(1f))
                            StatBox("ERRORS", "${result.invalidRows.size}", if (result.invalidRows.isEmpty()) SlateDarkTextSecondary else ExpenseRed, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Duplicate Handling Switch
                        if (result.duplicateTransactions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GoalAmber.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, GoalAmber.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GoalAmber, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${result.duplicateTransactions.size} possible duplicates detected",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoalAmber
                                        )
                                        Text(
                                            text = if (skipDuplicates) "Duplicates will be safely skipped." else "Duplicates will be imported as new entries.",
                                            fontSize = 11.sp,
                                            color = SlateDarkTextPrimary
                                        )
                                    }
                                    Switch(
                                        checked = skipDuplicates,
                                        onCheckedChange = { skipDuplicates = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = GoalAmber, checkedTrackColor = GoalAmber.copy(alpha = 0.4f))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Problematic Rows list if any
                        if (result.invalidRows.isNotEmpty()) {
                            Text(
                                text = "Problematic Rows (${result.invalidRows.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ExpenseRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ExpenseRed.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    result.invalidRows.take(3).forEach { err ->
                                        Text(
                                            text = "• Row ${err.rowIndex}: ${err.reason}",
                                            fontSize = 11.sp,
                                            color = ExpenseRed
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Primary Import Button
                        val importableTransactions = if (skipDuplicates) result.validTransactions else (result.validTransactions + result.duplicateTransactions)

                        Button(
                            onClick = {
                                if (importableTransactions.isNotEmpty()) {
                                    onImportConfirmed(importableTransactions)
                                    importedCount = importableTransactions.size
                                    duplicatesSkippedCount = if (skipDuplicates) result.duplicateTransactions.size else 0
                                    isImportComplete = true
                                    Toast.makeText(context, "Successfully imported $importedCount transactions!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No valid transactions to import", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_confirm_import"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                            enabled = importableTransactions.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import ${importableTransactions.size} Transactions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { validationResult = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pick a Different File", color = SlateDarkTextSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    // --- 4. IMPORT SUCCESS VIEW ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = IncomeGreen,
                            modifier = Modifier.size(54.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Import Complete",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )

                        Text(
                            text = "$importedCount transactions have been recorded in your Zenith ledger.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (duplicatesSkippedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "($duplicatesSkippedCount duplicate entries were safely skipped)",
                                fontSize = 11.sp,
                                color = GoalAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    onDismiss()
                                    onNavigateToActivity()
                                },
                                modifier = Modifier.weight(1.3f).height(44.dp).testTag("btn_view_imported_activity"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                            ) {
                                Text("View Activity →", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateDarkSurfaceVariant,
        border = BorderStroke(1.dp, GlassBorderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
