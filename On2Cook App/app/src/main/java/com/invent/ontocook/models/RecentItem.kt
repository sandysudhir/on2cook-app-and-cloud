package com.invent.ontocook.models

import java.io.Serializable

class RecentItem(
    id : Int,
    image: Int,
    name: String,
    difficulty: String,
    duration: String,
    desc : String = "",
    ingredients: MutableList<IngredientsSteps> = mutableListOf<IngredientsSteps>(),
    nutritionalInfo: MutableList<Nutritional> = mutableListOf<Nutritional>(),
    recipe : String = ""
) : Serializable {
    var id = id
    var image = image
    var name = name
    var difficulty = difficulty
    var duration = duration
    var desc = desc
    var ingredients = ingredients
    var nutritionalInfo = nutritionalInfo
    var recipe = recipe
}