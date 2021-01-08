package com.duke.elliot.opicdi.audio_recoder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.view.WaveformView
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
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.roundToInt

class AudioRecorderActivity: BaseActivity() {

    private lateinit var binding: ActivityAudioRecorderBinding
    private lateinit var viewModel: AudioRecorderViewModel
    private lateinit var recordTimer: Timer
    private lateinit var playTimer: Timer
    private lateinit var audioFile: RandomAccessFile
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private var player: StreamAudioPlayer? = null
    private var recorder: StreamAudioRecorder? = null
    private var buffer = ByteArray(BUFFER_SIZE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_audio_recorder)

        val viewModelFactory = AudioRecorderViewModelFactory(application)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[AudioRecorderViewModel::class.java]

        setDisplayHomeAsUpEnabled(binding.toolbar)
        setOnHomePressedCallback { onBackPressed() }
        updateUI(STOP_RECORDING)
        binding.waveformView.registerSeekBar(binding.seekBar)
        binding.waveformView.registerTimerTextView(binding.recordingTimeTimer)
        binding.waveformView.setOnTouchActionDownCallback {
            dragWhilePlaying()
        }

        binding.waveformView.setOnTouchActionUpCallback {
            resumePlaying()
        }

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
                PLAY -> stopPlaying()
                RECORD -> stopRecording()
            }
        }
    }

    private fun updateState(state: Int) {
        updateUI(state)
        viewModel.state = state
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
                binding.recordPause.isEnabled = true
            }
            PAUSE_RECORDING -> {
                binding.recordPause.scale(0.5F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
                binding.waveformView.isEnabled = true
                binding.playPause.isEnabled = true
            }
            PLAY -> {
                binding.playPause.scale(0.5F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
                binding.recordPause.isEnabled = false
            }
            RECORD -> {
                binding.playPause.scale(1F, 200L)
                binding.stop.scale(1F, 200L)
                binding.recordPause.scale(0.5F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (binding.seekBar.isVisible)
                    binding.seekBar.fadeOut(200) {  }
                binding.waveformView.isEnabled = false
                binding.playPause.isEnabled = false
            }
            STOP_PLAYING -> {
                binding.playPause.scale(0.5F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBar.isVisible)
                    binding.seekBar.fadeIn(200) {  }
                binding.recordPause.isEnabled = true
            }
            STOP_RECORDING -> {
                binding.waveformView.isEnabled = true
                binding.playPause.visibility = View.GONE
                binding.stop.visibility = View.GONE
            }
        }
    }

    private fun onPlay() {
        when(viewModel.state) {
            PAUSE_PLAYING -> resumePlaying()
            PAUSE_RECORDING -> startPlaying()
            PLAY -> pausePlaying()
            STOP_PLAYING -> startPlaying()
        }
    }

    private fun onRecord() {
        when (viewModel.state) {
            PAUSE_RECORDING -> resumeRecording()
            PAUSE_PLAYING -> resumeRecording()
            RECORD -> pauseRecording()
            STOP_PLAYING -> resumeRecording()
            STOP_RECORDING -> startRecording()
        }
    }

    @SuppressLint("CheckResult")
    private fun startPlaying() {
        updateState(PLAY)

        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")
        player = StreamAudioPlayer.getInstance()

        Observable.just(audioFile).subscribeOn(Schedulers.io()).subscribe { file ->
            try {
                player?.init()
                binding.waveformView.setMode(WaveformView.Mode.PLAY)

                val frames = audioFile.length().toInt() / BUFFER_SIZE
                val playedBytes = (frames * binding.waveformView.progressRate()).roundToInt() * BUFFER_SIZE
                file.seek(playedBytes.toLong())

                var read: Int
                var timerScheduled = false
                while (file.read(buffer).also { read = it } > 0) {
                    player?.play(buffer, read)

                    if (!timerScheduled) {
                        playTimer = Timer()
                        playTimer.schedule(object : TimerTask() {
                            override fun run() {
                                coroutineScope.launch {
                                    binding.waveformView.shiftPivot(1)
                                    binding.recordingTimeTimer.text = binding.waveformView.time().toDateFormat(TIMER_PATTERN)

                                    if (binding.waveformView.isPivotAtEnd())
                                        playTimer.cancel()
                                }
                            }
                        }, 0, UPDATE_INTERVAL_MILLISECONDS)

                        timerScheduled = true
                    }
                }

                file.close()
                player?.release()
                coroutineScope.launch {
                    stopPlaying()
                }
            } catch (e: IOException) {
                coroutineScope.launch {
                    if (viewModel.state == PLAY) {
                        showToast(getString(R.string.audio_player_playback_failure_message))
                        playTimer.cancel()
                        stopPlaying()
                    }
                }

                playTimer.cancel()
                file.close()
                e.printStackTrace()
            }
        }
    }

    private fun pausePlaying() {
        updateState(PAUSE_PLAYING)

        if (::playTimer.isInitialized)
            playTimer.cancel()
        audioFile.close()
        player?.release()

        binding.waveformView.setMode(WaveformView.Mode.PAUSE_PLAYING)
    }

    private fun dragWhilePlaying() {
        viewModel.state = PAUSE_PLAYING

        if (::playTimer.isInitialized)
            playTimer.cancel()
        audioFile.close()
        player?.release()

        binding.waveformView.setMode(WaveformView.Mode.DRAG_WHILE_PLAYING)
    }

    private fun resumePlaying() {
        startPlaying()
    }

    private fun stopPlaying() {
        updateState(STOP_PLAYING)

        audioFile.close()
        player?.release()

        binding.waveformView.setMode(WaveformView.Mode.STOP_PLAYING)
    }

    private fun startRecording() {
        updateState(RECORD)

        var timerScheduled = false

        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {
                try {
                    audioFile.write(data, 0, data.size)

                    if (!timerScheduled) {
                        binding.waveformView.setMode(WaveformView.Mode.RECORD)
                        recordTimer = Timer()
                        recordTimer.schedule(object : TimerTask() {
                            override fun run() {
                                updateWaveformView(data)
                                binding.recordingTimeTimer.text = binding.waveformView.time().toDateFormat(TIMER_PATTERN)
                            }
                        }, 0, UPDATE_INTERVAL_MILLISECONDS)

                        timerScheduled = true
                    }
                } catch (e: IOException) {
                    Timber.e(e)
                    recordTimer.cancel()
                    audioFile.close()
                }
            }

            override fun onError() {
                Timber.e("Audio recording failed.")
                recordTimer.cancel()
                audioFile.close()
            }
        })
    }

    private fun pauseRecording() {
        updateState(PAUSE_RECORDING)

        if (::recordTimer.isInitialized)
            recordTimer.cancel()
        audioFile.close()
        recorder?.stop()

        binding.waveformView.setMode(WaveformView.Mode.PAUSE_RECORDING)
        binding.waveformView.invalidate()
    }

    private fun resumeRecording() {
        updateUI(RECORD)
        viewModel.state = RECORD

        var timerScheduled = false
        var sought = false

        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {
                try {
                    if (!sought) {
                        val frames = audioFile.length().toInt() / BUFFER_SIZE
                        val playedBytes = (frames * binding.waveformView.progressRate()).roundToInt() * BUFFER_SIZE
                        audioFile.seek(playedBytes.toLong())
                        sought = true
                    }

                    audioFile.write(data, 0, data.size)

                    if (!timerScheduled) {
                        recordTimer = Timer()
                        recordTimer.schedule(object : TimerTask() {
                            override fun run() {
                                updateWaveformView(data, true)
                                binding.recordingTimeTimer.text = binding.waveformView.time().toDateFormat(TIMER_PATTERN)
                            }
                        }, 0, UPDATE_INTERVAL_MILLISECONDS)

                        timerScheduled = true
                    }
                } catch(e: IOException) {
                    Timber.e(e)
                    recordTimer.cancel()
                    audioFile.close()
                }
            }

            override fun onError() {
                Timber.e("Audio recording failed.")
                recordTimer.cancel()
                audioFile.close()
            }
        })
    }

    private fun stopRecording() {
        updateUI(STOP_RECORDING)
        audioFile.close()
        recorder?.stop()
    }

    private fun updateWaveformView(data: ByteArray, overwrite: Boolean = false) {
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        shorts.maxOrNull()?.let {
            if (overwrite)
                binding.waveformView.update(it.toInt(), WaveformView.Mode.OVERWRITE)
            else
                binding.waveformView.update(it.toInt(), WaveformView.Mode.RECORD)
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
        const val PLAY = 2
        const val RECORD = 3
        const val STOP_PLAYING = 4
        const val STOP_RECORDING = 5
    }
}