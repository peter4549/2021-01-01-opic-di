package com.duke.elliot.opicdi.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ScriptsViewModelFactory(private val application: Application): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (modelClass.isAssignableFrom(ScriptsViewModel::class.java)) {
            return ScriptsViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}