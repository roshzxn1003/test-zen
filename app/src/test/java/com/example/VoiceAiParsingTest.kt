package com.example

import com.example.data.ai.GeminiAiService
import com.example.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test

class VoiceAiParsingTest {

    @Test
    fun testVoiceExpense_LunchUpi() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Spent 350 for lunch via UPI")
        assertEquals("Lunch", result.title)
        assertEquals(350.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Food & Dining", result.category)
        assertEquals("UPI", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_ElectricityBillCash() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Paid 1200 for electricity bill cash")
        assertEquals("Electricity Bill", result.title)
        assertEquals(1200.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Bills & Utilities", result.category)
        assertEquals("Cash", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_PetrolPhonePe() {
        val result = GeminiAiService.fallbackParseVoiceCommand("500 petrol via PhonePe")
        assertEquals("Petrol / Fuel", result.title)
        assertEquals(500.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Transportation", result.category)
        assertEquals("UPI", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_GroceriesCreditCard() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Groceries 1500 with credit card")
        assertEquals("Groceries", result.title)
        assertEquals(1500.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Shopping", result.category)
        assertEquals("Credit Card", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_MovieTickets() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Spent 700 on movie tickets")
        assertEquals("Movie Tickets", result.title)
        assertEquals(700.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Entertainment", result.category)
    }

    @Test
    fun testVoiceExpense_CoffeeCash() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Coffee 80 cash")
        assertEquals("Coffee", result.title)
        assertEquals(80.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Food & Dining", result.category)
        assertEquals("Cash", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_HouseRentBankTransfer() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Paid 12000 rent via bank transfer")
        assertEquals("House Rent", result.title)
        assertEquals(12000.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Housing & Rent", result.category)
        assertEquals("Bank Transfer", result.paymentMethod)
    }

    @Test
    fun testVoiceIncome_MonthlySalaryInBank() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Received 50000 salary from office in bank")
        assertEquals("Monthly Salary", result.title)
        assertEquals(50000.0, result.amount, 0.01)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Salary & Income", result.category)
        assertEquals("Bank Transfer", result.paymentMethod)
    }

    @Test
    fun testVoiceIncome_FreelancePayment() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Received 15000 freelance payment")
        assertEquals("Freelance Payment", result.title)
        assertEquals(15000.0, result.amount, 0.01)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Salary & Income", result.category)
    }

    @Test
    fun testVoiceIncome_CashbackGPay() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Got 200 cashback on Google Pay")
        assertEquals("Cashback", result.title)
        assertEquals(200.0, result.amount, 0.01)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Investments", result.category)
        assertEquals("UPI", result.paymentMethod)
    }

    @Test
    fun testVoiceIncome_BonusCredit() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Got 5000 bonus credit")
        assertEquals("Bonus", result.title)
        assertEquals(5000.0, result.amount, 0.01)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Salary & Income", result.category)
    }

    @Test
    fun testVoiceExpense_AmountMultiplier_K() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Received 50k salary in bank")
        assertEquals("Monthly Salary", result.title)
        assertEquals(50000.0, result.amount, 0.01)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Salary & Income", result.category)
    }

    @Test
    fun testVoiceExpense_AmountMultiplier_Lakh() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Paid 1.5 lakh for car loan bank")
        assertEquals(150000.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
    }

    @Test
    fun testVoiceExpense_FamilyScopeDetection() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Family groceries 2500 credit card")
        assertEquals(com.example.data.models.FinanceScope.FAMILY, result.scope)
        assertEquals("Groceries", result.title)
        assertEquals(2500.0, result.amount, 0.01)
        assertEquals("Shopping", result.category)
        assertEquals("Credit Card", result.paymentMethod)
    }

    @Test
    fun testVoiceExpense_SharedVaultScopeDetection() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Shared house rent 15000 bank transfer")
        assertEquals(com.example.data.models.FinanceScope.FAMILY, result.scope)
        assertEquals("House Rent", result.title)
        assertEquals(15000.0, result.amount, 0.01)
        assertEquals("Housing & Rent", result.category)
    }

    @Test
    fun testVoiceExpense_DomainEntities_SwiggyAndRapido() {
        val swiggy = GeminiAiService.fallbackParseVoiceCommand("Swiggy dinner 450 UPI")
        assertEquals("Swiggy Order", swiggy.title)
        assertEquals(450.0, swiggy.amount, 0.01)
        assertEquals("Food & Dining", swiggy.category)

        val rapido = GeminiAiService.fallbackParseVoiceCommand("Rapido ride 65 cash")
        assertEquals("Rapido Ride", rapido.title)
        assertEquals(65.0, rapido.amount, 0.01)
        assertEquals("Transportation", rapido.category)
        assertEquals("Cash", rapido.paymentMethod)
    }

    @Test
    fun testVoiceExpense_TanglishRomanizedKeywords() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Saapadu 180 selavu UPI")
        assertEquals(180.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Food & Dining", result.category)
        assertEquals("UPI", result.paymentMethod)
    }
}
