package params.com.stepprogressview

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.reflect.KProperty


class StepProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {


    var totalProgress: Int by OnValidateProp(184)

    //list should be sorted in increasing order as per markers progress
    var markers: MutableList<Float> by OnLayoutProp(mutableListOf())


    var currentProgress: Float by OnValidateProp(0f)


    var markerWidth: Float by OnValidateProp(5F.pxValue())


    var rectRadius: Float by OnValidateProp(8F.pxValue())


    var textMargin: Float by OnValidateProp(0F.pxValue())


    var progressBarHeight: Float by OnLayoutProp(15F.pxValue())

    var progressBarWidth: Float by OnLayoutProp(300F.pxValue())


    var textSizeMarkers: Float by OnLayoutProp(12F.pxValue(TypedValue.COMPLEX_UNIT_SP)) {
        paintText.textSize = textSizeMarkers

    }

    var markerCurrentProgressColor: Int by OnValidateProp(Color.BLACK) {
        paintCurrentProgress.color = markerCurrentProgressColor
    }

    var markerColor: Int by OnValidateProp(Color.WHITE) {
        paintMarkers.color = markerColor
    }

    var progressColor: Int by OnValidateProp(Color.GREEN) {
        paintProgress.color = progressColor
    }

    var progressBackgroundColor: Int by OnValidateProp(Color.GRAY) {
        paintBackground.color = progressBackgroundColor
    }

    var textColorMarker: Int by OnValidateProp(Color.BLACK) {
        paintText.color = textColorMarker
    }

