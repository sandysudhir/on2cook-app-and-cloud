package com.invent.ontocook.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemRecentItemListBinding
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog
import java.util.ArrayList

class RecentItemListAdapter(itemClickListener: RecentItemListAdapter.ItemClickListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var itemClickListener = itemClickListener
    private var listOfRecentItems = listOf<RecentlyPlayedRecipe>()
    lateinit var context: Context

    interface ItemClickListener {
        fun onItemClick(position: Int, recentlyPlayedRecipe: RecentlyPlayedRecipe)
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
        val headerViewHolder = holder as RecentItemListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setRecentItemList(listOfRecentItems: List<RecentlyPlayedRecipe>, position: Int? = null) {
        Log.e("PrinceEWW>>>", "listOfRecentItems - ${listOfRecentItems.size}")
        this.listOfRecentItems = listOfRecentItems
        if (position != null) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    fun getItemList() = if (listOfRecentItems.isNotEmpty())listOfRecentItems as ArrayList<RecentlyPlayedRecipe> else arrayListOf()

    /*//-------For clear list-------//
    fun clearRecentItemList() {
        this.listOfRecentItems = listOf<RecentlyPlayedRecipe>()
        notifyDataSetChanged()
    }*/

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
            binding.tvDifficulty.text = listOfRecentItems[absoluteAdapterPosition].difficulty
            try {
                binding.tvTime.text =
                    "${DateTimeHelper.getTimeInMinSec(listOfRecentItems[absoluteAdapterPosition].Instruction.sumOf { it.durationInSec })} mins"
            } catch (e: Exception) {
                DebugLog.e("Error ${e.message}")
            }

            binding.clItem.setOnClickListener {
                itemClickListener.onItemClick(
                    absoluteAdapterPosition,
                    listOfRecentItems[absoluteAdapterPosition]
                )
            }
        }
    }
}