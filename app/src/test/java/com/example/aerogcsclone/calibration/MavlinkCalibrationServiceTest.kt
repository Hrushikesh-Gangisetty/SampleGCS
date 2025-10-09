package com.example.aerogcsclone.calibration

import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.definitions.common.MavCmd
import com.divpundir.mavlink.definitions.common.Statustext
import com.example.aerogcsclone.telemetry.MavlinkTelemetryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class MavlinkCalibrationServiceTest {

    private val testScope = TestScope()
    private lateinit var service: MavlinkCalibrationService
    private lateinit var mockTelemetryRepository: MavlinkTelemetryRepository
    private lateinit var mavFrameFlow: MutableSharedFlow<MavFrame<*>>

    @Before
    fun setup() {
        mavFrameFlow = MutableSharedFlow()
        mockTelemetryRepository = mock {
            whenever(it.mavFrame).thenReturn(mavFrameFlow)
        }
        service = MavlinkCalibrationService(mockTelemetryRepository, testScope)
    }

    @Test
    fun `startImuCalibration sends correct MAVLink command`() = testScope.runTest {
        service.startImuCalibration()

        verify(mockTelemetryRepository).sendCommandLong(
            argThat { command.value == MavCmd.PREFLIGHT_CALIBRATION.value },
            any(), any(), any(), argThat { this == 1.0f }, any(), any()
        )
    }

    @Test
    fun `cancelCalibration sends correct MAVLink command`() = testScope.runTest {
        service.cancelCalibration()

        verify(mockTelemetryRepository).sendCommandLong(
            argThat { command.value == MavCmd.PREFLIGHT_CALIBRATION.value },
            eq(0f), eq(0f), eq(0f), eq(0f), eq(0f), eq(0f)
        )
    }

    @Test
    fun `service state updates on STATUSTEXT message for user input`() = testScope.runTest {
        service.startImuCalibration()

        val statustext = Statustext.Builder().text("Place vehicle level").build()
        mavFrameFlow.emit(MavFrame(0, 0, 0, statustext))

        val state = service.calibrationState.value
        assertTrue(state is CalibrationState.AwaitingUserInput)
        assertEquals("Place vehicle level", (state as CalibrationState.AwaitingUserInput).instruction)
        assertEquals(ImuPosition.LEVEL, state.visualHint)
    }

    @Test
    fun `service state updates on STATUSTEXT message for success`() = testScope.runTest {
        service.startImuCalibration()

        val statustext = Statustext.Builder().text("Calibration successful").build()
        mavFrameFlow.emit(MavFrame(0, 0, 0, statustext))

        val state = service.calibrationState.value
        assertTrue(state is CalibrationState.Success)
        assertEquals("IMU Calibration Successful!", (state as CalibrationState.Success).summary)
    }

    @Test
    fun `service state updates on STATUSTEXT message for failure`() = testScope.runTest {
        service.startImuCalibration()

        val statustext = Statustext.Builder().text("Calibration FAILED").build()
        mavFrameFlow.emit(MavFrame(0, 0, 0, statustext))

        val state = service.calibrationState.value
        assertTrue(state is CalibrationState.Error)
        assertEquals("IMU Calibration Failed.", (state as CalibrationState.Error).message)
    }
}