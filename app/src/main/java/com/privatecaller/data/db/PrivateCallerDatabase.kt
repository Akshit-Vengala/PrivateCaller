package com.privatecaller.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun toOutcome(value: String): ScreenOutcome = ScreenOutcome.valueOf(value)

    @TypeConverter
    fun fromOutcome(outcome: ScreenOutcome): String = outcome.name
}

@Database(
    entities = [
        MonitoredApp::class,
        UnblockWindow::class,
        ScreenedCall::class,
        BlockedNumber::class,
        ContactSimPref::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PrivateCallerDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun unblockWindowDao(): UnblockWindowDao
    abstract fun screenedCallDao(): ScreenedCallDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun contactSimPrefDao(): ContactSimPrefDao

    companion object {
        @Volatile private var instance: PrivateCallerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_numbers` (
                        `normalized` TEXT NOT NULL,
                        `rawNumber` TEXT NOT NULL,
                        `label` TEXT,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`normalized`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contact_sim_prefs` (
                        `normalized` TEXT NOT NULL,
                        `rawNumber` TEXT NOT NULL,
                        `simSlot` INTEGER,
                        PRIMARY KEY(`normalized`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): PrivateCallerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrivateCallerDatabase::class.java,
                    "private_caller.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
