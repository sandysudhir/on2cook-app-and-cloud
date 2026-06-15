package com.invent.ontocook.multiple_connection.model

class LogItem(
    var deviceOnCounter: String,
    var power: String,
    val indPower: String,
    var magPower: String,
    var magTemp: String,
    var coilTemp: String,
    var ambientTemp: String,
    var panTemp: String,
    var pcbTemp: String,
    var oilTemp: String,
    var deviceOnTime: String,
    var indTime: String,
    var magTime: String,
    var recipeName: String = "",
    var stepNo: String = "",
    var timeRemains: String = "",
    var stepName: String = "",
    var totalSteps: String = "",
    var recipeTime: String = "",
    var elapsedTime: String = ""
)