package com.invent.ontocook.Classes

import com.invent.ontocook.models.Instructions

class Soakings : Steps() {

    var ingredients0Index = 0
    var ingredients1Index = 0
    var magnetronIOnTime = 20
    var inductionOnTime = 20

    override fun getStepsList(): MutableList<Instructions> {
        prepareSteps()
        return steps
    }

    private fun prepareSteps(){
        ingredients0Index = ingredients.indexOfFirst { it.consistency == "liquid" }

        if(ingredients0Index == -1){
            ingredients.add(getWaterIngredients())
            ingredients1Index = 0
        }

        ingredients1Index = ingredients.indexOfFirst { it.consistency == "solid" }

        steps.add(Instructions("Take ${ingredients[ingredients0Index].amount} ${ingredients[ingredients0Index].unit}  ${ingredients[ingredients0Index].name} in a bowl", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
        steps.add(Instructions("Add ${ingredients[ingredients1Index].name} to ${ingredients[ingredients0Index].name} and Wash twice", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
        steps.add(Instructions("Immerse ${ingredients[ingredients0Index].name} in ${ingredients[ingredients1Index].amount} ${ingredients[ingredients1Index].unit} ${ingredients[ingredients1Index].name} and leave for ${stepTime[0].time} ${stepTime[0].unit}", "",
            "$magnetronIOnTime", "0", "$inductionOnTime",
            "0", durationInSec = 0))
    }

}