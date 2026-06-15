package com.invent.ontocook.models

class RecipeObject {
    var vegetarian : Boolean = false
    var title : String = ""
    var sourceUrl : String = ""
    var summary : String = ""
    lateinit var dishTypes : List<String>
    var instructions : String = ""
    var image : String = ""
    var vegan : Boolean = false
    var readyInMinutes : Int = 0
    lateinit var extendedIngredients : List<Ingredient>
    lateinit var analyzedInstructions : List<StepsObject>
    var reAnalyzedInstructions : ArrayList<Instructions> = ArrayList()
}

class Ingredient{
    var name : String = ""
    var original : String = ""
    var consistency : String = ""
    var amount : Float = 0f
    var unit : String = ""
    var image : String = ""
}