package com.frustradar.di

import com.frustradar.config.AppConfig
import com.frustradar.config.BuildConfigBackedAppConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAppConfig(impl: BuildConfigBackedAppConfig): AppConfig
}
