package com.invent.ontocook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.models.IngredientsSteps

class AddStepIngredientsAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listOfQuickAccessItems = mutableListOf<IngredientsSteps>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_add_step_ingredients, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfQuickAccessItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as AddStepIngredientsAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setQuickAccessList(listOfQuickAccessItems: MutableList<IngredientsSteps>, position: Int? = null) {
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
        }
    }
}