package com.invent.ontocook

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.*
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.invent.ontocook.dialog.DialogPauseRecipeView
import com.invent.ontocook.dialog.DialogScaleStepTime
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.RecipeSteps
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.Round
import com.invent.ontocook.extension.showSnackBarShort
import com.masoudss.lib.Utils
import com.masoudss.lib.WaveGravity
import com.mohammedalaa.seekbar.Direction
import kotlinx.android.synthetic.main.activity_on2cook_new_design.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

class OnTocookNewDesign : AppCompatActivity() {

    var currentPrepareStep = 0
    private var currentRunningStep = 0
    private var pulseAnimator: AnimatorSet? = null
    private var pulseAnimatorCurrentStep: AnimatorSet? = null
    private var scheduleTimer: Timer? = null
    private var recentItem: RecentItem? = null
    var recipe: Recipe? = null
    var recipeStepList = mutableListOf<RecipeSteps>()
    var stepDoneList = mutableListOf<Int>()
    private var stepSums = 0
    var remainSecond = 0
    private var increment = 1f
    private var progressIncrement = 1f
    private var stepProgressList = mutableListOf(20f, 40f, 70f, 80f)
    var seekBarHeight: Int = 0
    private var isManualScroll = false
    var seekBarTop: Int = 0
    var singleDecimalFormat = DecimalFormat("0")
    var timerMw: CountDownTimer? = null
    var timerInduction: CountDownTimer? = null
    private var isPlaying = false
    var stepProg = 0
    var decimalFormat = DecimalFormat("0.0")
    var currentStep = 1
    private var nextExpectedProgressView = 0
    private var inductionMWSteps = 0f
    var totalPrepareStep = 3
    var broadcastReceiver: BroadcastReceiver? = null
    var triggerStepDoneAction = false
    var pipRatio: Rational? = null
    var isPipMode = false
    var oldPower = ""

    var dialogScaleStepTime : DialogScaleStepTime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_on2cook_new_design)

        init()
    }

    private fun sendMessage(message: String) {
//        OnToCookApplication.instance.bleBoundService.writeData(
//            message.toByteArray(
//                Charsets.UTF_8
//            )
//        )
    }

    private fun updateRec(step: Int, duration: Int) {
        if (recipeStepList[step].stepDuration - stepProg > duration) {
            stopAnimation()
            stopScheduleTimer()

            remainSecond -= recipeStepList[step].stepDuration

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
            prepareStepSlider()
            viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
            viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))
            //prepareDetailsView(true)

            Thread {
                runOnUiThread {
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

    private fun updateRecStep(step: Int, duration: Int) {
        stopAnimation()
        stopScheduleTimer()

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
        prepareStepSlider()
        viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
        viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))
        //prepareDetailsView(true)
        startAnimation()
        updateDetailsView(stepProgressList[step])

        showSnackBarShort("Updated recipe with $duration seconds")
    }

    private fun updateRunningRecStep(step: Int) {
        stopAnimation()
        stopScheduleTimer()

        remainSecond -= recipeStepList[step].stepDuration

        var nextStepPro = 0
        for (i in step until recipeStepList.size) {
            if (i == step) {
                recipeStepList[i].stepDuration = recipe!!.Instruction[i].Induction_on_time.toInt()
                    .coerceAtLeast(recipe!!.Instruction[i].Magnetron_on_time.toInt())
                recipeStepList[i].duration = "${recipeStepList[step].stepDuration} sec."
                nextStepPro =
                    recipeStepList[i].durationInSec + recipeStepList[i].stepDuration
            } else {
                recipeStepList[i].durationInSec = nextStepPro
                nextStepPro += recipeStepList[i].stepDuration
            }
        }

        remainSecond += recipeStepList[step].stepDuration
        prepareProgressbar()
        prepareStepSlider()
        viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
        viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))
        //prepareDetailsView(true)
//        startAnimation()
//        updateDetailsView(stepProgressList[step])
        Thread(Runnable {
            runOnUiThread {
                startAnimation()
                prepareTimer()
                updateDetailsView(stepProgressList[step])
            }
        }).start()

        showSnackBarShort("Updated recipe with seconds")
    }

    private fun cancelMWTimer() {
        if (timerMw != null) {
            timerMw?.cancel()
            timerMw = null
        }
    }

    private fun cancelInductionTimer() {
        if (timerInduction != null) {
            timerInduction?.cancel()
            timerInduction = null
        }
    }

    private fun startMWTimer() {
        cancelMWTimer()
        timerMw = object : CountDownTimer(1500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                ivMWProgress.alpha = 1f
                seekBarMWWrapper.visibility = View.GONE
            }
        }
        timerMw?.start()
    }

    private fun startInductionTimer() {
        cancelInductionTimer()
        timerInduction = object : CountDownTimer(1500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                ivInductionProgress.alpha = 1f
                seekBarInductionWrapper.visibility = View.GONE
            }
        }
        timerInduction?.start()
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

    private fun pausePlayer() {
        ivPlayPause.setImageResource(R.drawable.ic_play)
        stopAnimation()
        stopScheduleTimer()
        isPlaying = false
    }

    private fun stopScheduleTimer() {
        scheduleTimer?.cancel()
        scheduleTimer = null
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            pausePlayer()
        } else {
            playPlayer()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val d = windowManager.defaultDisplay
        val p = Point()
        d.getSize(p)
        val width: Int = p.x
        val height: Int = p.y

        pipRatio = Rational(width, height)
        val pipBuilder = PictureInPictureParams.Builder()
        pipBuilder.setAspectRatio(pipRatio).build()
        enterPictureInPictureMode(pipBuilder.build())
//        val param = PictureInPictureParams.Builder().setAspectRatio(Rational(350, 500)).build()
//        enterPictureInPictureMode(param)
    }

