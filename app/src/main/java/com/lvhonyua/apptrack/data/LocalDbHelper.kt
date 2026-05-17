package com.lvhonyua.apptrack.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class LocalDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "location_tracker_v5.db" // 再次升级文件名以彻底重置
        private const val DATABASE_VERSION = 17 
        private const val TABLE_NAME = "locations"
        private const val TABLE_APPS = "app_info_stats" 
        private const val TABLE_SESSIONS = "app_session_history"
        private const val TABLE_CALLS = "call_history"
        private const val TABLE_SMS = "sms_history"
        private const val TABLE_NOTIFICATIONS = "notification_history"
        
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_LATITUDE = "latitude"
        private const val COL_LONGITUDE = "longitude"
        private const val COL_PROVIDER = "provider"
        private const val COL_DEVICE_ID = "device_id"
        private const val COL_DEVICE_NAME = "device_name"
        private const val COL_BATTERY_LEVEL = "battery_level"
        private const val COL_SATELLITE_COUNT = "satellite_count"
        private const val COL_BEIDOU_COUNT = "beidou_count"
        private const val COL_GPS_COUNT = "gps_count"
        private const val COL_IS_SYNCED = "is_synced"

        private const val COL_PACKAGE_NAME = "package_name"
        private const val COL_APP_NAME = "app_name"
        private const val COL_INSTALL_TIME = "install_time"
        private const val COL_USAGE_TIME = "usage_time_s"
        private const val COL_LAST_TIME_USED = "last_time_used"

        private const val COL_START_TIME = "start_time"
        private const val COL_DURATION = "duration_s"

        private const val COL_NUMBER = "number"
        private const val COL_NAME = "name"
        private const val COL_TYPE = "type"
        private const val COL_TIME = "time"
        private const val COL_BODY = "body"
        private const val COL_ADDRESS = "address"

        private const val COL_TITLE = "title"
        private const val COL_CONTENT = "content"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
    }

    private fun createAllTables(db: SQLiteDatabase) {
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TIMESTAMP TEXT,
                    $COL_LATITUDE REAL,
                    $COL_LONGITUDE REAL,
                    $COL_PROVIDER TEXT,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_BATTERY_LEVEL INTEGER,
                    $COL_SATELLITE_COUNT INTEGER DEFAULT 0,
                    $COL_BEIDOU_COUNT INTEGER DEFAULT 0,
                    $COL_GPS_COUNT INTEGER DEFAULT 0,
                    $COL_IS_SYNCED INTEGER DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_APPS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PACKAGE_NAME TEXT UNIQUE,
                    $COL_APP_NAME TEXT,
                    $COL_INSTALL_TIME TEXT,
                    $COL_USAGE_TIME INTEGER DEFAULT 0,
                    $COL_LAST_TIME_USED TEXT,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_IS_SYNCED INTEGER DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PACKAGE_NAME TEXT,
                    $COL_APP_NAME TEXT,
                    $COL_START_TIME TEXT,
                    $COL_DURATION INTEGER,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_IS_SYNCED INTEGER DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_CALLS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NUMBER TEXT,
                    $COL_NAME TEXT,
                    $COL_TYPE TEXT,
                    $COL_TIME TEXT,
                    $COL_DURATION INTEGER,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_IS_SYNCED INTEGER DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_SMS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_ADDRESS TEXT,
                    $COL_BODY TEXT,
                    $COL_TYPE TEXT,
                    $COL_TIME TEXT,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_IS_SYNCED INTEGER DEFAULT 0
                )
            """.trimIndent())

            // 核心改进：为通知增加 UNIQUE 约束以去重
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_NOTIFICATIONS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PACKAGE_NAME TEXT,
                    $COL_APP_NAME TEXT,
                    $COL_TITLE TEXT,
                    $COL_CONTENT TEXT,
                    $COL_TIME TEXT,
                    $COL_DEVICE_ID TEXT,
                    $COL_DEVICE_NAME TEXT,
                    $COL_IS_SYNCED INTEGER DEFAULT 0,
                    UNIQUE($COL_PACKAGE_NAME, $COL_TITLE, $COL_CONTENT, $COL_TIME)
                )
            """.trimIndent())
        } catch (e: Exception) {
            Log.e("LocalDbHelper", "Error creating tables: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 17) {
            db.execSQL("DROP TABLE IF EXISTS locations")
            db.execSQL("DROP TABLE IF EXISTS app_info_stats")
            db.execSQL("DROP TABLE IF EXISTS app_session_history")
            db.execSQL("DROP TABLE IF EXISTS call_history")
            db.execSQL("DROP TABLE IF EXISTS sms_history")
            db.execSQL("DROP TABLE IF EXISTS notification_history")
            createAllTables(db)
        }
    }

    fun insertRecord(record: LocationRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_TIMESTAMP, record.timestamp)
                put(COL_LATITUDE, record.latitude)
                put(COL_LONGITUDE, record.longitude)
                put(COL_PROVIDER, record.provider)
                put(COL_DEVICE_ID, record.deviceId)
                put(COL_DEVICE_NAME, record.deviceName)
                put(COL_BATTERY_LEVEL, record.batteryLevel)
                put(COL_SATELLITE_COUNT, record.satelliteCount)
                put(COL_BEIDOU_COUNT, record.beidouCount)
                put(COL_GPS_COUNT, record.gpsCount)
                put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
            }
            db.insert(TABLE_NAME, null, values)
        } catch (e: Exception) { -1L }
    }

    fun markSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedRecords(): List<LocationRecord> {
        val records = mutableListOf<LocationRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_NAME, null, "$COL_IS_SYNCED = 0", null, null, null, null)
            with(cursor) {
                while (moveToNext()) {
                    records.add(LocationRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        timestamp = getString(getColumnIndexOrThrow(COL_TIMESTAMP)),
                        latitude = getDouble(getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = getDouble(getColumnIndexOrThrow(COL_LONGITUDE)),
                        provider = getString(getColumnIndexOrThrow(COL_PROVIDER)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        batteryLevel = getInt(getColumnIndexOrThrow(COL_BATTERY_LEVEL)),
                        satelliteCount = getInt(getColumnIndexOrThrow(COL_SATELLITE_COUNT)),
                        beidouCount = getInt(getColumnIndexOrThrow(COL_BEIDOU_COUNT)),
                        gpsCount = getInt(getColumnIndexOrThrow(COL_GPS_COUNT)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return records
    }

    fun getAllRecords(): List<LocationRecord> {
        val records = mutableListOf<LocationRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COL_ID DESC")
            with(cursor) {
                while (moveToNext()) {
                    records.add(LocationRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        timestamp = getString(getColumnIndexOrThrow(COL_TIMESTAMP)),
                        latitude = getDouble(getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = getDouble(getColumnIndexOrThrow(COL_LONGITUDE)),
                        provider = getString(getColumnIndexOrThrow(COL_PROVIDER)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        batteryLevel = getInt(getColumnIndexOrThrow(COL_BATTERY_LEVEL)),
                        satelliteCount = getInt(getColumnIndexOrThrow(COL_SATELLITE_COUNT)),
                        beidouCount = getInt(getColumnIndexOrThrow(COL_BEIDOU_COUNT)),
                        gpsCount = getInt(getColumnIndexOrThrow(COL_GPS_COUNT)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return records
    }

    fun insertAppInfo(app: AppInfo): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_PACKAGE_NAME, app.packageName)
                put(COL_APP_NAME, app.appName)
                put(COL_INSTALL_TIME, app.installTime)
                put(COL_USAGE_TIME, app.usageTimeSeconds)
                put(COL_LAST_TIME_USED, app.lastTimeUsed)
                put(COL_DEVICE_ID, app.deviceId)
                put(COL_DEVICE_NAME, app.deviceName)
                put(COL_IS_SYNCED, if (app.isSynced) 1 else 0)
            }
            db.insertWithOnConflict(TABLE_APPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) { -1L }
    }

    fun markAppSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_APPS, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedAppInfos(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_APPS, null, "$COL_IS_SYNCED = 0", null, null, null, "$COL_USAGE_TIME DESC")
            with(cursor) {
                while (moveToNext()) {
                    apps.add(AppInfo(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        packageName = getString(getColumnIndexOrThrow(COL_PACKAGE_NAME)),
                        appName = getString(getColumnIndexOrThrow(COL_APP_NAME)),
                        installTime = getString(getColumnIndexOrThrow(COL_INSTALL_TIME)),
                        usageTimeSeconds = getLong(getColumnIndexOrThrow(COL_USAGE_TIME)),
                        lastTimeUsed = getString(getColumnIndexOrThrow(COL_LAST_TIME_USED)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return apps
    }

    fun insertSession(session: AppSessionRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_PACKAGE_NAME, session.packageName)
                put(COL_APP_NAME, session.appName)
                put(COL_START_TIME, session.startTime)
                put(COL_DURATION, session.durationSeconds)
                put(COL_DEVICE_ID, session.deviceId)
                put(COL_DEVICE_NAME, session.deviceName)
                put(COL_IS_SYNCED, if (session.isSynced) 1 else 0)
            }
            db.insert(TABLE_SESSIONS, null, values)
        } catch (e: Exception) { -1L }
    }

    fun markSessionSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_SESSIONS, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedSessions(): List<AppSessionRecord> {
        val list = mutableListOf<AppSessionRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SESSIONS, null, "$COL_IS_SYNCED = 0", null, null, null, "$COL_ID DESC")
            with(cursor) {
                while (moveToNext()) {
                    list.add(AppSessionRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        packageName = getString(getColumnIndexOrThrow(COL_PACKAGE_NAME)),
                        appName = getString(getColumnIndexOrThrow(COL_APP_NAME)),
                        startTime = getString(getColumnIndexOrThrow(COL_START_TIME)),
                        durationSeconds = getLong(getColumnIndexOrThrow(COL_DURATION)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return list
    }

    fun insertCall(record: CallRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_NUMBER, record.number)
                put(COL_NAME, record.name)
                put(COL_TYPE, record.type)
                put(COL_TIME, record.time)
                put(COL_DURATION, record.durationSeconds)
                put(COL_DEVICE_ID, record.deviceId)
                put(COL_DEVICE_NAME, record.deviceName)
                put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
            }
            db.insert(TABLE_CALLS, null, values)
        } catch (e: Exception) { -1L }
    }

    fun markCallSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_CALLS, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedCalls(): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_CALLS, null, "$COL_IS_SYNCED = 0", null, null, null, "$COL_ID DESC")
            with(cursor) {
                while (moveToNext()) {
                    list.add(CallRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        number = getString(getColumnIndexOrThrow(COL_NUMBER)),
                        name = getString(getColumnIndexOrThrow(COL_NAME)),
                        type = getString(getColumnIndexOrThrow(COL_TYPE)),
                        time = getString(getColumnIndexOrThrow(COL_TIME)),
                        durationSeconds = getLong(getColumnIndexOrThrow(COL_DURATION)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return list
    }

    fun insertSms(record: SmsRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_ADDRESS, record.address)
                put(COL_BODY, record.body)
                put(COL_TYPE, record.type)
                put(COL_TIME, record.time)
                put(COL_DEVICE_ID, record.deviceId)
                put(COL_DEVICE_NAME, record.deviceName)
                put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
            }
            db.insert(TABLE_SMS, null, values)
        } catch (e: Exception) { -1L }
    }

    fun markSmsSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_SMS, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedSms(): List<SmsRecord> {
        val list = mutableListOf<SmsRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SMS, null, "$COL_IS_SYNCED = 0", null, null, null, "$COL_ID DESC")
            with(cursor) {
                while (moveToNext()) {
                    list.add(SmsRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        address = getString(getColumnIndexOrThrow(COL_ADDRESS)),
                        body = getString(getColumnIndexOrThrow(COL_BODY)),
                        type = getString(getColumnIndexOrThrow(COL_TYPE)),
                        time = getString(getColumnIndexOrThrow(COL_TIME)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return list
    }

    fun insertNotification(record: NotificationRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_PACKAGE_NAME, record.packageName)
                put(COL_APP_NAME, record.appName)
                put(COL_TITLE, record.title)
                put(COL_CONTENT, record.content)
                put(COL_TIME, record.time)
                put(COL_DEVICE_ID, record.deviceId)
                put(COL_DEVICE_NAME, record.deviceName)
                put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
            }
            // 使用 REPLACE 去重
            db.insertWithOnConflict(TABLE_NOTIFICATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) { -1L }
    }

    fun markNotificationSynced(id: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply { put(COL_IS_SYNCED, 1) }
            db.update(TABLE_NOTIFICATIONS, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    fun getUnsyncedNotifications(): List<NotificationRecord> {
        val list = mutableListOf<NotificationRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_NOTIFICATIONS, null, "$COL_IS_SYNCED = 0", null, null, null, "$COL_ID DESC")
            with(cursor) {
                while (moveToNext()) {
                    list.add(NotificationRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        packageName = getString(getColumnIndexOrThrow(COL_PACKAGE_NAME)),
                        appName = getString(getColumnIndexOrThrow(COL_APP_NAME)),
                        title = getString(getColumnIndexOrThrow(COL_TITLE)),
                        content = getString(getColumnIndexOrThrow(COL_CONTENT)),
                        time = getString(getColumnIndexOrThrow(COL_TIME)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    ))
                }
            }
            cursor.close()
        } catch (e: Exception) {}
        return list
    }
}
