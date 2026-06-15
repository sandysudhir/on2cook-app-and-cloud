package com.invent.ontocook.multiple_connection

import android.app.ListActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.recyclerview.widget.ListAdapter
import com.invent.ontocook.R
import java.util.Arrays
import java.util.Collections

@Suppress("DEPRECATION")
class FastScoll : ListActivity() {
    var fruitView: ListView? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fast_scoll)
        fruitView = findViewById<View>(android.R.id.list) as ListView
        fruitView!!.isFastScrollEnabled = true
        val fruits = resources.getStringArray(R.array.fruits_array)
        val fruitList = listOf(*fruits).sorted()
        listAdapter = ListAdapter(this, fruitList)
        fruitView!!.onItemClickListener = OnItemClickListener { parent, arg1, position, arg3 ->
            Log.e(
                "sushildlh",
                fruitList[position]
            )
        }
    }
}