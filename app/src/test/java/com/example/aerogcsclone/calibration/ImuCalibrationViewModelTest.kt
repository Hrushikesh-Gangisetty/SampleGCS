package com.example.aerogcsclone.calibration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class ImuCalibrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ImuCalibrationViewModel
    private lateinit var mockCalibrationService: CalibrationService

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockCalibrationService = mock {
            on { calibrationState } doReturn MutableStateFlow(CalibrationState.Idle)
        }
        viewModel = ImuCalibrationViewModel(mockCalibrationService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onStartClicked calls startImuCalibration on service`() = runTest {
        viewModel.onStartClicked()
        verify(mockCalibrationService).startImuCalibration()
    }

    @Test
    fun `onPositionAcknowledged calls advanceImuCalibrationStep on service`() = runTest {
        val awaitingUserInputState = CalibrationState.AwaitingUserInput("Place vehicle level", ImuPosition.LEVEL)
        (mockCalibrationService.calibrationState as MutableStateFlow).value = awaitingUserInputState

        viewModel.onPositionAcknowledged()

        verify(mockCalibrationService).advanceImuCalibrationStep(ImuPosition.LEVEL)
    }

    @Test
    fun `onCancelClicked calls cancelCalibration on service`() = runTest {
        viewModel.onCancelClicked()
        verify(mockCalibrationService).cancelCalibration()
    }

    @Test
    fun `uiState reflects calibrationService state`() = runTest {
        val testState = CalibrationState.InProgress("Testing")
        (mockCalibrationService.calibrationState as MutableStateFlow).value = testState

        assertEquals(testState, viewModel.uiState.value)
    }
}