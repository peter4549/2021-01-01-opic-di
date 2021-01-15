package com.duke.elliot.opicdi.audio_recoder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.view.WaveformView
import com.duke.elliot.opicdi.base.BaseActivity
import com.duke.elliot.opicdi.databinding.ActivityAudioRecorderBinding
import com.duke.elliot.opicdi.script.ScriptWritingFragment.Companion.EXTRA_NAME_AUDIO_FILE_METADATA
import com.duke.elliot.opicdi.util.*
import com.github.piasy.rxandroidaudio.StreamAudioPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

class AudioRecorderActivity: BaseActivity(), WaveformView.OnTouchListener {
    private lateinit var binding: ActivityAudioRecorderBinding
    private lateinit var viewModel: AudioRecorderViewModel
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private var player: StreamAudioPlayer = StreamAudioPlayer.getInstance()
    private var recorder: AudioRecorder = AudioRecorder.getInstance()
    private var audioFile: RandomAccessFile? = null
    private var buffer = ByteArray(BUFFER_SIZE)
    private var bitDepth = BIT_DEPTH
    private var channels = CHANNELS
    private var sampleRate = SAMPLE_RATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_audio_recorder)

        val viewModelFactory = AudioRecorderViewModelFactory(application)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[AudioRecorderViewModel::class.java]

        setDisplayHomeAsUpEnabled(binding.toolbar)
        setOnHomePressedCallback { onBackPressed() }
        updateState(INITIALIZED)
        initSeekBar()

        binding.waveformView.setOnTouchListener(this)

        @SuppressLint("SetTextI18n")
        binding.timer.text = "00:00.00"
        val text = "00:00"
        binding.elapsedTime.text = text
        binding.totalTime.text = text

        binding.playPause.setOnClickListener {
            onPlay()
        }

        binding.recordPause.setOnClickListener {
            onRecord()
        }

        binding.stop.setOnClickListener {
            stop()
        }
    }

    override fun onResume() {
        super.onResume()
        requestPermissions()
    }

    private fun updateState(state: Int) {
        updateUI(state)
        viewModel.state = state
    }

    private fun updateUI(state: Int) {
        when(state) {
            INITIALIZED -> {
                binding.waveformView.isEnabled = false
                binding.playPause.visibility = View.GONE
                binding.stop.visibility = View.GONE
            }
            PAUSE_PLAYING -> {
                binding.playPause.scaleDown(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isNotVisible())
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.recordPause.isEnabled = true
            }
            PAUSE_RECORDING -> {
                binding.recordPause.scaleDown(0.5F, 100L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isNotVisible())
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.waveformView.isEnabled = true
                binding.playPause.isEnabled = true
            }
            PLAYING -> {
                binding.playPause.scaleDown(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isNotVisible())
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.recordPause.isEnabled = false
            }
            RECORDING -> {
                binding.playPause.scaleUp(1F, 100L)
                binding.stop.scaleUp(1F, 100L)
                binding.recordPause.scaleDown(0.5F, 100L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.GONE
                binding.waveformView.isEnabled = false
                binding.playPause.isEnabled = false
            }
            STOP_PLAYING -> {
                binding.playPause.scaleDown(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isNotVisible())
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.recordPause.isEnabled = true
            }
            STOP_RECORDING -> {
                binding.recordPause.scaleDown(0.5F, 100L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scaleUp(1F, 100L)
                }

                if (binding.seekBarContainer.isNotVisible())
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.waveformView.isEnabled = true
                binding.playPause.isEnabled = true
            }
        }
    }

    private fun onPlay() {
        when(viewModel.state) {
            PAUSE_PLAYING -> resumePlaying()
            PAUSE_RECORDING -> startPlaying()
            PLAYING -> pausePlaying()
            STOP_PLAYING -> startPlaying()
            STOP_RECORDING -> startPlaying()
        }
    }

    private fun onRecord() {
        when (viewModel.state) {
            INITIALIZED -> startRecording()
            PAUSE_RECORDING -> resumeRecording()
            PAUSE_PLAYING -> resumeRecording()
            RECORDING -> pauseRecording()
            STOP_PLAYING -> resumeRecording()
            STOP_RECORDING -> startRecording()
        }
    }

    @SuppressLint("CheckResult")
    private fun startPlaying() {
        updateState(PLAYING)
        binding.waveformView.updateState(WaveformView.State.PLAYING)
        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")

        Observable.just(audioFile).subscribeOn(Schedulers.io()).subscribe { randomAccessFile ->
            randomAccessFile?.let { file ->
                try {
                    player.init(
                            sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE
                    )

                    var audioFileLength = audioFile?.length() ?: 44
                    audioFileLength -= 44L
                    val frameCount: Double = audioFileLength / BUFFER_SIZE.toDouble()
                    val playedFrames = (frameCount * binding.waveformView.progressRate()).roundToLong()
                    val playedBytes = playedFrames * BUFFER_SIZE
                    file.seek(playedBytes)

                    var read: Int
                    while (file.read(buffer).also { read = it } > 0) {
                        player.play(buffer, read)
                        coroutineScope.launch {
                            binding.waveformView.shiftPivot(1)
                            updateTimer()
                            binding.seekBar.updateProgress()
                        }
                    }

                    file.close()
                    player.release()
                    coroutineScope.launch {
                        stopPlaying()
                    }
                } catch (e: IOException) {
                    coroutineScope.launch {
                        if (viewModel.state == PLAYING) {
                            showToast(getString(R.string.audio_player_playback_failure_message))
                            stopPlaying()
                        }
                    }

                    e.printStackTrace()
                }
            }
        }
    }

    private fun pausePlaying() {
        updateState(PAUSE_PLAYING)
        binding.waveformView.updateState(WaveformView.State.PAUSE_PLAYING)
        audioFile?.close()
        audioFile = null
        player.release()
    }

    private fun dragWhilePlaying() {
        viewModel.state = PAUSE_PLAYING
        binding.waveformView.updateState(WaveformView.State.DRAG_WHILE_PLAYING)
        audioFile?.close()
        player.release()
    }

    private fun resumePlaying() {
        startPlaying()
    }

    private fun stopPlaying() {
        updateState(STOP_PLAYING)
        binding.waveformView.updateState(WaveformView.State.STOP_PLAYBACK)
        audioFile?.close()
        audioFile = null
        player.release()
    }

    private fun startRecording() {
        updateState(RECORDING)
        binding.waveformView.updateState(WaveformView.State.RECORDING)
        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")
        writeWavHeader(channels.toShort(), sampleRate, bitDepth.toShort())
        recorder.start(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, object : AudioRecorder.AudioDataCallback {
            override fun onAudioDataRecord(data: ByteArray, size: Int) {
                try {
                    audioFile?.write(data, 0, data.size)
                    coroutineScope.launch {
                        updateWaveformView(data)
                        updateTimer()
                    }

                } catch (e: IOException) {
                    Timber.e(e)
                    audioFile?.close()
                }
            }

            override fun onError() {
                Timber.e("Audio recording failed.")
                audioFile?.close()
            }
        })
    }

    private fun pauseRecording() {
        updateState(PAUSE_RECORDING)
        binding.waveformView.updateState(WaveformView.State.PAUSE_RECORDING)
        recorder.stop()
        binding.seekBar.update()
        binding.totalTime.text = binding.waveformView.totalTime().toDateFormat(TIMESTAMP_PATTERN)
    }

    private fun resumeRecording() {
        updateState(RECORDING)
        binding.waveformView.updateState(WaveformView.State.OVERWRITING)
        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")

        var audioFileLength = audioFile?.length() ?: 44
        audioFileLength -= 44L
        val frameCount: Double = audioFileLength / BUFFER_SIZE.toDouble()
        val playedFrames = (frameCount * binding.waveformView.progressRate()).roundToLong()
        val playedBytes = playedFrames * BUFFER_SIZE
        audioFile?.seek(playedBytes)

        recorder.start(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE,
                object : AudioRecorder.AudioDataCallback {
                    override fun onAudioDataRecord(data: ByteArray, size: Int) {
                        try {
                            audioFile?.write(data, 0, data.size)
                            coroutineScope.launch {
                                updateWaveformView(data)
                                updateTimer()
                            }
                        } catch (e: IOException) {
                            Timber.e(e)
                            audioFile?.close()
                        }
                    }

                    override fun onError() {
                        Timber.e("Audio recording failed.")
                        audioFile?.close()
                    }
                })
    }

    private fun stopRecording() {
        updateState(STOP_RECORDING)
        binding.waveformView.updateState(WaveformView.State.STOP_RECORDING)
        audioFile?.close()
        audioFile = null
        recorder.stop()
    }

    private fun stop() {
        when(viewModel.state) {
            PLAYING -> stopPlaying()
            RECORDING -> stopRecording()
        }

        updateWavHeader(File(viewModel.audioFilePath))
        audioFile?.close()
        audioFile = null
        recorder.stop()

        showSaveConfirmationMessage()
    }

    private fun updateWaveformView(data: ByteArray) {
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        shorts.maxOrNull()?.let {
            binding.waveformView.update(it.toInt())
        }
    }

    private fun microphoneAvailable(context: Context): Boolean {
        val recorder = MediaRecorder()
        recorder.maxAmplitude
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
        Dexter.withContext(this)
                .withPermissions(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted())
                            Timber.d("All permissions are granted.")
                        else
                            finish()
                    }

                    override fun onPermissionRationaleShouldBeShown(
                            permissions: List<PermissionRequest>,
                            token: PermissionToken
                    ) {
                        token.continuePermissionRequest()
                    }
                }).check()
    }

    private fun updateTimer() {
        val time = binding.waveformView.elapsedTime()
        binding.timer.text = time.toDateFormat(TIMER_PATTERN)
        binding.elapsedTime.text = time.toDateFormat(TIMESTAMP_PATTERN)
    }

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

    override fun onBackPressed() {
        when(viewModel.state) {
            PLAYING -> pausePlaying()
            RECORDING -> pauseRecording()
        }
        // showAudioFileNameInputDialog()
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isRecording())
            pauseRecording()

        if (isPlaying())
            pausePlaying()
    }

    override fun onTouchActionDown() {
        dragWhilePlaying()
    }

    override fun onTouchActionMove() {
        updateTimer()
        binding.seekBar.updateProgress()
    }

    override fun onTouchActionUp() {
        if (!binding.waveformView.isPivotAtEnd())
            resumePlaying()
        else
            stopPlaying()
    }

    private fun showSaveConfirmationMessage() {
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.save_confirmation_message_title))
            .setMessage(resources.getString(R.string.save_confirmation_message_message))
            .setNeutralButton(resources.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(R.string.save_confirmation_message_negative_button_text)) { dialog, which ->
                dialog.dismiss()
                val intent = Intent()
                setResult(RESULT_CANCELED, intent)
                finish()
            }
            .setPositiveButton(resources.getString(R.string.save_confirmation_message_positive_button_text)) { dialog, which ->
                dialog.dismiss()
                val intent = Intent()
                intent.putExtra(EXTRA_NAME_AUDIO_FILE_METADATA, viewModel.audioFilePath)
                setResult(RESULT_OK, intent)
                finish()
            }
            .show()
    }

    private fun updateWavHeader(wavFile: File) {
        val byteBuffer = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((wavFile.length() - 8).toInt())
                .putInt((wavFile.length() - 44).toInt())
                .array()

        val randomAccessWavFile = RandomAccessFile(wavFile, "rw")
        try {
            randomAccessWavFile.seek(4)
            randomAccessWavFile.write(byteBuffer, 0, 4)
            randomAccessWavFile.seek(40)
            randomAccessWavFile.write(byteBuffer, 4, 4)

        } catch (e: IOException) {
            throw e
        } finally {
            try {
                randomAccessWavFile.close()
            } catch (e: IOException) {

            }
        }
    }

    private fun writeWavHeader(channels: Short, sampleRate: Int, bitDepth: Short) {
        val wavHeader = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((channels * (bitDepth / 8)).toShort())
                .putShort(bitDepth)
                .array()
        audioFile?.write(
                byteArrayOf(
                        'R'.toByte(), 'I'.toByte(), 'F'.toByte(), 'F'.toByte(), // Chunk ID
                        0, 0, 0, 0, // Chunk Size
                        'W'.toByte(), 'A'.toByte(), 'V'.toByte(), 'E'.toByte(), // Format
                        'f'.toByte(), 'm'.toByte(), 't'.toByte(), ' '.toByte(), // Sub-chunk1 ID
                        16, 0, 0, 0, // Sub-chunk1 Size
                        1, 0, // Audio Format
                        wavHeader[0], wavHeader[1], // Num Channels
                        wavHeader[2], wavHeader[3], wavHeader[4], wavHeader[5], // Sample Rate
                        wavHeader[6], wavHeader[7], wavHeader[8], wavHeader[9], // Byte Rate
                        wavHeader[10], wavHeader[11], // Block Align
                        wavHeader[12], wavHeader[13], // Bits Per Sample
                        'd'.toByte(), 'a'.toByte(), 't'.toByte(), 'a'.toByte(), // Sub-chunk2 ID
                        0, 0, 0, 0 // // Sub-chunk2 Size
                )
        )
    }

    private fun isRecording() = viewModel.state == RECORDING
    private fun isPlaying() = viewModel.state == PLAYING

    companion object {
        const val INITIALIZED = -1
        const val PAUSE_PLAYING = 0
        const val PAUSE_RECORDING = 1
        const val PLAYING = 2
        const val RECORDING = 3
        const val STOP_PLAYING = 4
        const val STOP_RECORDING = 5
    }
}