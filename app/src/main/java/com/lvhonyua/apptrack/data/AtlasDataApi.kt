package com.lvhonyua.apptrack.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AtlasDataApi {
    @POST("action/insertOne")
    suspend fun insertOne(
        @Header("apiKey") apiKey: String,
        @Body request: AtlasInsertRequest
    ): AtlasInsertResponse
}
