package com.duke.elliot.opicdi.audio_recoder.view

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.*
import com.duke.elliot.opicdi.util.toDateFormat
import com.duke.elliot.opicdi.util.toPx
import java.util.*

class WaveformView : View {
    enum class State {
        INITIALIZED,
        PAUSE_PLAYING,
        PAUSE_RECORDING,
        PLAY,
        RECORD,
        STOP_PLAYING,
        STOP_RECORDING,
        DRAG_WHILE_PLAYING,
        OVERWRITE,
    }

    private var state = State.INITIALIZED

    private var allowDragWhilePlaying = false

    private var onTouchListener: OnTouchListener? = null
    fun setOnTouchListener(onTouchListener: OnTouchListener) {
        this.onTouchListener = onTouchListener
    }
    interface OnTouchListener {
        fun onTouchActionDown()
        fun onTouchActionMove()
        fun onTouchActionUp()
    }

    private var pivot = 0

    private var measured = false
    private var startX = 0F
    private var previousDx = 0F

    private var maxVisiblePulseCount = 0F
    private var halfMaxVisiblePulseCount = 0F

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
    private val timestampInterval = (TIMESTAMP_INTERVAL_MILLISECONDS / VISUALIZER_UPDATE_INTERVAL_MILLISECONDS).toInt()
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
        maxVisiblePulseCount = viewWidth / pulseHorizontalScale
        halfMaxVisiblePulseCount = maxVisiblePulseCount / 2

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

    fun update(amplitude: Int) {
        if (state == State.RECORD)
            add(amplitude)
        else if (state == State.OVERWRITE)
            overwrite(amplitude)
    }

    private fun add(amplitude: Int) {
        amplitudes.add(amplitudes.size, adjustAmplitude(amplitude))
        pivot = amplitudes.size.dec()
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

        setOnTouchListener { _, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    previousDx = 0F

                    if (allowDragWhilePlaying)
                        onTouchListener?.onTouchActionDown()
                }
                MotionEvent.ACTION_MOVE -> {
                    var dx = event.x - startX
                    if (previousDx == 0F)
                        previousDx = dx
                    else {
                        val t = dx
                        dx -= previousDx
                        previousDx = t
                    }

                    val shift = -(dx / pulseHorizontalScale).toInt()
                    shiftPivot(shift)
                    onTouchListener?.onTouchActionMove()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    if (allowDragWhilePlaying)
                        onTouchListener?.onTouchActionUp()
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

            if (state == State.OVERWRITE && overwrittenAmplitudes.isNotEmpty()) {
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
        return when (VISUALIZER_UPDATE_INTERVAL_MILLISECONDS) {
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

        var range = if (pivot > halfMaxVisiblePulseCount)
            halfMaxVisiblePulseCount.toInt()
        else
            pivot

        for (i in 1 until range.inc()) {
            val index = pivot - i
            val startX = halfViewWidth - pulseHorizontalScale * i
            val startY = verticalCenter - amplitudes[index] / 2
            val stopY = verticalCenter + amplitudes[index] / 2

            if (state == State.OVERWRITE)
                println("WHY???????????")
            if (state == State.OVERWRITE && index > overwriteStart)
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

            range = if (amplitudes.size - pivot > halfMaxVisiblePulseCount)
                halfMaxVisiblePulseCount.toInt()
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

        var start = pivot - maxVisiblePulseCount.toInt()
        if (start < 0)
            start = 0
        val end = pivot + maxVisiblePulseCount.toInt()
        val subGridInterval = timestampInterval / subGridCount.inc() * pulseHorizontalScale

        for (i in start..end) {
            if (i % timestampInterval == 0) {
                val x = if (i <= pivot)
                    halfViewWidth - (pivot - i) * pulseHorizontalScale
                else
                    halfViewWidth + (i - pivot) * pulseHorizontalScale
                canvas.drawText(
                        (i * VISUALIZER_UPDATE_INTERVAL_MILLISECONDS).toTimestampString(),
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

    fun updateState(state: State) {
        this.state = state

        when(state) {
            State.INITIALIZED -> {
                allowDragWhilePlaying = false
            }
            State.PAUSE_PLAYING -> {
                allowDragWhilePlaying = false
            }
            State.PAUSE_RECORDING -> {
                allowDragWhilePlaying = false
                overwriteStart = 0
                overwrittenAmplitudes.clear()
            }
            State.PLAY -> {
                allowDragWhilePlaying = true
                if (pivot >= amplitudes.size.dec())
                    pivot = 0
            }
            State.RECORD -> {
                allowDragWhilePlaying = false
            }
            State.STOP_PLAYING -> {
                allowDragWhilePlaying = false
            }
            State.STOP_RECORDING -> {
                overwriteStart = 0
                overwrittenAmplitudes.clear()
            }
            State.DRAG_WHILE_PLAYING -> {
                allowDragWhilePlaying = true
            }
            State.OVERWRITE -> {
                overwriteStart = pivot
                allowDragWhilePlaying = false
            }
        }
    }

    fun getMode() = state

    fun progressRate(): Double = pivot.toDouble() / amplitudes.size.dec()

    fun pivot() = pivot

    fun setPivot(pivot: Int) {
        this.pivot = pivot

        if (pivot < 0)
            this.pivot = 0
        else if (pivot > amplitudes.size.dec())
            this.pivot = amplitudes.size.dec()

        invalidate()
    }

    fun shiftPivot(shift: Int) {
        pivot += shift
        if (pivot < 0)
            this.pivot = 0
        else if (pivot > amplitudes.size.dec())
            this.pivot = amplitudes.size.dec()

        invalidate()
    }

    fun pulseCount() = amplitudes.count()

    fun isPivotAtEnd() = pivot >= amplitudes.size.dec()

    private fun drawTimestampBackgroundColor(canvas: Canvas) {
        rect.set(0, 0, width, (textHeight + timestampBottomPadding).toInt())
        canvas.drawRect(rect, timestampBackgroundPaint)
    }

    fun time() = pivot * VISUALIZER_UPDATE_INTERVAL_MILLISECONDS

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
