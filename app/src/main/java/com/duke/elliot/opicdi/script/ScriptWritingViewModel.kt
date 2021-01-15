package com.duke.elliot.opicdi.script

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duke.elliot.opicdi.database.AppDatabase
import com.duke.elliot.opicdi.database.Script
import com.duke.elliot.opicdi.script.audio.AudioFileMetaData
import kotlinx.coroutines.launch

class ScriptWritingViewModel(application: Application, val originalScript: Script?): ViewModel() {

    private val scriptDao = AppDatabase.getInstance(application).scriptDao()
    val audioFilePaths = mutableListOf<String>()

    fun insert(script: Script) {
        viewModelScope.launch {
            scriptDao.insert(script)
        }
    }

    fun update(script: Script) {
        viewModelScope.launch {
            scriptDao.update(script)
        }
    }
}