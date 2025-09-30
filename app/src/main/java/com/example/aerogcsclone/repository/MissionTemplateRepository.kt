package com.example.aerogcsclone.repository

import com.example.aerogcsclone.database.MissionTemplateDao
import com.example.aerogcsclone.database.MissionTemplateEntity
import com.example.aerogcsclone.database.GridParameters
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Mission Template data operations
 * Provides a clean API for the ViewModel to access mission template data
 */
@Singleton
class MissionTemplateRepository @Inject constructor(
    private val missionTemplateDao: MissionTemplateDao
) {

    /**
     * Get all mission templates as a Flow for reactive UI updates
     */
    fun getAllTemplates(): Flow<List<MissionTemplateEntity>> {
        return missionTemplateDao.getAllTemplates()
    }

    /**
     * Get a specific template by ID
     */
    suspend fun getTemplateById(id: Long): MissionTemplateEntity? {
        return missionTemplateDao.getTemplateById(id)
    }

    /**
     * Check if a template with the same project and plot name already exists
     */
    suspend fun getTemplateByNames(projectName: String, plotName: String): MissionTemplateEntity? {
        return missionTemplateDao.getTemplateByNames(projectName, plotName)
    }

    /**
     * Save a new mission template
     */
    suspend fun saveTemplate(
        projectName: String,
        plotName: String,
        waypoints: List<MissionItemInt>,
        waypointPositions: List<LatLng>,
        isGridSurvey: Boolean = false,
        gridParameters: GridParameters? = null
    ): Result<Long> {
        return try {
            val template = MissionTemplateEntity(
                projectName = projectName.trim(),
                plotName = plotName.trim(),
                waypoints = waypoints,
                waypointPositions = waypointPositions,
                isGridSurvey = isGridSurvey,
                gridParameters = gridParameters,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val id = missionTemplateDao.insertTemplate(template)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing template
     */
    suspend fun updateTemplate(template: MissionTemplateEntity): Result<Unit> {
        return try {
            val updatedTemplate = template.copy(updatedAt = System.currentTimeMillis())
            missionTemplateDao.updateTemplate(updatedTemplate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a template
     */
    suspend fun deleteTemplate(template: MissionTemplateEntity): Result<Unit> {
        return try {
            missionTemplateDao.deleteTemplate(template)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a template by ID
     */
    suspend fun deleteTemplateById(id: Long): Result<Unit> {
        return try {
            missionTemplateDao.deleteTemplateById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the total count of templates
     */
    suspend fun getTemplateCount(): Int {
        return missionTemplateDao.getTemplateCount()
    }
}
