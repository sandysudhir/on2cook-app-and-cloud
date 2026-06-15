package com.invent.ontocook.adapter

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.invent.ontocook.R
import com.invent.ontocook.models.IngredientsSteps
import kotlinx.android.synthetic.main.activity_recipe_details2.*
import kotlinx.android.synthetic.main.item_ingredients_list.view.*
import java.io.File
import java.util.Locale

class IngredientsListAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listOfQuickAccessItems = mutableListOf<IngredientsSteps>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_ingredients_list, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfQuickAccessItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as IngredientsListAdapter.ContentViewHolder
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
    private fun String.toIngredientTitleCase(): String {
        return this.trim()
            .lowercase(Locale.getDefault())
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.substring(0, 1).uppercase(Locale.getDefault()) + word.substring(1)
            }
    }

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var currentPos = 0
        fun bindView(pos: Int) {
            currentPos = pos
            if (listOfQuickAccessItems[pos].image.isNotEmpty()) {
                itemView.ivItem.setImageURI(Uri.parse(listOfQuickAccessItems[pos].image))
//                Glide.with(itemView.context).load(Uri.parse(ingredientsList[pos].image))
//                    .centerCrop().into(binding.ivItem)
            }
            itemView.tvItemName.text = listOfQuickAccessItems[pos].name.toIngredientTitleCase()
            itemView.tvQty.text = listOfQuickAccessItems[pos].qty
            itemView.tvDesc.text = listOfQuickAccessItems[pos].desc
        }
    }
}