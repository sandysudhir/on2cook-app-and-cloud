package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import com.invent.ontocook.databinding.DialogEditDeviceNameBinding
import com.invent.ontocook.databinding.DialogFilterBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.FilterData
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import kotlinx.android.synthetic.main.activity_fast_scoll.list

class EditNameDialog(
    private val deviceList: ArrayList<PairedDeviceData>,
    var callbackSuccess: (string: String) -> Unit
) : DialogFragment() {

    lateinit var context: AppCompatActivity
    private lateinit var currentView: View
    lateinit var binding: DialogEditDeviceNameBinding

    interface OnDoneButtonClickListener {
        fun onDoneButtonClick()
    }

    var mOnDoneButtonClickListener: OnDoneButtonClickListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
//        currentView = inflater.inflate(R.layout.dialog_filter, container, false)
        binding =
            DataBindingUtil.inflate(inflater, R.layout.dialog_edit_device_name, container, false)
        dialog?.setTitle("")
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun init() {

    }


    private fun initListener() {
        binding.btnCancel.setSafeOnClickListener {
            dismiss()
        }
        binding.btnDone.setSafeOnClickListener {
            if (binding.etDeviceName.text.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    context.resources.getText(R.string.txt_already_exits),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (!deviceList.any { it.name == binding.etDeviceName.text.toString() }) {
                callbackSuccess.invoke(binding.etDeviceName.text.toString())
                dismiss()
            } else {
                Toast.makeText(
                    context,
                    context.resources.getText(R.string.txt_already_exits),
                    Toast.LENGTH_SHORT
                ).show()
            }
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