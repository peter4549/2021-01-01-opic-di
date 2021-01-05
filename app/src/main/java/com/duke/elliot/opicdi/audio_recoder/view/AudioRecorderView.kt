package com.duke.elliot.opicdi.audio_recoder.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.DISPLAY_CHUNK_INTERVAL_MILLISECONDS
import com.duke.elliot.opicdi.audio_recoder.DISPLAY_TIMESTAMP_INTERVAL_MILLISECONDS
import com.duke.elliot.opicdi.util.toDateFormat
import java.util.*


class AudioRecorderView : View {

    enum class AlignTo(var value: Int) {
        CENTER(1),
        BOTTOM(2)
    }

    val timestampInterval = (DISPLAY_TIMESTAMP_INTERVAL_MILLISECONDS / DISPLAY_CHUNK_INTERVAL_MILLISECONDS).toInt()
    var timestampHorizontalScale = 0F
    var elapsedTime = 0L

    /** 보이는 거만 그리도록 변경필수. */
    private var visibleChunkCount = 0F
    private var visibleChunkCountHalf = 0F
    private var quarterVisibleChunkCount = 0F
    private var halfWidth = 0
    private var centerChunkIndex = 0
    private var pivot = 0
    private var recording = false

    /** Seek Bar */
    private var seekBar: SeekBar? = null
    var ignoreOnSeekBarChangeListenerInvoke = false

    /** Timer Text View */
    private var timerTextView: TextView? = null

    fun setTimerTextView(timerTextView: TextView) {
        this.timerTextView = timerTextView
    }

    /** Scrubber */
    private var scrubberPosition = 0F

    private val maxReportableAmp = 22760F // effective size,  max fft = 32760
    private val uninitialized = 0F
    var chunkAlignTo = AlignTo.CENTER

    /** Paint */
    private val chunkPaint = Paint()
    private val textPaint = TextPaint()
    private var textHeight = 0F
    private val scrubberPaint = Paint()
    private var lastUpdateTime = 0L

    private val gridPaint = android.graphics.Paint()

    private var lastChunkShift = 0F
    var reachedHalf = false
    private var displayedChunkLength = 0F

    private var usageWidth = 0F
    private var usageTimeWidth = 0F
    private var chunkHeights = ArrayList<Float>()
    private var chunkWidths = ArrayList<Float>()
    private var timeWidths = ArrayList<Float>()
    private var topBottomPadding = 6.toPx()
    private var halfChunkCount = 0F

    private var waveformShift = 0F
    private var screenShift = 0
    private var viewWidth = 0F
    private var isMeasured = false
    private var playProgressPx = -1
    private var prevScreenShift = 0
    private var startX = 0f
    private var startMargin = 0F

    /** Timestamp */
    private var timeStampInitMargin = 0

    var chunkSoftTransition = false
    var chunkColor = Color.RED
        set(value) {
            chunkPaint.color = value
            field = value
        }
    var chunkWidth = 2.toPx()
        set(value) {
            chunkPaint.strokeWidth = value
            field = value
        }
    var chunkSpace = 1.toPx()
    var chunkMaxHeight = uninitialized
    var chunkMinHeight = 3.toPx()  // recommended size > 10 dp
    var chunkRoundedCorners = false
        set(value) {
            if (value) {
                chunkPaint.strokeCap = Paint.Cap.ROUND
            } else {
                chunkPaint.strokeCap = Paint.Cap.BUTT
            }
            field = value
        }

    constructor(context: Context) : super(context) {
        init()
    }

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
        startMargin = viewWidth / 2
        halfWidth = width / 2


