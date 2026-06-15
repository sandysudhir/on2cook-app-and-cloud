package com.invent.ontocook.models


class RecipeNew {
    var name = mutableListOf<String>()
    var audio1 = mutableListOf<String>()
    var audio2 = mutableListOf<String>()
    var description: String = ""
    var imageUrl: String = ""
    var id: String = ""
    var tags: String = ""
    var difficulty: String = ""
    var category: String = ""
    var subCategories: String = ""
    var Ingredients = mutableListOf<Ingredients>()
    var Instruction = mutableListOf<Instructions>()

    constructor()

    fun toMap(): Map<Any, Any> {
        val result = HashMap<Any, Any>()
        result["name"] = name
        result["description"] = description
        result["image"] = imageUrl
        result["tags"] = tags
        result["difficulty"] = difficulty
        result["category"] = category
        result["subCategories"] = subCategories
        return result
    }

}