//    override fun onPictureInPictureModeChanged(
//        isInPictureInPictureMode: Boolean,
//        newConfig: Configuration?
//    ) {
//        isPipMode = isInPictureInPictureMode
//
//        if (isInPictureInPictureMode) {
//            setLayoutParam(pipRatio!!.denominator, pipRatio!!.denominator)
//            updateDetailsView(stepProgress.currentProgress)
//            //guidelineCurrentPro.setGuidelinePercent(1f)
//            guidelineOuter.setGuidelinePercent(1f)
//            //guideline.setGuidelinePercent(1f)
//            viewHeader.visibility = View.GONE
//            llNextExpectedStep.visibility = View.GONE
//            clFooterView.visibility = View.GONE
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//        } else {
//            setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
//            updateDetailsView(stepProgress.currentProgress)
//            //guidelineCurrentPro.setGuidelinePercent(0.4f)
//            guidelineOuter.setGuidelinePercent(0.4f)
//            //guideline.setGuidelinePercent(0.4f)
//            viewHeader.visibility = View.VISIBLE
//            llNextExpectedStep.visibility = View.VISIBLE
//            clFooterView.visibility = View.VISIBLE
//        }
//        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
//    }

    private fun playPlayer() {
        if (clPrePrepare.visibility == View.VISIBLE) {
            OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
            sendMessage("ingredients=100")

            togglePrePareSteps(false)
            setLayoutParam(300, 40)
//            Thread {
//                runOnUiThread {
//                    prepareDetailsView(true)
//                }
//            }.start()
        }
        if (triggerStepDoneAction) {
            triggerStepDoneAction = false
            OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
            sendMessage("add_confirm=${currentRunningStep}")
        }
        ivPulseBg.visibility = View.VISIBLE
        ivCenterPlay.visibility = View.VISIBLE
        ivPlayPause.setImageResource(R.drawable.ic_pause)
        startAnimation()
        prepareTimer()
        isPlaying = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stepProgress.currentProgress < 99f) {
            val alertDialog: android.app.AlertDialog? =
                android.app.AlertDialog.Builder(this@OnTocookNewDesign).create()
            alertDialog?.setTitle("Close Current Recipe")
            alertDialog?.setMessage("Are you sure you want to close current recipe?")
            alertDialog?.setButton(
                AlertDialog.BUTTON_POSITIVE, "Yes"
            ) { dialog, _ ->
                dialog.dismiss()
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
            super.onBackPressed()
        }
    }

    private fun prepareTimer() {
        scheduleTimer = Timer()
        scheduleTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    performHandlerAction()
                }
            }
        }, 0, 1000)
    }

    private fun setStoveMVProgress() {
        tvInductionProgress.text =
            viewStoveProgress.sample?.get(inductionMWSteps.toInt())?.toString()
        tvMWProgress.text =
            viewMicrowaveProgress.sample?.get(inductionMWSteps.toInt())?.toString()
//        arcProgressMv.progress =
//            viewStoveProgress.sample?.get(viewStoveProgress.progress.toInt())?.toFloat()!!
//        arcProgressFlam.progress =
//            viewMicrowaveProgress.sample?.get(viewMicrowaveProgress.progress.toInt())?.toFloat()!!
    }