        screenShift = -playProgressPx
        waveformShift = (viewWidth / 2)
       //  prevScreenShift = screenShift

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                heightMeasureSpec
        )
    }

    fun recreate() {
        usageWidth = 0f
        chunkWidths.clear()
        chunkHeights.clear()
        invalidate()
    }

    fun update(fft: Int) {
        handleNewFFT(fft)
        invalidate() // call to the onDraw function
        lastUpdateTime = System.currentTimeMillis()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawChunks(canvas)
        if (reachedHalf) {
            scrubberPosition = viewWidth / 2
            canvas.drawLine(
                    scrubberPosition,
                    0F,
                    scrubberPosition,
                    measuredHeight.toFloat(),
                    scrubberPaint
            )
        }
    }

    private fun init() {
        chunkPaint.strokeWidth = chunkWidth
        chunkPaint.color = chunkColor
    }

    private fun init(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(
                attrs, R.styleable.AudioRecordView,
                0, 0
        ).apply {
            try {
                chunkSpace = getDimension(R.styleable.AudioRecordView_chunkSpace, chunkSpace)
                chunkMaxHeight =
                    getDimension(R.styleable.AudioRecordView_chunkMaxHeight, chunkMaxHeight)
                chunkMinHeight =
                    getDimension(R.styleable.AudioRecordView_chunkMinHeight, chunkMinHeight)
                chunkRoundedCorners =
                    getBoolean(R.styleable.AudioRecordView_chunkRoundedCorners, chunkRoundedCorners)
                chunkWidth = getDimension(R.styleable.AudioRecordView_chunkWidth, chunkWidth)
                chunkColor = getColor(R.styleable.AudioRecordView_chunkColor, chunkColor)
                chunkAlignTo =
                    when (getInt(R.styleable.AudioRecordView_chunkAlignTo, chunkAlignTo.ordinal)) {
                        AlignTo.BOTTOM.value -> AlignTo.BOTTOM
                        else -> AlignTo.CENTER
                    }

                chunkSoftTransition =
                    getBoolean(R.styleable.AudioRecordView_chunkSoftTransition, chunkSoftTransition)

                setWillNotDraw(false)
                chunkPaint.isAntiAlias = true

                /** Scrubber */
                scrubberPaint.isAntiAlias = false
                scrubberPaint.style = Paint.Style.STROKE
                scrubberPaint.strokeWidth = 2.toPx()
                scrubberPaint.color = ContextCompat.getColor(context, R.color.teal_200)

                /** Timestamp */
                textPaint.color = ContextCompat.getColor(context, R.color.white)
                textPaint.strokeWidth = 2.toPx()
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textHeight = context.resources.getDimension(R.dimen.text_size_small)
                textPaint.textSize = textHeight
                timestampHorizontalScale = timestampInterval * (chunkWidth + chunkSpace)

                /** Grid */
                gridPaint.color = ContextCompat.getColor(context, R.color.dark_gray)
                gridPaint.strokeWidth = 0.5F.toPx()

            } finally {
                recycle()
            }
        }

        setOnTouchListener { view, motionEvent ->
                when (motionEvent.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = motionEvent.x
                        reachedHalf = true
                        ignoreOnSeekBarChangeListenerInvoke = true
                    }
                    MotionEvent.ACTION_MOVE -> {

                        val chunkHorizontalScale = chunkWidth + chunkSpace
                        val maxChunkCount = (width / chunkHorizontalScale) / 5

                        val movedPx = (motionEvent.x - startX)
                        waveformShift += (motionEvent.x - startX) / 8//(motionEvent.x - startX).toInt() / 8

                        println("moved PX: $movedPx")
                        println("to Index.. : ${movedPx / chunkWidth}")
                        pivot -= (movedPx / chunkWidth).toInt() / 2 // 피벗 레인지 지정.

                        if (pivot < 0)
                            pivot = 0
                        else if (pivot > chunkHeights.size.dec())
                            pivot = chunkHeights.size.dec()

                        setSeekBarProgress(pivot)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        prevScreenShift = screenShift
                        performClick()
                        ignoreOnSeekBarChangeListenerInvoke = false
                    }
                }
            true
        }
    }

    private fun updateShifts(px: Int) {
        screenShift = px
        waveformShift = (screenShift + viewWidth / 2)
    }

    private fun handleNewFFT(fft: Int) {
        recording = true

        if (fft == 0)
            return

        val chunkHorizontalScale = chunkWidth + chunkSpace
        visibleChunkCountHalf = (viewWidth / chunkHorizontalScale) / 2
        visibleChunkCount = (viewWidth / chunkHorizontalScale)

        waveformShift = startMargin
        // TODO chunck 0에 마진을 미리 더해준다.

        if (chunkHeights.isNotEmpty() && chunkHeights.size > visibleChunkCountHalf) {
            waveformShift -= (chunkHorizontalScale * (chunkHeights.size - visibleChunkCountHalf))
            // waveformShift -= startMargin
            reachedHalf = true
        }

        println("WAV SHIFT: $waveformShift")
        println("heights size: ${chunkHeights.size}")
        println("result: ${chunkHorizontalScale * (chunkHeights.size - visibleChunkCountHalf)}")
        println("lastupdatetime: $lastUpdateTime")


        if (chunkWidths.isEmpty() || chunkWidths.size % (timestampInterval * 3) == 0) {
            for (i in 0..3) {
                timeWidths.add(timeWidths.size, usageTimeWidth)
                usageTimeWidth += (timestampHorizontalScale)
            }

            if (chunkWidths.isNotEmpty()) {
                println("CCCCCC: ${chunkWidths.last()}")
            }
        }

        usageWidth += chunkHorizontalScale
        chunkWidths.add(chunkWidths.size, usageWidth)

        elapsedTime += DISPLAY_CHUNK_INTERVAL_MILLISECONDS
        timerTextView?.text = elapsedTime.toDateFormat("mm:ss.SS")

        if (chunkMaxHeight == uninitialized) {
            chunkMaxHeight = height - (topBottomPadding * 2)
        } else if (chunkMaxHeight > height - (topBottomPadding * 2)) {
            chunkMaxHeight = height - (topBottomPadding * 2)
        }

        val verticalDrawScale = chunkMaxHeight - chunkMinHeight
        if (verticalDrawScale == 0F)
            return

        val point = maxReportableAmp / verticalDrawScale
        if (point == 0f)
            return

        var fftPoint = fft / point

        if (chunkSoftTransition && chunkHeights.isNotEmpty()) {
            val updateTimeInterval = System.currentTimeMillis() - lastUpdateTime
            val scaleFactor = calculateScaleFactor(updateTimeInterval)
            val prevFftWithoutAdditionalSize = chunkHeights.last() - chunkMinHeight
            fftPoint = fftPoint.softTransition(prevFftWithoutAdditionalSize, 2.2F, scaleFactor)
        }

        fftPoint += chunkMinHeight

        if (fftPoint > chunkMaxHeight) {
            fftPoint = chunkMaxHeight
        } else if (fftPoint < chunkMinHeight) {
            fftPoint = chunkMinHeight
        }

        chunkHeights.add(chunkHeights.size, fftPoint)
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
        newDraw2(canvas)
        drawTimestamp(canvas)
        /** Scrubber */
        canvas.drawLine(
                halfWidth.toFloat(),
                0F,
                halfWidth.toFloat(),
                measuredHeight.toFloat(),
                scrubberPaint
        )
    }

    private fun drawAlignCenter(canvas: Canvas) {
        val verticalCenter = height / 2

        if (!reachedHalf) {
            for (i in 0 until chunkHeights.size.dec()) {
                val chunkX = chunkWidths[i]
                val startY = verticalCenter - chunkHeights[i] / 2
                val stopY = verticalCenter + chunkHeights[i] / 2

                /** Chunk */
                canvas.drawLine(
                        chunkX + waveformShift,
                        startY,
                        chunkX + waveformShift,
                        stopY,
                        chunkPaint
                )
                if (!reachedHalf && i == chunkHeights.size - 2) {
                    lastChunkShift = chunkX + waveformShift
                    displayedChunkLength = lastChunkShift - startMargin

                    /** Scrubber */
                    scrubberPosition = chunkX + waveformShift
                    canvas.drawLine(
                            scrubberPosition,
                            0F,
                            scrubberPosition,
                            measuredHeight.toFloat(),
                            scrubberPaint
                    )
                }
            }
        } else {
            if (chunkHeights.size > halfChunkCount) {
                for (i in (chunkHeights.size - (halfChunkCount)).toInt() until chunkHeights.size.dec()) {
                    val chunkX = chunkWidths[i]
                    val startY = verticalCenter - chunkHeights[i] / 2
                    val stopY = verticalCenter + chunkHeights[i] / 2

                    /** Chunk */
                    canvas.drawLine(
                            chunkX + waveformShift,
                            startY,
                            chunkX + waveformShift,
                            stopY,
                            chunkPaint
                    )
                    if (!reachedHalf && i == chunkHeights.size - 2) {
                        lastChunkShift = chunkX + waveformShift
                        displayedChunkLength = lastChunkShift - startMargin

                        /** Scrubber */
                        scrubberPosition = chunkX + waveformShift
                        canvas.drawLine(
                                scrubberPosition,
                                0F,
                                scrubberPosition,
                                measuredHeight.toFloat(),
                                scrubberPaint
                        )
                    }
                }
            }
                else {
                for (i in (chunkHeights.size - (halfChunkCount / 2)).toInt() until chunkHeights.size.dec()) {
                    val chunkX = chunkWidths[i]
                    val startY = verticalCenter - chunkHeights[i] / 2
                    val stopY = verticalCenter + chunkHeights[i] / 2

                    /** Chunk */
                    canvas.drawLine(
                            chunkX + waveformShift,
                            startY,
                            chunkX + waveformShift,
                            stopY,
                            chunkPaint
                    )
                    if (!reachedHalf && i == chunkHeights.size - 2) {
                        lastChunkShift = chunkX + waveformShift
                        displayedChunkLength = lastChunkShift - startMargin

                        /** Scrubber */
                        scrubberPosition = chunkX + waveformShift
                        canvas.drawLine(
                                scrubberPosition,
                                0F,
                                scrubberPosition,
                                measuredHeight.toFloat(),
                                scrubberPaint
                        )
                    }
                }
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        /** Timestamp */
        for (i in 0 until timeWidths.size.dec()) {
            val timeWidth = timeWidths[i]

            canvas.drawText(
                    (i * DISPLAY_TIMESTAMP_INTERVAL_MILLISECONDS).toTimestampString(),
                    timeWidth + waveformShift,
                    textHeight,
                    textPaint)

            println("timewidth: $timeWidth")

            val inset = 6.toDp()

            canvas.drawLine(timeWidth + waveformShift,
                    height - inset, timeWidth + waveformShift,
                    inset, gridPaint)
        }
    }

    private fun newDraw2(canvas: Canvas) {
        val verticalCenter = height / 2

        var range = if (pivot > visibleChunkCountHalf)
            visibleChunkCountHalf.toInt()
        else
            pivot

        for (i in 1 until range.inc()) {
            val startX = halfWidth - chunkWidth * i
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
            range = if (chunkHeights.size - pivot > visibleChunkCountHalf)
                visibleChunkCountHalf.toInt()
            else
                chunkHeights.size - pivot

            for (i in 0 until range) {
                val startX = halfWidth + chunkWidth * i
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

    private fun drawTimestamp(canvas: Canvas) {
        var start = pivot - visibleChunkCount.toInt()

        if (start < 0)
            start = 0

        val end = pivot + visibleChunkCount.toInt()

        for (i in start..end) {
            if (i % timestampInterval == 0) {
                println("IIIIII: $i")
                println("IIIIII: ${i * DISPLAY_TIMESTAMP_INTERVAL_MILLISECONDS}")
                val x = if (i <= pivot)
                    halfWidth - (pivot - i) * chunkWidth
                else
                    halfWidth + (i - pivot) * chunkWidth
                canvas.drawText(
                        (i * DISPLAY_CHUNK_INTERVAL_MILLISECONDS).toTimestampString(),
                        x,
                        textHeight,
                        textPaint)

                val inset = 6.toDp()

                canvas.drawLine(x,
                        height - inset, x,
                        inset, gridPaint)
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

    fun setSeekBarProgress(shift: Int) {
        seekBar?.let {
            val pr = shift * it.max / chunkHeights.size.toFloat()
            it.progress = pr.toInt()
        }

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
    return this.toDateFormat("mm:ss")
}

fun Float.softTransition(compareWith: Float, allowedDiff: Float, scaleFactor: Float): Float {
    if (scaleFactor == 0f) return this //avoid from ArithmeticException (divide by zero)

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