package com.example

import com.example.data.ai.GeminiAiService
import com.example.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test

class VoiceAiParsingTest {

    @Test
    fun testTamilSingleTurnExtraction_PathuThakkali() {
        val result = GeminiAiService.fallbackParseVoiceCommand("பத்து தக்காளி")
        assertEquals("Tomato", result.item)
        assertEquals(10.0, result.quantity ?: 0.0, 0.01)
        assertEquals("Tomato (10 pcs)", result.title)
        assertEquals("Shopping", result.category)
        assertTrue(result.amount > 0)
    }

    @Test
    fun testTamilSingleTurnExtraction_WithPrice() {
        val result = GeminiAiService.fallbackParseVoiceCommand("10 தக்காளி 50 ரூபாய்")
        assertEquals("Tomato", result.item)
        assertEquals(10.0, result.quantity ?: 0.0, 0.01)
        assertEquals(50.0, result.amount, 0.01)
        assertEquals("Tomato (10 pcs)", result.title)
    }

    @Test
    fun testTamilMilkWithUnit_Liter() {
        val result = GeminiAiService.fallbackParseVoiceCommand("இரண்டு லிட்டர் பால் 70 ரூபாய்")
        assertEquals("Milk", result.item)
        assertEquals(2.0, result.quantity ?: 0.0, 0.01)
        assertEquals("L", result.unit)
        assertEquals(70.0, result.amount, 0.01)
        assertEquals("Milk (2 L)", result.title)
    }

    @Test
    fun testTamilOnionWithKg() {
        val result = GeminiAiService.fallbackParseVoiceCommand("5 கிலோ வெங்காயம் 100 ரூபாய்")
        assertEquals("Onion", result.item)
        assertEquals(5.0, result.quantity ?: 0.0, 0.01)
        assertEquals("kg", result.unit)
        assertEquals(100.0, result.amount, 0.01)
        assertEquals("Onion (5 kg)", result.title)
    }

    @Test
    fun testTanglishSingleTurnExtraction_PathuThakkali() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Pathu thakkali")
        assertEquals("Tomato", result.item)
        assertEquals(10.0, result.quantity ?: 0.0, 0.01)
        assertEquals("Tomato (10 pcs)", result.title)
    }

    @Test
    fun testTanglishVengayamWithKgAndPrice() {
        val result = GeminiAiService.fallbackParseVoiceCommand("2 kg vengayam 60 rs")
        assertEquals("Onion", result.item)
        assertEquals(2.0, result.quantity ?: 0.0, 0.01)
        assertEquals("kg", result.unit)
        assertEquals(60.0, result.amount, 0.01)
        assertEquals("Onion (2 kg)", result.title)
    }

    @Test
    fun testTanglishRenduBiryaniGpay() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Rendu biryani 400 gpay")
        assertEquals("Biryani", result.item)
        assertEquals(2.0, result.quantity ?: 0.0, 0.01)
        assertEquals(400.0, result.amount, 0.01)
        assertEquals("UPI", result.paymentMethod)
        assertEquals("Food & Dining", result.category)
        assertEquals("Biryani (2 pcs)", result.title)
    }

    @Test
    fun testEnglishAddFiveApples() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Add 5 apples")
        assertEquals("Apple", result.item)
        assertEquals(5.0, result.quantity ?: 0.0, 0.01)
        assertEquals("Apple (5 pcs)", result.title)
        assertTrue(result.amount > 0)
    }

    @Test
    fun testTamilIncome_Salary() {
        val result = GeminiAiService.fallbackParseVoiceCommand("சம்பளம் 35000 வந்தது")
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(35000.0, result.amount, 0.01)
        assertEquals("Salary & Income", result.category)
    }

    @Test
    fun testTanglishTeaPrice() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Oru tea 15 rubai")
        assertEquals("Tea", result.item)
        assertEquals(1.0, result.quantity ?: 0.0, 0.01)
        assertEquals(15.0, result.amount, 0.01)
        assertEquals("Food & Dining", result.category)
    }

    @Test
    fun testPetrolTransportationExpense() {
        val result = GeminiAiService.fallbackParseVoiceCommand("500 petrol phonepe")
        assertEquals("Petrol / Fuel", result.item)
        assertEquals(500.0, result.amount, 0.01)
        assertEquals("UPI", result.paymentMethod)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun testTanglishMovieExpense() {
        val result = GeminiAiService.fallbackParseVoiceCommand("Innaiku movie ki 250 selavu aachu")
        assertEquals("Movie", result.item)
        assertEquals(250.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Entertainment", result.category)
    }

    @Test
    fun testTamilMovieExpense() {
        val result = GeminiAiService.fallbackParseVoiceCommand("இன்று படத்திற்கு 250 செலவு")
        assertEquals("Movie", result.item)
        assertEquals(250.0, result.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Entertainment", result.category)
    }
}
