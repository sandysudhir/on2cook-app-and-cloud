package com.invent.ontocook.multiple_connection.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemLogDataBinding
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import com.invent.ontocook.utils.Constants.RoundTwo
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog

class LogDataAdapter(
    var availableDevicesList: MutableList<LogDataDb>,
    var itemClickListener: ItemClickListener
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var loading = true
    var pastVisiblesItems = 0
    var visibleItemCount: Int = 0
    var totalItemCount: Int = 0
    lateinit var context: Context
    var pageIndex = 1

    interface ItemClickListener {
        fun onItemClick(position: Int, recipeDb: BluetoothDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_log_data,
                parent,
                false
            )
//            LayoutInflater.from(parent.context).inflate(
//                R.layout.item_log_data, parent, false
//            )
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

    fun setRecentItemList(listOfRecentItems: MutableList<LogDataDb>, position: Int? = null) {
        this.availableDevicesList = listOfRecentItems
        Log.e("TAG", "setRecentItemList: ${listOfRecentItems.size}")
        if (position != null) {
            notifyItemChanged(position)
        } else {
            Log.e("TAG", "setRecentItemList: Else")
            notifyDataSetChanged()
        }
    }

    fun clear() {
        availableDevicesList.clear()
        notifyDataSetChanged()
    }

    fun addSamples(it: List<LogDataDb>) {
        availableDevicesList.addAll(it)
        if (availableDevicesList.isNotEmpty() && availableDevicesList.size > 10)
            notifyItemRangeInserted(availableDevicesList.size - 10, availableDevicesList.size)
        else
            notifyDataSetChanged()
    }


    inner class ContentViewHolder(var binding: ItemLogDataBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemLog = availableDevicesList[pos]
            DebugLog.e("Subscribe Check pageIndex Adapter $pos Counter ${itemLog.deviceOnCounterTime}")
            if (pos == 0 || itemLog.date != availableDevicesList[pos - 1].date) {
                binding.tvDate.viewShow()
                binding.tvDate.text = itemLog.date
            } else
                binding.tvDate.viewGone()
            binding.tvCurrent.text =
                "Voltage:- ${itemLog.power} , Ind:- ${itemLog.indCurrent.RoundTwo()} Amps ,Mag:- ${itemLog.magCurrent.RoundTwo()} Amps"
            binding.tvTemp.text =
                "Igbt:- ${itemLog.igbtTemp.RoundTwo()}°C, Glass:- ${itemLog.glassTemp.RoundTwo()}°C , Mag:- ${itemLog.magTemp.RoundTwo()}°C , " +
//                        "Coil:- ${itemLog.coilTemp.RoundTwo()}°C ," +
                        " Pan:- ${itemLog.panTemp.RoundTwo()}°C "
//            , Pcb:- ${itemLog.pcbTemp.RoundTwo()}°C "
            binding.tvTime.text =
                "Device On:- ${DateTimeHelper.getTimeCheck(itemLog.deviceOnTime)} , Ind:- ${
                    DateTimeHelper.getTimeCheck(
                        itemLog.indTime
                    )
                } , Mag:- ${DateTimeHelper.getTimeCheck(itemLog.magTime)} , Device Off:- ${
                    DateTimeHelper.getTimeCheck(
                        itemLog.deviceOffTime
                    )
                }"

            if (itemLog.recipeName.isNotEmpty() && itemLog.recipeName != "0") {
                binding.tvRecipe.viewShow()
                binding.tvRecipe.text =
                    "Name:- ${itemLog.recipeName} , StepNo:- ${itemLog.stepNo} ," +
//                            " /*StepName:- ${itemLog.stepName} , */" +
                            "TotalSteps:- ${itemLog.totalSteps} , " +
//                            "/*RecipeTime:- ${itemLog.recipeTime} , */" +
                            "TimeRemains:- ${itemLog.timeRemains}"
            } else {
                binding.tvRecipe.viewGone()
            }
            binding.tvDeviceOnCounter.text = itemLog.deviceOnCounterTime
//            binding.tvPower.text = "Power ${itemLog.power}"
//            binding.tvIndPower.text = "IndPower ${itemLog.indCurrent}"
//            binding.tvMagPower.text = "MagPower ${itemLog.magCurrent}"
//            binding.tvMagTemp.text = "MagTemp ${itemLog.magTemp}"
//            binding.tvCoilTemp.text = "CoilTemp ${itemLog.coilTemp}"
//            binding.tvAmbientTemp.text = "AmbientTemp ${itemLog.ambientTemp}"
//            binding.tvPanTemp.text = "PanTemp ${itemLog.panTemp}"
//            binding.tvPcbTemp.text = "PcbTemp ${itemLog.pcbTemp}"
//            binding.tvOilTemp.text = "OilTemp ${itemLog.oilTemp}"
//            binding.tvDeviceOnTime.text = "DeviceOnTime ${itemLog.deviceOnTime}"
//            binding.tvIndTime.text = "IndTime ${itemLog.indTime}"
//            binding.tvMagTime.text = "MagTime ${itemLog.magTime}"
//            if (itemLog.recipeName.isNotEmpty()&& itemLog.recipeName != "0") {
//                binding.recipeDetail.viewShow()
//                binding.tvRecipeName.text = "RecipeName ${itemLog.recipeName}"
//                binding.tvStepNo.text = "StepNo ${itemLog.stepNo}"
//                binding.tvStepName.text = "StepName ${itemLog.stepName}"
//                binding.tvTotalSteps.text = "TotalSteps ${itemLog.totalSteps}"
//                binding.tvRecipeTime.text = "RecipeTime ${itemLog.recipeTime}"
//                binding.tvTimeRemains.text =
//                    "TimeRemains ${itemLog.elapsedTime}\n${itemLog.timeRemains}"
//            } else {
//                binding.recipeDetail.viewGone()
//            }
        }
    }
}