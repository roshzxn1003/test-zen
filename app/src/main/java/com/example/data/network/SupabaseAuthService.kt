package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.ZenithApplication
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class SupabaseAuthService(
    private val context: Context? = null
) : AuthService {
    private val tag = "SupabaseAuthService"
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val prefs by lazy {
        try {
            context?.getSharedPreferences(PREFS_AUTH_CACHE, Context.MODE_PRIVATE)
                ?: ZenithApplication.instance.getSharedPreferences(PREFS_AUTH_CACHE, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun restoreSession() = withContext(Dispatchers.IO) {
        // 1. Immediately restore cached local user so offline experience is instantaneous
        val cachedUser = loadUserFromCache()
        if (cachedUser != null) {
            _currentUser.value = cachedUser
            Log.d(tag, "Restored local cached user: ${cachedUser.email}")
        }

        // 2. If Supabase is configured, refresh cloud session
        if (!SupabaseClientConfig.isConfigured) return@withContext
        try {
            SupabaseClientConfig.supabase.auth.awaitInitialization()
            val user = SupabaseClientConfig.supabase.auth.currentUserOrNull()
            if (user != null) {
                val email = user.email ?: cachedUser?.email ?: ""
                val authUser = AuthUser(
                    id = user.id,
                    email = email,
                    fullName = cachedUser?.fullName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                )
                _currentUser.value = authUser
                saveUserToCache(authUser)
                Log.d(tag, "Refreshed live Supabase session for: ${user.id}")
            }
        } catch (e: Exception) {
            Log.w(tag, "Supabase session refresh note: ${e.message}")
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext AuthResult(false, "Please enter your email address.")
        }
        if (password.isBlank()) {
            return@withContext AuthResult(false, "Please enter your password.")
        }

        if (!SupabaseClientConfig.isConfigured) {
            val user = AuthUser(
                id = UUID.nameUUIDFromBytes(cleanEmail.lowercase().toByteArray()).toString(),
                email = cleanEmail,
                fullName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            )
            _currentUser.value = user
            saveUserToCache(user)
            return@withContext AuthResult(
                success = true,
                message = "Signed in successfully (Offline Vault)",
                isOffline = true,
                user = user
            )
        }

        try {
            SupabaseClientConfig.supabase.auth.signInWith(Email) {
                this.email = cleanEmail
                this.password = password
            }
            val user = SupabaseClientConfig.supabase.auth.currentUserOrNull()
            if (user != null) {
                val authUser = AuthUser(
                    id = user.id,
                    email = cleanEmail,
                    fullName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                )
                _currentUser.value = authUser
                saveUserToCache(authUser)
                return@withContext AuthResult(
                    success = true,
                    message = "Welcome back to Zenith!",
                    user = authUser
                )
            }
            AuthResult(false, "Unable to establish user session. Please try again.")
        } catch (e: Exception) {
            Log.e(tag, "signIn error: ${e.message}", e)
            val msg = e.message ?: ""
            val isNetwork = isNetworkException(e)

            if (isNetwork) {
                val cached = loadUserFromCache()
                if (cached != null && cached.email.equals(cleanEmail, ignoreCase = true)) {
                    _currentUser.value = cached
                    return@withContext AuthResult(
                        success = true,
                        message = "Network unavailable. Signed in using cached offline vault.",
                        isOffline = true,
                        user = cached
                    )
                }
                return@withContext AuthResult(
                    success = false,
                    message = "Network error: Unable to reach Zenith Cloud. Please check your internet connection or open Offline Vault."
                )
            }

            val friendlyMessage = when {
                msg.contains("Invalid login credentials", ignoreCase = true) ->
                    "Invalid email or password. Please verify your credentials."
                msg.contains("Email not confirmed", ignoreCase = true) ->
                    "Your email address is not verified yet. Please check your inbox for the confirmation link."
                msg.contains("User not found", ignoreCase = true) ->
                    "No account registered with this email. Please create an account first."
                msg.contains("rate limit", ignoreCase = true) ->
                    "Too many login attempts. Please wait a few moments and try again."
                else ->
                    cleanErrorMessage(msg).ifBlank { "Authentication failed. Please check your credentials." }
            }

            AuthResult(
                success = false,
                message = friendlyMessage,
                requiresEmailConfirmation = msg.contains("Email not confirmed", ignoreCase = true)
            )
        }
    }

    override suspend fun signUp(email: String, password: String, fullName: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanName = fullName.trim()

        if (cleanName.isBlank()) {
            return@withContext AuthResult(false, "Please enter your full name.")
        }
        if (cleanEmail.isBlank()) {
            return@withContext AuthResult(false, "Please enter your email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult(false, "Password must be at least 6 characters long.")
        }

        if (!SupabaseClientConfig.isConfigured) {
            val user = AuthUser(
                id = UUID.nameUUIDFromBytes(cleanEmail.lowercase().toByteArray()).toString(),
                email = cleanEmail,
                fullName = cleanName
            )
            _currentUser.value = user
            saveUserToCache(user)
            return@withContext AuthResult(
                success = true,
                message = "Account created successfully (Offline Vault)",
                isOffline = true,
                user = user
            )
        }

        try {
            SupabaseClientConfig.supabase.auth.signUpWith(Email) {
                this.email = cleanEmail
                this.password = password
            }
            val user = SupabaseClientConfig.supabase.auth.currentUserOrNull()
            if (user != null) {
                val authUser = AuthUser(
                    id = user.id,
                    email = cleanEmail,
                    fullName = cleanName
                )
                _currentUser.value = authUser
                saveUserToCache(authUser)
                try {
                    val profile = ProfileDto(
                        id = user.id,
                        fullName = cleanName,
                        email = cleanEmail
                    )
                    SupabaseClientConfig.supabase.postgrest["profiles"].insert(profile)
                } catch (pe: Exception) {
                    Log.w(tag, "Profile insert notice: ${pe.message}")
                }
                AuthResult(
                    success = true,
                    message = "Account created successfully! Welcome to Zenith.",
                    user = authUser
                )
            } else {
                // Email confirmation is required by Supabase project settings
                AuthResult(
                    success = true,
                    message = "Account created! A confirmation link has been sent to $cleanEmail. Please verify your email, then sign in.",
                    requiresEmailConfirmation = true
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "signUp error: ${e.message}", e)
            val msg = e.message ?: ""
            if (isNetworkException(e)) {
                return@withContext AuthResult(
                    success = false,
                    message = "Network error: Unable to connect to server. Please check your internet connection."
                )
            }

            val friendly = when {
                msg.contains("already registered", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) ->
                    "An account with this email already exists. Please sign in instead."
                msg.contains("at least 6 characters", ignoreCase = true) ->
                    "Password must be at least 6 characters long."
                msg.contains("valid email", ignoreCase = true) ->
                    "Please provide a valid email format."
                else ->
                    cleanErrorMessage(msg).ifBlank { "Unable to create account. Please check your details." }
            }
            AuthResult(success = false, message = friendly)
        }
    }

    override suspend fun resetPassword(email: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext AuthResult(false, "Please enter your email address.")
        }
        if (!SupabaseClientConfig.isConfigured) {
            return@withContext AuthResult(true, "Offline vault active. You can log in directly without password reset.", isOffline = true)
        }

        try {
            SupabaseClientConfig.supabase.auth.resetPasswordForEmail(cleanEmail)
            AuthResult(true, "Password reset instructions sent to $cleanEmail. Please check your inbox.")
        } catch (e: Exception) {
            Log.e(tag, "resetPassword error: ${e.message}", e)
            val msg = e.message ?: ""
            if (isNetworkException(e)) {
                AuthResult(false, "Network error: Unable to reach server. Please check your connection.")
            } else {
                AuthResult(false, cleanErrorMessage(msg).ifBlank { "Could not send password reset link." })
            }
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        if (SupabaseClientConfig.isConfigured) {
            try {
                SupabaseClientConfig.supabase.auth.signOut()
            } catch (e: Exception) {
                Log.e(tag, "signOut error: ${e.message}")
            }
        }
        clearUserCache()
        _currentUser.value = null
    }

    private fun saveUserToCache(user: AuthUser) {
        try {
            prefs?.edit()
                ?.putString(KEY_USER_ID, user.id)
                ?.putString(KEY_USER_EMAIL, user.email)
                ?.putString(KEY_USER_NAME, user.fullName)
                ?.apply()
        } catch (e: Exception) {
            Log.w(tag, "Failed to save user to cache: ${e.message}")
        }
    }

    private fun loadUserFromCache(): AuthUser? {
        val p = prefs ?: return null
        val id = p.getString(KEY_USER_ID, null) ?: return null
        val email = p.getString(KEY_USER_EMAIL, null)
        val name = p.getString(KEY_USER_NAME, null)
        return AuthUser(id = id, email = email, fullName = name)
    }

    private fun clearUserCache() {
        try {
            prefs?.edit()
                ?.remove(KEY_USER_ID)
                ?.remove(KEY_USER_EMAIL)
                ?.remove(KEY_USER_NAME)
                ?.apply()
        } catch (e: Exception) {
            Log.w(tag, "Failed to clear user cache: ${e.message}")
        }
    }

    private fun isNetworkException(e: Exception): Boolean {
        val name = e::class.java.simpleName
        val msg = e.message ?: ""
        return name.contains("Timeout", ignoreCase = true) ||
                name.contains("Host", ignoreCase = true) ||
                name.contains("Connect", ignoreCase = true) ||
                name.contains("Socket", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("network", ignoreCase = true)
    }

    private fun cleanErrorMessage(raw: String): String {
        return raw.replace(Regex("[{}\"\\[\\]]"), " ")
            .replace("error_description:", "")
            .replace("error:", "")
            .replace("message:", "")
            .trim()
    }

    companion object {
        private const val PREFS_AUTH_CACHE = "zenith_auth_user_cache"
        private const val KEY_USER_ID = "cached_user_id"
        private const val KEY_USER_EMAIL = "cached_user_email"
        private const val KEY_USER_NAME = "cached_user_name"
    }
}
