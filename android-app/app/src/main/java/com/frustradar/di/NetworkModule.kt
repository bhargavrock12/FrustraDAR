package com.frustradar.di

import android.content.Context
import com.frustradar.auth.AuthInterceptor
import com.frustradar.config.AppConfig
import com.frustradar.data.local.AppDatabase
import com.frustradar.data.local.ScoreQueueDao
import com.frustradar.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt DI module providing Phase 2 networking, database, and auth components.
 *
 * Provides:
 * - OkHttpClient with AuthInterceptor and logging
 * - Retrofit instance pointing to the backend API
 * - All Retrofit API interfaces
 * - Room database and DAOs
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ── OkHttp ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Retrofit ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, appConfig: AppConfig): Retrofit {
        val baseUrl = appConfig.apiBaseUrl.let {
            if (it.endsWith("/")) it else "$it/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ── API Interfaces ────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideApiClient(retrofit: Retrofit, okHttpClient: OkHttpClient): ApiClient {
        return ApiClient(retrofit, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSessionsApi(retrofit: Retrofit): SessionsApi {
        return retrofit.create(SessionsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideScoresApi(retrofit: Retrofit): ScoresApi {
        return retrofit.create(ScoresApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAlertsApi(retrofit: Retrofit): AlertsApi {
        return retrofit.create(AlertsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi {
        return retrofit.create(UsersApi::class.java)
    }

    // ── Room Database ────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideScoreQueueDao(database: AppDatabase): ScoreQueueDao {
        return database.scoreQueueDao()
    }
}
