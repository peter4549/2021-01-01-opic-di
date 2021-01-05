package com.duke.elliot.opicdi.audio_recoder

import android.app.Application
import androidx.lifecycle.ViewModel
import com.duke.elliot.opicdi.util.getCurrentTime
import com.duke.elliot.opicdi.util.toDateFormat

class AudioRecorderViewModel(private val application: Application): ViewModel() {

    var audioFilePath = createAudioFilePath()
    var state = AudioRecorderFragment.STOP

    private fun createAudioFilePath(): String {
        val currentTimeString = getCurrentTime().toDateFormat("yyyyMMddHHmmss")
        return application.getExternalFilesDir(null).toString() + "/${currentTimeString}.m4a"
    }
}