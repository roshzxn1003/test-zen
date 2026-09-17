package com.example.data.repository

import com.example.data.dao.UserProfileDao
import com.example.data.models.UserProfileEntity
import com.example.data.network.ProfileDto
import com.example.data.network.SupabaseClientConfig
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

class UserProfileRepository(private val userProfileDao: UserProfileDao) {
    fun getProfileFlow(userId: String): Flow<UserProfileEntity?> {
        return userProfileDao.getProfileFlow(userId)
    }

    suspend fun getProfile(userId: String): UserProfileEntity? {
        return userProfileDao.getProfile(userId)
    }

    suspend fun syncProfile(userId: String) {
        if (!com.example.data.network.SupabaseClientConfig.isConfigured) return
        if (userId.isBlank() || userId == "local_user_1") return
        try {
            java.util.UUID.fromString(userId)
        } catch (e: Exception) {
            return
        }

        try {
            val profiles = com.example.data.network.SupabaseClientConfig.supabase.postgrest["profiles"]
                .select {
                    filter { eq("id", userId) }
                }
                .decodeList<com.example.data.network.ProfileDto>()

            val remote = profiles.firstOrNull()
            if (remote != null) {
                val entity = UserProfileEntity(
                    id = remote.id,
                    fullName = remote.fullName,
                    email = remote.email,
                    avatarUrl = remote.avatarUrl,
                    serverId = remote.id,
                    syncStatus = "SYNCED",
                    updatedAt = System.currentTimeMillis()
                )
                userProfileDao.insertProfile(entity)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserProfileRepo", "syncProfile error: ${e.message}")
        }
    }
}
