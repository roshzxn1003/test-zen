package com.example.data.ai

import com.example.data.models.TransactionType
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedVoiceExpense(
    val title: String,
    val amount: Double,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "Food & Dining",
    val paymentMethod: String = "UPI",
    val note: String = "",
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

object GeminiAiService {
    
    /**
     * Parses a spoken or typed financial transaction command in English, Tamil (தமிழ்), or Tanglish.
     * Extracts Item, Quantity, Unit, Amount, Category, Type, and Payment Method simultaneously in a single transaction.
     */
    suspend fun parseVoiceCommand(prompt: String): ParsedVoiceExpense = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackParseVoiceCommand(prompt)
        }
        try {
            val systemInstruction = """
                You are a financial parsing assistant. The user will input transaction details via voice in English, Tamil (தமிழ்), or Tanglish (a mix of Tamil and English words written in English script). Translate the intent accurately into English and extract the JSON values (Amount, Item, Expense/Income, Category). For example, if the input is 'Innaiku movie ki 250 selavu aachu' (Tanglish) or 'இன்று படத்திற்கு 250 செலவு' (Tamil), recognize it as an Expense of 250 for 'Movie' in the 'Entertainment' category.
                
                You must extract BOTH 'Item' and 'Quantity' simultaneously from a single voice command, along with amount, category, and payment method.
                
                Extract the data into ONLY a valid JSON object matching this schema:
                - "item": Standard clean English name of the item or service (e.g. "Movie", "Tomato", "Apple", "Onion", "Milk", "Tea", "Petrol", "Biryani", "Groceries", "Salary")
                - "quantity": Numeric count or quantity (e.g. 10 for "Pathu thakkali", 5 for "Add 5 apples", 2.0 for "2 kg onion", 1.0 for "Oru tea", 1.0 for "Innaiku movie ki 250").
                - "unit": Unit of measure if mentioned or implied (e.g. "pcs", "kg", "g", "L", "pkt", "bunch", "dozen", "cup", "trip", "month", "ticket"). Default to "pcs" for countable items, "ticket" for movie/cinema.
                - "unitPrice": Estimated or stated price per unit (number)
                - "amount": Pure numeric total monetary cost in rupees/currency without symbols (e.g. 50.0, 250.0, 500.0). If no total price is given in speech, estimate total based on unitPrice * quantity, or set an appropriate sensible amount so the transaction is immediately ready.
                - "title": Clean, concise transaction title (e.g. "Movie", "Tomato (10 pcs)", "Apple (5 pcs)", "Onion (2 kg)", "Lunch", "Petrol", "Monthly Salary")
                - "type": "EXPENSE" or "INCOME"
                - "category": Match best from ("Food & Dining", "Transportation", "Shopping", "Entertainment", "Bills & Utilities", "Housing & Rent", "Healthcare", "Education", "Salary & Income", "Investments", "Other")
                - "paymentMethod": Match from ("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer")
                - "note": Original user prompt verbatim
                
                Tamil / Tanglish Few-Shot Examples:
                1. "Innaiku movie ki 250 selavu aachu" / "இன்று படத்திற்கு 250 செலவு" -> {"item": "Movie", "quantity": 1, "unit": "ticket", "unitPrice": 250.0, "amount": 250.0, "title": "Movie", "category": "Entertainment", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Innaiku movie ki 250 selavu aachu"}
                2. "Pathu thakkali" / "10 தக்காளி" -> {"item": "Tomato", "quantity": 10, "unit": "pcs", "unitPrice": 5.0, "amount": 50.0, "title": "Tomato (10 pcs)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "Cash", "note": "Pathu thakkali"}
                3. "Add 5 apples" / "5 ஆப்பிள்" -> {"item": "Apple", "quantity": 5, "unit": "pcs", "unitPrice": 20.0, "amount": 100.0, "title": "Apple (5 pcs)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Add 5 apples"}
                4. "2 kg vengayam 60 rs" / "2 கிலோ வெங்காயம் 60 ரூபாய்" -> {"item": "Onion", "quantity": 2, "unit": "kg", "unitPrice": 30.0, "amount": 60.0, "title": "Onion (2 kg)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "UPI", "note": "2 kg vengayam 60 rs"}
                5. "Rendu biryani 400 gpay" / "இரண்டு பிரியாணி 400 gpay" -> {"item": "Biryani", "quantity": 2, "unit": "pcs", "unitPrice": 200.0, "amount": 400.0, "title": "Biryani (2 pcs)", "category": "Food & Dining", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Rendu biryani 400 gpay"}
                6. "Oru tea 15 rubai" / "ஒரு டீ 15 ரூபாய்" -> {"item": "Tea", "quantity": 1, "unit": "cup", "unitPrice": 15.0, "amount": 15.0, "title": "Tea (1 cup)", "category": "Food & Dining", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Oru tea 15 rubai"}
                7. "500 petrol phonepe" / "பெட்ரோல் 500 ரூபாய்" -> {"item": "Petrol / Fuel", "quantity": 1, "unit": "refill", "unitPrice": 500.0, "amount": 500.0, "title": "Petrol / Fuel", "category": "Transportation", "type": "EXPENSE", "paymentMethod": "UPI", "note": "500 petrol phonepe"}
                8. "Got salary 35000" / "சம்பளம் 35000 வந்தது" -> {"item": "Monthly Salary", "quantity": 1, "unit": "month", "unitPrice": 35000.0, "amount": 35000.0, "title": "Monthly Salary", "category": "Salary & Income", "type": "INCOME", "paymentMethod": "Bank Transfer", "note": "Got salary 35000"}
                
                Return plain JSON only without markdown formatting.
            """.trimIndent()
            val responseText = callGeminiApi(apiKey, systemInstruction, prompt)
            val jsonClean = responseText.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(jsonClean)
            val item = jsonObj.optString("item", "").ifBlank { null }
            val quantity = if (jsonObj.has("quantity")) jsonObj.optDouble("quantity", 1.0) else null
            val unit = jsonObj.optString("unit", "").ifBlank { null }
            val unitPrice = if (jsonObj.has("unitPrice")) jsonObj.optDouble("unitPrice", 0.0) else null
            val title = jsonObj.optString("title", item?.let { if (quantity != null && quantity > 1) "$it (${quantity.toInt()} ${unit ?: "pcs"})" else it } ?: "Voice Entry")
            val amount = jsonObj.optDouble("amount", 0.0)
            val typeStr = jsonObj.optString("type", "EXPENSE")
            val type = if (typeStr.uppercase() == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
            val category = jsonObj.optString("category", "Food & Dining")
            val paymentMethod = jsonObj.optString("paymentMethod", "UPI")
            val note = jsonObj.optString("note", prompt)
            
            if (amount > 0 || (quantity != null && quantity > 0)) {
                val finalAmount = if (amount > 0) amount else ((quantity ?: 1.0) * (unitPrice ?: 10.0))
                ParsedVoiceExpense(
                    title = title,
                    amount = finalAmount,
                    type = type,
                    category = category,
                    paymentMethod = paymentMethod,
                    note = note,
                    item = item,
                    quantity = quantity,
                    unit = unit,
                    unitPrice = unitPrice
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
                You are a financial parsing assistant. The user will input transaction details via voice/audio in English, Tamil (தமிழ்), or Tanglish (a mix of Tamil and English words written in English script). Translate the intent accurately into English and extract the JSON values (Amount, Item, Expense/Income, Category). For example, if the input is 'Innaiku movie ki 250 selavu aachu' (Tanglish) or 'இன்று படத்திற்கு 250 செலவு' (Tamil), recognize it as an Expense of 250 for 'Movie' in the 'Entertainment' category.
                
                Transcribe the audio accurately AND extract the transaction details simultaneously into ONLY a JSON object:
                - "item": clean item name in English (e.g. "Movie", "Tomato", "Apple", "Tea", "Petrol", "Biryani", "Groceries")
                - "quantity": numeric count or quantity (e.g. 1 for movie, 10 for "Pathu thakkali", 5 for "5 apples", 2 for "2 kg onion")
                - "unit": unit of measure ("ticket", "pcs", "kg", "L", "pkt", "cup", etc.)
                - "title": concise formatted title (e.g. "Movie", "Tomato (10 pcs)", "Apple (5 pcs)", "Lunch")
                - "amount": total amount as number (e.g. 250.0)
                - "type": "EXPENSE" or "INCOME"
                - "category": ("Food & Dining", "Transportation", "Shopping", "Entertainment", "Bills & Utilities", "Housing & Rent", "Healthcare", "Education", "Salary & Income", "Investments", "Other")
                - "paymentMethod": ("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer")
                - "note": accurate transcription of speech in English/Tamil

                Tamil / Tanglish Few-Shot Examples:
                1. "Innaiku movie ki 250 selavu aachu" / "இன்று படத்திற்கு 250 செலவு" -> {"item": "Movie", "quantity": 1, "unit": "ticket", "unitPrice": 250.0, "amount": 250.0, "title": "Movie", "category": "Entertainment", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Innaiku movie ki 250 selavu aachu"}
                2. "Pathu thakkali" / "10 தக்காளி" -> {"item": "Tomato", "quantity": 10, "unit": "pcs", "unitPrice": 5.0, "amount": 50.0, "title": "Tomato (10 pcs)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "Cash", "note": "Pathu thakkali"}
                3. "Add 5 apples" / "5 ஆப்பிள்" -> {"item": "Apple", "quantity": 5, "unit": "pcs", "unitPrice": 20.0, "amount": 100.0, "title": "Apple (5 pcs)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Add 5 apples"}
                4. "2 kg vengayam 60 rs" / "2 கிலோ வெங்காயம் 60 ரூபாய்" -> {"item": "Onion", "quantity": 2, "unit": "kg", "unitPrice": 30.0, "amount": 60.0, "title": "Onion (2 kg)", "category": "Shopping", "type": "EXPENSE", "paymentMethod": "UPI", "note": "2 kg vengayam 60 rs"}
                5. "Rendu biryani 400 gpay" / "இரண்டு பிரியாணி 400 gpay" -> {"item": "Biryani", "quantity": 2, "unit": "pcs", "unitPrice": 200.0, "amount": 400.0, "title": "Biryani (2 pcs)", "category": "Food & Dining", "type": "EXPENSE", "paymentMethod": "UPI", "note": "Rendu biryani 400 gpay"}

                Return plain JSON only without markdown formatting.
            """.trimIndent()
            
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
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
                        put(JSONObject().apply { put("text", "Please transcribe and extract this transaction.") })
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
                val responseText = firstPart?.optString("text") ?: ""

                val jsonClean = responseText.replace("```json", "").replace("```", "").trim()
                val jsonObj = JSONObject(jsonClean)
                val item = jsonObj.optString("item", "").ifBlank { null }
                val quantity = if (jsonObj.has("quantity")) jsonObj.optDouble("quantity", 1.0) else null
                val unit = jsonObj.optString("unit", "").ifBlank { null }
                val title = jsonObj.optString("title", item ?: "Audio Entry")
                val amount = jsonObj.optDouble("amount", 0.0)
                val typeStr = jsonObj.optString("type", "EXPENSE")
                val type = if (typeStr.uppercase() == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                val category = jsonObj.optString("category", "Food & Dining")
                val paymentMethod = jsonObj.optString("paymentMethod", "UPI")
                val note = jsonObj.optString("note", "Audio input")
                ParsedVoiceExpense(
                    title = title,
                    amount = amount,
                    type = type,
                    category = category,
                    paymentMethod = paymentMethod,
                    note = note,
                    item = item,
                    quantity = quantity,
                    unit = unit
                )
            } else {
                throw RuntimeException("Gemini API error code: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackParseVoiceCommand("Audio Expense 150")
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

        // Search for Grand Total / Total Paid / Total
        val grandTotalRegex = Regex("""(?:grand\s*total|total\s*paid|amount\s*paid|net\s*payable|total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val grandMatch = grandTotalRegex.find(rawText)
        val detectedTotal = grandMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: run {
            val numbers = Regex("""\d+\.\d{2}""").findAll(rawText).mapNotNull { it.value.toDoubleOrNull() }.toList()
            numbers.maxOrNull() ?: 0.0
        }

        // Subtotal
        val subtotalRegex = Regex("""(?:subtotal|sub\s*total|sub-total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val subtotal = subtotalRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: detectedTotal

        // Tax / GST
        val taxRegex = Regex("""(?:tax|gst|vat)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val tax = taxRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // Discount
        val discountRegex = Regex("""(?:discount|disc|saved)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val discount = discountRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // Receipt / Invoice Number
        val invoiceRegex = Regex("""(?:inv(?:oice)?|receipt|bill|order|ref)[\s#:]*([A-Za-z0-9\-_]+)""", RegexOption.IGNORE_CASE)
        val receiptNumber = invoiceRegex.find(rawText)?.groupValues?.get(1)

        // Date
        val dateRegex = Regex("""(\d{1,2}[-/.](?:\d{1,2}|[A-Za-z]{3})[-/.](?:\d{2,4}))""")
        val dateStr = dateRegex.find(rawText)?.groupValues?.get(1) ?: "Today"

        // Time
        val timeRegex = Regex("""(\d{1,2}:\d{2}(?:\s*(?:AM|PM|am|pm))?)""")
        val timeStr = timeRegex.find(rawText)?.groupValues?.get(1) ?: ""

        val lower = rawText.lowercase()
        val category = when {
            lower.contains("cafe") || lower.contains("coffee") || lower.contains("restaurant") || 
            lower.contains("burger") || lower.contains("pizza") || lower.contains("food") || lower.contains("swiggy") || lower.contains("zomato") -> "Food & Dining"
            lower.contains("pharmacy") || lower.contains("med") || lower.contains("hospital") || lower.contains("clinic") -> "Healthcare"
            lower.contains("fuel") || lower.contains("petrol") || lower.contains("gas") || lower.contains("diesel") || lower.contains("uber") || lower.contains("ola") -> "Transportation"
            lower.contains("bill") || lower.contains("electricity") || lower.contains("internet") || lower.contains("water") -> "Bills & Utilities"
            else -> "Shopping"
        }

        val paymentMethod = when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
            lower.contains("cash") -> "Cash"
            lower.contains("card") || lower.contains("visa") || lower.contains("mastercard") || lower.contains("debit") || lower.contains("credit") -> "Card"
            else -> "UPI"
        }

        // Extract item rows
        val itemsList = mutableListOf<ParsedReceiptItem>()
        val itemPattern = Regex("""^([A-Za-z0-9\s&'-]+?)(?:\s+(\d+)\s*[xX@]\s*[$₹€£]?\s*(\d+(?:\.\d{1,2})?))?\s+[$₹€£]?\s*(\d+(?:\.\d{1,2})?)$""")

        for (line in lines) {
            val lowerLine = line.lowercase()
            if (lowerLine.contains("total") || lowerLine.contains("tax") || lowerLine.contains("subtotal") || 
                lowerLine.contains("discount") || lowerLine.contains("cash") || lowerLine.contains("change") || 
                lowerLine.contains("thank you") || lowerLine.contains("invoice") || lowerLine.contains("date")) {
                continue
            }
            val match = itemPattern.find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val qty = match.groupValues[2].toDoubleOrNull() ?: 1.0
                val unitPrice = match.groupValues[3].toDoubleOrNull() ?: (match.groupValues[4].toDoubleOrNull() ?: 0.0) / qty
                val lineTotal = match.groupValues[4].toDoubleOrNull() ?: (qty * unitPrice)
                if (name.length > 1 && lineTotal > 0) {
                    itemsList.add(ParsedReceiptItem(name, qty, unitPrice, lineTotal))
                }
            }
        }

        // If line items couldn't be regex parsed individually, parse any lines with prices
        if (itemsList.isEmpty()) {
            val priceLineRegex = Regex("""^(.+?)\s+[$₹€£]?\s*(\d+\.\d{2})$""")
            for (line in lines) {
                val lowerLine = line.lowercase()
                if (lowerLine.contains("total") || lowerLine.contains("tax") || lowerLine.contains("subtotal") || 
                    lowerLine.contains("discount") || lowerLine.contains("date") || lowerLine.contains("card")) continue
                val m = priceLineRegex.find(line)
                if (m != null) {
                    val name = m.groupValues[1].trim()
                    val price = m.groupValues[2].toDoubleOrNull() ?: 0.0
                    if (name.length > 2 && price > 0) {
                        itemsList.add(ParsedReceiptItem(name = name, quantity = 1.0, unitPrice = price, totalPrice = price))
                    }
                }
            }
        }

        if (itemsList.isEmpty() && detectedTotal > 0) {
            itemsList.add(ParsedReceiptItem(name = merchant, quantity = 1.0, unitPrice = detectedTotal, totalPrice = detectedTotal))
        }

        val itemsSummary = itemsList.joinToString(", ") { it.name }.ifBlank { "Receipt items" }

        return ParsedReceipt(
            merchantName = merchant,
            receiptNumber = receiptNumber,
            totalAmount = detectedTotal,
            subtotal = if (subtotal > 0) subtotal else detectedTotal,
            discount = discount,
            tax = tax,
            category = category,
            dateString = dateStr,
            timeString = timeStr,
            paymentMethod = paymentMethod,
            itemsSummary = itemsSummary,
            items = itemsList,
            rawText = rawText
        )
    }

    suspend fun getFinancialCoachAdvice(totalIncome: Double, totalExpense: Double, topExpenseCategory: String): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        val prompt = "User status: Monthly Income = $totalIncome, Monthly Expense = $totalExpense, Top Category = '$topExpenseCategory'. Provide 3 concise, practical financial recommendations."
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val savingsRate = if (totalIncome > 0) (((totalIncome - totalExpense) / totalIncome) * 100).toInt() else 0
            return@withContext "• **Savings Velocity**: Your net savings rate is currently ~${savingsRate.coerceAtLeast(0)}%. Strive for the 20%+ recommended target.\n• **Category Watch**: Highest spend is in **$topExpenseCategory**. Consider setting an active weekly budget limit.\n• **Rule of 50/30/20**: Direct 50% to essential needs, 30% to lifestyle, and 20% into savings & investments."
        }
        try {
            val systemInstruction = "You are Zenith AI Financial Advisor. Provide inspiring, concise, bullet-pointed financial coaching advice."
            callGeminiApi(apiKey, systemInstruction, prompt)
        } catch (e: Exception) {
            "• **Consistent Tracking**: Recording every transaction prevents cash leaks by up to 15%.\n• **Subscription Audit**: Check recurring payments to cancel unused memberships.\n• **Emergency Fund**: Maintain a 3-month living expense reserve in high-yield liquid savings."
        }
    }

    private fun callGeminiApi(apiKey: String, systemInstruction: String, promptText: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 3500
        conn.readTimeout = 4500

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
                put("parts", JSONArray().put(JSONObject().apply { put("text", promptText) }))
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
        } else {
            throw RuntimeException("Gemini API error code: ${conn.responseCode}")
        }
    }

    // --- Offline NLP Rule-Based Tamil, Tanglish & English Parser ---

    private data class ItemMeta(
        val standardName: String,
        val category: String,
        val defaultUnitPrice: Double,
        val defaultUnit: String
    )

    private val TAMIL_NUMBER_MAP: Map<String, Double> = mapOf(
        // Pure Tamil script numbers
        "ஒன்று" to 1.0, "ஒன்னு" to 1.0, "ஒரு" to 1.0, "ஓர்" to 1.0,
        "இரண்டு" to 2.0, "ரெண்டு" to 2.0, "இரு" to 2.0,
        "மூன்று" to 3.0, "மூணு" to 3.0,
        "நான்கு" to 4.0, "நாலு" to 4.0,
        "ஐந்து" to 5.0, "அஞ்சு" to 5.0,
        "ஆறு" to 6.0,
        "ஏழு" to 7.0,
        "எட்டு" to 8.0,
        "ஒன்பது" to 9.0,
        "பத்து" to 10.0,
        "பதினொன்று" to 11.0, "பதினொன்னு" to 11.0,
        "பன்னிரண்டு" to 12.0, "பனிரெண்டு" to 12.0,
        "பதின்மூன்று" to 13.0, "பதின்மூணு" to 13.0,
        "பதினான்கு" to 14.0, "பதினாலு" to 14.0,
        "பதினைந்து" to 15.0, "பதினஞ்சு" to 15.0,
        "இருபது" to 20.0, "இருபத்தி" to 20.0,
        "இருபத்தைந்து" to 25.0, "இருபத்தஞ்சு" to 25.0,
        "முப்பது" to 30.0, "முப்பத்தி" to 30.0,
        "நாற்பது" to 40.0, "நாப்பத்தி" to 40.0,
        "ஐம்பது" to 50.0, "அம்பது" to 50.0, "ஐம்பத்தி" to 50.0,
        "அறுபது" to 60.0,
        "எழுபது" to 70.0,
        "எண்பது" to 80.0,
        "தொண்ணூறு" to 90.0,
        "நூறு" to 100.0, "நூத்தி" to 100.0,
        "இருநூறு" to 200.0,
        "முந்நூறு" to 300.0,
        "நாநூறு" to 400.0,
        "ஐந்நூறு" to 500.0,
        "ஆயிரம்" to 1000.0,
        
        // Tanglish numbers (Romanized Tamil)
        "onnu" to 1.0, "ondru" to 1.0, "oru" to 1.0, "one" to 1.0,
        "rendu" to 2.0, "irandu" to 2.0, "two" to 2.0,
        "moonu" to 3.0, "moondru" to 3.0, "three" to 3.0,
        "naalu" to 4.0, "naangu" to 4.0, "four" to 4.0,
        "anju" to 5.0, "ainthu" to 5.0, "five" to 5.0,
        "aaru" to 6.0, "six" to 6.0,
        "ezhu" to 7.0, "seven" to 7.0,
        "ettu" to 8.0, "eight" to 8.0,
        "onbathu" to 9.0, "ompathu" to 9.0, "nine" to 9.0,
        "pathu" to 10.0, "ten" to 10.0,
        "pathinonnu" to 11.0, "eleven" to 11.0,
        "pannirendu" to 12.0, "twelve" to 12.0, "dozen" to 12.0,
        "pathinanju" to 15.0, "fifteen" to 15.0,
        "irubathu" to 20.0, "irubadhu" to 20.0, "iruvathu" to 20.0, "twenty" to 20.0,
        "irubathi anju" to 25.0, "iruvathi anju" to 25.0, "twenty five" to 25.0,
        "muppathu" to 30.0, "thirty" to 30.0,
        "naarpathu" to 40.0, "naappathu" to 40.0, "forty" to 40.0,
        "aimbathu" to 50.0, "aimbadhu" to 50.0, "ambadhu" to 50.0, "fifty" to 50.0,
        "arubathu" to 60.0, "sixty" to 60.0,
        "ezhubathu" to 70.0, "seventy" to 70.0,
        "enbathu" to 80.0, "eighty" to 80.0,
        "thonnooru" to 90.0, "ninety" to 90.0,
        "nooru" to 100.0, "hundred" to 100.0,
        "irunooru" to 200.0,
        "ainnooru" to 500.0,
        "aayiram" to 1000.0, "thousand" to 1000.0
    )

    private val UNIT_KEYWORDS: Map<String, String> = mapOf(
        "கிலோ" to "kg", "kilo" to "kg", "kg" to "kg", "kgs" to "kg",
        "கிராம்" to "g", "gram" to "g", "gm" to "g", "gms" to "g",
        "லிட்டர்" to "L", "லிட்" to "L", "litre" to "L", "liter" to "L", "lit" to "L", "l" to "L",
        "பாக்கெட்" to "pkt", "பாக்கட்" to "pkt", "packet" to "pkt", "packets" to "pkt", "pkt" to "pkt",
        "பீஸ்" to "pcs", "பீசு" to "pcs", "piece" to "pcs", "pieces" to "pcs", "pcs" to "pcs",
        "கட்டு" to "bunch", "kattu" to "bunch", "bunch" to "bunch",
        "டஜன்" to "dozen", "dozen" to "dozen",
        "டிக்கெட்" to "ticket", "டிக்கட்" to "ticket", "ticket" to "ticket", "tickets" to "ticket",
        "கப்" to "cup", "cup" to "cup", "cups" to "cup",
        "பாட்டில்" to "bottle", "bottle" to "bottle", "bottles" to "bottle"
    )

    private val ITEM_DICTIONARY: Map<String, ItemMeta> = mapOf(
        // Entertainment & Movies (Tamil & Tanglish)
        "movie" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "cinema" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "theater" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "theatre" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "padam" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "padathukku" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "படம்" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "படத்திற்கு" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "படத்துக்கு" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "சினிமா" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),
        "திரையரங்கம்" to ItemMeta("Movie", "Entertainment", 250.0, "ticket"),

        // Vegetables & Groceries (Tamil & Tanglish)
        "தக்காளி" to ItemMeta("Tomato", "Shopping", 5.0, "pcs"),
        "thakkali" to ItemMeta("Tomato", "Shopping", 5.0, "pcs"),
        "tomato" to ItemMeta("Tomato", "Shopping", 5.0, "pcs"),
        "tomatoes" to ItemMeta("Tomato", "Shopping", 5.0, "pcs"),

        "வெங்காயம்" to ItemMeta("Onion", "Shopping", 30.0, "kg"),
        "vengayam" to ItemMeta("Onion", "Shopping", 30.0, "kg"),
        "onion" to ItemMeta("Onion", "Shopping", 30.0, "kg"),
        "onions" to ItemMeta("Onion", "Shopping", 30.0, "kg"),

        "உருளைக்கிழங்கு" to ItemMeta("Potato", "Shopping", 25.0, "kg"),
        "urulaikilangu" to ItemMeta("Potato", "Shopping", 25.0, "kg"),
        "urulaikizhangu" to ItemMeta("Potato", "Shopping", 25.0, "kg"),
        "potato" to ItemMeta("Potato", "Shopping", 25.0, "kg"),
        "potatoes" to ItemMeta("Potato", "Shopping", 25.0, "kg"),

        "ஆப்பிள்" to ItemMeta("Apple", "Shopping", 20.0, "pcs"),
        "apple" to ItemMeta("Apple", "Shopping", 20.0, "pcs"),
        "apples" to ItemMeta("Apple", "Shopping", 20.0, "pcs"),

        "வாழைப்பழம்" to ItemMeta("Banana", "Shopping", 5.0, "pcs"),
        "vazhaipazham" to ItemMeta("Banana", "Shopping", 5.0, "pcs"),
        "banana" to ItemMeta("Banana", "Shopping", 5.0, "pcs"),
        "bananas" to ItemMeta("Banana", "Shopping", 5.0, "pcs"),

        "பால்" to ItemMeta("Milk", "Food & Dining", 35.0, "pkt"),
        "paal" to ItemMeta("Milk", "Food & Dining", 35.0, "pkt"),
        "milk" to ItemMeta("Milk", "Food & Dining", 35.0, "pkt"),

        "முட்டை" to ItemMeta("Eggs", "Food & Dining", 6.0, "pcs"),
        "muttai" to ItemMeta("Eggs", "Food & Dining", 6.0, "pcs"),
        "egg" to ItemMeta("Eggs", "Food & Dining", 6.0, "pcs"),
        "eggs" to ItemMeta("Eggs", "Food & Dining", 6.0, "pcs"),

        "அரிசி" to ItemMeta("Rice", "Shopping", 60.0, "kg"),
        "arisi" to ItemMeta("Rice", "Shopping", 60.0, "kg"),
        "rice" to ItemMeta("Rice", "Shopping", 60.0, "kg"),

        "பருப்பு" to ItemMeta("Dal", "Shopping", 120.0, "kg"),
        "paruppu" to ItemMeta("Dal", "Shopping", 120.0, "kg"),
        "dal" to ItemMeta("Dal", "Shopping", 120.0, "kg"),
        "dhal" to ItemMeta("Dal", "Shopping", 120.0, "kg"),

        "எண்ணெய்" to ItemMeta("Cooking Oil", "Shopping", 140.0, "L"),
        "ennai" to ItemMeta("Cooking Oil", "Shopping", 140.0, "L"),
        "oil" to ItemMeta("Cooking Oil", "Shopping", 140.0, "L"),

        // Food & Beverages
        "டீ" to ItemMeta("Tea", "Food & Dining", 15.0, "cup"),
        "தேநீர்" to ItemMeta("Tea", "Food & Dining", 15.0, "cup"),
        "tea" to ItemMeta("Tea", "Food & Dining", 15.0, "cup"),
        "chai" to ItemMeta("Tea", "Food & Dining", 15.0, "cup"),

        "காபி" to ItemMeta("Coffee", "Food & Dining", 20.0, "cup"),
        "coffee" to ItemMeta("Coffee", "Food & Dining", 20.0, "cup"),

        "பிரியாணி" to ItemMeta("Biryani", "Food & Dining", 180.0, "pcs"),
        "biryani" to ItemMeta("Biryani", "Food & Dining", 180.0, "pcs"),
        "briyani" to ItemMeta("Biryani", "Food & Dining", 180.0, "pcs"),

        "தோசை" to ItemMeta("Dosa", "Food & Dining", 40.0, "pcs"),
        "dosai" to ItemMeta("Dosa", "Food & Dining", 40.0, "pcs"),
        "dosa" to ItemMeta("Dosa", "Food & Dining", 40.0, "pcs"),

        "இட்லி" to ItemMeta("Idli", "Food & Dining", 10.0, "pcs"),
        "idli" to ItemMeta("Idli", "Food & Dining", 10.0, "pcs"),

        "சப்பாத்தி" to ItemMeta("Chappathi", "Food & Dining", 30.0, "pcs"),
        "chappathi" to ItemMeta("Chappathi", "Food & Dining", 30.0, "pcs"),
        "roti" to ItemMeta("Chappathi", "Food & Dining", 30.0, "pcs"),

        "சாப்பாடு" to ItemMeta("Lunch / Meals", "Food & Dining", 100.0, "pcs"),
        "sappadu" to ItemMeta("Lunch / Meals", "Food & Dining", 100.0, "pcs"),
        "meals" to ItemMeta("Lunch / Meals", "Food & Dining", 100.0, "pcs"),
        "lunch" to ItemMeta("Lunch / Meals", "Food & Dining", 120.0, "pcs"),
        "dinner" to ItemMeta("Dinner", "Food & Dining", 120.0, "pcs"),
        "breakfast" to ItemMeta("Breakfast", "Food & Dining", 80.0, "pcs"),
        "tiffin" to ItemMeta("Tiffin", "Food & Dining", 60.0, "pcs"),

        "ஸ்நாக்ஸ்" to ItemMeta("Snacks", "Food & Dining", 30.0, "pcs"),
        "snacks" to ItemMeta("Snacks", "Food & Dining", 30.0, "pcs"),
        "samosa" to ItemMeta("Samosa", "Food & Dining", 15.0, "pcs"),
        "biscuit" to ItemMeta("Biscuits", "Food & Dining", 30.0, "pkt"),
        "biscuits" to ItemMeta("Biscuits", "Food & Dining", 30.0, "pkt"),

        // Transport & Fuel
        "பெட்ரோல்" to ItemMeta("Petrol / Fuel", "Transportation", 105.0, "L"),
        "petrol" to ItemMeta("Petrol / Fuel", "Transportation", 105.0, "L"),
        "diesel" to ItemMeta("Diesel", "Transportation", 95.0, "L"),
        "fuel" to ItemMeta("Fuel", "Transportation", 105.0, "L"),

        "ஆட்டோ" to ItemMeta("Auto Ride", "Transportation", 100.0, "trip"),
        "auto" to ItemMeta("Auto Ride", "Transportation", 100.0, "trip"),
        "uber" to ItemMeta("Cab Ride", "Transportation", 200.0, "trip"),
        "ola" to ItemMeta("Cab Ride", "Transportation", 200.0, "trip"),
        "rapido" to ItemMeta("Bike Taxi", "Transportation", 50.0, "trip"),
        "cab" to ItemMeta("Cab Ride", "Transportation", 200.0, "trip"),

        // Groceries & General Items
        "மளிகை" to ItemMeta("Groceries", "Shopping", 300.0, "basket"),
        "maligai" to ItemMeta("Groceries", "Shopping", 300.0, "basket"),
        "groceries" to ItemMeta("Groceries", "Shopping", 300.0, "basket"),
        "provision" to ItemMeta("Groceries", "Shopping", 300.0, "basket"),

        "காய்கறி" to ItemMeta("Vegetables", "Shopping", 150.0, "basket"),
        "kaaikari" to ItemMeta("Vegetables", "Shopping", 150.0, "basket"),
        "vegetables" to ItemMeta("Vegetables", "Shopping", 150.0, "basket"),
        "veggies" to ItemMeta("Vegetables", "Shopping", 150.0, "basket"),

        "பழங்கள்" to ItemMeta("Fruits", "Shopping", 150.0, "basket"),
        "pazhangal" to ItemMeta("Fruits", "Shopping", 150.0, "basket"),
        "fruits" to ItemMeta("Fruits", "Shopping", 150.0, "basket"),

        "சோப்பு" to ItemMeta("Soap", "Shopping", 40.0, "pcs"),
        "soap" to ItemMeta("Soap", "Shopping", 40.0, "pcs"),
        "shampoo" to ItemMeta("Shampoo", "Shopping", 120.0, "bottle"),

        "மருந்து" to ItemMeta("Medicines", "Healthcare", 150.0, "pcs"),
        "medicine" to ItemMeta("Medicines", "Healthcare", 150.0, "pcs"),
        "tablet" to ItemMeta("Medicines", "Healthcare", 80.0, "pcs"),
        "pharmacy" to ItemMeta("Medicines", "Healthcare", 200.0, "pcs"),

        "மின் கட்டணம்" to ItemMeta("Electricity Bill", "Bills & Utilities", 500.0, "bill"),
        "electricity" to ItemMeta("Electricity Bill", "Bills & Utilities", 500.0, "bill"),
        "eb bill" to ItemMeta("Electricity Bill", "Bills & Utilities", 500.0, "bill"),
        "current bill" to ItemMeta("Electricity Bill", "Bills & Utilities", 500.0, "bill"),

        "ரீசார்ஜ்" to ItemMeta("Mobile / Wifi Recharge", "Bills & Utilities", 299.0, "bill"),
        "recharge" to ItemMeta("Mobile / Wifi Recharge", "Bills & Utilities", 299.0, "bill"),
        "wifi" to ItemMeta("Wifi Bill", "Bills & Utilities", 799.0, "bill"),

        "வாடகை" to ItemMeta("House Rent", "Housing & Rent", 10000.0, "month"),
        "rent" to ItemMeta("House Rent", "Housing & Rent", 10000.0, "month"),

        "சம்பளம்" to ItemMeta("Monthly Salary", "Salary & Income", 25000.0, "month"),
        "salary" to ItemMeta("Monthly Salary", "Salary & Income", 25000.0, "month"),
        "income" to ItemMeta("Income", "Salary & Income", 10000.0, "month"),
        "freelance" to ItemMeta("Freelance Payment", "Salary & Income", 5000.0, "project")
    )

    fun fallbackParseVoiceCommand(prompt: String): ParsedVoiceExpense {
        val lower = prompt.lowercase().trim()
        
        // Income detection (English + Tamil + Tanglish)
        val isIncome = lower.contains("salary") || lower.contains("received") || 
                       lower.contains("earned") || lower.contains("income") || 
                       lower.contains("got paid") || lower.contains("vanthuchu") ||
                       lower.contains("vandoo") || lower.contains("credit") || 
                       lower.contains("bonus") || lower.contains("freelance") ||
                       lower.contains("சம்பளம்") || lower.contains("வந்தது") || lower.contains("வருமானம்")
        
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // Payment method extraction
        val paymentMethod = when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("google pay") ||
            lower.contains("phonepe") || lower.contains("paytm") || lower.contains("scan") ||
            lower.contains("qr") -> "UPI"
            lower.contains("cash") || lower.contains("பணம்") -> "Cash"
            lower.contains("credit") || lower.contains("cc") -> "Credit Card"
            lower.contains("debit") || lower.contains("card") -> "Debit Card"
            lower.contains("bank") || lower.contains("transfer") || lower.contains("netbanking") ||
            lower.contains("neft") || lower.contains("imps") -> "Bank Transfer"
            else -> "UPI"
        }

        // Clean tokens
        val rawTokens = lower.split(Regex("""[\s,]+""")).map { it.trim().trim('.', '!', '?', ':', ';', '₹', '$') }.filter { it.isNotBlank() }

        // 1. Detect Item Match
        var matchedItemMeta: ItemMeta? = null
        var matchedItemWord = ""
        for ((key, meta) in ITEM_DICTIONARY) {
            if (lower.contains(key)) {
                if (key.length > matchedItemWord.length) {
                    matchedItemWord = key
                    matchedItemMeta = meta
                }
            }
        }

        // 2. Detect Unit Match (Token-based)
        var detectedUnit: String? = null
        for (token in rawTokens) {
            if (UNIT_KEYWORDS.containsKey(token)) {
                detectedUnit = UNIT_KEYWORDS[token]
                break
            }
        }

        // 3. Extract explicit price markers (e.g. "50 rs", "60 ரூபாய்", "250 rupees", "₹500", "250 selavu", "250 செலவு")
        val priceMarkerRegex = Regex("""(?:[$₹€£]|rs|rupees|rubai|roobai|ரூபாய்|gpay|phonepe|paid|spent|cost|selavu|selavachu|selavu aachu|செலவு)\s*(\d+(?:\.\d{1,2})?)|(\d+(?:\.\d{1,2})?)\s*(?:rs|rupees|rubai|roobai|ரூபாய்|selavu|selavachu|selavu aachu|செலவு)""", RegexOption.IGNORE_CASE)
        val priceMatch = priceMarkerRegex.find(lower)
        var explicitPrice: Double? = null
        if (priceMatch != null) {
            val pVal = (priceMatch.groupValues[1].ifBlank { priceMatch.groupValues[2] }).toDoubleOrNull()
            if (pVal != null) {
                explicitPrice = pVal
            }
        }

        // 4. Token-by-token number scanning (handles Tamil, Tanglish and digit numbers)
        val extractedNumbers = mutableListOf<Double>()
        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            
            // Check two-word number (e.g. "இருபத்தி அஞ்சு")
            if (i + 1 < rawTokens.size) {
                val twoWord = "$token ${rawTokens[i + 1]}"
                if (TAMIL_NUMBER_MAP.containsKey(twoWord)) {
                    extractedNumbers.add(TAMIL_NUMBER_MAP[twoWord]!!)
                    i += 2
                    continue
                }
            }
            
            if (TAMIL_NUMBER_MAP.containsKey(token)) {
                extractedNumbers.add(TAMIL_NUMBER_MAP[token]!!)
            } else {
                val num = token.toDoubleOrNull()
                if (num != null) {
                    extractedNumbers.add(num)
                }
            }
            i++
        }

        var detectedQuantity: Double? = null
        var detectedAmount: Double? = null

        if (extractedNumbers.size >= 2) {
            val first = extractedNumbers[0]
            val second = extractedNumbers[1]
            if (explicitPrice != null) {
                detectedAmount = explicitPrice
                detectedQuantity = if (first == explicitPrice) second else first
            } else {
                detectedQuantity = first
                detectedAmount = second
            }
        } else if (extractedNumbers.size == 1) {
            val num = extractedNumbers[0]
            if (explicitPrice != null) {
                detectedAmount = explicitPrice
                detectedQuantity = 1.0
            } else if (matchedItemMeta != null) {
                // If the number is likely a quantity (e.g. "5 apples", "pathu thakkali", "2 kg onion")
                if (lower.startsWith("add ") || lower.contains(" kg") || lower.contains(" kilo") || lower.contains(" litre") || num <= 50) {
                    detectedQuantity = num
                    detectedAmount = num * matchedItemMeta.defaultUnitPrice
                } else {
                    // e.g. "250 lunch" or "500 petrol"
                    detectedAmount = num
                    detectedQuantity = 1.0
                }
            } else {
                detectedAmount = num
                detectedQuantity = 1.0
            }
        }

        val finalQuantity = detectedQuantity ?: 1.0
        val finalUnit = detectedUnit ?: matchedItemMeta?.defaultUnit ?: "pcs"
        val finalItem = matchedItemMeta?.standardName
        val finalAmount = detectedAmount ?: (if (matchedItemMeta != null) finalQuantity * matchedItemMeta.defaultUnitPrice else 150.0)

        // Category resolution
        val category = matchedItemMeta?.category ?: when {
            isIncome -> "Salary & Income"
            lower.contains("swiggy") || lower.contains("zomato") || lower.contains("restaurant") || lower.contains("hotel") -> "Food & Dining"
            lower.contains("petrol") || lower.contains("fuel") || lower.contains("uber") || lower.contains("ola") -> "Transportation"
            lower.contains("movie") || lower.contains("cinema") || lower.contains("netflix") -> "Entertainment"
            lower.contains("doctor") || lower.contains("hospital") || lower.contains("clinic") -> "Healthcare"
            lower.contains("school") || lower.contains("college") || lower.contains("fee") -> "Education"
            lower.contains("rent") -> "Housing & Rent"
            lower.contains("electricity") || lower.contains("bill") || lower.contains("recharge") -> "Bills & Utilities"
            else -> "Shopping"
        }

        val title = when {
            finalItem != null && finalQuantity > 1.0 -> {
                val qStr = if (finalQuantity % 1.0 == 0.0) "${finalQuantity.toInt()}" else "$finalQuantity"
                "$finalItem ($qStr $finalUnit)"
            }
            finalItem != null -> finalItem
            prompt.isNotBlank() -> prompt.split(" ").take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
            else -> "Voice Entry"
        }

        return ParsedVoiceExpense(
            title = title,
            amount = finalAmount,
            type = type,
            category = category,
            paymentMethod = paymentMethod,
            note = prompt.ifBlank { "Voice expense" },
            item = finalItem,
            quantity = finalQuantity,
            unit = finalUnit,
            unitPrice = matchedItemMeta?.defaultUnitPrice
        )
    }
}
