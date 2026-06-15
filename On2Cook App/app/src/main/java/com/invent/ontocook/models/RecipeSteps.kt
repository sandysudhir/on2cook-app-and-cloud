package com.invent.ontocook.models

class RecipeSteps(
    image: Int,
    name: String,
    desc: String,
    duration: String,
    action: String,
    stepDuration : Int,
    durationInSec: Int,
    app_audio : String
) {
    var image = image
    var name = name
    var desc = desc
    var duration = duration
    var action = action
    var stepDuration = stepDuration
    var durationInSec = durationInSec
    var app_audio = app_audio
}