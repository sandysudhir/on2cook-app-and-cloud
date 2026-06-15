package com.invent.ontocook.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

abstract class PaginationListener(layoutManager: LinearLayoutManager) :
    RecyclerView.OnScrollListener() {

    private var linearLayoutManager: LinearLayoutManager? = layoutManager

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy > 0) {
            if (isLoading()) {
                val visibleItemCount = linearLayoutManager?.childCount
                val pastVisibleItems = linearLayoutManager?.findFirstVisibleItemPosition()
                val totalItemCount = linearLayoutManager?.itemCount
                if (visibleItemCount!! + pastVisibleItems!! >= totalItemCount!!) {
                    //Do pagination.. i.e. fetch new data
                    loadMore()
                }
            }
        }
    }

    abstract fun loadMore()
    abstract fun isLoading(): Boolean

}