package com.duke.elliot.opicdi.audio_recoder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.WorkerThread
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioRecorder private constructor() {
    private val isRecording: AtomicBoolean = AtomicBoolean(false)
    private var executorService: ExecutorService? = null
    @Synchronized
    fun start(audioDataCallback: AudioDataCallback): Boolean {
        return start(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, audioDataCallback
        )
    }

    @Synchronized
    fun start(
            sampleRate: Int, channelConfig: Int, audioFormat: Int,
            bufferSize: Int, audioDataCallback: AudioDataCallback
    ): Boolean {
        stop()
        executorService = Executors.newSingleThreadExecutor()
        if (isRecording.compareAndSet(false, true)) {
            executorService?.execute(
                    AudioRecordRunnable(
                            sampleRate, channelConfig, audioFormat, bufferSize,
                            audioDataCallback
                    )
            )
            return true
        }
        
        return false
    }

    @Synchronized
    fun stop() {
        isRecording.compareAndSet(true, false)
        executorService?.let {
            it.shutdown()
            executorService = null
        }
    }

    /**
     * Although Android frameworks jni implementation are the same for ENCODING_PCM_16BIT and
     * ENCODING_PCM_8BIT, the Java doc declared that the buffer type should be the corresponding
     * type, so we use different ways.
     */
    interface AudioDataCallback {
        @WorkerThread
        fun onAudioDataRecord(data: ByteArray, size: Int)
        fun onError()
    }

    private inner class AudioRecordRunnable constructor(
            sampleRate: Int, channelConfig: Int, private val mAudioFormat: Int, byteBufferSize: Int,
            audioDataCallback: AudioDataCallback
    ) :
        Runnable {
        private val audioRecord: AudioRecord
        private val audioDataCallback: AudioDataCallback
        private val byteBuffer: ByteArray
        private val shortBuffer: ShortArray
        private val byteBufferSize: Int
        private val shortBufferSize: Int

        override fun run() {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord.startRecording()
                } catch (e: IllegalStateException) {
                    Timber.e(e, "startRecording failed: ${e.message}")
                    audioDataCallback.onError()
                    return
                }

                while (isRecording.get()) {
                    var read: Int
                    if (mAudioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                        read = audioRecord.read(shortBuffer, 0, shortBufferSize)
                        if (read > 0)
                            audioDataCallback.onAudioDataRecord(
                                    short2byte(shortBuffer, read, byteBuffer), read * 2
                            )
                        else {
                            onError(read)
                            break
                        }
                    } else {
                        read = audioRecord.read(byteBuffer, 0, byteBufferSize)
                        if (read > 0)
                            audioDataCallback.onAudioDataRecord(byteBuffer, read)
                        else {
                            onError(read)
                            break
                        }
                    }
                }
            }

            audioRecord.release()
        }

        private fun short2byte(shortArray: ShortArray, size: Int, byteArray: ByteArray): ByteArray {
            if (size > shortArray.size || size * 2 > byteArray.size)
                Timber.e("short2byte: Too long short array.")
            for (i in 0 until size) {
                byteArray[i * 2] = (shortArray[i].toInt() and 0x00FF).toByte()
                byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
            }
            return byteArray
        }

        private fun onError(errorCode: Int) {
            if (errorCode == AudioRecord.ERROR_INVALID_OPERATION) {
                Timber.e("Record failed: ERROR_INVALID_OPERATION")
                audioDataCallback.onError()
            } else if (errorCode == AudioRecord.ERROR_BAD_VALUE) {
                Timber.e("Record failed: ERROR_BAD_VALUE")
                audioDataCallback.onError()
            }
        }

        init {
            val minBufferSize =
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, mAudioFormat)
            this.byteBufferSize = byteBufferSize
            shortBufferSize = this.byteBufferSize / 2
            byteBuffer = ByteArray(this.byteBufferSize)
            shortBuffer = ShortArray(shortBufferSize)
            audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    mAudioFormat, max(minBufferSize, byteBufferSize)
            )
            this.audioDataCallback = audioDataCallback
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioRecorder? = null

        fun getInstance(): AudioRecorder {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = AudioRecorder()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}