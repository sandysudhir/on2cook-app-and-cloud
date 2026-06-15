package com.invent.ontocook.extension

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment

/**
 * Show Keyboard.
 */
fun DialogFragment.showKeyboard() {
    val view = dialog?.currentFocus
    val inputManager =
        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * Hide Keyboard.
 */
fun DialogFragment.hideKeyboard() {
    val view = dialog?.currentFocus
    val inputManager =
        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputManager?.hideSoftInputFromWindow(view?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
}

var dialog: Dialog? = null

/*fun showLoader(mContext: Context) {
    dialog = Dialog(mContext, R.style.ProgressDialogTheme) //FromAsistee
    dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog?.setContentView(R.layout.dialog_common_progress) //From Asistee
    dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog?.setCanceledOnTouchOutside(false)
    val window: Window? = dialog?.window
    val wlp: WindowManager.LayoutParams? = window?.attributes

    wlp?.gravity = Gravity.CENTER
    wlp?.flags = wlp?.flags?.and(WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv())
    window?.attributes = wlp
    dialog?.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    val activity = mContext as Activity
    if (dialog?.isShowing == false && !activity.isFinishing) {
        try {
            dialog?.show()
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}*/

fun hideLoader() {
    if (dialog != null) {
        dialog?.dismiss()
        dialog = null
    }
}

fun DialogFragment.setWidthPercent(percentage: Int) {
    val percent = percentage.toFloat() / 100
    val dm = Resources.getSystem().displayMetrics
    val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
    val percentWidth = rect.width() * percent
    dialog?.window?.setLayout(percentWidth.toInt(), (percentWidth.toInt()/2))
}