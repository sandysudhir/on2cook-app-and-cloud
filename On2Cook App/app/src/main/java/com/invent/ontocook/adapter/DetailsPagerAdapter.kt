package com.invent.ontocook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.item_setp_description.view.*

class DetailsPagerAdapter(var onUpdateTimeBarDuration: OnUpdateTimeBarDuration) :
    RecyclerView.Adapter<DetailsPagerAdapter.PagerVH>() {

    interface OnUpdateTimeBarDuration{
        fun onUpdateDuration(duration  : Int, isIncrement : Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailsPagerAdapter.PagerVH =
        PagerVH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setp_description, parent, false)
        )

    override fun getItemCount(): Int = 3

    override fun onBindViewHolder(holder: DetailsPagerAdapter.PagerVH, position: Int) = holder.itemView.run {
        val headerViewHolder = holder
        headerViewHolder.bindView(position)
    }

    inner class PagerVH(itemView: View) : RecyclerView.ViewHolder(itemView){
        var currentPos = 0
        fun bindView(pos: Int) {
            currentPos = pos
            itemView.tvPlusTenSecond.setOnClickListener {
                onUpdateTimeBarDuration.onUpdateDuration(10, isIncrement = true)
            }
            itemView.tvPlusThirtySecond.setOnClickListener {
                onUpdateTimeBarDuration.onUpdateDuration(30, isIncrement = true)
            }
            itemView.tvMinusTenSecond.setOnClickListener {
                onUpdateTimeBarDuration.onUpdateDuration(10, isIncrement = false)
            }
            itemView.tvMinusThirtySecond.setOnClickListener {
                onUpdateTimeBarDuration.onUpdateDuration(30, isIncrement = false)
            }
        }
    }
}

