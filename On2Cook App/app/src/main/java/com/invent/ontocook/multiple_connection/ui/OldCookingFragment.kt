package com.invent.ontocook.multiple_connection.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.invent.ontocook.CookingActivity
import com.invent.ontocook.MainActivity
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.DetailsPagerAdapter
import com.invent.ontocook.databinding.FragmentCookingBinding
import com.invent.ontocook.dialog.DialogEditStepTime
import com.invent.ontocook.extension.showSnackBarShort
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.models.RecipeSteps
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.sequence.children
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.Round
import com.invent.ontocook.view.ItemStepView
import com.masoudss.lib.Utils
import com.masoudss.lib.WaveGravity
import java.text.DecimalFormat
import java.util.Timer
import java.util.TimerTask
import kotlin.math.roundToInt

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

private const val ARG_PARAM1 = "recipeItem"
private const val ARG_PARAM2 = "MAC_ADDRESS"

class OldCookingFragment : Fragment() {
    //stop=0 - resume    stop=1  //stop     stop=2   //pause

    private var pulseAnimator: AnimatorSet? = null
    private var pulseAnimatorCurrentStep: AnimatorSet? = null
    private var increment = 1f
    var schedulTimer: Timer? = null
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
    private var macAddress: String = ""
    private var param2: String? = null
    private lateinit var binding: FragmentCookingBinding
    lateinit var service: BleService
    private val TAG = this::class.java.simpleName

    private var isClickDone = false
    private var isPrepareRunning = true
    private var isResume = false
    private var changeTime = 0
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.bindService(
            Intent(requireContext(), BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )
        arguments?.let {
            macAddress = it.getString(ARG_PARAM2)!!
            recentItem = it.getSerializable(ARG_PARAM1) as RecentItem?
        }
        arguments.let {
            if (it != null) {
                if (it.containsKey(Constants.IS_RESUME))
                    isResume =
                        it.getBoolean(Constants.IS_RESUME, false)
                if (it.containsKey(Constants.CURRENT_STEP)) currentStep =
                    it.getInt(Constants.CURRENT_STEP)
                if (it.containsKey(Constants.PREPARE_STEP))
                    currentPrepareStep = it.getInt(Constants.PREPARE_STEP)
                if (it.containsKey(Constants.REMAINSEC))
                    remainSecond = it.getInt(Constants.REMAINSEC)
                if (it.containsKey(Constants.IS_PREPARE_RUNNING))
                    isPrepareRunning = it.getBoolean(Constants.IS_PREPARE_RUNNING, true)
                if (it.containsKey(Constants.RECIPE)) recentItem =
                    it.getSerializable(Constants.RECIPE) as RecentItem?
                if (it.containsKey(Constants.CHANGE_TIME)) changeTime =
                    it.getInt(Constants.CHANGE_TIME)
                if (it.containsKey(Constants.IS_PLAYING)) isPlaying =
                    it.getBoolean(Constants.IS_PLAYING, false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(layoutInflater, R.layout.fragment_cooking_old_2, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun init() {
        binding.clPrePrepare.post {
            setLayoutParam(binding.clPrePrepare.height + 40, binding.clPrePrepare.height / 2)
            if (!isPrepareRunning) {
                binding.clPrePrepare.visibility = View.GONE
            }
            Thread {
                activity?.runOnUiThread {
                    prepareDetailsView()
                }
            }.start()
        }
        binding.tvRecipeName.text = recentItem?.name

        binding.ivPulseBg.visibility = View.GONE
        binding.ivCenterPlay.visibility = View.GONE

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    parseData(intent)
                } else {
                    val fragment = intent.getStringExtra(Constants.MAC_ADDRESS)?.let {
                        (activity as HomeActivity).viewPagerAdapter.getFragmentFromMac(
                            it
                        )
                    }
                    Log.e("TAG", "onReceive:DD ${fragment!!.id}")
                    val navController = (fragment as DashboardFragment).navController
                    navController?.let { _ ->
                        navController.currentDestination?.let {
                            if (it.id == R.id.cookingFragment) {
                                (fragment.getCurrentFragment() as OldCookingFragment).parseData(intent)
                            }
                        }
                    }
                }
            }
        }

        recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)


        totalPrepareStep = recipe!!.Ingredients.size - 1

        prepareRecipeSteps()

        preparePrePareStep()

        togglePrePareSteps(true)

        nextExpectedProgressView =
            (recipeStepList[0].durationInSec + recipeStepList[0].stepDuration) - 6

        binding.stepProgress.markers = stepProgressList

        binding.viewHeader.ivHome.visibility = View.GONE
        binding.viewHeader.ivLeft.visibility = View.VISIBLE
        binding.viewHeader.tvPageTitle.text = resources.getString(R.string.txt_cooking)


        binding.nestedScrollSeekBar.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                seekBarHeight = binding.nestedScrollSeekBar.height
                binding.nestedScrollSeekBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        binding.viewPagerDetails.adapter =
            DetailsPagerAdapter(object :
                DetailsPagerAdapter.OnUpdateTimeBarDuration {
                override fun onUpdateDuration(duration: Int, isIncrement: Boolean) {
                    //println("duration $duration")
                    //sendMessage("seconds:${(recipeStepList[currentStep - 1].stepDuration + duration) - stepProg}")
                    //updateRecipe(currentStep - 1, duration, if (isIncrement) 0 else 1)
                }
            })

        TabLayoutMediator(
            binding.tab, binding.viewPagerDetails
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

        binding.viewStoveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(requireContext(), 4)
            waveGap = Utils.dp(requireContext(), 4)
            waveMinHeight = Utils.dp(requireContext(), 5)
            waveCornerRadius = Utils.dp(requireContext(), 20)
            waveGravity = WaveGravity.RIGHT
            waveBackgroundColor = ContextCompat.getColor(requireContext(), R.color.dark_grey5)
            waveProgressColor = ContextCompat.getColor(requireContext(), R.color.colorMWColor)
            sample = getDummyWaveSample(false)
        }

        binding.viewMicrowaveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(requireContext(), 4)
            waveGap = Utils.dp(requireContext(), 4)
            waveMinHeight = Utils.dp(requireContext(), 5)
            waveCornerRadius = Utils.dp(requireContext(), 20)
            waveGravity = WaveGravity.LEFT
            waveBackgroundColor = ContextCompat.getColor(requireContext(), R.color.dark_grey5)
            waveProgressColor =
                ContextCompat.getColor(requireContext(), R.color.colorFlamColor)
            sample = getDummyWaveSample(true)
        }
        //stepProgress.onCurrentStepHighlight = this
        prepareStepView(0)

        Handler(Looper.getMainLooper()).postDelayed({
//            fastForward(intent.getIntExtra("currentstep", -1), intent.getIntExtra("remainTime", -1))
        }, 100)
    }

