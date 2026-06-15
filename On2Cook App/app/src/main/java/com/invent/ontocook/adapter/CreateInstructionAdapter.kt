package com.invent.ontocook.adapter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.invent.ontocook.R
import com.invent.ontocook.create_recipe.AudioFileAdapter
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.databinding.ItemInstructionBinding
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.record.MP3Recorder
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.MinMaxFilter
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.changeBackgroundTint
import com.invent.ontocook.utils.createAudioFile
import com.invent.ontocook.utils.gone
import com.invent.ontocook.utils.goneIfOrVisible
import com.invent.ontocook.utils.notNullAndNotEmpty
import com.invent.ontocook.utils.openPermissionSettings
import com.invent.ontocook.utils.visible
import com.invent.ontocook.utils.withNotNull
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


class CreateInstructionAdapter(
    val context: Context,
    val createRecipeScreenFlowType: Constants.CreateRecipeScreenFlowType?,
    var listener: CreateInstructionAdapter.OnClick,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), MediaPlayer.OnPreparedListener {

    interface OnClick {
        fun OnItemClick(index: Int)
        fun enableButton(btnEnable: Boolean)
    }

    private var listOfQuickAccessItems = mutableListOf<Instructions>()
    internal var listVisible = mutableListOf<Int>()

    var time = arrayOf("sec", "min")
    var severity = arrayOf("high", "low")
    var lid = arrayOf("close", "open")
    var spinnerStirrerList = arrayOf("Off", "Low", "Med", "High", "V High")
    private var spinnerAdapter: SpinnerAdapter? = null
    private val record: MP3Recorder by lazy {
        com.invent.ontocook.record.MP3Recorder()
    }
    private var severityAdapter: SpinnerAdapter? = null
    private var lidAdapter: SpinnerAdapter? = null
    private var spinnerStirrerAdapter: SpinnerAdapter? = null
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var unitAdapter: ArrayAdapter<String>
    private lateinit var typeAdapter: ArrayAdapter<String>
    private var strIngAudioList = listOf<String>()
    private var strQtyAudioList = listOf<String>()
    private var strTypeAudioList = listOf<String>()
    private val qtyAudioFileList by lazy { mutableListOf<AudioFileModel>() }
    private val actionAudioFileList by lazy { mutableListOf<AudioFileModel>() }
    private val ingredientsAudioFileList by lazy { ArrayList<AudioFileModel>() }
    private lateinit var audioIngredientsAdapter: ArrayAdapter<String>
    private lateinit var audioQtyAdapter: AudioFileAdapter
    private lateinit var audioActionAdapter: AudioFileAdapter
    private val mPlayer: MediaPlayer by lazy {
        MediaPlayer()
    }

    var typeUnits = arrayListOf(
        "Add",
        "Heat",
        "Mix",
        "Stir"
    )
    var ingredients = arrayListOf<String>(
    )

    init {
        val externalStorageDir = context.filesDir
        val assetManager = context.assets
        val ingFolder = File(externalStorageDir, Constants.INGREDIENTS_AUDIO_PATH)
        var ingIndex = -1
        var qtyIndex = -1
        var typeIndex = -1
        ingFolder.listFiles()?.forEachIndexed { index, file ->
            ingIndex++
            ingredientsAudioFileList.add(
                AudioFileModel(
                    id = ingIndex,
                    fileName = file.name,
                    filePath = null, type = Constants.FILE_TYPE.UPLOADED,
                    isSelected = false,
                    file = file
                )
            )
        }

        assetManager.list(Constants.STATIC_INGREDIENTS_AUDIO_PATH)?.let { list ->
            list.forEachIndexed { index, it ->
                ingIndex++
                ingredientsAudioFileList.add(
                    AudioFileModel(
                        id = ingIndex,
                        fileName = "s_$it".replace(".mp3", ""),
                        filePath = null, type = Constants.FILE_TYPE.STATIC,
                        isSelected = false,
                        file = null
                    )
                )
            }
        }
        strIngAudioList = ingredientsAudioFileList.map { it.fileName }
        val qtyFolder = File(externalStorageDir, Constants.QTY_AUDIO_PATH)
        qtyFolder.listFiles()?.forEachIndexed { index, file ->
            qtyIndex++
            val model = AudioFileModel(
                id = qtyIndex,
                fileName = file.name,
                filePath = null, type = Constants.FILE_TYPE.UPLOADED,
                isSelected = false,
                file = file
            )
            qtyAudioFileList.add(model)
        }
        assetManager.list(Constants.STATIC_QTY_AUDIO_PATH)?.let { list ->
            list.forEachIndexed { index, it ->
                qtyIndex++
                qtyAudioFileList.add(
                    AudioFileModel(
                        id = qtyIndex,
                        fileName = "s_$it".replace(".mp3", ""),
                        filePath = null, type = Constants.FILE_TYPE.STATIC,
                        isSelected = false,
                        file = null
                    )
                )
            }
        }
        strQtyAudioList = qtyAudioFileList.map { it.fileName }

        val actionFolder = File(externalStorageDir, Constants.ACTION_AUDIO_PATH)
        actionFolder.listFiles()?.forEachIndexed { index, file ->
            typeIndex++
            val model = AudioFileModel(
                id = typeIndex,
                fileName = file.name,
                filePath = null, type = Constants.FILE_TYPE.UPLOADED,
                isSelected = false,
                file = file
            )
            actionAudioFileList.add(model)
        }
        assetManager.list(Constants.STATIC_ACTION_AUDIO_PATH)?.let { list ->
            list.forEachIndexed { index, it ->
                typeIndex++
                actionAudioFileList.add(
                    AudioFileModel(
                        id = typeIndex,
                        fileName = "s_$it".replace(".mp3", ""),
                        filePath = null, type = Constants.FILE_TYPE.STATIC,
                        isSelected = false,
                        file = null
                    )
                )
            }
        }
        strTypeAudioList = actionAudioFileList.map { it.fileName }
        mPlayer.setOnPreparedListener(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_instruction,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return listOfQuickAccessItems.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val headerViewHolder = holder as CreateInstructionAdapter.ContentViewHolder
        headerViewHolder.bindView(position)
    }

    fun setList(
        listOfQuickAccessItems: MutableList<Instructions>, position: Int? = null,
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

    fun add(step: Instructions) {
        listOfQuickAccessItems.add(step)
        listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
        listVisible.add(0)
//        notifyItemInserted(listOfQuickAccessItems.size)
    }

    fun getList(): ArrayList<Instructions> {
        val list = ArrayList<Instructions>()
        listOfQuickAccessItems.forEachIndexed { index, instructions ->
            if (instructions.Induction_on_time.isEmpty()) {
                instructions.Induction_on_time = "0"
            }
            if (instructions.Magnetron_on_time.isEmpty()) {
                instructions.Magnetron_on_time = "0"
            }
            if (instructions.Magnetron_power.isEmpty()) {
                instructions.Magnetron_power = "0"
            }
            if (instructions.Induction_power.isEmpty()) {
                instructions.Induction_power = "0"
            }
            //----updating time issue
//            if (instructions.durationInSec == 0) {
            try {
                instructions.durationInSec = instructions.Induction_on_time.toInt()
                    .coerceAtLeast(instructions.Magnetron_on_time.toInt())
            } catch (e: Exception) {
                DebugLog.e("Exception ${e.message}")
            }
//            }
        }
        list.addAll(listOfQuickAccessItems)
        return list
    }

    fun open(index: Int) {
        listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
        listVisible[index] = 0
        notifyDataSetChanged()
    }

    var lastClickedPos = 0

    inner class ContentViewHolder(var binding: ItemInstructionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var indFile: File? = null
        private var qtyFile: File? = null
        private var typeFile: File? = null
        fun bindView(pos: Int) {
            val item = listOfQuickAccessItems[pos]
            Log.e("Item", "listOfQuickAccessItems: $pos :- ${Gson().toJson(item)}")

            //-------No need to show pump section(Sprinkle and Spray) createRecipeScreenFlowType = "CREATE_FRY_RECIPE"-------//
            binding.tvSprinkleLabel.goneIfOrVisible(createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE)
            binding.etPumpTimeMin.goneIfOrVisible(createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE)
//            binding.etPumpTimeSec.goneIfOrVisible(createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE)
            binding.tvSprayLabel.goneIfOrVisible(createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE)
            binding.etSprayValue.goneIfOrVisible(createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE)

            //-------Threshold instruction value is different for both mode-------//
            //-------For fry mode itr should be 150 - 201" and for regular mode it should be 0 - 1000-------//
            binding.tvThresholdRange.text =
                if (createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE) {
                    "150 - 210"
                } else {
                    "0 - 1000"
                }

            listVisible.forEachIndexed { index, i ->
                Log.e("TAG", "bindView: $index value $i")
            }

            if (listVisible[pos] == 0) {
                binding.clInsItem.visibility = View.VISIBLE
                binding.tvSubTitle.visibility = View.GONE
            } else {
                binding.clInsItem.visibility = View.GONE
                if (item.Text.isNotEmpty()) {
                    val upperString: String =
                        item.Text.substring(0, 1).uppercase() + item.Text.substring(1).lowercase()
                    binding.tvSubTitle.text = ":- $upperString"
                    binding.tvSubTitle.visibility = View.VISIBLE
                } else {
                    binding.tvSubTitle.visibility = View.GONE
                }
            }
            item.id = pos + 1
//            item.audioQ = "wait"
            if (item.lid.isEmpty()) item.lid = "close"
            binding.cbSkip.isChecked = item.skip.equals("true", true)
            binding.tvTitle.text = "Step ${pos + 1}"
            spinnerAdapter = SpinnerAdapter(context, (context as CreateNewRecipe).qtyUnits)
            severityAdapter = SpinnerAdapter(context, severity)
            lidAdapter = SpinnerAdapter(context, lid)
            spinnerStirrerAdapter = SpinnerAdapter(context, spinnerStirrerList)
            binding.qtyItemSpinner.adapter = spinnerAdapter
            binding.lidSpinner.adapter = lidAdapter
            binding.spinnerStirrer.adapter = spinnerStirrerAdapter
            binding.severitySpinner.adapter = severityAdapter
            binding.cl.requestFocus()
//            binding.qtyMTimeSpinner.adapter = timeSpinner
//            binding.qtyITimeSpinner.adapter = timeSpinner
//            binding.indLidTimeSpinner.adapter = timeSpinner
//            binding.pumpTimeSpinner.adapter = timeSpinner
//            binding.stirrerTimeSpinner.adapter = timeSpinner
//            binding.waitTimeSpinner.adapter = timeSpinner
//            binding.warmTimeSpinner.adapter = timeSpinner
            adapter = ArrayAdapter(
                context,
                R.layout.item_spinner_dropdown_text, ingredients
            )
            unitAdapter = ArrayAdapter(
                context, R.layout.item_spinner_dropdown_text, (context as CreateNewRecipe).gmUnits
            )
            typeAdapter = ArrayAdapter(
                context, R.layout.item_spinner_dropdown_text, typeUnits
            )

            val ingAudioAdapter =
                ArrayAdapter(context, R.layout.item_spinner_dropdown_text, strIngAudioList)
            binding.etIngAudio.setAdapter(ingAudioAdapter)
            val qtyAudioAdapter =
                ArrayAdapter(context, R.layout.item_spinner_dropdown_text, strQtyAudioList)
            binding.etQtyAudio.setAdapter(qtyAudioAdapter)
            val typeAudioAdapter =
                ArrayAdapter(context, R.layout.item_spinner_dropdown_text, strTypeAudioList)
            binding.etTypeAudio.setAdapter(typeAudioAdapter)
            binding.etIngAudio.onItemClickListener =
                OnItemClickListener { parent, view, position, id ->
                    Log.e("TAG", "bindView: isName${position}")
                    Log.e("TAG", "bindView: isName${strIngAudioList[position]}")
                    Log.e("TAG", "bindView: isName${ingredientsAudioFileList.size}")
                    Log.e("TAG", "bindView: isName ${ingredientsAudioFileList[position].fileName}")
                    if (ingredientsAudioFileList[position].type == Constants.FILE_TYPE.UPLOADED)
                        ingredientsAudioFileList[position].file.let { it ->
                            it.let {
                                item.audioIUrl = Uri.fromFile(it).toString()
                            }
                        }
                    else {
                        item.audioI = ingredientsAudioFileList[position].fileName.replace("s_", "")
                    }
                }
            binding.etQtyAudio.onItemClickListener =
                OnItemClickListener { parent, view, position, id ->
                    if (qtyAudioFileList[position].type == Constants.FILE_TYPE.UPLOADED)
                        qtyAudioFileList[position].file.let { it ->
                            it.let {
                                item.audioQUrl = Uri.fromFile(it).toString()
                            }
                        }
                    else {
                        item.audioQ = qtyAudioFileList[position].fileName.replace("s_", "")
                    }
                }
            binding.etTypeAudio.onItemClickListener =
                OnItemClickListener { parent, view, position, id ->
                    if (actionAudioFileList[position].type == Constants.FILE_TYPE.UPLOADED)
                        actionAudioFileList[position].file.let { it ->
                            it.let {
                                item.audioPUrl = Uri.fromFile(it).toString()
                            }
                        }
                    else {
                        item.audioP = actionAudioFileList[position].fileName.replace("s_", "")
                    }
                }



            binding.etName.setAdapter(adapter)
//            binding.etQty.setAdapter(unitAdapter)
            binding.etType.setAdapter(typeAdapter)
            binding.etType.threshold = 1
//            binding.etQty.threshold = 1
            binding.etName.threshold = 1
            binding.etIngAudio.threshold = 1
            binding.etQtyAudio.threshold = 1
            binding.etTypeAudio.threshold = 1

            binding.etIndPower.filters = arrayOf<InputFilter>(MinMaxFilter(1, 100))
            binding.etMagPower.filters = arrayOf<InputFilter>(MinMaxFilter(1, 100))
            binding.etMicrowaveTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
            binding.etInductionTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
            binding.etIndLidTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
            binding.etWaitTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
            binding.etWarmTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
//            binding.etStirrerTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
//            binding.etPumpTimeSec.filters = arrayOf<InputFilter>(MinMaxFilter(0, 59))
            binding.etThreshold.filters = arrayOf<InputFilter>(MinMaxFilter(0, 1000))
            binding.ivDelete.setOnClickListener {
                Constants.showAlertDialog(context,
                    "Delete Step",
                    "Are you sure you want to delete ${item.Text} ?",
                    { _, _ ->
                        if (listVisible[pos] == 0 && listVisible.size > 1) {
                            listVisible[pos - 1] = 0
                            notifyDataSetChanged()
                        } else {
                            notifyItemRangeChanged(pos, listOfQuickAccessItems.size)
                        }
                        listOfQuickAccessItems.removeAt(pos)
                        listVisible.removeAt(pos)
                    },
                    { _, _ ->

                    })
            }
            binding.rlTitle.setOnClickListener {
                if (binding.clInsItem.visibility == View.VISIBLE) {
//                    binding.clInsItem.visibility = View.GONE
                } else {
//                    if (checkValid()) {
                    listVisible.forEachIndexed { index, _ -> listVisible[index] = 1 }
                    listVisible[pos] = 0
                    lastClickedPos = absoluteAdapterPosition
                    binding.clInsItem.visibility = View.VISIBLE

                    listener.OnItemClick(lastClickedPos)

//                    }
                }
            }
            //set Data From Model
            binding.etName.setText(item.Text)
            binding.cbMicrowave.isChecked = true
            binding.cbInduction.isChecked = true
            binding.etType.setText(item.Audio)
            if (!item.threshold.isNullOrEmpty()) {
                binding.etThreshold.setText(item.threshold)
            }else {
                binding.etThreshold.setText("")
            }
            binding.qtyItemSpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    Log.e("TAG", "afterTextChanged: etItemPosition $position")
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
                    Log.e("TAG", "afterTextChanged: et Weight: ${item.Weight}")

//                    binding.etQty.setAdapter(unitAdapter)
                    if (listOfQuickAccessItems[pos].Weight.contains(" ")) {
                        listOfQuickAccessItems[pos].Weight =
                            listOfQuickAccessItems[pos].Weight.split(" ")[0] + " " + (context as CreateNewRecipe).qtyUnits[position]
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.e("TAG", "onItemSelected: ")
                }
            }
            if (item.Weight.contains(" ")) {
                binding.etQty.setText(item.Weight.split(" ")[0])
                item.audioQ = item.Weight.split(" ")[0]
                context.qtyUnits.mapIndexed { index, weight ->
                    if (weight == item.Weight.split(" ")[1]) {
                        binding.qtyItemSpinner.setSelection(index)
                        return@mapIndexed
                    }
                }
            } else binding.etQty.setText("")
            lid.mapIndexed { index, lid ->
                if (lid == item.lid) {
                    binding.lidSpinner.setSelection(index)
                    return@mapIndexed
                }
            }
            severity.mapIndexed { index, severity ->
                if (severity == item.mag_severity) {
                    binding.severitySpinner.setSelection(index)
                    return@mapIndexed
                }
            }
            if (item.Magnetron_on_time.isNotEmpty() /*&& item.Magnetron_on_time != "0"*/) {
                val min = TimeUnit.SECONDS.toMinutes(item.Magnetron_on_time.toLong())
                binding.etMicrowaveTimeMin.setText(min.toString())
                Log.e("TAG", "Magnetron_on_time: ${(item.Magnetron_on_time.toInt() % 60)}")
                binding.etMicrowaveTimeSec.setText((item.Magnetron_on_time.toInt() % 60).toString())
                binding.cbMicrowave.isChecked = true
                Log.e("TAG", "enableButton: 1")
                listener.enableButton(true)
            } else {
                listener.enableButton(false)
                Log.e(
                    "TAG",
                    "Magnetron_on_time:False ${listOfQuickAccessItems[pos].Magnetron_on_time}"
                )
                if (item.Magnetron_on_time.isNotEmpty()) binding.cbMicrowave.isChecked = false
                binding.etMicrowaveTimeMin.setText("")
            }
            if (item.Magnetron_power.isNotEmpty() /*&& item.Magnetron_power != "0"*/) {
                binding.etMagPower.setText(listOfQuickAccessItems[pos].Magnetron_power)
            } else {
                binding.etMagPower.setText("")
            }
            if (item.Induction_on_time.isNotEmpty() /*&& item.Induction_on_time != "0"*/) {
                val min = TimeUnit.SECONDS.toMinutes(item.Induction_on_time.toLong())
                binding.etInductionTimeMin.setText(min.toString())
                binding.etInductionTimeSec.setText((item.Induction_on_time.toInt() % 60).toString())
                binding.cbInduction.isChecked = true
                listener.enableButton(true)
            } else {
                binding.etInductionTimeMin.setText("")
                binding.etInductionTimeSec.setText("")
                if (item.Induction_on_time.isNotEmpty()) binding.cbInduction.isChecked = false
            }
            if (item.Induction_power.isNotEmpty() /*&& item.Induction_power != "0"*/) {
                binding.etIndPower.setText(listOfQuickAccessItems[pos].Induction_power)
            } else {
                binding.etIndPower.setText("")
            }
            if (item.warm_time.isNotEmpty()) {
                val min = TimeUnit.SECONDS.toMinutes(item.warm_time.toLong())
                binding.etWarmTimeMin.setText(min.toString())
                binding.etWarmTimeSec.setText((item.warm_time.toInt() % 60).toString())
            } else {
                binding.etWarmTimeMin.setText("")
                binding.etWarmTimeSec.setText("")
            }
            if (item.wait_time.isNotEmpty()) {
                val min = TimeUnit.SECONDS.toMinutes(item.wait_time.toLong())
                binding.etWaitTimeMin.setText(min.toString())
                binding.etWaitTimeSec.setText((item.wait_time.toInt() % 60).toString())
            } else {
                binding.etWaitTimeMin.setText("")
                binding.etWaitTimeSec.setText("")
            }
            //According changes no need to set time (minute OR sec), from now we have to set 0(Off), 1(Low), 2(Med), 3(High), 4(V.High) from spinner
            /*if (item.stirrer_on.isNotEmpty()) {
                val min = TimeUnit.SECONDS.toMinutes(item.stirrer_on.toLong())
                binding.etStirrerTimeMin.setText(min.toString())
                binding.etStirrerTimeSec.setText((item.stirrer_on.toInt() % 60).toString())
            } else {
                binding.etStirrerTimeMin.setText("")
                binding.etStirrerTimeSec.setText("")
            }*/

            //-------Set selection of stirrer-------//
            if (item.stirrer_on.notNullAndNotEmpty() && item.stirrer_on.toInt() <= 4) {
                binding.spinnerStirrer.setSelection(item.stirrer_on.toInt())
            }

            if (item.pump_on.isNotEmpty()) {
//                val min = TimeUnit.SECONDS.toMinutes(item.pump_on.toLong())
                binding.etPumpTimeMin.setText(item.pump_on)
//                binding.etPumpTimeSec.setText((item.pump_on.toInt() % 60).toString())
            } else {
                binding.etPumpTimeMin.setText("")
//                binding.etPumpTimeSec.setText("")
            }

            if (item.purge_on.isNotEmpty()) {
                binding.etSprayValue.setText(item.purge_on)
            } else {
                binding.etSprayValue.setText("")
            }

            enableDisableSprinkleField(binding = binding, isEnable = binding.etSprayValue.text.toString().trim().isEmpty())
            enableDisableSprayField(binding = binding, isEnable = binding.etPumpTimeMin.text.toString().trim().isEmpty())

            if (item.Indtime_lid_con.isNotEmpty()) {
                val min = TimeUnit.SECONDS.toMinutes(item.Indtime_lid_con.toLong())
                binding.etIndLidTimeMin.setText(min.toString())
                binding.etIndLidTimeSec.setText((item.Indtime_lid_con.toInt() % 60).toString())
            } else {
                binding.etIndLidTimeMin.setText("")
                binding.etIndLidTimeSec.setText("")
            }

            if (item.Induction_power.isNotEmpty()) {
                binding.etIndPower.setText(listOfQuickAccessItems[pos].Induction_power)
            } else {
                binding.etIndPower.setText("")
            }
            if (item.Induction_power.isNotEmpty()) {
                binding.etIndPower.setText(listOfQuickAccessItems[pos].Induction_power)
            } else {
                binding.etIndPower.setText("")
            }
            var isName = false
            binding.etName.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    binding.etName.showDropDown()
                } else {
                    binding.etName.dismissDropDown()
                }
                isName = hasFocus
            }
            binding.etName.setOnClickListener {
                binding.etName.showDropDown()
            }
            binding.etName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isName && s.toString().isNotEmpty()) {
                        binding.ivRecordIng.isEnabled = true
                        item.Text = s.toString()
                        listOfQuickAccessItems[pos].audioI = s.toString()
                        checkValid(pos)
                        checkAndSet(pos)
                        return
                    }
                    if (isName)
                        binding.ivRecordIng.isEnabled = s.toString().isNotEmpty()
                }
            })
            var isQty = false

            binding.etQty.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
