package com.invent.ontocook.multiple_connection.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemDeviceBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.utils.DebugLog

class AvailableDevicesAdapter(
    var availableDevicesList: MutableList<BluetoothDevice>,
    val callBack: (Int, BluetoothDevice) -> Unit
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
                R.layout.item_device, parent, false
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

    fun getList(): ArrayList<BluetoothDevice> {
        val list = ArrayList<BluetoothDevice>()
        list.addAll(availableDevicesList)
        return list
    }

    fun add(device: BluetoothDevice) {
        availableDevicesList.add(device)
        notifyItemInserted(availableDevicesList.size)
    }

    fun remove(device: BluetoothDevice) {
        availableDevicesList.forEachIndexed { index, bluetoothDevice ->
            if (bluetoothDevice.address == device.address)
                notifyItemRemoved(index)
        }
    }

    fun clear() {
        availableDevicesList.clear()
        notifyDataSetChanged()
    }

    inner class ContentViewHolder(var binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            binding.deviceNameTextView.text = "${availableDevicesList[absoluteAdapterPosition].name}"
//            itemView.tvTime.text = "${listOfRecentItems[pos].duration} mins"

            binding.root.setSafeOnClickListener {
                try {
                    DebugLog.e("setSafeOnClickListener$absoluteAdapterPosition Size.. ${availableDevicesList.size}")
                    callBack.invoke(absoluteAdapterPosition, availableDevicesList[absoluteAdapterPosition])
                } catch (e: Exception) {
                    DebugLog.e("$e :-${e.message}")
                }
            }
        }
    }
}