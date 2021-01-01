package com.duke.elliot.opicdi.database

import androidx.room.TypeConverter
import com.google.gson.Gson

class Converters {
    @TypeConverter
    fun stringArrayToJson(value: Array<String>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToStringArray(value: String): Array<String> = Gson().fromJson(value, Array<String>::class.java)

    @TypeConverter
    fun intArrayToJson(value: Array<Int>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToIntArray(value: String): Array<Int> = Gson().fromJson(value, Array<Int>::class.java)
}