//                    binding.etQty.showDropDown()
                } else {
//                    binding.etQty.dismissDropDown()
                }
                isQty = hasFocus
            }
            binding.etQty.setOnClickListener {
//                binding.etQty.showDropDown()
            }



            binding.ivRecordQty.setOnTouchListener(OnTouchListener { v, event -> // TODO Auto-generated method stub
                try {
                    if (binding.qtyAudioSelect.checkedRadioButtonId == R.id.rbQtyUpload) {
                        if (event.action == MotionEvent.ACTION_DOWN)
                            Toast.makeText(
                                context,
                                context.getText(R.string.txt_select_audio),
                                Toast.LENGTH_SHORT
                            ).show()
                        return@OnTouchListener false
                    } else {

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (binding.etQty.text.toString().isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.strPleaseEnterQty),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@OnTouchListener true
                                }
                                binding.etQty.isEnabled = false
                                Log.e("TAG", "enableButton:QTY ${checkRecordAudioPermission()}")
                                item.audioQ = binding.etQty.text.toString()
                                qtyFile =
                                    createAudioFile(
                                        context,
                                        "${binding.etQty.text}.mp3",
                                        Constants.AUDIO_TYPE.QUANTITY
                                    )
                                qtyFile?.let {
                                    it.createNewFile()
                                    it.setWritable(true)
                                }
                                if (checkRecordAudioPermission()) {
                                    startRecording(binding.ivRecordQty, qtyFile)
                                    return@OnTouchListener true
                                }
                            }

                            MotionEvent.ACTION_UP -> {
                                if (binding.etQty.text.toString().isNotEmpty()) {
                                    binding.etQty.isEnabled = false
                                    stopRecording(binding.ivRecordQty)
                                }
//                        try {
//                            // below method is used to set the
//                            // data source which will be our file name
//                            mPlayer.setDataSource(
//                                zipFile.absolutePath
//                            )
//                            // below method will prepare our media player
//                            mPlayer.prepareAsync()
//
//                            // below method will start our media player.
//                        } catch (e: IOException) {
//                            Log.e("TAG", "prepare() failed")
//                        }
                                item.audioQUrl = Uri.fromFile(qtyFile).toString()
                            }
                        }
                        false
                    }
                } catch (e: Exception) {
                    Log.e("Exception", "Exception in Record Qty $e")
                    false
                }
            })
            binding.ivRecordType.setOnTouchListener(OnTouchListener { v, event -> // TODO Auto-generated method stub
                try {
                    if (binding.typeAudioSelect.checkedRadioButtonId == R.id.rbTypeUpload) {
                        if (event.action == MotionEvent.ACTION_DOWN)
                            Toast.makeText(
                                context,
                                context.getText(R.string.txt_select_audio),
                                Toast.LENGTH_SHORT
                            ).show()
                        return@OnTouchListener false
                    } else {

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (binding.etType.text.toString().isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.strPleaseEnterType),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@OnTouchListener true
                                }
                                binding.etType.isEnabled = false
                                typeFile = createAudioFile(
                                    context,
                                    "${binding.etType.text}.mp3",
                                    Constants.AUDIO_TYPE.ACTION
                                )
                                typeFile?.let {
                                    it.createNewFile()
                                    it.setWritable(true)
                                }
                                if (checkRecordAudioPermission()) {
                                    startRecording(binding.ivRecordType, typeFile)
                                    return@OnTouchListener true
                                }
                            }

                            MotionEvent.ACTION_UP -> {
                                if (binding.etType.text.toString().isNotEmpty()) {
                                    binding.etType.isEnabled = false
                                    stopRecording(binding.ivRecordType)
                                }

                                //                        try {
                                //                            // below method is used to set the
                                //                            // data source which will be our file name
                                //                            mPlayer.setDataSource(
                                //                                zipFile.absolutePath
                                //                            )
                                //                            // below method will prepare our media player
                                //                            mPlayer.prepareAsync()
                                //
                                //                            // below method will start our media player.
                                //                        } catch (e: IOException) {
                                //                            Log.e("TAG", "prepare() failed")
                                //                        }
                                item.audioPUrl = Uri.fromFile(typeFile).toString()
                            }
                        }
                        false
                    }
                } catch (e: Exception) {
                    Log.e("Exception", "Exception in Record Qty $e")
                    false
                }
            })
            var isIngAudio = false
            binding.etIngAudio.setOnFocusChangeListener { v, hasFocus ->
                isIngAudio = hasFocus
                if (hasFocus) {
                    binding.etIngAudio.showDropDown()
                } else {
                    binding.etIngAudio.dismissDropDown()
                }
            }
            binding.etIngAudio.setOnClickListener {
                binding.etIngAudio.showDropDown()
            }
            binding.etIngAudio.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isIngAudio) {
                        DebugLog.e("Hello Test ${s.toString().trim()}")
                        ((binding.etIngAudio.adapter) as ArrayAdapter<AudioFileModel>).filter.filter(
                            s.toString().trim()
                        )
                        /*                        audioIngredientsAdapter.*/
                    }
                }
            })
            var isQtyAudio = false
            binding.etQtyAudio.setOnFocusChangeListener { v, hasFocus ->
                isQtyAudio = hasFocus
                if (hasFocus) {
                    binding.etQtyAudio.showDropDown()
                } else {
                    binding.etQtyAudio.dismissDropDown()
                }
            }
            binding.etQty.setOnClickListener {
                binding.etQtyAudio.showDropDown()
            }
            binding.etQtyAudio.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isQtyAudio) {
                        DebugLog.e("Hello Test ${s.toString().trim()}")
                        ((binding.etQtyAudio.adapter) as ArrayAdapter<AudioFileModel>).filter.filter(
                            s.toString().trim()
                        )
                        /*                        audioIngredientsAdapter.*/
                        binding.ivRecordQty.isEnabled = s.toString().isNotEmpty()
                    }
                }
            })
            var isActionAudio = false
            binding.etTypeAudio.setOnFocusChangeListener { v, hasFocus ->
                isActionAudio = hasFocus
                if (hasFocus) {
                    binding.etTypeAudio.showDropDown()
                } else {
                    binding.etTypeAudio.dismissDropDown()
                }
            }
            binding.etTypeAudio.setOnClickListener {
                binding.etTypeAudio.showDropDown()
            }
            binding.etTypeAudio.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isActionAudio) {
                        DebugLog.e("Hello Test ${s.toString().trim()}")
                        ((binding.etTypeAudio.adapter) as ArrayAdapter<AudioFileModel>).filter.filter(
                            s.toString().trim()
                        )
                        /*                        audioIngredientsAdapter.*/
                        binding.ivRecordType.isEnabled = s.toString().isNotEmpty()
                    }
                }
            })

            binding.typeAudioSelect.setOnCheckedChangeListener { radioGroup, i ->
                if (i == R.id.rbTypeUpload) {
                    binding.ivRecordType.isEnabled = false
                    binding.etType.isEnabled = true
                } else {
                    if (binding.etName.text.isNotEmpty()) {
                        binding.etType.isEnabled = false
                        binding.ivRecordType.isEnabled = true
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.strPleaseEnterType), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            binding.ingAudioSelect.setOnCheckedChangeListener { radioGroup, i ->
                if (i == R.id.rbUploadIng) {
                    binding.ivRecordIng.isEnabled = false
                    binding.etName.isEnabled = true
                } else {
                    if (binding.etName.text.isNotEmpty()) {
                        binding.etName.isEnabled = false
                        binding.ivRecordIng.isEnabled = true
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.strPleaseEnterName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                DebugLog.e("Hello Test Ing ${radioGroup.id} $i")
            }
            binding.qtyAudioSelect.setOnCheckedChangeListener { radioGroup, i ->
                if (i == R.id.rbQtyUpload) {
                    binding.ivRecordQty.isEnabled = false
                    binding.etQty.isEnabled = true
                } else {
                    if (binding.etQty.text.isNotEmpty()) {
                        binding.etQty.isEnabled = false
                        binding.ivRecordQty.isEnabled = true
                    } else {
                        binding.rbQtyRecord.isSelected = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.strPleaseEnterQty),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                DebugLog.e("Hello Test Ing ${radioGroup.id} $i")
            }

            binding.ivRecordIng.setOnTouchListener(OnTouchListener { v, event ->
                DebugLog.e("Selected after Selected ${binding.rbUploadIng.isSelected}")

                if (binding.ingAudioSelect.checkedRadioButtonId == R.id.rbUploadIng) {
                    if (event.action == MotionEvent.ACTION_DOWN)
                        Toast.makeText(
                            context,
                            context.getText(R.string.txt_select_audio),
                            Toast.LENGTH_SHORT
                        ).show()
                    return@OnTouchListener true
                } else {
                    if (binding.etName.text.toString().isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.strPleaseEnterName),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@OnTouchListener true
                    }
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (binding.etName.text.toString().isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.strPleaseEnterName),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@OnTouchListener true
                            }
                            binding.etName.isEnabled = false
                            indFile =
                                createAudioFile(
                                    context,
                                    "${item.Text}.mp3",
                                    Constants.AUDIO_TYPE.INGREDIENTS
                                )
                            indFile?.let {
                                it.createNewFile()
                                it.setWritable(true)
                            }
                            if (checkRecordAudioPermission()) {
                                startRecording(binding.ivRecordIng, indFile)
                                return@OnTouchListener true
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            DebugLog.e(
                                "Recording Test ${
                                    binding.etName.text.toString().isNotEmpty()
                                }"
                            )
                            if (binding.etName.text.toString().isNotEmpty()) {
                                binding.etName.isEnabled = false
                                stopRecording(binding.ivRecordIng)
//                                record.stop()
                            }

//                        try {
//                            // below method is used to set the
//                            // data source which will be our file name
//                            mPlayer.setDataSource(
//                                zipFile.absolutePath
//                            )
//                            // below method will prepare our media player
//                            mPlayer.prepareAsync()
//
//                            // below method will start our media player.
//                        } catch (e: IOException) {
//                            Log.e("TAG", "prepare() failed")
//                        }
                            item.audioIUrl = Uri.fromFile(indFile).toString()
                        }
                    }
                    false
                }

            })

            binding.ivSpeakIng.setOnClickListener {
                try {
                    mPlayer.stop()
                    mPlayer.reset()
//                    mPlayer.release()
                    indFile?.let {
                        mPlayer.setDataSource(
                            it.absolutePath
                        )
                        mPlayer.prepareAsync()
                    }

//                    mPlayer.prepare()
                    // below method is used to set the
                    // data source which will be our file name
                    // below method will prepare our media player

                    // below method will start our media player.
                } catch (e: IOException) {
                    Log.e("TAG", "prepare() failed $e")
                }
            }

            binding.ivSpeakQty.setOnClickListener {
                try {
                    mPlayer.stop()
                    mPlayer.reset()
                    // below method is used to set the
                    // data source which will be our file name
                    qtyFile?.let {
                        mPlayer.setDataSource(
                            it.absolutePath
                        )
                        mPlayer.prepareAsync()
                    }

                    // below method will prepare our media player
                    // below method will start our media player.
                } catch (e: IOException) {
                    Log.e("TAG", "prepare() failed")
                }
            }

            binding.ivSpeakerType.setOnClickListener {
                try {
                    mPlayer.stop()
                    mPlayer.reset()
                    // below method is used to set the
                    // data source which will be our file name
                    typeFile?.let {
                        mPlayer.setDataSource(
                            it.absolutePath
                        )
                        mPlayer.prepareAsync()
                    }
                    // below method will prepare our media player
                    // below method will start our media player.
                } catch (e: IOException) {
                    Log.e("TAG", "prepare() failed")
                }
            }
            binding.etQty.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
//                    if (isQty && s.toString().isNotEmpty()) { //Commented by PrinceEWW, because also need to update Qty(Weight) value in model when user enter Qty(Weight) value and remove whole text
                    if (isQty /*&& s.toString().isNotEmpty()*/) {
                        item.Weight = if(s.toString().isNotEmpty()) { //If Qty(Weight) is not empty, then set value with selected measurement(gm/ml/cup/tsp)
                            s.toString() + " " + (context as CreateNewRecipe).qtyUnits[binding.qtyItemSpinner.selectedItemPosition]
                        } else { //and if Qty(Weight) is empty, then set empty("")
                            ""
                        }
                        checkValid(pos)
                        item.audioQ = s.toString()
                    }
                }
            })
            var isThreshold = false

            binding.etThreshold.setOnFocusChangeListener { v, hasFocus ->
                isThreshold = hasFocus
            }
            binding.etThreshold.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
