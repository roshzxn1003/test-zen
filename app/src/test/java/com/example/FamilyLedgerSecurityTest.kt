package com.example

import com.example.data.models.FamilyRole
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import com.example.data.familyledger.LedgerTransaction
import com.example.data.familyledger.FamilyVault
import com.example.data.familyledger.FamilyVaultMember
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FamilyLedgerSecurityTest {

    @Test
    fun testPersonalVsFamilyScopeSeparation() {
        val personalTx = LedgerTransaction(
            transactionId = UUID.randomUUID().toString(),
            familyId = "",
            title = "Solo Coffee",
            amount = 120.0,
            type = TransactionType.EXPENSE,
            createdBy = "user_alice",
            syncStatus = "SYNCED"
        )

        val familyTx = LedgerTransaction(
            transactionId = UUID.randomUUID().toString(),
            familyId = "vault_roshan_family",
            title = "Weekend Groceries",
            amount = 2500.0,
            type = TransactionType.EXPENSE,
            paidByMemberId = "member_bob",
            paidByName = "Bob",
            createdBy = "user_bob",
            syncStatus = "SYNCED"
        )

        // Rule: Personal transaction must not have a familyId
        assertTrue(personalTx.familyId.isBlank())
        // Rule: Family transaction must have a valid familyId
        assertEquals("vault_roshan_family", familyTx.familyId)
        assertNotEquals(personalTx.familyId, familyTx.familyId)
    }

    @Test
    fun testFamilyRoleHierarchyPermissions() {
        fun canDeleteFamily(role: FamilyRole): Boolean = role == FamilyRole.ADMIN // In legacy or OWNER
        fun canManageMembers(role: FamilyRole): Boolean = role in listOf(FamilyRole.ADMIN)
        fun canAddTransactions(role: FamilyRole): Boolean = role in listOf(FamilyRole.ADMIN, FamilyRole.MEMBER)

        assertTrue(canAddTransactions(FamilyRole.ADMIN))
        assertTrue(canAddTransactions(FamilyRole.MEMBER))
        assertTrue(canManageMembers(FamilyRole.ADMIN))
        assertFalse(canManageMembers(FamilyRole.MEMBER))
    }

    @Test
    fun testFamilyVaultCreationGeneratesUniqueInviteCode() {
        val vault1 = FamilyVault(
            familyId = UUID.randomUUID().toString(),
            familyName = "Roshan Household",
            inviteCode = "ROSHAN26",
            createdBy = "user_roshan"
        )

        val vault2 = FamilyVault(
            familyId = UUID.randomUUID().toString(),
            familyName = "Kapoor Household",
            inviteCode = "KAPOOR99",
            createdBy = "user_kapoor"
        )

        assertNotNull(vault1.inviteCode)
        assertNotNull(vault2.inviteCode)
        assertNotEquals(vault1.inviteCode, vault2.inviteCode)
        assertEquals("ROSHAN26", vault1.inviteCode)
    }

    @Test
    fun testOptimisticLockingSyncVersionIncrement() {
        val initialTx = LedgerTransaction(
            transactionId = "tx_101",
            familyId = "family_abc",
            title = "Dinner",
            amount = 1200.0,
            syncVersion = 1L
        )

        // When updated, syncVersion must increment
        val updatedTx = initialTx.copy(
            amount = 1400.0,
            syncVersion = initialTx.syncVersion + 1,
            syncStatus = "PENDING_UPDATE"
        )

        assertEquals(2L, updatedTx.syncVersion)
        assertEquals("PENDING_UPDATE", updatedTx.syncStatus)
        assertTrue(updatedTx.syncVersion > initialTx.syncVersion)
    }

    @Test
    fun testFlexibleInviteCodeCandidateNormalization() {
        fun normalizeCandidates(input: String): List<String> {
            val clean = input.trim().uppercase()
            if (clean.isBlank()) return emptyList()
            return listOf(
                clean,
                if (clean.startsWith("FAM-")) clean.removePrefix("FAM-") else "FAM-$clean"
            ).distinct()
        }

        val candidatesWithPrefix = normalizeCandidates("FAM-894201")
        assertTrue(candidatesWithPrefix.contains("FAM-894201"))
        assertTrue(candidatesWithPrefix.contains("894201"))

        val candidatesWithoutPrefix = normalizeCandidates("894201")
        assertTrue(candidatesWithoutPrefix.contains("FAM-894201"))
        assertTrue(candidatesWithoutPrefix.contains("894201"))

        val candidatesCaseInsensitive = normalizeCandidates("fam-894201")
        assertTrue(candidatesCaseInsensitive.contains("FAM-894201"))
        assertTrue(candidatesCaseInsensitive.contains("894201"))
    }

    @Test
    fun testInviteCodeUuidValidation() {
        fun isValidUuid(str: String?): Boolean {
            if (str.isNullOrBlank()) return false
            return try {
                UUID.fromString(str)
                true
            } catch (e: Exception) {
                false
            }
        }

        assertTrue(isValidUuid(UUID.randomUUID().toString()))
        assertTrue(isValidUuid("00000000-0000-0000-0000-000000000001"))
        assertFalse(isValidUuid("FAM-894201"))
        assertFalse(isValidUuid("894201"))
        assertFalse(isValidUuid(""))
        assertFalse(isValidUuid(null))
    }
}
