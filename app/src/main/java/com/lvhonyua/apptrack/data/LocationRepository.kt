package com.lvhonyua.apptrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest

object LocationRepository {
    private var settingsManager: SettingsManager? = null
    private var supabaseClient: SupabaseClient? = null
    private var dbHelper: LocalDbHelper? = null
    private var currentDeviceId: String = "unknown"
    private var currentDeviceName: String = "unknown"
    
    private val _locationRecords = MutableStateFlow<List<LocationRecord>>(emptyList())
    val locationRecords = _locationRecords.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        settingsManager = SettingsManager(context)
        dbHelper = LocalDbHelper(context)
        
        // 获取唯一的 Android ID 以区分设备
        currentDeviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, 
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        updateDeviceName(context)

        updateClient()
        refreshLocalRecords()
        // 显式触发一次同步
        syncUnsyncedRecords()
    }

    @SuppressLint("MissingPermission")
    fun updateDeviceName(context: Context) {
        val bluetoothName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.name
                } else null
            } else {
                android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.name
            }
        } catch (e: Exception) {
            null
        }
        
        currentDeviceName = bluetoothName ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        Log.d("LocationRepository", "Device name updated: $currentDeviceName")
    }

    private fun refreshLocalRecords() {
        repositoryScope.launch {
            val records = dbHelper?.getAllRecords() ?: emptyList()
            _locationRecords.value = records
        }
    }

    fun updateClient() {
        val settings = settingsManager ?: return
        if (settings.isConfigured()) {
            try {
                supabaseClient = createSupabaseClient(
                    supabaseUrl = settings.supabaseUrl,
                    supabaseKey = settings.supabaseAnonKey
                ) {
                    install(Postgrest)
                }
                Log.d("LocationRepository", "Supabase client updated, triggering sync...")
                syncUnsyncedRecords()
            } catch (e: Exception) {
                Log.e("LocationRepository", "Failed to create Supabase client: ${e.message}")
                supabaseClient = null
            }
        } else {
            supabaseClient = null
        }
    }

    suspend fun validateConnection(url: String, key: String, table: String = "locations"): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val tempClient = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
                    install(Postgrest)
                }
                
                // 执行一个带超时的简单查询
                withTimeout(15000) {
                    tempClient.postgrest.from(table).select {
                        limit(1)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                val friendlyError = when {
                    msg.contains("timeout") -> "连接超时：请确认网络正常。若一直失败，请检查 Supabase 表名是否正确，且表已创建。"
                    msg.contains("Invalid API key") -> "Anon Key 错误：请检查拼写。"
                    msg.contains("Failed to connect") -> "网络不可达：请检查 URL 是否正确。"
                    msg.contains("not found") -> "表不存在：请检查 Table Name 是否正确。"
                    else -> "验证失败: $msg"
                }
                Result.failure(Exception(friendlyError))
            }
        }
    }

    fun addRecord(record: LocationRecord) {
        repositoryScope.launch {
            // 自动填充设备 ID 和名称
            val recordWithDevice = record.copy(
                deviceId = currentDeviceId,
                deviceName = currentDeviceName
            )
            
            // 1. 保存到本地 SQLite
            val id = dbHelper?.insertRecord(recordWithDevice) ?: return@launch
            Log.d("LocationRepository", "New record saved locally with ID: $id")
            refreshLocalRecords()

            // 2. 尝试同步
            syncRecord(recordWithDevice.copy(id = id))
        }
    }

    fun syncUnsyncedRecords() {
        repositoryScope.launch {
            val unsynced = dbHelper?.getUnsyncedRecords() ?: return@launch
            if (unsynced.isEmpty()) {
                Log.d("LocationRepository", "No unsynced records found.")
                return@launch
            }
            
            Log.i("LocationRepository", "Found ${unsynced.size} unsynced records, starting sync...")
            unsynced.forEach { record ->
                syncRecord(record)
            }
        }
    }

    private suspend fun syncRecord(record: LocationRecord) {
        val client = supabaseClient
        val settings = settingsManager
        
        if (client == null) {
            Log.e("LocationRepository", "Sync failed: SupabaseClient is null. Check configuration.")
            return
        }
        if (settings == null) {
            Log.e("LocationRepository", "Sync failed: SettingsManager is null.")
            return
        }
        
        try {
            Log.d("LocationRepository", "Attempting to sync record: ${record.id} to table: ${settings.tableName}")
            // 由于 id 和 isSynced 已标记为 @Transient，它们不会被发送到 Supabase
            val response = client.postgrest.from(settings.tableName).insert(record)
            Log.d("LocationRepository", "Sync response for record ${record.id}: $response")
            
            // 3. 同步成功
            dbHelper?.markSynced(record.id)
            Log.i("LocationRepository", "Record ${record.id} marked as synced in local DB")
            refreshLocalRecords()
        } catch (e: Exception) {
            Log.e("LocationRepository", "Sync failed for record ${record.id}")
            Log.e("LocationRepository", "Error message: ${e.message}")
            Log.e("LocationRepository", "Error type: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
    
    fun initializeRealm() {}
}
