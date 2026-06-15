package com.invent.ontocook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.models.Modes
import kotlinx.android.synthetic.main.item_modes_item_list.view.*

class ModesItemListAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listOfModes = listOf<Modes>()

//    interface SettingsListAdapterListener {
//        fun onChangeCheckStatus(position: Int, isChecked: Boolean)
//        fun onSettingClick(position: Int)
//    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_modes_item_list, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfModes.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ModesItemListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setModesList(listOfModes: List<Modes>, position: Int? = null) {
        this.listOfModes = listOfModes
        if (position != null) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var currentPos = 0
        fun bindView(pos: Int) {
            currentPos = pos

            itemView.ivModes.setImageResource(listOfModes[currentPos].image)
            itemView.tvName.text = listOfModes[currentPos].name
        }
    }
}