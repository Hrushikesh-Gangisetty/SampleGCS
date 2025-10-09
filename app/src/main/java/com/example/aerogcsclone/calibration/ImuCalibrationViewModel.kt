package com.example.aerogcsclone.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImuCalibrationViewModel @Inject constructor(
    private val calibrationService: CalibrationService
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val uiState: StateFlow<CalibrationState> = _uiState.asStateFlow()

    init {
        calibrationService.calibrationState
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onStartClicked() {
        viewModelScope.launch {
            calibrationService.startImuCalibration()
        }
    }

    fun onPositionAcknowledged() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is CalibrationState.AwaitingUserInput) {
                calibrationService.advanceImuCalibrationStep(currentState.visualHint)
            }
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch {
            calibrationService.cancelCalibration()
        }
    }
}