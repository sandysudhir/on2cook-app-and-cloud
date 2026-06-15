package com.invent.ontocook

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.invent.ontocook.Classes.StepTime
import com.invent.ontocook.adapter.AddStepIngredientsAdapter
import com.invent.ontocook.models.*
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.getDp
import com.invent.ontocook.utils.Constants.predefineSteps
import com.invent.ontocook.view.ItemStepView
import com.masoudss.lib.Utils
import com.masoudss.lib.WaveGravity
import com.rx2androidnetworking.Rx2AndroidNetworking
import com.sixthsolution.apex.nlp.dict.DictionaryBuilder
import com.sixthsolution.apex.nlp.dict.Tag
import com.sixthsolution.apex.nlp.english.EnglishTokenizer
import com.sixthsolution.apex.nlp.event.SeekBy
import com.sixthsolution.apex.nlp.ner.Entity
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetector
import com.sixthsolution.apex.nlp.ner.regex.RegExChunker
import com.sixthsolution.apex.nlp.tagger.StandardTagger
import com.sixthsolution.apex.nlp.tagger.TaggedWords
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_create_recipe_add_steps.*
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.text.BreakIterator
import java.util.*

class CreateRecipeAddStepsActivity : AppCompatActivity() {

    companion object{
        var RECIPE_ID = "recipe_id"
        var QUERY = "query"
    }

    //var recipe: Recipe? = null
    //var recipeStepList = mutableListOf<OnlineRecipeSteps>()
    private var stepProgressList = mutableListOf<Float>()

    var seekBarTop: Int = 0

    lateinit var addStepIngredientsAdapter: AddStepIngredientsAdapter

    var processList = arrayOf("Please select", "Preheat", "Boil", "Cut", "Soak", "Pour", "Add")
    var qtyList = arrayOf("Qty", "1 Tbsp", "1 cups", "1/2 cups", "100g")
    var powerList = arrayOf("Power", "10", "20", "30", "40")
    var timeList = arrayOf("Time", "10", "20", "30", "40")

    lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    var instructionList = mutableListOf<Instructions>()
    var recipeJson = Constants.RECIPES[4].recipe

    lateinit var recipeObject : RecipeObject
    var excludeIngredients = arrayOf("and", ",", "or", "the", "-", ";")
    var ingredientsImageUrl = ""
    var timePercent = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_recipe_add_steps)

        init()
    }

    @SuppressLint("CheckResult")
    private fun init(){
        tvPageTitle.text = "Create Recipe"

        spinnerInductionPower.isEnabled = false
        spinnerInductionTime.isEnabled = false

        spinnerMicrowavePower.isEnabled = false
        spinnerMicrowaveTime.isEnabled = false

        bottomSheetBehavior = BottomSheetBehavior.from(constraintBottomSheet)

        prepareTimeLine()
        //prepareIngredientsView()

        if(intent.hasExtra(RECIPE_ID)){
            val recipeId = intent.getIntExtra(RECIPE_ID, -1)
            if(recipeId != -1){
                Rx2AndroidNetworking
                    .get(Constants.RECIPE_DETAILS_URL)
                    .addPathParameter("recipe_id", recipeId.toString())
                    .build()
                    .getObjectObservable(RecipeObject::class.java)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        recipeObject = it
                        prepareRecipeSteps()
                    }, {
                        println("Error   ${it.localizedMessage}")
                    })
            }
        }else if(intent.hasExtra(QUERY)){
            val query = intent.getStringExtra(QUERY)
            if(query != ""){
                Rx2AndroidNetworking
                    .get(Constants.EXTRACT_RECIPE_URL)
                    .addPathParameter("query", query)
                    .build()
                    .getObjectObservable(ExtractRecipe::class.java)
                    .flatMap {
                        println("found url    ${it.items.first()}")
                        Observable.fromIterable(it.items)
                    }
                    .flatMap {
                        println("calling url    ${it.link}")
                        Rx2AndroidNetworking
                            .get(Constants.READ_RECIPE_PAGE_URL)
                            .addPathParameter("extract_url", it.link)
                            .build()
                            .getObjectObservable(RecipeObject::class.java)
                    }
                    .takeUntil {
                        it.title != ""
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        if(it.title != ""){
                            recipeObject = it
                            prepareRecipeSteps()
                        }
                    }, {
                        println("Error   ${it.localizedMessage}")
                    })
            }
        }else{
//            prepareRecipeSteps()
        }

        //prepareProcessSpinner()
        //prepareQtySpinner()
        //preparePowerSpinner()
        //prepareTimeSpinner()

        ivLeft.setOnClickListener {
            onBackPressed()
        }

