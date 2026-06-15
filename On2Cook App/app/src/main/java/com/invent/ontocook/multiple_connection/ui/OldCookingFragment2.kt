package com.invent.ontocook.multiple_connection.ui

/*Old Ingredients Viewing Single Item*/

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
import android.widget.ImageView
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
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.DetailsPagerAdapter
import com.invent.ontocook.databinding.FragmentCookingOld2Binding
import com.invent.ontocook.dialog.DialogEditStepTime
import com.invent.ontocook.extension.showSnackBarShort
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.RecipeSteps
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.sequence.children
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.Round
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.isBluetoothOn
import com.invent.ontocook.view.ItemStepView
import com.masoudss.lib.Utils
import com.masoudss.lib.WaveGravity
import kotlinx.android.synthetic.main.activity_dashboard.stepProgress
import params.com.stepprogressview.StepProgressView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import kotlin.math.roundToInt

private const val ARG_PARAM1 = "recipeItem"
private const val ARG_PARAM2 = "MAC_ADDRESS"

class OldCookingFragment2 : Fragment(), StepProgressView.OnCurrentStepHighlight {
    private lateinit var binding: FragmentCookingOld2Binding

    //stop=0 - resume    stop=1  //stop     stop=2   //pause

    private var pulseAnimator: AnimatorSet? = null
    private var pulseAnimatorCurrentStep: AnimatorSet? = null
    private var increment = 1f

    //    var schedulTimer: Timer? = null
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

    var totalPrepareStep = 3
    var currentStep = 1
    var currentRunningStep = 0
    private var remainSecond = 0
    private var changeTime = 0

    val listRecipe: ArrayList<Recipe> = ArrayList()
    var isPrepareRunning = true
    var stepProg = 0
    private var nextExpectedProgressView = 0
    var recipe: Recipe? = null

    private var macAddress: String = ""
    var recentItem: RecentItem? = null
    var isClickDone = false
    lateinit var broadcastReceiver: BroadcastReceiver
    var isPause = false
    var isResume = false
    val TAG = this::class.java.simpleName
    var currentPrepareStep = 0

    lateinit var service: BleService
    val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    //Toggle PrePrepare Step Visibility
    private fun togglePrePareSteps(isVisible: Boolean) {
        Log.e(TAG, "togglePrePareSteps:isPrepareRunning $isVisible")
        isPrepareRunning = isVisible
        Constants.isPrepareRunning = isVisible
        if (isVisible) {
            context?.let { ContextCompat.getColor(it, R.color.dark_grey) }
                ?.let { binding.nestedScrollSeekBar.setBackgroundColor(it) }
            binding.clPrePrepare.visibility = View.VISIBLE
        } else {
            context?.let { ContextCompat.getColor(it, R.color.white) }
                ?.let { binding.nestedScrollSeekBar.setBackgroundColor(it) }
            binding.clPrePrepare.visibility = View.GONE
        }
    }

    private fun setLayoutParam(topMargin: Int, bottomMargin: Int? = null) {
        val layoutParam = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParam.topMargin = topMargin
        if (bottomMargin != null) {
            layoutParam.bottomMargin = bottomMargin
        }
        seekBarTop = topMargin
        binding.clSeekBarView.layoutParams = layoutParam
    }

    private fun prepareProgressbar(isResetProgress: Boolean = true) {
        stepSums = recipeStepList.sumOf { it.stepDuration }
        recipeStepList.map {
            Log.e(TAG, "prepareProgressbar: ${it.stepDuration}")
            Log.e(TAG, "prepareProgressbar: ${it.durationInSec}")
            Log.e(TAG, "prepareProgressbar: ${it.name}")
        }
        Log.e(TAG, "prepareProgressbar:StepsSums $stepSums")

        if (isResetProgress) {
            val totalProgress = stepSums - remainSecond
            val pro = (totalProgress * 100) / stepSums.toFloat()
            binding.stepProgress.currentProgress = pro.Round()
            binding.viewStoveProgress.progress = pro.Round()
            Log.e(TAG, "viewMicrowaveProgress:1 ${pro.Round()}")
            binding.viewMicrowaveProgress.progress = pro.Round()
        }

        //setStepTime()
        binding.stepProgress.progressBarHeight =
            Constants.getDp(stepSums * 3, requireContext()).toFloat()
        binding.viewMicrowaveProgress.sample = getDummyWaveSample(true)
        binding.viewStoveProgress.sample = getDummyWaveSample(false)
        binding.viewStoveProgress.progressSample = getDummyWaveSample(false)
        binding.viewMicrowaveProgress.progressSample = getDummyWaveSample(true)
        increment = 100f / stepSums

        stepProgressList.clear()
        stepProgressList.addAll(recipeStepList.map { ((it.durationInSec * 100) / stepSums.toFloat()).Round() })
    }

    private fun updateRecStep(step: Int, duration: Int) {
        stopAnimation()
        Log.e("TAG", "performHandlerAction: stopScheduleTimer1")

        stopScheduleTimer()
        binding.viewDetailsBottomSheet.collapse()

        remainSecond -= recipeStepList[step].stepDuration

        var nextStepPro = 0
        for (i in step until recipeStepList.size) {
            if (i == step) {
                recipeStepList[i].stepDuration = duration
                recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                nextStepPro = recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
            } else {
                recipeStepList[i].durationInSec = nextStepPro
                nextStepPro += recipeStepList[i].stepDuration
            }
        }
        Log.e(TAG, "updateRecStep:Before $remainSecond")

        remainSecond += duration
        remainSecond += stepProg
        Log.e(TAG, "updateRecStep: $remainSecond")
        Log.e(TAG, "updateRecStep: $duration")
        Log.e(TAG, "updateRecStep:StepProgress $stepProg")
        Constants.remainSecond = remainSecond
        prepareProgressbar()

        prepareDetailsView(true)
        Handler(Looper.getMainLooper()).postDelayed({
            startAnimation()
            prepareTimer()
            updateDetailsView(stepProgressList[currentStep - 1])
        }, 10)
        showSnackBarShort("Updated recipe with $duration seconds")
    }

