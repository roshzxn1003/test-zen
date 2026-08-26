package com.example.data.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupabaseAuthService {
    private val supabase = SupabaseClientConfig.supabase

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    init {
        // Monitor session changes
        // For simple offline caching, you could use gotrue session restoration if available
        // in supabase-kt, but for now we just hold memory state.
    }

    suspend fun restoreSession() {
        try {
            supabase.auth.awaitInitialization()
            _currentUser.value = supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun signUp(email: String, password: String, fullName: String): Boolean {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // Supabase auto logins on signup depending on confirm email settings
            _currentUser.value = supabase.auth.currentUserOrNull()
            
            // Create profile
            _currentUser.value?.let { user ->
                val profile = ProfileDto(
                    id = user.id,
                    fullName = fullName,
                    email = email
                )
                SupabaseClientConfig.supabase.postgrest["profiles"].insert(profile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun signIn(email: String, password: String): Boolean {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            _currentUser.value = supabase.auth.currentUserOrNull()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun signOut() {
        try {
            supabase.auth.signOut()
            _currentUser.value = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
