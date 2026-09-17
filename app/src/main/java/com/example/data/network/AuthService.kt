package com.example.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AuthUser(
    val id: String,
    val email: String? = null,
    val fullName: String? = null
)

data class AuthResult(
    val success: Boolean,
    val message: String? = null,
    val isOffline: Boolean = false,
    val requiresEmailConfirmation: Boolean = false,
    val user: AuthUser? = null
)

interface AuthService {
    val currentUser: StateFlow<AuthUser?>
    suspend fun restoreSession()
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signUp(email: String, password: String, fullName: String): AuthResult
    suspend fun resetPassword(email: String): AuthResult
    suspend fun signOut()
}

/**
 * Local implementation of [AuthService] providing offline-first session handling.
 */
class LocalAuthService : AuthService {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override suspend fun restoreSession() {
        // Ready for session restoration logic from reconstructed backend or local storage
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val cleanEmail = email.trim()
        val user = AuthUser(
            id = UUID.nameUUIDFromBytes(cleanEmail.lowercase().toByteArray()).toString(),
            email = cleanEmail,
            fullName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        )
        _currentUser.value = user
        return AuthResult(success = true, message = "Signed in successfully (Offline Vault)", isOffline = true, user = user)
    }

    override suspend fun signUp(email: String, password: String, fullName: String): AuthResult {
        val cleanEmail = email.trim()
        val user = AuthUser(
            id = UUID.nameUUIDFromBytes(cleanEmail.lowercase().toByteArray()).toString(),
            email = cleanEmail,
            fullName = fullName.trim()
        )
        _currentUser.value = user
        return AuthResult(success = true, message = "Account created successfully (Offline Vault)", isOffline = true, user = user)
    }

    override suspend fun resetPassword(email: String): AuthResult {
        return AuthResult(success = true, message = "Offline vault active. You can log in directly without password reset.", isOffline = true)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }
}
