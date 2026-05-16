package com.lvhonyua.apptrack.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object LocationRepository {
    // TODO: Configure your Atlas Data API settings
    private const val BASE_URL = "https://data.mongodb-api.com/app/YOUR_APP_ID/endpoint/data/v1/"
    private const val API_KEY = "YOUR_API_KEY_HERE"
    private const val CLUSTER = "Cluster0"
    private const val DATABASE = "apptrack"
    private const val COLLECTION = "locations"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    private val api: AtlasDataApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AtlasDataApi::class.java)
    }

    private val _locationRecords = MutableStateFlow<List<LocationRecord>>(emptyList())
    val locationRecords = _locationRecords.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun initializeRealm() {
        // No longer needed for Data API, but kept for compatibility with UI call
    }

    fun addRecord(record: LocationRecord) {
        // 1. Update local UI state immediately
        _locationRecords.update { listOf(record) + it }

        // 2. Sync to Atlas in background
        repositoryScope.launch {
            try {
                val request = AtlasInsertRequest(
                    collection = COLLECTION,
                    database = DATABASE,
                    dataSource = CLUSTER,
                    document = record
                )
                api.insertOne(API_KEY, request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
}
