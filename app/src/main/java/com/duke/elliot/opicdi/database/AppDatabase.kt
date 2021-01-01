package com.duke.elliot.opicdi.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val APP_DATABASE_NAME = "com.duke.elliot.opicdi.database.app_data_base.debug.v1"

@Database(entities = [Script::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun scriptDao(): ScriptDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = this.instance

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        APP_DATABASE_NAME
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    this.instance = instance
                }

                return instance
            }
        }
    }
}