package com.invent.ontocook.create_recipe

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.invent.ontocook.R
import com.invent.ontocook.multiple_connection.model.AudioFileListModel
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import java.util.Locale

class AudioFileAdapter(
    @get:JvmName("getAdapterContext")
    var context: Context,
    var resource: Int,
    var textViewResourceId: Int,
    var items: List<AudioFileModel>
) : ArrayAdapter<AudioFileModel?>(
    context, resource, textViewResourceId, items
) {
    var tempItems: ArrayList<AudioFileModel> = ArrayList(items)
    var suggestions: MutableList<AudioFileModel> = ArrayList()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (convertView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.item_dropdown_text, parent, false)
        }
        val people = items[position]
        val lblName = view!!.findViewById<View>(R.id.tv) as TextView
        lblName.text = people.fileName
        return view
    }

    override fun getFilter(): Filter {
        return nameFilter
    }

    /**
     * Custom Filter implementation for custom suggestions we provide.
     */

    private val nameFilter: Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence): FilterResults {
            val filteredList: MutableList<AudioFileModel> = ArrayList()
            if (constraint == null || constraint.isEmpty()) {
                Log.e("TAG", "performFiltering:If $constraint")
                filteredList.addAll(tempItems)
            } else {
                Log.e("TAG", "performFiltering:Else $constraint")
                val filterPattern =
                    constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                for (people in tempItems) {
                    if (people.fileName.lowercase(Locale.getDefault()).contains(
                            constraint.toString().lowercase(
                                Locale.getDefault()
                            )
                        )
                    ) {
                        filteredList.add(people)
                        suggestions.add(people)
                    }
                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results
        }

        override fun publishResults(constraint: CharSequence, results: FilterResults) {
            tempItems.clear()
            val variantiConstructors =
                (results.values as? ArrayList<AudioFileModel?>)?.let { ArrayList(it) }
            Log.e("TAG", "performFiltering:Origin ${variantiConstructors.isNullOrEmpty()}")
            if (variantiConstructors.isNullOrEmpty()) {
                return
            }
            (results.values as? ArrayList<AudioFileModel>).let {
                tempItems.addAll(results.values as ArrayList<AudioFileModel>)
                Log.e("TAG", "performFiltering:notifyDataSetChanged ")
                notifyDataSetChanged()
            }
        }
    }

//    private var nameFilter: Filter = object : Filter() {
//        override fun convertResultToString(resultValue: Any): CharSequence {
//            return (resultValue as AudioFileModel).fileName
//        }
//
//        override fun performFiltering(constraint: CharSequence): FilterResults {
//            return run {
//                suggestions.clear()
//                for (people in tempItems) {
//                    if (people.fileName.lowercase(Locale.getDefault()).contains(
//                            constraint.toString().lowercase(
//                                Locale.getDefault()
//                            )
//                        )
//                    ) {
//                        suggestions.add(people)
//                    }
//                }
//                val filterResults = FilterResults()
//                filterResults.values = suggestions
//                filterResults.count = suggestions.size
//                filterResults
//            }
//        }
//
//        override fun publishResults(constraint: CharSequence, results: FilterResults) {
//            clear()
//            val filterList =
//                (results.values as? ArrayList<AudioFileModel?>)?.let { ArrayList(it) }
//            Log.e("TAG", "performFiltering:Origin ${filterList.isNullOrEmpty()}")
//            if (filterList.isNullOrEmpty()) {
//                return
//            }
//            (results.values as? ArrayList<AudioFileListModel>).let {
//                for (people in filterList) {
//                    add(people)
//                    notifyDataSetChanged()
//                }
//            }
//        }
//    }

    init {
        // this makes the difference.
    }
}