    private fun initListener() {
        binding.tvDonePreAdd.setOnClickListener {
            performDoneButtonAction("add_confirm=${currentRunningStep}")
        }
        binding.viewHeader.ivLeft.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.btnNext.setOnClickListener {
            if (binding.btnNext.text.toString().lowercase() == "done") {
//                togglePlayPause()
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                sendMessage("ingredients=100")
            } else {
                if (currentPrepareStep < totalPrepareStep) {
                    currentPrepareStep++
                    if (currentPrepareStep == totalPrepareStep) {
                        binding.btnNext.text = "Done"
                    }
                    binding.btnSkip.text = "Previous"
                    sendMessage("ingredients=${currentPrepareStep + 1}")
                    preparePrePareStep()
                }
            }
        }
        binding.ivEditTime.setOnClickListener {
            val dialogEditStepTime = DialogEditStepTime()
            dialogEditStepTime.context = requireActivity() as AppCompatActivity
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
                            updateRecStep(
                                currentStep - 1,
                                inductionOnTime.coerceAtLeast(magnetronOnTime)
                            )
                            performDoneButtonAction("add_confirm=${currentRunningStep},magTime=${magnetronOnTime},indTime=${inductionOnTime}")
                            println("current step   $currentStep")
                        } else if (isDone && isPlaying) {
                            println("inc   $isIncrement  mg $magnetronOnTime in  $inductionOnTime ")
                            println(
                                "message  ${
                                    "seconds:${
                                        (recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(
                                            magnetronOnTime
                                        ))) - stepProg
                                    }"
                                }"
                            )
                            sendMessage(
                                "seconds:${
                                    (recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(
                                        magnetronOnTime
                                    ))) - stepProg
                                }"
                            )
                            updateRecipe(
                                currentStep - 1,
                                inductionOnTime.coerceAtLeast(magnetronOnTime),
                                if (isIncrement) 0 else 1
                            )
                        }
                    }
                }
            dialogEditStepTime.show(childFragmentManager, "")
        }

        binding.btnSkip.setOnClickListener {
            if (binding.btnSkip.text.toString().lowercase() == "skip") {
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                togglePlayPause()
                sendMessage("ingredients=100")
            } else {
                if (currentPrepareStep >= 0) {
                    binding.btnNext.text = "Next"
                    currentPrepareStep--
                    if (currentPrepareStep == 0) {
                        binding.btnSkip.text = "Skip"
                    }
                    sendMessage("ingredients=${currentPrepareStep + 1}")
                    preparePrePareStep()
                }
            }
        }

        binding.ivBottomCurrentProgress.setOnClickListener {
            binding.clCurrentStep.visibility = View.GONE
            toggleManualScrolling()
            updateDetailsView(binding.stepProgress.currentProgress)
        }
        binding.ivPlaySound.setOnClickListener {
            OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        }
        binding.ivPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.nestedScrollSeekBar.setOnTouchListener { _, _ ->
            toggleManualScrolling(true)
            false
        }
        binding.nestedScrollSeekBar.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            if (isManualScroll) {
                val progressPercent =
                    (scrollY * 100) / (binding.stepProgress.progressBarHeight)
                val index = binding.stepProgress.markers.indexOf(
                    singleDecimalFormat.format(progressPercent).toFloat()
                )
                if (index != -1) {
                    prepareStepView(index + 1)
                }
            }
        }
    }

    private fun togglePrePareSteps(isVisible: Boolean) {
        if (isVisible) {
            binding.nestedScrollSeekBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.dark_grey
                )
            )
            binding.clPrePrepare.visibility = View.VISIBLE
        } else {
            binding.nestedScrollSeekBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.clPrePrepare.visibility = View.GONE
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
        binding.clSeekBarView.layoutParams = layoutParam
    }

    private fun prepareProgressbar(isResetProgress: Boolean = true) {
        stepSums = recipeStepList.map { it.stepDuration }.sum()

        if (isResetProgress) {
            val totalProgress = stepSums - remainSecond
            val pro = (totalProgress * 100) / stepSums.toFloat()
            binding.stepProgress.currentProgress = pro.Round()
            binding.viewStoveProgress.progress = pro.Round()
            binding.viewMicrowaveProgress.progress = pro.Round()
        }

        //setStepTime()
        binding.stepProgress.progressBarHeight =
            Constants.getDp(stepSums * 3, requireContext()).toFloat()
        increment = 100f / stepSums

        stepProgressList.clear()
        stepProgressList.addAll(recipeStepList.map { ((it.durationInSec * 100) / stepSums.toFloat()).Round() })
    }

    private fun updateRecStep(step: Int, duration: Int) {
        stopAnimation()
        stopScheduleTimer()
        binding.viewDetailsBottomSheet.collapse()

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
        if (recipeStepList[step].stepDuration - stepProg > duration) {
            stopAnimation()
            stopScheduleTimer()
            binding.viewDetailsBottomSheet.collapse()

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
                activity?.runOnUiThread {
                    startAnimation()
                    prepareTimer()
                    updateDetailsView(stepProgressList[step])
                }
            }.start()

            showSnackBarShort("Updated recipe with $duration seconds")
        } else {
            showSnackBarShort("$duration second is to short for update")
        }
    }

    private fun prepareTimer() {
        schedulTimer = Timer()
        schedulTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    performHandlerAction()
                }
            }
        }, 0, 1000)
    }

    //0 - increment  1 - decrement
    private fun updateRecipe(step: Int, duration: Int, incrementDec: Int = 0) {
        if (incrementDec == 1 && recipeStepList[step].stepDuration - stepProg < (duration + 3)) {
            showSnackBarShort("$duration second is to short for update")
        } else {
            stopAnimation()
            stopScheduleTimer()
            binding.viewDetailsBottomSheet.collapse()

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
                activity?.runOnUiThread {
                    startAnimation()
                    prepareTimer()
                    updateDetailsView(stepProgressList[step])
                }
            }.start()
            showSnackBarShort("Updated recipe with ${if (incrementDec == 0) "+" else "-"}$duration seconds")
        }
    }

    private fun getImages(name: String): Int {
        return when (name.lowercase()) {
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
        return when (name.lowercase()) {
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
        return when (name.lowercase()) {
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
                "${name.lowercase()}."
            }
        }
    }

    private fun getAction(name: String): String {
        return when (name.lowercase()) {
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
        setStepTime(true)
    }

    //TODO Restructure
    private fun setStepTime(b: Boolean) {
        if (remainSecond < 0) {
            remainSecond = 0
        }
        val min = (remainSecond % 3600) / 60
        val second = remainSecond % 60
        binding.tvTime.text = String.format("%02d:%02d", min, second)
    }

    private fun preparePrePareStep() {
        OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        val step = "Step ${currentPrepareStep + 1} of ${totalPrepareStep + 1}"
        binding.tvStep.text = step
        binding.tvPrePrepareTitle.text = recipe!!.Ingredients[currentPrepareStep].title
        binding.tvPrePrepareDesc.text = recipe!!.Ingredients[currentPrepareStep].text
    }

    private fun sendMessage(message: String) {
        println("write message   $message")
        if (Constants.IS_PRODUCTION_MODE) {
            service.writeData(
                macAddress, message.toByteArray(
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
        binding.clNextStep.visibility = View.GONE

        //For done buttonn....
        if (binding.stepProgress.currentProgress >= 100) {
            if (isPlaying) {
                togglePlayPause()
            }
            sendMessage("stop=1")
            OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
            activity?.onBackPressedDispatcher?.onBackPressed()
            return;
        }
        //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
        //recipeStepList[currentRunningStep].app_audio + "." +
        OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
        if (!isPlaying) {
            playPlayer()
        }
    }

    private fun jumpToStep(currentStp: Int) {
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

        binding.stepProgress.currentProgress = totalpercent.Round()
        binding.viewStoveProgress.progress = totalpercent.Round()
        binding.viewMicrowaveProgress.progress = totalpercent.Round()

        Handler(Looper.getMainLooper()).postDelayed({
            startAnimation()
            prepareTimer()
            updateDetailsView(stepProgressList[currentStep - 1])
        }, 50)
    }

    private fun fastForward(currentStp: Int, remainTime: Int, b: Boolean) {
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

            binding.stepProgress.currentProgress = totalpercent.Round()
            binding.viewStoveProgress.progress = totalpercent.Round()
            binding.viewMicrowaveProgress.progress = totalpercent.Round()

            Handler(Looper.getMainLooper()).postDelayed({
                togglePlayPause()
                updateDetailsView(stepProgressList[currentStep - 1])
            }, 50)
        }
    }

    private fun toggleManualScrolling(forceToggle: Boolean? = null) {
        isManualScroll = forceToggle ?: !isManualScroll

        if (isManualScroll) {
            binding.ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.dark_grey2
                    ), BlendModeCompat.SRC_IN
                )
        } else {
            scrollToPos()
            binding.ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.black
                    ), BlendModeCompat.SRC_IN
                )
        }
    }

    private fun manageManualScroll(stepIndex: Int) {
        val totalTime = stepSums - remainSecond
        binding.viewContainer.children().withIndex().map {
            val view = it.value
            val index = it.index

            val currentPro =
                (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()

            if (totalTime >= recipeStepList[index].durationInSec
                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)
            ) {
                if (isManualScroll) {
                    binding.viewDetailsBottomSheet.visibility = View.VISIBLE
                    binding.viewIndicatorCurrentStep.visibility = View.VISIBLE
                    binding.clCurrentRecipe.visibility = View.VISIBLE
                    binding.viewConnectorCurrentStep.visibility = View.VISIBLE

                    binding.tvTitle.text = recipeStepList[stepIndex].name
                    binding.tvDesc.text = recipeStepList[stepIndex].desc
                    binding.tvDuration.text = recipeStepList[stepIndex].duration
                    binding.viewIndicatorCurrentStep.text = recipeStepList[stepIndex].action

                    binding.tvStepNo.text = "${stepIndex + 1}/${recipeStepList.size}"
                    binding.tvBottomStepTitle.text = recipeStepList[stepIndex].name
                    binding.tvBottomStepDesc.text = recipeStepList[stepIndex].desc
                    binding.tvBottomStepTime.text = recipeStepList[stepIndex].duration

                    view.visibility = View.GONE
                } else {
                    view.visibility = View.GONE
                }
            } else if (totalTime > recipeStepList[index].durationInSec) {
                if (stepDoneList[index] == 1) {
                    view.visibility = View.VISIBLE
                    //completed
                    val top: Float = (currentPro / 100f) * (binding.stepProgress.getBarHeight())
                    (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                        (top.roundToInt() + seekBarTop) - view.height + (binding.stepProgress.getBarWidth() / 2).toInt()
                    (view as ItemStepView).setCompletedProgressView()
                }
            } else if (totalTime < recipeStepList[index].durationInSec) {
                view.visibility = View.VISIBLE
                //remain
                val top: Float = (currentPro / 100f) * (binding.stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt()
                (view as ItemStepView).setRemainProgressView()
            }
        }
    }

    private fun updateViews() {
        val totalTime = stepSums - remainSecond
        binding.viewContainer.children().withIndex().map {
            val view = it.value
            val index = it.index

            val currentPro =
                (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()

            if (totalTime >= recipeStepList[index].durationInSec
                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && !isManualScroll
            ) {
                binding.viewDetailsBottomSheet.visibility = View.VISIBLE
                binding.viewIndicatorCurrentStep.visibility = View.VISIBLE
                binding.clCurrentRecipe.visibility = View.VISIBLE
                binding.viewConnectorCurrentStep.visibility = View.VISIBLE

                binding.tvTitle.text = recipeStepList[index].name
                binding.tvDesc.text = recipeStepList[index].desc
                binding.tvDuration.text = recipeStepList[index].duration
                binding.viewIndicatorCurrentStep.text = recipeStepList[index].action

                binding.tvStepNo.text = "${index + 1}/${recipeStepList.size}"
                binding.tvBottomStepTitle.text = recipeStepList[index].name
                binding.tvBottomStepDesc.text = recipeStepList[index].desc

                view.visibility = View.GONE
                if (stepDoneList[index] == 1) {
                    binding.clNextStep.visibility = View.GONE
                }
                currentRunningStep = index

                if ((index + 1) <= stepProgressList.lastIndex) {
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            } else if (totalTime == (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)
                && isManualScroll
            ) {
                toggleManualScrolling()
                view.visibility = View.GONE
                if (stepDoneList[index] == 1) {
                    binding.clNextStep.visibility = View.GONE
                }
                if ((index + 1) <= stepProgressList.lastIndex) {
                    //nextExpectedProgressView = stepProgressList[index + 1] - 1
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            } else if (totalTime > recipeStepList[index].durationInSec) {
                if (stepDoneList[index] == 1) {
                    view.visibility = View.VISIBLE
                    //completed
                    val top: Float = (currentPro / 100f) * (binding.stepProgress.getBarHeight())
                    //val top: Float = (view.id / 100f) * (stepProgress.getBarHeight())
                    (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                        (top.roundToInt() + seekBarTop) - view.height + (binding.stepProgress.getBarWidth() / 2).toInt()
                    (view as ItemStepView).setCompletedProgressView()
                }
            } else if (totalTime < recipeStepList[index].durationInSec) {
                view.visibility = View.VISIBLE
                //remain
                val top: Float = (currentPro / 100f) * (binding.stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt()
                (view as ItemStepView).setRemainProgressView()
            }
        }
    }

    private fun updateDetailsView(currentProgress: Float) {
        //if (!isManualScroll) {
        binding.tvBottomStepTime.text = getCurrentTime()
        //}

        val totalTime = stepSums - remainSecond
        if (totalTime >= nextExpectedProgressView && totalTime <= (nextExpectedProgressView + 6)) {
            if ((currentStep + 1) <= recipeStepList.size) {
                binding.clNextStep.visibility = View.VISIBLE
                val index = currentStep
                binding.tvDonePreAdd.isEnabled = true
                binding.tvStepTitle.text = recipeStepList[index].name
                binding.tvStepDescDash.text = recipeStepList[index].desc
                binding.tvTimeIn.visibility = View.VISIBLE
                binding.tvNextIn.text = "Next in"
                binding.tvTimeIn.text = "${(nextExpectedProgressView + 6) - totalTime} sec"
            }
        }

        if (totalTime == stepSums) {
            togglePlayPause()
            for (view in binding.viewCurrentProContainer.children()) {
                view.visibility = View.GONE
            }
            binding.clCurrentStep.visibility = View.GONE
            binding.viewDetailsBottomSheet.visibility = View.GONE
            binding.viewIndicatorCurrentStep.visibility = View.GONE
            binding.clCurrentRecipe.visibility = View.GONE
            binding.viewConnectorCurrentStep.visibility = View.GONE
        } else {
            updateViews()
        }

        if (currentProgress < 100 && isManualScroll && (binding.viewContainer.children()
                .isNotEmpty() && currentProgress >= binding.viewContainer.children()[0].id)
        ) {
            binding.viewCurrentProContainer.children().withIndex().map {
                val currentPro = ((totalTime * 100) / stepSums.toFloat()).Round()
                val top: Float = (currentPro / 100f) * (binding.stepProgress.getBarHeight())
                //val top: Float = (currentProgress / 100f) * (stepProgress.getBarHeight())

                (it.value.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.toInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt()
                it.value.visibility = View.VISIBLE
//                it.value.findViewById<ImageView>(R.id.ivStepImage)
//                    .setImageResource(recipeStepList[currentRunningStep].image)
                it.value.findViewById<AppCompatTextView>(R.id.tvStepName).text =
                    recipeStepList[currentRunningStep].name
                it.value.findViewById<AppCompatTextView>(R.id.viewIndicatorTop).text =
                    getCurrentTime()
                it.value.findViewById<AppCompatTextView>(R.id.tvStepDesc).text =
                    recipeStepList[currentRunningStep].desc
                it.value.findViewById<AppCompatTextView>(R.id.tvTime).text =
                    recipeStepList[currentRunningStep].duration
            }

            binding.clCurrentStep.visibility = View.VISIBLE
            binding.tvCurrentStepTime.text = getCurrentTime()
            binding.tvBottomTitle.text = recipeStepList[currentRunningStep].name
            binding.tvBottomDesc.text = recipeStepList[currentRunningStep].desc
        } else {
            for (view in binding.viewCurrentProContainer.children()) {
                view.visibility = View.GONE
            }
            binding.clCurrentStep.visibility = View.GONE
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
            for ((index, view) in binding.viewContainer.children.withIndex()) {
                view.id = stepProgressList[index].toInt()
                val top: Float =
                    (stepProgressList[index] / 100f) * (binding.stepProgress.getBarHeight())
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.roundToInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt()
            }
        } else {
            binding.viewContainer.removeAllViews()
            binding.viewCurrentProContainer.removeAllViews()

            val rootView = ItemStepView(requireContext())
            rootView.setCurrentProgressView(0)
            rootView.setCurrentProgressView(0)
            rootView.setLayoutParam((0 + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt())
            binding.viewCurrentProContainer.addView(rootView)

            for ((index, progress) in stepProgressList.withIndex()) {
                val top: Float = (progress / 100f) * (binding.stepProgress.getBarHeight())

                val rootView = ItemStepView(requireContext())
                rootView.setDefaultView(
                    progress.toInt(),
                    recipeStepList[index].name,
                    recipeStepList[index].desc,
                    recipeStepList[index].duration,
                    "${index + 1}",
                    recipeStepList[index].action,
                    recipeStepList[index].image
                )
                rootView.setLayoutParam((top.roundToInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt())
                binding.viewContainer.addView(rootView)
            }
        }
    }

    private fun prepareStepView(index: Int) {
        if (index - 1 >= 0 && binding.stepProgress.currentProgress > 0 && binding.stepProgress.currentProgress < 100) {
            manageManualScroll(index - 1)
        }
    }

    private fun setupAnimator() {
        toggleManualScrolling(false)
        binding.viewStoveProgress.progressSample = getDummyWaveSample(false)
        binding.viewMicrowaveProgress.progressSample = getDummyWaveSample(true)

        pulseAnimator =
            AnimatorInflater.loadAnimator(context, R.animator.fading_pulse) as AnimatorSet

        pulseAnimatorCurrentStep = pulseAnimator!!.clone()
        pulseAnimatorCurrentStep!!.setTarget(binding.ivCurrentPulseBg)
        pulseAnimatorCurrentStep!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                animator.start()
            }
        })

        pulseAnimator!!.setTarget(binding.ivPulseBg)
        pulseAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                animator.start()
            }
        })
    }

    private fun pausePlayer(isSendMessage: Boolean = false) {
        if (isSendMessage) {
            sendMessage("stop=2")
        }
        binding.ivPlayPause.setImageResource(R.drawable.ic_play)
        stopAnimation()
        stopScheduleTimer()
        isPlaying = false
    }

    private fun stopScheduleTimer() {
        schedulTimer?.cancel()
        schedulTimer = null
    }

    private fun playPlayer(isSendMessage: Boolean = false) {
        if (isSendMessage) {
            sendMessage("stop=0")
        }
        if (binding.clPrePrepare.visibility == View.VISIBLE) {
            togglePrePareSteps(false)
            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
            Thread {
                activity?.runOnUiThread {
                    prepareDetailsView(true)
                }
            }.start()
        }
        binding.ivPulseBg.visibility = View.VISIBLE
        binding.ivCenterPlay.visibility = View.VISIBLE
        binding.ivPlayPause.setImageResource(R.drawable.ic_pause)
        startAnimation()
        prepareTimer()
        isPlaying = true
    }

    private fun togglePlayPause(isSendMessageAction: Boolean = false) {
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
            ((binding.stepProgress.currentProgress / binding.stepProgress.totalProgress.toFloat()) *
                    (binding.stepProgress.progressBarHeight))
        binding.nestedScrollSeekBar.scrollTo(
            binding.nestedScrollSeekBar.scrollX,
            progress.toInt()
        )
    }

    private fun performHandlerAction() {
        if (binding.stepProgress.currentProgress >= 100) {
            stopScheduleTimer()
            return
        }

        binding.stepProgress.currentProgress += increment
        binding.viewStoveProgress.progress += increment
        binding.viewMicrowaveProgress.progress += increment

        if (!isManualScroll) {
            scrollToPos()
        }
        setStoveMVProgress()
        remainSecond -= 1
        stepProg += 1
        setStepTime(true)
        onCurrentProgress(binding.stepProgress.currentProgress)
        updateDetailsView(decimalFormat.format(binding.stepProgress.currentProgress).toFloat())

        if (currentStep <= recipeStepList.size) {
            val totalTime = stepSums - remainSecond
            if (totalTime == ((recipeStepList[currentStep - 1]).durationInSec + recipeStepList[currentStep - 1].stepDuration) && stepDoneList[currentStep - 1] == 0) {
                pausePlayer()
                currentStep += 1

                if (currentStep <= recipeStepList.size) {
                    //Speak Details of next step...
                    OnToCookApplication.instance.speak(recipeStepList[currentStep - 1].app_audio + ".")

                    //To reset remain time view...
                    if (currentStep <= recipeStepList.size) {
                        binding.tvTimeIn.text = ""
                        binding.tvTimeIn.visibility = View.GONE
                        binding.tvNextIn.text = recipeStepList[currentStep - 1].action
                    }
                }
            }
        }
    }

    private fun setStoveMVProgress() {
        binding.arcProgressMv.progress =
            binding.viewStoveProgress.sample?.get(binding.viewStoveProgress.progress.toInt())
                ?.toFloat()!!
        binding.arcProgressFlam.progress =
            binding.viewMicrowaveProgress.sample?.get(binding.viewMicrowaveProgress.progress.toInt())
                ?.toFloat()!!
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver)
        if (isPlaying) {
            togglePlayPause(true)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
    }

    private fun getDummyWaveSample(isMagnetron: Boolean = false): IntArray {
        val data = IntArray(stepSums)
        var dataIndex = 0

        for ((index, recipeObj) in recipeStepList.withIndex()) {
            //Divide by 3 because we have sey height of bar as 3 second (1 bar  = 3 sec..)
            for (i in 1..(recipeObj.stepDuration / 3)) {
                if (isMagnetron) {
                    data[dataIndex] = recipe!!.Instruction[index].Magnetron_power.toInt()
                } else {
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

    private fun onCurrentProgress(progress: Float) {
        val top: Float = (progress / 100f) * (binding.stepProgress.getBarHeight())
        val layoutParam = FrameLayout.LayoutParams(
            binding.ivCurrentProgress.width,
            binding.ivCurrentProgress.height
        )
        layoutParam.gravity = Gravity.CENTER_HORIZONTAL
        layoutParam.topMargin = top.toInt() + seekBarTop - (Constants.getDp(
            26,
            requireContext()
        ) / 2)
        binding.ivCurrentProgress.layoutParams = layoutParam
        binding.ivCurrentPulseBg.layoutParams = layoutParam
    }

//    override fun onBackPressed() {
//        if (stepProgress.currentProgress < 99f) {
//            val alertDialog: android.app.AlertDialog? =
//                android.app.AlertDialog.Builder(requireContext()).create()
//            alertDialog?.setTitle("Close Current Recipe")
//            alertDialog?.setMessage("Are you sure you want to close current recipe?")
//            alertDialog?.setButton(
//                AlertDialog.BUTTON_POSITIVE, "Yes"
//            ) { dialog, _ ->
//                //stop playing - set to only call on stop not on pause
//                if (isPlaying) {
//                    togglePlayPause()
//                }
//                dialog.dismiss()
//                sendMessage("stop=1")
//                super.onBackPressed()
//            }
//            alertDialog?.setButton(
//                AlertDialog.BUTTON_NEGATIVE,
//                "No"
//            ) { dialog, _ ->
//                dialog.dismiss()
//            }
//            alertDialog?.show()
//        } else {
//            pausePlayer()
//            super.onBackPressed()
//        }
//    }

    //TODO Restructure
    private fun parseData(intent: Intent) {
        when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                println("$TAG message  $message")
                binding.tvDonePreAdd.isEnabled = true

                when {
                    //Command For Device To App
                    message.lowercase().contains("recipe=") -> {
                        if (!message.contains(",")) {
                            if (message.uppercase() == "RECIPE=COMPLETE") {
                                if (isPlaying) {
                                    Log.e(TAG, "onReceive: togglePlayPause1")
                                    togglePlayPause()
                                }
                                sendMessage("stop=1")
                                Constants.nextDescription = ""

//                                    OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
                                //TODO Restructure
//                                openDialog()
                            }
                        } else {
                            val recipeName =
                                message.split(",")[0].lowercase().replace("recipe=", "")
                            val cmdSize: Int = message.split(",").size

                            val stepNo =
                                if (cmdSize > 2) message.split(",")[2].lowercase()
                                    .replace("stepno=", "") else "0"
                            val mode =
                                if (cmdSize > 1) message.split(",")[1].uppercase()
                                    .replace("MODE=", "").uppercase() else ""
                            val foundRecipe = Constants.RECIPES.filter {
                                it.name[0].lowercase() == message.split(",")[0].lowercase()
                                    .replace("recipe=", "")
                            }
                            when (mode) {
                                Constants.INGREDIENT_MODE -> {
                                    if (recipeName == recipe!!.name[0].lowercase()) {
                                        //TODO Service
//                                                    OnToCookApplication.instance.bleBoundService.initRecipe(
//                                                        recentItem!!
//                                                    )
                                        currentPrepareStep = stepNo.toInt() - 1
                                        Constants.currentPrepareStep =
                                            currentPrepareStep
                                        togglePrePareSteps(true)
                                        preparePrePareStep()
                                        prepareRecipeSteps()
                                        prepareProgressbar()
                                        setStepTime(true)
                                        prepareStepView(0)
                                        Log.e(
                                            TAG,
                                            "onReceive: ${binding.stepProgress.currentProgress}"
                                        )
                                        binding.ivPulseBg.visibility = View.GONE
                                        binding.ivCenterPlay.visibility = View.GONE
                                        binding.ivPlayPause.setImageResource(R.drawable.ic_play)
                                    } else {
                                        if (foundRecipe.isNotEmpty()) {
                                            val intent = Intent(
                                                requireContext(),
                                                OldDashboardActivity::class.java
                                            )
                                            intent.putExtra("recipe", foundRecipe[0])
                                            intent.putExtra("isResume", true)
                                            intent.putExtra(
                                                "prepreparestep", stepNo.toInt() - 1
                                            )
                                            intent.putExtra("isPrepareRunning", true)
                                            startActivity(intent)
                                        }
                                    }
                                }
                                Constants.COOKING_MODE -> {
                                    if (cmdSize > 4) {
                                        val indTime = message.split(",")[3].lowercase()
                                            .replace("ind_run=", "")
                                        val magTime = message.split(",")[4].lowercase()
                                            .replace("mag_run=", "")
                                        Log.e(
                                            TAG,
                                            "onReceive: Same recipeName $recipeName"
                                        )
                                        Log.e(
                                            TAG,
                                            "onReceive: Same recipeName ${recipe!!.name[0].lowercase()}"
                                        )
                                        if (recipeName == recipe!!.name[0].lowercase()) {
                                            Log.e(TAG, "onReceive: Same recipeName")
                                            currentStep = stepNo.toInt()
                                            togglePrePareSteps(false)
                                            Constants.currentStep = currentStep
                                            val status =
                                                message.split(",")[5].lowercase()
                                                    .replace("status=", "")

                                            isPlaying = indTime.toInt()
                                                .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                            //TODO Service
//                                                        OnToCookApplication.instance.bleBoundService.initRecipe(
//                                                            recentItem!!
//                                                        )
                                            if (isPlaying) {
                                                if (indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) > recipeStepList[currentStep - 1].stepDuration
                                                ) {
                                                    Log.e(TAG, "init: UpdateRecipe")
                                                    //TODO Restructure
//                                                    completeStep(currentStep)
                                                    stepProg = 0 //temp solution
//                                                    updateRecipe2(
//                                                        currentStep - 1,
//                                                        indTime.toInt()
//                                                            .coerceAtLeast(magTime.toInt()),
//                                                        indTime.toInt(),
//                                                        magTime.toInt()
//                                                    )
                                                } else {
                                                    prepareRecipeSteps()
                                                    if (indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()) != 0
                                                    ) fastForward(
                                                        currentStep,
                                                        indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()),
                                                        true
                                                    )
                                                    Log.e(TAG, "init: UpdateRecipe FF")
                                                }
                                                nextExpectedProgressView =
                                                    (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6
                                                updateDetailsView(
                                                    decimalFormat.format(binding.stepProgress.currentProgress)
                                                        .toFloat()
                                                )
                                                binding.ivPlayPause.setImageResource(R.drawable.ic_pause)
                                            } else {
                                                prepareRecipeSteps()
                                                jumpToStep(currentStep)
                                                Log.e(
                                                    TAG,
                                                    "init: RemainSecond 02. $remainSecond"
                                                )
                                                if (!isManualScroll) {
                                                    scrollToPos()
                                                }
                                                var totalDuration = 0
                                                for (i in 0 until currentStep - 1) {
                                                    totalDuration += recipeStepList[i].stepDuration
                                                    stepDoneList[i] = 1
                                                }
                                                remainSecond -= totalDuration
                                                binding.ivPulseBg.visibility =
                                                    View.VISIBLE
                                                binding.ivCenterPlay.visibility =
                                                    View.VISIBLE
                                                binding.ivPlayPause.setImageResource(R.drawable.ic_play)
                                                nextExpectedProgressView =
                                                    (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6
                                                if (indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) != 0
                                                ) fastForward(
                                                    currentStep,
                                                    indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()),
                                                    false
                                                )
                                                updateViews()
                                                binding.stepProgress.markers =
                                                    stepProgressList
                                                setStoveMVProgress()
                                                onCurrentProgress(binding.stepProgress.currentProgress)
                                                updateDetailsView(stepProgressList[currentStep - 1])
                                                setStepTime(true)
                                            }
                                        } else {
                                            Log.e(TAG, "onReceive: NotSame recipeName")
                                            if (foundRecipe.isNotEmpty()) {
//                                                        finish()
                                                val intent = Intent(
                                                    requireContext(),
                                                    OldDashboardActivity::class.java
                                                )
                                                intent.putExtra(
                                                    "recipe",
                                                    foundRecipe[0]
                                                )
                                                intent.putExtra("isResume", true)
                                                intent.putExtra(
                                                    "currentStep", stepNo.toInt()
                                                )
                                                intent.putExtra(
                                                    "isPrepareRunning",
                                                    false
                                                )
                                                intent.putExtra(
                                                    "isPlaying",
                                                    indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) != 0
                                                )
                                                intent.putExtra(
                                                    "changeTime",
                                                    indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt())
                                                )
                                                startActivity(intent)
                                            }
                                        }
                                    }
                                }
//                                        else -> onBackPressed()
                            }
                        }
                    }

                    message.uppercase().contains("INGRE=") -> {
                        binding.btnNext.isEnabled = true
                        binding.btnSkip.isEnabled = true

                        var ingreStep =
                            message.uppercase().toString().replace("INGRE=", "")
                        currentPrepareStep = ingreStep.toInt()
                        Constants.currentPrepareStep = ingreStep.toInt()
                        if (currentPrepareStep <= totalPrepareStep) {
                            val step =
                                "Step ${currentPrepareStep + 1} of ${totalPrepareStep + 1}"
                            binding.tvStep.text = step
                            binding.tvPrePrepareTitle.text =
                                recipe!!.Ingredients[currentPrepareStep].title
                            binding.tvPrePrepareDesc.text =
                                recipe!!.Ingredients[currentPrepareStep].text
                            //TODO Service
//                                        OnToCookApplication.instance.bleBoundService.notifyUpdate(
//                                            "${recipe!!.ingredients[currentPrepareStep].title}  ",
//                                            recipe!!.ingredients[currentPrepareStep].text
//                                        )
                            if (currentPrepareStep == totalPrepareStep) {
                                binding.btnNext.text = "Done"
                            } else {
                                binding.btnNext.text = "Next"
                            }
                            if (currentPrepareStep == 0) {
                                binding.btnSkip.text = "Skip"
                            } else binding.btnSkip.text = "Previous"
                        }
                    }
                    message.uppercase().contains("INSTR=") -> {
                        if (message.contains(",")) {
                            val instrStep =
                                message.split(",")[0].uppercase().replace("INSTR=", "")
                            if (instrStep.toInt() != 0) {
                                val totalStep =
                                    message.split(",")[1].uppercase()
                                        .replace("COUNT=", "")
                                currentStep = instrStep.toInt()
                                if (totalStep.toInt() != currentStep.toInt() - 1) {
                                    currentStep++
                                    //TODO Restructure
//                                    completeStep(currentStep)
                                }
//                                        stepDoneList[instrStep.toInt() - 1] = 1
                            }
                        }
                    }
                    message.uppercase().contains("INSTR_RUN=") -> {
                        if (message.uppercase() == "INSTR_RUN=COMPLETE") {
//                                    if (currentStep <= recipeStepList.size) {
//                                        tvTimeIn.text = ""
//                                        tvTimeIn.visibility = View.GONE
//                                        tvNextIn.text = recipeStepList[currentStep - 1].action
//                                        if (currentStep == recipeStepList.size) nextDescription =
//                                            recipeStepList[currentStep - 1].app_audio
//                                        else nextDescription = recipeStepList[currentStep].app_audio
//                                        tvStepTitle.text = recipeStepList[currentStep - 1].name
//                                        tvStepDescDash.text = recipeStepList[currentStep - 1].desc
//                                    } else {
//                                        nextDescription = ""
//                                    }
//                                    clNextStep.visibility = View.VISIBLE
                            pausePlayer()
                            stepProg = 0
                            Log.e(TAG, "onReceive: togglePlayPause1$currentStep")
                        } else {
                            if (message.uppercase() == "INSTR_RUN=START") {
                                LoadingUtils.hideDialog()
                                binding.clNextStep.visibility = View.GONE
                                if (!isPlaying) playPlayer()
                            }

                        }
                    }
                    message.lowercase().contains("info=") -> {
                        var messageChunk =
                            message.lowercase().toString().replace("info=", "")
                                .split(",")

                        var recipeNo = messageChunk[0]
                        var ingredient =
                            if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                        var stepno =
                            if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                        var second =
                            if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                        var foundRecipe =
                            Constants.RECIPES.filter { it.id.toString() == recipeNo }
                        if (foundRecipe.isNotEmpty()) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                fastForward(
                                    stepno, second, true
                                )
                            }, 100)
                        }
                    }

                    message.lowercase().contains("complete") -> {
                        Log.e("TAG", "onReceive: updateDetailsView $message")
                        if (message.uppercase() != "INSTR_RUN=COMPLETE") updateDetailsView(
                            decimalFormat.format(binding.stepProgress.currentProgress)
                                .toFloat()
                        )
                        else {
                            Log.e(TAG, "onReceive:setHandler $stepSums")
                            Log.e(TAG, "onReceive:setHandler $remainSecond")
                        }
                    }
                    message.uppercase() == "MANUAL MODE" -> {
                        Log.e(TAG, "onReceive: Manual Mode")
                        val alertDialog: android.app.AlertDialog? =
                            android.app.AlertDialog.Builder(context).create()
                        pausePlayer()
                        alertDialog?.setTitle("Manual Mode Started")
                        alertDialog?.setMessage("Please Cook Manually.")
                        alertDialog?.setButton(
                            AlertDialog.BUTTON_POSITIVE, "Cook"
                        ) { dialog, _ ->
                            //TODO service
//                                        OnToCookApplication.instance.bleBoundService.notifyManualMode()
                            Constants.currentStep = 1
                            dialog.dismiss()
                            val intent =
                                Intent(requireContext(), MainActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                        }
                        alertDialog?.setCancelable(false)
                        alertDialog?.show()
                    }
                    message.lowercase()
                        .contains("add_confirm") && message.lowercase() != "add_confirm=0" -> {
                        Log.e("TAG", "onReceive: add_confirm")
                        if (binding.stepProgress.currentProgress >= 100) {
                            OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
                            Log.e("TAG", "performHandlerAction: pausePlayer 1")

                            pausePlayer()
                            //TODO
//                                    finish()
                            return
                        }
                        var pos = currentRunningStep - 1
                        if (pos <= 0) {
                            pos = 0
                        }
                        Log.e("TAG", "stepDoneList1: $pos")

                        stepDoneList[pos] = 1
                        stepProg = 0
                        OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
                        binding.clNextStep.visibility = View.GONE
                        Log.e(TAG, "performDoneButtonAction:clNextStep 2")
                        Log.e(TAG, "onReceive: Start Progress11")
                    }
                    message.lowercase().contains("ingredients") -> {
                        currentPrepareStep =
                            message.lowercase().replace("ingredients=", "").toInt()

                        binding.btnNext.text = "Next"
                        binding.btnSkip.text = "Previous"
                        Log.e(TAG, "onReceive: Satrtinggg")
                        if ((currentPrepareStep - 1) == totalPrepareStep) {
                            Log.e(TAG, "onReceive: togglePlayPause1")
                            togglePlayPause()
                            OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                            sendMessage("ingredients=100")
                        } else {
                            if (currentPrepareStep == totalPrepareStep) {
                                binding.btnNext.text = "Done"
                            }
                            if (currentPrepareStep == 0) {
                                binding.btnSkip.text = "Skip"
                            }
                            preparePrePareStep()
                        }
                    }
                    message.lowercase().contains("stop") -> {
                        var stopAction = message.lowercase().replace("stop=", "")
                        if (stopAction == "0") {
                            if (!isPlaying) {
                                playPlayer()
                            }
                        } else if (stopAction == "1") {
                            Log.e(TAG, "onReceive: togglePlayPause2")
                            if (isPlaying) {
                                togglePlayPause()
                            }
                            //TODO
//                                    finish()
                        } else if (stopAction == "2") {
                            if (isPlaying) {
                                Log.e("TAG", "performHandlerAction: pausePlayer 2")

                                pausePlayer()
                            }
                        } else if (stopAction == "100") {

                            stopScheduleTimer()
                            //TODO
//                                    onBackPressed()
                        }
                    }
                    message.lowercase().contains("ack_command") -> {
//                                btnNext.isEnabled = true
//                                btnSkip.isEnabled = true
                        if (isClickDone) {
                            isClickDone = false
                            Log.e(TAG, "onReceive: Start Progress")
                            Log.e(TAG, "onReceive: Start $currentStep")
                            Log.e(TAG, "onReceive: Start ${recipeStepList.size}")
                            if ((stepSums - remainSecond) == stepSums) {
                                return
                            }
                            OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
                        }
                    }
                    message.uppercase().contains("INDQUICKSTART=") -> {
                        if (Constants.checkNavigation(message)) {
                            val intent = Intent(
                                requireContext(), CookingActivity::class.java
                            )
                            startActivity(intent)
                        }
                    }

                    message.lowercase().contains("magtime=") -> {
                        val magnetronOnTime =
                            message.lowercase().split(",")[0].replace("magtime=", "")
                                .toInt()
                        val inductionOnTime =
                            message.lowercase().split(",")[1].replace("indtime=", "")
                                .toInt()
                        Log.e(TAG, "onReceive: currentStep$currentStep")
                        if (inductionOnTime.coerceAtLeast(magnetronOnTime) == 0) {
                            currentStep++
                            //TODO Restructure
//                            completeStep(currentStep)
                        } else {
                            if (!isPlaying) {
                                currentStep--
                            }
                            binding.clNextStep.visibility = View.GONE
                            //TODO Restructure
//                            updateRecipe2(
//                                currentStep - 1,
//                                inductionOnTime.toInt()
//                                    .coerceAtLeast(magnetronOnTime.toInt()),
//                                inductionOnTime.toInt(),
//                                magnetronOnTime.toInt()
//                            )
                        }
                    }
                    message.uppercase().contains("MAG_RUN=") -> {
                        if (message.contains(",")) {
                            if (message.split(",")[0].uppercase()
                                    .replace("MAG_RUN=", "") == "PAUSE"
                            ) {
                                binding.ivPlayPause.isEnabled = false
                                pausePlayer()
                            } else {
                                binding.ivPlayPause.isEnabled = true
                                playPlayer()
                            }
                        }

                    }
                    message.equals("workstatus=idle", true) -> {
//                                    onBackPressed()
                    }

                }


            }
            Constants.EVENT_BLE_CONNECTION_ERROR -> {
                var message =
                    intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
//                        bleStateView1.state = BluetoothState.State.OFF

            }
            Constants.EVENT_BLE_CONNECTION_ABORT -> {
//                        bleStateView1.state = BluetoothState.State.OFF
//                        showSnackbarLong(resources.getText(R.string.device_disconnect))
                Constants.prepareScanObserver()
            }
            Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                binding.btnNext.isEnabled = true
                binding.btnSkip.isEnabled = true
                binding.tvDonePreAdd.isEnabled = true
//                        bleStateView1.state = BluetoothState.State.CONNECTED
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CookingFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            OldCookingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}