//        btnAddNewSteps.setOnClickListener {
//            toggleBottomSheet()
//        }

//        btnAddStep.setOnClickListener {
//            var instructions = Instructions()
//            instructions.Text = processList[spinnerProcess.selectedItemPosition]
//            instructions.Weight = etDescription.text.toString()
//            if(cbMicrowave.isChecked){
//                instructions.Magnetron_on_time = timeList[spinnerMicrowaveTime.selectedItemPosition]
//                instructions.Magnetron_power = powerList[spinnerMicrowavePower.selectedItemPosition]
//            }
//            if(cbInduction.isChecked){
//                instructions.Induction_on_time = timeList[spinnerInductionTime.selectedItemPosition]
//                instructions.Induction_power = powerList[spinnerInductionPower.selectedItemPosition]
//            }
//            instructionList.add(instructions)
//
//            var recipe = Recipe()
//            recipe.instruction = instructionList
//            recipeJson = Gson().toJson(recipe)
//
//            resetInputView()
//            prepareRecipeSteps()
//            toggleBottomSheet()
//        }

//        cbInduction.setOnCheckedChangeListener { _, isChecked ->
//            spinnerInductionPower.isEnabled = isChecked
//            spinnerInductionTime.isEnabled = isChecked
//        }
//
//        cbMicrowave.setOnCheckedChangeListener { _, isChecked ->
//            spinnerMicrowaveTime.isEnabled = isChecked
//            spinnerMicrowavePower.isEnabled = isChecked
//        }
    }

//    private fun toggleBottomSheet(){
//        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
//            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
//        } else {
//            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
//        }
//    }

//    private fun resetInputView(){
//        spinnerProcess.setSelection(0)
//        etDescription.setText("")
//        cbMicrowave.isChecked = false
//        cbInduction.isChecked = false
//        spinnerInductionTime.setSelection(0)
//        spinnerInductionPower.setSelection(0)
//        spinnerMicrowaveTime.setSelection(0)
//        spinnerMicrowavePower.setSelection(0)
//    }

