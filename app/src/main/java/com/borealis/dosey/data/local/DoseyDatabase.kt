package com.borealis.dosey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DoseyDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var instance: DoseyDatabase? = null

        fun getInstance(context: Context): DoseyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DoseyDatabase::class.java,
                    "dosey.db"
                ).build().also { instance = it }
            }
    }
}
