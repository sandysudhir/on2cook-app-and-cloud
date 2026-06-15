package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemFilesBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.database.Recipe

class LocalRecipeAdapter(val callBack: (Recipe, Int) -> Unit) :
    PagingDataAdapter<Recipe, RecyclerView.ViewHolder>(Comparator) {

    lateinit var context: Context
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as LocalRecipeAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun markItemAsRead(position: Int) {
//        snapshot()[position]!!.name = "true"
        notifyItemChanged(position)
    }

    fun getItemFrom(position: Int): Recipe? {
        return getItem(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_files, parent, false
            )
        )
    }

    inner class ContentViewHolder(var binding: ItemFilesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemFile = getItem(absoluteAdapterPosition) ?: return
            binding.tvItemName.text = itemFile.name[0]
            if (itemFile.isSelected) {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_selected)
            } else {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_white)
            }
            binding.clItem.setSafeOnClickListener {
                if (itemFile.name[0].length < 40) {
                    itemFile.isSelected = !itemFile.isSelected
                    callBack.invoke(itemFile, absoluteAdapterPosition)
                    notifyItemChanged(absoluteAdapterPosition)
                } else
                    Toast.makeText(context, context.getString(R.string.strEditRecipe), Toast.LENGTH_SHORT).show()
            }
            Log.e(
                "Bind",
                "Item ID ${getItem(absoluteAdapterPosition)!!.id} Value :- ${
                    getItem(absoluteAdapterPosition)!!.name[0]
                }"
            )
            if (getItem(absoluteAdapterPosition) != null && getItem(absoluteAdapterPosition)!!.name.isNotEmpty())
                binding.tvItemName.text = getItem(absoluteAdapterPosition)!!.name[0]
        }
    }

    object Comparator : DiffUtil.ItemCallback<Recipe>() {

        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem.id == newItem.id
        }
    }
}