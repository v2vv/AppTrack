package com.lvhonyua.apptrack.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "location_tracker.db"
        private const val DATABASE_VERSION = 3 // 升级到版本 3
        private const val TABLE_NAME = "locations"
        
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_LATITUDE = "latitude"
        private const val COL_LONGITUDE = "longitude"
        private const val COL_PROVIDER = "provider"
        private const val COL_DEVICE_ID = "device_id"
        private const val COL_DEVICE_NAME = "device_name"
        private const val COL_BATTERY_LEVEL = "battery_level"
        private const val COL_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP TEXT,
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL,
                $COL_PROVIDER TEXT,
                $COL_DEVICE_ID TEXT,
                $COL_DEVICE_NAME TEXT,
                $COL_BATTERY_LEVEL INTEGER,
                $COL_IS_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_DEVICE_ID TEXT DEFAULT 'unknown'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_DEVICE_NAME TEXT DEFAULT 'unknown'")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_BATTERY_LEVEL INTEGER DEFAULT -1")
        }
    }

    fun insertRecord(record: LocationRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, record.timestamp)
            put(COL_LATITUDE, record.latitude)
            put(COL_LONGITUDE, record.longitude)
            put(COL_PROVIDER, record.provider)
            put(COL_DEVICE_ID, record.deviceId)
            put(COL_DEVICE_NAME, record.deviceName)
            put(COL_BATTERY_LEVEL, record.batteryLevel)
            put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun markSynced(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_IS_SYNCED, 1)
        }
        db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getUnsyncedRecords(): List<LocationRecord> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, "$COL_IS_SYNCED = 0", null, null, null, null)
        val records = mutableListOf<LocationRecord>()
        with(cursor) {
            while (moveToNext()) {
                records.add(
                    LocationRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        timestamp = getString(getColumnIndexOrThrow(COL_TIMESTAMP)),
                        latitude = getDouble(getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = getDouble(getColumnIndexOrThrow(COL_LONGITUDE)),
                        provider = getString(getColumnIndexOrThrow(COL_PROVIDER)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        batteryLevel = getInt(getColumnIndexOrThrow(COL_BATTERY_LEVEL)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    )
                )
            }
        }
        cursor.close()
        return records
    }

    fun getAllRecords(): List<LocationRecord> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COL_ID DESC")
        val records = mutableListOf<LocationRecord>()
        with(cursor) {
            while (moveToNext()) {
                records.add(
                    LocationRecord(
                        id = getLong(getColumnIndexOrThrow(COL_ID)),
                        timestamp = getString(getColumnIndexOrThrow(COL_TIMESTAMP)),
                        latitude = getDouble(getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = getDouble(getColumnIndexOrThrow(COL_LONGITUDE)),
                        provider = getString(getColumnIndexOrThrow(COL_PROVIDER)),
                        deviceId = getString(getColumnIndexOrThrow(COL_DEVICE_ID)),
                        deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
                        batteryLevel = getInt(getColumnIndexOrThrow(COL_BATTERY_LEVEL)),
                        isSynced = getInt(getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
                    )
                )
            }
        }
        cursor.close()
        return records
    }
}
