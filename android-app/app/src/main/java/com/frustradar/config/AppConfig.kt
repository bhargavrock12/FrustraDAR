package com.frustradar.config

/**
 * Backend endpoint configuration (04_API_CONTRACT.md): REST base `/api/v1`, WebSocket `/ws`.
 */
interface AppConfig {
    val apiBaseUrl: String
    val wsBaseUrl: String
}
