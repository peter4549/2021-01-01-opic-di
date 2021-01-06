package com.duke.elliot.opicdi.audio_recoder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.base.BaseActivity
import com.duke.elliot.opicdi.databinding.ActivityAudioRecorderBinding
import com.duke.elliot.opicdi.util.*
import com.github.piasy.rxandroidaudio.StreamAudioPlayer
import com.github.piasy.rxandroidaudio.StreamAudioRecorder
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.abs

class AudioRecorderActivity: BaseActivity() {

    private lateinit var binding: ActivityAudioRecorderBinding
    private lateinit var viewModel: AudioRecorderViewModel
    private lateinit var inputStream: FileInputStream
    private lateinit var outputStream: FileOutputStream
    private lateinit var outputFile: File
    private lateinit var timer: Timer
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private var player: StreamAudioPlayer? = null
    private var recorder: StreamAudioRecorder? = null
    private var buffer = ByteArray(BUFFER_SIZE)
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_audio_recorder)

        val viewModelFactory = AudioRecorderViewModelFactory(application)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[AudioRecorderViewModel::class.java]

        setDisplayHomeAsUpEnabled(binding.toolbar)
        setOnHomePressedCallback { onBackPressed() }
        updateUI(AudioRecorderFragment.STOP)
        initSeekBar()
        binding.audioRecorderView.registerSeekBar(binding.seekBar)
        binding.audioRecorderView.registerTimerTextView(binding.recordingTimeTimer)

        binding.playPause.setOnClickListener {
            onPlay()
        }

        binding.recordPause.setOnClickListener {
            showToast("STATE: ${viewModel.state}")
            onRecord()
        }

        binding.stop.setOnClickListener {
            when(viewModel.state) {
                PAUSE_PLAYING -> stopPlaying()
                PAUSE_RECORDING -> stopRecording()
                PLAYING -> stopPlaying()
                RECORDING -> stopRecording()
            }
        }
    }

    private fun updateUI(state: Int) {
        when(state) {
            PAUSE_PLAYING -> {
                binding.playPause.scale(0.5F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
            }
            PAUSE_RECORDING -> {
                binding.recordPause.scale(0.5F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
            }
            PLAYING -> {
                binding.playPause.scale(0.5F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
            }
            RECORDING -> {
                binding.playPause.scale(1F, 200L)
                binding.stop.scale(1F, 200L)
                binding.recordPause.scale(0.5F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (binding.seekBar.isVisible)
                    binding.seekBar.fadeOut(200) {  }
            }
            STOP -> {
                binding.playPause.visibility = View.GONE
                binding.stop.visibility = View.GONE
            }
        }
    }

    private fun onPlay() {
        when(viewModel.state) {
            PAUSE_PLAYING -> resumePlaying()
            PAUSE_RECORDING -> startPlaying()
            PLAYING -> pausePlaying()
        }
    }

    private fun onRecord() {
        when (viewModel.state) {
            STOP -> startRecording()
            RECORDING -> pauseRecording()
            PAUSE_RECORDING -> resumeRecording()
            PAUSE_PLAYING -> resumeRecording()
        }
    }

    @SuppressLint("CheckResult")
    private fun startPlaying() {
        updateUI(PLAYING)
        viewModel.state = PLAYING

        player = StreamAudioPlayer.getInstance()
        Observable.just(outputFile).subscribeOn(Schedulers.io()).subscribe { file ->
            try {
                player?.init()
                inputStream = FileInputStream(file)

                if (binding.seekBar.isEnd())
                    binding.seekBar.progress = 0

                var playedBytes = (frameCount * binding.seekBar.progressRate() * BUFFER_SIZE).toInt()
                inputStream.skip(playedBytes.toLong())

                var read: Int
                while (inputStream.read(buffer).also { read = it } > 0) {
                    player?.play(buffer, read)
                    playedBytes += buffer.size
                    coroutineScope.launch {
                        binding.seekBar.progress = playedBytes / BUFFER_SIZE
                    }
                }

                inputStream.close()
                player?.release()
                coroutineScope.launch {
                    updateUI(PAUSE_PLAYING)
                    viewModel.state = PAUSE_PLAYING
                }
            } catch (e: IOException) {
                coroutineScope.launch {
                    if (viewModel.state == PLAYING)
                        showToast(getString(R.string.audio_player_playback_failure_message))
                }
                stopPlaying()
                e.printStackTrace()
            }
        }
    }

    private fun pausePlaying() {
        updateUI(PAUSE_PLAYING)
        viewModel.state = PAUSE_PLAYING
        player?.release()
        inputStream.close()
    }

    private fun resumePlaying() {
        startPlaying()
    }

    private fun stopPlaying() {
        player?.release()
    }

    private fun startRecording() {
        updateUI(RECORDING)
        viewModel.state = RECORDING

        var timerScheduled = false

        outputFile = File(viewModel.audioFilePath)
        outputFile.createNewFile()
        outputStream = FileOutputStream(outputFile)
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {
                outputStream.write(data, 0, data.size)

                if (!timerScheduled) {
                    timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() { updateAudioRecorderView(data) }
                    }, 0, DISPLAY_CHUNK_INTERVAL_MILLISECONDS)

                    timerScheduled = true
                }
            }

            override fun onError() {
                Timber.e("Audio recording failed.")
            }
        })
    }

    private fun pauseRecording() {
        updateUI(PAUSE_RECORDING)
        viewModel.state = PAUSE_RECORDING

        recorder?.stop()
        if (::timer.isInitialized)
            timer.cancel()

        val inputStream = FileInputStream(viewModel.audioFilePath)
        frameCount = inputStream.available() / BUFFER_SIZE
        inputStream.close()

        binding.seekBar.max = frameCount
        binding.seekBar.progress = frameCount
        binding.audioRecorderView.invalidate()
    }

    private fun resumeRecording() {
        updateUI(RECORDING)
        viewModel.state = RECORDING

        binding.audioRecorderView.halfReached = false // 기존의 리치드로..

        var timerScheduled = false

        outputStream = FileOutputStream(outputFile, true)
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {
                outputStream.write(data, 0, data.size)
                if (!timerScheduled) {
                    timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() { updateAudioRecorderView(data) }
                    }, 0, DISPLAY_CHUNK_INTERVAL_MILLISECONDS)

                    timerScheduled = true
                }
            }

            override fun onError() {
                Timber.e("Audio recording failed.")
            }
        })
    }

    private fun stopRecording() {
        updateUI(STOP)
        recorder?.stop()
    }

    private fun updateAudioRecorderView(data: ByteArray) {
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        shorts.maxOrNull()?.let {
            binding.audioRecorderView.update(abs(it.toInt()))
        }
    }

    private fun microphoneAvailable(context: Context): Boolean {
        val recorder = MediaRecorder()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
        recorder.setOutputFile(File(context.cacheDir, "MediaUtil#micAvailTestFile").absolutePath)
        var available = true
        try {
            recorder.prepare()
            recorder.start()
        } catch (exception: Exception) {
            available = false
        }
        recorder.release()
        return available
    }

    private fun initSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!binding.audioRecorderView.ignoreOnSeekBarChangeListenerInvoke)
                    binding.audioRecorderView.moveAccordingToProgress(progress, seekBar.max)

                val changedTime = (progress * binding.audioRecorderView.elapsedTime / frameCount.toFloat())
                binding.recordingTimeTimer.text = changedTime.toLong().toDateFormat(TIMER_PATTERN)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {  }

            override fun onStopTrackingTouch(seekBar: SeekBar) {  }
        })
    }

    private fun requestPermissions() {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted(response: PermissionGrantedResponse) {  }

            override fun onPermissionDenied(response: PermissionDeniedResponse) {
                finish()
            }

            override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
            ) {
                token?.continuePermissionRequest()
            }
        }

        Dexter.withContext(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(permissionListener)
                .check()
    }

    companion object {
        const val PAUSE_PLAYING = 0
        const val PAUSE_RECORDING = 1
        const val PLAYING = 2
        const val RECORDING = 3
        const val STOP = 4
    }
}