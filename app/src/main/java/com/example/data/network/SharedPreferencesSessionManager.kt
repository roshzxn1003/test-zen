package com.example.data.network

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Robust, persistent session manager for Supabase Auth on Android.
 * Serializes and deserializes [UserSession] into private [SharedPreferences].
 * Ensures that JWT tokens and refresh tokens survive app kills, restarts, and backgrounding.
 */
class SharedPreferencesSessionManager(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
) : SessionManager {

    private val tag = "ZenithSessionManager"
    private val prefs by lazy {
        context.getSharedPreferences("zenith_supabase_auth_session", Context.MODE_PRIVATE)
    }

    override suspend fun saveSession(session: UserSession) {
        withContext(Dispatchers.IO) {
            try {
                val serialized = json.encodeToString(UserSession.serializer(), session)
                prefs.edit().putString(KEY_SESSION_DATA, serialized).apply()
                Log.d(tag, "Saved Supabase session successfully for user: ${session.user?.id}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to save Supabase session: ${e.message}", e)
            }
        }
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_SESSION_DATA, null) ?: return@withContext null
        try {
            val session = json.decodeFromString(UserSession.serializer(), raw)
            Log.d(tag, "Loaded Supabase session successfully for user: ${session.user?.id}")
            session
        } catch (e: Exception) {
            Log.e(tag, "Failed to decode saved Supabase session: ${e.message}", e)
            null
        }
    }

    override suspend fun deleteSession() {
        withContext(Dispatchers.IO) {
            try {
                prefs.edit().remove(KEY_SESSION_DATA).apply()
                Log.d(tag, "Cleared saved Supabase session.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to clear Supabase session: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val KEY_SESSION_DATA = "saved_supabase_session"
    }
}
