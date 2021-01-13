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
        return application.externalCacheDir.toString() + "/${currentTimeString}.wav"
    }

    // TODO: abs path 리턴하는 방식으로 변경할 것.
    @SuppressLint("Recycle")
    fun moveAudioFileToExternalStorage(audioFilePath: String): Boolean {
        val audioFileName = audioFilePath.fileName()
        val inputStream = FileInputStream(audioFilePath)
        val outputStream: OutputStream
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, audioFileName)
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "audio/*")
                contentValues.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "$APPLICATION_DIR_PATH/"
                )
                val audioFileUri = application.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false
                outputStream = application.contentResolver.openOutputStream(audioFileUri) ?: return false
            } else {
                val applicationDir = File(applicationDir())
                if (!applicationDir.exists())
                    applicationDir.mkdir()
                val audioFile = File(applicationDir, audioFilePath.fileName())
                if (!audioFile.exists())
                    audioFile.createNewFile()
                outputStream = FileOutputStream(audioFile)
            }

            var read: Int
            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)
            while (inputStream.read(buffer).also { it.let { read = it } } != -1)
                outputStream.write(buffer, 0, read)

            deleteFile(audioFilePath)
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to move audio file.")
            return false
        }
    }

    fun deleteFile(path: String) {
        val audioFile = File(path)
        if (audioFile.exists())
            audioFile.delete()

        mediaScanner.scanMedia(path)
    }
}