//    private fun prepareProcessSpinner(){
//        var arrayAdapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_text, processList){
//            override fun isEnabled(position: Int): Boolean {
//                return position != 0
//            }
//        }
//        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_text)
//        spinnerProcess.adapter = arrayAdapter
//    }
//
//    private fun prepareQtySpinner(){
//        var arrayAdapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_text, qtyList){
//            override fun isEnabled(position: Int): Boolean {
//                return position != 0
//            }
//        }
//        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_text)
//        spinnerQty.adapter = arrayAdapter
//    }
//
//    private fun preparePowerSpinner(){
//        var arrayAdapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_text, powerList){
//            override fun isEnabled(position: Int): Boolean {
//                return position != 0
//            }
//        }
//        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_text)
//        spinnerMicrowavePower.adapter = arrayAdapter
//        spinnerInductionPower.adapter = arrayAdapter
//    }
//
//    private fun prepareTimeSpinner(){
//        var arrayAdapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_text, timeList){
//            override fun isEnabled(position: Int): Boolean {
//                return position != 0
//            }
//        }
//        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_text)
//        spinnerMicrowaveTime.adapter = arrayAdapter
//        spinnerInductionTime.adapter = arrayAdapter
//    }
//
//    private fun prepareIngredientsView(){
//        rvIngredients.layoutManager = LinearLayoutManager(this@CreateRecipeAddStepsActivity, LinearLayoutManager.HORIZONTAL, false)
//        addStepIngredientsAdapter = AddStepIngredientsAdapter()
//        rvIngredients.adapter = addStepIngredientsAdapter
//    }

    private fun prepareTimeLine(){
        viewStoveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@CreateRecipeAddStepsActivity, 5)
            waveGap = Utils.dp(this@CreateRecipeAddStepsActivity, 6)
            waveMinHeight = Utils.dp(this@CreateRecipeAddStepsActivity, 6)
            waveCornerRadius = Utils.dp(this@CreateRecipeAddStepsActivity, 20)
            waveGravity = WaveGravity.RIGHT
            waveBackgroundColor =
                ContextCompat.getColor(this@CreateRecipeAddStepsActivity, R.color.dark_grey5)
            waveProgressColor =
                ContextCompat.getColor(this@CreateRecipeAddStepsActivity, R.color.colorMWColor)
            sample = getDummyWaveSample()
        }

        viewMicrowaveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@CreateRecipeAddStepsActivity, 5)
            waveGap = Utils.dp(this@CreateRecipeAddStepsActivity, 6)
            waveMinHeight = Utils.dp(this@CreateRecipeAddStepsActivity, 6)
            waveCornerRadius = Utils.dp(this@CreateRecipeAddStepsActivity, 20)
            waveGravity = WaveGravity.LEFT
            waveBackgroundColor =
                ContextCompat.getColor(this@CreateRecipeAddStepsActivity, R.color.dark_grey5)
            waveProgressColor =
                ContextCompat.getColor(
                    this@CreateRecipeAddStepsActivity,
                    R.color.colorFlamColor
                )
            sample = getDummyWaveSample()
        }
    }

    private fun getWordsDictionary() : DictionaryBuilder{
        val dictionaryBuilder = DictionaryBuilder()
        dictionaryBuilder.tag(
            Tag.PRESTEP_PREFIX,
            Entity.PRESTEP
        ).e(arrayOf("pour", "mix", "wash", "soak", "add", "cut", "pulse",
                "spoon", "drain", "put", "take", "make", "place", "peel", "chunk", "combine", "chop", "melt"))
        dictionaryBuilder.tag(
            Tag.COOKSTEP_MATCH,
            Entity.COOKSTEP
        ).e(arrayOf("cook", "heat", "bake", "preheat", "steam", "fry"))
        dictionaryBuilder.tag(
            Tag.DATE_SEPARATOR,
            Entity.COOKSTEP
        ).e(arrayOf(",", ".", ";"))
        dictionaryBuilder.tag(
            Tag.TIME_MIN,
            Entity.TIME
        ).e(SeekBy.MIN, "minutes", "min", "min.", "mins", "mins.")
        return dictionaryBuilder
//        dictionaryBuilder.tag(
//            Tag.PRESTEP_SEPARATOR,
//            Entity.PRESTEP
//        ).e(arrayOf("and", ",", "or", "the"))
    }

    private fun getIngredientsFromStep(step : Step) : List<String>{
        val ingredients = mutableListOf<String>()
        step.ingredients.map { it ->
            it.name.split(" ").map {
            if(!excludeIngredients.contains(it)){
                ingredients.add(it)
                ingredients.add(it + "s")
                ingredients.add(it + "es")
            }
        }}
        return ingredients
    }

    private fun calculateChunk(tags : TaggedWords, detector: List<ChunkDetector>, step : Step) : String{
        return RegExChunker(detector)
            .chunk(tags).joinToString(" ") {chunkPart ->
                val tagWordIndex = chunkPart.taggedWords.indexOfFirst { it.tags.containsTag(Tag.PRESTEP_SUFFIX) }
                if(tagWordIndex != -1){
                    val ingredientsIndex = step.ingredients.indexOfFirst { it.name == chunkPart.taggedWords[tagWordIndex].word }
                    if(ingredientsIndex != -1){
                        ingredientsImageUrl = Constants.INGREDIENTS_HOST_URL + step.ingredients[ingredientsIndex].image
                    }
                }
                chunkPart.toStringTaggedWords()
            }
    }

    private fun prepareInstruction(chunk : String, step : Step, inductionOnTime : Int = 30,
                                   recipeDuration : Int = 0, isCookStep : Boolean = false) : Int{
        if(chunk.isNotEmpty() && chunk.split("\\s+".toRegex()).size > 1){
            println("Step cook($isCookStep) ->  $chunk")
            var updatedInductionOnTime = inductionOnTime
            if(step.length != null && isCookStep){
                if(step.length!!.unit.lowercase() == "minutes"){
                    updatedInductionOnTime = (((step.length!!.number * timePercent) / 100.0) * 60).toInt()
                }else if(step.length!!.unit.lowercase() == "seconds"){
                    updatedInductionOnTime = step.length!!.number
                }
                updatedInductionOnTime = if(updatedInductionOnTime < 20) 20 else updatedInductionOnTime
                updatedInductionOnTime = if(updatedInductionOnTime > 300) 300 else updatedInductionOnTime
            }
            recipeObject.reAnalyzedInstructions.add(Instructions(chunk, "",
                "$updatedInductionOnTime", "100", "$updatedInductionOnTime",
                "100", durationInSec = recipeDuration, image = ingredientsImageUrl))
            return recipeDuration + updatedInductionOnTime
        }
        return recipeDuration
    }

    private fun prepareRecipe(){
        val dictionaryBuilder = getWordsDictionary()

        //recipeObject = Gson().fromJson(loadJSONFromAsset(), RecipeObject::class.java)

//        recipe = Recipe()
//        recipe!!.name = recipeObject.title
//        recipe!!.description = recipeObject.summary
//        recipe!!.imageUrl = recipeObject.image
//        recipe!!.tags = recipeObject.dishTypes.joinToString(",") { it }

//        recipeObject.extendedIngredients.map {
//            recipe!!.ingredients.add(Ingredients(title = it.name, text = it.original, weight = "${it.amount}${it.unit}"))
//        }

        if(recipeObject.analyzedInstructions.isNotEmpty()){
            var nextStepDuration = 0
            recipeObject.analyzedInstructions.map {
                it.steps.map { singleStep ->
                    //Prepare Dictionary from ingredients....
                    dictionaryBuilder.tag(
                        Tag.PRESTEP_SUFFIX,
                        Entity.PRESTEP
                    ).e(getIngredientsFromStep(singleStep).toTypedArray())

                    ingredientsImageUrl = if(singleStep.ingredients.firstOrNull() != null)
                        Constants.INGREDIENTS_HOST_URL + singleStep.ingredients.first().image else ""

                    val iterator = BreakIterator.getSentenceInstance(Locale.US)
                    iterator.setText(singleStep.step)
                    var lastIndex = iterator.first()
                    while(lastIndex != BreakIterator.DONE){
                        val firstIndex = lastIndex
                        lastIndex = iterator.next()
                        if(lastIndex != BreakIterator.DONE){
                            val parsingString = singleStep.step.substring(firstIndex, lastIndex).replace(".", " ").trim().lowercase()
                            println("parsing sen  ---     $parsingString ")
                            val tokens = EnglishTokenizer().tokenize(parsingString)
                            val tags = StandardTagger(dictionaryBuilder.build()).tag(tokens)

                            val filterToken = tokens.indexOfFirst { predefineSteps[it.toString()] != null }

                            if(filterToken != -1){
                                val ingredints = tags.filter { it.tags.containsTag(Tag.PRESTEP_SUFFIX) }
                                println("Ingredients   $ingredints ")

                                val ingrd = mutableListOf<Ingredient>()
                                ingredints.map { word ->
                                    val actualIngre = recipeObject.extendedIngredients.filter { it.name.lowercase().contains(word.word.lowercase()) }
                                    ingrd.addAll(actualIngre)
                                }

                                if(ingrd.size > 0){
                                    val soaking = predefineSteps[tokens[filterToken].toString()]
                                    soaking?.ingredients?.clear()
                                    soaking?.stepTime?.clear()
                                    soaking?.steps?.clear()

                                    soaking?.ingredients = ingrd.distinct().toMutableList()
                                    val time = StepTime()
                                    time.unit = "minutes"
                                    time.time = 2
                                    soaking?.stepTime = mutableListOf(time)

                                    recipeObject.reAnalyzedInstructions.addAll(soaking?.getStepsList()?.toList()!!)
                                    //println("Actual steps   ${soaking?.getStepsList()?.map { it.Text }} ")
                                }
                            }
//                            var preStepChunk = calculateChunk(tags, listOf(PreStepDetector()), singleStep)
//                            nextStepDuration = prepareInstruction(preStepChunk, singleStep, recipeDuration = nextStepDuration)
//
//                            var cookStepChunk = calculateChunk(tags, listOf(CookStepDetector()), singleStep)
//                            nextStepDuration = prepareInstruction(cookStepChunk, singleStep, recipeDuration = nextStepDuration, isCookStep = true)
                        }
                    }
                }
            }
        }
        recipeObject.reAnalyzedInstructions.map {
            println("step :  ${it.Text}")
        }
    }

    private fun loadJSONFromAsset(): String? {
        var json: String? = null
        json = try {
            val `is`: InputStream = assets.open("jsonfile.txt")
            val size: Int = `is`.available()
            val buffer = ByteArray(size)
            `is`.read(buffer)
            `is`.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return json
    }

    private fun prepareRecipeSteps(){
        prepareRecipe()

        if(recipeObject != null){
            val stepSums = recipeObject.reAnalyzedInstructions.map { it.Induction_on_time.toInt() }.sum()
            stepProgress.progressBarHeight = getDp(stepSums * 3, this@CreateRecipeAddStepsActivity).toFloat()
            stepProgressList.clear()
            stepProgressList.addAll(recipeObject.reAnalyzedInstructions.map { ((it.durationInSec * 100) / stepSums).toFloat() })
        }else{
            nestedScrollSeekBar.visibility = View.GONE
            //tvNoStep.visibility = View.VISIBLE
        }
        prepareStepView()
    }

    private fun prepareStepView(){
        stepProgress.markers = stepProgressList
        setLayoutParam(20)
        if(stepProgressList.size > 0) {
            nestedScrollSeekBar.visibility = View.VISIBLE
            //tvNoStep.visibility = View.GONE
            clCookingStepHeader.visibility = View.VISIBLE
        }else{
            nestedScrollSeekBar.visibility = View.GONE
            //tvNoStep.visibility = View.VISIBLE
            clCookingStepHeader.visibility = View.GONE
        }
        stepProgress.post {
            prepareDetailsView()
        }
    }

    private fun prepareDetailsView(isUpdateMargin: Boolean = false) {
        if (isUpdateMargin) {
            for ((index,view) in viewContainer.children.withIndex()) {
                view.id = stepProgressList[index].toInt()
                val top: Float = (view.id / 100f) * (stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.toInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
            }
        } else {
            viewContainer.removeAllViews()
            recipeObject.reAnalyzedInstructions.withIndex().map {
                val progress = stepProgressList[it.index]
                val index = it.index
                val top: Float = (progress / 100f) * (stepProgress.getBarHeight())

                val rootView = ItemStepView(this@CreateRecipeAddStepsActivity)
                rootView.setDefaultView(progress.toInt(), it.value.Text, it.value.Weight
                , it.value.Induction_on_time + " sec", "${index + 1}", it.value.image)
                rootView.setLayoutParam((top.toInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt())
                viewContainer.addView(rootView)
            }
        }
    }

//    private var dragListener = object : View.OnDragListener{
//        override fun onDrag(v: View?, event: DragEvent?): Boolean {
//            when (event?.action) {
//                DragEvent.ACTION_DRAG_ENDED -> {
//                    //var view = event?.localState as View
////                    var layoutParams = FrameLayout.LayoutParams(
////                        FrameLayout.LayoutParams.MATCH_PARENT,
////                        FrameLayout.LayoutParams.WRAP_CONTENT
////                    )
////                    layoutParams.topMargin =
////                        (event.y.toInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
////                    view?.layoutParams = layoutParams
//
////                    var stepSums = recipeStepList.map { it.stepDuration }.sum()
////                    var percent = (stepSums * event.y.toInt()) / stepProgress.getBarHeight()
////                    println("percen  ${percent} ${event.y.toInt()}  ")
////
////                    for(view in viewContainer.children.filter { it.id == view.id }){
////                        (view.layoutParams as FrameLayout.LayoutParams).topMargin =
////                            event.y.toInt()
////                    }
//                    return true
//                }
//                DragEvent.ACTION_DRAG_EXITED -> {
//                    return true
//                }
//                DragEvent.ACTION_DRAG_ENTERED -> {
//                    return true
//                }
//                DragEvent.ACTION_DRAG_STARTED -> {
//                    return true
//                }
//                DragEvent.ACTION_DROP -> {
//                    println("action drop")
//                    return true
//                }
//                DragEvent.ACTION_DRAG_LOCATION -> {
//                    return true
//                }
//                else -> return false
//            }
//        }
//    }
//
//    private var touchListener = View.OnTouchListener { v, event ->
//        if (event.action === MotionEvent.ACTION_DOWN) {
//            val dragShadowBuilder = View.DragShadowBuilder(v)
//            v.startDrag(null, dragShadowBuilder, v, 0)
//            true
//        } else {
//            false
//        }
//    }

//    private var onStepDeleteActionListener = View.OnClickListener { v ->
//        println("click on ${v.tag}")
//        Constants.showAlertDialog(this@CreateRecipeAddStepsActivity, "Delete Steps",
//            "Are you sure you want to delete step?", DialogInterface.OnClickListener { dialog, which ->
//                //recipeStepList.removeAt(v.tag as Int);
//
//            }, DialogInterface.OnClickListener { dialog, which ->
//                dialog.dismiss()
//            })
//    }

    private fun setLayoutParam(topMargin: Int, bottomMargin: Int? = null) {
        val layoutParam = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParam.topMargin = topMargin
        if (bottomMargin != null) {
            layoutParam.bottomMargin = bottomMargin
        }
        seekBarTop = topMargin
        clSeekBarView.layoutParams = layoutParam
    }

    private fun getDummyWaveSample(): IntArray {
        val data = IntArray(720)
        val validNumber = mutableListOf<Int>(25, 50, 75, 100)
        for (i in data.indices) {
            data[i] = validNumber[Random().nextInt(3)]
        }
        return data
    }

}