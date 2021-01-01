package com.duke.elliot.opicdi.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ScriptDao {
    @Query("SELECT * FROM script ORDER BY id DESC")
    fun getAll(): LiveData<MutableList<Script>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(script: Script)

    @Delete
    fun delete(script: Script)

    @Update
    fun update(script: Script)
}