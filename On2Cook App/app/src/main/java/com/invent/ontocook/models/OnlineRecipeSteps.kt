package com.invent.ontocook.models

class OnlineRecipeSteps(
    image: String,
    name: String,
    desc: String,
    duration: String,
    action: String,
    stepDuration : Int,
    durationInSec: Int
) {
    var image = image
    var name = name
    var desc = desc
    var duration = duration
    var action = action
    var stepDuration = stepDuration
    var durationInSec = durationInSec
}