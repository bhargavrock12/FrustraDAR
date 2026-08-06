package com.frustradar.ml

import com.google.gson.annotations.SerializedName

/**
 * Data classes representing the ml_manifest.json schema.
 */
data class MlManifestDto(
    @SerializedName("schema_version") val schemaVersion: String,
    @SerializedName("modalities") val modalities: ModalitiesDto
)

data class ModalitiesDto(
    @SerializedName("facial") val facial: ModalityConfigDto?,
    @SerializedName("voice") val voice: ModalityConfigDto?,
    @SerializedName("motion") val motion: ModalityConfigDto?
)

data class ModalityConfigDto(
    @SerializedName("artifact") val artifact: String,
    @SerializedName("present") val present: Boolean,
    @SerializedName("version") val version: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("sha256") val sha256: String
)