//                    if (isThreshold && s.toString().isNotEmpty()) { //Commented by PrinceEWW, because also need to update threshold value in model when user enter threshold value and remove whole text
                    if (isThreshold) {
                        //                        Log.e("PrinceEWW>>>", "Set threshold: ${s.toString()} on position $pos")
                        Log.e("PrinceEWW>>>", "Value of threshold s: ${s.toString()} on position $pos")
                        Log.e("PrinceEWW>>>", "Value of threshold text: ${binding.etThreshold.text.toString()} on position $pos")
                        item.threshold = binding.etThreshold.text.toString()
                    }
//                    }
                }
            })

            var isType = false
            binding.etType.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    binding.etType.showDropDown()
                } else {
                    binding.etType.dismissDropDown()
                }
                isType = hasFocus
            }
            binding.etType.setOnClickListener {
                binding.etType.showDropDown()
            }
            binding.etType.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isType) {
                        item.Audio = s.toString()
                        item.audioP = s.toString()
                        item.app_audio =
                            item.audioP + " " + item.audioI + " " + item.audioQ + " " + item.audioU
                    }
                }
            })
            binding.cbMicrowave.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!isChecked) {
                    binding.etMicrowaveTimeMin.isEnabled = false
                    binding.etMicrowaveTimeSec.isEnabled = false
                    binding.etMagPower.isEnabled = false
                    binding.etMagPower.text?.clear()
                    binding.etMicrowaveTimeMin.text?.clear()
                    binding.etMicrowaveTimeSec.text?.clear()
                    item.Magnetron_power = "0"
                    checkValid(pos)
                    item.Magnetron_on_time = "0"
                } else {
                    Log.e("TAG", "enableButton: 3")
                    listener.enableButton(false)
                    binding.etMicrowaveTimeMin.isEnabled = true
                    binding.etMicrowaveTimeSec.isEnabled = true
                    binding.etMagPower.isEnabled = true
                }
            }
            var isMicrowaveTime = false
            binding.etMicrowaveTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isMicrowaveTime = hasFocus
            }
            binding.etMicrowaveTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isMicrowaveTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etMicrowaveTimeSec.text!!.isNotEmpty()) {
                                item.Magnetron_on_time = (s.toString().toInt() * 60).plus(
                                    binding.etMicrowaveTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.Magnetron_on_time = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.Magnetron_on_time = binding.etMicrowaveTimeSec.text.toString()
                        }
                        if (!binding.cbInduction.isChecked) checkValid(pos)
                        else {
                            checkBothValid(pos)
                        }
                    }
                }
            })
            var isMicrowaveTimeSec = false
            binding.etMicrowaveTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isMicrowaveTimeSec = hasFocus
            }
            binding.etMicrowaveTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isMicrowaveTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etMicrowaveTimeMin.text!!.isNotEmpty()) {
                                item.Magnetron_on_time =
                                    (binding.etMicrowaveTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.Magnetron_on_time = s.toString()
                            }
                        } else {
                            if (binding.etMicrowaveTimeMin.text!!.isNotEmpty()) item.Magnetron_on_time =
                                (binding.etMicrowaveTimeMin.text.toString()
                                    .toInt() * 60).toString()
                            else {
                                item.Magnetron_on_time = "0"
                            }
                        }
                        if (!binding.cbInduction.isChecked) checkValid(pos)
                        else {
                            checkBothValid(pos)
                        }
                    }
                }
            })

            var isMagPower = false
            binding.etMagPower.setOnFocusChangeListener { v, hasFocus ->
                isMagPower = hasFocus
            }
            binding.etMagPower.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                }

                override fun afterTextChanged(s: Editable?) {
                    if (isMagPower) {
                        item.Magnetron_power = s.toString()
                        if (!binding.cbInduction.isChecked) checkValid(pos)
                        else {
                            checkBothValid(pos)
                        }
                    }
                }
            })
            binding.cbInduction.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!isChecked) {
                    binding.etInductionTimeMin.isEnabled = false
                    binding.etInductionTimeSec.isEnabled = false
                    binding.etIndPower.isEnabled = false
                    binding.etIndPower.text?.clear()
                    binding.etInductionTimeSec.text?.clear()
                    binding.etInductionTimeMin.text?.clear()
                    item.Induction_power = "0"
                    item.Induction_on_time = "0"
                    checkValid(pos)
                } else {
                    Log.e("TAG", "enableButton: 4")
                    listener.enableButton(false)
                    binding.etInductionTimeMin.isEnabled = true
                    binding.etInductionTimeSec.isEnabled = true
                    binding.etIndPower.isEnabled = true
                }
            }
            var isInductionTime = false
            binding.etInductionTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isInductionTime = hasFocus
            }
            binding.etInductionTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isInductionTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etInductionTimeSec.text!!.isNotEmpty()) {
                                item.Induction_on_time = (s.toString().toInt() * 60).plus(
                                    binding.etInductionTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.Induction_on_time = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.Induction_on_time = binding.etInductionTimeSec.text.toString()
                        }
                        if (!binding.cbMicrowave.isChecked) checkValid(pos)
                        else {
                            checkBothValid(pos)
                        }
                    }
                }
            })
            var isInductionTimeSec = false
            binding.etInductionTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isInductionTimeSec = hasFocus
            }
            binding.etInductionTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isInductionTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etInductionTimeMin.text!!.isNotEmpty()) {
                                item.Induction_on_time =
                                    (binding.etInductionTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.Induction_on_time = s.toString()
                            }
                        } else {
                            if (binding.etInductionTimeMin.text!!.isNotEmpty()) item.Induction_on_time =
                                (binding.etInductionTimeMin.text.toString()
                                    .toInt() * 60).toString()
                            else {
                                item.Induction_on_time = "0"
                            }
                        }
                        if (!binding.cbMicrowave.isChecked) checkValid(pos)
                        else {
                            checkBothValid(pos)
                        }
                    }
                }
            })
            var isIndPower = false
            binding.etIndPower.setOnFocusChangeListener { v, hasFocus ->
                isIndPower = hasFocus
            }
            binding.etIndPower.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isIndPower) {
                        item.Induction_power = s.toString()
                        if (!binding.cbMicrowave.isChecked) checkValid(pos) else {
                            checkBothValid(pos)
                        }
                    }
                }
            })
            var isLidTime = false
            binding.etIndLidTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isLidTime = hasFocus
            }
            binding.etIndLidTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isLidTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etIndLidTimeSec.text!!.isNotEmpty()) {
                                item.Indtime_lid_con = (s.toString().toInt() * 60).plus(
                                    binding.etIndLidTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.Indtime_lid_con = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.Indtime_lid_con = binding.etIndLidTimeSec.text.toString()
                        }
//                        checkValid(pos)
                    }
                }
            })
            var isLidTimeSec = false
            binding.etIndLidTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isLidTimeSec = hasFocus
            }
            binding.etIndLidTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isLidTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etIndLidTimeMin.text!!.isNotEmpty()) {
                                item.Indtime_lid_con =
                                    (binding.etIndLidTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.Indtime_lid_con = s.toString()
                            }
                        } else {
                            if (binding.etIndLidTimeMin.text!!.isNotEmpty()) item.Indtime_lid_con =
                                (binding.etIndLidTimeMin.text.toString().toInt() * 60).toString()
                            else item.Indtime_lid_con = "0"
                        }
