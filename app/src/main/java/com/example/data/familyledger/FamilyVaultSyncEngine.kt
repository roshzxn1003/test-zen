package com.example.data.familyledger

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.dao.FamilyDao
import com.example.data.dao.FamilyMemberDao
import com.example.data.dao.TransactionDao
import com.example.data.models.FamilyEntity
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FamilyRole
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionEntity
import com.example.data.models.TransactionType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

data class VaultSyncQrData(
    val familyId: String,
    val familyName: String,
    val inviteCode: String,
    val createdAt: Long,
    val memberCount: Int = 1
)

data class VaultImportSummary(
    val familyId: String,
    val familyName: String,
    val inviteCode: String,
    val transactionsImported: Int,
    val membersImported: Int
)

/**
 * High-Reliability Alternative Sync Engine for Family Vault.
 *
 * Provides two alternative ways to synchronize Family Vaults without relying on
 * direct cloud availability:
 *
 * 1. **Direct QR Code Sync**:
 *    - Device A displays a high-density, error-corrected QR code containing vault identity & invite tokens.
 *    - Device B scans with camera to immediately link and synchronize.
 *
 * 2. **Direct Vault File Sync (.zenithvault / JSON)**:
 *    - Device A exports the entire Family Vault state (metadata, connected members, full itemized transactions).
 *    - Shares securely via WhatsApp, Nearby Share, Telegram, Bluetooth, or Email.
 *    - Device B imports the file: idempotent merge with latest-write-wins (LWW) and Room database consistency.
 */
