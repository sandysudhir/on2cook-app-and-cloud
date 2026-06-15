package com.invent.ontocook.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemRecentItemListBinding
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog

class AddedRecipeAdapter(itemClickListener: AddedRecipeAdapter.ItemClickListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var itemClickListener = itemClickListener
    private var listOfRecentItems = listOf<Recipe>()
    lateinit var context: Context

    interface ItemClickListener {
        fun onItemClick(position: Int, recipe: Recipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_recent_item_list,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfRecentItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as AddedRecipeAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setRecentItemList(listOfRecentItems: List<Recipe>, position: Int? = null) {
        this.listOfRecentItems = listOfRecentItems
        Log.e("TAG", "setRecentItemList: ${listOfRecentItems.size}")
        if (position != null) {
            notifyItemChanged(position)
        } else {
            Log.e("TAG", "setRecentItemList: Else")
            notifyDataSetChanged()
        }
    }

    fun getList(): ArrayList<Recipe> {
        val list = ArrayList<Recipe>()
        list.addAll(listOfRecentItems)
        return list
    }

    inner class ContentViewHolder(var binding: ItemRecentItemListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindView(pos: Int) {

            when (listOfRecentItems[absoluteAdapterPosition].difficulty.lowercase()) {
                "medium" -> {
                    binding.tvDifficulty.background.colorFilter =
                        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                            ContextCompat.getColor(
                                context,
                                R.color.orange
                            ), BlendModeCompat.SRC_IN
                        )
                }

                "hard" -> {
                    binding.tvDifficulty.background.colorFilter =
                        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                            ContextCompat.getColor(
                                context,
                                R.color.colorFlamColor
                            ), BlendModeCompat.SRC_IN
                        )
                }

                "easy" -> {
                    binding.tvDifficulty.background.colorFilter =
                        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                            ContextCompat.getColor(
                                context,
                                R.color.colorMWColor
                            ), BlendModeCompat.SRC_IN
                        )
                }
            }

            binding.tvDifficulty.text = listOfRecentItems[absoluteAdapterPosition].difficulty
            try {
                Log.e("TAG", "bindView: ${listOfRecentItems[absoluteAdapterPosition].imageUrl}")
                Log.e("TAG", "bindView: $absoluteAdapterPosition")
                if (listOfRecentItems[absoluteAdapterPosition].imageUrl.isNotEmpty()) {
                    Log.e("TAG", "bindView: $absoluteAdapterPosition")
                    Glide.with(context)
                        .load(Uri.parse(listOfRecentItems[absoluteAdapterPosition].imageUrl))
                        .into(binding.ivItem)
                } else {
                    Glide.with(context)
                        .load(R.drawable.placeholder)
                        .into(binding.ivItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (listOfRecentItems[absoluteAdapterPosition].name.isNotEmpty())
                binding.tvItemName.text = listOfRecentItems[absoluteAdapterPosition].name[0]
//            itemView.tvTime.text = "${listOfRecentItems[absoluteAdapterPosition].duration} mins"
            try {
                binding.tvTime.text =
                    "${DateTimeHelper.getTimeInMinSec(listOfRecentItems[absoluteAdapterPosition].Instruction.sumOf { it.durationInSec })} mins"
            } catch (e: Exception) {
                DebugLog.e("Error ${e.message}")
            }

            binding.root.setOnClickListener {
                itemClickListener.onItemClick(
                    absoluteAdapterPosition,
                    listOfRecentItems[absoluteAdapterPosition]
                )
            }
        }
    }
}