package com.borealis.dosey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicationEntity::class, DoseScheduleEntity::class, DoseLogEntity::class, OneOffDoseEntity::class],
    version = 5,
    exportSchema = false
)
abstract class DoseyDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseScheduleDao(): DoseScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun oneOffDoseDao(): OneOffDoseDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dose_schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        minutesAfterMidnight INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dose_schedules_medicationId_minutesAfterMidnight ON dose_schedules(medicationId, minutesAfterMidnight)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dose_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        medicationName TEXT NOT NULL,
                        scheduledDate TEXT NOT NULL,
                        minutesAfterMidnight INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dose_logs_medicationId_scheduledDate_minutesAfterMidnight ON dose_logs(medicationId, scheduledDate, minutesAfterMidnight)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN stockQuantity INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE medications ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE medications ADD COLUMN unitsPerDose INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS one_off_dose_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationName TEXT NOT NULL,
                        doseDescription TEXT NOT NULL,
                        scheduledDate TEXT NOT NULL,
                        minutesAfterMidnight INTEGER NOT NULL,
                        recordedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: DoseyDatabase? = null

        fun getInstance(context: Context): DoseyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DoseyDatabase::class.java,
                    "dosey.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