//    private fun setViewAsCompleted() {
//        viewContainer.children().withIndex().map {
//            val view = it.value
//            val index = it.index
//
//            val currentPro =
//                (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()
//
//            view.visibility = View.VISIBLE
//            //completed
//            val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
//            (view.layoutParams as FrameLayout.LayoutParams).topMargin =
//                (top.roundToInt() + seekBarTop) - view.height + (stepProgress.getBarWidth() / 2).toInt()
//            (view as ItemExpandableStepView).setCompletedProgressView()
//        }
//    }

    private fun updateViews() {
        val totalTime = stepSums - remainSecond

        recipeStepList.withIndex().map {
            val index = it.index

            val currentPro =
                (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()

            if (totalTime >= recipeStepList[index].durationInSec
                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && !isManualScroll
            ) {
                if ((index + 1) <= viewContainer.childCount - 1) {
                    if (!isPipMode) {
                        llNextExpectedStep.visibility = View.VISIBLE
                    }

                    tvStepTitle.text = recipeStepList[index + 1].name
                    tvStepDescDash.text = recipeStepList[index + 1].desc
                } else {
                    if (!isPipMode) {
                        llNextExpectedStep.visibility = View.GONE
                    }
                }

                clCurrentRecipe.visibility = View.VISIBLE
                clCurrentRecipeTime.visibility = View.VISIBLE
                //viewConnectorCurrentStep.visibility = View.VISIBLE

                ivCurrentStepImage.setImageResource(recipeStepList[index].image)

                ivCurrentStepImage.drawable.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            this@OnTocookNewDesign,
                            R.color.white
                        ), BlendModeCompat.SRC_IN
                    )

                tvTitle.text = recipeStepList[index].name
                tvDesc.text = recipeStepList[index].desc
                tvStepTime.text = getCurrentStepTime(index)
                currentRunningStep = index

                if ((index + 1) <= stepProgressList.lastIndex) {
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            }else if (totalTime == (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)
                && isManualScroll
            ) {
                toggleManualScrolling()
                if ((index + 1) <= stepProgressList.lastIndex) {
                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
                }
            }
        }

//        viewContainer.children().withIndex().map {
//            val view = it.value
//            val index = it.index
//
//            val currentPro =
//                (((recipeStepList[index].durationInSec) * 100) / stepSums.toFloat()).Round()
//
//            if (totalTime >= recipeStepList[index].durationInSec
//                && totalTime <= (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration) && !isManualScroll
//            ) {
//                //viewDetailsBottomSheet.visibility = View.VISIBLE
//                //viewIndicatorCurrentStep.visibility = View.VISIBLE
//
//                if ((index + 1) <= viewContainer.childCount - 1) {
//                    if (!isPipMode) {
//                        llNextExpectedStep.visibility = View.VISIBLE
//                    }
//
//                    tvStepTitle.text = recipeStepList[index + 1].name
//                    tvStepDescDash.text = recipeStepList[index + 1].desc
//                } else {
//                    if (!isPipMode) {
//                        llNextExpectedStep.visibility = View.GONE
//                    }
//                }
//
//                clCurrentRecipe.visibility = View.VISIBLE
//                clCurrentRecipeTime.visibility = View.VISIBLE
//                //viewConnectorCurrentStep.visibility = View.VISIBLE
//
//                ivCurrentStepImage.setImageResource(recipeStepList[index].image)
//
//                ivCurrentStepImage.drawable.colorFilter =
//                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
//                        ContextCompat.getColor(
//                            this@OnTocookNewDesign,
//                            R.color.white
//                        ), BlendModeCompat.SRC_IN
//                    )
//
//                tvTitle.text = recipeStepList[index].name
//                tvDesc.text = recipeStepList[index].desc
//                view.visibility = View.GONE
//                tvStepTime.text = getCurrentStepTime(index)
//                //tvStepTime.text = getFormattedTime(recipeStepList[index].stepDuration)
//                //tvDuration.text = recipeStepList[index].duration
//                //viewIndicatorCurrentStep.text = recipeStepList[index].action
////                tvStepNo.text = "${index + 1}/${recipeStepList.size}"
////                tvBottomStepTitle.text = recipeStepList[index].name
////                tvBottomStepDesc.text = recipeStepList[index].desc
////                if (stepDoneList[index] == 1) {
////                    llNextExpectedStep.visibility = View.GONE
////                }
//                currentRunningStep = index
//
//                if ((index + 1) <= stepProgressList.lastIndex) {
//                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
//                }
//            } else if (totalTime == (recipeStepList[index].durationInSec + recipeStepList[index].stepDuration)
//                && isManualScroll
//            ) {
//                toggleManualScrolling()
//                view.visibility = View.GONE
////                if (stepDoneList[index] == 1) {
////                    llNextExpectedStep.visibility = View.GONE
////                }
//                if ((index + 1) <= stepProgressList.lastIndex) {
//                    //nextExpectedProgressView = stepProgressList[index + 1] - 1
//                    nextExpectedProgressView = (recipeStepList[index + 1].durationInSec) - 6
//                }
//            } else if (totalTime > recipeStepList[index].durationInSec) {
//                if (stepDoneList[index] == 1) {
//                    view.visibility = View.VISIBLE
//                    //completed
//                    val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
//                    (view.layoutParams as FrameLayout.LayoutParams).topMargin =
//                        (top.roundToInt() + seekBarTop) - view.height + (stepProgress.getBarWidth() / 2).toInt()
//                    (view as ItemExpandableStepView).setCompletedProgressView()
//                }
//            } else if (totalTime < recipeStepList[index].durationInSec) {
//                view.visibility = View.VISIBLE
//                //remain
//                val top: Float = (currentPro / 100f) * (stepProgress.getBarHeight())
//                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
//                    (top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
//                (view as ItemExpandableStepView).setRemainProgressView()
//            }
//        }
    }

    private fun performHandlerAction() {
        toggleProgressUpdateView(true)
        if (stepProgress.currentProgress >= 100) {
            stopScheduleTimer()
            return
        }

        stepProgress.currentProgress += increment
        viewStoveProgress.progress += increment
        viewMicrowaveProgress.progress += increment

        inductionMWSteps += progressIncrement

        if (!isManualScroll) {
            scrollToPos()
        }
        setStoveMVProgress()
        remainSecond -= 1
        stepProg += 1
        //setStepTime()
        onCurrentProgress(stepProgress.currentProgress)
        updateDetailsView(decimalFormat.format(stepProgress.currentProgress).toFloat())

        val totalTime = stepSums - remainSecond
        if (totalTime == ((recipeStepList[currentStep - 1]).durationInSec + recipeStepList[currentStep - 1].stepDuration) && stepDoneList[currentStep - 1] == 0) {
            pausePlayer()
            //to restart player again....
            stepDoneList[currentStep - 1] = 1
            isManualScroll = false
            stepProg = 0
            currentStep += 1
            if (currentStep <= recipeStepList.size) {
                triggerStepDoneAction = true
                prepareProgressDuration()
                tvStepTime.text = getCurrentStepTime(currentStep - 1)
                tvFloatingStepTime.text = getCurrentStepTime(currentStep - 1)

                prepareStepSlider()
            } else {
                triggerStepDoneAction = false
                updateDetailsView(decimalFormat.format(stepProgress.currentProgress).toFloat())
            }
        }
    }

    private fun prepareStepSlider() {
        seekBarMWTime.maxValue = 0
//        seekBarMWTime.currentValue = recipeStepList[currentStep - 1].stepDuration
//        seekBarMWTime.minValue = recipeStepList[currentStep - 1].stepDuration
        seekBarMWTime.currentValue = recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()
        seekBarMWTime.minValue = recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()

        seekBarInductionTime.minValue = 0
//        seekBarInductionTime.currentValue = recipeStepList[currentStep - 1].stepDuration
//        seekBarInductionTime.maxValue = recipeStepList[currentStep - 1].stepDuration
        seekBarInductionTime.currentValue =
            recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()
        seekBarInductionTime.maxValue =
            recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()

        seekBarFloatingMWTime.maxValue = 0
//        seekBarFloatingMWTime.currentValue = recipeStepList[currentStep - 1].stepDuration
//        seekBarFloatingMWTime.minValue = recipeStepList[currentStep - 1].stepDuration
        seekBarFloatingMWTime.currentValue =
            recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()
        seekBarFloatingMWTime.minValue =
            recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()

        seekBarFloatingBarInductionTime.minValue = 0
//        seekBarFloatingBarInductionTime.currentValue = recipeStepList[currentStep - 1].stepDuration
//        seekBarFloatingBarInductionTime.maxValue = recipeStepList[currentStep - 1].stepDuration
        seekBarFloatingBarInductionTime.currentValue =
            recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()
        seekBarFloatingBarInductionTime.maxValue =
            recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()
    }

    private fun prepareProgressDuration() {
        if (currentStep <= stepProgressList.size) {
            //val time = recipeStepList[currentStep - 1].stepDuration / 3
            val time = round(recipeStepList[currentStep - 1].stepDuration / 3.0).toInt()
            var upperBound = if (currentStep > stepProgressList.size - 1) {
                100f
            } else {
                stepProgressList[currentStep]
            }
            progressIncrement =
                (time * increment) / (upperBound - stepProgressList[currentStep - 1])
        }
    }

    private fun updateDetailsView(currentProgress: Float) {
        val totalTime = stepSums - remainSecond

        tvReadyInTime.text = "${getFormattedTime(remainSecond)} min"

        if (totalTime == stepSums) {
            //togglePlayPause()
            //setViewAsCompleted()

            llNextExpectedStep.visibility = View.GONE
            clCurrentRecipe.visibility = View.GONE
            clCurrentRecipeTime.visibility = View.GONE
            clFloatingCurrentStep.visibility = View.GONE
            //viewConnectorCurrentStep.visibility = View.GONE
        } else {
            var inductionTime =
                recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt() - stepProg
            if (inductionTime <= 0) {
                inductionTime = 0
            }

            var magnetronTime =
                recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt() - stepProg
            if (magnetronTime <= 0) {
                magnetronTime = 0
            }

            seekBarMWTime.currentValue = magnetronTime
//            seekBarMWTime.currentValue = recipeStepList[currentStep - 1].stepDuration -  stepProg
//            seekBarInductionTime.currentValue = recipeStepList[currentStep - 1].stepDuration - stepProg
            seekBarInductionTime.currentValue = inductionTime

            seekBarFloatingMWTime.currentValue = magnetronTime
//            seekBarFloatingMWTime.currentValue = recipeStepList[currentStep - 1].stepDuration - stepProg
//            seekBarFloatingBarInductionTime.currentValue = recipeStepList[currentStep - 1].stepDuration - stepProg
            seekBarFloatingBarInductionTime.currentValue = inductionTime

            updateViews()
        }

        //&& (viewContainer.children()
        //                .isNotEmpty() && currentProgress >= viewContainer.children()[0].id
        if (currentProgress < 100 && isManualScroll){
            prepareFloatingCurrentView()
        }
    }

    private fun prepareFloatingCurrentView() {
        ivCurrentStepFloatingImage.setImageResource(recipeStepList[currentRunningStep].image)
        ivCurrentStepFloatingImage.drawable.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    this@OnTocookNewDesign,
                    R.color.white
                ), BlendModeCompat.SRC_IN
            )
        tvFloatingTitle.text = recipeStepList[currentRunningStep].name
        tvFloatingDesc.text = recipeStepList[currentRunningStep].desc
        tvFloatingStepTime.text = getCurrentStepTime(currentRunningStep)
    }

    private fun getCurrentStepTime(index: Int): String {
        return if (index <= recipeStepList.size - 1) {
            val currentTime = recipeStepList[index].stepDuration - stepProg
            val min = (currentTime % 3600) / 60
            val second = currentTime % 60
            String.format("%02d:%02d", min, second)
        } else {
            ""
        }
    }

