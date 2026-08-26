package com.example.data.repository

import com.example.data.dao.UserProfileDao
import com.example.data.models.UserProfileEntity
import com.example.data.network.ProfileDto
import com.example.data.network.SupabaseClientConfig
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class UserProfileRepository(private val userProfileDao: UserProfileDao) {
    fun getProfileFlow(userId: String): Flow<UserProfileEntity?> {
        return userProfileDao.getProfileFlow(userId)
    }

    suspend fun getProfile(userId: String): UserProfileEntity? {
        return userProfileDao.getProfile(userId)
    }

    suspend fun syncProfile(userId: String) {
        try {
            // Fetch from Supabase
            val profileDto = SupabaseClientConfig.supabase.postgrest["profiles"]
                .select(Columns.ALL) {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<ProfileDto>()
            
            if (profileDto != null) {
                // Save locally
                val localProfile = UserProfileEntity(
                    id = profileDto.id,
                    fullName = profileDto.fullName,
                    email = profileDto.email,
                    avatarUrl = profileDto.avatarUrl,
                    serverId = profileDto.id,
                    syncStatus = "SYNCED",
                    updatedAt = System.currentTimeMillis(),
                    isDeleted = false
                )
                userProfileDao.insertProfile(localProfile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
