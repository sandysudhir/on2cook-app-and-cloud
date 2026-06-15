package com.invent.ontocook.models

class StepsObject {
    var name = ""
    lateinit var steps : List<Step>
}

class Step{
    var number : Int = 0
    var step : String = ""
    lateinit var ingredients : List<Ingrednts>
    var length : StepLength? = null
}

class Ingrednts{
    var id : Int = 0
    var name : String = ""
    var amount : Double = 0.0
    var unit : String = ""
    var localizedName : String = ""
    var image : String = ""
}

class StepLength{
    var number : Int = 0
    var unit : String = ""
}