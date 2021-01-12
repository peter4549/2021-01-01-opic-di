package com.duke.elliot.opicdi.main

import android.app.Application
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException
import timber.log.Timber


class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

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
    }
}