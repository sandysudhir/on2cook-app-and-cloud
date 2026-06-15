package com.invent.ontocook.Classes

import com.invent.ontocook.models.Instructions

class Adding : Steps() {

    var magnetronIOnTime = 20
    var inductionOnTime = 20

    override fun getStepsList(): MutableList<Instructions> {
        prepareSteps()
        return steps
    }

    private fun prepareSteps(){
        var instructions = mutableListOf<String>()

        for(ingredients in ingredients){
            instructions.add("${ingredients.amount} ${ingredients.unit} of ${ingredients.name}")
        }

        steps.add(Instructions("Add ${instructions.joinToString(", ")}", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
    }

}