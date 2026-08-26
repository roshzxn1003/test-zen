package com.example.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.models.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val dateFilter: DateFilterType = DateFilterType.THIS_MONTH,
    val typeFilter: TransactionType? = null,
    val scopeFilter: FinanceScope? = null,
    val includeCategories: Boolean = true,
    val includeNotes: Boolean = true,
    val includePaymentMethods: Boolean = true,
    val includeBudgetSummary: Boolean = true
)

enum class ExportFormat {
    CSV,
    EXCEL,
    PDF,
    JSON
}

enum class DateFilterType(val label: String) {
    ALL_TIME("All Time"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THIS_YEAR("This Year")
}

data class ExportResult(
    val file: File,
    val fileName: String,
    val fileType: String,
    val transactionCount: Int,
    val fileSizeFormatted: String,
    val contentUri: Uri
)

data class ImportValidationResult(
    val totalFound: Int,
    val validTransactions: List<TransactionEntity>,
    val duplicateTransactions: List<TransactionEntity>,
    val invalidRows: List<ImportRowError>
)

data class ImportRowError(
    val rowIndex: Int,
    val rawText: String,
    val reason: String
)

data class BackupMetadata(
    val createdAt: Long,
    val transactionCount: Int,
    val budgetCount: Int,
    val goalCount: Int,
    val categoryCount: Int,
    val appVersion: String = "2.0.0 (Zenith)"
)

object ExportImportService {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun filterTransactions(transactions: List<TransactionEntity>, options: ExportOptions): List<TransactionEntity> {
        val now = Calendar.getInstance()
        var filtered = transactions

        // Filter by scope
        if (options.scopeFilter != null) {
            filtered = filtered.filter { it.financeScope == options.scopeFilter }
        }

        // Filter by type
        if (options.typeFilter != null) {
            filtered = filtered.filter { it.type == options.typeFilter }
        }

