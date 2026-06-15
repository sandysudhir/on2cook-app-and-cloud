package com.invent.ontocook.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import com.invent.ontocook.databinding.DialogFilterBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.FilterData

class FilterDialog(
    private val logData: FilterData?,
    private val minMaxData: FilterData?,
    var callbackSuccess: (filterData: FilterData) -> Unit
) : DialogFragment() {

    lateinit var context: AppCompatActivity
    private lateinit var currentView: View
    lateinit var binding: DialogFilterBinding

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
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_filter, container, false)
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
        if (minMaxData != null) {
            binding.edMicrowaveFrom.hint = "${minMaxData.magCurrentStart}"
            binding.edMicrowaveTo.hint = "${minMaxData.magCurrentEnd}"
            binding.edInductionFrom.hint = "${minMaxData.indCurrentStart}"
            binding.edInductionTo.hint = "${minMaxData.indCurrentEnd}"
            binding.edMagnatronFrom.hint = "${minMaxData.magTempStart}"
            binding.edMagnatronTo.hint = "${minMaxData.magTempEnd}"
            binding.edCoilFrom.hint = "${minMaxData.coilTempStart}"
            binding.edCoilTo.hint = "${minMaxData.coilTempEnd}"
            binding.edAmbientFrom.hint = "${minMaxData.ambientTempStart}"
            binding.edAmbientTo.hint = "${minMaxData.ambientTempEnd}"
            binding.edPanFrom.hint = "${minMaxData.panTempStart}"
            binding.edPanTo.hint = "${minMaxData.panTempEnd}"
            binding.edPcbFrom.hint = "${minMaxData.pcbTempStart}"
            binding.edPcbTo.hint = "${minMaxData.pcbTempEnd}"
            binding.edOilFrom.hint = "${minMaxData.oilTempStart}"
            binding.edOilTo.hint = "${minMaxData.oilTempEnd}"
            binding.edDeviceOnFrom.hint = "${minMaxData.deviceOnTimeStart}"
            binding.edDeviceOnTo.hint = "${minMaxData.deviceOnTimeEnd}"
            binding.edDeviceOffFrom.hint = "${minMaxData.deviceOffTimeStart}"
            binding.edDeviceOffTo.hint = "${minMaxData.deviceOffTimeEnd}"
            binding.edInductionONFrom.hint = "${minMaxData.indTimeStart}"
            binding.edInductionONTo.hint = "${minMaxData.indTimeEnd}"
            binding.edMicrowaveONFrom.hint = "${minMaxData.magTimeStart}"
            binding.edMicrowaveONTo.hint = "${minMaxData.magTimeEnd}"
        }
        if (logData != null) {
            binding.edMicrowaveFrom.setText("${logData.magCurrentStart}")
            binding.edMicrowaveTo.setText("${logData.magCurrentEnd}")
            binding.edInductionFrom.setText("${logData.indCurrentStart}")
            binding.edInductionTo.setText("${logData.indCurrentEnd}")
            binding.edMagnatronFrom.setText("${logData.magTempStart}")
            binding.edMagnatronTo.setText("${logData.magTempEnd}")
            binding.edCoilFrom.setText("${logData.coilTempStart}")
            binding.edCoilTo.setText("${logData.coilTempEnd}")
            binding.edAmbientFrom.setText("${logData.ambientTempStart}")
            binding.edAmbientTo.setText("${logData.ambientTempEnd}")
            binding.edPanFrom.setText("${logData.panTempStart}")
            binding.edPanTo.setText("${logData.panTempEnd}")
            binding.edPcbFrom.setText("${logData.pcbTempStart}")
            binding.edPcbTo.setText("${logData.pcbTempEnd}")
            binding.edOilFrom.setText("${logData.oilTempStart}")
            binding.edOilTo.setText("${logData.oilTempEnd}")
            binding.edDeviceOnFrom.setText("${logData.deviceOnTimeStart}")
            binding.edDeviceOnTo.setText("${logData.deviceOnTimeEnd}")
            binding.edDeviceOffFrom.setText("${logData.deviceOffTimeStart}")
            binding.edDeviceOffTo.setText("${logData.deviceOffTimeEnd}")
            binding.edInductionONFrom.setText("${logData.indTimeStart}")
            binding.edInductionONTo.setText("${logData.indTimeEnd}")
            binding.edMicrowaveONFrom.setText("${logData.magTimeStart}")
            binding.edMicrowaveONTo.setText("${logData.magTimeEnd}")
        }
    }

    private fun initListener() {
        binding.ivClose.setSafeOnClickListener {
            dismiss()
        }
        binding.btnApply.setSafeOnClickListener {
            val filterData = FilterData(
                getDataFromEditText(binding.edMicrowaveFrom),
                getDataFromEditText(binding.edMicrowaveTo),
                getDataFromEditText(binding.edInductionFrom),
                getDataFromEditText(binding.edInductionTo),
                getDataFromEditText(binding.edMagnatronFrom),
                getDataFromEditText(binding.edMagnatronTo),
                getDataFromEditText(binding.edCoilFrom),
                getDataFromEditText(binding.edCoilTo),
                getDataFromEditText(binding.edAmbientFrom),
                getDataFromEditText(binding.edAmbientTo),
                getDataFromEditText(binding.edPanFrom),
                getDataFromEditText(binding.edPanTo),
                getDataFromEditText(binding.edPcbFrom),
                getDataFromEditText(binding.edPcbTo),
                getDataFromEditText(binding.edOilFrom),
                getDataFromEditText(binding.edOilTo),
                getDataFromEditText(binding.edDeviceOnFrom).toInt(),
                getDataFromEditText(binding.edDeviceOnTo).toInt(),
                getDataFromEditText(binding.edDeviceOffFrom).toInt(),
                getDataFromEditText(binding.edDeviceOffTo).toInt(),
                getDataFromEditText(binding.edInductionONFrom).toInt(),
                getDataFromEditText(binding.edInductionONTo).toInt(),
                getDataFromEditText(binding.edMicrowaveONFrom).toInt(),
                getDataFromEditText(binding.edMicrowaveONTo).toInt(),
            )
            callbackSuccess.invoke(filterData)
            dismiss()
        }
    }

    private fun getDataFromEditText(edMicrowaveFrom: AppCompatEditText): Float {
        return if (edMicrowaveFrom.text?.isNotEmpty() == true) {
            edMicrowaveFrom.text.toString().toFloat()
        } else {
            0f
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