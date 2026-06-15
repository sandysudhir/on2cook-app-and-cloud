package com.invent.ontocook.utils

import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView


/**
 * Change font of TextInputLayout.
 */
fun TextInputLayout.changeFontFamily(@FontRes font: Int) {
    typeface = ResourcesCompat.getFont(context, font)

}

/**
 * Change color of SwipeRefreshLayout Progressbar
 */
fun SwipeRefreshLayout.changeProgressColor(@ColorRes color: Int) {
    setColorSchemeColors(resources.getColor(color, null))
}

/**
 * Change background tint of AppCompact ImageView.
 */
fun AppCompatImageView.changeDrawableImage(@DrawableRes drawable: Int) {
    setImageResource(drawable)
}

/**
 * Change background of AppCompactImageView.
 */
fun AppCompatImageView.changeBackground(@DrawableRes drawable: Int) {
    background = ResourcesCompat.getDrawable(resources, drawable, null)
}

/**
 * Change endIconDrawable of TextInputLayout.
 */
fun TextInputLayout.changeEndIconDrawable(@DrawableRes drawable: Int) {
    /*endIconDrawable = ResourcesCompat.getDrawable(resources, drawable, null)
    changeEndIconDrawableTintColor(if (drawable == R.drawable.ic_verified) R.color.green else R.color.colorPurple) //We have to show verified icon in Grrn color*/
}

/**
 * Change endIconDrawableTintColor of TextInputLayout.
 */
fun TextInputLayout.changeEndIconDrawableTintColor(@ColorRes color: Int) {
    setEndIconTintList(AppCompatResources.getColorStateList(context, color))
}

fun MaterialButton.changeBackgroundTint(@ColorRes color: Int) {
    backgroundTintList = ResourcesCompat.getColorStateList(resources, color, null)
}

fun MaterialButton.changeTextColor(@ColorRes color: Int) {
    setTextColor(ResourcesCompat.getColor(resources, color, null))
}

/*
* Execute block into try...catch
* */
inline fun <T> justTry(tryBlock: () -> T) = try {
    tryBlock()
} catch (e: Throwable) {
    e.printStackTrace()
}

fun changeTextInputLayoutsFont(list: Array<TextInputLayout>) {
    /* for (element in list) {
         element.changeFontFamily(R.font.lufga_regular)
     }*/
}

fun Any?.isNull() = this == null

fun Any?.notNull() = this != null

fun changeTextInputLayoutsBoldFont(list: Array<TextInputLayout>) {
    /*for (element in list) {
        element.changeFontFamily(R.font.lufga_bold)
    }*/
}

/**
 * Change font of AppCompatCheckBox.
 */
fun AppCompatCheckBox.changeFontFamily(@FontRes font: Int) {
    typeface = ResourcesCompat.getFont(context, font)
}

/**
 * Change background tint of ConstraintLayout.
 */
fun ConstraintLayout.changeBackgroundTint(@ColorRes color: Int) {
    backgroundTintList = ResourcesCompat.getColorStateList(resources, color, null)
}

/**
 * Change background tint of TextView.
 */
fun TextView.changeBackgroundTint(@ColorRes color: Int) {
    backgroundTintList = ResourcesCompat.getColorStateList(resources, color, null)
}

/**
 * Change background tint of View.
 */
fun View.changeBackgroundTint(@ColorRes color: Int) {
    backgroundTintList = ResourcesCompat.getColorStateList(resources, color, null)
}

/**
 * Apply imageView src tint*/
fun AppCompatImageView.applyColorFilter(@ColorRes color: Int) {
//    clearColorFilter()
    setColorFilter(ContextCompat.getColor(context, color), android.graphics.PorterDuff.Mode.SRC_IN)
//    setColorFilter(ResourcesCompat.getColor(resources, color, null))
}

/**
 * Change font of Tabs in TabLayout.
 */
fun TabLayout.changeFontFamily(@FontRes font: Int) {
    val vg = getChildAt(0) as ViewGroup
    val tabsCount = vg.childCount
    for (j in 0 until tabsCount) {
        val vgTab = vg.getChildAt(j) as ViewGroup
        val tabChildCount = vgTab.childCount
        for (i in 0 until tabChildCount) {
            val tabViewChild = vgTab.getChildAt(i)
            if (tabViewChild is TextView) {
                tabViewChild.typeface = ResourcesCompat.getFont(context, font)
            }
        }
    }
}

/**
 * Change margin of Tabs in TabLayout.
 */
fun TabLayout.setMargin(@DimenRes dimen: Int) {
    for (i in 0 until tabCount) {
        val tab = (getChildAt(0) as ViewGroup).getChildAt(i)
        val p = tab.layoutParams as ViewGroup.MarginLayoutParams
        p.setMargins(
            context.resources.getDimension(dimen).toInt(), 1,
            context.resources.getDimension(dimen).toInt(), 1
        )
        tab.requestLayout()
    }
}

/**
 * Change text of MaterialButton.
 */
fun MaterialButton.changeText(@StringRes string: Int) {
    text = resources.getString(string)
}

/**
 * Change text of MaterialTextView.
 */
fun MaterialTextView.changeText(@StringRes string: Int) {
    text = resources.getString(string)
}


private val transition: ChangeBounds by lazy {
    ChangeBounds().apply {
        duration = 300 // Set the duration of the animation (in milliseconds)
    }
}

fun ViewGroup.applyLayoutAnimation() {
    TransitionManager.beginDelayedTransition(
        this,
        transition
    )
}

//-------Convert string to integer-------//
fun convertStringToInt(fullString:String, replaceChar: String? = null): Int {
    return if (replaceChar.notNullAndNotEmpty()) {
        fullString.replace(replaceChar!!, "").trim().toInt()
    } else {
        fullString.trim().toInt()
    }
}







