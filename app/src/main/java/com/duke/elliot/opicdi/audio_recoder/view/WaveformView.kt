package com.duke.elliot.opicdi.audio_recoder.view

import android.content.Context
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
import com.duke.elliot.opicdi.util.isNotZero
import com.duke.elliot.opicdi.util.toDateFormat
import com.duke.elliot.opicdi.util.toPx
import java.util.*

class WaveformView : View {
    enum class Mode {
        RECORD,
        PLAY,
        PAUSE_RECORDING,
        PAUSE_PLAYING,
        STOP_PLAYING,
        STOP_RECORDING,
        DRAG_WHILE_PLAYING,
        OVERWRITE,
    }

    private var mode = Mode.RECORD

    private var seekBar: SeekBar? = null
    private var allowOnProgressChanged = true

    private var timerTextView: TextView? = null
    fun registerTimerTextView(timerTextView: TextView) {
        this.timerTextView = timerTextView
    }

    private var onTouchActionDownCallback: ((WaveformView) -> Unit)? = null
    private var allowOnTouchCallback = true
    fun setOnTouchActionDownCallback(onTouchActionDownCallback: (WaveformView) -> Unit) {
        this.onTouchActionDownCallback = onTouchActionDownCallback
    }

    private var onTouchActionUpCallback: ((WaveformView) -> Unit)? = null
    fun setOnTouchActionUpCallback(onTouchActionUpCallback: (WaveformView) -> Unit) {
        this.onTouchActionUpCallback = onTouchActionUpCallback
    }

    private var pivot = 0

    private var measured = false
    private var startX = 0F

    private var maximumVisiblePulseCount = 0F
    private var halfMaximumVisiblePulseCount = 0F

    private var viewWidth = 0F
    private var halfViewWidth = 0

    private val maximumReportableAmplitude = 22760F  // Effective size, maximum amplitude: 32760F
    private val uninitialized = 0F
    private var topBottomPadding = 8.toPx()

    private var amplitudes = ArrayList<Float>()

    private var overwrittenAmplitudes = ArrayList<Float>()
    private var overwriteStart = 0

    private val pulsePaint = Paint()
    private var maximumAmplitude = uninitialized
    private var minimumAmplitude = 2.toPx()
    private var pulseWidth = 0.5F.toPx()
        set(value) {
            pulsePaint.strokeWidth = value
            field = value
        }
    private var pulseSpacing = 0.toPx()
    private var pulseColor = Color.RED
        set(value) {
            pulsePaint.color = value
            field = value
        }
    private var pulseRoundedCorners = false
        set(value) {
            if (value)
                pulsePaint.strokeCap = Paint.Cap.ROUND
            else
                pulsePaint.strokeCap = Paint.Cap.BUTT
            field = value
        }
    private var pulseSmoothTransition = false
    private var pulseHorizontalScale = 0F
    private var overwrittenPulseColor = Color.YELLOW

    private val scrubberPaint = Paint()
    private var scrubberColor = Color.RED
    private var scrubberWidth = 2.toPx()

    private val timestampPaint = TextPaint()
    private val timestampBottomPadding = 8.toPx()
    private val timestampInterval = (TIMESTAMP_INTERVAL_MILLISECONDS / UPDATE_INTERVAL_MILLISECONDS).toInt()
    private var timestampTextColor = Color.WHITE
    private var timestampTextSize = context.resources.getDimension(R.dimen.text_size_small)
    private var textHeight = 0F

    private val timestampBackgroundPaint = Paint()
    private var timestampTextBackgroundColor = Color.BLACK
    private val rect = Rect()

    private val gridPaint = Paint()
    private var gridColor = ContextCompat.getColor(context, R.color.dark_gray)
    private var gridVisibility = true
    private var gridWidth = 1.toPx()

    private val subGridPaint = Paint()
    private var subGridColor = ContextCompat.getColor(context, R.color.gray)
    private var subGridCount = 3
    private var subGridVisibility = true
    private var subGridWidth = 1.toPx()

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
        if (!measured)
            measured = true
        // Reconcile the measured dimensions with the this view's constraints and
        // set the final measured viewWidth and height.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        viewWidth = width.toFloat()
        halfViewWidth = width / 2

