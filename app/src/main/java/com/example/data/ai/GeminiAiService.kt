package com.example.data.ai

import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedVoiceExpense(
    val title: String,
    val amount: Double,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "Food & Dining",
    val paymentMethod: String = "UPI",
    val note: String = "",
    val scope: FinanceScope = FinanceScope.PERSONAL,
    val item: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val unitPrice: Double? = null
)

data class ParsedReceiptItem(
    val name: String,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0
)

data class ParsedReceipt(
    val merchantName: String,
    val receiptNumber: String? = null,
    val totalAmount: Double,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val category: String = "Shopping",
    val dateString: String = "Today",
    val timeString: String = "",
    val paymentMethod: String = "UPI",
    val itemsSummary: String = "",
    val items: List<ParsedReceiptItem> = emptyList(),
    val rawText: String = ""
)

// Maintained for backward compatibility
enum class VoiceAssistantIntent {
    LOG_TRANSACTION
}

data class VoiceAssistantResponse(
    val intent: VoiceAssistantIntent = VoiceAssistantIntent.LOG_TRANSACTION,
    val spokenText: String = "",
    val displayText: String = "",
    val parsedExpense: ParsedVoiceExpense? = null
)

object GeminiAiService {

    /**
     * Parses a spoken or typed financial transaction command in English.
     * Extracts Title, Amount, Type (EXPENSE vs INCOME), Category, and Payment Method.
     */
    suspend fun parseVoiceCommand(prompt: String): ParsedVoiceExpense = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackParseVoiceCommand(prompt)
        }
        try {
            val systemInstruction = """
                You are a domain-expert financial transaction parser for Zenith CashFlow, an intelligent personal & family finance app.
                The user will provide a transaction command in natural English, spoken voice transcription, or Romanized Tanglish (Tamil phrases written strictly in English/Latin alphabet).
                
                PARSING RULES:
                1. Language: Always output ALL JSON values (title, note, category, payment method) strictly in clean, professional English. Never use Tamil script characters.
                2. Transaction Type:
                   - "INCOME": salary, sambalam, freelance, varavu, stipend, bonus, cashback, dividend, received money, credited, refund, interest, allowance, profit.
                   - "EXPENSE": spent, paid, bought, purchase, ordered, bill, recharge, fee, rent, selavu, kuduthen, vanginen, kattinen, subscription.
                3. Scope:
                   - "FAMILY": mentions of family, vault, shared, home, split, joint, house, parents, roommates.
                   - "PERSONAL": default unless family/shared context is clear.
                4. Title: Concise, clear standard English title (e.g., "Lunch", "Movie Tickets", "Monthly Salary", "House Rent", "Medicines for Father", "Swiggy Order", "Rapido Ride", "Electricity Bill", "Groceries", "Tea & Snacks").
                5. Amount: Numeric value (e.g., 250.0). Accurately convert numeric abbreviations and words:
                   - "50k" -> 50000.0, "2.5k" -> 2500.0
                   - "1.5 lakh" / "1.5 lac" -> 150000.0, "1 crore" -> 10000000.0
                   - Romanized number words: "nooru" -> 100.0, "irunooru" -> 200.0, "munnooru" -> 300.0, "ainooru" -> 500.0, "aayiram" -> 1000.0, "rendaayiram" -> 2000.0, "anjaayiram" -> 5000.0, "pathaayiram" -> 10000.0.
                6. Category: Choose best from:
                   - "Food & Dining" (restaurants, delivery, cafes, groceries/dining, tea, snacks, swiggy, zomato)
                   - "Transportation" (fuel, petrol, diesel, cab, auto, metro, train, flight, rapido, uber, ola, toll, parking)
                   - "Shopping" (groceries, clothing, electronics, ecommerce, blinkit, zepto, instamart, amazon, flipkart)
                   - "Entertainment" (movies, cinema, streaming, ott, netflix, spotify, games, outings)
                   - "Bills & Utilities" (electricity, water, gas, cylinder, wifi, internet, mobile recharge, dth)
                   - "Housing & Rent" (house rent, apartment maintenance, property tax)
                   - "Healthcare" (medicines, pharmacy, doctor consultation, hospital, gym, wellness)
                   - "Education" (school fees, college fees, books, courses, tuition)
                   - "Salary & Income" (salary, wages, freelance, bonus, consulting, stipend)
                   - "Investments" (stocks, mutual funds, sip, crypto, gold, dividends, cashback)
                   - "Other" (miscellaneous, gifts, donations, transfers)
                7. Payment Method: Choose best from ("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer"). Default to "UPI".
                
                EXAMPLES:
                1. "Spent 350 for lunch via UPI" -> {"title": "Lunch", "amount": 350.0, "type": "EXPENSE", "category": "Food & Dining", "paymentMethod": "UPI", "scope": "PERSONAL", "note": "Spent 350 for lunch via UPI"}
                2. "Innaiku movie ki 250 selavu" -> {"title": "Movie Tickets", "amount": 250.0, "type": "EXPENSE", "category": "Entertainment", "paymentMethod": "UPI", "scope": "PERSONAL", "note": "Innaiku movie ki 250 selavu"}
                3. "Paid 1200 for electricity bill cash" -> {"title": "Electricity Bill", "amount": 1200.0, "type": "EXPENSE", "category": "Bills & Utilities", "paymentMethod": "Cash", "scope": "PERSONAL", "note": "Paid 1200 for electricity bill cash"}
                4. "Received 50k salary from office in bank" -> {"title": "Monthly Salary", "amount": 50000.0, "type": "INCOME", "category": "Salary & Income", "paymentMethod": "Bank Transfer", "scope": "PERSONAL", "note": "Received 50k salary from office in bank"}
                5. "Kadaila provision samantham 1200 selavu cash" -> {"title": "Provisions & Groceries", "amount": 1200.0, "type": "EXPENSE", "category": "Shopping", "paymentMethod": "Cash", "scope": "PERSONAL", "note": "Kadaila provision samantham 1200 selavu cash"}
                6. "Family groceries 2.5k credit card" -> {"title": "Family Groceries", "amount": 2500.0, "type": "EXPENSE", "category": "Shopping", "paymentMethod": "Credit Card", "scope": "FAMILY", "note": "Family groceries 2.5k credit card"}
                7. "Veetu vaadagai 15000 family vault bank transfer" -> {"title": "House Rent", "amount": 15000.0, "type": "EXPENSE", "category": "Housing & Rent", "paymentMethod": "Bank Transfer", "scope": "FAMILY", "note": "Veetu vaadagai 15000 family vault bank transfer"}
                8. "Appavukku marundhu vanginen 450 PhonePe" -> {"title": "Medicines for Father", "amount": 450.0, "type": "EXPENSE", "category": "Healthcare", "paymentMethod": "UPI", "scope": "FAMILY", "note": "Appavukku marundhu vanginen 450 PhonePe"}
                9. "Vandi petrol 500 Google Pay" -> {"title": "Petrol / Fuel", "amount": 500.0, "type": "EXPENSE", "category": "Transportation", "paymentMethod": "UPI", "scope": "PERSONAL", "note": "Vandi petrol 500 Google Pay"}
                10. "Ammavukku send panen 3000 bank transfer" -> {"title": "Money Sent to Mother", "amount": 3000.0, "type": "EXPENSE", "category": "Other", "paymentMethod": "Bank Transfer", "scope": "FAMILY", "note": "Ammavukku send panen 3000 bank transfer"}
                11. "Tea and snacks ainooru selavu cash" -> {"title": "Tea & Snacks", "amount": 500.0, "type": "EXPENSE", "category": "Food & Dining", "paymentMethod": "Cash", "scope": "PERSONAL", "note": "Tea and snacks ainooru selavu cash"}
                12. "Swiggy dinner 450 UPI" -> {"title": "Swiggy Food Order", "amount": 450.0, "type": "EXPENSE", "category": "Food & Dining", "paymentMethod": "UPI", "scope": "PERSONAL", "note": "Swiggy dinner 450 UPI"}
                13. "Dinner with roommates 800 split" -> {"title": "Dinner with Roommates", "amount": 800.0, "type": "EXPENSE", "category": "Food & Dining", "paymentMethod": "UPI", "scope": "FAMILY", "note": "Dinner with roommates 800 split"}
                14. "Got 200 cashback on Google Pay" -> {"title": "Cashback", "amount": 200.0, "type": "INCOME", "category": "Investments", "paymentMethod": "UPI", "scope": "PERSONAL", "note": "Got 200 cashback on Google Pay"}
                
                Return plain JSON only without markdown formatting.
            """.trimIndent()
            val responseText = callGeminiApi(apiKey, systemInstruction, prompt)
            val jsonClean = responseText.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(jsonClean)
            val title = jsonObj.optString("title", "Voice Entry")
            val amount = jsonObj.optDouble("amount", 0.0)
            val typeStr = jsonObj.optString("type", "EXPENSE")
            val type = if (typeStr.uppercase() == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
            val category = jsonObj.optString("category", if (type == TransactionType.INCOME) "Salary & Income" else "Food & Dining")
            val paymentMethod = jsonObj.optString("paymentMethod", "UPI")
            val scopeStr = jsonObj.optString("scope", "PERSONAL")
            val scope = if (scopeStr.uppercase() == "FAMILY") FinanceScope.FAMILY else FinanceScope.PERSONAL
            val note = jsonObj.optString("note", prompt)

            if (amount > 0) {
                ParsedVoiceExpense(
                    title = title,
                    amount = amount,
                    type = type,
                    category = category,
                    paymentMethod = paymentMethod,
                    note = note,
                    scope = scope
                )
            } else {
                fallbackParseVoiceCommand(prompt)
            }
        } catch (e: Exception) {
            fallbackParseVoiceCommand(prompt)
        }
    }

    suspend fun parseAudioCommand(audioBase64: String): ParsedVoiceExpense = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackParseVoiceCommand("Audio Expense 150")
        }
        try {
            val systemInstruction = """
                You are a financial transaction voice parser. Transcribe the spoken audio in English and extract the transaction into ONLY a JSON object:
                - "title": concise English title (e.g. "Lunch", "Groceries", "Salary")
                - "amount": total amount as number (e.g. 250.0)
                - "type": "EXPENSE" or "INCOME"
                - "category": ("Food & Dining", "Transportation", "Shopping", "Entertainment", "Bills & Utilities", "Housing & Rent", "Healthcare", "Education", "Salary & Income", "Investments", "Other")
                - "paymentMethod": ("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer")
                - "note": transcription in English
                Return plain JSON only without markdown formatting.
            """.trimIndent()

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 4000
            conn.readTimeout = 5000

            val requestPayload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 256)
                    put("responseMimeType", "application/json")
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "audio/mp4")
                                put("data", audioBase64)
                            })
                        })
                    })
                }))
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestPayload.toString())
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val responseString = conn.inputStream.bufferedReader().readText()
                val respObj = JSONObject(responseString)
                val candidates = respObj.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text") ?: ""
                val jsonClean = text.replace("```json", "").replace("```", "").trim()
                val jsonObj = JSONObject(jsonClean)
                val title = jsonObj.optString("title", "Voice Entry")
                val amount = jsonObj.optDouble("amount", 0.0)
                val typeStr = jsonObj.optString("type", "EXPENSE")
                val type = if (typeStr.uppercase() == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                val category = jsonObj.optString("category", if (type == TransactionType.INCOME) "Salary & Income" else "Food & Dining")
                val paymentMethod = jsonObj.optString("paymentMethod", "UPI")
                val note = jsonObj.optString("note", "")

                ParsedVoiceExpense(
                    title = title,
                    amount = if (amount > 0) amount else 100.0,
                    type = type,
                    category = category,
                    paymentMethod = paymentMethod,
                    note = note
                )
            } else {
                fallbackParseVoiceCommand("Voice Transaction 100")
            }
        } catch (e: Exception) {
            fallbackParseVoiceCommand("Voice Transaction 100")
        }
    }

    suspend fun parseReceiptOcr(receiptText: String): ParsedReceipt = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackParseReceipt(receiptText)
        }
        try {
            val systemInstruction = """
                You are a receipt scanner OCR AI. Analyze the receipt text and return ONLY a JSON object:
                - "merchantName": name of store or restaurant
                - "receiptNumber": invoice / receipt number or null
                - "dateString": detected date or "Today"
                - "timeString": detected time (e.g. "14:22") or ""
                - "category": ("Food & Dining", "Shopping", "Transportation", "Bills & Utilities", "Healthcare", "Other")
                - "paymentMethod": ("UPI", "Cash", "Card", "Bank Transfer", "Other")
                - "subtotal": subtotal amount as number
                - "discount": discount amount as number or 0.0
                - "tax": tax / GST amount as number or 0.0
                - "totalAmount": final total paid as number
                - "itemsSummary": concise 1-line comma separated items list
                - "items": array of objects with:
                    - "name": item name
                    - "quantity": number (e.g. 1.0, 2.0)
                    - "unitPrice": unit price as number
                    - "totalPrice": line total as number
                Return plain JSON only without markdown formatting.
            """.trimIndent()
            val responseText = callGeminiApi(apiKey, systemInstruction, receiptText)
            val jsonClean = responseText.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(jsonClean)
            val merchant = jsonObj.optString("merchantName", "Store Purchase")
            val receiptNum = jsonObj.optString("receiptNumber", "").ifBlank { null }
            val dateStr = jsonObj.optString("dateString", "Today")
            val timeStr = jsonObj.optString("timeString", "")
            val category = jsonObj.optString("category", "Shopping")
            val paymentMethod = jsonObj.optString("paymentMethod", "UPI")
            val subtotal = jsonObj.optDouble("subtotal", 0.0)
            val discount = jsonObj.optDouble("discount", 0.0)
            val tax = jsonObj.optDouble("tax", 0.0)
            val total = jsonObj.optDouble("totalAmount", 0.0)
            val itemsSummary = jsonObj.optString("itemsSummary", "")

            val itemsList = mutableListOf<ParsedReceiptItem>()
            val itemsArray = jsonObj.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.optJSONObject(i) ?: continue
                    val name = itemObj.optString("name", "Item")
                    val qty = itemObj.optDouble("quantity", 1.0)
                    val unitPrice = itemObj.optDouble("unitPrice", 0.0)
                    val lineTotal = itemObj.optDouble("totalPrice", qty * unitPrice)
                    itemsList.add(ParsedReceiptItem(name, qty, unitPrice, lineTotal))
                }
            }

            ParsedReceipt(
                merchantName = merchant,
                receiptNumber = receiptNum,
                totalAmount = total,
                subtotal = if (subtotal > 0) subtotal else total,
                discount = discount,
                tax = tax,
                category = category,
                dateString = dateStr,
                timeString = timeStr,
                paymentMethod = paymentMethod,
                itemsSummary = itemsSummary.ifBlank { itemsList.joinToString(", ") { it.name } },
                items = itemsList,
                rawText = receiptText
            )
        } catch (e: Exception) {
            fallbackParseReceipt(receiptText)
        }
    }

    private fun fallbackParseReceipt(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchant = lines.firstOrNull { it.length > 2 && !it.startsWith("Date") && !it.startsWith("Time") } ?: "Store Purchase"

        val grandTotalRegex = Regex("""(?:grand\s*total|total\s*paid|amount\s*paid|net\s*payable|total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val grandMatch = grandTotalRegex.find(rawText)
        val detectedTotal = grandMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: run {
            val numbers = Regex("""\d+\.\d{2}""").findAll(rawText).mapNotNull { it.value.toDoubleOrNull() }.toList()
            numbers.maxOrNull() ?: 0.0
        }

        val subtotalRegex = Regex("""(?:subtotal|sub\s*total|sub-total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val subtotal = subtotalRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: detectedTotal

        val taxRegex = Regex("""(?:tax|gst|vat)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val tax = taxRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val discountRegex = Regex("""(?:discount|disc|saved)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val discount = discountRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
        val detectedDate = dateRegex.find(rawText)?.value ?: "Today"

        val timeRegex = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?)\b""")
        val detectedTime = timeRegex.find(rawText)?.value ?: ""

        val category = when {
            rawText.contains("restaurant", ignoreCase = true) || rawText.contains("cafe", ignoreCase = true) ||
            rawText.contains("kitchen", ignoreCase = true) || rawText.contains("bistro", ignoreCase = true) -> "Food & Dining"
            rawText.contains("fuel", ignoreCase = true) || rawText.contains("petrol", ignoreCase = true) -> "Transportation"
            rawText.contains("pharmacy", ignoreCase = true) || rawText.contains("medical", ignoreCase = true) -> "Healthcare"
            else -> "Shopping"
        }

        val paymentMethod = when {
            rawText.contains("upi", ignoreCase = true) || rawText.contains("gpay", ignoreCase = true) -> "UPI"
            rawText.contains("cash", ignoreCase = true) -> "Cash"
            rawText.contains("card", ignoreCase = true) || rawText.contains("visa", ignoreCase = true) -> "Card"
            else -> "UPI"
        }

        return ParsedReceipt(
            merchantName = merchant,
            receiptNumber = null,
            totalAmount = detectedTotal,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            category = category,
            dateString = detectedDate,
            timeString = detectedTime,
            paymentMethod = paymentMethod,
            itemsSummary = "Scanned Purchase",
            items = emptyList(),
            rawText = rawText
        )
    }

    suspend fun getFinancialCoachAdvice(totalIncome: Double, totalExpense: Double, topExpenseCategory: String): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Aim to save at least 20% of your income each month. Keep non-essential expenses in $topExpenseCategory measured to build a strong savings cushion."
        }
        try {
            val systemInstruction = "You are an empathetic financial coach. Provide a concise, actionable 2-sentence piece of financial advice in English based on the user's spending."
            val prompt = "Monthly Income: ₹$totalIncome, Total Expenses: ₹$totalExpense, Top Expense Category: $topExpenseCategory. Give 2 crisp tips."
            val response = callGeminiApi(apiKey, systemInstruction, prompt)
            response.trim().ifBlank { "Track your recurring bills carefully and build an emergency fund of 3-6 months expenses." }
        } catch (e: Exception) {
            "Maintain a healthy balance by directing discretionary spending toward your savings goals."
        }
    }

    private fun callGeminiApi(apiKey: String, systemInstruction: String, promptText: String): String {
        val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash")
        var lastError: Exception? = null

        for (model in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 5000

                val requestPayload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 512)
                    })
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        })
                    }))
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(requestPayload.toString())
                    writer.flush()
                }

                if (conn.responseCode == 200) {
                    val responseString = conn.inputStream.bufferedReader().readText()
                    val respObj = JSONObject(responseString)
                    val candidates = respObj.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    return firstPart?.optString("text") ?: ""
                } else if (conn.responseCode == 404) {
                    continue
                } else {
                    lastError = RuntimeException("Gemini API error code: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Gemini API request failed across all candidate models")
    }

    // --- Fast English Rule-Based Fallback Parser ---

    private data class ItemMeta(
        val standardName: String,
        val category: String,
        val defaultAmount: Double
    )

    private val ENGLISH_ITEM_DICTIONARY = mapOf(
        // Income & Investments
        "salary" to ItemMeta("Monthly Salary", "Salary & Income", 35000.0),
        "sambalam" to ItemMeta("Monthly Salary", "Salary & Income", 35000.0),
        "freelance" to ItemMeta("Freelance Payment", "Salary & Income", 10000.0),
        "bonus" to ItemMeta("Bonus", "Salary & Income", 5000.0),
        "stipend" to ItemMeta("Stipend", "Salary & Income", 8000.0),
        "allowance" to ItemMeta("Allowance", "Salary & Income", 3000.0),
        "cashback" to ItemMeta("Cashback", "Investments", 200.0),
        "dividend" to ItemMeta("Dividend", "Investments", 1000.0),
        "interest" to ItemMeta("Interest Income", "Investments", 500.0),
        "mutual fund" to ItemMeta("Mutual Fund SIP", "Investments", 2500.0),
        "stocks" to ItemMeta("Stock Investment", "Investments", 5000.0),

        // Food & Dining / Delivery
        "swiggy" to ItemMeta("Swiggy Order", "Food & Dining", 350.0),
        "zomato" to ItemMeta("Zomato Order", "Food & Dining", 380.0),
        "lunch" to ItemMeta("Lunch", "Food & Dining", 200.0),
        "dinner" to ItemMeta("Dinner", "Food & Dining", 350.0),
        "breakfast" to ItemMeta("Breakfast", "Food & Dining", 100.0),
        "saapadu" to ItemMeta("Food & Dining", "Food & Dining", 150.0),
        "kaalaila" to ItemMeta("Morning Breakfast", "Food & Dining", 80.0),
        "mathiyam" to ItemMeta("Afternoon Lunch", "Food & Dining", 150.0),
        "iravu" to ItemMeta("Night Dinner", "Food & Dining", 180.0),
        "hotel" to ItemMeta("Restaurant Dining", "Food & Dining", 450.0),
        "coffee" to ItemMeta("Coffee", "Food & Dining", 80.0),
        "tea" to ItemMeta("Tea", "Food & Dining", 20.0),
        "chai" to ItemMeta("Chai", "Food & Dining", 20.0),
        "pizza" to ItemMeta("Pizza", "Food & Dining", 400.0),
        "burger" to ItemMeta("Burger", "Food & Dining", 180.0),
        "biryani" to ItemMeta("Biryani", "Food & Dining", 250.0),
        "snacks" to ItemMeta("Snacks", "Food & Dining", 120.0),
        "bakery" to ItemMeta("Bakery Items", "Food & Dining", 150.0),

        // Shopping & Quick Commerce
        "groceries" to ItemMeta("Groceries", "Shopping", 600.0),
        "grocery" to ItemMeta("Groceries", "Shopping", 600.0),
        "blinkit" to ItemMeta("Blinkit Groceries", "Shopping", 450.0),
        "zepto" to ItemMeta("Zepto Groceries", "Shopping", 350.0),
        "instamart" to ItemMeta("Instamart Delivery", "Shopping", 400.0),
        "bigbasket" to ItemMeta("BigBasket Groceries", "Shopping", 1200.0),
        "dunzo" to ItemMeta("Dunzo Delivery", "Shopping", 250.0),
        "supermarket" to ItemMeta("Supermarket", "Shopping", 800.0),
        "dmart" to ItemMeta("D-Mart Shopping", "Shopping", 1500.0),
        "d-mart" to ItemMeta("D-Mart Shopping", "Shopping", 1500.0),
        "reliance fresh" to ItemMeta("Reliance Fresh", "Shopping", 900.0),
        "kadai" to ItemMeta("Store Purchase", "Shopping", 300.0),
        "maligai" to ItemMeta("Provisions / Maligai", "Shopping", 800.0),
        "provisions" to ItemMeta("Provisions & Groceries", "Shopping", 1500.0),
        "provision" to ItemMeta("Provisions & Groceries", "Shopping", 1000.0),
        "vegetables" to ItemMeta("Vegetables", "Shopping", 150.0),
        "fruits" to ItemMeta("Fruits", "Shopping", 200.0),
        "apple" to ItemMeta("Apples", "Shopping", 120.0),
        "onion" to ItemMeta("Onions", "Shopping", 60.0),
        "tomato" to ItemMeta("Tomatoes", "Shopping", 40.0),
        "milk" to ItemMeta("Milk", "Food & Dining", 35.0),
        "bread" to ItemMeta("Bread & Dairy", "Food & Dining", 45.0),
        "eggs" to ItemMeta("Eggs", "Food & Dining", 70.0),
        "chicken" to ItemMeta("Chicken", "Food & Dining", 220.0),
        "mutton" to ItemMeta("Mutton", "Food & Dining", 800.0),
        "fish" to ItemMeta("Fish", "Food & Dining", 350.0),
        "amazon" to ItemMeta("Amazon Shopping", "Shopping", 1200.0),
        "flipkart" to ItemMeta("Flipkart Shopping", "Shopping", 1100.0),
        "myntra" to ItemMeta("Myntra Fashion", "Shopping", 1500.0),
        "meesho" to ItemMeta("Meesho Order", "Shopping", 500.0),
        "clothes" to ItemMeta("Clothing", "Shopping", 1800.0),
        "shoes" to ItemMeta("Footwear", "Shopping", 1400.0),

        // Transportation & Rides
        "rapido" to ItemMeta("Rapido Ride", "Transportation", 75.0),
        "uber" to ItemMeta("Uber Ride", "Transportation", 250.0),
        "ola" to ItemMeta("Ola Ride", "Transportation", 200.0),
        "cab" to ItemMeta("Cab Ride", "Transportation", 250.0),
        "auto" to ItemMeta("Auto Fare", "Transportation", 100.0),
        "bus" to ItemMeta("Bus Ticket", "Transportation", 50.0),
        "train" to ItemMeta("Train Ticket", "Transportation", 300.0),
        "irctc" to ItemMeta("IRCTC Train Booking", "Transportation", 650.0),
        "flight" to ItemMeta("Flight Ticket", "Transportation", 4500.0),
        "metro" to ItemMeta("Metro Fare", "Transportation", 40.0),
        "petrol" to ItemMeta("Petrol / Fuel", "Transportation", 500.0),
        "fuel" to ItemMeta("Fuel", "Transportation", 500.0),
        "diesel" to ItemMeta("Diesel", "Transportation", 1000.0),
        "vandi" to ItemMeta("Vehicle Maintenance / Fuel", "Transportation", 400.0),
        "parking" to ItemMeta("Parking Fee", "Transportation", 50.0),
        "toll" to ItemMeta("Highway Toll", "Transportation", 120.0),

        // Entertainment
        "movie" to ItemMeta("Movie Tickets", "Entertainment", 350.0),
        "cinema" to ItemMeta("Cinema", "Entertainment", 350.0),
        "bookmyshow" to ItemMeta("BookMyShow Movie Tickets", "Entertainment", 450.0),
        "netflix" to ItemMeta("Netflix Subscription", "Entertainment", 649.0),
        "spotify" to ItemMeta("Spotify Subscription", "Entertainment", 119.0),
        "prime" to ItemMeta("Amazon Prime", "Entertainment", 299.0),
        "hotstar" to ItemMeta("Disney Hotstar", "Entertainment", 299.0),
        "youtube" to ItemMeta("YouTube Premium", "Entertainment", 149.0),

        // Utilities & Bills
        "electricity" to ItemMeta("Electricity Bill", "Bills & Utilities", 1200.0),
        "power bill" to ItemMeta("Electricity Bill", "Bills & Utilities", 1200.0),
        "current bill" to ItemMeta("Electricity Bill", "Bills & Utilities", 1200.0),
        "tneb" to ItemMeta("TNEB Power Bill", "Bills & Utilities", 1400.0),
        "bescom" to ItemMeta("BESCOM Electricity", "Bills & Utilities", 1300.0),
        "water" to ItemMeta("Water Bill", "Bills & Utilities", 300.0),
        "gas" to ItemMeta("Gas Cylinder", "Bills & Utilities", 950.0),
        "cylinder" to ItemMeta("LPG Gas Cylinder", "Bills & Utilities", 950.0),
        "indane" to ItemMeta("Indane Gas", "Bills & Utilities", 950.0),
        "wifi" to ItemMeta("Wi-Fi / Internet Bill", "Bills & Utilities", 800.0),
        "internet" to ItemMeta("Internet Bill", "Bills & Utilities", 800.0),
        "airtel" to ItemMeta("Airtel Bill / Recharge", "Bills & Utilities", 499.0),
        "jio" to ItemMeta("Jio Bill / Recharge", "Bills & Utilities", 399.0),
        "vi" to ItemMeta("Vodafone Idea Recharge", "Bills & Utilities", 349.0),
        "recharge" to ItemMeta("Mobile Recharge", "Bills & Utilities", 299.0),
        "mobile" to ItemMeta("Mobile Bill", "Bills & Utilities", 399.0),

        // Housing & Rent
        "rent" to ItemMeta("House Rent", "Housing & Rent", 12000.0),
        "veedu" to ItemMeta("House Rent", "Housing & Rent", 12000.0),
        "vaadagai" to ItemMeta("House Rent", "Housing & Rent", 12000.0),
        "maintenance" to ItemMeta("Maintenance Fee", "Housing & Rent", 2000.0),

        // Healthcare
        "medicine" to ItemMeta("Medicines", "Healthcare", 350.0),
        "marundhu" to ItemMeta("Medicines / Pharmacy", "Healthcare", 350.0),
        "pharmacy" to ItemMeta("Pharmacy", "Healthcare", 400.0),
        "apollo" to ItemMeta("Apollo Pharmacy", "Healthcare", 450.0),
        "medplus" to ItemMeta("Medplus Pharmacy", "Healthcare", 400.0),
        "hospital" to ItemMeta("Medical / Doctor Fee", "Healthcare", 800.0),
        "doctor" to ItemMeta("Doctor Consultation", "Healthcare", 500.0),
        "gym" to ItemMeta("Gym Membership", "Healthcare", 1500.0),
        "cult" to ItemMeta("Cult.fit Fitness", "Healthcare", 2000.0),

        // Education
        "books" to ItemMeta("Books", "Education", 450.0),
        "course" to ItemMeta("Course / Tuition", "Education", 2500.0),
        "tuition" to ItemMeta("Tuition Fee", "Education", 1500.0),
        "school" to ItemMeta("School Fees", "Education", 8000.0),
        "college" to ItemMeta("College Fees", "Education", 25000.0),

        // Everyday & Tanglish Extensions
        "tea" to ItemMeta("Tea & Snacks", "Food & Dining", 30.0),
        "chai" to ItemMeta("Tea & Snacks", "Food & Dining", 30.0),
        "vadai" to ItemMeta("Tea & Snacks", "Food & Dining", 40.0),
        "samosa" to ItemMeta("Tea & Snacks", "Food & Dining", 40.0),
        "dosa" to ItemMeta("Breakfast / Dosa", "Food & Dining", 90.0),
        "idli" to ItemMeta("Breakfast / Idli", "Food & Dining", 60.0),
        "meals" to ItemMeta("Lunch Meals", "Food & Dining", 140.0),
        "parotta" to ItemMeta("Dinner / Parotta", "Food & Dining", 120.0),
        "juice" to ItemMeta("Beverages / Juice", "Food & Dining", 60.0),
        "broadband" to ItemMeta("Broadband Bill", "Bills & Utilities", 799.0),
        "water can" to ItemMeta("Drinking Water", "Bills & Utilities", 80.0),
        "stationery" to ItemMeta("Stationery", "Education", 200.0),
        "haircut" to ItemMeta("Haircut & Grooming", "Healthcare", 200.0),
        "salon" to ItemMeta("Salon & Grooming", "Healthcare", 350.0),
        "service" to ItemMeta("Vehicle Service", "Transportation", 2500.0),
        "tyre" to ItemMeta("Vehicle Maintenance", "Transportation", 400.0),
        "puncture" to ItemMeta("Puncture Repair", "Transportation", 100.0),
        "gift" to ItemMeta("Gift", "Other", 1000.0),
        "donation" to ItemMeta("Donation", "Other", 500.0),
        "courier" to ItemMeta("Courier Service", "Bills & Utilities", 150.0),
        "post" to ItemMeta("Postal Service", "Bills & Utilities", 100.0)
    )

    fun fallbackParseVoiceCommand(prompt: String): ParsedVoiceExpense {
        val lower = prompt.lowercase().trim()

        // 1. Scope Detection (Family Vault vs Personal)
        val isFamilyScope = lower.contains("family") || lower.contains("shared") ||
            lower.contains("vault") || lower.contains("household") ||
            lower.contains("split") || lower.contains("joint") ||
            lower.contains("our ") || lower.endsWith("our") ||
            lower.contains("roommate") || lower.contains("veetu") ||
            lower.contains("amma") || lower.contains("appa") ||
            lower.contains("veedu") || lower.contains("vaadagai")
        val scope = if (isFamilyScope) FinanceScope.FAMILY else FinanceScope.PERSONAL

        // 2. Explicit Income vs Expense Detection
        val isCreditCard = lower.contains("credit card") || lower.contains("credit-card")
        val isIncome = !isCreditCard && (
            lower.contains("salary") || lower.contains("sambalam") ||
            lower.contains("varavu") || lower.contains("received") ||
            lower.contains("earned") || lower.contains("income") ||
            lower.contains("got paid") || lower.contains("credited") ||
            lower.contains("bonus") || lower.contains("cashback") ||
            lower.contains("freelance") || lower.contains("refund") ||
            lower.contains("dividend") || lower.contains("allowance") ||
            lower.contains("stipend") || lower.contains("panam vanthuchu") ||
            lower.contains("kaasu vanthuchu")
        )

        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 3. Payment Method Extraction
        val paymentMethod = when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("google pay") ||
            lower.contains("phonepe") || lower.contains("paytm") || lower.contains("scan") ||
            lower.contains("qr") || lower.contains("bhim") -> "UPI"
            lower.contains("cash") -> "Cash"
            lower.contains("credit card") || lower.contains("credit") || lower.contains("cc") -> "Credit Card"
            lower.contains("debit card") || lower.contains("debit") || lower.contains("card") -> "Debit Card"
            lower.contains("bank") || lower.contains("transfer") || lower.contains("netbanking") ||
            lower.contains("neft") || lower.contains("imps") -> "Bank Transfer"
            else -> "UPI"
        }

        // 4. Amount Extraction (Handling number words, 'k', 'thousand', 'lakh', 'crore' multipliers)
        var detectedAmount: Double? = null

        val numberWords = listOf(
            "iruvathaayiram" to 20000.0,
            "pathaayiram" to 10000.0,
            "anjaayiram" to 5000.0,
            "naalaayiram" to 4000.0,
            "moonaayiram" to 3000.0,
            "rendaayiram" to 2000.0,
            "aayiram" to 1000.0,
            "oru aayiram" to 1000.0,
            "thollaayiram" to 900.0,
            "ennooru" to 800.0,
            "elanooru" to 700.0,
            "arunooru" to 600.0,
            "ainooru" to 500.0,
            "naanooru" to 400.0,
            "munnooru" to 300.0,
            "irunooru" to 200.0,
            "nooru" to 100.0,
            "oru nooru" to 100.0,
            "two thousand" to 2000.0,
            "one thousand" to 1000.0,
            "five hundred" to 500.0
        )

        for ((word, wordVal) in numberWords) {
            if (lower.contains(word)) {
                detectedAmount = wordVal
                break
            }
        }

        if (detectedAmount == null || detectedAmount <= 0.0) {
            val multiplierRegex = Regex("""(?:(?:[$₹€£]|rs|rupees|inr|paid|spent|cost|of)\s*)?(\d+(?:\.\d+)?)\s*(k|thousand|lakh|lakhs|lac|lacs|crore|crores|cr)\b""", RegexOption.IGNORE_CASE)
            val multMatch = multiplierRegex.find(lower)
            if (multMatch != null) {
                val base = multMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = multMatch.groupValues[2].lowercase()
                val mult = when {
                    unit == "k" || unit == "thousand" -> 1000.0
                    unit.startsWith("la") -> 100000.0
                    unit.startsWith("cr") -> 10000000.0
                    else -> 1.0
                }
                if (base > 0) {
                    detectedAmount = base * mult
                }
            }
        }

        if (detectedAmount == null || detectedAmount <= 0.0) {
            val amountRegex = Regex("""(?:[$₹€£]|rs|rupees|inr|paid|spent|cost|of)\s*(\d+(?:\.\d{1,2})?)|(\d+(?:\.\d{1,2})?)\s*(?:rs|rupees|inr|bucks)?""", RegexOption.IGNORE_CASE)
            val allNumbers = Regex("""\b\d+(?:\.\d{1,2})?\b""").findAll(lower).mapNotNull { it.value.toDoubleOrNull() }.toList()

            val match = amountRegex.find(lower)
            if (match != null) {
                val num1 = match.groupValues[1].toDoubleOrNull()
                val num2 = match.groupValues[2].toDoubleOrNull()
                detectedAmount = num1 ?: num2
            }
            if (detectedAmount == null || detectedAmount <= 0.0) {
                detectedAmount = allNumbers.maxOrNull()
            }
        }

        // 5. Item and Category Matching
        var matchedMeta: ItemMeta? = null
        var bestKeyword = ""
        for ((key, meta) in ENGLISH_ITEM_DICTIONARY) {
            val matches = if (key.length <= 3) {
                Regex("""\b${Regex.escape(key)}\b""").containsMatchIn(lower)
            } else {
                lower.contains(key)
            }
            if (matches) {
                if (key.length > bestKeyword.length) {
                    bestKeyword = key
                    matchedMeta = meta
                }
            }
        }

        val finalAmount = detectedAmount ?: (matchedMeta?.defaultAmount ?: 100.0)
        val finalTitle = matchedMeta?.standardName ?: run {
            val clean = lower.replace(Regex("""\b(spent|paid|for|in|via|on|got|received|rs|rupees|upi|cash|card|bank|transfer|selavu|varavu|panam|kaasu|poten|kuduthen|vanginen|kattinen|innaiku|veetu|office|ku|la)\b"""), "").trim()
            if (clean.isNotBlank()) clean.replaceFirstChar { it.uppercase() } else (if (isIncome) "Income" else "Expense")
        }
        val finalCategory = if (isIncome) {
            matchedMeta?.category ?: "Salary & Income"
        } else {
            matchedMeta?.category ?: "Food & Dining"
        }

        return ParsedVoiceExpense(
            title = finalTitle,
            amount = finalAmount,
            type = type,
            category = finalCategory,
            paymentMethod = paymentMethod,
            note = prompt,
            scope = scope
        )
    }
}
