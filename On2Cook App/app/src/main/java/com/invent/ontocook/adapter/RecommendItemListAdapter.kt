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
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemRecommendItemListBinding
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog

class RecommendItemListAdapter(
    var listOfRecommended: MutableList<Recipe>,
    var callBack: (Int, Recipe) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    lateinit var context: Context


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_recommend_item_list,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfRecommended.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as RecommendItemListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun getList(): ArrayList<Recipe> {
        val list = ArrayList<Recipe>()
        list.addAll(listOfRecommended)
        return list
    }

    fun clearList(pageIndex: Int, size: Int) {
        if (pageIndex == 0)
            listOfRecommended.clear()
        else {
            listOfRecommended.subList(listOfRecommended.size - size, listOfRecommended.size).clear()
            notifyItemRangeRemoved(
                listOfRecommended.size - size, listOfRecommended.size
            )
        }
    }

    fun setModesList(listOfModes: List<Recipe>, position: Int? = null) {
//        this.listOfRecommended = listOfModes
        if (position != null) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    fun addAll(it: List<Recipe>) {
//        if (listOfRecommended.size > 10)
//            listOfRecommended.clear()
//        else {
//            listOfRecommended.subList(listOfRecommended.size - it.size, listOfRecommended.size).clear()
//            notifyItemRangeRemoved(
//                listOfRecommended.size - it.size, listOfRecommended.size
//            )
//        }
        this.listOfRecommended.clear()
        this.listOfRecommended.addAll(it)
//        DebugLog.e("List ..${listOfRecommended.size}")
//        DebugLog.e("List ${it.size}")

//        if (listOfRecommended.size > 10) {
//            notifyItemRangeInserted(
//                this.listOfRecommended.size - it.size,
//                this.listOfRecommended.size
//            )
//        } else
        notifyDataSetChanged()
    }

    fun clearList() {
        listOfRecommended.clear()
        notifyDataSetChanged()
    }

    fun updateList(list: ArrayList<Recipe>, notifyAdapterFromPosition: Int) {
        listOfRecommended = list
        Log.e("PrinceEWW>>>", "ItemInserted: from: $notifyAdapterFromPosition - to: ${listOfRecommended.size}")
        Log.e("PrinceEWW>>>", "listOfRecommended: $listOfRecommended")
        notifyItemRangeInserted(notifyAdapterFromPosition, listOfRecommended.size)
    }

    inner class ContentViewHolder(var binding: ItemRecommendItemListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(position: Int) {

            when (listOfRecommended[absoluteAdapterPosition].difficulty.lowercase()) {
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

            binding.tvType.text = listOfRecommended[absoluteAdapterPosition].description
            try {
//                Log.e("TAG", "bindView: ${listOfRecommended[absoluteAdapterPosition].imageUrl}")
//                Log.e("TAG", "bindView: $absoluteAdapterPosition")
                if (listOfRecommended[absoluteAdapterPosition].imageUrl.isNotEmpty()) {
                    binding.ivItem.setImageURI(Uri.parse(listOfRecommended[absoluteAdapterPosition].imageUrl))
//                Glide.with(itemView.context).load(Uri.parse(ingredientsList[absoluteAdapterPosition].image))
//                    .centerCrop().into(binding.ivItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (listOfRecommended[absoluteAdapterPosition].name.isNotEmpty())
                binding.tvItemName.text = listOfRecommended[absoluteAdapterPosition].name[0]
//            binding.tvTime.text = listOfRecommended[absoluteAdapterPosition].duration
            binding.tvDifficulty.text = listOfRecommended[absoluteAdapterPosition].difficulty
            binding.clItem.setOnClickListener {
                callBack.invoke(absoluteAdapterPosition, listOfRecommended[absoluteAdapterPosition])
            }
            try {
                binding.tvTime.text =
                    "${DateTimeHelper.getTimeInMinSec(listOfRecommended[absoluteAdapterPosition].Instruction.sumOf { it.durationInSec })} mins"
            } catch (e: Exception) {
                DebugLog.e("Error ${e.message}")
            }

        }
    }
}