//                        checkValid(pos)
                    }
                }
            })
            var isWarmTime = false
            binding.etWarmTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isWarmTime = hasFocus
            }
            binding.etWarmTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isWarmTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etWarmTimeSec.text!!.isNotEmpty()) {
                                item.warm_time = (s.toString().toInt() * 60).plus(
                                    binding.etWarmTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.warm_time = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.warm_time = binding.etWarmTimeSec.text.toString()
                        }
//                        checkValid(pos)
                    }
                }
            })
            var isWarmTimeSec = false
            binding.etWarmTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isWarmTimeSec = hasFocus
            }
            binding.etWarmTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isWarmTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etWarmTimeMin.text!!.isNotEmpty()) {
                                item.warm_time =
                                    (binding.etWarmTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.warm_time = s.toString()
                            }
                        } else {
                            if (binding.etWarmTimeMin.text!!.isNotEmpty()) item.warm_time =
                                (binding.etWarmTimeMin.text.toString().toInt() * 60).toString()
                            else item.warm_time = "0"
                        }
//                        checkValid(pos)
                    }
                }
            })
            var isWaitTime = false
            binding.etWaitTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isWaitTime = hasFocus
            }
            binding.etWaitTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isWaitTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etWaitTimeSec.text!!.isNotEmpty()) {
                                item.wait_time = (s.toString().toInt() * 60).plus(
                                    binding.etWaitTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.wait_time = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.wait_time = binding.etWaitTimeSec.text.toString()
                        }
//                        checkValid(pos)
                    }
                }
            })
            var issWaitTimeSec = false
            binding.etWaitTimeSec.setOnFocusChangeListener { v, hasFocus ->
                issWaitTimeSec = hasFocus
            }
            binding.etWaitTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (issWaitTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etWaitTimeMin.text!!.isNotEmpty()) {
                                item.wait_time =
                                    (binding.etWaitTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.wait_time = s.toString()
                            }
                        } else {
                            if (binding.etWaitTimeMin.text!!.isNotEmpty()) item.wait_time =
                                (binding.etWaitTimeMin.text.toString().toInt() * 60).toString()
                            else item.wait_time = "0"
                        }
