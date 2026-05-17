package com.lvhonyua.apptrack.data

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // 统一的时间格式：包含日期、时间和时区
    private fun getSdf() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        settingsManager = SettingsManager(context)
        dbHelper = LocalDbHelper(context)
        
        currentDeviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, 
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        updateDeviceName(context)

        updateClient()
        refreshLocalRecords()
        
        syncUnsyncedRecords()
        syncUnsyncedApps()
        syncUnsyncedUsage()
        syncUnsyncedSessions()
        
        scanAndSaveInstalledApps(context)
        scanAndSaveAppUsage(context)
        scanAndSaveAppSessions(context)
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
                syncUnsyncedRecords()
                syncUnsyncedApps()
                syncUnsyncedUsage()
                syncUnsyncedSessions()
            } catch (e: Exception) {
                Log.e("LocationRepository", "Failed to create Supabase client: ${e.message}")
                supabaseClient = null
            }
        } else {
            supabaseClient = null
        }
    }

    fun scanAndSaveInstalledApps(context: Context) {
        repositoryScope.launch {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val sdf = getSdf()
            
            apps.forEach { appInfo ->
                val packageName = appInfo.packageName
                val appName = appInfo.loadLabel(pm).toString()
                val installTime = try {
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    sdf.format(Date(packageInfo.firstInstallTime))
                } catch (e: Exception) {
                    "Unknown"
                }

                val app = InstalledApp(
                    packageName = packageName,
                    appName = appName,
                    installTime = installTime,
                    deviceId = currentDeviceId,
                    deviceName = currentDeviceName
                )
                dbHelper?.insertApp(app)
            }
            syncUnsyncedApps()
        }
    }

    fun scanAndSaveAppUsage(context: Context) {
        repositoryScope.launch {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1) 
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (stats.isNullOrEmpty()) return@launch

            val pm = context.packageManager
            val sdf = getSdf()

            stats.forEach { usageStats ->
                if (usageStats.totalTimeInForeground > 0) {
                    val packageName = usageStats.packageName
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    
                    val record = AppUsageRecord(
                        packageName = packageName,
                        appName = appName,
                        usageTimeSeconds = usageStats.totalTimeInForeground / 1000,
                        lastTimeUsed = sdf.format(Date(usageStats.lastTimeUsed)),
                        deviceId = currentDeviceId,
                        deviceName = currentDeviceName
                    )
                    dbHelper?.insertUsage(record)
                }
            }
            syncUnsyncedUsage()
        }
    }

    fun scanAndSaveAppSessions(context: Context) {
        repositoryScope.launch {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, -12) 
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val pm = context.packageManager
            val sdf = getSdf()
            
            val openTimeMap = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                
                val pkg = event.packageName
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        openTimeMap[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = openTimeMap[pkg]
                        if (start != null) {
                            val duration = event.timeStamp - start
                            if (duration > 1000) { 
                                val appName = try {
                                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                } catch (e: Exception) {
                                    pkg
                                }
                                
                                val session = AppSessionRecord(
                                    packageName = pkg,
                                    appName = appName,
                                    startTime = sdf.format(Date(start)),
                                    durationSeconds = duration / 1000,
                                    deviceId = currentDeviceId,
                                    deviceName = currentDeviceName
                                )
                                dbHelper?.insertSession(session)
                            }
                            openTimeMap.remove(pkg)
                        }
                    }
                }
            }
            syncUnsyncedSessions()
        }
    }

    fun syncUnsyncedApps() {
        repositoryScope.launch {
            val unsynced = dbHelper?.getUnsyncedApps() ?: return@launch
            if (unsynced.isEmpty()) return@launch
            unsynced.forEach { syncApp(it) }
        }
    }

    private suspend fun syncApp(app: InstalledApp) {
        val client = supabaseClient ?: return
        try {
            client.postgrest.from("installed_apps").upsert(app) {
                onConflict = "device_id,package_name"
            }
            dbHelper?.markAppSynced(app.id)
        } catch (e: Exception) {
            Log.e("LocationRepository", "Sync app failed: ${app.packageName}")
        }
    }

    fun syncUnsyncedUsage() {
        repositoryScope.launch {
            val unsynced = dbHelper?.getUnsyncedUsage() ?: return@launch
            if (unsynced.isEmpty()) return@launch
            unsynced.forEach { syncUsage(it) }
        }
    }

    private suspend fun syncUsage(usage: AppUsageRecord) {
        val client = supabaseClient ?: return
        try {
            client.postgrest.from("app_usage_stats").upsert(usage) {
                onConflict = "device_id,package_name"
            }
            dbHelper?.markUsageSynced(usage.id)
        } catch (e: Exception) {
            Log.e("LocationRepository", "Sync usage failed for ${usage.packageName}")
        }
    }

    fun syncUnsyncedSessions() {
        repositoryScope.launch {
            val unsynced = dbHelper?.getUnsyncedSessions() ?: return@launch
            if (unsynced.isEmpty()) return@launch
            unsynced.forEach { syncSession(it) }
        }
    }

    private suspend fun syncSession(session: AppSessionRecord) {
        val client = supabaseClient ?: return
        try {
            client.postgrest.from("app_session_history").insert(session)
            dbHelper?.markSessionSynced(session.id)
        } catch (e: Exception) {
            Log.e("LocationRepository", "Sync session failed for ${session.appName}")
        }
    }

    suspend fun validateConnection(url: String, key: String, table: String = "locations"): Result<Unit> {      
        return withContext(Dispatchers.IO) {
            try {
                val tempClient = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
                    install(Postgrest)
                }
                withTimeout(15000) {
                    tempClient.postgrest.from(table).select { limit(1) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun addRecord(record: LocationRecord) {
        repositoryScope.launch {
            val recordWithDevice = record.copy(deviceId = currentDeviceId, deviceName = currentDeviceName)
            val id = dbHelper?.insertRecord(recordWithDevice) ?: return@launch
            refreshLocalRecords()
            syncRecord(recordWithDevice.copy(id = id))
        }
    }

    fun syncUnsyncedRecords() {
        repositoryScope.launch {
            val unsynced = dbHelper?.getUnsyncedRecords() ?: return@launch
            unsynced.forEach { syncRecord(it) }
        }
    }

    private suspend fun syncRecord(record: LocationRecord) {
        val client = supabaseClient ?: return
        val settings = settingsManager ?: return
        try {
            client.postgrest.from(settings.tableName).insert(record)
            dbHelper?.markSynced(record.id)
            refreshLocalRecords()
        } catch (e: Exception) {
            Log.e("LocationRepository", "Sync failed: ${e.message}")
        }
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
    
    fun initializeRealm() {}
}
