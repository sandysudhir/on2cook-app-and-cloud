package com.invent.ontocook.extension

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextView.BufferType
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.core.text.HtmlCompat.fromHtml
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.tabs.TabLayout
import com.invent.ontocook.R
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.SafeClickListener


/**
 * Set visibility of the view
 */
fun View.viewShow() {
    visibility = VISIBLE
}

fun View.viewHide() {
    visibility = INVISIBLE
}

fun View.viewGone() {
    visibility = GONE
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

fun Fragment.getDrawable(drawableImage: Int): Drawable? {
    return ContextCompat.getDrawable(this.requireContext(), drawableImage)
}

fun Activity.getDrawable(drawableImage: Int): Drawable? {
    return ContextCompat.getDrawable(this, drawableImage)
}


fun loadCircleImage(
    imageView: ImageView,
    imagePath: String,
    placeHolder: Drawable? = null
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
   }
   catch (e:Exception){
       DebugLog.e("imageLoadError-->"+e.message)
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
    addTextChangedListener {
        object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                callback.invoke(p0.toString())
            }
        }
    }
}


fun getLayoutSize(layout: View): Size {
    var size = Size(0, 0)
    val vto = layout.viewTreeObserver
    vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
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
            src.toString().uppercase()
        } else src.toString().dropLast(1).uppercase()
    })

}

fun EditText.onlyCharacterWithUpperCase() {
    filters = arrayOf(InputFilter { src, start, end, dest, dstart, dend ->
        if (src.toString().matches("[\\p{L}\\p{M}]*".toRegex())) {
            src.toString().uppercase()
        } else src.toString().dropLast(1).uppercase()
    })

}