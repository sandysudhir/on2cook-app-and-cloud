package com.invent.ontocook.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextView.BufferType
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.core.text.HtmlCompat.fromHtml
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale


/**
 * Set visibility of the view
 */
fun View.viewShow() {
    visibility = VISIBLE
}

fun View.gone() {
    visibility = GONE
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.viewHide() {
    visibility = INVISIBLE
}

fun View.viewGone() {
    visibility = GONE
}

fun View.visible() {
    visibility = VISIBLE
}


fun View.applyDateBackground(drawable: Drawable?) {
//    makeVisible()
//    background = drawable
}


// Show hide button loader
/*
fun showButtonLoader(view: View, loader: AVLoadingIndicatorView)
{
    view.viewHide()
    loader.viewShow()
}
fun hideButtonLoader(view: View, loader: AVLoadingIndicatorView)
{
    view.viewShow()
    loader.viewGone()
}
*/

/**
 * Scale bitmap.
 */
fun Bitmap.scale(ratio: Float): Bitmap {
    return Bitmap.createScaledBitmap(
        this,
        Math.round(width.times(ratio)),
        Math.round(height.times(ratio)),
        true
    )
}

infix fun View.onSafeClick(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

infix fun View.onNoSafeClick(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener(defaultInterval = 0) {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

/*fun blurRadius(
    llRoot: ViewGroup,
    context: Activity,
    editLayout: BlurView,
    radius: Float
) {
    val windowBackground = context.window.decorView.background

    editLayout.setupWith(llRoot)
        .setFrameClearDrawable(windowBackground)
        .setBlurAlgorithm(RenderScriptBlur(context))
        .setBlurRadius(radius)
}*/

fun TabLayout.changeTabFont(@FontRes font: Int) {
    val vg = getChildAt(0) as ViewGroup
    val tabsCount = vg.childCount
    for (j in 0 until tabsCount) {
        val vgTab = vg.getChildAt(j) as ViewGroup
        val tabChildCount = vgTab.childCount
        for (i in 0 until tabChildCount) {
            val tabViewChild = vgTab.getChildAt(i)
            if (tabViewChild is TextView) {
                tabViewChild.typeface = ResourcesCompat.getFont(context, font)
                tabViewChild.isAllCaps = false
            }
        }
    }
}

infix fun View.onClick(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

fun View.visibleIfOrGone(isShown: Boolean) {
    if (isShown) {
        visible()
    } else {
        gone()
    }
}

fun View.goneIfOrVisible(isHide: Boolean) {
    if (isHide) {
        gone()
    } else {
        visible()
    }
}

//-------if need to visibleView on boolean condition-------//
fun View.visibleIf(isVisible: Boolean) {
    if (isVisible) {
        visible()
    }
}

//-------if need to Hide(Gone)View on boolean condition-------//
fun View.goneIf(isGone: Boolean) {
    if (isGone) {
        gone()
    }
}

fun Fragment.getDrawable(drawableImage: Int): Drawable? {
    return ContextCompat.getDrawable(this.requireContext(), drawableImage)
}

fun Activity.getDrawable(drawableImage: Int): Drawable? {
    return ContextCompat.getDrawable(this, drawableImage)
}


fun loadCircleImage(
    imageView: ImageView,
    imagePath: String,
    placeHolder: Drawable? = null,
) {
    imagePath.let {
        Glide.with(imageView.context)
            .load(it)
            .apply(
                RequestOptions().transform(
                    CircleCrop(),
                    RoundedCorners(16)
                )
            )
            .placeholder(placeHolder)
            .into(imageView)

    }
}


fun loadCircleImage(imageView: ImageView, imagePath: String) {
    imagePath.let {
        Glide.with(imageView.context)
            .load(it)
            .apply(
                RequestOptions().transform(
                    CircleCrop(),
                    RoundedCorners(16)
                )
            )
            .into(imageView)

    }
}

fun loadImage(imageView: ImageView, imagePath: String) {
    try {
        imagePath.let {
            Glide.with(imageView.context)
                .load(it)
                .into(imageView)
        }
    } catch (e: Exception) {
        DebugLog.e("imageLoadError-->" + e.message)
    }
}


fun Fragment.loadRoundCornerImage(imageView: ImageView, link: String, placeHolder: Drawable?) {
    link.let {
        Glide.with(imageView.context).load(it)
            .apply(
                RequestOptions().transform(
                    CenterCrop(),
                    RoundedCorners(16)
                )
            )
            .placeholder(placeHolder)
            .into(imageView)
    }
}

fun Fragment.loadRoundCornerImage(imageView: ImageView, placeHolder: Drawable?) {
    placeHolder.let {
        Glide.with(imageView.context).load(placeHolder)
            .apply(
                RequestOptions().transform(
                    CenterCrop(),
                    RoundedCorners(16)
                )
            )
            .into(imageView)
    }
}


fun Fragment.loadCircleImage(imageView: ImageView, placeHolder: Drawable?) {
    placeHolder.let {
        Glide.with(imageView.context)
            .load(placeHolder)
            .apply(
                RequestOptions().transform(
                    CircleCrop(),
                    RoundedCorners(16)
                )
            )
            .into(imageView)

    }
}

fun EditText.onTextChange(callback: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            callback.invoke(s.toString())
        }
    })
}

/**
 * TextInputLayout endDrawable click listener
 * */
fun TextInputLayout.onEndIconClick(callback: (View) -> Unit) {
    this.setEndIconOnClickListener {
        callback.invoke(it)
    }
}


fun getLayoutSize(layout: View): Size {
    var size = Size(0, 0)
    val vto = layout.viewTreeObserver
    vto.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            layout.viewTreeObserver.removeOnGlobalLayoutListener(this)
            size = Size(layout.measuredWidth, layout.measuredHeight)
        }
    })
    return size
}

