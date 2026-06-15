package com.invent.ontocook.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.invent.ontocook.R
import com.invent.ontocook.base.BaseAdapterWithViewBinding
import com.invent.ontocook.databinding.ItemChooseDeviceForSetupBinding
import com.invent.ontocook.databinding.LayoutLoadingItemBinding
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import com.invent.ontocook.utils.onSafeClick

class ChooseDeviceForSetupAdapter(
    var devicesList: ArrayList<PairedDeviceData>,
    var deviceItemClickCallback: ((Int, PairedDeviceData) -> Unit),
) : BaseAdapterWithViewBinding(devicesList) {

    private lateinit var context: Context
    override fun getItemViewType(position: Int): Int = R.layout.item_choose_device_for_setup

    override fun getViewBinding(viewType: Int, parent: ViewGroup): ViewBinding {
        context = parent.context
        return when (viewType) {
            R.layout.item_choose_device_for_setup -> {
                ItemChooseDeviceForSetupBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            }

            else -> {
                LayoutLoadingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            }
        }
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        if (holder.itemViewType == R.layout.item_choose_device_for_setup) {
            val binding = holder.binding as ItemChooseDeviceForSetupBinding
            val dataBean: PairedDeviceData = devicesList[position]
            binding.apply {
                textViewDeviceName.text = dataBean.name

                root.onSafeClick {
                    deviceItemClickCallback.invoke(holder.absoluteAdapterPosition, dataBean)
                }
            }
        }
    }
}