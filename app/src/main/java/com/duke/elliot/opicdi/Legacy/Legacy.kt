package com.duke.elliot.opicdi.Legacy

import timber.log.Timber

/*
@SuppressLint("Recycle")
fun moveAudioFileToExternalStorage(audioFilePath: String): Boolean {
    val audioFileName = audioFilePath.fileName()
    val inputStream = FileInputStream(audioFilePath)
    val outputStream: OutputStream
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, audioFileName)
            // contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
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
 */

/*
fun deleteFile(path: String) {
    val audioFile = File(path)
    if (audioFile.exists())
        audioFile.delete()

    mediaScanner.scanMedia(path)
}
 */

/*
private fun convertToM4a() {
    @Suppress("SpellCheckingInspection")
    val ffmpeg = FFmpeg.getInstance(this)
    try {
        val audioFilePath = viewModel.audioFilePath.substringBeforeLast(".")
        val m4aAudioFilePath = "$audioFilePath.m4a"
        val cmd = arrayOf("-i", viewModel.audioFilePath, m4aAudioFilePath)
        ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onStart() {}
            override fun onProgress(message: String) {}
            override fun onFailure(message: String) {
                Timber.e("Failed to convert to m4a.: $message")
            }

            override fun onSuccess(message: String) {
                viewModel.deleteFile(viewModel.audioFilePath)
                if (viewModel.moveAudioFileToExternalStorage(m4aAudioFilePath))
                    Timber.d("Successfully moved the audio file to external storage.")
                else
                    Timber.e("Failed to move the audio file to external storage")
            }

            override fun onFinish() {}
        })
    } catch (e: FFmpegCommandAlreadyRunningException) {
        Timber.e(e)
    }
}
 */

/*
private fun initSeekBar() {
    binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                seekBar.updatePivot()
                if (binding.waveformView.isPlaying())
                    dragWhilePlaying()
            }

            updateTimer()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (binding.waveformView.isDraggingWhilePlaying()) {
                if (!binding.waveformView.isPivotAtEnd())
                    resumePlaying()
                else
                    stopPlaying()
            }
        }
    })
}
 */

/*
private fun SeekBar.updatePivot() {
    if (this.max.isNotZero()) {
        val pivot = (binding.waveformView.pulseCount().dec() * progressRate()).toInt()
        binding.waveformView.setPivot(pivot)
        binding.waveformView.invalidate()
    }
}

private fun SeekBar.update() {
    max = binding.waveformView.pulseCount().dec()
    if (binding.waveformView.pulseCount().isNotZero())
        progress = binding.waveformView.pivot()
}

private fun SeekBar.updateProgress() {
    if (binding.waveformView.pulseCount().isNotZero())
        progress = binding.waveformView.pivot()
}


 */

/*
@Suppress("SpellCheckingInspection")
val ffmpeg = FFmpeg.getInstance(this)
try {
    ffmpeg.loadBinary(object : LoadBinaryResponseHandler() {
        override fun onStart() {  }
        override fun onFailure() {  }
        override fun onSuccess() {  }
        override fun onFinish() {  }
    })
} catch (e: FFmpegNotSupportedException) {
    Timber.e(e)
}
 */

