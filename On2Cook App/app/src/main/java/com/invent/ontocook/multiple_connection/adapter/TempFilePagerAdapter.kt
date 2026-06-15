package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemFilesBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.FileModel

class TempFilePagerAdapter(val callBack: (FileModel, Int) -> Unit) :
    PagingDataAdapter<FileModel, RecyclerView.ViewHolder>(Comparator) {

    lateinit var context: Context
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as TempFilePagerAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun markItemAsRead(position: Int) {
//        snapshot()[position]!!.name = "true"
        notifyItemChanged(position)
    }

    fun getItemFrom(position: Int): FileModel? {
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
            if (itemFile.isSelected) {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_selected)
            } else {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_white)
            }
            binding.clItem.setSafeOnClickListener {
                itemFile.isSelected = !itemFile.isSelected
                callBack.invoke(itemFile, absoluteAdapterPosition)
                notifyItemChanged(absoluteAdapterPosition)
            }
            Log.e("Bind", "Item ID ${getItem(absoluteAdapterPosition)!!.id} Value :- ${getItem(absoluteAdapterPosition)!!.fileName}")
            if (getItem(absoluteAdapterPosition) != null && getItem(absoluteAdapterPosition)!!.fileName.isNotEmpty())
                binding.tvItemName.text = getItem(absoluteAdapterPosition)!!.fileName
        }
    }

    object Comparator : DiffUtil.ItemCallback<FileModel>() {

        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.id == newItem.id
        }
    }
}