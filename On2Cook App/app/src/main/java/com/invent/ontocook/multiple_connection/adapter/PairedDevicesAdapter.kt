package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemPairedDeviceBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import com.invent.ontocook.utils.withNotNull
import kotlinx.android.synthetic.main.item_paired_device.view.device_name_textView

class PairedDevicesAdapter(
    var pairedDevicesList: MutableList<PairedDeviceData>,
    var isEdit: Boolean = false,
    val callBack: (Int, Int, PairedDeviceData,editedName:String) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    lateinit var context: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_paired_device, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return pairedDevicesList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun getList(): ArrayList<PairedDeviceData> {
        val list = ArrayList<PairedDeviceData>()
        list.addAll(pairedDevicesList)
        return list
    }

    fun add(device: PairedDeviceData) {
        pairedDevicesList.add(device)
        notifyItemInserted(pairedDevicesList.size)
    }

    inner class ContentViewHolder(val binding: ItemPairedDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(currentPos: Int) {
            if (isEdit)
                binding.ivEdit.viewGone()
            binding.deviceNameTextView.setText(
                pairedDevicesList[absoluteAdapterPosition].name
            )
            //-------Need to change drawable according device is connected OR disconnected-------//
            val drawable = context.resources.getDrawable(if (pairedDevicesList[absoluteAdapterPosition].isConnected) R.drawable.bluetooth else R.drawable.bluetooth_red_24)
            binding.deviceNameTextView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

//            itemView.tvTime.text = "${listOfRecentItems[pos].duration} mins"

            binding.root.setSafeOnClickListener {
//                itemClickListener.onItemClick(absoluteAdapterPosition, pairedDevicesList[absoluteAdapterPosition])
            }
            binding.ivDelete.setSafeOnClickListener {
                pairedDevicesList.withNotNull {
                    callBack.invoke(
                        binding.ivDelete.id,
                        absoluteAdapterPosition,
                        pairedDevicesList[absoluteAdapterPosition],
                        itemView.device_name_textView.text.toString()
                    )
                }
            }
            if (pairedDevicesList[absoluteAdapterPosition].isEdit) {
                binding.ivEdit.setImageResource(R.drawable.ic_done)
                binding.deviceNameTextView.isEnabled = true
                binding.deviceNameTextView.isCursorVisible = true
                binding.deviceNameTextView.backgroundTintList =
                    ColorStateList.valueOf(context.getColor(R.color.dark_gray))
            } else {
                binding.deviceNameTextView.isEnabled = false
                binding.deviceNameTextView.isCursorVisible = false
                binding.deviceNameTextView.backgroundTintList =
                    ColorStateList.valueOf(context.getColor(R.color.transparent))
                binding.ivEdit.setImageResource(R.drawable.ic_edit)
            }
            binding.ivEdit.setSafeOnClickListener {
                if (pairedDevicesList[absoluteAdapterPosition].isEdit) {
                    pairedDevicesList[absoluteAdapterPosition].isEdit = false
                    callBack.invoke(
                        binding.ivEdit.id,
                        absoluteAdapterPosition,
                        pairedDevicesList[absoluteAdapterPosition],
                        itemView.device_name_textView.text.toString()
                    )
                } else {
                    pairedDevicesList[absoluteAdapterPosition].isEdit = true
                    pairedDevicesList[absoluteAdapterPosition].name =
                        itemView.device_name_textView.text.toString()
                    callBack.invoke(
                        binding.ivEdit.id,
                        absoluteAdapterPosition,
                        pairedDevicesList[absoluteAdapterPosition],
                        itemView.device_name_textView.text.toString()
                    )
                }
            }
        }
    }
}