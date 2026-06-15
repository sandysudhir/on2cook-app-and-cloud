package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.dialog_pause_recipe_view.view.*

class DialogPauseRecipeView : DialogFragment() {

    lateinit var context: AppCompatActivity
    lateinit var currentView: View

    var onButtonClickListener : OnButtonClickListener? = null

    interface OnButtonClickListener {
        fun onButtonClick(isDone : Boolean, magnetronOnTime : Int, inductionOnTime : Int, isIncrement : Boolean)
    }

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_pause_recipe_view, container, false)
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        currentView.tvCancelInTime.setOnClickListener {
            dismiss()
        }

        return currentView
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.DeviceScanDialogTheme)
    }
}