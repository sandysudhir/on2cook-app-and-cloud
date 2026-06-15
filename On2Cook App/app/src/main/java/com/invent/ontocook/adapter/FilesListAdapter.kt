package com.invent.ontocook.adapter

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.utils.Constants
import kotlinx.android.synthetic.main.item_files_list.view.*
import kotlinx.android.synthetic.main.item_ingredients_list.view.tvItemName

class FilesListAdapter(var context : Context, var onFileDeleteActionListener: OnFileDeleteActionListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnFileDeleteActionListener{
        fun OnItemDelete(index : Int)
    }

    private var listOfFilesItems = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_files_list, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfFilesItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as FilesListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setFilesList(listOfFilesItems: MutableList<String>) {
        this.listOfFilesItems = listOfFilesItems
        notifyDataSetChanged()
    }

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var currentPos = 0
        fun bindView(pos: Int) {
            currentPos = pos

            itemView.tvItemName.text = listOfFilesItems[pos]
            itemView.ivDelete.setOnClickListener {
                Constants.showAlertDialog(context, "Delete File",
                    "Are you sure you want to delete ${listOfFilesItems[pos]}?",
                    DialogInterface.OnClickListener { dialog, which ->
                        onFileDeleteActionListener.OnItemDelete(pos)
                    },
                    DialogInterface.OnClickListener { dialog, which ->

                    })
            }
        }
    }
}