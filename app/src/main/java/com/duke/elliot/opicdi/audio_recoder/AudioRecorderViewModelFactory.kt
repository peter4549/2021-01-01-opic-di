package com.duke.elliot.opicdi.audio_recoder

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.main.ScriptsViewModel

class AudioRecorderViewModelFactory(private val application: Application): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (modelClass.isAssignableFrom(AudioRecorderViewModel::class.java)) {
            return AudioRecorderViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}