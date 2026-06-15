package com.invent.ontocook.base

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

abstract class BaseAdapterWithViewBinding(private val items: ArrayList<out Any?>) :
    RecyclerView.Adapter<BaseAdapterWithViewBinding.ItemViewHolder>() {

    protected abstract fun getViewBinding(viewType: Int, parent: ViewGroup): ViewBinding

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ItemViewHolder(getViewBinding(viewType, parent))

    class ItemViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
}