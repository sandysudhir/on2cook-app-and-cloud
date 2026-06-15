package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.dialog_edit_step_time.*
import kotlinx.android.synthetic.main.dialog_edit_step_time.view.*

class DialogEditStepTime : DialogFragment() {

    lateinit var context: AppCompatActivity
    lateinit var currentView: View

    var inductionTime = 0
    var magnetronTime = 0

    var onButtonClickListener : OnButtonClickListener? = null
    var isPlaying = false

    interface OnButtonClickListener {
        fun onButtonClick(isDone : Boolean, magnetronOnTime : Int, inductionOnTime : Int, isIncrement : Boolean, isIncInd: String, isIncMag: String)
    }

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_edit_step_time, container, false)
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        currentView.btnSubmit.setOnClickListener {
            if(currentView.etInductionTime.text.toString() != "" && currentView.etMagnetronTime.text.toString() != ""){
                var inductionTime = currentView.etInductionTime.text.toString().toInt()
                var magnetronTime = currentView.etMagnetronTime.text.toString().toInt()
                var isIncrement = isPlaying
                if(isIncrement){
                    isIncrement = if(inductionTime >= magnetronTime && tvInducPlusMinus.text == "+"){
                        true
                    }
                    else
                        inductionTime <= magnetronTime && tvMagPlusMinus.text == "+"
                }
                onButtonClickListener?.onButtonClick(true, currentView.etMagnetronTime.text.toString().toInt(),
                    currentView.etInductionTime.text.toString().toInt(), isIncrement,tvInducPlusMinus.text.toString(),tvMagPlusMinus.text.toString())
                dismiss()
            }else{
                Toast.makeText(context, "Please enter time", Toast.LENGTH_SHORT).show()
            }
        }

        if(isPlaying){
            inductionTime = 0
            magnetronTime = 0
            currentView.tvMagPlusMinus.visibility = View.VISIBLE
            currentView.tvInducPlusMinus.visibility = View.VISIBLE
        }else{
            currentView.tvMagPlusMinus.visibility = View.GONE
            currentView.tvInducPlusMinus.visibility = View.GONE
        }

        currentView.tvMagPlusMinus.setOnClickListener {
            if(currentView.tvMagPlusMinus.text == "+"){
                currentView.tvMagPlusMinus.text = "-"
            }else{
                currentView.tvMagPlusMinus.text = "+"
            }
        }

        currentView.tvInducPlusMinus.setOnClickListener {
            if(currentView.tvInducPlusMinus.text == "+"){
                currentView.tvInducPlusMinus.text = "-"
            }else{
                currentView.tvInducPlusMinus.text = "+"
            }
        }

        currentView.btnCancel.setOnClickListener {
            onButtonClickListener?.onButtonClick(false, 0, 0, false,tvInducPlusMinus.text.toString(),tvMagPlusMinus.text.toString())
            dismiss()
        }

        currentView.etInductionTime.setText("$inductionTime")
        currentView.etMagnetronTime.setText("$magnetronTime")

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