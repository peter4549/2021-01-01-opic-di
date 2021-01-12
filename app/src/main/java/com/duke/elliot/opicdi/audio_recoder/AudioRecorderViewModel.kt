package com.duke.elliot.opicdi.audio_recoder

import android.app.Application
import androidx.lifecycle.ViewModel
import com.duke.elliot.opicdi.audio_recoder.AudioRecorderActivity.Companion.INITIALIZED
import com.duke.elliot.opicdi.audio_recoder.AudioRecorderActivity.Companion.STOP_RECORDING
import com.duke.elliot.opicdi.util.getCurrentTime
import com.duke.elliot.opicdi.util.toDateFormat
import java.io.File

class AudioRecorderViewModel(private val application: Application): ViewModel() {

    var audioFilePath = createAudioFilePath()
    var state = INITIALIZED

    private fun createAudioFilePath(): String {
        val currentTimeString = getCurrentTime().toDateFormat("yyyyMMddHHmmss")
        return application.getExternalFilesDir(null).toString() + "/${currentTimeString}.wav"
    }
}