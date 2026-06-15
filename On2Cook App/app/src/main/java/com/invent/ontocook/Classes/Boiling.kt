package com.invent.ontocook.Classes

import com.invent.ontocook.models.Instructions

class Boiling : Steps() {

    var ingredients0Index = 0
    var ingredients1Index = 0
    var magnetronIOnTime = 20
    var inductionOnTime = 20

    override fun getStepsList(): MutableList<Instructions> {
        prepareSteps()
        return steps
    }

    private fun prepareSteps(){
        ingredients0Index = ingredients.indexOfFirst { it.consistency == "solid" }
        ingredients1Index = ingredients.indexOfFirst { it.consistency == "liquid" }

        if(ingredients1Index == -1){
            ingredients.add(getWaterIngredients())
            ingredients1Index = 1
        }

        steps.add(Instructions("Take ${ingredients[ingredients0Index].amount} ${ingredients[ingredients0Index].unit} ${ingredients[ingredients0Index].name} in the Pan", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
        steps.add(Instructions("Add ${ingredients[ingredients1Index].amount} ${ingredients[ingredients1Index].unit} of ${ingredients[ingredients1Index].name} to ${ingredients[ingredients0Index].name}", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
        steps.add(Instructions("Let it Heat for ${stepTime[0].time} ${stepTime[0].unit}", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
    }

}