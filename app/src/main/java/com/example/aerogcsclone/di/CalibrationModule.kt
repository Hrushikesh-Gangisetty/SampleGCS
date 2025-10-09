package com.example.aerogcsclone.di

import com.example.aerogcsclone.calibration.CalibrationService
import com.example.aerogcsclone.calibration.MavlinkCalibrationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalibrationModule {

    @Binds
    abstract fun bindCalibrationService(
        mavlinkCalibrationService: MavlinkCalibrationService
    ): CalibrationService

    companion object {
        @Provides
        @Singleton
        fun provideCoroutineScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}