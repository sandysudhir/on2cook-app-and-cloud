package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import eo.view.bluetoothstate.BluetoothState
import kotlinx.android.synthetic.main.dialog_ble_error_dialog.view.*

class BLEErrorDialog : DialogFragment() {

    lateinit var context: AppCompatActivity
    private lateinit var currentView: View
    private var timer: CountDownTimer? = null

    interface OnDoneButtonClickListener {
        fun onDoneButtonClick()
    }

    var mOnDoneButtonClickListener: OnDoneButtonClickListener? = null

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_ble_error_dialog, container, false)
        dialog?.setTitle("")
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        setConnectionState()

        currentView.btnContinue.setOnClickListener {
            // mOnDoneButtonClickListener?.onDoneButtonClick()
            //dismiss()
            setConnectionState(
                title = "Searching",
                message = "Please wait.\nSearching for device...",
                state = BluetoothState.State.SEARCHING
            )
            OnToCookApplication.instance.startBoundService(true)
        }

        currentView.btnContinue.isEnabled = true

        return currentView
    }

    fun setConnectionState(
        title: String = "Connection Aborted",
        message: String = "Something went wrong with connection. Please reconnect.",
        state: BluetoothState.State = BluetoothState.State.OFF
    ) {
        currentView.tvTitle.text = title
        currentView.tvErrorMessage.text = message
        currentView.bleStateView.state = state

        when (state) {
            BluetoothState.State.OFF -> {
                currentView.btnContinue.visibility = View.VISIBLE
            }
            BluetoothState.State.SEARCHING -> {
                currentView.btnContinue.visibility = View.GONE
            }
            BluetoothState.State.CONNECTING -> {
                currentView.btnContinue.visibility = View.GONE
                prepareCountDownTimer()
            }
            BluetoothState.State.CONNECTED -> {
                currentView.btnContinue.visibility = View.GONE
            }
            else -> {}
        }
    }

    private fun prepareCountDownTimer() {
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished <= 1500) {
                    setConnectionState(title = "Connected", message ="Connection Established..", state = BluetoothState.State.CONNECTED)
                }
            }

            override fun onFinish() {
                dismiss()
            }
        }
        timer?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
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
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme_Dialog_Custom)
    }
}