//                        checkValid(pos)
                    }
                }
            })

            //-------Below commented code is for old design(minute and second), after changes we have toggle of 0(Off), 1(Low), 2(Med), 3(High), 4(V.High)-------//
            //-------insted of this we manage on toggle change of stirrer-------//
            /*var isStirrerTime = false
            binding.etStirrerTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isStirrerTime = hasFocus
            }
            binding.etStirrerTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isStirrerTime) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etStirrerTimeSec.text!!.isNotEmpty()) {
                                item.stirrer_on = (s.toString().toInt() * 60).plus(
                                    binding.etStirrerTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.stirrer_on = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.stirrer_on = binding.etStirrerTimeSec.text.toString()
                        }
//                        checkValid(pos)
                    }
                }
            })
            var isStirrerTimeSec = false
            binding.etStirrerTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isStirrerTimeSec = hasFocus
            }
            binding.etStirrerTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isStirrerTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etStirrerTimeMin.text!!.isNotEmpty()) {
                                item.stirrer_on =
                                    (binding.etStirrerTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.stirrer_on = s.toString()
                            }
                        } else {
                            if (binding.etStirrerTimeMin.text!!.isNotEmpty()) item.stirrer_on =
                                (binding.etStirrerTimeMin.text.toString().toInt() * 60).toString()
                            else item.stirrer_on = "0"
                        }

//                        checkValid(pos)
                    }
                }
            })*/

            var isPumpTime = false
            binding.etPumpTimeMin.setOnFocusChangeListener { v, hasFocus ->
                isPumpTime = hasFocus
                enableDisableSprayField(binding = binding, isEnable = binding.etPumpTimeMin.text.toString().trim().isEmpty())
            }
            binding.etPumpTimeMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isPumpTime) {
                        /*if (s!!.isNotEmpty()) {
                            if (binding.etPumpTimeSec.text!!.isNotEmpty()) {
                                item.pump_on = (s.toString().toInt() * 60).plus(
                                    binding.etPumpTimeSec.text.toString().toInt()
                                ).toString()
                            } else {
                                item.pump_on = (s.toString().toInt() * 60).toString()
                            }
                        } else {
                            item.pump_on = binding.etPumpTimeSec.text.toString()
                        }*/
//                        checkValid(pos)

                        item.pump_on = binding.etPumpTimeMin.text.toString()
                    }
                    enableDisableSprayField(binding = binding, isEnable = s.toString().isEmpty())
                }
            })
            //-------We change functionality of pump, before it is working on minute and secound and now it is working on ml-------//
            /*var isPumpTimeSec = false
            binding.etPumpTimeSec.setOnFocusChangeListener { v, hasFocus ->
                isPumpTimeSec = hasFocus
            }
            binding.etPumpTimeSec.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isPumpTimeSec) {
                        if (s!!.isNotEmpty()) {
                            if (binding.etPumpTimeMin.text!!.isNotEmpty()) {
                                item.pump_on =
                                    (binding.etPumpTimeMin.text.toString().toInt() * 60).plus(
                                        s.toString().toInt()
                                    ).toString()
                            } else {
                                item.pump_on = s.toString()
                            }
                        } else {
                            if (binding.etPumpTimeMin.text!!.isNotEmpty()) item.pump_on =
                                (binding.etPumpTimeMin.text.toString().toInt() * 60).toString()
                            else item.pump_on = "0"
                        }
//                        checkValid(pos)
                    }
                }
            })*/

            var isPurgeFocused = false
            binding.etSprayValue.setOnFocusChangeListener { v, hasFocus ->
                isPurgeFocused = hasFocus
                Log.e("PrinceEWW>>>", "etSprayValue: ${binding.etSprayValue.text.toString().trim()}")
                enableDisableSprinkleField(binding = binding, isEnable = binding.etSprayValue.text.toString().trim().isEmpty())
            }
            binding.etSprayValue.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isPurgeFocused) {
                        item.purge_on = binding.etSprayValue.text.toString()
                    }
                    enableDisableSprinkleField(binding = binding, isEnable = s.toString().isEmpty())
                }
            })

            //-------With check change listener, there are some issue while notify adapter during add new step, so we perform task on click listener instead of check change listener-------//
            /*binding.cbSkip.setOnCheckedChangeListener { buttonView, isChecked ->
                item.skip = "$isChecked"
            }*/
            binding.cbSkip.setOnClickListener {
                //-------If skip is true set it false, and if skip is false set it true-------//
                item.skip = if (item.skip == "true") "false" else "true"
            }

            binding.lidSpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    item.lid = lid[position]
                    if (position == 1) { // "open" is selected (index 1 in lid array: ["close", "open"])
                        // Clear Magnetron-related fields
                        item.Magnetron_on_time = ""
                        item.Magnetron_power = ""
                        binding.etMicrowaveTimeMin.text?.clear()
                        binding.etMicrowaveTimeSec.text?.clear()
                        binding.etMagPower.text?.clear()
                        binding.cbMicrowave.isChecked = false // Uncheck microwave checkbox
                        binding.etMicrowaveTimeMin.isEnabled = false
                        binding.etMicrowaveTimeSec.isEnabled = false
                        binding.etMagPower.isEnabled = false
                        binding.tvUpdationTime.visibility = View.GONE
                        binding.etIndLidTimeMin.visibility = View.GONE
                        binding.etIndLidTimeSec.visibility = View.GONE
                        binding.clMag.visibility = View.GONE
                        item.mag_severity = "" // Clear severity as well
                    } else { // "close" is selected
                        binding.clMag.visibility = View.VISIBLE
                        item.mag_severity = severity[binding.severitySpinner.selectedItemPosition]
                        if (binding.severitySpinner.selectedItemPosition == 1) {
                            binding.tvUpdationTime.visibility = View.VISIBLE
                            binding.etIndLidTimeMin.visibility = View.VISIBLE
                            binding.etIndLidTimeSec.visibility = View.VISIBLE
                        } else {
                            binding.tvUpdationTime.visibility = View.GONE
                            binding.etIndLidTimeMin.visibility = View.GONE
                            binding.etIndLidTimeSec.visibility = View.GONE
                        }
                        // Enable microwave fields if checkbox is checked
                        if (binding.cbMicrowave.isChecked) {
                            binding.etMicrowaveTimeMin.isEnabled = true
                            binding.etMicrowaveTimeSec.isEnabled = true
                            binding.etMagPower.isEnabled = true
                        }
                    }
                    checkValid(pos) // Update button state
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.e("TAG", "onItemSelected: No selection")
                }
            }

            //-------Spinner Stirrer item change listener-------//
            binding.spinnerStirrer.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    item.stirrer_on = position.toString()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

            }

