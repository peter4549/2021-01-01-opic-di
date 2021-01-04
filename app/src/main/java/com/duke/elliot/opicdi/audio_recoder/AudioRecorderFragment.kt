package com.duke.elliot.opicdi.audio_recoder

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaPlayer.OnBufferingUpdateListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.base.BaseFragment
import com.duke.elliot.opicdi.databinding.FragmentAudioRecorderBinding
import com.duke.elliot.opicdi.util.scale
import com.duke.elliot.opicdi.util.toDateFormat
import com.github.piasy.rxandroidaudio.StreamAudioPlayer
import com.github.piasy.rxandroidaudio.StreamAudioRecorder
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.Observable
import io.reactivex.Single.just
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


// TODO Activity로 바꿔서 컨피겨 체인지 봉인할 것.
class AudioRecorderFragment: BaseFragment() {

    private lateinit var binding: FragmentAudioRecorderBinding
    private lateinit var viewModel: AudioRecorderViewModel
    private var player: StreamAudioPlayer? = null
    private var recorder: StreamAudioRecorder? = null
    private lateinit var fileOutputStream: FileOutputStream
    private lateinit var outputFile: File
    private lateinit var timer: Timer
    private var recordingTime = 0L
    private var displayedTime = 0L
    var buffer = ByteArray(BUFFER_SIZE)

    private var byteCorrespondingToSeekBar = 0

