package com.borealis.dosey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicationEntity::class, DoseScheduleEntity::class, DoseLogEntity::class, OneOffDoseEntity::class, CompanionSyncMetadataEntity::class],
    version = 6,
    exportSchema = false
)
abstract class DoseyDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseScheduleDao(): DoseScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun oneOffDoseDao(): OneOffDoseDao
    abstract fun companionSyncMetadataDao(): CompanionSyncMetadataDao

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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS companion_sync_metadata (
                        id INTEGER NOT NULL,
                        revision INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                installCompanionRevisionTracking(db)
            }
        }

        private fun installCompanionRevisionTracking(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO companion_sync_metadata (id, revision) VALUES (1, 1)")
            val tables = listOf(
                "medications",
                "dose_schedules",
                "dose_logs",
                "one_off_dose_logs"
            )
            tables.forEach { table ->
                listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                    val trigger = "companion_revision_${table}_${operation.lowercase()}"
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS $trigger
                        AFTER $operation ON $table
                        BEGIN
                            UPDATE companion_sync_metadata
                            SET revision = revision + 1
                            WHERE id = 1;
                        END
                    """.trimIndent())
                }
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            installCompanionRevisionTracking(db)
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
