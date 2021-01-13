package com.duke.elliot.opicdi.audio_recoder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.view.WaveformView
import com.duke.elliot.opicdi.base.BaseActivity
import com.duke.elliot.opicdi.databinding.ActivityAudioRecorderBinding
import com.duke.elliot.opicdi.util.*
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException
import com.github.piasy.rxandroidaudio.StreamAudioPlayer
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
            when(viewModel.state) {
                PAUSE_PLAYING -> stopPlaying()
                PAUSE_RECORDING -> stopRecording()
                PLAY -> stopPlaying()
                RECORD -> stopRecording()
            }
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
                binding.waveformView.isEnabled = true
                binding.playPause.visibility = View.GONE
                binding.stop.visibility = View.GONE
            }
            PAUSE_PLAYING -> {
                binding.playPause.scale(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.recordPause.isEnabled = true
            }
            PAUSE_RECORDING -> {
                binding.recordPause.scale(0.5F, 100L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_fiber_manual_record_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (!binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.waveformView.isEnabled = true
                binding.playPause.isEnabled = true
            }
            PLAY -> {
                binding.playPause.scale(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.VISIBLE
                binding.recordPause.isEnabled = false
            }
            RECORD -> {
                binding.playPause.scale(1F, 100L)
                binding.stop.scale(1F, 100L)
                binding.recordPause.scale(0.5F, 100L) {
                    binding.recordPause.setBackgroundResource(R.drawable.ic_round_pause_24)
                    binding.recordPause.setImageResource(android.R.color.transparent)
                    binding.recordPause.scale(1F, 100L)
                }

                if (binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.GONE
                binding.waveformView.isEnabled = false
                binding.playPause.isEnabled = false
            }
            STOP_PLAYING -> {
                binding.playPause.scale(0.5F, 100L) {
                    binding.playPause.setBackgroundResource(R.drawable.ic_round_play_arrow_24)
                    binding.playPause.setImageResource(android.R.color.transparent)
                    binding.playPause.scale(1F, 100L)
                }

                if (!binding.seekBarContainer.isVisible)
                    binding.seekBarContainer.visibility = View.VISIBLE
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
            STOP_RECORDING -> startPlaying()
        }
    }

    private fun onRecord() {
        when (viewModel.state) {
            INITIALIZED -> startRecording()
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
        binding.waveformView.updateState(WaveformView.State.PLAY)
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
                            updateTimerText()
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
                        if (viewModel.state == PLAY) {
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
        binding.waveformView.updateState(WaveformView.State.STOP_PLAYING)
        audioFile?.close()
        audioFile = null
        player.release()
    }

    private fun startRecording() {
        updateState(RECORD)
        binding.waveformView.updateState(WaveformView.State.RECORD)
        audioFile = RandomAccessFile(viewModel.audioFilePath, "rw")
        writeWavHeader(channels.toShort(), sampleRate, bitDepth.toShort())
        recorder.start(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, object : AudioRecorder.AudioDataCallback {
                override fun onAudioDataRecord(data: ByteArray, size: Int) {
                    try {
                        audioFile?.write(data, 0, data.size)
                        coroutineScope.launch {
                            updateWaveformView(data)
                            updateTimerText()
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
        updateState(RECORD)
        binding.waveformView.updateState(WaveformView.State.OVERWRITE)
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
                            updateTimerText()
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
        updateWavHeader(File(viewModel.audioFilePath))
        convertToM4a()
        audioFile?.close()
        audioFile = null
        recorder.stop()
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
                override fun onPermissionsChecked (report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted())
                        Timber.d("All permissions are granted.")
                    else
                        finish()
                }

                override fun onPermissionRationaleShouldBeShown (
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
    }

    private fun initSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekBar.updatePivot()
                    if (binding.waveformView.getMode() == WaveformView.State.PLAY)
                        dragWhilePlaying()
                }

                updateTimerText()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (binding.waveformView.getMode() == WaveformView.State.DRAG_WHILE_PLAYING) {
                    if (!binding.waveformView.isPivotAtEnd())
                        resumePlaying()
                    else
                        stopPlaying()
                }
            }
        })
    }

    private fun updateTimerText() {
        val time = binding.waveformView.elapsedTime()
        binding.timer.text = time.toDateFormat(TIMER_PATTERN)
        binding.elapsedTime.text = time.toDateFormat(TIMESTAMP_PATTERN)
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
            PLAY -> pausePlaying()
            RECORD -> pauseRecording()
        }
        // showAudioFileNameInputDialog()
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        showToast("this not called?")
        showRecordingNotification()
    }

    private fun showSaveConfirmationDialog() {

    }

    private fun showRecordingNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel(notificationManager, CHANNEL_ID, CHANNEL_NAME)

        val remoteViewsSmall = RemoteViews(packageName, R.layout.layout_recording_notification)
        remoteViewsSmall.setOnClickPendingIntent(
            R.id.btn_recording_stop1, getPendingSelfIntent(
                applicationContext,
                ACTION_STOP_RECORDING
            )
        )
        remoteViewsSmall.setOnClickPendingIntent(
            R.id.btn_recording_pause, getPendingSelfIntent(
                applicationContext,
                ACTION_PAUSE_RECORDING
            )
        )
        remoteViewsSmall.setTextViewText(R.id.txt_recording_progress, "test")
        remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", Color.BLACK)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        builder.setWhen(System.currentTimeMillis())
        builder.setSmallIcon(R.drawable.ic_round_play_arrow_24)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builder.priority = NotificationManager.IMPORTANCE_MAX
        else
            builder.priority = Notification.PRIORITY_MAX

        builder.setContentIntent(createContentIntent())
        builder.setCustomContentView(remoteViewsSmall)
        builder.setOnlyAlertOnce(true)
        builder.setDefaults(0)
        builder.setSound(null)

        notificationManager.notify(0, builder.build())
    }

    private fun createContentIntent(): PendingIntent? {
        val intent = Intent(applicationContext, AudioRecorderActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
        return PendingIntent.getActivity(applicationContext, 0, intent, 0)
    }

    @Suppress("SameParameterValue")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        id: String,
        name: String
    ): String? {
        notificationManager.getNotificationChannel(id)?.let {
            val notificationChannel = NotificationChannel(
                id,
                name,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationChannel.setSound(null, null)
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)
            return id
        } ?: return null
    }

    private fun convertToM4a() {
        @Suppress("SpellCheckingInspection")
        val ffmpeg = FFmpeg.getInstance(this)
        try {
            // TODO: Change file name.
            val audioFilePath = viewModel.audioFilePath.substringBeforeLast(".")
            val m4aAudioFilePath = "$audioFilePath.m4a"
            val cmd = arrayOf("-i", viewModel.audioFilePath, m4aAudioFilePath)
            ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
                override fun onStart() {  }
                override fun onProgress(message: String) {  }
                override fun onFailure(message: String) { Timber.e("Failed to convert to m4a.: $message") }
                override fun onSuccess(message: String) {
                    viewModel.deleteFile(viewModel.audioFilePath)
                    if (viewModel.moveAudioFileToExternalStorage(m4aAudioFilePath)) {
                        Timber.d("")
                        showToast("외부저장소이동성성공공.")
                    } else
                        showToast("외부저장소이동실패.")
                }
                override fun onFinish() {  }
            })
        } catch (e: FFmpegCommandAlreadyRunningException) {
            Timber.e(e)
        }
    }

    companion object {
        const val INITIALIZED = -1
        const val PAUSE_PLAYING = 0
        const val PAUSE_RECORDING = 1
        const val PLAY = 2
        const val RECORD = 3
        const val STOP_PLAYING = 4
        const val STOP_RECORDING = 5

        const val ACTION_STOP_RECORDING = "com.duke.elliot.opicdi.audio_recoder.action_stop_recording"
        const val ACTION_PAUSE_RECORDING = "com.duke.elliot.opicdi.audio_recoder.action_pause_recording"

        const val RECORDING_NOTIFICATION_ID = 224

        private const val CHANNEL_ID = "default"
        private const val CHANNEL_NAME = "com.duke.elliot.opicdi.audio_recoder.channel_name"
    }


    private fun getPendingSelfIntent(context: Context?, action: String?): PendingIntent {
        val intent = Intent(context, RecordingReceiver::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(context, 10, intent, 0)
    }

    override fun onTouchActionDown() {
        dragWhilePlaying()
    }

    override fun onTouchActionMove() {
        updateTimerText()
        binding.seekBar.updateProgress()
    }

    override fun onTouchActionUp() {
        if (!binding.waveformView.isPivotAtEnd())
            resumePlaying()
        else
            stopPlaying()
    }

    class RecordingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

        }
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
}