class Size(var width: Int = 0, var height: Int = 0)

/**
 * User This Extension For Handle Multiple Click..
 */
fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

/*fun makeTextViewResizable(
    tv: TextView,
    maxLine: Int,
    expandText: String,
    viewMore: Boolean,
) {
    if (tv.tag == null) {
        tv.tag = tv.text
    }
    val vto = tv.viewTreeObserver
    vto.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val text: String
            val lineEndIndex: Int
            val obs = tv.viewTreeObserver
            obs.removeGlobalOnLayoutListener(this)
            if (maxLine == 0) {
                lineEndIndex = tv.layout.getLineEnd(0)
                text = tv.text.subSequence(0, lineEndIndex - expandText.length + 1)
                    .toString() + " " + expandText
            } else if (maxLine > 0 && tv.lineCount >= maxLine) {
                lineEndIndex = tv.layout.getLineEnd(maxLine - 1)
                text = tv.text.subSequence(0, lineEndIndex - expandText.length + 1)
                    .toString() + " " + expandText
            } else {
                lineEndIndex = tv.layout.getLineEnd(tv.layout.lineCount - 1)
                text = tv.text.subSequence(0, lineEndIndex).toString() + " " + expandText
            }
            tv.text = text
            tv.movementMethod = LinkMovementMethod.getInstance()
            tv.setText(
                addClickablePartTextViewResizable(
                    fromHtml(tv.text.toString(), FROM_HTML_MODE_COMPACT),
                    tv,
                    lineEndIndex,
                    expandText,
                    viewMore
                ), BufferType.SPANNABLE
            )
        }
    })
}*/


/*fun addClickablePartTextViewResizable(
    strSpanned: Spanned, tv: TextView,
    maxLine: Int, spanableText: String, viewMore: Boolean
): SpannableStringBuilder {
    val str = strSpanned.toString()
    val ssb = SpannableStringBuilder(strSpanned)
    if (str.contains(spanableText)) {
        ssb.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                tv.layoutParams = tv.layoutParams
                tv.setText(tv.tag.toString(), BufferType.SPANNABLE)
                tv.invalidate()
                if (viewMore) {
                    makeTextViewResizable(
                        tv,
                        -1,
                        tv.context.getString(R.string.label_read_less),
                        false
                    )
                } else {
                    makeTextViewResizable(
                        tv,
                        6,
                        tv.context.getString(R.string.label_read_more),
                        true
                    )
                }
            }
        }, str.indexOf(spanableText), str.indexOf(spanableText) + spanableText.length, 0)
    }
    return ssb
}*/

fun EditText.capsEditText() {
//        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    filters = arrayOf(InputFilter.AllCaps())

}

fun EditText.onlyCharacter() {
    filters = arrayOf(InputFilter { src, start, end, dest, dstart, dend ->
        if (src.toString().matches("[\\p{L}\\p{M}]*".toRegex())) {
            src
        } else src.toString().dropLast(1)
    })

}

fun EditText.onlyCharacterWithSpace() {
    filters = arrayOf(InputFilter { src, start, end, dest, dstart, dend ->
        if (src.toString().matches("[\\p{L}\\p{M}\\p{javaSpaceChar}]*".toRegex())) {
            src
        } else src.toString().dropLast(1)
    })

}

fun EditText.onlyCharacterWithSpaceWithUpperCaps() {
    filters = arrayOf(InputFilter { src, start, end, dest, dstart, dend ->
        if (src.toString().matches("[\\p{L}\\p{M}\\p{javaSpaceChar}]*".toRegex())) {
            src.toString().uppercase(Locale.getDefault())
        } else src.toString().dropLast(1).uppercase(Locale.getDefault())
    })

}

fun EditText.onlyCharacterWithUpperCase() {
    filters = arrayOf(InputFilter { src, start, end, dest, dstart, dend ->
        if (src.toString().matches("[\\p{L}\\p{M}]*".toRegex())) {
            src.toString().uppercase(Locale.getDefault())
        } else src.toString().dropLast(1).uppercase(Locale.getDefault())
    })

}