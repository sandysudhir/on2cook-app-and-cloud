package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemFilesBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.FileModel
import com.invent.ontocook.utils.DebugLog
class FileListAdapter(var context: Context, val callBack: (FileModel, Int) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    private var listOfFilesItems = mutableListOf<FileModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_files, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfFilesItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun add(recipeList: ArrayList<FileModel>) {
        DebugLog.e("add")
        listOfFilesItems.addAll(recipeList)
    }


    inner class ContentViewHolder(var binding: ItemFilesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemFile = listOfFilesItems[pos]
            binding.tvItemName.text = listOfFilesItems[pos].fileName
            if (itemFile.isSelected) {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_selected)
            } else {
                binding.clItem.background =
                    ContextCompat.getDrawable(context, R.drawable.ic_round_corner_white)
            }
            binding.clItem.setSafeOnClickListener {
                itemFile.isSelected = !itemFile.isSelected
                callBack.invoke(itemFile, pos)
                notifyItemChanged(pos)
            }

        }
    }
}