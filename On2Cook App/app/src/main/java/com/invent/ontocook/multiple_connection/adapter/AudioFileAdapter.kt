package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemFilesBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.utils.Constants
import java.util.Locale

class AudioFileAdapter(
    var list: ArrayList<AudioFileModel>,
    val callBack: (AudioFileModel, Int) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    lateinit var context: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_files,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }


    inner class ContentViewHolder(var binding: ItemFilesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemFile = list[absoluteAdapterPosition]
            binding.tvItemName.text =
                if (itemFile.type == Constants.FILE_TYPE.STATIC) "s_${itemFile.fileName}" else itemFile.fileName
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

    private val dataFilter: Filter = object : Filter() {
        private var oriValue = list
        override fun performFiltering(constraint: CharSequence): FilterResults {
            val filteredList: MutableList<AudioFileModel> = ArrayList()
            if (constraint == null || constraint.isEmpty()) {
                Log.e("TAG", "performFiltering:If $constraint")
                filteredList.addAll(list)
            } else {
                Log.e("TAG", "performFiltering:Else $constraint")
                val filterPattern =
                    constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }

                val list = list.filter { it.fileName.contains(filterPattern, true) }
                filteredList.addAll(list)
//                for (item in list) {
//                    val list = item.fileName.filter { contains(filterPattern, true) }
//                    if (list.isNotEmpty()) {
//                        filteredList.add(
//                            AudioFileListModel(
//                                item.id,
//                                item.name,
//                                item.isSelected,
//                                list
//                            )
//                        )
//                    }
////                    item.fileModel.forEach { originModel ->
//////                        filteredList.add(item)
////                        Log.e("TAG", "performFiltering:Origin ${originModel.string}")
////                        Log.e("TAG", "performFiltering:FilterPAtten $filterPattern")
////                        lateinit var audioFileModel: AudioFileModel
////                        lateinit var fileList: ArrayList<FileModel>
////                        if (originModel.string.contains(filterPattern, true)) {
////                            if (!filteredList.any { it.id == item.id }) {
////                                Log.e("TAG", "performFiltering Adding:filteredList ${item.id}")
////                                fileList = ArrayList()
////                                audioFileModel = AudioFileModel(
////                                    item.id,
////                                    item.name,
////                                    item.isSelected,
////                                    fileList
////                                )
////                                filteredList.add(item)
//////                                item.fileModel.add(originModel)
////                            } else {
////
////                            }
////                        }
//////                        item.fileModel.clear()
////                    }
//                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results
        }

        override fun publishResults(constraint: CharSequence, results: FilterResults) {
            list.clear()
            val variantiConstructors =
                (results.values as? ArrayList<AudioFileModel?>)?.let { ArrayList(it) }
            Log.e("TAG", "performFiltering:Origin ${variantiConstructors.isNullOrEmpty()}")
            if (variantiConstructors.isNullOrEmpty()) {
                return
            }
            (results.values as? ArrayList<AudioFileModel>).let {
                list.addAll(results.values as ArrayList<AudioFileModel>)
                Log.e("TAG", "performFiltering:notifyDataSetChanged ")
                notifyDataSetChanged()
            }
        }
    }

    override fun getFilter(): Filter {
        return dataFilter
    }
}