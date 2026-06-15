package com.invent.ontocook.models

import java.io.Serializable

class Nutritional(
    name: String,
    qty: String,
    desc: String
) : Serializable {
    var qty = qty
    var name = name
    var desc = desc
}