//    private fun getFormattedMin(time: Int): String {
//        val min = (time % 3600) / 60
//        return String.format("%02d", min)
//    }

    private fun getFormattedTime(time: Int): String {
        val min = (time % 3600) / 60
        val second = time % 60
        //return String.format("%02d:%02d", min, second)
        return String.format("%02d", min)
    }

    private fun toggleProgressUpdateView(isShow: Boolean = false) {
        if (isShow) {
            ivInductionProgress.visibility = View.VISIBLE
            tvInductionProgress.visibility = View.VISIBLE
            ivMWProgress.visibility = View.VISIBLE
            tvMWProgress.visibility = View.VISIBLE
        } else {
            ivInductionProgress.visibility = View.GONE
            tvInductionProgress.visibility = View.GONE
            ivMWProgress.visibility = View.GONE
            tvMWProgress.visibility = View.GONE
        }
    }

    private fun onCurrentProgress(progress: Float) {
        val top: Float = (progress / 100f) * (stepProgress.getBarHeight())
        var layoutParam = ivCurrentProgress.layoutParams as ConstraintLayout.LayoutParams
        layoutParam.topMargin = top.toInt() + seekBarTop - (Constants.getDp(
            26,
            this@OnTocookNewDesign
        ) / 2)
        ivCurrentProgress.layoutParams = layoutParam

        ivCurrentProgress.alpha = 1f
        ivCurrentPulseBg.alpha = 1f
    }

