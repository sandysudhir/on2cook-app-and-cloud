package com.invent.ontocook.dialog

import android.os.Bundle
import com.invent.ontocook.R
import com.invent.ontocook.adapter.ChooseDeviceForSetupAdapter
import com.invent.ontocook.base.BaseDialogFragment
import com.invent.ontocook.databinding.DialogChooseDeviceForSetupBinding
import com.invent.ontocook.extension.setWidthPercent
import com.invent.ontocook.multiple_connection.model.PairedDeviceData

class ChooseDeviceForSetupDialog : BaseDialogFragment<DialogChooseDeviceForSetupBinding>(
    DialogChooseDeviceForSetupBinding::inflate, isCancelable = true
) {

    var deviceForSetupItemClickCallback: ((Int, PairedDeviceData) -> Unit)? = null

    var devicesList: ArrayList<PairedDeviceData> = arrayListOf()
    private val chooseDeviceForSetupAdapter: ChooseDeviceForSetupAdapter by lazy {
        ChooseDeviceForSetupAdapter(
            devicesList = devicesList,
            deviceItemClickCallback = ::deviceItemClickCallback
        )
    }

    private fun deviceItemClickCallback(position: Int, dataBean: PairedDeviceData) {
        deviceForSetupItemClickCallback?.invoke(position, dataBean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun initControl() {

//        setWidthPercent(90)

        binding.recyclerViewChooseDeviceForSetup.adapter = chooseDeviceForSetupAdapter

        binding.imageViewBack.setOnClickListener {
            dismiss()
        }

    }

    companion object {
        fun newInstance(devicesArrayList: ArrayList<PairedDeviceData>) = ChooseDeviceForSetupDialog().apply {
            devicesList = devicesArrayList
        }
    }

}