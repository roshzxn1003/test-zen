package com.example.data.dao

import androidx.room.*
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {

    @Query("SELECT * FROM families WHERE syncStatus = 'PENDING_CREATE'")
    suspend fun getPendingCreates(): List<FamilyEntity>
    
    @Query("SELECT * FROM families WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeletes(): List<FamilyEntity>

    @Query("SELECT * FROM families WHERE serverId = :serverId LIMIT 1")
    suspend fun getFamilyByServerId(serverId: String): FamilyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamily(family: FamilyEntity)

    @Update
    suspend fun updateFamily(family: FamilyEntity)

    @Delete
    suspend fun deleteFamily(family: FamilyEntity)

    @Query("DELETE FROM families WHERE id = :id")
    suspend fun deleteFamilyById(id: String)

    @Query("SELECT * FROM families WHERE id = :id")
    suspend fun getFamilyById(id: String): FamilyEntity?

    @Query("SELECT * FROM families LIMIT 1")
    suspend fun getFirstFamily(): FamilyEntity?

    @Query("SELECT * FROM families ORDER BY createdAt DESC")
    fun getAllFamilies(): Flow<List<FamilyEntity>>

    @Query("SELECT DISTINCT families.* FROM families LEFT JOIN family_members ON families.id = family_members.familyId WHERE families.createdByUserId = :userId OR family_members.userId = :userId")
    fun getAllFamiliesForUser(userId: String): Flow<List<FamilyEntity>>
}

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreates(): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdates(): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeletes(): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE serverId = :serverId LIMIT 1")
    suspend fun getMemberByServerId(serverId: String): FamilyMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FamilyMemberEntity)

    @Update
    suspend fun updateMember(member: FamilyMemberEntity)

    @Delete
    suspend fun deleteMember(member: FamilyMemberEntity)

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun deleteMemberById(id: String)

    @Query("SELECT * FROM family_members WHERE familyId = :familyId")
    fun getMembersByFamilyId(familyId: String): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE userId = :userId")
    fun getMemberByUserId(userId: String): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND userId = :userId")
    suspend fun getMemberByFamilyAndUser(familyId: String, userId: String): FamilyMemberEntity?
}
