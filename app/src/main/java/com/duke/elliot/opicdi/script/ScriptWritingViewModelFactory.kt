package com.duke.elliot.opicdi.script

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.database.Script

class ScriptWritingViewModelFactory (
    private val application: Application,
    private val originalScript: Script?
): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (modelClass.isAssignableFrom(ScriptWritingViewModel::class.java)) {
            return ScriptWritingViewModel(application, originalScript) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}