package com.duke.elliot.opicdi.audio_recoder.view

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.*
import com.duke.elliot.opicdi.util.progressRate
import com.duke.elliot.opicdi.util.toDateFormat
import com.github.piasy.rxandroidaudio.StreamAudioPlayer
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

class AudioRecorderView : View {

    private val timestampInterval = (DISPLAY_TIMESTAMP_INTERVAL_MILLISECONDS / DISPLAY_CHUNK_INTERVAL_MILLISECONDS).toInt()
    var elapsedTime = 0L

    private var visibleChunkCount = 0F
    private var halfVisibleChunkCount = 0F
    private var halfWidth = 0
    private var pivot = 0
    private var recording = false
    var halfReached = false

    /** Seek Bar */
    private var seekBar: SeekBar? = null
    var ignoreOnSeekBarChangeListenerInvoke = false

    /** Timer Text View */
    private var timerTextView: TextView? = null

    fun registerTimerTextView(timerTextView: TextView) {
        this.timerTextView = timerTextView
    }

    private val maxReportableAmplitude = 22760F  // Effective size, maximum amplitude: 32760F
    private val uninitialized = 0F
    private var topBottomPadding = 8.toPx()
    private var lastUpdateTime = 0L
    private var chunkHeights = ArrayList<Float>()
    private var viewWidth = 0F
    private var isMeasured = false
    private var startX = 0F

    /** Paint */
    private val chunkPaint = Paint()
    private val scrubberPaint = Paint()
    private val textPaint = TextPaint()
    private val timestampBackgroundPaint = Paint()
    private var textHeight = 0F
    private val rect = Rect()
    private val timestampBottomPadding = 4.toPx()
    private val gridPaint = Paint()
    private val subGridPaint = Paint()

    private var chunkWidth = 0.5F.toPx()
        set(value) {
            chunkPaint.strokeWidth = value
            field = value
        }
    private var chunkMaxHeight = uninitialized
    private var chunkMinHeight = 4.toPx()
    private var chunkSpace = 0.toPx()
    private var chunkColor = Color.RED
        set(value) {
            chunkPaint.color = value
            field = value
        }
    private var chunkRoundedCorners = false
        set(value) {
            if (value)
                chunkPaint.strokeCap = Paint.Cap.ROUND
            else
                chunkPaint.strokeCap = Paint.Cap.BUTT
            field = value
        }
    private var chunkSoftTransition = false
    private var chunkHorizontalScale = 0F

    private var scrubberWidth = 2.toPx()
    private var scrubberColor = Color.RED

    private var timestampTextBackgroundColor = Color.BLACK
    private var timestampTextColor = Color.WHITE
    private var timestampTextSize = context.resources.getDimension(R.dimen.text_size_smallest)

    private var gridVisibility = true
    private var gridWidth = 1.toPx()
    private var gridColor = ContextCompat.getColor(context, R.color.dark_gray)