//            binding.cbLid.setOnCheckedChangeListener { buttonView, isChecked ->
//                if (isChecked) {
//                    item.lid = "open"
//                    item.mag_severity = ""
//                    binding.tvSeverity.visibility = View.GONE
//                    binding.severitySpinner.visibility = View.GONE
//                    binding.tvIndLidTime.visibility = View.GONE
//                    binding.etIndLidTime.visibility = View.GONE
//                    binding.indLidTimeSpinner.visibility = View.GONE
//                } else {
//                    item.mag_severity = "${severity[binding.severitySpinner.selectedItemPosition]}"
//                    item.lid = "close"
//                    binding.tvSeverity.visibility = View.VISIBLE
//                    binding.severitySpinner.visibility = View.VISIBLE
//                    if (binding.severitySpinner.selectedItemPosition == 1) {
//                        binding.tvIndLidTime.visibility = View.VISIBLE
//                        binding.etIndLidTime.visibility = View.VISIBLE
//                        binding.indLidTimeSpinner.visibility = View.VISIBLE
//                    }
//                }
//            }
            binding.severitySpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    item.mag_severity = severity[position]
                    if (position == 0) {
                        binding.tvUpdationTime.visibility = View.GONE
                        binding.etIndLidTimeMin.visibility = View.GONE
                        binding.etIndLidTimeSec.visibility = View.GONE
                    } else {
                        binding.tvUpdationTime.visibility = View.VISIBLE
                        binding.etIndLidTimeMin.visibility = View.VISIBLE
                        binding.etIndLidTimeSec.visibility = View.VISIBLE
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.e("TAG", "onItemSelected: ")
                }

            }
        }

        private fun checkAndSet(pos: Int) {
            if (binding.etName.text.isNotEmpty() && binding.etName.text.length > 2) {
                (context as CreateNewRecipe).recipe.Ingredients.forEachIndexed { index, ingredients ->
                    if (ingredients.title.trim().equals(
                            binding.etName.text.toString().trim(), true
                        ) && ingredients.weight.contains(" ")
                    ) {
                        (context as CreateNewRecipe).qtyUnits.mapIndexed { index, weight ->
                            if (weight == ingredients.weight.split(" ")[1]) {
                                binding.qtyItemSpinner.setSelection(index)
                                binding.qtyItemSpinner.isEnabled = false
                                return@mapIndexed
                            }
                        }
                        try {
                            binding.etQty.filters = arrayOf<InputFilter>(
                                MinMaxFilter(
                                    0,
                                    ingredients.weight.split(" ")[0].toInt()
                                )
                            )
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        }
                        binding.etQty.setText(ingredients.weight.split(" ")[0])
                        listOfQuickAccessItems[pos].Weight = ingredients.weight
                        listOfQuickAccessItems[pos].audioQ =
                            listOfQuickAccessItems[pos].Weight.split(" ")[0]
                        return@forEachIndexed
                    }
                }
            }
        }

        private fun checkBothValid(pos: Int): Boolean {
            Log.e("TAG", "enableButton: $pos")
            Log.e("TAG", "enableButton: ${Gson().toJson(listOfQuickAccessItems[pos])}")
            if (listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty()) listOfQuickAccessItems[pos].audioU =
                Constants.getAudioFromTime(
                    listOfQuickAccessItems[pos].Magnetron_on_time.toInt()
                        .coerceAtLeast(listOfQuickAccessItems[pos].Induction_on_time.toInt())
                        .toString()
                )
            else {
                if (listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty()) listOfQuickAccessItems[pos].audioU =
                    Constants.getAudioFromTime(
                        listOfQuickAccessItems[pos].Magnetron_on_time
                    )
                else if (listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty()) {
                    listOfQuickAccessItems[pos].audioU = Constants.getAudioFromTime(
                        listOfQuickAccessItems[pos].Induction_on_time
                    )
                }
            }
            listOfQuickAccessItems[pos].app_audio =
                listOfQuickAccessItems[pos].audioP + " " + listOfQuickAccessItems[pos].audioI
            return if (listOfQuickAccessItems[pos].Text.isNotEmpty() && listOfQuickAccessItems[pos].Weight.isNotEmpty() && (listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Magnetron_power.isNotEmpty()) && (listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Induction_power.isNotEmpty())) {
                Log.e("TAG", "enableButton: 5")
                listener.enableButton(true)
                true
            } else {
                Log.e("TAG", "enableButton: 6")
                listener.enableButton(false)
                false
            }
        }

        private fun checkValid(): Boolean {
            var pos = 0
            listVisible.forEachIndexed { index, i ->
                if (listVisible[index] == 0) {
                    pos = index
                }
            }
            return listOfQuickAccessItems[pos].Text.isNotEmpty() && listOfQuickAccessItems[pos].Weight.isNotEmpty() && ((listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Magnetron_power.isNotEmpty()) || (listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Induction_power.isNotEmpty()))
        }

        private fun checkValid(pos: Int): Boolean {
            if (listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty()) {
                listOfQuickAccessItems[pos].audioU = Constants.getAudioFromTime(
                    listOfQuickAccessItems[pos].Magnetron_on_time.toInt()
                        .coerceAtLeast(listOfQuickAccessItems[pos].Induction_on_time.toInt())
                        .toString()
                )
            } else {
                if (listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty()) listOfQuickAccessItems[pos].audioU =
                    Constants.getAudioFromTime(
                        listOfQuickAccessItems[pos].Magnetron_on_time
                    )
                else if (listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty()) {
                    listOfQuickAccessItems[pos].audioU = Constants.getAudioFromTime(
                        listOfQuickAccessItems[pos].Induction_on_time
                    )
                }
            }
            listOfQuickAccessItems[pos].app_audio =
                listOfQuickAccessItems[pos].audioP + " " + listOfQuickAccessItems[pos].audioI
            return if (listOfQuickAccessItems[pos].Text.isNotEmpty() && listOfQuickAccessItems[pos].Weight.isNotEmpty() && listOfQuickAccessItems[pos].Magnetron_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Magnetron_power.isNotEmpty() && listOfQuickAccessItems[pos].Induction_on_time.isNotEmpty() && listOfQuickAccessItems[pos].Induction_power.isNotEmpty()) {
                listener.enableButton(true)
                true
            } else {
                listener.enableButton(false)
                false
            }
        }
    }

    private fun checkRecordAudioPermission(): Boolean {
        var result = false
        val permissionList =
            arrayListOf(
                Manifest.permission.RECORD_AUDIO
            )
        PermissionManagerUtils.checkPermission(
            context,
            context as Activity,
            permissionList,
            PermissionManagerUtils.PermissionSessionManager(context as Activity),
            object : PermissionManagerUtils.PermissionAskListener {
                override fun onNeedPermission() {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        permissionList.toTypedArray(),
                        Constants.REQUEST_RECORD_PERMISSION
                    )
                }

                override fun onPermissionPreviouslyDenied() {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        permissionList.toTypedArray(),
                        Constants.REQUEST_RECORD_PERMISSION
                    )
                }

                override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                    //-------------HERE OPEN
                    DialogUtils().commonDialog(
                        context = context,
                        title = context.getString(R.string.title_permission_open_settings),
                        message = context.getString(R.string.message_storage_camera_permission),
                        positiveButton = context.getString(R.string.button_setting),
                        negativeButton = context.getString(R.string.button_cancel),
                        isAppLogoDisplay = true,
                        isCancelable = true,
                        callbackSuccess = {
                            //------Positive CallBack----//
                            context?.openPermissionSettings()
                        },
                        callbackNegative = {
                            //---------Negative CallBack----------//
                        })
                }

                override fun onPermissionGranted() {
                    result = true
                }

            })
        return result
    }

    private fun startRecording(id: LottieAnimationView, qtyFile: File?) {
        Toast.makeText(context, "Started", Toast.LENGTH_SHORT).show()
        Thread {
            record.startRecording(qtyFile)
        }.start()
        /*mediaRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(qtyFile!!.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            DebugLog.e("error Converting $e")
        }
        MediaMetadataRetriever.METADATA_KEY_TITLE
        mediaRecord?.let {
            it.setAudioSource(MediaRecorder.AudioSource.MIC) // Set audio source (change as needed)
            it.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Set output format (change as needed)
            it.setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // Set audio encoder (change as needed)
            it.setOutputFile(qtyFile)
            it.prepare()
            it.start()
        }*/
        id.playAnimation()
    }

    private fun stopRecording(id: LottieAnimationView) {
        try {
            Toast.makeText(context, "Stoped", Toast.LENGTH_SHORT).show()
            id.cancelAnimation()
            id.progress = 0F
            record.stop()
        } catch (e: Exception) {
            Log.e("stopRecording", "stopRecording: ${e.message}")
        }

        /* mediaRecord?.let {
             try {
                 it.stop()
             } catch (stopException: RuntimeException) {

             }
             it.release()
         }
         mediaRecord = null*/
    }

    override fun onPrepared(p0: MediaPlayer?) {
        mPlayer.start()
    }

    //-------According functionality, user can add sprinkle OR spray-------//
    //-------Enable/Disable Sprinkle Field-------//
    //-------In firmware, SPRINKLE = PUMP-------//
    private fun enableDisableSprinkleField(binding: ItemInstructionBinding, isEnable: Boolean) {
        binding.etPumpTimeMin.isEnabled = isEnable
        binding.etPumpTimeMin.isFocusable = isEnable
        binding.etPumpTimeMin.isFocusableInTouchMode = isEnable
        binding.etPumpTimeMin.isCursorVisible = isEnable
        binding.etPumpTimeMin.changeBackgroundTint(if (isEnable) R.color.white else R.color.textColorBlackOp15)
    }

    //-------Enable/Disable Spray Field-------//
    //-------In firmware, SPRAY = PURGE-------//
    private fun enableDisableSprayField(binding: ItemInstructionBinding, isEnable: Boolean) {
        binding.etSprayValue.isEnabled = isEnable
        binding.etSprayValue.isFocusable = isEnable
        binding.etSprayValue.isFocusableInTouchMode = isEnable
        binding.etSprayValue.isCursorVisible = isEnable
        binding.etSprayValue.changeBackgroundTint(if (isEnable) R.color.white else R.color.textColorBlackOp15)
    }
}