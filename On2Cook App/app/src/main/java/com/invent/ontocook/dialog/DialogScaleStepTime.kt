package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import com.invent.ontocook.utils.Settings
import kotlinx.android.synthetic.main.dialog_scale_step_time.view.*
import kotlinx.android.synthetic.main.dialog_scale_step_time.view.btnCancel
import kotlinx.android.synthetic.main.dialog_scale_step_time.view.btnSubmit

class DialogScaleStepTime : DialogFragment() {

    lateinit var context: AppCompatActivity
    lateinit var currentView: View
    var isPlaying = false
    var maxTime = 0
    var minTime = 0
    var isInduction = true
    var onButtonClickListener : OnButtonClickListener? = null

    interface OnButtonClickListener {
        fun onButtonClick(isDone : Boolean, magnetronOnTime : Int?, inductionOnTime : Int?)
    }

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_scale_step_time, container, false)
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if(isPlaying){
            currentView.seekBarTime.minValue = minTime
            currentView.seekBarTime.currentValue = 0
            currentView.seekBarTime.maxValue = maxTime
        }else{
            currentView.seekBarTime.minValue = Settings.STEPT_TIME_SLIDER_MIN
            currentView.seekBarTime.currentValue = currentView.seekBarTime.minValue
            currentView.seekBarTime.maxValue = Settings.STEPT_TIME_SLIDER_MAX
        }

        if(isInduction){
            currentView.seekBarTime.circleTextColor = ContextCompat.getColor(context, R.color.colorFlamColor)
            currentView.seekBarTime.fillColor = ContextCompat.getColor(context, R.color.colorFlamColor)
        }else{
            currentView.seekBarTime.circleTextColor = ContextCompat.getColor(context, R.color.colorMWColor)
            currentView.seekBarTime.fillColor = ContextCompat.getColor(context, R.color.colorMWColor)
        }

        currentView.btnSubmit.setOnClickListener {
            if(currentView.seekBarTime.currentValue != 0){
                onButtonClickListener?.onButtonClick(true, if(!isInduction) currentView.seekBarTime.currentValue else null,
                    if(isInduction) currentView.seekBarTime.currentValue else null)
                dismiss()
            }else{
                Toast.makeText(context, "Please select proper time", Toast.LENGTH_SHORT).show()
            }
        }

        currentView.btnCancel.setOnClickListener {
            onButtonClickListener?.onButtonClick(false, 0, 0)
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