    private val paintBackground = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = progressBackgroundColor
    }

    private val paintMarkers = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = markerColor
    }

    private val paintCurrentProgress = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = markerCurrentProgressColor
    }

    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = progressColor
    }

    val paintText = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = textColorMarker
        it.style = Paint.Style.FILL
        it.textSize = textSizeMarkers
        it.textAlign = Paint.Align.CENTER
        it.typeface = Typeface.DEFAULT
    }

    var onCurrentStepHighlight: OnCurrentStepHighlight? = null

    interface OnCurrentStepHighlight {
        fun onCurrentStepHighlight(position: Int)
        fun onBeforeCurrentStepHighlight(position: Int)
    }

    private val rBar = RectF()

    //used for drawing one-side curved rectangle
    private val rectRoundPath = Path()

    //used for drawing complete view
    private val drawingPath = Path()

    private val arcRect = RectF()

    // FIX 1: textHeight should be Float to properly align text inside circles
    private var textHeight: Float = 0f

    private var textWidth: Int = 0

    private var textHorizontalCenter: Float = 0F

    private var propsInitialisedOnce = false

    private var extraWidthLeftText: Float = 16F

    private var extraWidthRightText: Float = 16F

    private var currentPoint: Drawable? = null

    private var thumbSize = dp(26)

    init {

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.StepProgressView, 0, 0)

        try {
            // FIX 2: getDrawable(int) is deprecated — use ContextCompat.getDrawable() instead
            currentPoint = ContextCompat.getDrawable(context, R.drawable.ic_current_point)

            currentProgress =
                a.getFloat(R.styleable.StepProgressView_currentProgress, currentProgress)
            totalProgress = a.getInt(R.styleable.StepProgressView_totalProgress, totalProgress)

            progressBarHeight = a.getDimension(
                R.styleable.StepProgressView_progressBarHeight,
                progressBarHeight
            )
            progressBarWidth = a.getDimension(
                R.styleable.StepProgressView_progressBarWidth,
                progressBarWidth
            )
            textMargin = a.getDimension(R.styleable.StepProgressView_textMargin, textMargin)
            markerWidth = a.getDimension(R.styleable.StepProgressView_markerWidth, markerWidth)
            textSizeMarkers = a.getDimension(R.styleable.StepProgressView_textSize, textSizeMarkers)

            progressBackgroundColor = a.getColor(
                R.styleable.StepProgressView_progressBackgroundColor,
                progressBackgroundColor
            )
            markerColor = a.getColor(R.styleable.StepProgressView_markerColor, markerColor)
            progressColor = a.getColor(R.styleable.StepProgressView_progressColor, progressColor)
            textColorMarker = a.getColor(R.styleable.StepProgressView_textColor, textColorMarker)

            try {
                val resource: Int = a.getResourceId(R.styleable.StepProgressView_textFont, -1)
                if (resource != -1) {
                    val statusTypeface = ResourcesCompat.getFont(getContext(), resource)
                    paintText.typeface = statusTypeface
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }


            val markerString = a.getString(R.styleable.StepProgressView_markers)
            if (!markerString.isNullOrBlank()) {
                this.markers.clear()

                val input = markerString.split(",")

                try {
                    input.map { it -> if (it.trim().toInt() in 1..totalProgress) this.markers.add(it.trim().toFloat()) }
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid input markers! Should be comma separated digits")
                }
            }
        } finally {
            a.recycle()
        }

        // FIX 3: Compute textHeight once after init so onDraw can use it correctly
        updateTextHeight()

        propsInitialisedOnce = true
    }

    // FIX 3: Helper to measure text height from paintText
    private fun updateTextHeight() {
        val rect = Rect()
        paintText.getTextBounds("0", 0, 1, rect)
        textHeight = rect.height().toFloat()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun getSuggestedMinimumWidth(): Int {
        return Math.ceil((progressBarWidth + extraWidthLeftText + extraWidthRightText).toDouble())
            .toInt()
    }

    override fun getSuggestedMinimumHeight(): Int {
        return Math.ceil((progressBarHeight).toDouble()).toInt()
    }

    fun getBarHeight(): Float {
        return rBar.bottom - rBar.top
    }

    fun getBarWidth(): Float {
        return rBar.width()
    }

    private fun setTextWidth(): Float {
        if (markers.size == 0) {
            return 0F
        }

        val rect = Rect()
        val text = "8"
        paintText.getTextBounds(text, 0, text.length, rect)
        textWidth = rect.width() * 6
        return textWidth + textMargin
    }

    override fun onLayout(changed: Boolean, leftP: Int, topP: Int, rightP: Int, bottomP: Int) {
        super.onLayout(changed, leftP, topP, rightP, bottomP)
        rBar.apply {
            left = extraWidthLeftText
            top = 0F
            right = left + progressBarWidth
            bottom = progressBarHeight
        }
        textHorizontalCenter = (progressBarWidth + textMargin) + textHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawingPath.reset()

        if (currentProgress.toInt() in 0 until totalProgress) {
            val progressY = (currentProgress / totalProgress.toFloat()) * (rBar.bottom - rBar.top)

            if (progressY > rectRadius) {
                drawingPath.addPath(
                    drawRoundedBottomRect(
                        rBar.left, (progressY - 1), rBar.right,
                        rBar.bottom, rectRadius, paintBackground, canvas
                    )
                )

                val progressBottom: Float
                val drawProgressInRightCorner = progressY > (rBar.bottom - rectRadius)

                if (drawProgressInRightCorner) {
                    progressBottom = rBar.bottom - rectRadius
                } else {
                    progressBottom = progressY
                }

                drawingPath.addPath(
                    drawRoundedTopRect(
                        rBar.left, rBar.top, rBar.right,
                        progressBottom, rectRadius, paintProgress, canvas
                    )
                )

                canvas.save()
                canvas.clipPath(drawingPath)

                if (drawProgressInRightCorner) {
                    canvas.drawRect(
                        rBar.left,
                        (rBar.bottom - rectRadius),
                        rBar.right,
                        progressY,
                        paintProgress
                    )
                }

            } else {
                drawCompleteProgressBar(canvas, paintBackground)
                canvas.drawRect(rBar.left, rBar.top, rBar.right, progressY, paintProgress)
            }
        } else {
            val paint = if (currentProgress > 0) paintProgress else paintBackground
            drawCompleteProgressBar(canvas, paint)
        }

        for ((index, i) in markers.withIndex()) {
            if (i.toInt() in 1..totalProgress) {
                val top: Float = (i / totalProgress.toFloat()) * (rBar.bottom - rBar.top)
                val circleRadius = (rBar.width() / 2) - 2f

                canvas.drawCircle(
                    (rBar.width() / 2) + extraWidthLeftText,
                    top,
                    circleRadius,
                    paintMarkers
                )

                // FIX 4: Correct text vertical centering inside the circle
                // Previously used `textHeight` (Int=0) and circleRadius/2 offset which misaligned text.
                // Now use textHeight/2 to properly center text inside the circle.
                canvas.drawText(
                    (index + 1).toString(),
                    (rBar.width() / 2) + extraWidthLeftText,
                    top + (textHeight / 2),
                    paintText
                )
            }

            if (i == currentProgress.toInt().toFloat()) {
                // FIX 5: Guard against index - 1 going negative (first marker has no "before" step)
                if (index > 0) {
                    onCurrentStepHighlight?.onBeforeCurrentStepHighlight(index - 1)
                }
                onCurrentStepHighlight?.onCurrentStepHighlight(index + 1)
            }
        }

        canvas.restore()
    }

    private fun dp(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context!!.resources.displayMetrics
        ).toInt()
    }

    private fun drawCompleteProgressBar(canvas: Canvas, paint: Paint) {
        drawingPath.addRoundRect(rBar, rectRadius, rectRadius, Path.Direction.CW)
        canvas.drawPath(drawingPath, paint)
        canvas.save()
        canvas.clipPath(drawingPath)
    }

    private fun drawRoundedTopRect(
        leftP: Float, topP: Float, rightP: Float, bottomP: Float,
        cornerRadius: Float, paint: Paint, canvas: Canvas
    ): Path {

        rectRoundPath.reset()
        arcRect.run {
            left = leftP
            top = topP
            right = rightP
            bottom = topP + (2 * cornerRadius)
        }

        rectRoundPath.addArc(arcRect, 90F, 270F)

        rectRoundPath.addRect(leftP, topP + cornerRadius, rightP, bottomP, Path.Direction.CW)

        canvas.drawPath(rectRoundPath, paint)

        return rectRoundPath
    }

    private fun drawRoundedBottomRect(
        leftP: Float, topP: Float, rightP: Float, bottomP: Float,
        cornerRadius: Float, paint: Paint, canvas: Canvas
    ): Path {

        arcRect.run {
            left = leftP
            top = bottomP - (2 * cornerRadius)
            right = rightP
            bottom = bottomP
        }

        rectRoundPath.reset()
        if (bottomP - topP > cornerRadius) {
            rectRoundPath.addRect(leftP, topP, rightP, bottomP - cornerRadius, Path.Direction.CW)
        }
        rectRoundPath.addArc(arcRect, -90F, 270F)
        canvas.drawPath(rectRoundPath, paint)

        return rectRoundPath
    }


    private fun Float.pxValue(unit: Int = TypedValue.COMPLEX_UNIT_DIP): Float {
        return TypedValue.applyDimension(unit, this, resources.displayMetrics)
    }

    /**
     * Delegate property used to requestLayout on value set after executing a custom function
     */
    inner class OnLayoutProp<T>(private var field: T, private inline var func: () -> Unit = {}) {
        operator fun setValue(thisRef: Any?, p: KProperty<*>, v: T) {
            field = v
            func()
            if (propsInitialisedOnce) {
                requestLayout()
            }
        }

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T {
            return field
        }
    }

    /**
     * Delegate Property used to invalidate on value set after executing a custom function
     */
    inner class OnValidateProp<T>(private var field: T, private inline var func: () -> Unit = {}) {
        operator fun setValue(thisRef: Any?, p: KProperty<*>, v: T) {
            field = v
            func()
            if (propsInitialisedOnce) {
                invalidate()
            }
        }

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T {
            return field
        }
    }
}