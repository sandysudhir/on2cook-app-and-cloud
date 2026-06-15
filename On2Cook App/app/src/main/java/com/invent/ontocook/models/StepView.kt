package com.invent.ontocook.models

import android.view.LayoutInflater
import android.view.View
import com.invent.ontocook.R
import ernestoyaquello.com.verticalstepperform.Step

class StepView@JvmOverloads constructor(
    title: String?,
    subtitle: String? = ""
) : Step<String>(title, subtitle) {

    override fun getStepData(): String {
        return "Step"
    }

    override fun getStepDataAsHumanReadableString(): String {
        return "Step"
    }

    override fun restoreStepData(data: String?) {

    }

    override fun isStepDataValid(stepData: String?): IsDataValid {
        return IsDataValid(true)
    }

    override fun createStepContentLayout(): View {
        // We create this step view programmatically
//        var alarmDescriptionEditText = TextInputEditText(context)
//        alarmDescriptionEditText.setHint("Add text")
//        alarmDescriptionEditText.setSingleLine(true)
//        alarmDescriptionEditText.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
//            formView.goToNextStep(true)
//            false
//        })
//
//        return alarmDescriptionEditText
        return LayoutInflater.from(context).inflate(
            R.layout.item_add_recipe, null, false
        )
    }

    override fun onStepOpened(animated: Boolean) {

    }

    override fun onStepClosed(animated: Boolean) {

    }

    override fun onStepMarkedAsCompleted(animated: Boolean) {

    }

    override fun onStepMarkedAsUncompleted(animated: Boolean) {

    }
}