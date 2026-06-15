package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemCookingIngredientsBinding
import com.invent.ontocook.models.Ingredients

class CookingIngredientsAdapter(
    var ingredientsList: MutableList<Ingredients>,
    var itemClickListener: ItemClickListener
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var loading = true
    var pastVisiblesItems = 0
    var visibleItemCount: kotlin.Int = 0
    var totalItemCount: kotlin.Int = 0
    lateinit var context: Context
    var pageIndex = 1

    interface ItemClickListener {
        fun onItemClick(position: Int, recipeDb: Ingredients)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_cooking_ingredients,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return ingredientsList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun getList(): ArrayList<Ingredients> {
        val list = ArrayList<Ingredients>()
        list.addAll(ingredientsList)
        return list
    }

    fun add(device: Ingredients) {
        ingredientsList.add(device)
        notifyItemInserted(ingredientsList.size)
    }

    fun setRecentItemList(listOfRecentItems: MutableList<Ingredients>, position: Int? = null) {
        this.ingredientsList = listOfRecentItems
        Log.e("TAG", "setRecentItemList: ${listOfRecentItems.size}")
        if (position != null) {
            notifyItemChanged(position)
        } else {
            Log.e("TAG", "setRecentItemList: Else")
            notifyDataSetChanged()
        }
    }

    fun clear() {
        ingredientsList.clear()
        notifyDataSetChanged()
    }

    fun addSamples(it: List<Ingredients>) {
        ingredientsList.addAll(it)
        if (ingredientsList.isNotEmpty() && ingredientsList.size > 10)
            notifyItemRangeInserted(ingredientsList.size - 10, ingredientsList.size)
        else
            notifyDataSetChanged()
    }


    inner class ContentViewHolder(var binding: ItemCookingIngredientsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemLog = ingredientsList[absoluteAdapterPosition]
            binding.tvItemName.text = itemLog.title
            binding.tvQty.text = itemLog.weight
            binding.tvDesc.text = itemLog.text
            if (ingredientsList[absoluteAdapterPosition].image.isNotEmpty()) {
                binding.ivItem.setImageURI(Uri.parse(ingredientsList[absoluteAdapterPosition].image))
//                Glide.with(itemView.context).load(Uri.parse(ingredientsList[pos].image))
//                    .centerCrop().into(binding.ivItem)
            }

        }
    }
}