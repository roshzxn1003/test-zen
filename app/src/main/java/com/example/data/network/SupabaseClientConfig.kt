package com.example.data.network

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.MemoryCodeVerifierCache

object SupabaseClientConfig {
    val isConfigured: Boolean
        get() {
            val url = BuildConfig.SUPABASE_URL
            val key = BuildConfig.SUPABASE_ANON_KEY
            return url.isNotBlank() &&
                    url.startsWith("https://") &&
                    !url.contains("YOUR_SUPABASE_URL") &&
                    !url.contains("your-project-id.supabase.co") &&
                    key.isNotBlank() &&
                    !key.contains("YOUR_SUPABASE_ANON_KEY") &&
                    !key.contains("your_supabase_anon_key_here")
        }

    val supabase: SupabaseClient by lazy {
        val safeUrl = if (isConfigured) BuildConfig.SUPABASE_URL else "https://fallback.zenith.vault.supabase.co"
        val safeKey = if (isConfigured) BuildConfig.SUPABASE_ANON_KEY else "fallback_anon_key_placeholder"

        createSupabaseClient(
            supabaseUrl = safeUrl,
            supabaseKey = safeKey
        ) {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