    private var subGridVisibility = true
    private var subGridWidth = 1.toPx()
    private var subGridColor = ContextCompat.getColor(context, R.color.gray)
    private var subGridCount = 3

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
            context,
            attrs,
            defStyleAttr
    ) {
        init(attrs)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!isMeasured)
            isMeasured = true
        // Reconcile the measured dimensions with the this view's constraints and
        // set the final measured viewWidth and height.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        viewWidth = width.toFloat()
        halfWidth = width / 2

        chunkHorizontalScale = chunkWidth + chunkSpace

        halfVisibleChunkCount = (viewWidth / chunkHorizontalScale) / 2
        visibleChunkCount = (viewWidth / chunkHorizontalScale)

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                heightMeasureSpec
        )
    }

    fun recreate() {
        chunkHeights.clear()
        invalidate()
    }

    fun update(amplitude: Int) {
        handleNewAmplitude(amplitude)
        invalidate()
        lastUpdateTime = System.currentTimeMillis()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawChunks(canvas)
    }

    private fun init(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(
                attrs, R.styleable.AudioRecordView,
                0, 0
        ).apply {
            try {
                /** Chunk */
                chunkSpace = getDimension(R.styleable.AudioRecordView_chunkSpace, chunkSpace)
                chunkMaxHeight =
                    getDimension(R.styleable.AudioRecordView_chunkMaxHeight, chunkMaxHeight)
                chunkMinHeight =
                    getDimension(R.styleable.AudioRecordView_chunkMinHeight, chunkMinHeight)
                chunkRoundedCorners =
                    getBoolean(R.styleable.AudioRecordView_chunkRoundedCorners, chunkRoundedCorners)
                chunkWidth = getDimension(R.styleable.AudioRecordView_chunkWidth, chunkWidth)
                chunkColor = getColor(R.styleable.AudioRecordView_chunkColor, chunkColor)
                chunkSoftTransition =
                    getBoolean(R.styleable.AudioRecordView_chunkSoftTransition, chunkSoftTransition)

                setWillNotDraw(false)
                chunkPaint.isAntiAlias = true

                /** Scrubber */
                scrubberWidth = getDimension(R.styleable.AudioRecordView_scrubberWidth, scrubberWidth)
                scrubberColor = getColor(R.styleable.AudioRecordView_scrubberColor, scrubberColor)

                scrubberPaint.isAntiAlias = false
                scrubberPaint.style = Paint.Style.STROKE
                scrubberPaint.strokeWidth = scrubberWidth
                scrubberPaint.color = scrubberColor

                /** Timestamp */
                timestampTextBackgroundColor =
                        getColor(R.styleable.AudioRecordView_timestampTextBackgroundColor, timestampTextBackgroundColor)
                timestampTextColor = getColor(R.styleable.AudioRecordView_timestampTextColor, timestampTextColor)
                timestampTextSize = getDimension(R.styleable.AudioRecordView_timestampTextSize, timestampTextSize)

                textPaint.color = timestampTextColor
                textPaint.strokeWidth = 2.toPx()
                textPaint.textAlign = Paint.Align.CENTER
                // textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textHeight = timestampTextSize
                textPaint.textSize = textHeight
                textPaint.isAntiAlias = true

                timestampBackgroundPaint.style = Paint.Style.FILL
                timestampBackgroundPaint.color = timestampTextBackgroundColor

                /** Grid */
                gridVisibility = getBoolean(R.styleable.AudioRecordView_gridVisibility, gridVisibility)
                gridWidth = getDimension(R.styleable.AudioRecordView_gridWidth, gridWidth)
                gridColor = getColor(R.styleable.AudioRecordView_gridColor, gridColor)

                gridPaint.color = gridColor
                gridPaint.strokeWidth = gridWidth

                subGridVisibility = getBoolean(R.styleable.AudioRecordView_subGridVisibility, subGridVisibility)
                subGridWidth = getDimension(R.styleable.AudioRecordView_subGridWidth, subGridWidth)
                subGridColor = getColor(R.styleable.AudioRecordView_subGridColor, subGridColor)
                subGridCount = getInt(R.styleable.AudioRecordView_subGridCount, subGridCount)

                subGridPaint.color = subGridColor
                subGridPaint.strokeWidth = subGridWidth

                chunkHorizontalScale = chunkWidth + chunkSpace
            } finally {
                recycle()
            }
        }

        setOnTouchListener { _, motionEvent ->
                when (motionEvent.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = motionEvent.x
                        halfReached = true
                        ignoreOnSeekBarChangeListenerInvoke = true
                    }
                    MotionEvent.ACTION_MOVE -> {

                        val movedPx = (motionEvent.x - startX)

                        println("moved PX: $movedPx")
                        println("to Index.. : ${movedPx / chunkWidth}")
                        pivot -= (movedPx / chunkHorizontalScale).toInt() / 8 // 피벗 레인지 지정.

                        if (pivot < 0)
                            pivot = 0
                        else if (pivot > chunkHeights.size.dec())
                            pivot = chunkHeights.size.dec()

                        setSeekBarProgress(pivot)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        performClick()
                        ignoreOnSeekBarChangeListenerInvoke = false
                    }
                }
            true
        }
    }

    private fun handleNewAmplitude(amplitude: Int) {
        recording = true

        if (amplitude == 0)
            return

        if (chunkHeights.isNotEmpty() && chunkHeights.size > halfVisibleChunkCount)
            halfReached = true

        elapsedTime += DISPLAY_CHUNK_INTERVAL_MILLISECONDS
        timerTextView?.text = elapsedTime.toDateFormat(TIMER_PATTERN)

        if (chunkMaxHeight == uninitialized)
            chunkMaxHeight = height - topBottomPadding * 2
        else if (chunkMaxHeight > height - (topBottomPadding * 2))
            chunkMaxHeight = height - topBottomPadding * 2

        val verticalDrawScale = chunkMaxHeight - chunkMinHeight
        if (verticalDrawScale == 0F)
            return

        val point = maxReportableAmplitude / verticalDrawScale
        if (point == 0F)
            return

        var amplitudePoint = amplitude / point

        if (chunkSoftTransition && chunkHeights.isNotEmpty()) {
            val updateTimeInterval = System.currentTimeMillis() - lastUpdateTime
            val scaleFactor = calculateScaleFactor(updateTimeInterval)
            val prevFftWithoutAdditionalSize = chunkHeights.last() - chunkMinHeight
            amplitudePoint = amplitudePoint.softTransition(prevFftWithoutAdditionalSize, 2.2F, scaleFactor)
        }

        amplitudePoint += chunkMinHeight

        if (amplitudePoint > chunkMaxHeight)
            amplitudePoint = chunkMaxHeight
        else if (amplitudePoint < chunkMinHeight)
            amplitudePoint = chunkMinHeight

        chunkHeights.add(chunkHeights.size, amplitudePoint)
        pivot = chunkHeights.size.dec()
    }

    private fun calculateScaleFactor(updateTimeInterval: Long): Float {
        return when (updateTimeInterval) {
            in 0..50 -> 1.6F
            in 50..100 -> 2.2F
            in 100..150 -> 2.8F
            in 100..150 -> 3.4F
            in 150..200 -> 4.2F
            in 200..500 -> 4.8F
            else -> 5.4F
        }
    }

    private fun drawChunks(canvas: Canvas) {
        drawTimestampAndGrid(canvas)
        newDraw2(canvas)
        /** Scrubber */
        canvas.drawLine(
                halfWidth.toFloat(),
                0F,
                halfWidth.toFloat(),
                measuredHeight.toFloat(),
                scrubberPaint
        )
    }

    private fun newDraw2(canvas: Canvas) {
        val verticalCenter = (height + textHeight) / 2
        android.R.color.widget_edittext_dark

        var range = if (pivot > halfVisibleChunkCount)
            halfVisibleChunkCount.toInt()
        else
            pivot

        for (i in 1 until range.inc()) {
            val startX = halfWidth - chunkHorizontalScale * i
            val startY = verticalCenter - chunkHeights[pivot - i] / 2
            val stopY = verticalCenter + chunkHeights[pivot - i] / 2
            canvas.drawLine(
                    startX,
                    startY,
                    startX,
                    stopY,
                    chunkPaint)
        }

        if (!recording) {
            range = if (chunkHeights.size - pivot > halfVisibleChunkCount)
                halfVisibleChunkCount.toInt()
            else
                chunkHeights.size - pivot

            for (i in 0 until range) {
                val startX = halfWidth + chunkHorizontalScale * i
                val startY = verticalCenter - chunkHeights[pivot + i] / 2
                val stopY = verticalCenter + chunkHeights[pivot + i] / 2
                canvas.drawLine(
                        startX,
                        startY,
                        startX,
                        stopY,
                        chunkPaint)
            }
        }

        recording = false
    }

    private fun drawTimestampAndGrid(canvas: Canvas) {
        drawTimestampBackgroundColor(canvas)

        var start = pivot - visibleChunkCount.toInt()
        if (start < 0)
            start = 0
        val end = pivot + visibleChunkCount.toInt()
        val subGridInterval = timestampInterval / subGridCount.inc() * chunkHorizontalScale

        for (i in start..end) {
            if (i % timestampInterval == 0) {
                val x = if (i <= pivot)
                    halfWidth - (pivot - i) * chunkHorizontalScale
                else
                    halfWidth + (i - pivot) * chunkHorizontalScale
                canvas.drawText(
                        (i * DISPLAY_CHUNK_INTERVAL_MILLISECONDS).toTimestampString(),
                        x,
                        textHeight,
                        textPaint
                )

                // Grid
                if (gridVisibility) {
                    canvas.drawLine(
                            x,
                            height / 4F,
                            x,
                            textHeight + timestampBottomPadding,
                            gridPaint
                    )

                    if (subGridVisibility) {
                        for (j in 1..subGridCount) {
                            canvas.drawLine(
                                    x + j * subGridInterval,
                                    height / 5F,
                                    x + j * subGridInterval,
                                    textHeight + timestampBottomPadding,
                                    subGridPaint
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 특정위치로 이동시키는 함수. 이동거리를 계산해야함.
     * 시크바에 매핑되는 위치만큼 shift를 조작해야한다.
     *
     */
    fun moveAccordingToProgress(progress: Int, maxProgress: Int) {
        val shift = progress * (chunkHeights.size / maxProgress.toFloat())
        pivot = shift.toInt()
        invalidate()
    }

    fun registerSeekBar(seekBar: SeekBar) {
        this.seekBar = seekBar
    }

    private fun setSeekBarProgress(dx: Int) {
        seekBar?.let {
            val progress = dx * it.max / chunkHeights.size.toFloat()
            it.progress = progress.toInt()
        }
    }

    private fun drawTimestampBackgroundColor(canvas: Canvas) {
        rect.set(0, 0, width, (textHeight + 4.toPx()).toInt())
        canvas.drawRect(rect, timestampBackgroundPaint)
    }
}

fun Int.toPx(): Float {
    return this * Resources.getSystem().displayMetrics.density
}

fun Float.toPx(): Float {
    return this * Resources.getSystem().displayMetrics.density
}

fun Int.toDp(): Float {
    return this / Resources.getSystem().displayMetrics.density
}

fun Float.toDp(): Float {
    return this / Resources.getSystem().displayMetrics.density
}

fun Long.toTimestampString(): String {
    return this.toDateFormat(TIMESTAMP_PATTERN)
}

fun Float.softTransition(compareWith: Float, allowedDiff: Float, scaleFactor: Float): Float {
    if (scaleFactor == 0F)
        return this

    var result = this
    if (compareWith > this) {
        if (compareWith / this > allowedDiff) {
            val diff = this.coerceAtLeast(compareWith) - this.coerceAtMost(compareWith)
            result += diff / scaleFactor
        }
    } else if (this > compareWith) {
        if (this / compareWith > allowedDiff) {
            val diff = this.coerceAtLeast(compareWith) - this.coerceAtMost(compareWith)
            result -= diff / scaleFactor
        }
    }

    return result
}