        pulseHorizontalScale = pulseWidth + pulseSpacing
        maximumVisiblePulseCount = viewWidth / pulseHorizontalScale
        halfMaximumVisiblePulseCount = maximumVisiblePulseCount / 2

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                heightMeasureSpec
        )
    }

    fun recreate() {
        amplitudes.clear()
        overwrittenAmplitudes.clear()
        invalidate()
    }

    fun update(amplitude: Int, mode: Mode) {
        if (mode == Mode.OVERWRITE && mode != this.mode)
            overwriteStart = pivot

        this.mode = mode

        if (mode == Mode.RECORD)
            add(amplitude)
        else if (mode == Mode.OVERWRITE)
            overwrite(amplitude)
    }

    private fun add(amplitude: Int) {
        amplitudes.add(amplitudes.size, adjustAmplitude(amplitude))
        pivot = amplitudes.size.dec()
        updateSeekBar()
        invalidate()
    }

    private fun overwrite(amplitude: Int) {
        val adjustedAmplitude = adjustAmplitude(amplitude)

        if (pivot >= amplitudes.size.dec()) {
            amplitudes[pivot] = adjustedAmplitude
            amplitudes.add(amplitudes.size, adjustedAmplitude)
            pivot = amplitudes.size.dec()
        } else {
            amplitudes[pivot++] = adjustedAmplitude

            if (pivot > amplitudes.size.dec())
                pivot = amplitudes.size.dec()
        }

        overwrittenAmplitudes.add(overwrittenAmplitudes.size, adjustedAmplitude)
        updateSeekBar()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGridsAndTimestamps(canvas)
        drawAmplitudes(canvas)
        canvas.drawLine(
                halfViewWidth.toFloat(),
                0F,
                halfViewWidth.toFloat(),
                measuredHeight.toFloat(),
                scrubberPaint
        )
    }

    private fun init(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(
                attrs, R.styleable.WaveformView,
                0, 0
        ).apply {
            try {
                pulseSpacing = getDimension(R.styleable.WaveformView_pulseSpacing, pulseSpacing)
                maximumAmplitude =
                        getDimension(R.styleable.WaveformView_maximumAmplitude, maximumAmplitude)
                minimumAmplitude =
                        getDimension(R.styleable.WaveformView_minimumAmplitude, minimumAmplitude)
                pulseRoundedCorners =
                        getBoolean(R.styleable.WaveformView_pulseRoundedCorners, pulseRoundedCorners)
                pulseWidth = getDimension(R.styleable.WaveformView_pulseWidth, pulseWidth)
                pulseColor = getColor(R.styleable.WaveformView_pulseColor, pulseColor)
                pulseSmoothTransition =
                        getBoolean(R.styleable.WaveformView_pulseSmoothTransition, pulseSmoothTransition)

                setWillNotDraw(false)
                pulsePaint.isAntiAlias = true

                scrubberWidth = getDimension(R.styleable.WaveformView_scrubberWidth, scrubberWidth)
                scrubberColor = getColor(R.styleable.WaveformView_scrubberColor, scrubberColor)

                scrubberPaint.isAntiAlias = false
                scrubberPaint.style = Paint.Style.STROKE
                scrubberPaint.strokeWidth = scrubberWidth
                scrubberPaint.color = scrubberColor

                timestampTextBackgroundColor =
                        getColor(R.styleable.WaveformView_timestampTextBackgroundColor, timestampTextBackgroundColor)
                timestampTextColor = getColor(R.styleable.WaveformView_timestampTextColor, timestampTextColor)
                timestampTextSize = getDimension(R.styleable.WaveformView_timestampTextSize, timestampTextSize)

                timestampPaint.color = timestampTextColor
                timestampPaint.strokeWidth = 2.toPx()
                timestampPaint.textAlign = Paint.Align.CENTER
                // textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textHeight = timestampTextSize
                timestampPaint.textSize = textHeight
                timestampPaint.isAntiAlias = true

                timestampBackgroundPaint.style = Paint.Style.FILL
                timestampBackgroundPaint.color = timestampTextBackgroundColor

                gridVisibility = getBoolean(R.styleable.WaveformView_gridVisibility, gridVisibility)
                gridWidth = getDimension(R.styleable.WaveformView_gridWidth, gridWidth)
                gridColor = getColor(R.styleable.WaveformView_gridColor, gridColor)

                gridPaint.color = gridColor
                gridPaint.strokeWidth = gridWidth

                subGridVisibility = getBoolean(R.styleable.WaveformView_subGridVisibility, subGridVisibility)
                subGridWidth = getDimension(R.styleable.WaveformView_subGridWidth, subGridWidth)
                subGridColor = getColor(R.styleable.WaveformView_subGridColor, subGridColor)
                subGridCount = getInt(R.styleable.WaveformView_subGridCount, subGridCount)

                subGridPaint.color = subGridColor
                subGridPaint.strokeWidth = subGridWidth

                overwrittenPulseColor = getColor(R.styleable.WaveformView_overwrittenPulseColor, overwrittenPulseColor)

                pulseHorizontalScale = pulseWidth + pulseSpacing
            } finally {
                recycle()
            }
        }

        setOnTouchListener { _, motionEvent ->
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    startX = motionEvent.x
                    allowOnProgressChanged = false

                    if (allowOnTouchCallback)
                        onTouchActionDownCallback?.invoke(this)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (motionEvent.x - startX)
                    pivot -= (dx / pulseHorizontalScale).toInt() / 8 // 피벗 레인지 지정.

                    if (pivot < 0)
                        pivot = 0
                    else if (pivot > amplitudes.size.dec())
                        pivot = amplitudes.size.dec()

                    updateSeekBarProgress()
                    updateTimerText()
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    allowOnProgressChanged = true

                    if (allowOnTouchCallback)
                        onTouchActionUpCallback?.invoke(this)
                }
            }
            true
        }
    }

    private fun adjustAmplitude(amplitude: Int): Float {
        if (amplitude == 0)
            return minimumAmplitude

        if (maximumAmplitude == uninitialized)
            maximumAmplitude = height - topBottomPadding * 2
        else if (maximumAmplitude > height - (topBottomPadding * 2))
            maximumAmplitude = height - topBottomPadding * 2

        val verticalDrawScale = maximumAmplitude - minimumAmplitude
        if (verticalDrawScale == 0F)
            return minimumAmplitude

        val point = maximumReportableAmplitude / verticalDrawScale
        if (point == 0F)
            return minimumAmplitude

        var amplitudePoint = amplitude / point

        if (pulseSmoothTransition) {
            val scaleFactor = calculateScaleFactor()

            if (mode == Mode.OVERWRITE && overwrittenAmplitudes.isNotEmpty()) {
                val prevFftWithoutAdditionalSize = overwrittenAmplitudes[overwrittenAmplitudes.size.dec()] - minimumAmplitude
                amplitudePoint = amplitudePoint.smoothTransition(prevFftWithoutAdditionalSize, 2.2F, scaleFactor)
            } else if (amplitudes.isNotEmpty()) {
                val prevFftWithoutAdditionalSize = amplitudes[pivot] - minimumAmplitude
                amplitudePoint = amplitudePoint.smoothTransition(prevFftWithoutAdditionalSize, 2.2F, scaleFactor)
            }
        }

        amplitudePoint += minimumAmplitude

        if (amplitudePoint > maximumAmplitude)
            amplitudePoint = maximumAmplitude
        else if (amplitudePoint < minimumAmplitude)
            amplitudePoint = minimumAmplitude

        return amplitudePoint
    }

    private fun calculateScaleFactor(): Float {
        return when (UPDATE_INTERVAL_MILLISECONDS) {
            in 0..50 -> 1.6F
            in 50..100 -> 2.2F
            in 100..150 -> 2.8F
            in 150..200 -> 3.4F
            in 200..250 -> 4.2F
            in 250..500 -> 4.8F
            else -> 5.4F
        }
    }

    private fun drawAmplitudes(canvas: Canvas) {
        if (amplitudes.isEmpty())
            return

        val verticalCenter = (height + textHeight) / 2

        var range = if (pivot > halfMaximumVisiblePulseCount)
            halfMaximumVisiblePulseCount.toInt()
        else
            pivot

        for (i in 1 until range.inc()) {
            val index = pivot - i
            val startX = halfViewWidth - pulseHorizontalScale * i
            val startY = verticalCenter - amplitudes[index] / 2
            val stopY = verticalCenter + amplitudes[index] / 2

            if (mode == Mode.OVERWRITE && index >= overwriteStart)
                pulsePaint.color = overwrittenPulseColor
            else
                pulsePaint.color = pulseColor

            canvas.drawLine(
                    startX,
                    startY,
                    startX,
                    stopY,
                    pulsePaint)
        }

        if (pivot < amplitudes.size.dec()) {
            pulsePaint.color = pulseColor

            range = if (amplitudes.size - pivot > halfMaximumVisiblePulseCount)
                halfMaximumVisiblePulseCount.toInt()
            else
                amplitudes.size - pivot

            for (i in 0 until range) {
                val index = pivot + i
                if (index > amplitudes.size.dec())
                    break

                val startX = halfViewWidth + pulseHorizontalScale * i
                val startY = verticalCenter - amplitudes[index] / 2
                val stopY = verticalCenter + amplitudes[index] / 2
                canvas.drawLine(
                        startX,
                        startY,
                        startX,
                        stopY,
                        pulsePaint)
            }
        }
    }

    private fun drawGridsAndTimestamps(canvas: Canvas) {
        drawTimestampBackgroundColor(canvas)

        var start = pivot - maximumVisiblePulseCount.toInt()
        if (start < 0)
            start = 0
        val end = pivot + maximumVisiblePulseCount.toInt()
        val subGridInterval = timestampInterval / subGridCount.inc() * pulseHorizontalScale

        for (i in start..end) {
            if (i % timestampInterval == 0) {
                val x = if (i <= pivot)
                    halfViewWidth - (pivot - i) * pulseHorizontalScale
                else
                    halfViewWidth + (i - pivot) * pulseHorizontalScale
                canvas.drawText(
                        (i * UPDATE_INTERVAL_MILLISECONDS).toTimestampString(),
                        x,
                        textHeight,
                        timestampPaint
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

    fun registerSeekBar(seekBar: SeekBar) {
        this.seekBar = seekBar
        this.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (allowOnProgressChanged) {
                        seekBar.let {
                            it.updatePivot()
                            if (pivot >= amplitudes.size)
                                pivot = amplitudes.size.dec()
                            updateTimerText()
                            invalidate()
                        }

                        if (allowOnTouchCallback)
                            onTouchActionDownCallback?.invoke(this@WaveformView)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (allowOnTouchCallback)
                    onTouchActionUpCallback?.invoke(this@WaveformView)
            }
        })
    }

    private fun SeekBar.updatePivot() {
        if (this.max.isNotZero())
            pivot = (this.progress * amplitudes.size / this.max.toFloat()).toInt()
    }

    private fun updateSeekBar() {
        seekBar?.let {
            it.max = amplitudes.size
            if (amplitudes.isNotEmpty())
                it.progress = pivot
        }
    }

    private fun updateSeekBarProgress() {
        seekBar?.let {
            if (amplitudes.isNotEmpty())
                it.progress = pivot
        }
    }

    fun setMode(mode: Mode) {
        this.mode = mode

        when(mode) {
            Mode.PAUSE_PLAYING -> {
                allowOnProgressChanged = true
                allowOnTouchCallback = false
                updateSeekBar()
            }
            Mode.PAUSE_RECORDING -> {
                allowOnProgressChanged = true
                overwriteStart = 0
                overwrittenAmplitudes.clear()
                updateSeekBar()
            }
            Mode.PLAY -> {
                allowOnTouchCallback = true
                if (pivot >= amplitudes.size.dec())
                    pivot = 0
            }
            Mode.STOP_PLAYING -> {
                allowOnProgressChanged = true
                allowOnTouchCallback = false
                updateSeekBar()
            }
            Mode.STOP_RECORDING -> {
                allowOnProgressChanged = true
                overwriteStart = 0
                overwrittenAmplitudes.clear()
                updateSeekBar()
            }
            Mode.DRAG_WHILE_PLAYING -> {
                allowOnTouchCallback = true
            }
            else -> {
                allowOnTouchCallback = false
            }
        }
    }

    fun progressRate(): Float {
        return if (pivot == 0)
            0F
        else
            pivot / amplitudes.count().toFloat()
    }

    fun shiftPivot(shift: Int) {
        pivot += shift
        if (pivot < 0)
            pivot = 0
        else if (pivot > amplitudes.size.dec())
            pivot = amplitudes.size.dec()

        updateSeekBar()
        invalidate()
    }

    private fun updateTimerText() {
        timerTextView?.text = (pivot * UPDATE_INTERVAL_MILLISECONDS).toDateFormat(TIMER_PATTERN)
    }

    fun isPivotAtEnd() = pivot >= amplitudes.size.dec()

    private fun drawTimestampBackgroundColor(canvas: Canvas) {
        rect.set(0, 0, width, (textHeight + timestampBottomPadding).toInt())
        canvas.drawRect(rect, timestampBackgroundPaint)
    }

    fun time() = pivot * UPDATE_INTERVAL_MILLISECONDS

    private fun Long.toTimestampString(): String {
        return this.toDateFormat(TIMESTAMP_PATTERN)
    }

    private fun Float.smoothTransition(compareWith: Float, allowedDiff: Float, scaleFactor: Float): Float {
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
}
