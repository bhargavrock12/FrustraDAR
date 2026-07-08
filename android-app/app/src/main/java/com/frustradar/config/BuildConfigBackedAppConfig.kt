package com.frustradar.config

import com.frustradar.BuildConfig
import javax.inject.Inject

class BuildConfigBackedAppConfig @Inject constructor() : AppConfig {
    override val apiBaseUrl: String = BuildConfig.API_BASE_URL
    override val wsBaseUrl: String = BuildConfig.WS_BASE_URL
}
