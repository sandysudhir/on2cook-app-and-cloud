package com.invent.ontocook.models

import java.io.Serializable

class IngredientsSteps(
    image: String,
    name: String,
    qty: String,
    desc: String
) : Serializable {
    var image = image
    var name = name
    var qty = qty
    var desc = desc
}