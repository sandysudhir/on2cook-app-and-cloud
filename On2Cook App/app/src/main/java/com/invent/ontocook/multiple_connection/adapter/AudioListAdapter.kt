package com.invent.ontocook.multiple_connection.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ItemAudioFilesBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.model.AudioFileListModel
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.utils.DebugLog
import java.util.Locale


class AudioListAdapter(
    var list: ArrayList<AudioFileListModel>,
    val callBack: (AudioFileListModel, Int) -> Unit,
    val callBackItemChange: (AudioFileModel, Int) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {
    val filteredList = ArrayList<AudioFileListModel>()
    lateinit var context: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_audio_files,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun add(device: AudioFileListModel) {
        list.add(device)
        notifyItemInserted(list.size)
    }


    inner class ContentViewHolder(var binding: ItemAudioFilesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(pos: Int) {
            val itemFile = filteredList[absoluteAdapterPosition]
            binding.tvItemName.text = itemFile.name
//            val adapter = AudioFileAdapter(itemFile.fileModel)
//            { it, pos ->
//                callBackItemChange.invoke(it, pos)
//            }
            binding.checkBox.isChecked = itemFile.isSelected
//            binding.checkBox.setOnTouchListener { view, motionEvent ->  }
            binding.checkBox.setOnCheckedChangeListener { compoundButton, b ->
                when {
                    compoundButton.isPressed -> {
                        binding.clItem.performClick()
                    }
                }
                DebugLog.e("FileListAdapter Size $b")
//                itemFile.isSelected = b
//                itemFile.fileModel.forEach { it ->
//                    it.isSelected = b
//                }
//                callBack.invoke(itemFile, absoluteAdapterPosition)
//                notifyItemChanged(absoluteAdapterPosition)

            }
            binding.clItem.setSafeOnClickListener {
                itemFile.isSelected = !itemFile.isSelected
                itemFile.fileModel.forEach { it ->
                    it.isSelected = itemFile.isSelected
                }
                callBack.invoke(itemFile, absoluteAdapterPosition)
                notifyItemChanged(absoluteAdapterPosition)
            }
//            binding.rvLocalRecipe.adapter = adapter
        }
    }

    private val dataFilter: Filter = object : Filter() {
        private var oriValue = list
        override fun performFiltering(constraint: CharSequence): FilterResults {
            val filteredList: MutableList<AudioFileListModel> = ArrayList()
            if (constraint == null || constraint.isEmpty()) {
                Log.e("TAG", "performFiltering:If $constraint")
                filteredList.addAll(list)
            } else {
                Log.e("TAG", "performFiltering:Else $constraint")
                val filterPattern =
                    constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                for (item in list) {
                    val list = item.fileModel.filter { it.fileName.contains(filterPattern, true) }
                    if (list.isNotEmpty()) {
                        filteredList.add(
                            AudioFileListModel(
                                item.id,
                                item.name,
                                item.isSelected,
                                list
                            )
                        )
                    }
//                    item.fileModel.forEach { originModel ->
////                        filteredList.add(item)
//                        Log.e("TAG", "performFiltering:Origin ${originModel.string}")
//                        Log.e("TAG", "performFiltering:FilterPAtten $filterPattern")
//                        lateinit var audioFileModel: AudioFileModel
//                        lateinit var fileList: ArrayList<FileModel>
//                        if (originModel.string.contains(filterPattern, true)) {
//                            if (!filteredList.any { it.id == item.id }) {
//                                Log.e("TAG", "performFiltering Adding:filteredList ${item.id}")
//                                fileList = ArrayList()
//                                audioFileModel = AudioFileModel(
//                                    item.id,
//                                    item.name,
//                                    item.isSelected,
//                                    fileList
//                                )
//                                filteredList.add(item)
////                                item.fileModel.add(originModel)
//                            } else {
//
//                            }
//                        }
////                        item.fileModel.clear()
//                    }
                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results
        }

        override fun publishResults(constraint: CharSequence, results: FilterResults) {
            filteredList.clear()
                val variantiConstructors =
                    (results.values as? ArrayList<AudioFileListModel?>)?.let { ArrayList(it) }
                Log.e("TAG", "performFiltering:Origin ${variantiConstructors.isNullOrEmpty()}")
                if (variantiConstructors.isNullOrEmpty()) {
                    return
                }
                (results.values as? ArrayList<AudioFileListModel>).let {
                    filteredList.addAll(results.values as ArrayList<AudioFileListModel>)
                    Log.e("TAG", "performFiltering:notifyDataSetChanged ")
                    notifyDataSetChanged()
                }
        }
    }

    override fun getFilter(): Filter {
        return dataFilter
    }
}