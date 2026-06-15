package com.masoudss.lib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewConfiguration
import com.masoudss.lib.exception.SampleDataException

class WaveformSeekBar : View {

    private var mCanvasWidth = 0
    private var mCanvasHeight = 0
    private var MAX_POWER = 100
    private val mWavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mWaveRect = RectF()
    private var mWaveRectProgress = RectF()
    private val mProgressCanvas = Canvas()
    private var mMaxValue = Utils.dp(context, 2).toInt()
    private var mTouchDownY = 0F
    private var mScaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {

        val ta = context.obtainStyledAttributes(attrs, R.styleable.WaveformSeekBar)

        waveWidth = ta.getDimension(R.styleable.WaveformSeekBar_wave_width, waveWidth)
        waveGap = ta.getDimension(R.styleable.WaveformSeekBar_wave_gap, waveGap)
        waveCornerRadius =
            ta.getDimension(R.styleable.WaveformSeekBar_wave_corner_radius, waveCornerRadius)
        waveMinHeight = ta.getDimension(R.styleable.WaveformSeekBar_wave_min_height, waveMinHeight)
        waveBackgroundColor =
            ta.getColor(R.styleable.WaveformSeekBar_wave_background_color, waveBackgroundColor)
        waveProgressColor =
            ta.getColor(R.styleable.WaveformSeekBar_wave_progress_color, waveProgressColor)
        progress = ta.getFloat(R.styleable.WaveformSeekBar_wave_progress, progress)
        val gravity = ta.getString(R.styleable.WaveformSeekBar_wave_gravity)
        waveGravity = when (gravity) {
            "1" -> WaveGravity.LEFT
            "2" -> WaveGravity.CENTER
            else -> WaveGravity.RIGHT
        }

        ta.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasWidth = w
        mCanvasHeight = h
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (sample == null || sample!!.isEmpty())
            return
            //throw SampleDataException()

        //mMaxValue = sample!!.max()!!
        mMaxValue = MAX_POWER

        var i = 0F
        var lastWaveTop = paddingTop.toFloat()

        while (i < sample!!.size) {
            var waveCalWidth = getAvailableWith() * (sample!![i.toInt()].toFloat() / mMaxValue)

            var left: Float = when (waveGravity) {
                WaveGravity.LEFT -> paddingLeft.toFloat()
                WaveGravity.CENTER -> paddingLeft + getAvailableWith() / 2F - waveCalWidth / 2F
                WaveGravity.RIGHT -> mCanvasWidth - paddingRight - waveCalWidth
            }

            //backgroud rectangle
//            canvas.drawRect(
//                paddingLeft.toFloat(),
//                lastWaveTop,
//                paddingLeft.toFloat() + getAvailableWith(),
//                lastWaveTop + waveMinHeight,
//                mWavePaint
//            )
            mWavePaint.color = waveBackgroundColor
            canvas.drawRoundRect(
                RectF(
                    left,
                    lastWaveTop,
                    left + waveCalWidth,
                    lastWaveTop + waveMinHeight
                ), waveCornerRadius, waveCornerRadius, mWavePaint
            )

            mWaveRect.set(left, lastWaveTop, left + waveCalWidth, lastWaveTop + waveMinHeight)

            when {
//                mWaveRect.contains(
//                    mWaveRect.centerX(),
//                    (getAvailableHeight() * progress) / 100F
//                ) -> {
//                    println("contain center")
//                    val fillHeight = ((getAvailableHeight() * progress) / 100F)
//                    //println("available height  ${getAvailableHeight()}     ${progress}   ${fillHeight}")
//
//                    mWavePaint.color = waveProgressColor
//                    canvas.drawRect(
//                        mWaveRect.left,
//                        mWaveRect.top,
//                        mWaveRect.right,
//                        fillHeight,
//                        mWavePaint
//                    )
//
//                    mWavePaint.color = waveBackgroundColor
//                    canvas.drawRect(
//                        mWaveRect.left,
//                        fillHeight,
//                        mWaveRect.right,
//                        mWaveRect.bottom,
//                        mWavePaint
//                    )
//
//                    mWavePaint.shader = null
//                }
                mWaveRect.bottom <= ((getAvailableHeight() * progress) / 100F) -> {
                    // println("progress color blue")
                    mWavePaint.color = waveProgressColor
                    mWavePaint.shader = null

                    mWaveRectProgress = mWaveRect

                    if (progressSample != null && progressSample!!.isNotEmpty()) {
                        val progressWidth =
                            getAvailableWith() * (progressSample!![i.toInt()].toFloat() / progressSample!!.maxOrNull()!!)
                        val progressLeft = when (waveGravity) {
                            WaveGravity.LEFT -> paddingLeft.toFloat()
                            WaveGravity.CENTER -> paddingLeft + getAvailableWith() / 2F - progressWidth / 2F
                            WaveGravity.RIGHT -> mCanvasWidth - paddingRight - progressWidth
                        }
                        mWaveRectProgress.left = progressLeft
                        mWaveRectProgress.right = progressLeft + progressWidth
                    }

                    canvas.drawRoundRect(
                        mWaveRectProgress,
                        waveCornerRadius,
                        waveCornerRadius,
                        mWavePaint
                    )
                }
                else -> {
                    //println("backgroud color white")
                    mWavePaint.color = waveBackgroundColor
                    mWavePaint.shader = null
                    canvas.drawRoundRect(mWaveRect, waveCornerRadius, waveCornerRadius, mWavePaint)
                }
            }
            lastWaveTop = mWaveRect.bottom + waveGap

            if (lastWaveTop + waveMinHeight > getAvailableHeight() + paddingTop) {
                //println("in break....")
                break
            }
            i += 1
        }
    }

    private fun getAvailableWith() = mCanvasWidth - paddingLeft - paddingRight
    private fun getAvailableHeight() = mCanvasHeight - paddingTop - paddingBottom

    var onProgressChanged: SeekBarOnProgressChanged? = null

    fun setProgressSampleData(progressSample : IntArray?){
        this.progressSample = progressSample
        this.sample = progressSample
    }

    var sample: IntArray? = null
        set(value) {
            field = value
            invalidate()
        }

    var progressSample: IntArray? = null

    var progress: Float = 0F
        set(value) {
            field = value
            invalidate()

            if (onProgressChanged != null)
                onProgressChanged!!.onProgressChanged(this, progress, false)
        }

    var waveBackgroundColor: Int = Color.LTGRAY
        set(value) {
            field = value
            invalidate()
        }

    var waveProgressColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var waveGap: Float = Utils.dp(context, 2)
        set(value) {
            field = value
            invalidate()
        }

    var waveWidth: Float = Utils.dp(context, 5)
        set(value) {
            field = value
            invalidate()
        }

    var waveMinHeight: Float = waveWidth
        set(value) {
            field = value
            invalidate()
        }

    var waveCornerRadius: Float = Utils.dp(context, 2)
        set(value) {
            field = value
            invalidate()
        }

    var waveGravity: WaveGravity = WaveGravity.CENTER
        set(value) {
            field = value
            invalidate()
        }
}