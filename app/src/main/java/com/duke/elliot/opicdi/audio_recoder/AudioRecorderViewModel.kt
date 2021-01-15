package com.duke.elliot.opicdi.audio_recoder

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import com.duke.elliot.opicdi.audio_recoder.AudioRecorderActivity.Companion.INITIALIZED
import com.duke.elliot.opicdi.main.APPLICATION_DIR_PATH
import com.duke.elliot.opicdi.util.*
import com.duke.elliot.opicdi.util.MediaScanner
import timber.log.Timber
import java.io.*

class AudioRecorderViewModel(private val application: Application): ViewModel() {

    private val mediaScanner = MediaScanner.getInstance(application)
    var audioFilePath = createAudioFilePath()
    var state = INITIALIZED

    private fun createAudioFilePath(): String {
        val currentTimeString = getCurrentTime().toDateFormat("yyyyMMddHHmmss")
        return application.getExternalFilesDir(null).toString() + "/${currentTimeString}.wav"
    }
}