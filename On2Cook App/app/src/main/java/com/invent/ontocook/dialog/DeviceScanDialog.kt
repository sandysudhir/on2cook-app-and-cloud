package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.dialog_scan_device.view.*

class DeviceScanDialog : DialogFragment() {

    lateinit var context: AppCompatActivity
    lateinit var currentView: View

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_scan_device, container, false)
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        currentView.rippleBackground.startRippleAnimation()

        return currentView
    }

    fun updateResult(result: String) {
        currentView.tvResult.text = result
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