//    private fun setStepTime() {
//        if(remainSecond < 0){
//            remainSecond = 0
//        }
//        var min = (remainSecond % 3600) / 60
//        var second = remainSecond % 60
//        tvTime.text = String.format("%02d:%02d", min, second)
//    }

    private fun setupAnimator() {
        toggleManualScrolling(false)
        viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
        viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))

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

    private var onTouchListener = View.OnTouchListener { _, _ -> true }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver!!)
//        if (isPlaying) {
//            togglePlayPause()
//        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                broadcastReceiver!!,
                IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
    }

    //0 - increment  1 - decrement
    private fun updateRecipe(step: Int, duration: Int, incrementDec: Int = 0) {
        println("is increment    ${incrementDec == 0}")
        if (incrementDec == 1 && recipeStepList[step].stepDuration - stepProg < (duration + 3)) {
            showSnackBarShort("$duration second is to short for update")
        } else {
            stopAnimation()
            stopScheduleTimer()

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
            prepareStepSlider()
            viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
            viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))
            //prepareDetailsView(true)
            Thread(Runnable {
                runOnUiThread {
                    startAnimation()
                    prepareTimer()
                    updateDetailsView(stepProgressList[step])
                }
            }).start()
            showSnackBarShort("Updated recipe with ${if (incrementDec == 0) "+" else "-"}$duration seconds")
        }
    }

    private fun prepareScaleTimeDialog(isInduction : Boolean = true){
        if(dialogScaleStepTime != null){
            return
        }
        dialogScaleStepTime = DialogScaleStepTime()
        dialogScaleStepTime?.isPlaying = isPlaying
        dialogScaleStepTime?.context = this@OnTocookNewDesign
        dialogScaleStepTime?.isInduction = isInduction
        dialogScaleStepTime?.maxTime = recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()
        dialogScaleStepTime?.minTime = 0 - recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt()
        dialogScaleStepTime?.onButtonClickListener =
            object : DialogScaleStepTime.OnButtonClickListener {
                override fun onButtonClick(
                    isDone: Boolean,
                    magnetronOnTime: Int?,
                    inductionOnTime: Int?
                ) {
                    if(isDone && isPlaying){
                        var timeToConsider = magnetronOnTime ?: inductionOnTime
                        var newInductionTime = if(timeToConsider!! > 0) (recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt() + abs(timeToConsider))
                        else (recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt() - abs(timeToConsider))
                        var newMagnetronTime = if(timeToConsider > 0) (recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt() + abs(timeToConsider))
                        else (recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt() - abs(timeToConsider))

                        var expectDuration = newInductionTime.coerceAtLeast(newMagnetronTime) - (stepProg + 3)

                        if(expectDuration > 0){
                            if(inductionOnTime != null){
                                recipe!!.Instruction[currentStep - 1].Induction_on_time = newInductionTime.toString()
                            }else if(magnetronOnTime != null){
                                recipe!!.Instruction[currentStep - 1].Magnetron_on_time = newMagnetronTime.toString()
                            }
                            var timeToUpdate = newInductionTime.coerceAtLeast(newMagnetronTime) - stepProg
                            println("inc  ${(timeToConsider > 0)}  $timeToUpdate")
                            sendMessage("seconds:$timeToUpdate")
                            updateRunningRecStep(
                                currentStep - 1
                            )
                        }else{
                            showSnackBarShort("$expectDuration second is to short for update")
                        }
//                        if (timeToConsider!! > 0){
//                            recipe!!.instruction[currentStep - 1].Induction_on_time =
//                                (recipe!!.instruction[currentStep - 1].Induction_on_time.toInt() + abs(timeToConsider)).toString()
//                            recipe!!.instruction[currentStep - 1].Magnetron_on_time =
//                                (recipe!!.instruction[currentStep - 1].Magnetron_on_time.toInt() + abs(timeToConsider)).toString()
//                        }else{
//                            recipe!!.instruction[currentStep - 1].Induction_on_time =
//                                (recipe!!.instruction[currentStep - 1].Induction_on_time.toInt() - abs(timeToConsider)).toString()
//                            recipe!!.instruction[currentStep - 1].Magnetron_on_time =
//                                (recipe!!.instruction[currentStep - 1].Magnetron_on_time.toInt() - abs(timeToConsider)).toString()
//                        }

//                        var timeToUpdate = if (timeToConsider!! > 0){
//                            (recipeStepList[currentStep - 1].stepDuration + abs(timeToConsider)) - stepProg
//                        }else{
//                            (recipeStepList[currentStep - 1].stepDuration - abs(timeToConsider)) - stepProg
//                        }

//                        updateRecipe(
//                            currentStep - 1,
//                            abs(timeToConsider),
//                            if (timeToConsider > 0) 0 else 1
//                        )
                    }else if(isDone && !isPlaying){
                        if(inductionOnTime != null){
                            recipe!!.Instruction[currentStep - 1].Induction_on_time =
                                "$inductionOnTime"
                        }else if(magnetronOnTime != null){
                            recipe!!.Instruction[currentStep - 1].Magnetron_on_time =
                                "$magnetronOnTime"
                        }
                        updateRecStep(
                            currentStep - 1,
                            recipe!!.Instruction[currentStep - 1].Induction_on_time.toInt()
                                .coerceAtLeast(recipe!!.Instruction[currentStep - 1].Magnetron_on_time.toInt())
                        )
                        togglePlayPause()
                    }
                    dialogScaleStepTime = null
                }
            }
        dialogScaleStepTime?.show(supportFragmentManager, "")
    }

    fun init() {
        seekBarMWTime.direction = Direction.RIGHT_TO_LEFT
        seekBarInductionTime.direction = Direction.LEFT_TO_RIGHT

        seekBarFloatingMWTime.direction = Direction.RIGHT_TO_LEFT
        seekBarFloatingBarInductionTime.direction = Direction.LEFT_TO_RIGHT

        seekBarInductionTime.setOnTouchListener { v, event ->
            if(event.action == MotionEvent.ACTION_UP){
                prepareScaleTimeDialog(true)
            }
            true
        }
        seekBarMWTime.setOnTouchListener { v, event ->
            if(event.action == MotionEvent.ACTION_UP){
                prepareScaleTimeDialog(false)
            }
            true
        }
        seekBarFloatingMWTime.setOnTouchListener(onTouchListener)
        seekBarFloatingBarInductionTime.setOnTouchListener(onTouchListener)

        prepareRecipeView()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        if (message.lowercase().contains("complete")) {
                            updateDetailsView(
                                decimalFormat.format(stepProgress.currentProgress).toFloat()
                            )
                        } else if (message.lowercase()
                                .contains("add_confirm") && message.lowercase() != "add_confirm=0"
                        ) {
                            var step = message.lowercase().replace("add_confirm=", "").toInt()
                            if (step >= recipeStepList.size) {
                                if (stepProgress.currentProgress >= 100) {
                                    OnToCookApplication.instance.speak("Done")
                                    onBackPressed()
                                }
                            } else {
                                var pos = currentRunningStep - 1
                                if (pos <= 0) {
                                    pos = 0
                                }
                                stepDoneList[pos] = 1
                                stepProg = 0
                                OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
                                clNextStep.visibility = View.GONE
                                if (!isPlaying) {
                                    playPlayer()
                                }
                            }
                        } else if (message.lowercase()
                                .contains("ingredients")
                        ) {
                            currentPrepareStep =
                                message.lowercase().replace("ingredients=", "").toInt()

                            btnNext.text = "Next"
                            btnSkip.text = "Previous"

                            if ((currentPrepareStep - 1) == totalPrepareStep) {
                                togglePlayPause()
                                OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
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
                        } else if (message.lowercase()
                                .contains("seconds")
                        ) {
                            println("message   $message")
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateRec(
                                    currentStep - 1,
                                    message.lowercase().replace("seconds:", "").toInt()
                                )
                            }, 100)
                        }
                    }
                }
            }
        }

        ivPlaySound.setOnClickListener {
            OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].title)
        }

        toggleProgressUpdateView(false)

        ivPlayPause.setOnClickListener {
            togglePlayPause()
        }

        //click on current floating view
        clFloatingCurrentStep.setOnClickListener {
            toggleManualScrolling()
            updateDetailsView(stepProgress.currentProgress)
        }

        ivInductionProgress.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                startInductionTimer()
                ivInductionProgress.alpha = 0f
                seekBarInductionWrapper.visibility = View.VISIBLE
                seekBarInduction.progress = tvInductionProgress.text.toString().toInt()
            }
            true
        }

        seekBarInduction.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    cancelInductionTimer()
                }
                tvInductionProgress.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelInductionTimer()
                ivInductionProgress.alpha = 1f
                tvInductionProgress.text = seekBar?.progress.toString()
                seekBarInductionWrapper.visibility = View.GONE

                oldPower = recipe!!.Instruction[currentStep - 1].Induction_power
                recipe!!.Instruction[currentStep - 1].Induction_power = seekBar?.progress.toString()
                viewStoveProgress.setProgressSampleData(getDummyWaveSample(false))
            }
        })

        ivMWProgress.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                startMWTimer()
                ivMWProgress.alpha = 0f
                seekBarMWWrapper.visibility = View.VISIBLE
                seekBarMW.progress = tvMWProgress.text.toString().toInt()
            }
            true
        }

        seekBarMW.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    cancelMWTimer()
                }
                tvMWProgress.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelMWTimer()
                ivMWProgress.alpha = 1f
                tvMWProgress.text = seekBar?.progress.toString()
                seekBarMWWrapper.visibility = View.GONE

                oldPower = recipe!!.Instruction[currentStep - 1].Magnetron_power
                recipe!!.Instruction[currentStep - 1].Magnetron_power = seekBar?.progress.toString()
                viewMicrowaveProgress.setProgressSampleData(getDummyWaveSample(true))
            }
        })

        tvSos.setOnClickListener {
            val dialogPauseRecipeView = DialogPauseRecipeView()
            dialogPauseRecipeView.context = this@OnTocookNewDesign
            dialogPauseRecipeView.show(supportFragmentManager, "")
        }

        viewExpansionLayout.addIndicatorListener { _, willExpand ->
            if (willExpand) {
                ivToggleUp.visibility = View.VISIBLE
                tvIngredients.visibility = View.GONE
                ivNextStepVideo.visibility = View.GONE
                ivPlayPauseNextStepVideo.visibility = View.GONE
            } else {
                ivToggleUp.visibility = View.GONE
                tvIngredients.visibility = View.VISIBLE
                ivNextStepVideo.visibility = View.VISIBLE
                ivPlayPauseNextStepVideo.visibility = View.VISIBLE
            }
        }

        viewELCurrentStep.addIndicatorListener { _, willExpand ->
            if (willExpand) {
                ivExpansionCurrentStepToggle.setImageResource(R.drawable.ic_toggle_up)
            } else {
                ivExpansionCurrentStepToggle.setImageResource(R.drawable.ic_toggle_down)
            }
        }

        ivLeft.setOnClickListener {
            onBackPressed()
        }
    }

    //Toggle PrePrepare Step Visibility
    private fun togglePrePareSteps(isVisible: Boolean) {
        if (isVisible) {
            nestedScrollSeekBar.setBackgroundColor(resources.getColor(R.color.dark_grey))
            clPrePrepare.visibility = View.VISIBLE
        } else {
            nestedScrollSeekBar.setBackgroundColor(resources.getColor(R.color.white))
            clPrePrepare.visibility = View.GONE
        }
    }

    private fun prepareRecipeView() {
        if (intent.hasExtra("recipe")) {
            recentItem = intent.extras?.getSerializable("recipe") as RecentItem
        }
        //recentItem = RECIPES[0]
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

        tvPageTitle.text = recentItem?.name
        recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)

        prepareRecipeSteps()

        clPrePrepare.post {
            setLayoutParam(clPrePrepare.height + 40)
//            Thread(Runnable {
//                runOnUiThread {
//                    prepareDetailsView()
//                }
//            }).start()
        }

        togglePrePareSteps(true)

        btnNext.setOnClickListener {
            if (btnNext.text.toString().lowercase() == "done") {
                togglePlayPause()
//                OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
//                sendMessage("ingredients=100")
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

        btnSkip.setOnClickListener {
            if (btnSkip.text.toString().lowercase() == "skip") {
                togglePlayPause()
//                OnToCookApplication.instance.speak("${getAudio(recipeStepList[currentRunningStep].name.lowercase())}" + "Wait for " + recipeStepList[currentRunningStep].duration)
//                sendMessage("ingredients=100")
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

        nextExpectedProgressView =
            (recipeStepList[0].durationInSec + recipeStepList[0].stepDuration) - 6

        stepProgress.markers = stepProgressList

        nestedScrollSeekBar.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                seekBarHeight = nestedScrollSeekBar.height
                nestedScrollSeekBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                //seekBarHeight / 2
                //setLayoutParam(seekBarHeight / 3, (seekBarHeight - (seekBarHeight / 3)))
//                setLayoutParam(seekBarHeight / 2, seekBarHeight / 2)
//                prepareDetailsView()
            }
        })

        viewStoveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@OnTocookNewDesign, 4)
            waveGap = Utils.dp(this@OnTocookNewDesign, 4)
            waveMinHeight = Utils.dp(this@OnTocookNewDesign, 5)
            waveCornerRadius = Utils.dp(this@OnTocookNewDesign, 20)
            waveGravity = WaveGravity.RIGHT
            waveBackgroundColor = ContextCompat.getColor(this@OnTocookNewDesign, R.color.dark_grey5)
            waveProgressColor = ContextCompat.getColor(this@OnTocookNewDesign, R.color.colorMWColor)
            setProgressSampleData(getDummyWaveSample(false))
        }

        viewMicrowaveProgress.apply {
            progress = 0.0F
            waveWidth = Utils.dp(this@OnTocookNewDesign, 4)
            waveGap = Utils.dp(this@OnTocookNewDesign, 4)
            waveMinHeight = Utils.dp(this@OnTocookNewDesign, 5)
            waveCornerRadius = Utils.dp(this@OnTocookNewDesign, 20)
            waveGravity = WaveGravity.LEFT
            waveBackgroundColor = ContextCompat.getColor(this@OnTocookNewDesign, R.color.dark_grey5)
            waveProgressColor =
                ContextCompat.getColor(this@OnTocookNewDesign, R.color.colorFlamColor)
            setProgressSampleData(getDummyWaveSample(true))
        }

        var scrollYIni = 0
        nestedScrollSeekBar.setOnTouchListener { _, event ->
            if (MotionEvent.ACTION_UP == event.action || MotionEvent.ACTION_MOVE == event.action) {
                if (isPlaying && (scrollYIni - nestedScrollSeekBar.scrollY) != 0) {
                    toggleManualScrolling(true)
                    performScrollAction()
                }
            } else if (MotionEvent.ACTION_DOWN == event.action) {
                scrollYIni = nestedScrollSeekBar.scrollY
            }
            false
        }

        prepareStepSlider()

        if (intent.getIntExtra("currentstep", -1) == -1) {
            preparePrePareStep()
        }

        nestedScrollSeekBar.viewTreeObserver.addOnScrollChangedListener {
            performScrollAction()
        }
    }

    private fun performScrollAction() {
        if (isManualScroll) {
            val progressPercent =
                (nestedScrollSeekBar.scrollY * 100) / (stepProgress.progressBarHeight)
//                val index = stepProgress.markers.indexOf(
//                    decimalFormat.format(progressPercent).toFloat()
//                )
            var index = stepProgress.markers.indexOfLast {
                it <= decimalFormat.format(progressPercent).toFloat()
            }
            if (index != -1) {
                prepareStepView(index)
            }
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

        (ivCenterPlay.layoutParams as ConstraintLayout.LayoutParams).topMargin =
            topMargin - (Constants.getDp(
                26,
                this@OnTocookNewDesign
            ) / 2)
        (clCurrentRecipe.layoutParams as ConstraintLayout.LayoutParams).topMargin = topMargin + Constants.getDp(
            30,
            this@OnTocookNewDesign
        )
    }

//    private fun prepareDetailsView(isUpdateMargin: Boolean = false) {
//        if (isUpdateMargin) {
//            for ((index, view) in viewContainer.children().withIndex()) {
//                view.id = stepProgressList[index].toInt()
//                val top: Float = (stepProgressList[index] / 100f) * (stepProgress.getBarHeight())
//                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
//                    (top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt()
//            }
//        } else {
//            viewContainer.removeAllViews()
//
//            for ((index, progress) in stepProgressList.withIndex()) {
//                val top: Float = (progress / 100f) * (stepProgress.getBarHeight())
//
//                val rootView = ItemExpandableStepView(this@OnTocookNewDesign)
//                rootView.setDefaultView(
//                    progress.toInt(),
//                    recipeStepList[index].name,
//                    recipeStepList[index].desc,
//                    recipeStepList[index].duration,
//                    "${index + 1}",
//                    recipeStepList[index].action,
//                    recipeStepList[index].image
//                )
//                rootView.setLayoutParam((top.roundToInt() + seekBarTop) - (stepProgress.getBarWidth() / 2).toInt())
//                viewContainer.addView(rootView)
//            }
//        }
//    }

    private fun toggleManualScrolling(forceToggle: Boolean? = null) {
        isManualScroll = forceToggle ?: !isManualScroll

        if (isManualScroll) {
            //llManualScrollStep.visibility = View.VISIBLE
            llNextExpectedStep.visibility = View.VISIBLE
            prepareFloatingCurrentView()
            clFloatingCurrentStep.visibility = View.VISIBLE
            ivCenterPlay.background.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    ContextCompat.getColor(
                        this,
                        R.color.dark_grey2
                    ), BlendModeCompat.SRC_IN
                )
        } else {
            //llManualScrollStep.visibility = View.GONE
            llNextExpectedStep.visibility = View.GONE
            clFloatingCurrentStep.visibility = View.GONE
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

    private fun scrollToPos() {
        val progress =
            ((stepProgress.currentProgress / stepProgress.totalProgress.toFloat()) *
                    (stepProgress.progressBarHeight))
        nestedScrollSeekBar.scrollTo(
            nestedScrollSeekBar.scrollX,
            progress.toInt()
        )
    }

    private fun prepareStepView(index: Int) {
        if (index >= 0 && stepProgress.currentProgress > 0 && stepProgress.currentProgress < 100) {
            manageManualScroll(index)
        }
    }

    private fun manageManualScroll(stepIndex: Int) {
        val totalTime = stepSums - remainSecond

        val index = recipeStepList.indexOfFirst {
            totalTime >= it.durationInSec
                    && totalTime <= (it.durationInSec + it.stepDuration)
        }
        if (index != -1) {
            //viewContainer.children()[index].visibility = View.GONE
            if (isManualScroll) {
                //viewIndicatorCurrentStep.visibility = View.GONE
                clCurrentRecipe.visibility = View.GONE
                clCurrentRecipeTime.visibility = View.GONE

                tvStepTitle.text = recipeStepList[stepIndex].name
                tvStepDescDash.text = recipeStepList[stepIndex].desc
            }
        }
    }

    private fun getDummyWaveSample(isMagnetron: Boolean = false): IntArray {
        val data = IntArray(stepSums)
        var dataIndex = 0
        var skipProgress = stepProg / 3

        for ((index, recipeObj) in recipeStepList.withIndex()) {
            //Divide by 3 because we have sey height of bar as 3 second (1 bar  = 3 sec..)
            for (i in 1..round(recipeObj.stepDuration / 3.0).toInt()) {
                if (isMagnetron) {
                    if (oldPower != "" && i <= skipProgress && index == currentStep - 1) {
                        data[dataIndex] = oldPower.toInt()
                    } else {
                        data[dataIndex] = recipe!!.Instruction[index].Magnetron_power.toInt()
                    }
                } else {
                    if (oldPower != "" && i <= skipProgress && index == currentStep - 1) {
                        data[dataIndex] = oldPower.toInt()
                    } else {
                        data[dataIndex] = recipe!!.Instruction[index].Induction_power.toInt()
                    }
                }
                dataIndex += 1
            }
        }
        oldPower = ""
        return data
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
    }

    private fun prepareProgressbar(isResetProgress: Boolean = true) {
        stepSums = recipeStepList.map { it.stepDuration }.sum()

        if (isResetProgress) {
            val totalProgress = stepSums - remainSecond
            val pro = (totalProgress * 100) / stepSums.toFloat()
            stepProgress.currentProgress = pro.Round()
            viewStoveProgress.progress = pro.Round()
            viewMicrowaveProgress.progress = pro.Round()
        }

        //setStepTime()
        stepProgress.progressBarHeight =
            Constants.getDp(stepSums * 3, this@OnTocookNewDesign).toFloat()
        increment = 100f / stepSums

        stepProgressList.clear()
        stepProgressList.addAll(recipeStepList.map { ((it.durationInSec * 100) / stepSums.toFloat()).Round() })

        tvReadyInTime.text = "${getFormattedTime(stepSums)} min"

        prepareProgressDuration()
    }

//    private fun setStepTime() {
//        if(remainSecond < 0){
//            remainSecond = 0
//        }
//        var min = (remainSecond % 3600) / 60
//        var second = remainSecond % 60
//        tvTime.text = String.format("%02d:%02d", min, second)
//    }

    private fun getAction(name: String): String {
        return when (name.toLowerCase(Locale.getDefault())) {
            "preheat", "close lid" -> {
                "Wait"
            }
            else -> {
                "Add"
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

    private fun preparePrePareStep() {
        OnToCookApplication.instance.speak(recipe!!.Ingredients[currentPrepareStep].title)
        var step = "Step ${currentPrepareStep + 1} of ${totalPrepareStep + 1}"
        tvStep.text = step
        tvPrePrepareTitle.text = recipe!!.Ingredients[currentPrepareStep].title
        tvPrePrepareDesc.text = recipe!!.Ingredients[currentPrepareStep].text
    }

    private fun getMaxTime(instructions: Instructions): Int {
        return instructions.Induction_on_time.toInt()
            .coerceAtLeast(instructions.Magnetron_on_time.toInt())
    }

    private fun getDesc(name: String): String {
        return when (name.toLowerCase(Locale.getDefault())) {
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
            else -> {
                "And wait"
            }
        }
    }

    private fun getImages(name: String): Int {
        return when (name.toLowerCase(Locale.getDefault())) {
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
            else -> {
                R.drawable.ic_step1
            }
        }
    }
}