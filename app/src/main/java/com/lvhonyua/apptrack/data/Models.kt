package com.lvhonyua.apptrack.data

import kotlinx.serialization.Serializable

@Serializable
data class LocationRecord(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String
)

@Serializable
data class AtlasInsertRequest(
    val collection: String,
    val database: String,
    val dataSource: String,
    val document: LocationRecord
)

@Serializable
data class AtlasInsertResponse(
    val insertedId: String? = null
)
