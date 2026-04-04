package com.sujalkatariya.qdec.citizen.DAO

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity

@Database(
    entities = [ComplaintEntity::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun complaintDao(): ComplaintDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qdec_db"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}