    var audioFileSize = 0

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
                inflater,
                R.layout.fragment_audio_recorder,
                container,
                false
        )
        val viewModelFactory = AudioRecorderViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[AudioRecorderViewModel::class.java]

        setDisplayHomeAsUpEnabled(binding.toolbar)
        setOnHomePressedCallback { findNavController().popBackStack() }

        resetDisplayRecordingTime()
        updateUI(STOP)
        initSeekBar()
        binding.audioRecordView.registerSeekBar(binding.seekbar)

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

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        requestPermission()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        recorder?.stop()
    }

    private fun onPlay() {
        when(viewModel.state) {
            PAUSE_PLAYING -> resumePlaying()
            PAUSE_RECORDING -> startPlaying()
            PLAYING -> pausePlaying()
        }
    }

    private fun onRecord() {
        when(viewModel.state) {
            STOP -> startRecording()
            RECORDING -> pauseRecording()
            PAUSE_RECORDING -> resumeRecording()
        }
    }

    private fun updateUI(state: Int) {
        when(state) {
            PAUSE_PLAYING -> {
                binding.playPause.scale(0.75F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }
            }
            PAUSE_RECORDING -> {
                binding.recordPause.scale(0.75F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }
            }
            PLAYING -> {
                binding.playPause.scale(0.75F, 200L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }
            }
            RECORDING -> {
                binding.playPause.scale(1F, 200L)
                binding.stop.scale(1F, 200L)
                binding.recordPause.scale(0.75F, 200L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }
            }
            STOP -> {
                /*
                binding.playPause.visibility = View.GONE
                binding.stop.visibility = View.GONE
                 */
            }
        }
    }

    // TODO: Change to MultiPermissions audio and write external file.
    private fun requestPermission() {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted(response: PermissionGrantedResponse) {  }

            override fun onPermissionDenied(response: PermissionDeniedResponse) {
                findNavController().popBackStack()
            }

            override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
            ) {
                token?.continuePermissionRequest()
            }
        }

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(permissionListener)
            .check()
    }

    @SuppressLint("CheckResult")
    private fun startPlaying() {
        updateUI(PLAYING)
        viewModel.state = PLAYING

        player = StreamAudioPlayer.getInstance()
        Observable.just(outputFile).subscribeOn(Schedulers.io()).subscribe { file ->
            try {
                player?.init()
                val inputStream = FileInputStream(file)

                audioFileSize = inputStream.available()
                binding.seekbar.max = audioFileSize
                var read: Int

                // byteCorrespondingToSeekBar 만큼 pass 해야함.
                byteCorrespondingToSeekBar = (audioFileSize * (binding.seekbar.progress / binding.seekbar.max.toFloat())).toInt()
                //inputStream.read(ByteArray(byteCorrespondingToSeekBar))
                inputStream.skip(byteCorrespondingToSeekBar.toLong())

                println("BBBBBBBBB: $byteCorrespondingToSeekBar")
                var playedBytes = byteCorrespondingToSeekBar
                while (inputStream.read(buffer).also { read = it } > 0) {
                    player?.play(buffer, read)
                    playedBytes += buffer.size
                    coroutineScope.launch {
                        binding.seekbar.setProgress(playedBytes)
                    }
                }

                inputStream.close()
                player?.release()
                coroutineScope.launch {
                    updateUI(PAUSE_PLAYING)
                    viewModel.state = PAUSE_PLAYING
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }

    private fun pausePlaying() {
        updateUI(PAUSE_PLAYING)
        viewModel.state = PAUSE_PLAYING
        player?.release()
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

        var isTimerScheduled = false

        outputFile = File(viewModel.audioFilePath)
        outputFile.createNewFile()
        fileOutputStream = FileOutputStream(outputFile)
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {
                fileOutputStream.write(data, 0, data.size)

                if (!isTimerScheduled) {
                    timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            val shorts = ShortArray(data.size / 2)
                            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                    .get(
                                            shorts
                                    )

                            shorts.maxOrNull()?.let {
                                binding.audioRecordView.update(abs(it.toInt()) * 2)
                            }
                        }
                    }, 0, DISPLAY_CHUNK_INTERVAL_MILLISECONDS)

                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            recordingTime += RECORDING_TIME_INTERVAL_MILLISECONDS
                            binding.recordingTimeTimer.text = recordingTime.toDateFormat("mm:ss.SS")
                        }
                    }, 0, RECORDING_TIME_INTERVAL_MILLISECONDS)

                    isTimerScheduled = true
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
        audioFileSize = inputStream.available()

        /** Seek Bar */
        // binding.seekbar.max = recordingTime.toInt()
    }

    private fun resumeRecording() {
        updateUI(RECORDING)
        viewModel.state = RECORDING

        var isTimerScheduled = false

        fileOutputStream = FileOutputStream(outputFile, true)
        recorder = StreamAudioRecorder.getInstance()
        recorder?.start(object : StreamAudioRecorder.AudioDataCallback {
            override fun onAudioData(data: ByteArray, size: Int) {

                // TODO: move cursor logic for overwriting.

                fileOutputStream.write(data, 0, data.size)

                if (!isTimerScheduled) {
                    timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            val shorts = ShortArray(data.size / 2)
                            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                    .get(
                                            shorts
                                    )

                            shorts.maxOrNull()?.let {
                                binding.audioRecordView.update(abs(it.toInt()) * 2)
                            }
                        }
                    }, 0, DISPLAY_CHUNK_INTERVAL_MILLISECONDS)

                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            recordingTime += RECORDING_TIME_INTERVAL_MILLISECONDS
                            binding.recordingTimeTimer.text = recordingTime.toDateFormat("mm:ss.SS")
                        }
                    }, 0, RECORDING_TIME_INTERVAL_MILLISECONDS)

                    isTimerScheduled = true
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

    private fun resetDisplayRecordingTime() {
        recordingTime = 0L
        val text = "00:00.00"
        binding.recordingTimeTimer.text = text
    }

    private fun initSeekBar() {
        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if(!binding.audioRecordView.ignoreOnSeekBarChangeListenerInvoke) {
                    binding.audioRecordView.moveAccordingToProgress(progress, seekBar.max) // TODO: 조건문 내부적으로 처리.

                }

                val changedTime = progress * (recordingTime / audioFileSize.toFloat())
                println("LLLLL: $progress,,, $recordingTime,,, $audioFileSize,,, $changedTime")
                // TODO, 레코딩 퍼즈시, audioFileSize 반드시 업데이트 할것.
                binding.recordingTimeTimer.text = changedTime.toLong().toDateFormat("mm:ss.SS")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })
    }

    companion object {
        const val PAUSE_PLAYING = 0
        const val PAUSE_RECORDING = 1
        const val PLAYING = 2
        const val RECORDING = 3
        const val STOP = 4
    }
}