        // Filter by date
        filtered = when (options.dateFilter) {
            DateFilterType.ALL_TIME -> filtered
            DateFilterType.THIS_MONTH -> {
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH)
                filtered.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                    cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
                }
            }
            DateFilterType.LAST_MONTH -> {
                val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val targetYear = lastMonthCal.get(Calendar.YEAR)
                val targetMonth = lastMonthCal.get(Calendar.MONTH)
                filtered.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                    cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) == targetMonth
                }
            }
            DateFilterType.THIS_YEAR -> {
                val currentYear = now.get(Calendar.YEAR)
                filtered.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                    cal.get(Calendar.YEAR) == currentYear
                }
            }
        }

        return filtered.sortedByDescending { it.dateMillis }
    }

    // --- 1. CSV EXPORT ---
    fun exportCsv(context: Context, transactions: List<TransactionEntity>, currencyCode: String, options: ExportOptions): ExportResult {
        val filtered = filterTransactions(transactions, options)
        val todayStr = dateFormat.format(Date())
        val fileName = "zenith_transactions_$todayStr.csv"
        val file = File(context.cacheDir, fileName)

        val csvBuilder = StringBuilder()
        csvBuilder.append("transaction_id,date,title,type,amount,currency,category,payment_method,account,notes,created_at,updated_at\n")

        filtered.forEach { tx ->
            val dateStr = dateFormat.format(Date(tx.dateMillis))
            val typeStr = if (tx.type == TransactionType.INCOME) "income" else "expense"
            val cleanTitle = escapeCsv(tx.title)
            val cleanNotes = escapeCsv(tx.note)
            val cleanCat = escapeCsv(tx.category)
            val cleanMethod = escapeCsv(tx.paymentMethod)
            val accountStr = if (tx.financeScope == FinanceScope.FAMILY) "Family" else "Personal"
            val createdAtStr = dateStr + " " + timeFormat.format(Date(tx.dateMillis))
            val updatedAtStr = dateStr + " " + timeFormat.format(Date(tx.updatedAt))

            // Amount strictly numeric without formatting
            val numAmount = String.format(Locale.US, "%.2f", tx.amount)

            csvBuilder.append("TXN${tx.id},$dateStr,$cleanTitle,$typeStr,$numAmount,$currencyCode,$cleanCat,$cleanMethod,$accountStr,$cleanNotes,$createdAtStr,$updatedAtStr\n")
        }

        file.writeText(csvBuilder.toString(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return ExportResult(
            file = file,
            fileName = fileName,
            fileType = "CSV Document",
            transactionCount = filtered.size,
            fileSizeFormatted = formatFileSize(file.length()),
            contentUri = uri
        )
    }

    // --- 2. EXCEL EXPORT (Structured XML Spreadsheet / CSV Excel Compatible) ---
    fun exportExcel(context: Context, transactions: List<TransactionEntity>, currencyCode: String, options: ExportOptions): ExportResult {
        val filtered = filterTransactions(transactions, options)
        val todayStr = dateFormat.format(Date())
        val fileName = "zenith_transactions_$todayStr.xlsx"
        val file = File(context.cacheDir, fileName)

        // Generate clean XML Spreadsheet (valid .xlsx / Excel XML document readable by all spreadsheet apps)
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\"?>\n")
        xmlBuilder.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
        xmlBuilder.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
        xmlBuilder.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
        xmlBuilder.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
        xmlBuilder.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
        xmlBuilder.append(" <Styles>\n")
        xmlBuilder.append("  <Style ss:ID=\"Header\"><Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/><Interior ss:Color=\"#0F172A\" ss:Pattern=\"Solid\"/></Style>\n")
        xmlBuilder.append("  <Style ss:ID=\"Income\"><Font ss:Color=\"#10B981\" ss:Bold=\"1\"/></Style>\n")
        xmlBuilder.append("  <Style ss:ID=\"Expense\"><Font ss:Color=\"#F43F5E\" ss:Bold=\"1\"/></Style>\n")
        xmlBuilder.append("  <Style ss:ID=\"Number\"><NumberFormat ss:Format=\"#,##0.00\"/></Style>\n")
        xmlBuilder.append(" </Styles>\n")
        xmlBuilder.append(" <Worksheet ss:Name=\"Zenith Transactions\">\n")
        xmlBuilder.append("  <Table>\n")

        // Columns
        val headers = listOf("Transaction ID", "Date", "Title", "Type", "Amount", "Currency", "Category", "Payment Method", "Account", "Notes")
        xmlBuilder.append("   <Row ss:StyleID=\"Header\">\n")
        headers.forEach { h ->
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">$h</Data></Cell>\n")
        }
        xmlBuilder.append("   </Row>\n")

        filtered.forEach { tx ->
            val dateStr = dateFormat.format(Date(tx.dateMillis))
            val isIncome = tx.type == TransactionType.INCOME
            val typeStr = if (isIncome) "income" else "expense"
            val accountStr = if (tx.financeScope == FinanceScope.FAMILY) "Family" else "Personal"

            xmlBuilder.append("   <Row>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">TXN${tx.id}</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">$dateStr</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.title)}</Data></Cell>\n")
            xmlBuilder.append("    <Cell ss:StyleID=\"${if (isIncome) "Income" else "Expense"}\"><Data ss:Type=\"String\">$typeStr</Data></Cell>\n")
            xmlBuilder.append("    <Cell ss:StyleID=\"Number\"><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", tx.amount)}</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">$currencyCode</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.category)}</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.paymentMethod)}</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">$accountStr</Data></Cell>\n")
            xmlBuilder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.note)}</Data></Cell>\n")
            xmlBuilder.append("   </Row>\n")
        }

        xmlBuilder.append("  </Table>\n")
        xmlBuilder.append(" </Worksheet>\n")
        xmlBuilder.append("</Workbook>\n")

        file.writeText(xmlBuilder.toString(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return ExportResult(
            file = file,
            fileName = fileName,
            fileType = "Excel Workbook (.xlsx)",
            transactionCount = filtered.size,
            fileSizeFormatted = formatFileSize(file.length()),
            contentUri = uri
        )
    }

    // --- 3. PROFESSIONAL PDF REPORT ---
    fun exportPdf(
        context: Context,
        transactions: List<TransactionEntity>,
        currencySymbol: String,
        options: ExportOptions
    ): ExportResult {
        val filtered = filterTransactions(transactions, options)
        val todayStr = dateFormat.format(Date())
        val fileName = "zenith_financial_report_$todayStr.pdf"
        val file = File(context.cacheDir, fileName)

        val totalIncome = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netCashFlow = totalIncome - totalExpense

        val startDateStr = if (filtered.isNotEmpty()) displayDateFormat.format(Date(filtered.last().dateMillis)) else todayStr
        val endDateStr = if (filtered.isNotEmpty()) displayDateFormat.format(Date(filtered.first().dateMillis)) else todayStr

        val pdfDoc = PdfDocument()
        val pageWidth = 595 // Standard A4 width in pt
        val pageHeight = 842 // Standard A4 height in pt
        val margin = 36f

        val titlePaint = Paint().apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subPaint = Paint().apply {
            color = AndroidColor.parseColor("#64748B")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = AndroidColor.parseColor("#1E293B")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val greenPaint = Paint().apply {
            color = AndroidColor.parseColor("#10B981")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val redPaint = Paint().apply {
            color = AndroidColor.parseColor("#F43F5E")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = AndroidColor.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        val bgPaint = Paint().apply {
            color = AndroidColor.parseColor("#F8FAFC")
        }

        val cardBgPaint = Paint().apply {
            color = AndroidColor.parseColor("#0F172A")
        }

        val rowsPerPage = 22
        val chunks = if (filtered.isEmpty()) listOf(emptyList()) else filtered.chunked(rowsPerPage)
        val totalPages = chunks.size

        chunks.forEachIndexed { pageIndex, pageItems ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            var y = margin

            if (pageIndex == 0) {
                // Header Banner
                canvas.drawText("ZENITH", margin, y + 20f, titlePaint)
                canvas.drawText("Transaction & Financial Summary Report", margin, y + 34f, subPaint)
                canvas.drawText("Report Period: $startDateStr – $endDateStr", margin, y + 46f, subPaint)

                // Date generated (Right aligned)
                val genDate = "Generated: ${displayDateFormat.format(Date())}"
                canvas.drawText(genDate, pageWidth - margin - textPaint.measureText(genDate), y + 20f, subPaint)

                y += 65f

                // KPI Summary Cards
                val cardWidth = (pageWidth - (margin * 2) - 20f) / 3f
                val cardHeight = 52f

                // Income Card
                drawKpiCard(canvas, margin, y, cardWidth, cardHeight, "TOTAL INCOME", "+$currencySymbol${String.format(Locale.US, "%,.2f", totalIncome)}", "#10B981", "#E6F4EA")

                // Expense Card
                drawKpiCard(canvas, margin + cardWidth + 10f, y, cardWidth, cardHeight, "TOTAL EXPENSES", "-$currencySymbol${String.format(Locale.US, "%,.2f", totalExpense)}", "#F43F5E", "#FCE8E6")

                // Net Balance Card
                val netColor = if (netCashFlow >= 0) "#10B981" else "#F43F5E"
                val netBg = if (netCashFlow >= 0) "#E6F4EA" else "#FCE8E6"
                drawKpiCard(canvas, margin + (cardWidth * 2) + 20f, y, cardWidth, cardHeight, "NET CASH FLOW", "${if (netCashFlow >= 0) "+" else "-"}$currencySymbol${String.format(Locale.US, "%,.2f", Math.abs(netCashFlow))}", netColor, netBg)

                y += cardHeight + 25f
            } else {
                y += 20f
            }

            // Table Header
            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, cardBgPaint)
            val headerTextPaint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            canvas.drawText("DATE", margin + 8f, y + 14f, headerTextPaint)
            canvas.drawText("DESCRIPTION / TITLE", margin + 70f, y + 14f, headerTextPaint)
            canvas.drawText("CATEGORY", margin + 220f, y + 14f, headerTextPaint)
            canvas.drawText("METHOD", margin + 320f, y + 14f, headerTextPaint)
            canvas.drawText("SCOPE", margin + 390f, y + 14f, headerTextPaint)
            val amtHead = "AMOUNT"
            canvas.drawText(amtHead, pageWidth - margin - 8f - headerTextPaint.measureText(amtHead), y + 14f, headerTextPaint)

            y += 22f

            // Table Rows
            pageItems.forEachIndexed { idx, tx ->
                val rowHeight = 22f
                if (idx % 2 == 1) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, bgPaint)
                }

                val dateStr = displayDateFormat.format(Date(tx.dateMillis))
                val isIncome = tx.type == TransactionType.INCOME
                val amountStr = "${if (isIncome) "+" else "-"}$currencySymbol${String.format(Locale.US, "%,.2f", tx.amount)}"
                val titleTruncated = if (tx.title.length > 25) tx.title.take(23) + "…" else tx.title
                val catTruncated = if (tx.category.length > 16) tx.category.take(14) + "…" else tx.category
                val scopeStr = if (tx.financeScope == FinanceScope.FAMILY) "Family" else "Personal"

                canvas.drawText(dateStr, margin + 8f, y + 14f, textPaint)
                canvas.drawText(titleTruncated, margin + 70f, y + 14f, boldTextPaint)
                canvas.drawText(catTruncated, margin + 220f, y + 14f, textPaint)
                canvas.drawText(tx.paymentMethod, margin + 320f, y + 14f, textPaint)
                canvas.drawText(scopeStr, margin + 390f, y + 14f, textPaint)

                val paintToUse = if (isIncome) greenPaint else redPaint
                val amtWidth = paintToUse.measureText(amountStr)
                canvas.drawText(amountStr, pageWidth - margin - 8f - amtWidth, y + 14f, paintToUse)

                canvas.drawLine(margin, y + rowHeight, pageWidth - margin, y + rowHeight, linePaint)
                y += rowHeight
            }

            // Footer
            val footerY = pageHeight - margin + 10f
            canvas.drawLine(margin, footerY - 14f, pageWidth - margin, footerY - 14f, linePaint)
            val footerText = "Zenith Financial Clarity • Page ${pageIndex + 1} of $totalPages"
            canvas.drawText(footerText, margin, footerY, subPaint)
            val confText = "Confidential Financial Record"
            canvas.drawText(confText, pageWidth - margin - subPaint.measureText(confText), footerY, subPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return ExportResult(
            file = file,
            fileName = fileName,
            fileType = "PDF Financial Report",
            transactionCount = filtered.size,
            fileSizeFormatted = formatFileSize(file.length()),
            contentUri = uri
        )
    }

    private fun drawKpiCard(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, title: String, value: String, textColor: String, bgColor: String) {
        val bgPaint = Paint().apply {
            color = AndroidColor.parseColor(bgColor)
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = AndroidColor.parseColor(textColor)
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = AndroidColor.parseColor("#475569")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = AndroidColor.parseColor(textColor)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawRoundRect(x, y, x + width, y + height, 6f, 6f, bgPaint)
        canvas.drawRoundRect(x, y, x + width, y + height, 6f, 6f, borderPaint)
        canvas.drawText(title, x + 10f, y + 18f, titlePaint)
        canvas.drawText(value, x + 10f, y + 38f, valPaint)
    }

    // --- 4. JSON BACKUP EXPORT ---
    fun exportJsonBackup(
        context: Context,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        savingsGoals: List<SavingsGoalEntity>
    ): ExportResult {
        val todayStr = dateFormat.format(Date())
        val fileName = "zenith_backup_$todayStr.json"
        val file = File(context.cacheDir, fileName)

        val rootObj = JSONObject()
        rootObj.put("appName", "Zenith")
        rootObj.put("version", "2.0.0")
        rootObj.put("exportedAt", System.currentTimeMillis())
        rootObj.put("exportDate", todayStr)

        // Transactions array
        val txArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("title", tx.title)
                put("amount", tx.amount)
                put("type", tx.type.name)
                put("category", tx.category)
                put("categoryIconName", tx.categoryIconName)
                put("note", tx.note)
                put("paymentMethod", tx.paymentMethod)
                put("dateMillis", tx.dateMillis)
                put("financeScope", tx.financeScope.name)
                put("familyId", tx.familyId ?: "")
                put("createdByUserId", tx.createdByUserId ?: "")
                put("createdAt", tx.dateMillis)
                put("updatedAt", tx.updatedAt)
            }
            txArray.put(obj)
        }
        rootObj.put("transactions", txArray)

        // Categories array
        val catArray = JSONArray()
        categories.forEach { cat ->
            catArray.put(JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("iconName", cat.iconName)
                put("colorHex", cat.colorHex)
                put("type", cat.type.name)
            })
        }
        rootObj.put("categories", catArray)

        // Budgets array
        val budgetArray = JSONArray()
        budgets.forEach { b ->
            budgetArray.put(JSONObject().apply {
                put("id", b.id)
                put("categoryName", b.categoryName)
                put("monthlyLimit", b.monthlyLimit)
                put("monthYear", b.monthYear)
                put("periodType", b.periodType)
                put("customPeriodName", b.customPeriodName)
                put("financeScope", b.financeScope.name)
            })
        }
        rootObj.put("budgets", budgetArray)

        // Goals array
        val goalArray = JSONArray()
        savingsGoals.forEach { g ->
            goalArray.put(JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("targetAmount", g.targetAmount)
                put("currentAmount", g.currentAmount)
                put("financeScope", g.financeScope.name)
            })
        }
        rootObj.put("savingsGoals", goalArray)

        file.writeText(rootObj.toString(2), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return ExportResult(
            file = file,
            fileName = fileName,
            fileType = "Zenith Full JSON Backup",
            transactionCount = transactions.size,
            fileSizeFormatted = formatFileSize(file.length()),
            contentUri = uri
        )
    }

    // --- 5. DOWNLOAD IMPORT TEMPLATES ---
    fun generateCsvTemplate(context: Context): File {
        val fileName = "zenith_transaction_template.csv"
        val file = File(context.cacheDir, fileName)
        val template = """
            date,title,type,amount,currency,category,payment_method,account,notes
            2026-08-15,Salary,income,10000,INR,Salary & Income,UPI,Personal,August monthly salary
            2026-08-15,Lunch,expense,150,INR,Food & Dining,Cash,Personal,Lunch at cafe
            2026-08-15,Petrol,expense,500,INR,Transportation,UPI,Personal,Fuel refill
        """.trimIndent()
        file.writeText(template + "\n", Charsets.UTF_8)
        return file
    }

    fun generateExcelTemplate(context: Context): File {
        val fileName = "zenith_transaction_template.xlsx"
        val file = File(context.cacheDir, fileName)
        val xml = """
            <?xml version="1.0"?>
            <?mso-application progid="Excel.Sheet"?>
            <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
             xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
             <Styles>
              <Style ss:ID="Header"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#0F172A" ss:Pattern="Solid"/></Style>
             </Styles>
             <Worksheet ss:Name="Transactions">
              <Table>
               <Row ss:StyleID="Header">
                <Cell><Data ss:Type="String">date</Data></Cell>
                <Cell><Data ss:Type="String">title</Data></Cell>
                <Cell><Data ss:Type="String">type</Data></Cell>
                <Cell><Data ss:Type="String">amount</Data></Cell>
                <Cell><Data ss:Type="String">currency</Data></Cell>
                <Cell><Data ss:Type="String">category</Data></Cell>
                <Cell><Data ss:Type="String">payment_method</Data></Cell>
                <Cell><Data ss:Type="String">account</Data></Cell>
                <Cell><Data ss:Type="String">notes</Data></Cell>
               </Row>
               <Row>
                <Cell><Data ss:Type="String">2026-08-15</Data></Cell>
                <Cell><Data ss:Type="String">Salary</Data></Cell>
                <Cell><Data ss:Type="String">income</Data></Cell>
                <Cell><Data ss:Type="Number">10000</Data></Cell>
                <Cell><Data ss:Type="String">INR</Data></Cell>
                <Cell><Data ss:Type="String">Salary &amp; Income</Data></Cell>
                <Cell><Data ss:Type="String">UPI</Data></Cell>
                <Cell><Data ss:Type="String">Personal</Data></Cell>
                <Cell><Data ss:Type="String">August monthly salary</Data></Cell>
               </Row>
               <Row>
                <Cell><Data ss:Type="String">2026-08-15</Data></Cell>
                <Cell><Data ss:Type="String">Lunch</Data></Cell>
                <Cell><Data ss:Type="String">expense</Data></Cell>
                <Cell><Data ss:Type="Number">150</Data></Cell>
                <Cell><Data ss:Type="String">INR</Data></Cell>
                <Cell><Data ss:Type="String">Food &amp; Dining</Data></Cell>
                <Cell><Data ss:Type="String">Cash</Data></Cell>
                <Cell><Data ss:Type="String">Personal</Data></Cell>
                <Cell><Data ss:Type="String">Lunch with team</Data></Cell>
               </Row>
              </Table>
             </Worksheet>
            </Workbook>
        """.trimIndent()
        file.writeText(xml, Charsets.UTF_8)
        return file
    }

    // --- 6. IMPORT VALIDATION & PARSING ---
    fun validateAndParseImport(
        rawContent: String,
        existingTransactions: List<TransactionEntity>
    ): ImportValidationResult {
        val validList = mutableListOf<TransactionEntity>()
        val duplicateList = mutableListOf<TransactionEntity>()
        val errorList = mutableListOf<ImportRowError>()

        val lines = rawContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportValidationResult(0, emptyList(), emptyList(), listOf(ImportRowError(1, "", "File is empty")))
        }

        // Check if JSON
        if (rawContent.trim().startsWith("{")) {
            return parseJsonImport(rawContent, existingTransactions)
        }

        val headerLine = lines.first()
        val isHeaderPresent = headerLine.lowercase().contains("title") || headerLine.lowercase().contains("amount")
        val dataRows = if (isHeaderPresent) lines.drop(1) else lines

        dataRows.forEachIndexed { idx, line ->
            val rowNumber = if (isHeaderPresent) idx + 2 else idx + 1
            val cols = parseCsvLine(line)

            if (cols.size < 4) {
                errorList.add(ImportRowError(rowNumber, line, "Insufficient columns. Expected at least date, title, type, amount"))
                return@forEachIndexed
            }

            val dateStr = cols.getOrNull(0)?.trim() ?: ""
            val titleStr = cols.getOrNull(1)?.trim() ?: ""
            val typeStr = cols.getOrNull(2)?.trim()?.lowercase() ?: "expense"
            val amountRaw = cols.getOrNull(3)?.trim() ?: ""
            val catStr = cols.getOrNull(5)?.trim() ?: cols.getOrNull(4)?.trim() ?: "Food & Dining"
            val methodStr = cols.getOrNull(6)?.trim() ?: "UPI"
            val accountStr = cols.getOrNull(7)?.trim() ?: "Personal"
            val noteStr = cols.getOrNull(8)?.trim() ?: ""

            // Validate Title
            if (titleStr.isBlank()) {
                errorList.add(ImportRowError(rowNumber, line, "Title cannot be empty"))
                return@forEachIndexed
            }

            // Validate Amount
            val cleanAmountNum = amountRaw.replace("$", "").replace("₹", "").replace("€", "").replace(",", "")
            val parsedAmount = cleanAmountNum.toDoubleOrNull()
            if (parsedAmount == null || parsedAmount <= 0) {
                errorList.add(ImportRowError(rowNumber, line, "Invalid amount: '$amountRaw'. Expected positive number (e.g. 150.0)"))
                return@forEachIndexed
            }

            // Validate Date
            val parsedMillis = try {
                dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            // Validate Type
            val parsedType = if (typeStr == "income" || typeStr == "inflow") TransactionType.INCOME else TransactionType.EXPENSE
            val parsedScope = if (accountStr.equals("family", ignoreCase = true)) FinanceScope.FAMILY else FinanceScope.PERSONAL

            val newTx = TransactionEntity(
                title = titleStr,
                amount = parsedAmount,
                type = parsedType,
                category = if (catStr.isBlank()) "Food & Dining" else catStr,
                paymentMethod = if (methodStr.isBlank()) "UPI" else methodStr,
                note = noteStr,
                dateMillis = parsedMillis,
                financeScope = parsedScope,
                syncStatus = "PENDING_CREATE",
                updatedAt = System.currentTimeMillis()
            )

            // Duplicate Detection
            val isDuplicate = existingTransactions.any { existing ->
                existing.title.equals(newTx.title, ignoreCase = true) &&
                Math.abs(existing.amount - newTx.amount) < 0.01 &&
                existing.type == newTx.type &&
                dateFormat.format(Date(existing.dateMillis)) == dateFormat.format(Date(newTx.dateMillis))
            }

            if (isDuplicate) {
                duplicateList.add(newTx)
            } else {
                validList.add(newTx)
            }
        }

        return ImportValidationResult(
            totalFound = dataRows.size,
            validTransactions = validList,
            duplicateTransactions = duplicateList,
            invalidRows = errorList
        )
    }

    private fun parseJsonImport(jsonContent: String, existingTransactions: List<TransactionEntity>): ImportValidationResult {
        val validList = mutableListOf<TransactionEntity>()
        val duplicateList = mutableListOf<TransactionEntity>()
        val errorList = mutableListOf<ImportRowError>()

        try {
            val root = JSONObject(jsonContent)
            val txArray = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until txArray.length()) {
                val obj = txArray.optJSONObject(i)
                if (obj == null) {
                    errorList.add(ImportRowError(i + 1, "", "Invalid JSON record"))
                    continue
                }

                val title = obj.optString("title", "")
                val amount = obj.optDouble("amount", 0.0)
                val typeStr = obj.optString("type", "EXPENSE")
                val cat = obj.optString("category", "Food & Dining")
                val pm = obj.optString("paymentMethod", "UPI")
                val note = obj.optString("note", "")
                val dateMillis = obj.optLong("dateMillis", System.currentTimeMillis())
                val scopeStr = obj.optString("financeScope", "PERSONAL")

                if (title.isBlank() || amount <= 0) {
                    errorList.add(ImportRowError(i + 1, obj.toString(), "Missing title or invalid amount"))
                    continue
                }

                val tx = TransactionEntity(
                    title = title,
                    amount = amount,
                    type = if (typeStr.uppercase() == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                    category = cat,
                    paymentMethod = pm,
                    note = note,
                    dateMillis = dateMillis,
                    financeScope = if (scopeStr.uppercase() == "FAMILY") FinanceScope.FAMILY else FinanceScope.PERSONAL,
                    syncStatus = "PENDING_CREATE",
                    updatedAt = System.currentTimeMillis()
                )

                val isDup = existingTransactions.any { ex ->
                    ex.title.equals(tx.title, ignoreCase = true) &&
                    Math.abs(ex.amount - tx.amount) < 0.01 &&
                    ex.type == tx.type
                }

                if (isDup) duplicateList.add(tx) else validList.add(tx)
            }
        } catch (e: Exception) {
            errorList.add(ImportRowError(1, "", "Failed to parse JSON: ${e.localizedMessage}"))
        }

        return ImportValidationResult(
            totalFound = validList.size + duplicateList.size + errorList.size,
            validTransactions = validList,
            duplicateTransactions = duplicateList,
            invalidRows = errorList
        )
    }

    fun shareFile(context: Context, exportResult: ExportResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (exportResult.file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "csv" -> "text/csv"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "json" -> "application/json"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, exportResult.contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Zenith Export: ${exportResult.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Zenith Financial Data"))
    }

    fun openFile(context: Context, exportResult: ExportResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                exportResult.contentUri,
                when (exportResult.file.extension.lowercase()) {
                    "pdf" -> "application/pdf"
                    "csv" -> "text/csv"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "json" -> "application/json"
                    else -> "*/*"
                }
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()

        line.forEach { ch ->
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun escapeXml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
