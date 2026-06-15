package com.invent.ontocook.models

class ExtractRecipe {
    lateinit var items : List<RecipeItemObject>
}

class RecipeItemObject{
    var title : String = ""
    var link : String = ""
}

