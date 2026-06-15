package com.invent.ontocook.multiple_connection.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.DetailsPagerAdapter
import com.invent.ontocook.dialog.DialogEditStepTime
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.RecipeSteps
import com.invent.ontocook.sequence.children
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.Round
import com.invent.ontocook.utils.Constants.getDp
import com.invent.ontocook.extension.showSnackBarShort
import com.invent.ontocook.view.ItemStepView
import com.masoudss.lib.Utils
import com.masoudss.lib.WaveGravity
import kotlinx.android.synthetic.main.activity_dashboard.*
import kotlinx.android.synthetic.main.view_header.*
import params.com.stepprogressview.StepProgressView
import java.text.DecimalFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.activity.OnBackPressedCallback

class OldDashboardActivity : AppCompatActivity(), StepProgressView.OnCurrentStepHighlight {

    //stop=0 - resume    stop=1  //stop     stop=2   //pause

    private var pulseAnimator: AnimatorSet? = null
    private var pulseAnimatorCurrentStep: AnimatorSet? = null
    private var increment = 1f
    var schedulTimer : Timer? = null
    private var isManualScroll = false
    var stepProgressList = mutableListOf<Float>(20f, 40f, 70f, 80f)
    var stepDoneList = mutableListOf<Int>()
    private var isPlaying = false
    private var stepSums = 0
    var seekBarHeight: Int = 0
    var seekBarTop: Int = 0
    var recipeStepList = mutableListOf<RecipeSteps>()
    var decimalFormat = DecimalFormat("0.0")
    var singleDecimalFormat = DecimalFormat("0")
    var currentPrepareStep = 0
    var totalPrepareStep = 3
    var currentStep = 1
    var currentRunningStep = 0
    private var remainSecond = 0
    var stepProg = 0
    private var nextExpectedProgressView = 0
    var recipe: Recipe? = null
    var recentItem: RecentItem? = null
    lateinit var broadcastReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        init()
    }


    //Toggle PrePrepare Step Visibility
    private fun togglePrePareSteps(isVisible: Boolean) {
        if (isVisible) {
            nestedScrollSeekBar.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_grey))
            clPrePrepare.visibility = View.VISIBLE
        } else {
            nestedScrollSeekBar.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
            clPrePrepare.visibility = View.GONE
        }
    }

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

    private fun prepareProgressbar(isResetProgress : Boolean = true){
        stepSums = recipeStepList.map { it.stepDuration }.sum()

        if(isResetProgress){
            val totalProgress = stepSums - remainSecond
            val pro = (totalProgress * 100) / stepSums.toFloat()
            stepProgress.currentProgress = pro.Round()
            viewStoveProgress.progress = pro.Round()
            viewMicrowaveProgress.progress = pro.Round()
        }

        //setStepTime()
        stepProgress.progressBarHeight = getDp(stepSums * 3, this@OldDashboardActivity).toFloat()
        increment = 100f / stepSums

        stepProgressList.clear()
        stepProgressList.addAll(recipeStepList.map { ((it.durationInSec * 100) / stepSums.toFloat()).Round() })
    }

    private fun updateRecStep(step: Int, duration: Int) {
        stopAnimation()
        stopScheduleTimer()
        viewDetailsBottomSheet.collapse()

        remainSecond -= recipeStepList[step].stepDuration

        var nextStepPro = 0
        for (i in step until recipeStepList.size) {
            if (i == step) {
                recipeStepList[i].stepDuration = duration
                recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                nextStepPro =
                    recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
            } else {
                recipeStepList[i].durationInSec = nextStepPro
                nextStepPro += recipeStepList[i].stepDuration
            }
        }

        remainSecond += duration
        prepareProgressbar()

        prepareDetailsView(true)
        startAnimation()
        updateDetailsView(stepProgressList[step])

        showSnackBarShort("Updated recipe with $duration seconds")
    }

    private fun updateRec(step: Int, duration: Int) {
        if(recipeStepList[step].stepDuration - stepProg > duration){
            stopAnimation()
            stopScheduleTimer()
            viewDetailsBottomSheet.collapse()

            //remainSecond -= recipeStepList[step].stepDuration
            remainSecond -= (recipeStepList[step].stepDuration - stepProg)

            var nextStepPro = 0
            for (i in step until recipeStepList.size) {
                if (i == step) {
                    recipeStepList[i].stepDuration = stepProg + duration
                    recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                    nextStepPro =
                        recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
                } else {
                    recipeStepList[i].durationInSec = nextStepPro
                    nextStepPro += recipeStepList[i].stepDuration
                }
            }

            remainSecond += duration
            prepareProgressbar()

            prepareDetailsView(true)

            Thread {
                runOnUiThread {
                    startAnimation()
                    prepareTimer()
                    updateDetailsView(stepProgressList[step])
                }
            }.start()

            showSnackBarShort("Updated recipe with $duration seconds")
        }else{
            showSnackBarShort("$duration second is to short for update")
        }
    }

    private fun prepareTimer(){
        schedulTimer = Timer()
        schedulTimer?.scheduleAtFixedRate(object : TimerTask(){
            override fun run() {
                runOnUiThread {
                    performHandlerAction()
                }
            }
        }, 0 , 1000)
    }

    //0 - increment  1 - decrement
    private fun updateRecipe(step: Int, duration: Int, incrementDec: Int = 0) {
        if(incrementDec == 1 && recipeStepList[step].stepDuration - stepProg < (duration + 3)){
            showSnackBarShort("$duration second is to short for update")
        }else{
            stopAnimation()
            stopScheduleTimer()
            viewDetailsBottomSheet.collapse()

            var nextStepPro = 0
            for (i in step until recipeStepList.size) {
                if (i == step) {
                    if (incrementDec == 0) {
                        recipeStepList[i].stepDuration = recipeStepList[i].stepDuration + duration
                    } else {
                        recipeStepList[i].stepDuration = recipeStepList[i].stepDuration - duration
                    }
                    recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                    nextStepPro =
                        recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
                } else {
                    recipeStepList[i].durationInSec = nextStepPro
                    nextStepPro += recipeStepList[i].stepDuration
                }
            }

            if (incrementDec == 0) {
                remainSecond += duration
            } else {
                remainSecond -= duration
            }
            prepareProgressbar()
            prepareDetailsView(true)
            Thread {
                runOnUiThread {
                    startAnimation()
                    prepareTimer()
                    updateDetailsView(stepProgressList[step])
                }
            }.start()
            showSnackBarShort("Updated recipe with ${if(incrementDec == 0) "+" else "-"}$duration seconds")
        }
    }

    private fun getImages(name: String): Int {
        return when (name.toLowerCase()) {
            "pizza" -> {
                R.drawable.ic_pizza
            }
            "butter" -> {
                R.drawable.ic_butter
            }
            "butter paper" -> {
                R.drawable.ic_butter_paper
            }
            "idlis" -> {
                R.drawable.ic_idlis
            }
            "preheat" -> {
                R.drawable.ic_step1
            }
            "oil" -> {
                R.drawable.ic_step2
            }
            "ginger & spices" -> {
                R.drawable.ic_step3
            }
            "onions" -> {
                R.drawable.ic_step4
            }
            "vegetable" -> {
                R.drawable.ic_step5
            }
            "potatoes", "tomatoes" -> {
                R.drawable.ic_step7
            }
            "rice" -> {
                R.drawable.ic_step8
            }
            "water" -> {
                R.drawable.ic_step9
            }
            "salt" -> {
                R.drawable.ic_step10
            }
            "close lid" -> {
                R.drawable.ic_step6
            }
            "done" -> {
                R.drawable.ic_done_step
            }
            else -> {
                R.drawable.ic_step1
            }
        }
    }

    private fun getDesc(name: String): String {
        return when (name.toLowerCase()) {
            "butter" -> {
                "1 Tbsp"
            }
            "idlis" -> {
                "6 Piece"
            }
            "butter paper", "pizza" -> {
                "1 Piece"
            }
            "preheat" -> {
                "Close lid and wait"
            }
            "oil" -> {
                "2 Tbsp and heat"
            }
            "ginger & spices" -> {
                "1 Tbsp and stir"
            }
            "onions" -> {
                "1/4 cup and fry "
            }
            "vegetable" -> {
                "Stir fry "
            }
            "potatoes", "tomatoes" -> {
                "Stir"
            }
            "rice" -> {
                "Stir"
            }
            "water" -> {
                "1 cup and Stir"
            }
            "salt" -> {
                "1 and 1/2 Tsp and Stir"
            }
            "close lid" -> {
                "And wait"
            }
            "done" -> {
                "Recipe Finished!"
            }
            else -> {
                "And wait"
            }
        }
    }

    private fun getAudio(name: String): String {
        return when (name.toLowerCase()) {
            "frozen pattie" -> {
                "frozen patti."
            }
            "preheat" -> {
                "preheat."
            }
            "oil" -> {
                "add oil."
            }
            "ginger & spices" -> {
                "add ginger & spices."
            }
            "onions" -> {
                "add onions."
            }
            "vegetable" -> {
                "add vegetable."
            }
            "potatoes" -> {
                "add potatoes."
            }
            "tomatoes" -> {
                "add tomatoes."
            }
            "rice" -> {
                "add rice."
            }
            "water" -> {
                "add water."
            }
            "salt" -> {
                "add salt."
            }
            "close lid" -> {
                "close lid."
            }
            else -> {
                "${name.toLowerCase()}."
            }
        }
    }

    private fun getAction(name: String): String {
        return when (name.toLowerCase()) {
            "preheat", "close lid" -> {
                "Wait"
            }
            "done" -> {
                ""
            }
            else -> {
                "Add"
            }
        }
    }

    private fun getMaxTime(instructions: Instructions): Int {
        return instructions.Induction_on_time.toInt()
            .coerceAtLeast(instructions.Magnetron_on_time.toInt())
    }

    private fun prepareRecipeSteps() {
        recipeStepList.clear()
        stepDoneList.clear()

        var nextStepDuration = 0

        recipe!!.Instruction.map {
            stepDoneList.add(0)
            recipeStepList.add(
                RecipeSteps(
                    getImages(it.Text),
                    it.Text,
                    getDesc(it.Text),
                    "${getMaxTime(it)} sec.",
                    getAction(it.Text), getMaxTime(it),
                    nextStepDuration,
                    it.app_audio
                )
            )
            nextStepDuration += getMaxTime(it)
        }

        prepareProgressbar(false)
        remainSecond = stepSums
        setStepTime()
    }

    private fun setStepTime() {
        if(remainSecond < 0){
            remainSecond = 0
        }
        val min = (remainSecond % 3600) / 60
        val second = remainSecond % 60
        tvTime.text = String.format("%02d:%02d", min, second)
    }

    private fun preparePrePareStep() {
        OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        val step = "Step ${currentPrepareStep + 1} of ${totalPrepareStep + 1}"
        tvStep.text = step
        tvPrePrepareTitle.text = recipe!!.Ingredients[currentPrepareStep].title
        tvPrePrepareDesc.text = recipe!!.Ingredients[currentPrepareStep].text
    }

    private fun sendMessage(message: String) {
        println("write message   $message")
        if(Constants.IS_PRODUCTION_MODE){
            OnToCookApplication.instance.bleBoundService.writeData(
                message.toByteArray(
                    Charsets.UTF_8
                )
            )
        }
    }

    fun performDoneButtonAction(message: String) {
        sendMessage(message)
        var pos = currentRunningStep - 1
        if (pos <= 0) {
            pos = 0
        }
        stepDoneList[pos] = 1
        stepProg = 0
        clNextStep.visibility = View.GONE

        //For done buttonn....
        if (stepProgress.currentProgress >= 100) {
            if (isPlaying) {
                togglePlayPause()
            }
            sendMessage("stop=1")
            OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
            onBackPressed()
            return;
        }
        //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
        //recipeStepList[currentRunningStep].app_audio + "." +
        OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
        if (!isPlaying) {
            playPlayer()
        }
    }

    private fun init() {
        if (intent.hasExtra("recipe")) {
            recentItem = intent.extras?.getSerializable("recipe") as RecentItem
        }

        tvRecipeName.text = recentItem?.name

        ivPulseBg.visibility = View.GONE
        ivCenterPlay.visibility = View.GONE

        tvDonePreAdd.setOnClickListener {
            performDoneButtonAction("add_confirm=${currentRunningStep}")
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        println("message  ${message}")
                        tvDonePreAdd.isEnabled = true

                        if (message.toLowerCase().contains("info=")) {
                            var messageChunk =  message.toLowerCase().toString().replace("info=", "").split(",")

                            var recipeNo = messageChunk[0]
                            var ingredient = if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                            var stepno = if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                            var second = if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                            var foundRecipe = Constants.RECIPES.filter { it.id.toString() == recipeNo}
                            if(foundRecipe.isNotEmpty()){
                                Handler(Looper.getMainLooper()).postDelayed({
                                    fastForward(stepno, second)
                                }, 100)
                            }
                        }
                        else if (message.toLowerCase().contains("complete")) {
                            updateDetailsView(
                                decimalFormat.format(stepProgress.currentProgress).toFloat()
                            )
                        } else if (message.toLowerCase()
                                .contains("add_confirm") && message.toLowerCase() != "add_confirm=0"
                        ) {
//                            val step = message.toLowerCase().replace("add_confirm=", "").toInt()
//                            if (step >= recipeStepList.size) {
//                                if (stepProgress.currentProgress >= 100) {
//                                    OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
//                                    onBackPressed()
//                                }
//                            } else {
//                                var pos = currentRunningStep - 1
//                                if (pos <= 0) {
//                                    pos = 0
//                                }
//                                stepDoneList[pos] = 1
//                                stepProg = 0
//                                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
//                                //recipeStepList[currentRunningStep].app_audio + "." +
//                                OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
//                                clNextStep.visibility = View.GONE
//                                if (!isPlaying) {
//                                    playPlayer()
//                                }
//                            }

                            if (stepProgress.currentProgress >= 100) {
                                OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
                                pausePlayer()
                                finish()
                                return;
                            }

                            //Jump to the step if not in async..

//                            val step = message.toLowerCase().replace("add_confirm=", "").toInt()
//                            if(step >= currentRunningStep){
//                                Handler(Looper.getMainLooper()).postDelayed({
//                                    fastForward(step, 0)
//                                }, 100)
//                            }else{
                                var pos = currentRunningStep - 1
                                if (pos <= 0) {
                                    pos = 0
                                }
                                stepDoneList[pos] = 1
                                stepProg = 0
                                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                                //recipeStepList[currentRunningStep].app_audio + "." +
                                OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
                                clNextStep.visibility = View.GONE
                                if (!isPlaying) {
                                    playPlayer()
                                }
                            //}
                        } else if (message.toLowerCase()
                                .contains("ingredients")
                        ) {
                            currentPrepareStep =
                                message.toLowerCase().replace("ingredients=", "").toInt()

                            btnNext.text = "Next"
                            btnSkip.text = "Previous"

                            if ((currentPrepareStep - 1) == totalPrepareStep) {
                                togglePlayPause()
                                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                                sendMessage("ingredients=100")
                            } else {
                                if (currentPrepareStep == totalPrepareStep) {
                                    btnNext.text = "Done"
                                }
                                if (currentPrepareStep == 0) {
                                    btnSkip.text = "Skip"
                                }
                                preparePrePareStep()
                            }
                        }
//                        else if (message.toLowerCase()
//                                .contains("seconds")
//                        ) {
//                            println("message   $message")
//                            Handler(Looper.getMainLooper()).postDelayed({
//                                updateRec(
//                                    currentStep - 1,
//                                    message.toLowerCase().replace("seconds:", "").toInt()
//                                )
//                            }, 100)
//                        }
                        else if (message.toLowerCase()
                                .contains("stop")
                        ) {
                            var stopAction = message.toLowerCase().replace("stop=", "")
                            if(stopAction == "0"){
                                if(!isPlaying){
                                    playPlayer()
                                }
                            }else if(stopAction == "1"){
                                if (isPlaying) {
                                    togglePlayPause()
                                }
                                finish()
                            }else if(stopAction == "2"){
                                if(isPlaying){
                                    pausePlayer()
                                }
                            }
                        }
                    }
                }
            }
        }

        recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)

        currentPrepareStep = 0

        if (intent.getIntExtra("prepreparestep", -1) != -1) {
            currentPrepareStep = intent.getIntExtra("prepreparestep", -1)

            if (currentPrepareStep == 0) {
                btnSkip.text = "Skip"
                btnNext.text = "Next"
            } else if (currentPrepareStep == totalPrepareStep) {
                btnSkip.text = "Previous"
                btnNext.text = "Done"
            } else {
                btnSkip.text = "Previous"
                btnNext.text = "Next"
            }
        }

        totalPrepareStep = recipe!!.Ingredients.size - 1

        ivPlaySound.setOnClickListener {
            OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        }

        prepareRecipeSteps()

        if (intent.getIntExtra("currentstep", -1) == -1
            && intent.getIntExtra("currentstep", -1) == -1
        ) {
            preparePrePareStep()
        }

        togglePrePareSteps(true)

        ivLeft.setOnClickListener {
            onBackPressed()
        }

        btnNext.setOnClickListener {
            if (btnNext.text.toString().toLowerCase() == "done") {
                togglePlayPause()
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                sendMessage("ingredients=100")
            } else {
                if (currentPrepareStep < totalPrepareStep) {
                    currentPrepareStep++
                    if (currentPrepareStep == totalPrepareStep) {
                        btnNext.text = "Done"
                    }
                    btnSkip.text = "Previous"
                    sendMessage("ingredients=${currentPrepareStep + 1}")
                    preparePrePareStep()
                }
            }
        }

        nextExpectedProgressView = (recipeStepList[0].durationInSec + recipeStepList[0].stepDuration) - 6

        stepProgress.markers = stepProgressList

        ivHome.visibility = View.GONE
        ivLeft.visibility = View.VISIBLE
        tvPageTitle.text = resources.getString(R.string.txt_cooking)

        ivEditTime.setOnClickListener {
            val dialogEditStepTime = DialogEditStepTime()
            dialogEditStepTime.context = this@OldDashboardActivity
            dialogEditStepTime.isPlaying = isPlaying
            dialogEditStepTime.inductionTime =
                recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()
            dialogEditStepTime.magnetronTime =
                recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()
            dialogEditStepTime.onButtonClickListener =
                object : DialogEditStepTime.OnButtonClickListener {
                    override fun onButtonClick(
                        isDone: Boolean,
                        magnetronOnTime: Int,
                        inductionOnTime: Int,
                        isIncrement: Boolean,
                        isIncInd: String,
                        isIncMag: String
                    ) {
                        if (isDone && !isPlaying) {
                            recipe!!.Instruction[currentStep - 1].Induction_on_time =
                                "$inductionOnTime"
                            recipe!!.Instruction[currentStep - 1].Magnetron_on_time =
                                "$magnetronOnTime"
                            updateRecStep(currentStep - 1,
                                inductionOnTime.coerceAtLeast(magnetronOnTime)
                            )
                            performDoneButtonAction("add_confirm=${currentRunningStep},magTime=${magnetronOnTime},indTime=${inductionOnTime}")
                            println("current step   $currentStep")
                        } else if (isDone && isPlaying) {
                            println("inc   $isIncrement  mg $magnetronOnTime in  $inductionOnTime ")
                            println("message  ${"seconds:${(recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(magnetronOnTime))) - stepProg}"}")
                            sendMessage("seconds:${(recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(magnetronOnTime))) - stepProg}")
                            updateRecipe(
                                currentStep - 1,
                                inductionOnTime.coerceAtLeast(magnetronOnTime),
                                if (isIncrement) 0 else 1
                            )
                        }
                    }
                }
            dialogEditStepTime.show(supportFragmentManager, "")
        }

        btnSkip.setOnClickListener {
            if (btnSkip.text.toString().toLowerCase() == "skip") {
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name.toLowerCase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                togglePlayPause()
                sendMessage("ingredients=100")
            } else {
                if (currentPrepareStep >= 0) {
                    btnNext.text = "Next"
                    currentPrepareStep--
                    if (currentPrepareStep == 0) {
                        btnSkip.text = "Skip"
                    }
                    sendMessage("ingredients=${currentPrepareStep + 1}")
                    preparePrePareStep()
                }
            }
        }

        clPrePrepare.post {
            setLayoutParam(clPrePrepare.height + 40, clPrePrepare.height / 2)
            Thread {
                runOnUiThread {
                    prepareDetailsView()
                }
            }.start()
        }

        ivBottomCurrentProgress.setOnClickListener {
            clCurrentStep.visibility = View.GONE
            toggleManualScrolling()
            updateDetailsView(stepProgress.currentProgress)
        }

        nestedScrollSeekBar.viewTreeObserver.addOnGlobalLayoutListener(object :
            OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                seekBarHeight = nestedScrollSeekBar.height
                nestedScrollSeekBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        viewPagerDetails.adapter =
            DetailsPagerAdapter(object :
                DetailsPagerAdapter.OnUpdateTimeBarDuration {
                override fun onUpdateDuration(duration: Int, isIncrement: Boolean) {
                    //println("duration $duration")
                    //sendMessage("seconds:${(recipeStepList[currentStep - 1].stepDuration + duration) - stepProg}")
                    //updateRecipe(currentStep - 1, duration, if (isIncrement) 0 else 1)
                }
            })

        TabLayoutMediator(tab, viewPagerDetails
        ) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Ingredients"
                }
                1 -> {
                    tab.text = "Process"
                }
                2 -> {
                    tab.text = "Utensils"
                }
            }
        }.attach()

        viewStoveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@OldDashboardActivity, 4)
            waveGap = Utils.dp(this@OldDashboardActivity, 4)
            waveMinHeight = Utils.dp(this@OldDashboardActivity, 5)
            waveCornerRadius = Utils.dp(this@OldDashboardActivity, 20)
            waveGravity = WaveGravity.RIGHT
            waveBackgroundColor = ContextCompat.getColor(this@OldDashboardActivity, R.color.dark_grey5)
            waveProgressColor = ContextCompat.getColor(this@OldDashboardActivity, R.color.colorMWColor)
            sample = getDummyWaveSample(false)
        }

        viewMicrowaveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@OldDashboardActivity, 4)
            waveGap = Utils.dp(this@OldDashboardActivity, 4)
            waveMinHeight = Utils.dp(this@OldDashboardActivity, 5)
            waveCornerRadius = Utils.dp(this@OldDashboardActivity, 20)
            waveGravity = WaveGravity.LEFT
            waveBackgroundColor = ContextCompat.getColor(this@OldDashboardActivity, R.color.dark_grey5)
            waveProgressColor =
                ContextCompat.getColor(this@OldDashboardActivity, R.color.colorFlamColor)
            sample = getDummyWaveSample(true)
        }

        ivPlayPause.setOnClickListener {
            togglePlayPause(true)
        }

        nestedScrollSeekBar.setOnTouchListener { _, _ ->
            toggleManualScrolling(true)
            false
        }
        nestedScrollSeekBar.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            if (isManualScroll) {
                val progressPercent =
                    (scrollY * 100) / (stepProgress.progressBarHeight)
                val index = stepProgress.markers.indexOf(
                    singleDecimalFormat.format(progressPercent).toFloat()
                )
                if (index != -1) {
                    prepareStepView(index + 1)
                }
            }
        }

        //stepProgress.onCurrentStepHighlight = this
        prepareStepView(0)

        Handler(Looper.getMainLooper()).postDelayed({
            fastForward(intent.getIntExtra("currentstep", -1), intent.getIntExtra("remainTime", -1))
        }, 100)
    }

    private fun jumpToStep(currentStp : Int){
        stopAnimation()
        stopScheduleTimer()

        currentStep = currentStp

        var totalDuration = 0
        for (i in 0 until currentStep - 1) {
            totalDuration += recipeStepList[i].stepDuration
            stepDoneList[i] = 1
        }

        remainSecond -= (totalDuration - stepProg)

        stepSums = recipeStepList.map { it.stepDuration }.sum()
        var totalpercent = (totalDuration * 100) / stepSums.toFloat()

        println("total percent   $totalpercent     $totalDuration")

        stepProgress.currentProgress = totalpercent.Round()
        viewStoveProgress.progress = totalpercent.Round()
        viewMicrowaveProgress.progress = totalpercent.Round()

        Handler(Looper.getMainLooper()).postDelayed({
            startAnimation()
            prepareTimer()
            updateDetailsView(stepProgressList[currentStep - 1])
        }, 50)
    }

    private fun fastForward(currentStp : Int, remainTime : Int) {
        if (currentStp != -1
            && remainTime != -1
        ) {
            isPlaying = false

            togglePrePareSteps(false)
            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
            prepareDetailsView(true)

            currentStep = currentStp

            var remainTm = remainTime
            var passedTm = recipeStepList[currentStep - 1].stepDuration - remainTm

            var totalDuration = 0
            for (i in 0 until currentStep - 1) {
                totalDuration += recipeStepList[i].stepDuration
                stepDoneList[i] = 1
            }

            totalDuration += passedTm

            remainSecond -= totalDuration

            stepSums = recipeStepList.map { it.stepDuration }.sum()
            var totalpercent = (totalDuration * 100) / stepSums.toFloat()

            stepProgress.currentProgress = totalpercent.Round()
            viewStoveProgress.progress = totalpercent.Round()
            viewMicrowaveProgress.progress = totalpercent.Round()

            Handler(Looper.getMainLooper()).postDelayed({
                togglePlayPause()
                updateDetailsView(stepProgressList[currentStep - 1])
            }, 50)
        }
    }

    private fun toggleManualScrolling(forceToggle: Boolean? = null) {
        isManualScroll = forceToggle ?: !isManualScroll

        if (isManualScroll) {
            ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        this,
                        R.color.dark_grey2
                    ), BlendModeCompat.SRC_IN
                )
        } else {
            scrollToPos()
            ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        this,
                        R.color.black
                    ), BlendModeCompat.SRC_IN
                )
        }
    }

    private fun manageManualScroll(stepIndex: Int) {
        val totalTime = stepSums - remainSecond
        viewContainer.children().withIndex().map {
            val view = it.value
            val index = it.index

            val currentPro = (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()

            if(totalTime >= recipeStepList[index].durationInSec
                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)){
                if(isManualScroll){
                    viewDetailsBottomSheet.visibility = View.VISIBLE
                    viewIndicatorCurrentStep.visibility = View.VISIBLE
                    clCurrentRecipe.visibility = View.VISIBLE
                    viewConnectorCurrentStep.visibility = View.VISIBLE

                    tvTitle.text = recipeStepList[stepIndex].name
                    tvDesc.text = recipeStepList[stepIndex].desc
                    tvDuration.text = recipeStepList[stepIndex].duration
                    viewIndicatorCurrentStep.text = recipeStepList[stepIndex].action

                    tvStepNo.text = "${stepIndex + 1}/${recipeStepList.size}"
                    tvBottomStepTitle.text = recipeStepList[stepIndex].name
                    tvBottomStepDesc.text = recipeStepList[stepIndex].desc
                    tvBottomStepTime.text = recipeStepList[stepIndex].duration

                    view.visibility = View.GONE
                }else{
                    view.visibility = View.GONE
                }
            }else if(totalTime > recipeStepList[index].durationInSec){
                if (stepDoneList[index] == 1) {
                    view.visibility = View.VISIBLE
                    //completed
                    val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
                    (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                        (top.roundToInt() + seekBarTop) - view.height + (stepProgress.getBarWidth() / 2).toInt()
                    (view as ItemStepView).setCompletedProgressView()
                }
            }else if(totalTime < recipeStepList[index].durationInSec){
                view.visibility = View.VISIBLE
                //remain
                val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
                (view as ItemStepView).setRemainProgressView()
            }
        }
    }

    private fun updateViews() {
        val totalTime = stepSums - remainSecond
        viewContainer.children().withIndex().map {
            val view = it.value
            val index = it.index

            val currentPro = (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()

            if(totalTime >= recipeStepList[index].durationInSec
                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && !isManualScroll){
                viewDetailsBottomSheet.visibility = View.VISIBLE
                viewIndicatorCurrentStep.visibility = View.VISIBLE
                clCurrentRecipe.visibility = View.VISIBLE
                viewConnectorCurrentStep.visibility = View.VISIBLE

                tvTitle.text = recipeStepList[index].name
                tvDesc.text = recipeStepList[index].desc
                tvDuration.text = recipeStepList[index].duration
                viewIndicatorCurrentStep.text = recipeStepList[index].action

                tvStepNo.text = "${index + 1}/${recipeStepList.size}"
                tvBottomStepTitle.text = recipeStepList[index].name
                tvBottomStepDesc.text = recipeStepList[index].desc

                view.visibility = View.GONE
                if (stepDoneList[index] == 1) {
                    clNextStep.visibility = View.GONE
                }
                currentRunningStep = index

                if ((index + 1) <= stepProgressList.lastIndex) {
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            }else if(totalTime == (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)
                && isManualScroll){
                toggleManualScrolling()
                view.visibility = View.GONE
                if (stepDoneList[index] == 1) {
                    clNextStep.visibility = View.GONE
                }
                if ((index + 1) <= stepProgressList.lastIndex) {
                    //nextExpectedProgressView = stepProgressList[index + 1] - 1
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            }
            else if(totalTime > recipeStepList[index].durationInSec){
                if (stepDoneList[index] == 1) {
                    view.visibility = View.VISIBLE
                    //completed
                    val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
                    //val top: Float = (view.id / 100f) * (stepProgress.getBarHeight())
                    (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                        (top.roundToInt() + seekBarTop) - view.height + (stepProgress.getBarWidth() / 2).toInt()
                    (view as ItemStepView).setCompletedProgressView()
                }
            }else if(totalTime < recipeStepList[index].durationInSec){
                view.visibility = View.VISIBLE
                //remain
                val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
                (view as ItemStepView).setRemainProgressView()
            }
        }
    }

    private fun updateDetailsView(currentProgress: Float) {
        //if (!isManualScroll) {
            tvBottomStepTime.text = getCurrentTime()
        //}

        val totalTime = stepSums - remainSecond
        if (totalTime >= nextExpectedProgressView && totalTime <= (nextExpectedProgressView + 6)) {
            if((currentStep + 1) <= recipeStepList.size){
                clNextStep.visibility = View.VISIBLE
                val index = currentStep
                tvDonePreAdd.isEnabled = true
                tvStepTitle.text = recipeStepList[index].name
                tvStepDescDash.text = recipeStepList[index].desc
                tvTimeIn.visibility = View.VISIBLE
                tvNextIn.text = "Next in"
                tvTimeIn.text = "${(nextExpectedProgressView + 6) - totalTime} sec"
            }
        }

        if (totalTime == stepSums) {
            togglePlayPause()
            for (view in viewCurrentProContainer.children()) {
                view.visibility = View.GONE
            }
            clCurrentStep.visibility = View.GONE
            viewDetailsBottomSheet.visibility = View.GONE
            viewIndicatorCurrentStep.visibility = View.GONE
            clCurrentRecipe.visibility = View.GONE
            viewConnectorCurrentStep.visibility = View.GONE
        }else{
            updateViews()
        }

        if (currentProgress < 100 && isManualScroll && (viewContainer.children()
                .isNotEmpty() && currentProgress >= viewContainer.children()[0].id)
        ) {
            viewCurrentProContainer.children().withIndex().map {
                val currentPro = ((totalTime * 100) / stepSums.toFloat()).Round()
                val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
                //val top: Float = (currentProgress / 100f) * (stepProgress.getBarHeight())

                (it.value.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.toInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
                it.value.visibility = View.VISIBLE
                it.value.findViewById<ImageView>(R.id.ivStepImage)
                    .setImageResource(recipeStepList[currentRunningStep].image)
                it.value.findViewById<AppCompatTextView>(R.id.tvStepName).text =
                    recipeStepList[currentRunningStep].name
                it.value.findViewById<AppCompatTextView>(R.id.viewIndicatorTop).text =
                    getCurrentTime()
                it.value.findViewById<AppCompatTextView>(R.id.tvStepDesc).text =
                    recipeStepList[currentRunningStep].desc
                it.value.findViewById<AppCompatTextView>(R.id.tvTime).text =
                    recipeStepList[currentRunningStep].duration
            }

            clCurrentStep.visibility = View.VISIBLE

            tvCurrentStepTime.text = getCurrentTime()
            tvBottomTitle.text = recipeStepList[currentRunningStep].name
            tvBottomDesc.text = recipeStepList[currentRunningStep].desc
        } else {
            for (view in viewCurrentProContainer.children()) {
                view.visibility = View.GONE
            }
            clCurrentStep.visibility = View.GONE
        }
    }

    private fun getCurrentTime(): String {
        val currentTime = stepSums - remainSecond
        val min = (currentTime % 3600) / 60
        val second = currentTime % 60
        return String.format("%02d:%02d", min, second)
    }

    private fun prepareDetailsView(isUpdateMargin: Boolean = false) {
        if (isUpdateMargin) {
            for ((index, view) in viewContainer.children.withIndex()) {
                view.id = stepProgressList[index].toInt()
                val top: Float = (stepProgressList[index] / 100f) * (stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
            }
        } else {
            viewContainer.removeAllViews()
            viewCurrentProContainer.removeAllViews()

            val rootView = ItemStepView(this@OldDashboardActivity)
            rootView.setCurrentProgressView(0)
            rootView.setCurrentProgressView(0)
            rootView.setLayoutParam((0 + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt())
            viewCurrentProContainer.addView(rootView)

            for ((index, progress) in stepProgressList.withIndex()) {
                val top: Float = (progress / 100f) * (stepProgress.getBarHeight())

                val rootView = ItemStepView(this@OldDashboardActivity)
                rootView.setDefaultView(
                    progress.toInt(),
                    recipeStepList[index].name,
                    recipeStepList[index].desc,
                    recipeStepList[index].duration,
                    "${index + 1}",
                    recipeStepList[index].action,
                    recipeStepList[index].image
                )
                rootView.setLayoutParam((top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt())
                viewContainer.addView(rootView)
            }
        }
    }

    private fun prepareStepView(index: Int) {
        if (index - 1 >= 0 && stepProgress.currentProgress > 0 && stepProgress.currentProgress < 100) {
            manageManualScroll(index - 1)
        }
    }

    private fun setupAnimator() {
        toggleManualScrolling(false)
        viewStoveProgress.progressSample = getDummyWaveSample(false)
        viewMicrowaveProgress.progressSample = getDummyWaveSample(true)

        pulseAnimator =
            AnimatorInflater.loadAnimator(this, R.animator.fading_pulse) as AnimatorSet

        pulseAnimatorCurrentStep = pulseAnimator!!.clone()
        pulseAnimatorCurrentStep!!.setTarget(ivCurrentPulseBg)
        pulseAnimatorCurrentStep!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                animator.start()
            }
        })

        pulseAnimator!!.setTarget(ivPulseBg)
        pulseAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                animator.start()
            }
        })
    }

    private fun pausePlayer(isSendMessage : Boolean = false) {
        if(isSendMessage){
            sendMessage("stop=2")
        }
        ivPlayPause.setImageResource(R.drawable.ic_play)
        stopAnimation()
        stopScheduleTimer()
        isPlaying = false
    }

    private fun stopScheduleTimer(){
        schedulTimer?.cancel()
        schedulTimer = null
    }

    private fun playPlayer(isSendMessage : Boolean = false) {
        if(isSendMessage){
            sendMessage("stop=0")
        }
        if (clPrePrepare.visibility == View.VISIBLE) {
            togglePrePareSteps(false)
            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
            Thread {
                runOnUiThread {
                    prepareDetailsView(true)
                }
            }.start()
        }
        ivPulseBg.visibility = View.VISIBLE
        ivCenterPlay.visibility = View.VISIBLE
        ivPlayPause.setImageResource(R.drawable.ic_pause)
        startAnimation()
        prepareTimer()
        isPlaying = true
    }

    private fun togglePlayPause(isSendMessageAction : Boolean = false) {
        if (isPlaying) {
            pausePlayer(isSendMessageAction)
        } else {
            playPlayer(isSendMessageAction)
        }
    }

    private fun stopAnimation() {
        if (pulseAnimator == null || !pulseAnimator!!.isStarted) {
            return
        }
        pulseAnimator!!.end()
        pulseAnimator!!.cancel()

        pulseAnimatorCurrentStep!!.end()
        pulseAnimatorCurrentStep!!.cancel()
    }

    private fun startAnimation() {
        if (pulseAnimator == null) {
            setupAnimator()
        }
        if (pulseAnimator!!.isStarted) {
            return
        }

        pulseAnimatorCurrentStep!!.start()
        pulseAnimator!!.start()
    }

    private fun scrollToPos() {
        val progress =
            ((stepProgress.currentProgress / stepProgress.totalProgress.toFloat()) *
                    (stepProgress.progressBarHeight))
        nestedScrollSeekBar.scrollTo(
            nestedScrollSeekBar.scrollX,
            progress.toInt()
        )
    }

    private fun performHandlerAction() {
        if (stepProgress.currentProgress >= 100) {
            stopScheduleTimer()
            return
        }

        stepProgress.currentProgress += increment
        viewStoveProgress.progress += increment
        viewMicrowaveProgress.progress += increment

        if (!isManualScroll) {
            scrollToPos()
        }
        setStoveMVProgress()
        remainSecond -= 1
        stepProg += 1
        setStepTime()
        onCurrentProgress(stepProgress.currentProgress)
        updateDetailsView(decimalFormat.format(stepProgress.currentProgress).toFloat())

        if(currentStep <= recipeStepList.size){
            val totalTime = stepSums - remainSecond
            if(totalTime == ((recipeStepList[currentStep - 1]).durationInSec + recipeStepList[currentStep - 1].stepDuration) && stepDoneList[currentStep - 1] == 0){
                pausePlayer()
                currentStep += 1

                if(currentStep <= recipeStepList.size) {
                    //Speak Details of next step...
                    OnToCookApplication.instance.speak(recipeStepList[currentStep - 1].app_audio + ".")

                    //To reset remain time view...
                    if (currentStep <= recipeStepList.size) {
                        tvTimeIn.text = ""
                        tvTimeIn.visibility = View.GONE
                        tvNextIn.text = recipeStepList[currentStep - 1].action
                    }
                }
            }
        }

//        val totalTime = stepSums - remainSecond
//        if(totalTime == ((recipeStepList[currentStep - 1]).durationInSec + recipeStepList[currentStep - 1].stepDuration) && stepDoneList[currentStep - 1] == 0){
//            pausePlayer()
//            currentStep += 1
////            if(currentStep >= recipeStepList.size){
////                currentStep = recipeStepList.size
////            }
//            //To reset remain time view...
//            if(currentStep <= recipeStepList.size){
//                tvTimeIn.text = ""
//                tvTimeIn.visibility = View.GONE
//                tvNextIn.text = recipeStepList[currentStep - 1].action
//            }
//        }
    }

    private fun setStoveMVProgress() {
        arcProgressMv.progress =
            viewStoveProgress.sample?.get(viewStoveProgress.progress.toInt())?.toFloat()!!
        arcProgressFlam.progress =
            viewMicrowaveProgress.sample?.get(viewMicrowaveProgress.progress.toInt())?.toFloat()!!
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        if (isPlaying) {
            togglePlayPause(true)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
    }

    private fun getDummyWaveSample(isMagnetron : Boolean = false): IntArray {
        val data = IntArray(stepSums)
        var dataIndex = 0

        for((index, recipeObj) in recipeStepList.withIndex()){
            //Divide by 3 because we have sey height of bar as 3 second (1 bar  = 3 sec..)
            for(i in 1..(recipeObj.stepDuration / 3)){
                if(isMagnetron){
                    data[dataIndex] = recipe!!.Instruction[index].Magnetron_power.toInt()
                }else{
                    data[dataIndex] = recipe!!.Instruction[index].Induction_power.toInt()
                }
                dataIndex += 1
            }
        }
        /*val validNumber = mutableListOf<Int>(25, 50, 75, 100)
        for (i in data.indices) {
            data[i] = validNumber[Random().nextInt(3)]
        }*/
        return data
    }

    override fun onCurrentStepHighlight(position: Int) {
//        currentStep = position
//        if (!isManualScroll) {
//            prepareStepView(position)
//        }
    }

    //For stop player after complete current step - as per req from h/w team
    override fun onBeforeCurrentStepHighlight(position: Int) {
//        var pos = position
//        if (pos != -1 && stepDoneList[pos] == 0) {
//            if (isPlaying) {
//                pausePlayer()
//                tvTimeIn.text = ""
//                tvTimeIn.visibility = View.GONE
//                tvNextIn.text = recipeStepList[currentRunningStep].action
//                //ivEditTime.visibility = View.VISIBLE
//                //TODO
//            }
//        }
    }

    private fun onCurrentProgress(progress: Float) {
        val top: Float = (progress / 100f) * (stepProgress.getBarHeight())
        val layoutParam = FrameLayout.LayoutParams(
            ivCurrentProgress.width,
            ivCurrentProgress.height
        )
        layoutParam.gravity = Gravity.CENTER_HORIZONTAL
        layoutParam.topMargin = top.toInt() + seekBarTop - (getDp(26, this@OldDashboardActivity) / 2)
        ivCurrentProgress.layoutParams = layoutParam
        ivCurrentPulseBg.layoutParams = layoutParam
    }

    override fun onBackPressed() {
        if (stepProgress.currentProgress < 99f) {
            val alertDialog: android.app.AlertDialog? =
                android.app.AlertDialog.Builder(this@OldDashboardActivity).create()
            alertDialog?.setTitle("Close Current Recipe")
            alertDialog?.setMessage("Are you sure you want to close current recipe?")
            alertDialog?.setButton(
                AlertDialog.BUTTON_POSITIVE, "Yes"
            ) { dialog, _ ->
                //stop playing - set to only call on stop not on pause
                if (isPlaying) {
                    togglePlayPause()
                }
                dialog.dismiss()
                sendMessage("stop=1")
                super.onBackPressed()
            }
            alertDialog?.setButton(
                AlertDialog.BUTTON_NEGATIVE,
                "No"
            ) { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog?.show()
        } else {
            pausePlayer()
            super.onBackPressed()
        }
    }
}