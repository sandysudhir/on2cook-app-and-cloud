package com.invent.ontocook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.models.SubCategory
import kotlinx.android.synthetic.main.item_sub_categories_list.view.*

class SubCategoriesListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listOfSubcategories = listOf<SubCategory>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_sub_categories_list, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfSubcategories.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as SubCategoriesListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setSubCategoriesList(listOfSubcategories: List<SubCategory>, position: Int? = null) {
        this.listOfSubcategories = listOfSubcategories
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
            itemView.tvName.text = listOfSubcategories[currentPos].name
        }
    }
}