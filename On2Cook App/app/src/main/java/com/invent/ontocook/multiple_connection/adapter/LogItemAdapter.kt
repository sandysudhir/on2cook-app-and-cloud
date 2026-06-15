package com.invent.ontocook.multiple_connection.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemLogBinding
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.multiple_connection.model.database.LogDataDb

class LogItemAdapter(
        var availableDevicesList: MutableList<LogDataDb>,
    var itemClickListener: ItemClickListener
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    lateinit var context: Context

    interface ItemClickListener {
        fun onItemClick(position: Int, recipeDb: BluetoothDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_log,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return availableDevicesList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun getList(): ArrayList<LogDataDb> {
        val list = ArrayList<LogDataDb>()
        list.addAll(availableDevicesList)
        return list
    }

    fun add(device: LogDataDb) {
        availableDevicesList.add(device)
        notifyItemInserted(availableDevicesList.size)
    }


    fun clear() {
        availableDevicesList.clear()
        notifyDataSetChanged()
    }

    inner class ContentViewHolder(var binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            binding.tvDeviceOnCounter.text = availableDevicesList[pos].deviceOnCounterTime
            binding.tvPower.text = "Power ${availableDevicesList[pos].power}"
            binding.tvIndPower.text = "IndPower ${availableDevicesList[pos].indCurrent}"
            binding.tvMagPower.text = "MagPower ${availableDevicesList[pos].magCurrent}"
            binding.tvMagTemp.text = "MagTemp ${availableDevicesList[pos].magTemp}"
            binding.tvCoilTemp.text = "CoilTemp ${availableDevicesList[pos].coilTemp}"
            binding.tvAmbientTemp.text = "AmbientTemp ${availableDevicesList[pos].ambientTemp}"
            binding.tvPanTemp.text = "PanTemp ${availableDevicesList[pos].panTemp}"
            binding.tvPcbTemp.text = "PcbTemp ${availableDevicesList[pos].pcbTemp}"
            binding.tvOilTemp.text = "OilTemp ${availableDevicesList[pos].oilTemp}"
            binding.tvDeviceOnTime.text = "DeviceOnTime ${availableDevicesList[pos].deviceOnTime}"
            binding.tvIndTime.text = "IndTime ${availableDevicesList[pos].indTime}"
            binding.tvMagTime.text = "MagTime ${availableDevicesList[pos].magTime}"
            if (availableDevicesList[pos].recipeName.isNotEmpty()) {
                binding.recipeDetail.viewShow()
                binding.tvRecipeName.text = "RecipeName ${availableDevicesList[pos].recipeName}"
                binding.tvStepNo.text = "StepNo ${availableDevicesList[pos].stepNo}"
                binding.tvStepName.text = "StepName ${availableDevicesList[pos].stepName}"
                binding.tvTotalSteps.text = "TotalSteps ${availableDevicesList[pos].totalSteps}"
                binding.tvRecipeTime.text = "RecipeTime ${availableDevicesList[pos].recipeTime}"
                binding.tvTimeRemains.text ="TimeRemains ${availableDevicesList[pos].elapsedTime}\n${availableDevicesList[pos].timeRemains}"
            } else {
                binding.recipeDetail.viewGone()
            }
        }
    }
}