class FamilyVaultSyncEngine(
    private val ledgerDao: FamilyLedgerDao,
    private val transactionDao: TransactionDao,
    private val familyDao: FamilyDao,
    private val memberDao: FamilyMemberDao
) {
    private val tag = "VaultSyncEngine"

    // =========================================================================
    // 1. QR CODE GENERATION & PARSING
    // =========================================================================

    /**
     * Builds the standard JSON protocol string for QR code syncing.
     */
    fun createQrSyncPayload(vault: FamilyVault, memberCount: Int = 1): String {
        val json = JSONObject().apply {
            put("protocol", PROTOCOL_VERSION)
            put("familyId", vault.familyId)
            put("familyName", vault.familyName)
            put("inviteCode", vault.inviteCode)
            put("createdAt", vault.createdAt)
            put("memberCount", memberCount)
            put("type", "FAMILY_VAULT_SYNC")
        }
        return json.toString()
    }

    /**
     * Generates a high-contrast, error-corrected QR Code [Bitmap] for on-screen display.
     */
    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            Log.e(tag, "Failed to generate QR bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Parses a scanned barcode string. Handles both Zenith QR protocol and raw invite codes.
     */
    fun parseScannedPayload(rawText: String): VaultSyncQrData? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        // Case A: JSON protocol format
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val familyId = json.optString("familyId")
                val familyName = json.optString("familyName", "Family Vault")
                val inviteCode = json.optString("inviteCode")
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())
                val memberCount = json.optInt("memberCount", 1)

                if (familyId.isNotBlank() || inviteCode.isNotBlank()) {
                    return VaultSyncQrData(
                        familyId = familyId.ifBlank { UUID.randomUUID().toString() },
                        familyName = familyName,
                        inviteCode = inviteCode.ifBlank { familyId },
                        createdAt = createdAt,
                        memberCount = memberCount
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "Scanned text is JSON but not Zenith sync format: ${e.message}")
            }
        }

        // Case B: Raw Invite code (e.g. FAM-XYZ123 or UUID)
        val cleanCode = trimmed.uppercase()
        return VaultSyncQrData(
            familyId = if (isValidUuid(trimmed)) trimmed else UUID.randomUUID().toString(),
            familyName = "Family Vault ($cleanCode)",
            inviteCode = cleanCode,
            createdAt = System.currentTimeMillis(),
            memberCount = 1
        )
    }

    // =========================================================================
    // 2. DIRECT VAULT FILE EXPORT & IMPORT (.zenithvault)
    // =========================================================================

    /**
     * Packages the complete Family Vault (vault details, members, itemized transactions)
     * into an exportable JSON payload.
     */
    suspend fun exportVaultPayload(familyId: String): String = withContext(Dispatchers.IO) {
        val vault = ledgerDao.getFamilyVault(familyId) ?: FamilyVault(
            familyId = familyId,
            familyName = "Family Vault",
            inviteCode = "FAM-" + familyId.take(6).uppercase()
        )
        val members = ledgerDao.getMembersForFamilyOnce(familyId)
        val transactions = ledgerDao.getTransactionsForFamilyOnce(familyId)

        val root = JSONObject().apply {
            put("format", "ZENITH_VAULT_SYNC_ARCHIVE")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())

            // Vault details
            val vaultObj = JSONObject().apply {
                put("familyId", vault.familyId)
                put("familyName", vault.familyName)
                put("inviteCode", vault.inviteCode)
                put("createdBy", vault.createdBy)
                put("createdAt", vault.createdAt)
                put("updatedAt", vault.updatedAt)
            }
            put("vault", vaultObj)

            // Members array
            val membersArray = JSONArray()
            members.forEach { m ->
                val mObj = JSONObject().apply {
                    put("memberId", m.memberId)
                    put("userId", m.userId)
                    put("name", m.name)
                    put("role", m.role.name)
                    put("joinedAt", m.joinedAt)
                    put("updatedAt", m.updatedAt)
                }
                membersArray.put(mObj)
            }
            put("members", membersArray)

            // Transactions array
            val txArray = JSONArray()
            transactions.forEach { tx ->
                val tObj = JSONObject().apply {
                    put("transactionId", tx.transactionId)
                    put("title", tx.title)
                    put("description", tx.description)
                    put("amount", tx.amount)
                    put("category", tx.category)
                    put("type", tx.type.name)
                    put("paymentMethod", tx.paymentMethod)
                    put("paidByMemberId", tx.paidByMemberId)
                    put("paidByName", tx.paidByName)
                    put("dateMillis", tx.dateMillis)
                    put("createdAt", tx.createdAt)
                    put("updatedAt", tx.updatedAt)
                    put("syncVersion", tx.syncVersion)
                }
                txArray.put(tObj)
            }
            put("transactions", txArray)
        }

        root.toString(2)
    }

    /**
     * Exports the payload to a shareable file and returns an Android Share Intent.
     */
    suspend fun createShareVaultIntent(context: Context, familyId: String): Intent? = withContext(Dispatchers.IO) {
        try {
            val jsonString = exportVaultPayload(familyId)
            val vault = ledgerDao.getFamilyVault(familyId)
            val safeName = (vault?.familyName ?: "Family_Vault").replace(Regex("[^a-zA-Z0-9_]"), "_")
            val fileName = "Zenith_${safeName}_Sync_${System.currentTimeMillis()}.zenithvault"

            val cacheDir = File(context.cacheDir, "vault_sync").apply { mkdirs() }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                fos.write(jsonString.toByteArray(StandardCharsets.UTF_8))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Zenith Family Vault Sync Package: ${vault?.familyName ?: "Vault"}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is the Zenith Family Vault sync file for '${vault?.familyName ?: "Family Vault"}' (Invite Code: ${vault?.inviteCode ?: familyId}).\nOpen Zenith Finance > Family Ledger > Alternative Sync > Import File to merge transactions."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Intent.createChooser(shareIntent, "Share Family Vault Sync File").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to create share intent: ${e.message}", e)
            null
        }
    }

    /**
     * Imports a vault sync payload (from a shared file or JSON string), performing
     * idempotent reconciliation into both FamilyLedger tables and CashFlow tables.
     */
    suspend fun importVaultPayload(jsonString: String, currentUserId: String, currentUserName: String): Result<VaultImportSummary> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val format = root.optString("format", "")
            if (format != "ZENITH_VAULT_SYNC_ARCHIVE" && !root.has("vault")) {
                return@withContext Result.failure(Exception("Unsupported vault sync file format."))
            }

            val vaultObj = root.getJSONObject("vault")
            val familyId = vaultObj.getString("familyId")
            val familyName = vaultObj.optString("familyName", "Family Vault")
            val inviteCode = vaultObj.optString("inviteCode", "FAM-" + familyId.take(6).uppercase())
            val createdBy = vaultObj.optString("createdBy", currentUserId)
            val createdAt = vaultObj.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = vaultObj.optLong("updatedAt", System.currentTimeMillis())

            // 1. Upsert Vault into Room
            val domainVault = FamilyVault(
                familyId = familyId,
                familyName = familyName,
                inviteCode = inviteCode,
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt,
                syncStatus = "SYNCED"
            )
            ledgerDao.upsertFamilyVault(domainVault)

            val legacyFamily = FamilyEntity(
                id = familyId,
                name = familyName,
                createdByUserId = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt,
                inviteCode = inviteCode,
                serverId = familyId,
                syncStatus = "SYNCED"
            )
            familyDao.insertFamily(legacyFamily)

            // 2. Import Members
            var importedMembersCount = 0
            val membersArray = root.optJSONArray("members") ?: JSONArray()
            for (i in 0 until membersArray.length()) {
                val mObj = membersArray.getJSONObject(i)
                val memberId = mObj.getString("memberId")
                val userId = mObj.optString("userId", UUID.randomUUID().toString())
                val name = mObj.optString("name", "Member")
                val roleStr = mObj.optString("role", "MEMBER")
                val role = try { FamilyRole.valueOf(roleStr) } catch (e: Exception) { FamilyRole.MEMBER }
                val mJoinedAt = mObj.optLong("joinedAt", System.currentTimeMillis())
                val mUpdatedAt = mObj.optLong("updatedAt", System.currentTimeMillis())

                val domainMember = FamilyVaultMember(
                    memberId = memberId,
                    familyId = familyId,
                    userId = userId,
                    name = name,
                    role = role,
                    joinedAt = mJoinedAt,
                    updatedAt = mUpdatedAt,
                    syncStatus = "SYNCED"
                )
                ledgerDao.upsertMember(domainMember)

                val legacyMember = FamilyMemberEntity(
                    id = memberId,
                    familyId = familyId,
                    userId = userId,
                    name = name,
                    role = role,
                    joinedAt = mJoinedAt,
                    syncStatus = "SYNCED"
                )
                memberDao.insertMember(legacyMember)
                importedMembersCount++
            }

            // Ensure current user is in roster
            if (ledgerDao.getMemberByFamilyAndUser(familyId, currentUserId) == null) {
                val curMember = FamilyVaultMember(
                    memberId = UUID.randomUUID().toString(),
                    familyId = familyId,
                    userId = currentUserId,
                    name = currentUserName.ifBlank { "You" },
                    role = FamilyRole.MEMBER,
                    joinedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "SYNCED"
                )
                ledgerDao.upsertMember(curMember)
                memberDao.insertMember(
                    FamilyMemberEntity(
                        id = curMember.memberId,
                        familyId = familyId,
                        userId = currentUserId,
                        name = curMember.name,
                        role = curMember.role,
                        joinedAt = curMember.joinedAt,
                        syncStatus = "SYNCED"
                    )
                )
            }

            // 3. Import Transactions
            var importedTxCount = 0
            val txArray = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until txArray.length()) {
                val tObj = txArray.getJSONObject(i)
                val txId = tObj.getString("transactionId")
                val title = tObj.optString("title", "Family Entry")
                val desc = tObj.optString("description", "")
                val amt = tObj.optDouble("amount", 0.0)
                val cat = tObj.optString("category", "General")
                val typeStr = tObj.optString("type", "EXPENSE")
                val type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
                val paymentMethod = tObj.optString("paymentMethod", "UPI")
                val payerId = tObj.optString("paidByMemberId", currentUserId)
                val payerName = tObj.optString("paidByName", "Family Member")
                val dateMillis = tObj.optLong("dateMillis", System.currentTimeMillis())
                val txCreatedAt = tObj.optLong("createdAt", System.currentTimeMillis())
                val txUpdatedAt = tObj.optLong("updatedAt", System.currentTimeMillis())
                val syncVersion = tObj.optLong("syncVersion", 1)

                val domainTx = LedgerTransaction(
                    transactionId = txId,
                    familyId = familyId,
                    title = title,
                    description = desc,
                    amount = amt,
                    category = cat,
                    type = type,
                    paymentMethod = paymentMethod,
                    paidByMemberId = payerId,
                    paidByName = payerName,
                    createdBy = payerId,
                    dateMillis = dateMillis,
                    createdAt = txCreatedAt,
                    updatedAt = txUpdatedAt,
                    syncStatus = "SYNCED",
                    syncVersion = syncVersion,
                    isDeleted = false
                )
                ledgerDao.upsertTransaction(domainTx)

                // Mirror to legacy transactions table
                val existingLegacy = transactionDao.getTransactionByServerId(txId)
                val legacyEntity = TransactionEntity(
                    id = existingLegacy?.id ?: 0L,
                    title = title,
                    amount = amt,
                    type = type,
                    category = cat,
                    categoryIconName = "Receipt",
                    paymentMethod = paymentMethod,
                    note = desc,
                    financeScope = FinanceScope.FAMILY,
                    familyId = familyId,
                    createdByUserId = payerId,
                    dateMillis = dateMillis,
                    serverId = txId,
                    syncStatus = "SYNCED",
                    updatedAt = txUpdatedAt,
                    isDeleted = false
                )
                if (existingLegacy == null) {
                    transactionDao.insertTransaction(legacyEntity)
                } else {
                    transactionDao.updateTransaction(legacyEntity)
                }
                importedTxCount++
            }

            Log.d(tag, "Imported vault $familyId: $importedTxCount transactions, $importedMembersCount members")
            Result.success(
                VaultImportSummary(
                    familyId = familyId,
                    familyName = familyName,
                    inviteCode = inviteCode,
                    transactionsImported = importedTxCount,
                    membersImported = importedMembersCount
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to import vault payload: ${e.message}", e)
            Result.failure(Exception("Failed to import Family Vault: ${e.localizedMessage}"))
        }
    }

    private fun isValidUuid(str: String): Boolean = try {
        UUID.fromString(str); true
    } catch (e: Exception) { false }

    companion object {
        const val PROTOCOL_VERSION = "ZENITH_VAULT_QR_V2"
    }
}
