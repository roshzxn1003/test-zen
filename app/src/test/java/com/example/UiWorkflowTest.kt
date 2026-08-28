package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.CashFlowTheme
import com.example.ui.viewmodel.CashFlowUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPaymentMethodDropdown() {
        var selectedMethod = "UPI"

        composeTestRule.setContent {
            CashFlowTheme {
                PaymentMethodDropdown(
                    selectedMethod = selectedMethod,
                    onMethodSelected = { selectedMethod = it }
                )
            }
        }

        // Click dropdown to open
        composeTestRule.onNodeWithTag("dropdown_payment_method").performClick()
        composeTestRule.waitForIdle()

        // Select Cash
        composeTestRule.onNodeWithTag("payment_option_Cash").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedMethod == "Cash")
    }

    @Test
    fun testFloatingNavigationBarTabs() {
        val selectedTab = mutableStateOf(0)

        composeTestRule.setContent {
            CashFlowTheme {
                ZenithFloatingNavigationBar(
                    selectedTab = selectedTab.value,
                    onTabSelected = { selectedTab.value = it }
                )
            }
        }

        // Click Activity Tab (1)
        composeTestRule.onNodeWithTag("nav_transactions").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedTab.value == 1)

        // Click Budgets Tab (2)
        composeTestRule.onNodeWithTag("nav_budgets").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedTab.value == 2)

        // Click Analytics Tab (3)
        composeTestRule.onNodeWithTag("nav_analytics").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedTab.value == 3)

        // Click Profile Tab (4)
        composeTestRule.onNodeWithTag("nav_profile").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedTab.value == 4)

        // Click Home Tab (0)
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedTab.value == 0)
    }

    @Test
    fun testAddTransactionDialogButtons() {
        var dismissed = false

        composeTestRule.setContent {
            CashFlowTheme {
                AddTransactionDialog(
                    categories = emptyList(),
                    currencySymbol = "₹",
                    onDismiss = { dismissed = true },
                    onAdd = { _, _, _, _, _, _, _, _, _ -> },
                    currentFinanceScope = FinanceScope.PERSONAL
                )
            }
        }

        // Toggle Expense and Income
        composeTestRule.onNodeWithTag("type_income_button").performClick()
        composeTestRule.onNodeWithTag("type_expense_button").performClick()

        // Click Close
        composeTestRule.onNodeWithTag("close_add_dialog").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun testHomeScreenInteractions() {
        var scopeChanged: FinanceScope? = null
        var addOpened = false
        var voiceOpened = false
        var profileOpened = false

        composeTestRule.setContent {
            CashFlowTheme {
                HomeScreen(
                    state = CashFlowUiState(),
                    totalIncome = 10000.0,
                    totalExpense = 2500.0,
                    netBalance = 7500.0,
                    currentFinanceScope = FinanceScope.PERSONAL,
                    onScopeChange = { scopeChanged = it },
                    onOpenAddTransaction = { addOpened = true },
                    onOpenVoiceAssistant = { voiceOpened = true },
                    onOpenReceiptScanner = {},
                    onDeleteTransaction = {},
                    onManageFamilyMembers = {},
                    onNavigateToActivity = {},
                    onNavigateToProfile = { profileOpened = true }
                )
            }
        }

        // Test Voice button in header
        composeTestRule.onNodeWithTag("btn_voice_entry_header").performClick()
        assertTrue(voiceOpened)
    }
}

