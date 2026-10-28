package com.borealis.dosey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicationEntity::class, DoseScheduleEntity::class, DoseLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class DoseyDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseScheduleDao(): DoseScheduleDao
    abstract fun doseLogDao(): DoseLogDao

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

        @Volatile
        private var instance: DoseyDatabase? = null

        fun getInstance(context: Context): DoseyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DoseyDatabase::class.java,
                    "dosey.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
