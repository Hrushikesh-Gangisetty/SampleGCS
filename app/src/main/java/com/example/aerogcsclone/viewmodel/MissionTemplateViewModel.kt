package com.example.aerogcsclone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aerogcsclone.database.MissionTemplateDatabase
import com.example.aerogcsclone.database.MissionTemplateEntity
import com.example.aerogcsclone.database.GridParameters
import com.example.aerogcsclone.repository.MissionTemplateRepository
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Mission Plan Templates
 */
class MissionTemplateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MissionTemplateRepository

    init {
        val database = MissionTemplateDatabase.getDatabase(application)
        repository = MissionTemplateRepository(database.missionTemplateDao())
    }

    // UI State
    private val _uiState = MutableStateFlow(MissionTemplateUiState())
    val uiState: StateFlow<MissionTemplateUiState> = _uiState.asStateFlow()

    // Templates flow
    val templates: Flow<List<MissionTemplateEntity>> = repository.getAllTemplates()

    /**
     * Save a new mission template
     */
    fun saveTemplate(
        projectName: String,
        plotName: String,
        waypoints: List<MissionItemInt>,
        waypointPositions: List<LatLng>,
        isGridSurvey: Boolean = false,
        gridParameters: GridParameters? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Validate input
            if (projectName.isBlank() || plotName.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Project name and plot name cannot be empty"
                )
                return@launch
            }

            if (waypoints.isEmpty() && !isGridSurvey) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Cannot save template with no waypoints"
                )
                return@launch
            }

            // Check if template with same names already exists
            val existingTemplate = repository.getTemplateByNames(projectName.trim(), plotName.trim())
            if (existingTemplate != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "A template with this project and plot name already exists"
                )
                return@launch
            }

            // Save template
            repository.saveTemplate(
                projectName = projectName,
                plotName = plotName,
                waypoints = waypoints,
                waypointPositions = waypointPositions,
                isGridSurvey = isGridSurvey,
                gridParameters = gridParameters
            ).fold(
                onSuccess = { templateId ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Template saved successfully",
                        lastSavedTemplateId = templateId
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to save template: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Load a template by ID
     */
    fun loadTemplate(templateId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val template = repository.getTemplateById(templateId)
            if (template != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedTemplate = template,
                    successMessage = "Template loaded successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Template not found"
                )
            }
        }
    }

    /**
     * Delete a template
     */
    fun deleteTemplate(template: MissionTemplateEntity) {
        viewModelScope.launch {
            repository.deleteTemplate(template).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Template deleted successfully"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to delete template: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Clear UI messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Clear selected template
     */
    fun clearSelectedTemplate() {
        _uiState.value = _uiState.value.copy(selectedTemplate = null)
    }
}

/**
 * UI State for Mission Template screen
 */
data class MissionTemplateUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedTemplate: MissionTemplateEntity? = null,
    val lastSavedTemplateId: Long? = null
)
