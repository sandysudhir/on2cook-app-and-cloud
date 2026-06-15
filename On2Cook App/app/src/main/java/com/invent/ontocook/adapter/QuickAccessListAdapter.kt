package com.invent.ontocook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.models.QuickAccess
import kotlinx.android.synthetic.main.item_quick_access_list.view.*

class QuickAccessListAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listOfQuickAccessItems = listOf<QuickAccess>()

//    interface SettingsListAdapterListener {
//        fun onChangeCheckStatus(position: Int, isChecked: Boolean)
//        fun onSettingClick(position: Int)
//    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_quick_access_list, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfQuickAccessItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as QuickAccessListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setQuickAccessList(listOfQuickAccessItems: List<QuickAccess>, position: Int? = null) {
        this.listOfQuickAccessItems = listOfQuickAccessItems
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

            itemView.ivStart.setBackgroundResource(listOfQuickAccessItems[currentPos].image)
            itemView.tvName.text = listOfQuickAccessItems[currentPos].name
            itemView.tvDesc.text = listOfQuickAccessItems[currentPos].desc
        }
    }
}