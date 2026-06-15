package com.invent.ontocook.Classes

import com.invent.ontocook.models.Ingredient
import com.invent.ontocook.models.Instructions

open class Steps {
    var ingredients = mutableListOf<Ingredient>()
    var steps  = mutableListOf<Instructions>()
    var stepTime = mutableListOf<StepTime>()

    open fun getStepsList() : MutableList<Instructions> {
        return steps
    }

    open fun getWaterIngredients() : Ingredient{
        var ingredient = Ingredient()
        ingredient.name = "water"
        ingredient.amount = 1f
        ingredient.unit = "cup"
        ingredient.consistency = "liquid"
        ingredient.original = "water"
        return ingredient
    }
}