    private fun updateRec(step: Int, duration: Int) {
        if (recipeStepList[step].stepDuration - stepProg > duration) {
            stopAnimation()
            Log.e("TAG", "performHandlerAction: stopScheduleTimer2")

            stopScheduleTimer()
            binding.viewDetailsBottomSheet.collapse()

            //remainSecond -= recipeStepList[step].stepDuration
            remainSecond -= (recipeStepList[step].stepDuration - stepProg)

            var nextStepPro = 0
            for (i in step until recipeStepList.size) {
                if (i == step) {
                    recipeStepList[i].stepDuration = stepProg + duration
                    recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                    nextStepPro = recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
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
//        schedulTimer = Timer()
//        Handler(Looper.getMainLooper()).postDelayed({
        Log.e(TAG, "prepareTimer: CheckTimer CheckIng $remainSecond")
        service.startTimer(macAddress, activity)
//        }, 2000)
//        schedulTimer?.scheduleAtFixedRate(object : TimerTask() {
//            override fun run() {
//                activity.runOnUiThread {
//                    OnToCookApplication.instance.bleBoundService.prepareNotifyTimer()
//                }
//            }
//        }, 0, 1000)
    }

    //0 - increment  1 - decrement
    private fun updateRecipe(
        step: Int, duration: Int, incrementDec: Int = 0, indTime: Int, magTime: Int
    ) {
        if (incrementDec == 1 && recipeStepList[step].stepDuration - stepProg < (duration + 3)) {
            showSnackBarShort("$duration second is to short for update")
        } else {
            stopAnimation()
            Log.e("TAG", "performHandlerAction: stopScheduleTimer3")

            stopScheduleTimer()
            binding.viewDetailsBottomSheet.collapse()

            var nextStepPro = 0
            var durationToMinus = 0
            for (i in step until recipeStepList.size) {
                if (i == step) {
                    if (incrementDec == 0) {
                        Log.e("TAG", "updateRecipe: incrementDec=0 duration $duration")
                        recipe!!.Instruction[i].Induction_on_time =
                            (recipe!!.Instruction[i].Induction_on_time.toInt() + indTime.toInt()).toString()
                        recipe!!.Instruction[i].Magnetron_on_time =
                            (recipe!!.Instruction[i].Magnetron_on_time.toInt() + magTime.toInt()).toString()
                        val indTimes = recipe!!.Instruction[i].Induction_on_time.toInt()
//                        indTimes += indTime
                        val magTimes = recipe!!.Instruction[i].Magnetron_on_time.toInt()
//                        magTimes += magTime
//                        recipeStepList[i].stepDuration = recipeStepList[i].stepDuration + duration
                        if (recipeStepList[i].stepDuration < indTimes.coerceAtLeast(magTimes)) {
                            durationToMinus =
                                indTimes.coerceAtLeast(magTimes) - recipeStepList[i].stepDuration
                            Log.e(TAG, "onButtonClick:durationToMinus $durationToMinus")

                        }
                        recipeStepList[i].stepDuration = indTimes.coerceAtLeast(magTimes)
                    } else {
                        recipe!!.Instruction[i].Induction_on_time =
                            (recipe!!.Instruction[i].Induction_on_time.toInt() - indTime.toInt()).toString()
                        recipe!!.Instruction[i].Magnetron_on_time =
                            (recipe!!.Instruction[i].Magnetron_on_time.toInt() - magTime.toInt()).toString()
                        var indTimes = recipe!!.Instruction[i].Induction_on_time.toInt()
//                        indTimes -= indTime
                        var magTimes = recipe!!.Instruction[i].Magnetron_on_time.toInt()
//                        magTimes -= magTime
                        Log.e(TAG, "onButtonClick: Corse ${indTimes.coerceAtLeast(magTimes)}")
                        Log.e(
                            TAG, "onButtonClick: eStepDuration: ${recipeStepList[i].stepDuration}"
                        )
                        if (recipeStepList[i].stepDuration > indTimes.coerceAtLeast(magTimes)) {
                            durationToMinus =
                                recipeStepList[i].stepDuration - indTimes.coerceAtLeast(magTimes)
                            Log.e(TAG, "onButtonClick:durationToPlus $durationToMinus")

                        }
//                        recipeStepList[i].stepDuration = recipeStepList[i].stepDuration - duration
                        recipeStepList[i].stepDuration = indTimes.coerceAtLeast(magTimes)
                    }

                    Log.e(
                        "TAG",
                        "updateRecipe: incrementDec=0 duration.. ${recipeStepList[step].stepDuration}"
                    )
                    recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                    nextStepPro = recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
                } else {
                    recipeStepList[i].durationInSec = nextStepPro
                    nextStepPro += recipeStepList[i].stepDuration
                }
            }

            if (incrementDec == 0) {
                Log.e("TAG", "updateRecipe:Inc remainSecond $remainSecond")
                if (durationToMinus != 0) remainSecond += durationToMinus
//                remainSecond += duration
                Log.e("TAG", "updateRecipe:Inc remainSecond $remainSecond")
            } else {
                if (durationToMinus != 0) remainSecond -= durationToMinus
            }
            Constants.remainSecond = remainSecond
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
        OnToCookApplication.instance.bleBoundService.changeRecipeList(recipeStepList)
        OnToCookApplication.instance.bleBoundService.updateRecipeItem(recipe!!)
        Constants.isChangeInRecipe = true
    }

    fun generateNoteOnSD(context: Context?, sFileName: String?, sBody: String?) {
        try {
            val root = File(context?.externalCacheDir, "Recipe")
            if (!root.exists()) {
                root.mkdirs()
            }
            val gpxfile = File(root, sFileName)
//            if (!gpxfile.exists()){
//                gpxfile.mkdirs()
////                gpxfile.createNewFile()
//            }
            val writer = FileOutputStream(gpxfile)
            writer.write(sBody!!.toByteArray())
//            val writer = FileWriter(gpxfile)
//            writer.append(sBody)
            writer.flush()
            writer.close()
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun jumpToStep(currentStp: Int) {
        stopAnimation()
        Log.e("TAG", "performHandlerAction: stopScheduleTimer4 $currentStp")

        stopScheduleTimer()

        currentStep = currentStp
        Constants.currentStep = currentStp
        var totalDuration = 0
        for (i in 0 until currentStep - 1) {
            totalDuration += recipeStepList[i].stepDuration
            stepDoneList[i] = 1
        }
//        remainSecond -= (totalDuration - stepProg)

        stepSums = recipeStepList.map { it.stepDuration }.sum()
        var totalpercent = (totalDuration * 100) / stepSums.toFloat()

        println("total percent   $totalpercent     $totalDuration")

        binding.stepProgress.currentProgress = totalpercent.Round()
        binding.viewStoveProgress.progress = totalpercent.Round()
        binding.viewMicrowaveProgress.progress = totalpercent.Round()
        Log.e(TAG, "viewMicrowaveProgress:5 ${totalpercent.Round()}")

        Handler(Looper.getMainLooper()).postDelayed({
            startAnimation()
//            prepareTimer()
            updateDetailsView(stepProgressList[currentStep - 1])
            if (currentStep <= recipeStepList.size) {
                binding.tvTimeIn.text = ""
                binding.tvTimeIn.visibility = View.GONE
                binding.tvNextIn.text = recipeStepList[currentStep - 1].action
                //TODO NextStep
//                if (currentStep == recipeStepList.size)
//                    Constants.nextDescription = recipeStepList[currentStep - 1].app_audio
//                else Constants.nextDescription = recipeStepList[currentStep].app_audio
                binding.tvStepTitle.text = recipeStepList[currentStep - 1].name
                binding.tvStepDescDash.text = recipeStepList[currentStep - 1].desc
            } else {
                //TODO NextStep
//                Constants.nextDescription = ""
            }
            Log.e(TAG, "updateDetailsView: clNextStep 2")

            binding.clNextStep.visibility = View.VISIBLE
        }, 60)
    }

    private fun fastForward(currentStp: Int, remainTime: Int, status: Boolean) {
        if (currentStp != -1 && remainTime != -1) {
            isPlaying = false
            Log.e(TAG, "fastForward:isPlaying $isPlaying")
            togglePrePareSteps(false)
            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
            prepareDetailsView(true)

            currentStep = currentStp

            var remainTm = remainTime
            var passedTm = recipeStepList[currentStep - 1].stepDuration - remainTm

            var totalDuration = 0
            for (i in 0 until currentStep - 1) {
                totalDuration += recipeStepList[i].stepDuration
                Log.e("TAG", "stepDoneList3: $i")

                stepDoneList[i] = 1
            }

            totalDuration += passedTm

            Log.e(TAG, "viewMicrowaveProgress:11 $remainSecond")
            remainSecond -= totalDuration

            stepSums = recipeStepList.map { it.stepDuration }.sum()
            val totalpercent = (totalDuration * 100) / stepSums.toFloat()

            binding.stepProgress.currentProgress = totalpercent.Round()
            binding.viewStoveProgress.progress = totalpercent.Round()
            binding.viewMicrowaveProgress.progress = totalpercent.Round()
            Log.e(TAG, "viewMicrowaveProgress:11 ${totalpercent.Round()}")
            Log.e(TAG, "viewMicrowaveProgress:11 $remainSecond")
            Log.e(TAG, "viewMicrowaveProgress:11 $totalDuration")

            Handler(Looper.getMainLooper()).postDelayed({
                Log.e(TAG, "onReceive: togglePlayPause6")
                if (status) togglePlayPause()
                else
                    binding.clNextStep.visibility = View.GONE
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
                        requireContext(), R.color.dark_grey2
                    ), BlendModeCompat.SRC_IN
                )
        } else {
            scrollToPos()
            binding.ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        requireContext(), R.color.black
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

            if (totalTime >= recipeStepList[index].durationInSec && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)) {
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

            if (totalTime >= recipeStepList[index].durationInSec && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && !isManualScroll) {
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
                Log.e(TAG, "performDoneButtonAction:clNextStep Gone 3 $index")
                if (stepDoneList[index] == 1) {
                    binding.clNextStep.visibility = View.GONE
                    Log.e(TAG, "performDoneButtonAction:clNextStep 3")
                }
                currentRunningStep = index

                if ((index + 1) <= stepProgressList.lastIndex) {
                    Log.e(TAG, "init: nextExpectedProgressView33 $nextExpectedProgressView")
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            } else if (totalTime == (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && isManualScroll) {
                toggleManualScrolling()
                view.visibility = View.GONE
                if (stepDoneList[index] == 1) {
                    binding.clNextStep.visibility = View.GONE
                    Log.e(TAG, "performDoneButtonAction:clNextStep 4")
                }
                if ((index + 1) <= stepProgressList.lastIndex) {
                    Log.e(TAG, "init: nextExpectedProgressView44 $nextExpectedProgressView")
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
        Log.e(TAG, "updateDetailsView:currentStep $currentStep")
        Log.e(TAG, "updateDetailsView:totalTime $totalTime")
        Log.e(TAG, "updateDetailsView:recipeStepList ${recipeStepList.size}")
        Log.e(TAG, "updateDetailsView:recipeStepList ${nextExpectedProgressView}")
        if (totalTime >= nextExpectedProgressView && totalTime <= (nextExpectedProgressView + 6)) {
            if ((currentStep + 1) <= recipeStepList.size) {
                Log.e(TAG, "updateDetailsView: clNextStep 11")
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

        Log.e(TAG, "onReceive: togglePlayPause7 $totalTime")
        Log.e(TAG, "onReceive: togglePlayPause7 $stepSums")
        Log.e(TAG, "onReceive: togglePlayPause7 $remainSecond")
        if (totalTime == stepSums) {
            Log.e(TAG, "onReceive: togglePlayPause7")
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
                Log.e(
                    TAG,
                    "updateDetailsView:DrawComplete ${recipeStepList[currentRunningStep].duration}"
                )
                Log.e(
                    TAG, "updateDetailsView:DrawComplete ${recipeStepList[currentRunningStep].name}"
                )
                Log.e(TAG, "updateDetailsView:DrawComplete $currentRunningStep")
                (it.value.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (top.toInt() + seekBarTop) - (binding.stepProgress.getBarWidth() / 2).toInt()
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
                Log.e(TAG, "prepareDetailsView: SetDeatilView")
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
            Log.e(TAG, "prepareStepView: $index")
        }
    }

    private fun setupAnimator() {
        toggleManualScrolling(false)
        binding.viewStoveProgress.progressSample = getDummyWaveSample(false)
        binding.viewMicrowaveProgress.progressSample = getDummyWaveSample(true)
        Log.e(TAG, "viewMicrowaveProgress:69 ${binding.viewMicrowaveProgress.progressSample}")

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
        Log.e("TAG", "performHandlerAction: stopScheduleTimer 5")
        Constants.isPlayStop = false
        stopScheduleTimer()
        isPlaying = false
        Log.e(TAG, "pausePlayer:isPlaying $isPlaying")

    }

    private fun stopScheduleTimer() {
        Log.e(TAG, "stopScheduleTimer: CheckIng")
        service.stopTimer(macAddress)
    }

    private fun playPlayer(isSendMessage: Boolean = false) {
        Log.e(TAG, "playPlayer: ")
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
        Log.e(TAG, "playPlayer: Start")
        prepareTimer()
        isPlaying = true
        Constants.isPlayStop = true

    }

    private fun togglePlayPause(isSendMessageAction: Boolean = false) {
        Log.e(TAG, "togglePlayPause: 10")
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
            ((binding.stepProgress.currentProgress / binding.stepProgress.totalProgress.toFloat()) * (binding.stepProgress.progressBarHeight))
        binding.nestedScrollSeekBar.scrollTo(
            binding.nestedScrollSeekBar.scrollX, progress.toInt()
        )

    }

    internal fun performHandlerAction() {
        activity?.runOnUiThread {
//            if (currentStep == 2 || currentStep == 4 || currentStep == 6) {
//                currentStep++
//                completeStep(currentStep)
//                stopScheduleTimer()
//                return@runOnUiThread
//            }
            Log.e(TAG, "stopScheduleTimer: CheckIng performHandlerAction")
            Log.e("TAG", "performHandlerAction: ${binding.stepProgress.currentProgress} ")
            Log.e("TAG", "performHandlerAction: $currentStep ")
            Log.e("TAG", "performHandlerAction: stepSums${recipeStepList.size} ")
            Log.e(TAG, "onResume:stepProg $stepProg")
            if (binding.stepProgress.currentProgress >= 100) {
                Log.e("TAG", "performHandlerAction: stopScheduleTimer End")
                stopScheduleTimer()
                return@runOnUiThread
            }

            binding.stepProgress.currentProgress += increment
            binding.viewStoveProgress.progress += increment
            binding.viewMicrowaveProgress.progress += increment
            Log.e(TAG, "viewMicrowaveProgress:22 ${binding.viewMicrowaveProgress.progress}")
            Log.e(TAG, "viewMicrowaveProgress:22 $increment")
            Log.e(TAG, "viewMicrowaveProgress:22 $isManualScroll")

            if (!isManualScroll) {
                scrollToPos()
            }
            setStoveMVProgress()
            remainSecond -= 1
            stepProg += 1
            val str = StringBuilder()
            str.append("${recentItem?.name}")
            setStepTime(false)
            Log.e(TAG, "performHandlerAction: CheckTimer33 $remainSecond")
            Log.e(TAG, "performHandlerAction: CheckTimer44 ${Constants.remainSecond}")
            service.notifyContent(str.toString())
            onCurrentProgress(binding.stepProgress.currentProgress)
            updateDetailsView(decimalFormat.format(binding.stepProgress.currentProgress).toFloat())

            if (currentStep <= recipeStepList.size) {
                val totalTime = stepSums - remainSecond
                if (totalTime == ((recipeStepList[currentStep - 1]).durationInSec + recipeStepList[currentStep - 1].stepDuration) && stepDoneList[currentStep - 1] == 0) {
                    Log.e("TAG", "performHandlerAction: pausePlayer 9")
                    Log.e("TAG", "performHandlerAction: pausePlayer 9")
                    Log.e(
                        "TAG",
                        "performHandlerAction: pausePlayer 9 ${(recipeStepList[currentStep - 1]).durationInSec}"
                    )
                    Log.e(
                        "TAG",
                        "performHandlerAction: pausePlayer 9 ${recipeStepList[currentStep - 1].stepDuration}"
                    )
                    pausePlayer()
                    currentStep += 1
                    if (currentStep <= recipeStepList.size) {
                        //Speak Details of next step...
                        Log.e(TAG, "performHandlerAction: Speak Ready")
                        Constants.currentStep = currentStep
                        Log.e(TAG, "init: NextStep4.${recipeStepList[currentStep - 1].app_audio}")
                        //TODO NextStep
//                        if (currentStep == recipeStepList.size) Constants.nextDescription =
//                            recipeStepList[currentStep - 1].app_audio
//                        else Constants.nextDescription = recipeStepList[currentStep].app_audio
                        service.notifyContent(
                            recipeStepList[currentStep - 1].app_audio
                        )
                        OnToCookApplication.instance.speak(recipeStepList[currentStep - 1].app_audio + ".")

                        //To reset remain time view...
                        if (currentStep <= recipeStepList.size) {
                            binding.tvTimeIn.text = ""
                            binding.tvTimeIn.visibility = View.GONE
                            binding.tvNextIn.text = recipeStepList[currentStep - 1].action
                        }
                    } else {
                        //TODO NextStep
//                        Constants.nextDescription = ""
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
    }


    private fun setHandler(i: Int) {
        var time: Long = 0
        val handler = Handler(Looper.getMainLooper())
        handler.removeCallbacksAndMessages(null)
        when (i) {
            0 -> {
                time = 2 * 60000
                handler.postDelayed({
                    Log.e(TAG, "setHandler:Play1 $isClickDone")
                    if (!isClickDone) {
//                        OnToCookApplication.instance.speak(getString(R.string.txt_cooked))
                    }
                }, time)
            }

            1 -> {
                time = 5 * 60000
                handler.postDelayed({
                    Log.e(TAG, "setHandler:Play2 $isClickDone")
                    if (!isClickDone) {
//                        OnToCookApplication.instance.speak(getString(R.string.txt_cooked))
                    }
                }, time)
            }

            2 -> {
                time = 10 * 60000
                handler.postDelayed({
                    Log.e(TAG, "setHandler:Play3 $isClickDone")

                    if (!isClickDone) {
//                        OnToCookApplication.instance.speak(getString(R.string.txt_overcook))
                    }
                }, time)
            }

            -1 -> handler.removeCallbacksAndMessages(null)

        }


    }

    private fun setStoveMVProgress() {
//        arcProgressMv.progress =
//            viewStoveProgress.sample?.get(viewStoveProgress.progress.toInt())?.toFloat()!!
//        arcProgressFlam.progress =
//            viewMicrowaveProgress.sample?.get(viewMicrowaveProgress.progress.toInt())?.toFloat()!!
    }

    override fun onPause() {
        super.onPause()
        Log.e(TAG, "onPause: ")
        isPause = true
        isResume = false
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver)
//        if (isPlaying) {
//            togglePlayPause(true)
//        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as HomeActivity).setToolbar(macAddress, true)

        if (OnToCookApplication.rxBleClient.isScanRuntimePermissionGranted) {
            if (isPlaying) {
                toggleManualScrolling()
                updateDetailsView(binding.stepProgress.currentProgress)
            }
            if (this::service.isInitialized && service.isDeviceConnected(macAddress)) {
                service.writeData(macAddress, Constants.STATUS.toByteArray(Charsets.UTF_8))
//                Constants.prepareScanObserver()
//                bleStateView1.state = BluetoothState.State.OFF
            } else {
//                bleStateView1.state = BluetoothState.State.CONNECTED
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.EVENT_BLE_COMMUNICATION)
//        intentFilter.addAction(Constants.EVENT_BLE_CONNECTION)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            broadcastReceiver, intentFilter
        )
        if (isPause) {
            if (OnToCookApplication.instance.isDeviceConnected()) if (Constants.remainSecond != -1) resumeFromBackground()
            else {
            }
            //TODO
//                onBackPressed()
            isPause = false
        }
    }

    private fun resumeFromBackground() {
        Log.e(TAG, "resumeFromBackground: ${Constants.remainSecond}")
        remainSecond = Constants.remainSecond
        currentStep = Constants.currentStep
        isPlaying = Constants.isPlayStop
        //TODO NextStep
//        if (currentStep < recipeStepList.size) Constants.nextDescription =
//            recipeStepList[currentStep].app_audio
//        else Constants.nextDescription = recipeStepList[currentStep - 1].app_audio

        if (!isPlaying) {
            remainSecond = Constants.setRound(remainSecond)
        }
        if (isPrepareRunning) {
            prepareProgressbar(false)
        } else {
            prepareProgressbar()
            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
            prepareDetailsView(true)

            if (!isPlaying) {
//                if (!isManualScroll) {
//                    scrollToPos()
//                }
                binding.ivPulseBg.visibility = View.VISIBLE
                binding.ivCenterPlay.visibility = View.VISIBLE
                binding.ivPlayPause.setImageResource(R.drawable.ic_play)
//                nextExpectedProgressView =
//                    (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6
//
//                updateViews()
//                stepProgress.markers = stepProgressList
//                if (remainSecond == 0) isPlaying = true
//                jumpToStep(currentStep)
//                setStoveMVProgress()
//                onCurrentProgress(stepProgress.currentProgress)
//                setStepTime(true)
                completeStep(currentStep)
            } else {
                onCurrentProgress(binding.stepProgress.currentProgress)
                binding.ivPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }
        Log.e(TAG, "resumeFromBackground: Status=?")
        service.writeData(
            macAddress, "STATUS=?".toByteArray(
                Charsets.UTF_8
            )
        )
        //TODO
//        OnToCookApplication.instance.bleBoundService.incStepsAfterResume = 0
    }


    private fun getDummyWaveSample(isMagnetron: Boolean = false): IntArray {
        val data = IntArray(stepSums)
        var dataIndex = 0
        Log.e(TAG, "getDummyWaveSample: $stepSums")
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
        Log.e(TAG, "getDummyWaveSample:Data $data")
        Log.e(TAG, "getDummyWaveSample:Data ${data.toList()}")

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
        val top: Float = (progress / 100f) * (binding.stepProgress.getBarHeight())
        val layoutParam = FrameLayout.LayoutParams(
            binding.ivCurrentProgress.width, binding.ivCurrentProgress.height
        )
        layoutParam.gravity = Gravity.CENTER_HORIZONTAL
        layoutParam.topMargin = top.toInt() + seekBarTop - (Constants.getDp(
            26, requireContext()
        ) / 2)
        binding.ivCurrentProgress.layoutParams = layoutParam
        binding.ivCurrentPulseBg.layoutParams = layoutParam
    }


    internal fun openCloseDialog() {
        val alertDialog: android.app.AlertDialog? =
            android.app.AlertDialog.Builder(context).create()
        alertDialog?.setTitle("Close Current Recipe")
        alertDialog?.setMessage("Are you sure you want to close current recipe?")
        alertDialog?.setButton(
            AlertDialog.BUTTON_POSITIVE, "Yes"
        ) { dialog, _ ->
            service.writeData(
                macAddress, "stop=100".toByteArray(
                    Charsets.UTF_8
                )
            )
            dialog.dismiss()
        }
        alertDialog?.setButton(
            AlertDialog.BUTTON_NEGATIVE, "No"
        ) { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog?.show()
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
        Log.e(TAG, "onCreate:Bundle $macAddress")
        arguments.let {
            if (it != null) {
                Log.e(TAG, "onCreate:Bundle $it")
                if (macAddress.isEmpty() && it.containsKey(Constants.MAC_ADDRESS))
                    macAddress = it.getString(Constants.MAC_ADDRESS, "")
                if (it.containsKey(Constants.IS_RESUME)) isResume =
                    it.getBoolean(Constants.IS_RESUME, false)
                if (it.containsKey(Constants.CURRENT_STEP)) currentStep =
                    it.getInt(Constants.CURRENT_STEP)
                if (it.containsKey(Constants.PREPARE_STEP))
                    currentPrepareStep = it.getInt(Constants.PREPARE_STEP)
                if (it.containsKey(Constants.REMAINSEC))
                    remainSecond = it.getInt(Constants.REMAINSEC)
                if (it.containsKey(Constants.IS_PREPARE_RUNNING)) isPrepareRunning =
                    it.getBoolean(Constants.IS_PREPARE_RUNNING, true)
                if (it.containsKey(Constants.RECIPE)) recentItem =
                    it.getSerializable(Constants.RECIPE) as RecentItem?
                if (it.containsKey(Constants.CHANGE_TIME)) changeTime =
                    it.getInt(Constants.CHANGE_TIME)
                if (it.containsKey(Constants.IS_PLAYING)) isPlaying =
                    it.getBoolean(Constants.IS_PLAYING, false)
                if (it.containsKey(Constants.RECIPE_LIST)) {
                    val list = it.getString(Constants.RECIPE_LIST)
                    val type = object : TypeToken<ArrayList<Recipe>>() {}.type
                    val listRecipeJson: ArrayList<Recipe> = Gson().fromJson(list, type)
                    listRecipe.addAll(listRecipeJson)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(layoutInflater, R.layout.fragment_cooking_old_2, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()

    }

    companion object {
        @JvmStatic
        fun newInstance(recentItem: RecentItem, mac: String) = OldCookingFragment2().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_PARAM1, recentItem)
                putString(ARG_PARAM2, mac)
//                    putBundle(ARG_PARAM2, mac)
            }
        }
    }

    private fun updateRecipe2(
        step: Int, duration: Int, indTime: Int, magTime: Int
    ) {
        stopAnimation()
        Log.e("TAG", "performHandlerAction: stopScheduleTimer3")

        stopScheduleTimer()
        binding.viewDetailsBottomSheet.collapse()

        var nextStepPro = 0
        for (i in step until recipeStepList.size) {
            if (i == step) {
//                    if (incrementDec == 0) {
                Log.e("TAG", "updateRecipe: incrementDec=0 duration $duration")

                if (indTime != 0) recipe!!.Instruction[i].Induction_on_time =
                    (indTime + stepProg).toString()
                if (magTime != 0) recipe!!.Instruction[i].Magnetron_on_time =
                    (magTime + stepProg).toString()
                else {
                    if (recipe!!.Instruction[i].lid.lowercase() == "open") {
                        recipe!!.Instruction[i].Magnetron_on_time = "0"
                    }
                }
                val indTimes = recipe!!.Instruction[i].Induction_on_time.toInt()
                val magTimes = recipe!!.Instruction[i].Magnetron_on_time.toInt()
                Log.e(TAG, "updateRecipe2:stepDuration ${recipeStepList[i].stepDuration}")
                remainSecond = remainSecond - recipeStepList[i].stepDuration + duration + stepProg
                Log.e(TAG, "updateRecipe2: $remainSecond")
                Log.e(TAG, "updateRecipe2:stepProg $stepProg")
                Log.e(TAG, "updateRecipe2:stepProg indTimes  $indTimes")
                Log.e(TAG, "updateRecipe2:stepProg magTimes $magTimes")
                Log.e(TAG, "updateRecipe2:stepProg ${indTimes.coerceAtLeast(magTimes)}")
                recipeStepList[i].stepDuration = indTimes.coerceAtLeast(magTimes) /*+ stepProg*/
                Log.e(TAG, "updateRecipe2: ${recipeStepList[i].stepDuration}")
                recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                nextStepPro = recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
            } else {
                recipeStepList[i].durationInSec = nextStepPro
                nextStepPro += recipeStepList[i].stepDuration
            }
        }
        binding.clNextStep.visibility = View.GONE
        nextExpectedProgressView = (recipeStepList[step].durationInSec) - 6
        if (nextExpectedProgressView <= 0) {
            nextExpectedProgressView = duration - 6
        }
        Log.e(TAG, "updateRecipe2: nextExpectedProgressView ${recipeStepList[step].durationInSec}")
        Log.e(TAG, "updateRecipe2: nextExpectedProgressView $nextExpectedProgressView")
        Constants.remainSecond = remainSecond
        prepareProgressbar()
//        prepareDetailsView(true)
        prepareDetailsView()
        Thread {
            activity?.runOnUiThread {
                startAnimation()
                playPlayer()
//                prepareTimer()
                updateDetailsView(stepProgressList[step])
            }
        }.start()
        //TODO Service
//        OnToCookApplication.instance.bleBoundService.changeRecipeList(recipeStepList)
//        OnToCookApplication.instance.bleBoundService.updateRecipeItem(recipe!!)
        Constants.isChangeInRecipe = true
    }

    internal fun updateOnlyRecipe(
        step: Int, duration: Int, indTime: Int, magTime: Int
    ) {
        stopScheduleTimer()

        var nextStepPro = 0
        for (i in step until recipeStepList.size) {
            if (i == step) {
//                    if (incrementDec == 0) {
                Log.e("TAG", "updateRecipe: incrementDec=0 duration $duration")

                if (indTime != 0) recipe!!.Instruction[i].Induction_on_time =
                    (indTime + stepProg).toString()
                if (magTime != 0) recipe!!.Instruction[i].Magnetron_on_time =
                    (magTime + stepProg).toString()
                val indTimes = recipe!!.Instruction[i].Induction_on_time.toInt()
                val magTimes = recipe!!.Instruction[i].Magnetron_on_time.toInt()
                Log.e(TAG, "updateRecipe2: ${recipeStepList[i].stepDuration}")
                remainSecond = remainSecond - recipeStepList[i].stepDuration + duration + stepProg
                Log.e(TAG, "updateRecipe2: $remainSecond")
                Log.e(TAG, "updateRecipe2:step $step")
                Log.e(TAG, "updateRecipe2:stepProg $stepProg")
                Log.e(TAG, "updateRecipe2:stepProg ${indTimes.coerceAtLeast(magTimes)}")
                recipeStepList[i].stepDuration = indTimes.coerceAtLeast(magTimes) /*+ stepProg*/
                Log.e(TAG, "updateRecipe2: ${recipeStepList[i].stepDuration}")
                recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                nextStepPro = recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
            } else {
                recipeStepList[i].durationInSec = nextStepPro
                nextStepPro += recipeStepList[i].stepDuration
            }
        }
        nextExpectedProgressView = (recipeStepList[step].durationInSec) - 6
        if (nextExpectedProgressView <= 0) {
            nextExpectedProgressView = duration - 6
        }
        Log.e(TAG, "updateRecipe2: nextExpectedProgressView ${recipeStepList[step].durationInSec}")
        Log.e(TAG, "updateRecipe2: nextExpectedProgressView $nextExpectedProgressView")
        Constants.remainSecond = remainSecond
        Thread {
            activity?.runOnUiThread {
                prepareTimer()
            }
        }.start()
        //TODO Service
//        OnToCookApplication.instance.bleBoundService.changeRecipeList(recipeStepList)
//        OnToCookApplication.instance.bleBoundService.updateRecipeItem(recipe!!)
        Constants.isChangeInRecipe = true
    }

    private fun getImages(name: String): Int {
        Log.e(TAG, "getImages: $name")
        return when (name.lowercase()) {
            "cut vegetables" -> {
                R.drawable.ic_vegetable
            }

            "pizza" -> {
                R.drawable.boiledaloo
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
        val ins = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
        Log.e(TAG, "prepareRecipeSteps:First ${Gson().toJson(ins.Instruction)}")

        recipe!!.Instruction.map {
            stepDoneList.add(0)
            Log.e(TAG, "prepareRecipeSteps: ${Gson().toJson(it)}it")
            Log.e(TAG, "prepareRecipeSteps: $it")
            recipeStepList.add(
                RecipeSteps(
                    getImages(it.Text),
                    it.Text,
                    getDesc(it.Text),
                    "${getMaxTime(it)} sec.",
                    getAction(it.Text),
                    getMaxTime(it),
                    nextStepDuration,
                    it.app_audio
                )
            )
            nextStepDuration += getMaxTime(it)
        }
        Log.e(TAG, "prepareRecipeSteps:Second ${Gson().toJson(recipeStepList)}")
        Log.e(TAG, "prepareRecipeSteps:Second ${Gson().toJson(recipe!!.Instruction)}")

        prepareProgressbar(false)
        remainSecond = stepSums
        setStepTime(false)
    }

    private fun setStepTime(once: Boolean) {
        if (remainSecond < 0) {
            remainSecond = 0
        }
        val min = (remainSecond % 3600) / 60
        val second = remainSecond % 60
        binding.tvTime.text = String.format("%02d:%02d", min, second)
        if (!once) {
            Log.e(TAG, "setStepTime: Reset Remain")
            //Update Not Timer
            Constants.remainSecond = remainSecond
            Log.e(TAG, "setStepTime: CheckTimer66 ${Constants.remainSecond}")
        }
//        if (stepProgress.currentProgress < 100)
//            OnToCookApplication.instance.bleBoundService.notifyContent(
//                "${recentItem?.name}"
//            )
    }

    private fun preparePrePareStep() {
        Log.e(TAG, "preparePrePareStep:currentPrepareStep $currentPrepareStep")
        OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        val step = "Step ${currentPrepareStep + 1} of ${totalPrepareStep + 1}"
        binding.tvStep.text = step
        binding.tvPrePrepareTitle.text = recipe!!.Ingredients[currentPrepareStep].title
        binding.tvPrePrepareDesc.text = recipe!!.Ingredients[currentPrepareStep].text
    }

    private fun sendMessage(message: String) {
        binding.btnNext.isEnabled = false
        binding.btnSkip.isEnabled = false
        binding.tvDonePreAdd.isEnabled = false
        val abc = message.toByteArray(
            Charsets.UTF_8
        )
        abc.forEach(System.out::print)
        if (Constants.IS_PRODUCTION_MODE) {
            if (isBluetoothOn() && service.isDeviceConnected(macAddress))
                service.writeData(macAddress, message.toByteArray(Charsets.UTF_8))
        }
    }

    fun performDoneButtonAction(message: String, isStepComplete: Boolean) {
        sendMessage(message)
        var pos = currentRunningStep - 1
        if (pos <= 0) {
            pos = 0
        }
        Log.e("TAG", "stepDoneList: $pos")
        if (isStepComplete) {
            stepDoneList[pos] = 1
            stepProg = 0
        }
        binding.clNextStep.visibility = View.GONE
        Log.e(TAG, "performDoneButtonAction:clNextStep 1")
        //For done buttonn....
//        if (stepProgress.currentProgress >= 100) {
//            if (isPlaying) {
//                togglePlayPause()
//            }
//            sendMessage("stop=1")
//            OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
//            onBackPressed()
//            return;
//        }
//        //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
//        //recipeStepList[currentRunningStep].app_audio + "." +
//        OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
//        if (!isPlaying) {
//            playPlayer()
//        }
    }

    private fun init() {
        binding.viewHeader.ivStop.visibility = View.VISIBLE
//        bleStateView1.visibility = View.VISIBLE
        binding.viewHeader.ivMenu.visibility = View.GONE
        binding.tvRecipeName.text = recentItem?.name

        binding.ivPulseBg.visibility = View.GONE
        binding.ivCenterPlay.visibility = View.GONE

        binding.viewHeader.ivStop.setOnClickListener {
            openCloseDialog()
        }
        binding.tvDonePreAdd.setOnClickListener {
            isClickDone = true
            performDoneButtonAction("add_confirm=${currentRunningStep}", true)
            Log.e(TAG, "init: tvDonePreAdd$currentStep")
            if (isPlaying) stepDoneList[currentRunningStep] = 1
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    DebugLog.e("If $macAddress")
                    parseData(intent)
                } else {
                    DebugLog.e("Else $macAddress")
                    (requireActivity() as HomeActivity).findAndParseData(
                        intent.getStringExtra(
                            Constants.MAC_ADDRESS
                        )!!, intent
                    )
//                    val fragment = intent.getStringExtra(Constants.MAC_ADDRESS)?.let {
//                        (activity as HomeActivity).viewPagerAdapter.getFragmentFromMac(
//                            it
//                        )
//                    }
//                    if ((fragment as DashboardFragment).navController.currentDestination!!.id == R.id.cookingFragment) {
//                        (fragment.getCurrentFragment() as CookingFragment).parseData(intent)
//                        Log.e("TAG", "onReceive: DD Matched")
//                    }
                }
            }
        }

        recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)

//        currentPrepareStep = 0

        totalPrepareStep = recipe!!.Ingredients.size - 1
//        if (requireActivity().intent.getIntExtra("prepreparestep", -1) != -1) {
//            currentPrepareStep = requireActivity().intent.getIntExtra("prepreparestep", -1)
        Constants.currentPrepareStep = currentPrepareStep
        Log.e(TAG, "doSendMessageBroadcast:prepreparestep ${currentPrepareStep}")
        if (currentPrepareStep == 0) {
            binding.btnSkip.text = "Skip"
            binding.btnNext.text = "Next"
        } else if (currentPrepareStep == totalPrepareStep) {
            binding.btnSkip.text = "Previous"
            binding.btnNext.text = "Done"
        } else {
            binding.btnSkip.text = "Previous"
            binding.btnNext.text = "Next"
        }
//        }


        binding.ivPlaySound.setOnClickListener {
            OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].app_audio)
        }

        if (isPrepareRunning) {
            preparePrePareStep()
        }
//        if (requireActivity().intent.getIntExtra(
//                "currentstep", -1
//            ) == -1 && requireActivity().intent.getIntExtra(
//                "currentstep", -1
//            ) == -1
//        ) {
//            if (requireActivity().intent.getBooleanExtra("isPrepareRunning", false)) {
//                preparePrePareStep()
//                //TODO service
////                OnToCookApplication.instance.bleBoundService.notifyUpdate(
////                    "${recipe!!.ingredients[currentPrepareStep].title}",
////                    "${recipe!!.ingredients[currentPrepareStep].text}"
////                )
//            }
//        }
//        if (!isResume) {
//            preparePrePareStep()
////            OnToCookApplication.instance.bleBoundService.notifyUpdate(
////                "${recipe!!.ingredients[currentPrepareStep].title}",
////                "${recipe!!.ingredients[currentPrepareStep].text}"
////            )
//        }
        Log.e(TAG, "init: $currentPrepareStep $totalPrepareStep")
        binding.viewHeader.ivLeft.setOnClickListener {
//            super.onBackPressed()
        }
//        bleStateView1.setOnClickListener {
//            generateNoteOnSD(this, recipe!!.name, Gson().toJson(recentItem))
//        }

        binding.btnNext.setOnClickListener {
            if (binding.btnNext.text.toString().lowercase() == "done") {
                Log.e(TAG, "onReceive: togglePlayPause2")
                LoadingUtils.showLoading(context, true, "Please Wait")
//                togglePlayPause()
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                sendMessage("ingredients=100")
                Log.e(TAG, "init: ${recipeStepList.size}")
//                Log.e(TAG, "init: nextDescription1.${recipeStepList[currentStep].app_audio}")
//                nextDescription = recipeStepList[currentStep].app_audio
            } else {
                Log.e("TAG", "init: currentPrepareStep $currentPrepareStep")
                if (currentPrepareStep < totalPrepareStep) {
                    currentPrepareStep++
                    Constants.currentPrepareStep++
                    binding.tvPrePrepareTitle.text = recipe!!.Ingredients[currentPrepareStep].title
                    binding.tvPrePrepareDesc.text = recipe!!.Ingredients[currentPrepareStep].text
                    //TODO Service
//                    OnToCookApplication.instance.bleBoundService.notifyUpdate(
//                        "${recipe!!.ingredients[currentPrepareStep].title}  ",
//                        "${recipe!!.ingredients[currentPrepareStep].text}"
//                    )
                    if (currentPrepareStep == totalPrepareStep) {
                        binding.btnNext.text = "Done"
                    }
                    binding.btnSkip.text = "Previous"
                    sendMessage("ingredients=${currentPrepareStep}")
                    preparePrePareStep()
                }
            }
        }



        binding.ivEditTime.setOnClickListener {
            Log.e("ivEditTime", "init: $isPlaying")
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
                        if (isDone && isPlaying) {
                            performDoneButtonAction(
                                "add_confirm=${currentRunningStep},magTime=${if (isIncMag == "+") magnetronOnTime else "-$magnetronOnTime"},indTime=${if (isIncInd == "+") inductionOnTime else "-$inductionOnTime"}",
                                false
                            )
                            Log.e(TAG, "onButtonClick:Ind ${inductionOnTime.toInt()}")
                            Log.e(TAG, "onButtonClick:Mag ${magnetronOnTime.toInt()}")
                            Log.e(
                                TAG, "onButtonClick:SUm ${
                                    inductionOnTime.toInt().coerceAtLeast(magnetronOnTime.toInt())
                                }"
                            )
//                            updateRecipe(
//                                currentStep - 1,
//                                inductionOnTime.toInt().coerceAtLeast(magnetronOnTime.toInt()),
//                                if (isIncrement) 0 else 1,
//                                inductionOnTime.toInt(),
//                                magnetronOnTime.toInt()
//                            )
                        } else {
                            if (isDone) {
//                                isSaveForNext = true
//                                saveInductionOnTime = inductionOnTime
//                                saveMagnetronOnTime = magnetronOnTime
//                                saveIsIncrement = isIncrement
//                                updateRecStep(
//                                    currentStep - 1, inductionOnTime.coerceAtLeast(magnetronOnTime)
//                                )
                            }

                        }
//                        if (isDone && !isPlaying) {
//                            recipe!!.instruction[currentStep - 1].Induction_on_time =
//                                "$inductionOnTime"
//                            recipe!!.instruction[currentStep - 1].Magnetron_on_time =
//                                "$magnetronOnTime"
//                            updateRecStep(
//                                currentStep - 1,
//                                inductionOnTime.coerceAtLeast(magnetronOnTime)
//                            )
//                            performDoneButtonAction("add_confirm=${currentRunningStep},magTime=${magnetronOnTime},indTime=${inductionOnTime}")
//                            println("current step   $currentStep")
//                        } else if (isDone && isPlaying) {
//                            println("inc   $isIncrement  mg $magnetronOnTime in  $inductionOnTime ")
//                            println(
//                                "message  ${
//                                    "seconds:${
//                                        (recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(
//                                            magnetronOnTime
//                                        ))) - stepProg
//                                    }"
//                                }"
//                            )
//                            sendMessage(
//                                "seconds:${
//                                    (recipeStepList[currentStep - 1].stepDuration + (inductionOnTime.coerceAtLeast(
//                                        magnetronOnTime
//                                    ))) - stepProg
//                                }"
//                            )
//                            updateRecipe(
//                                currentStep - 1,
//                                inductionOnTime.coerceAtLeast(magnetronOnTime),
//                                if (isIncrement) 0 else 1
//                            )
//                        }
                    }
                }
            dialogEditStepTime.show(childFragmentManager, "")
        }

        binding.btnSkip.setOnClickListener {
            if (binding.btnSkip.text.toString().lowercase() == "skip") {
                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)

                OnToCookApplication.instance.speak(recipeStepList[currentRunningStep].app_audio + "." + "Wait for " + recipeStepList[currentRunningStep].duration)
                //TODO NextStep
//               Constants.nextDescription = recipeStepList[currentStep].app_audio
//                togglePlayPause()
                sendMessage("ingredients=100")
                LoadingUtils.showLoading(requireActivity(), true)
            } else {
                if (currentPrepareStep >= 0) {
                    binding.btnNext.text = "Next"
                    currentPrepareStep--
                    Constants.currentPrepareStep--
                    if (currentPrepareStep == 0) {
                        binding.btnSkip.text = "Skip"
                    }
                    sendMessage("ingredients=${currentPrepareStep}")
                    //TODO Service
//                    OnToCookApplication.instance.bleBoundService.notifyUpdate(
//                        "${recipe!!.ingredients[currentPrepareStep].title}  ",
//                        "${recipe!!.ingredients[currentPrepareStep].text}"
//                    )
                    preparePrePareStep()
                }
            }
            binding.btnSkip.isEnabled = false
            binding.btnNext.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.btnSkip.isEnabled = true
                binding.btnNext.isEnabled = true
            }, 5000)
        }

        binding.clPrePrepare.post {
            setLayoutParam(binding.clPrePrepare.height + 40, binding.clPrePrepare.height / 2)
            Thread {
                activity?.runOnUiThread {
                    prepareDetailsView()
                }
            }.start()
        }

        binding.ivBottomCurrentProgress.setOnClickListener {
            binding.clCurrentStep.visibility = View.GONE
            toggleManualScrolling()
            updateDetailsView(binding.stepProgress.currentProgress)
        }

        binding.nestedScrollSeekBar.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                seekBarHeight = binding.nestedScrollSeekBar.height
                binding.nestedScrollSeekBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        binding.viewPagerDetails.adapter =
            DetailsPagerAdapter(object : DetailsPagerAdapter.OnUpdateTimeBarDuration {
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
            waveProgressColor = ContextCompat.getColor(requireContext(), R.color.colorFlamColor)
            sample = getDummyWaveSample(true)
        }


        binding.ivPlayPause.setOnClickListener {
            Log.e(TAG, "onReceive: togglePlayPause4")
            if (binding.clPrePrepare.visibility == View.VISIBLE) sendMessage("ingredients=100")
//            togglePlayPause(true)
        }

        binding.nestedScrollSeekBar.setOnTouchListener { _, _ ->
            toggleManualScrolling(true)
            false
        }
        binding.nestedScrollSeekBar.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            if (isManualScroll) {
                val progressPercent = (scrollY * 100) / (binding.stepProgress.progressBarHeight)
                val index = binding.stepProgress.markers.indexOf(
                    singleDecimalFormat.format(progressPercent).toFloat()
                )
                if (index != -1) {
                    prepareStepView(index + 1)
                }
            }
        }

        //stepProgress.onCurrentStepHighlight = this
        prepareStepView(0)

//        Handler(Looper.getMainLooper()).postDelayed({
//            Log.e(TAG, "init: fastForward")
//            fastForward(
//                requireActivity().intent.getIntExtra("currentstep", -1),
//                requireActivity().intent.getIntExtra("remainTime", -1),
//                true
//            )
//        }, 100)
        Log.e(TAG, "init: Checking isResume$isResume")
        if (isResume) {
//            service.writeData(
//                macAddress, "STATUS=?".toByteArray(
//                    Charsets.UTF_8
//                )
//            )
            val type = object : TypeToken<ArrayList<RecipeSteps>>() {}.type
            if (activity?.intent?.extras?.getString("recipeList") != null) {
                Log.e(TAG, "init: ${activity?.intent?.extras?.getString("recipeList")}")
                val recipeArrayList: ArrayList<RecipeSteps> =
                    Gson().fromJson(activity?.intent?.extras?.getString("recipeList"), type)
                recipeArrayList.map {
                    stepDoneList.add(0)
                }
                recipeStepList.clear()
                recipeStepList.addAll(recipeArrayList)
            } else {
                prepareRecipeSteps()
            }
//            remainSecond = activity?.intent?.extras?.getInt("remainSec", 0)!!
//            currentStep = activity?.intent?.extras?.getInt("currentStep", 1)!!
//            isPlaying = activity?.intent?.extras?.getBoolean("isPlaying", false)!!
            Log.e(TAG, "init: ${recipeStepList.size}")
            Log.e(TAG, "onInit: RecipeList Check $isPrepareRunning")
//            isPrepareRunning = activity?.intent?.extras?.getBoolean("isPrepareRunning", false)!!
            if (isPrepareRunning) {
                prepareProgressbar(false)
            } else {
                prepareProgressbar()
            }
            togglePrePareSteps(isPrepareRunning)
            if (remainSecond == 0) prepareRecipeSteps()
            Log.e(TAG, "init: RemainSecond $remainSecond")
            Constants.remainSecond = remainSecond
            Constants.isPlayStop = isPlaying
            Constants.currentStep = currentStep
            Log.e(TAG, "init: nextDescription5.${currentStep}")
            Log.e(TAG, "init: nextDescription5.${recipeStepList.size}")
            //TODO NextStep
//            if (currentStep < recipeStepList.size)
//                Constants.nextDescription = recipeStepList[currentStep].app_audio
//            else Constants.nextDescription = recipeStepList[currentStep - 1].app_audio

            Handler(Looper.getMainLooper()).postDelayed({
                setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
                Log.e(TAG, "postDelayed: Checking isResume$isPlaying")
                if (isPlaying) {
                    for (i in 0 until currentStep - 1) {
                        stepDoneList[i] = 1
                    }
                    prepareDetailsView(true)
                    Log.e(TAG, "onResume: isPlaying Visible")
                    startAnimation()
                    binding.ivPulseBg.visibility = View.VISIBLE
                    binding.ivCenterPlay.visibility = View.VISIBLE
                    binding.ivPlayPause.setImageResource(R.drawable.ic_pause)
                    onCurrentProgress(binding.stepProgress.currentProgress)
                    updateViews()
                    updateDetailsView(
                        decimalFormat.format(binding.stepProgress.currentProgress).toFloat()
                    )
                    if (changeTime > recipeStepList[currentStep - 1].stepDuration) {
                        Log.e(TAG, "init: UpdateRecipe")
                        completeStep(currentStep)
                        updateRecipe2(
                            currentStep - 1, changeTime, changeTime, changeTime
                        )
                    } else {
                        if (changeTime > 0) {
                            changeTime--
                            fastForward(currentStep, changeTime, true)
                            Log.e(TAG, "init: UpdateRecipe Inner")
                        }
                        Log.e(TAG, "init: UpdateRecipe FF")
                        Log.e(TAG, "init: UpdateRecipe FF $changeTime")
                    }
                } else {
                    if (!isPrepareRunning) {
                        remainSecond = Constants.setRound(remainSecond)
                        jumpToStep(currentStep)
                        Log.e(TAG, "init: RemainSecond 02. $remainSecond")
                        if (changeTime == 0) {
                            var totalDuration = 0
                            for (i in 0 until currentStep - 1) {
                                totalDuration += recipeStepList[i].stepDuration
                                stepDoneList[i] = 1
                            }
                            remainSecond -= totalDuration
                            Constants.remainSecond = remainSecond
                            Log.e(TAG, "init: RemainSecond 12. $totalDuration")
                        } else {
                            fastForward(currentStep, changeTime, false)
                        }
                        Log.e(TAG, "init: RemainSecond 22. $remainSecond")

                        if (!isManualScroll) {
                            scrollToPos()
                        }

                        binding.ivPulseBg.visibility = View.VISIBLE
                        binding.ivCenterPlay.visibility = View.VISIBLE
                        binding.ivPlayPause.setImageResource(R.drawable.ic_play)
                        nextExpectedProgressView =
                            (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6

                        updateViews()
                        binding.stepProgress.markers = stepProgressList
                        if (remainSecond == 0) isPlaying = true


                        setStoveMVProgress()
                        onCurrentProgress(binding.stepProgress.currentProgress)
                        setStepTime(true)

                    }
                }
//                OnToCookApplication.instance.bleBoundService.notifyContent("${recentItem?.name}  Started")

            }, 40)
        } else {
            togglePrePareSteps(true)
            prepareRecipeSteps()
            Constants.isChangeInRecipe = false
        }
        nextExpectedProgressView =
            (recipeStepList[0].durationInSec + recipeStepList[0].stepDuration) - 6
        Log.e(TAG, "init: nextExpectedProgressView77 $nextExpectedProgressView")

        binding.stepProgress.markers = stepProgressList
        binding.viewHeader.ivHome.visibility = View.GONE
        binding.viewHeader.ivLeft.visibility = View.VISIBLE
        binding.viewHeader.tvPageTitle.text = resources.getString(R.string.txt_cooking)
    }

    internal fun parseData(intent: Intent) {
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
                                //TODO NextStep
//                                Constants.nextDescription = ""

//                                    OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
                                findNavController().popBackStack(R.id.cookingFragment, true)
                                openDialog()
                            }
//                                    var foundRecipe = Constants.RECIPES.filter {
//                                        it.name[0].lowercase() == message.lowercase()
//                                            .replace("recipe=", "")
//                                    }
//                                    if (foundRecipe != null) {
//                                        var intent =
//                                            Intent(
//                                                applicationContext,
//                                                DashboardActivity::class.java
//                                            )
//                                        intent.putExtra("recipe", foundRecipe[0])
//                                        intent.putExtra("isResume", false)
//                                        startActivity(intent)
//                                    }
                        } else {
                            val recipeName =
                                message.split(",")[0].lowercase().replace("recipe=", "")
                            val cmdSize: Int = message.split(",").size

                            val stepNo =
                                if (cmdSize > 2) message.split(",")[2].lowercase()
                                    .replace("stepno=", "") else "0"
                            val mode = if (cmdSize > 1) message.split(",")[1].uppercase()
                                .replace("MODE=", "").uppercase() else ""
                            val foundRecipe = listRecipe.filter {
                                it.name[0].lowercase() == Constants.getRecipeNameFromCommand(message)
                            }
                            when (mode) {
                                Constants.INGREDIENT_MODE -> {
                                    if (recipeName == recipe!!.name[0].lowercase()) {
                                        OnToCookApplication.instance.bleBoundService.initRecipe(
                                            recentItem!!
                                        )
                                        currentPrepareStep = stepNo.toInt() - 1
                                        Constants.currentPrepareStep = currentPrepareStep
                                        togglePrePareSteps(true)
                                        preparePrePareStep()
                                        prepareRecipeSteps()
                                        prepareProgressbar()
                                        setStepTime(true)
                                        prepareStepView(0)
                                        Log.e(
                                            TAG,
                                            "onReceive: ${stepProgress.currentProgress}"
                                        )
                                        binding.ivPulseBg.visibility = View.GONE
                                        binding.ivCenterPlay.visibility = View.GONE
                                        binding.ivPlayPause.setImageResource(R.drawable.ic_play)
                                    } else {
                                        if (foundRecipe.isNotEmpty()) {
                                            //not found
//                                            val intent = Intent(
//                                                applicationContext,
//                                                OldDashboardActivity::class.java
//                                            )
//                                            intent.putExtra("recipe", foundRecipe[0])
//                                            intent.putExtra("isResume", true)
//                                            intent.putExtra(
//                                                "prepreparestep", stepNo.toInt() - 1
//                                            )
//                                            intent.putExtra("isPrepareRunning", true)
//                                            startActivity(intent)
                                        }
                                    }
                                }

                                Constants.COOKING_MODE -> {
                                    if (cmdSize > 4) {
                                        val indTime = message.split(",")[3].lowercase()
                                            .replace("ind_run=", "")
                                        val magTime = message.split(",")[4].lowercase()
                                            .replace("mag_run=", "")
                                        if (recipeName == recipe!!.name[0].lowercase()) {
                                            Log.e(TAG, "onReceive: Same recipeName")
                                            currentStep = stepNo.toInt()
                                            togglePrePareSteps(false)
//                                            Constants.currentStep = currentStep
                                            val status = message.split(",")[5].lowercase()
                                                .replace("status=", "")

                                            isPlaying = indTime.toInt()
                                                .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                            if (isPlaying) {
                                                if (indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) > recipeStepList[currentStep - 1].stepDuration
                                                ) {
                                                    Log.e(TAG, "init: UpdateRecipe")
                                                    completeStep(currentStep)
                                                    stepProg = 0 //temp solution
                                                    updateRecipe2(
                                                        currentStep - 1,
                                                        indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()),
                                                        indTime.toInt(),
                                                        magTime.toInt()
                                                    )
                                                } else {
                                                    prepareRecipeSteps()
                                                    if (indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()) != 0
                                                    )
                                                        fastForward(
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
                                                    decimalFormat.format(stepProgress.currentProgress)
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
                                               binding. ivPulseBg.visibility = View.VISIBLE
                                                binding. ivCenterPlay.visibility = View.VISIBLE
                                                binding. ivPlayPause.setImageResource(R.drawable.ic_play)
                                                nextExpectedProgressView =
                                                    (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6
                                                if (indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) != 0
                                                )
                                                    fastForward(
                                                        currentStep,
                                                        indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()),
                                                        false
                                                    )
                                                updateViews()
                                                binding.stepProgress.markers = stepProgressList
                                                setStoveMVProgress()
                                                onCurrentProgress( binding.stepProgress.currentProgress)
                                                updateDetailsView(stepProgressList[currentStep - 1])
                                                setStepTime(true)
                                            }
                                        } else {
                                            Log.e(TAG, "onReceive: NotSame recipeName")
//                                            if (foundRecipe.isNotEmpty()) {
//                                                finish()
//                                                val intent = Intent(
//                                                    applicationContext,
//                                                    OldDashboardActivity::class.java
//                                                )
//                                                intent.putExtra("recipe", foundRecipe[0])
//                                                intent.putExtra("isResume", true)
//                                                intent.putExtra(
//                                                    "currentStep", stepNo.toInt()
//                                                )
//                                                intent.putExtra("isPrepareRunning", false)
//                                                intent.putExtra(
//                                                    "isPlaying",
//                                                    indTime.toInt()
//                                                        .coerceAtLeast(magTime.toInt()) != 0
//                                                )
//                                                intent.putExtra(
//                                                    "changeTime",
//                                                    indTime.toInt()
//                                                        .coerceAtLeast(magTime.toInt())
//                                                )
//                                                startActivity(intent)
//                                            }
                                        }
                                    }
                                }

                                else -> findNavController().popBackStack()
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
                                    completeStep(currentStep)
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
                            if ((stepSums - remainSecond) == stepSums) {
                                for (i in 0..2) {
                                    setHandler(i)
                                }
                            }
                        } else {
                            if (message.uppercase() == "INSTR_RUN=START") {
                                LoadingUtils.hideDialog()
                                binding.clNextStep.visibility = View.GONE
                                if (!isPlaying) playPlayer()
                            }

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
//                        val alertDialog: android.app.AlertDialog? =
//                            android.app.AlertDialog.Builder(context).create()
                        findNavController().popBackStack()
//                        alertDialog?.setTitle("Manual Mode Started")
//                        alertDialog?.setMessage("Please Cook Manually.")
//                        alertDialog?.setButton(
//                            AlertDialog.BUTTON_POSITIVE, "Cook"
//                        ) { dialog, _ ->
//                            TODO service
//                                        OnToCookApplication.instance.bleBoundService.notifyManualMode()
//                            Constants.currentStep = 1
//                            setHandler(-1)
//                            dialog.dismiss()
//                            val intent =
//                                Intent(requireContext(), MainActivity::class.java)
//                            intent.flags =
//                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//                            startActivity(intent)
//                        }
//                        alertDialog?.setCancelable(false)
//                        alertDialog?.show()
                    }

                    message.lowercase()
                        .contains("add_confirm") && message.lowercase() != "add_confirm=0" -> {
                        Log.e("TAG", "onReceive: add_confirm")
                        //                            val step = message.lowercase().replace("add_confirm=", "").toInt()
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
                        //                                //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                        //                                //recipeStepList[currentRunningStep].app_audio + "." +
                        //                                OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
                        //                                clNextStep.visibility = View.GONE
                        //                                if (!isPlaying) {
                        //                                    playPlayer()
                        //                                }
                        //                            }

                        if (binding.stepProgress.currentProgress >= 100) {
                            OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
                            Log.e("TAG", "performHandlerAction: pausePlayer 1")

                            pausePlayer()
                            //TODO
//                                    finish()
                            return
                        }

                        //Jump to the step if not in async..

                        //                            val step = message.lowercase().replace("add_confirm=", "").toInt()
                        //                            if(step >= currentRunningStep){
                        //                                Handler(Looper.getMainLooper()).postDelayed({
                        //                                    fastForward(step, 0)
                        //                                }, 100)
                        //                            }else{
                        var pos = currentRunningStep - 1
                        if (pos <= 0) {
                            pos = 0
                        }
                        Log.e("TAG", "stepDoneList1: $pos")

                        stepDoneList[pos] = 1
                        stepProg = 0
                        //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                        //recipeStepList[currentRunningStep].app_audio + "." +
                        OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
                        binding.clNextStep.visibility = View.GONE
                        Log.e(TAG, "performDoneButtonAction:clNextStep 2")
                        Log.e(TAG, "onReceive: Start Progress11")
//                                if (!isPlaying) {
//                                    playPlayer()
//                                }
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
                            //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
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

                    //                        else if (message.lowercase()
                    //                                .contains("seconds")
                    //                        ) {
                    //                            println("message   $message")
                    //                            Handler(Looper.getMainLooper()).postDelayed({
                    //                                updateRec(
                    //                                    currentStep - 1,
                    //                                    message.lowercase().replace("seconds:", "").toInt()
                    //                                )
                    //                            }, 100)
                    //                        }
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
                            findNavController().popBackStack()
//                                    onBackPressed()
                        }
                    }

                    message.lowercase().contains("ack_command") -> {
//                                btnNext.isEnabled = true
//                                btnSkip.isEnabled = true
                        if (isClickDone) {
                            isClickDone = false
                            //Todo
//                                    if (stepProgress.currentProgress >= 100) {
//                                        if (isPlaying) {
//                                            Log.e(TAG, "onReceive: togglePlayPause1")
//                                            togglePlayPause()
//                                        }
//                                        sendMessage("stop=1")
//                                        OnToCookApplication.instance.speak("${recentItem?.name} is Ready")
//                                        openDialog()
//                                        //                                    onBackPressed()
//                                        return
//                                    }
                            //OnToCookApplication.instance.speak(getAudio(recipeStepList[currentRunningStep].name[0].lowercase()) + "Wait for " + recipeStepList[currentRunningStep].duration)
                            //recipeStepList[currentRunningStep].app_audio + "." +
                            Log.e(TAG, "onReceive: Start Progress")
                            Log.e(TAG, "onReceive: Start $currentStep")
                            Log.e(TAG, "onReceive: Start ${recipeStepList.size}")
                            if ((stepSums - remainSecond) == stepSums) {
                                return
                            }
                            OnToCookApplication.instance.speak("Wait for " + recipeStepList[currentRunningStep].duration)
//                                    if (!isPlaying) {
//                                        playPlayer()
//                                    }
                        }
                    }

                    message.uppercase().contains("INDQUICKSTART=") -> {
                        if (Constants.checkNavigation(message)) {
                            findNavController().popBackStack(R.id.recipeFragment, true)
                        }
                    }

                    message.lowercase().contains("magtime=") -> {
                        val magnetronOnTime =
                            message.lowercase().split(",")[0].replace("magtime=", "")
                                .toInt()
                        val inductionOnTime =
                            message.lowercase().split(",")[1].replace("indtime=", "")
                                .toInt()
//                                updateRecStep(
//                                    currentStep - 1, inductionOnTime.coerceAtLeast(magnetronOnTime)
//                                )

                        Log.e(TAG, "onReceive: currentStep$currentStep")
                        if (inductionOnTime.coerceAtLeast(magnetronOnTime) == 0) {
                            currentStep++
                            completeStep(currentStep)
                        } else {
                            if (!isPlaying) {
                                currentStep--
                            }
//                                    if (inductionOnTime.toInt()
//                                            .coerceAtLeast(magnetronOnTime.toInt()) > recipeStepList[currentStep - 1].stepDuration
//                                    ) {
                            Log.e(TAG, "init: UpdateRecipe")
                            binding.clNextStep.visibility = View.GONE
                            updateRecipe2(
                                currentStep - 1,
                                inductionOnTime.toInt()
                                    .coerceAtLeast(magnetronOnTime.toInt()),
                                inductionOnTime.toInt(),
                                magnetronOnTime.toInt()
                            )
//                                    } else {
//                                        prepareRecipeSteps()
//                                        if (inductionOnTime.toInt()
//                                                .coerceAtLeast(magnetronOnTime.toInt()) != 0
//                                        ) {
//                                            fastForward(
//                                                currentStep,
//                                                inductionOnTime.toInt()
//                                                    .coerceAtLeast(magnetronOnTime.toInt())
//                                            )
//                                            clNextStep.visibility = View.GONE
//                                        }
//                                        Log.e(TAG, "init: UpdateRecipe FF")
//                                    }
                        }
                    }

                    message.uppercase().contains("MAG_RUN=") -> {
                        if (message.contains(",")) {
                            if (message.split(",")[0].uppercase()
                                    .replace("MAG_RUN=", "") == "PAUSE"
                            ) {
                                binding.ivPlayPause.isEnabled = false
                                pausePlayer()
//                                        stopScheduleTimer()

                            } else {
                                binding.ivPlayPause.isEnabled = true
                                playPlayer()
//                                        prepareTimer()
                            }
                        }

                    }

                    message.lowercase() == Constants.IDLE_DEVICE -> {
                        findNavController().popBackStack(R.id.recipeFragment, true)
                    }
                }


            }

            Constants.EVENT_BLE_CONNECTION_ERROR -> {
                var message =
                    intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }

            Constants.EVENT_BLE_CONNECTION_ABORT -> {
                DebugLog.e("EVENT_BLE_CONNECTION_ABORT")
                service.stopTimer(macAddress)
            }

            Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                binding.btnNext.isEnabled = true
                binding.btnSkip.isEnabled = true
                binding.tvDonePreAdd.isEnabled = true
                service.writeData(
                    macAddress, Constants.STATUS.toByteArray(
                        Charsets.UTF_8
                    )
                )
//                        bleStateView1.state = BluetoothState.State.CONNECTED
            }
        }
    }

    private fun completeStep(currentStep: Int) {
        prepareRecipeSteps()
        jumpToStep(currentStep)
        Log.e(
            TAG, "init: RemainSecond 02. $remainSecond"
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
        binding.ivPulseBg.visibility = View.VISIBLE
        binding.ivCenterPlay.visibility = View.VISIBLE
        binding.ivPlayPause.setImageResource(R.drawable.ic_play)
        nextExpectedProgressView =
            (recipeStepList[currentStep - 1].durationInSec + recipeStepList[currentStep - 1].stepDuration) - 6
        Log.e(TAG, "init: nextExpectedProgressView55 $nextExpectedProgressView")

        updateViews()
        binding.stepProgress.markers = stepProgressList
        setStoveMVProgress()
        onCurrentProgress(binding.stepProgress.currentProgress)
        updateDetailsView(stepProgressList[currentStep - 1])
        setStepTime(false)
    }

    private fun openDialog() {
//        val alertDialog: android.app.AlertDialog? =
//            android.app.AlertDialog.Builder(this).create()
//        alertDialog?.setTitle("New Recipe")
//        alertDialog?.setMessage("Do you want to cook new Recipe?")
//        alertDialog?.setButton(
//            AlertDialog.BUTTON_POSITIVE, "Yes"
//        ) { dialog, _ ->
//            //stop playing - set to only call on stop not on pause
//            if (isPlaying) {
//                Log.e(TAG, "onReceive: togglePlayPause5")
//                pausePlayer()
//            }
//            isClickDone = true
//            setHandler(-1)
//            Constants.currentStep = 1
//            dialog.dismiss()
//            recentItem!!.recipe = Gson().toJson(recipe)
////            generateNoteOnSD(this, "${recipe!!.name}.txt", Gson().toJson(recentItem))
////                sendMessage("stop=1")
//            super.onBackPressed()
    }
}

