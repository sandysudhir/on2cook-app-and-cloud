package com.invent.ontocook.adapter

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.invent.ontocook.R
import com.invent.ontocook.create_recipe.AudioFileAdapter
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.databinding.ItemPrepareBinding
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.createAudioFile
import java.io.File
import java.io.IOException


class CreateIngredientsListAdapter(
    var context: Context, var listener: CreateIngredientsListAdapter.OnClick
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(){
    interface OnClick {
        fun onItemClick(index: Int)
        fun enableButton(b: Boolean)
        fun onOpenFilePicker(pos: Int)
    }

    private var listOfQuickAccessItems = mutableListOf<Ingredients>()
    private var listVisible = mutableListOf<Int>()
    private var spinnerAdapter: SpinnerAdapter? = null
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var unitAdapter: ArrayAdapter<String>
    private lateinit var typeAdapter: ArrayAdapter<String>

    init {
        spinnerAdapter = SpinnerAdapter(context, (context as CreateNewRecipe).qtyUnits)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_prepare,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfQuickAccessItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as CreateIngredientsListAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setList(
        listOfQuickAccessItems: MutableList<Ingredients>, position: Int? = null
    ) {
        this.listOfQuickAccessItems = listOfQuickAccessItems
        listOfQuickAccessItems.forEachIndexed { index, ingredients ->
            if (index == listOfQuickAccessItems.size - 1)
                listVisible.add(0)
            else listVisible.add(1)
        }
        if (position != null) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    fun open(index: Int) {
        listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
        listVisible[index] = 0
        notifyDataSetChanged()
    }

    fun add(step: Ingredients) {
        listOfQuickAccessItems.add(step)
        listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
        listVisible.add(0)
        listVisible.forEachIndexed { index, i -> Log.e("TAG", "add: listVisible$index Value $i") }
        notifyDataSetChanged()
    }

    fun getList(): ArrayList<Ingredients> {
        val list = ArrayList<Ingredients>()
        list.addAll(listOfQuickAccessItems)
        return list
    }

    fun getVisibleList(): ArrayList<Int> {
        val list = ArrayList<Int>()
        list.addAll(listVisible)
        return list
    }

    var lastClickedPos = 0

    inner class ContentViewHolder(var binding: ItemPrepareBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var currentPos = 0
        fun bindView(pos: Int) {
            currentPos = absoluteAdapterPosition
            val item = listOfQuickAccessItems[absoluteAdapterPosition]
            Log.e(
                "Item",
                "listOfQuickAccessItems: $absoluteAdapterPosition :- ${Gson().toJson(item)}"
            )
            item.id = absoluteAdapterPosition + 1
            binding.etName.setText(listOfQuickAccessItems[absoluteAdapterPosition].title)
            binding.etDescription1.setText(listOfQuickAccessItems[absoluteAdapterPosition].text)
            if (listOfQuickAccessItems[absoluteAdapterPosition].image.isNotEmpty()) {
                val fileName = Constants.getFileName(
                    context, uri = Uri.parse(listOfQuickAccessItems[absoluteAdapterPosition].image)
                )
                binding.tvImageName.text = fileName
            } else {
                binding.tvImageName.text = ""
            }
            binding.etType.setText(item.audio)
            Log.e("weight", "weight: ${item.weight}")
            binding.qtySpinner.adapter = spinnerAdapter
            adapter = ArrayAdapter(
                context,
                R.layout.item_spinner_dropdown_text,
                (context as CreateNewRecipe).ingredients
            )
            unitAdapter = ArrayAdapter(
                context, R.layout.item_spinner_dropdown_text, (context as CreateNewRecipe).gmUnits
            )
            typeAdapter = ArrayAdapter(
                context, R.layout.item_spinner_dropdown_text, (context as CreateNewRecipe).typeUnits
            )

            binding.etName.setAdapter(adapter)
            binding.etQty.setAdapter(unitAdapter)
            binding.etType.setAdapter(typeAdapter)
//            binding.etIngAudio.setAdapter(audioIngredientsAdapter)
//            binding.etQtyAudio.setAdapter(audioQtyAdapter)
//            binding.etTypeAudio.setAdapter(audioActionAdapter)
//            binding.etType.threshold = 1
//            binding.etQty.threshold = 1
            binding.etName.threshold = 1

            binding.qtySpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    when (position) {
                        0 -> {
                            unitAdapter = ArrayAdapter(
                                context,
                                R.layout.item_spinner_dropdown_text,
                                (context as CreateNewRecipe).gmUnits
                            )
                        }

                        1 -> {
                            unitAdapter = ArrayAdapter(
                                context,
                                R.layout.item_spinner_dropdown_text,
                                (context as CreateNewRecipe).mlUnits
                            )
                        }

                        2 -> {
                            unitAdapter = ArrayAdapter(
                                context,
                                R.layout.item_spinner_dropdown_text,
                                (context as CreateNewRecipe).cupUnits
                            )
                        }

                        3 -> {
                            unitAdapter = ArrayAdapter(
                                context,
                                R.layout.item_spinner_dropdown_text,
                                (context as CreateNewRecipe).tspUnits
                            )
                        }

                        4 -> {
                            unitAdapter = ArrayAdapter(
                                context,
                                R.layout.item_spinner_dropdown_text,
                                (context as CreateNewRecipe).tbspUnits
                            )
                        }
                    }
                    binding.etQty.setAdapter(unitAdapter)
                    if (listOfQuickAccessItems[pos].weight.contains(" ")) {
                        listOfQuickAccessItems[pos].weight =
                            listOfQuickAccessItems[pos].weight.split(" ")[0] + " " + (context as CreateNewRecipe).qtyUnits[position]
                        listOfQuickAccessItems[pos].audioU =
                            (context as CreateNewRecipe).qtyUnits[binding.qtySpinner.selectedItemPosition]
                        item.app_audio =
                            item.audioP + " " + item.audioI + " " + item.audioQ + " " + item.audioU
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.e("TAG", "onItemSelected: ")
                }
            }
            if (listOfQuickAccessItems[pos].weight.contains(" ")) {
                binding.etQty.setText(listOfQuickAccessItems[pos].weight.split(" ")[0])
                (context as CreateNewRecipe).qtyUnits.mapIndexed { index, weight ->
                    if (weight == listOfQuickAccessItems[pos].weight.split(" ")[1]) {
                        binding.qtySpinner.setSelection(index)
                        return@mapIndexed
                    }
                }
            } else binding.etQty.setText("")
            var isType = false
            binding.etType.setOnFocusChangeListener { _, hasFocus ->
                isType = hasFocus
                if (hasFocus) {
                    binding.etType.showDropDown()
                } else {
                    binding.etType.dismissDropDown()
                }
            }
            binding.etType.setOnClickListener {
                binding.etType.showDropDown()
            }
            binding.etType.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isType) {
                        item.audio = s.toString()
                        item.audioP = s.toString()
                        item.app_audio =
                            item.audioP + " " + item.audioI + " " + item.audioQ + " " + item.audioU
                    }
                }
            })
            var isName = false
            binding.etName.setOnFocusChangeListener { v, hasFocus ->
                isName = hasFocus
                if (hasFocus) {
                    binding.etName.showDropDown()
                } else {
                    binding.etName.dismissDropDown()
                }
            }
            binding.etName.setOnClickListener {
                binding.etName.showDropDown()
            }
            Log.e("TAG", "bindView: isName$isName")

            binding.etName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isName) {
                        listOfQuickAccessItems[pos].title = s.toString()
                        listOfQuickAccessItems[pos].audioI = s.toString()
                        checkValid(pos)
                    }
                }
            })
            var isQty = false
            binding.etQty.setOnFocusChangeListener { v, hasFocus ->
                isQty = hasFocus
                if (hasFocus) {
                    binding.etQty.showDropDown()
                } else {
                    binding.etQty.dismissDropDown()
                }
            }


            binding.etQty.setOnClickListener {
                binding.etQty.showDropDown()
            }

            binding.etQty.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    Log.e("TAG", "afterTextChanged: ${binding.qtySpinner.selectedItem}")
                    if (isQty) {
                        listOfQuickAccessItems[pos].weight =
                            s.toString() + " " + (context as CreateNewRecipe).qtyUnits[binding.qtySpinner.selectedItemPosition]
                        listOfQuickAccessItems[pos].audioQ = s.toString()
                        listOfQuickAccessItems[pos].audioU =
                            (context as CreateNewRecipe).qtyUnits[binding.qtySpinner.selectedItemPosition]
                        checkValid(pos)
                    }
                }
            })

            var isDescription = false
            binding.etDescription1.setOnFocusChangeListener { v, hasFocus ->
                isDescription = hasFocus
            }
            binding.etDescription1.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isDescription) {
                        listOfQuickAccessItems[pos].text = s.toString()
                        checkValid(pos)
                    }
                }
            })

            binding.cl.requestFocus()
            binding.tvTitle.text = "Ingredients ${pos + 1}"

            binding.etDescription1.setText(listOfQuickAccessItems[pos].text)
            lastClickedPos = currentPos
            Log.e("TAG", "bindView: $pos Plus ${listVisible[pos]}")
            if (listVisible[pos] == 0) {
                binding.tvSubTitle.visibility = View.GONE
                binding.clIngItem.visibility = View.VISIBLE
            } else {
                if (item.title.isNotEmpty()) {
                    val upperString: String =
                        item.title.substring(0, 1).uppercase() + item.title.substring(1).lowercase()
                    binding.tvSubTitle.text = ":- $upperString"
                    binding.tvSubTitle.visibility = View.VISIBLE
                } else {
                    binding.tvSubTitle.visibility = View.GONE
                }
                binding.clIngItem.visibility = View.GONE
            }
            binding.rlTitle.setOnClickListener {
                if (binding.clIngItem.visibility == View.VISIBLE) {
//                    binding.clIngItem.visibility = View.GONE
                } else {
//                    if (checkValid()) {
                    listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
                    listVisible[pos] = 0
                    listener.onItemClick(lastClickedPos)
//                    lastClickedPos = currentPos
//                    binding.clIngItem.visibility = View.VISIBLE
                    notifyDataSetChanged()
//                    }
                }
            }
            binding.ivDelete.setOnClickListener {
                Constants.showAlertDialog(context,
                    "Delete Ingredient",
                    "Are you sure you want to delete ${item.title} ?",
                    { _, _ ->
                        if (listVisible[pos] == 0 && listVisible.size > 1) {
                            listVisible[pos - 1] = 0
                            notifyDataSetChanged()
                        } else {
                            notifyItemRangeChanged(pos, listOfQuickAccessItems.size)
                        }
                        listVisible.removeAt(pos)
                        listOfQuickAccessItems.removeAt(pos)
                    },
                    { _, _ ->

                    })
            }
            binding.btnChooseImage.setOnClickListener {
                listener.onOpenFilePicker(pos)
            }
        }

        private fun checkValid(): Boolean {
            var openPos = 0
            listVisible.forEachIndexed { index, _ ->
                if (listVisible[index] == 0) {
                    openPos = index
                }
            }
            return listOfQuickAccessItems[openPos].title.isNotEmpty() && listOfQuickAccessItems[openPos].weight.isNotEmpty() && listOfQuickAccessItems[openPos].text.isNotEmpty()
        }

        private fun checkValid(pos: Int): Boolean {
            listOfQuickAccessItems[pos].app_audio =
                listOfQuickAccessItems[pos].audioP + " " + listOfQuickAccessItems[pos].audioI + " " + listOfQuickAccessItems[pos].audioQ + " " + listOfQuickAccessItems[pos].audioU
            return if (listOfQuickAccessItems[pos].title.isNotEmpty() && listOfQuickAccessItems[pos].weight.isNotEmpty() /*&& listOfQuickAccessItems[pos].text.isNotEmpty()*/) {
                listener.enableButton(true)
                true
            } else {
                listener.enableButton(false)
                false
            }
        }
    }





}