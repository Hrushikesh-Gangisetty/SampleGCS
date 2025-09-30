package com.example.aerogcsclone.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Mission Templates
 */
@Dao
interface MissionTemplateDao {

    @Query("SELECT * FROM mission_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<MissionTemplateEntity>>

    @Query("SELECT * FROM mission_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): MissionTemplateEntity?

    @Query("SELECT * FROM mission_templates WHERE projectName = :projectName AND plotName = :plotName LIMIT 1")
    suspend fun getTemplateByNames(projectName: String, plotName: String): MissionTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MissionTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: MissionTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: MissionTemplateEntity)

    @Query("DELETE FROM mission_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("SELECT COUNT(*) FROM mission_templates")
    suspend fun getTemplateCount(): Int
}
