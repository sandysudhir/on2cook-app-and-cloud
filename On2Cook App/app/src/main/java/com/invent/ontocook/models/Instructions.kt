package com.invent.ontocook.models

class Instructions {
    var id: Int = 0
    var Text: String = ""
    var app_audio: String = ""
    var Weight: String = ""
    var Magnetron_on_time: String = ""
    var Magnetron_power: String = ""
    var Induction_on_time: String = ""
    var Induction_power: String = ""
    var Audio: String = ""
    var lid: String = ""
    var image: String = ""
    var threshold: String? = null
    var mag_severity: String = ""
    var Indtime_lid_con: String = ""
    var warm_time: String = ""
    var wait_time: String = ""
    var stirrer_on: String = ""
    var durationInSec: Int = 0
    var pump_on: String = ""
    var purge_on: String = ""
    var skip: String = ""
    var audioP: String = ""
    var audioQ: String = ""
    var audioI: String = ""
    var audioU: String = ""
    var audioPUrl: String? = null
    var audioQUrl: String? = null
    var audioIUrl: String? = null
    var audioUUrl: String? = null

    constructor()

    constructor(
        text: String,
        weight: String,
        magnetronOnTime: String,
        magnetronPower: String,
        inductionOntime: String,
        inductionPower: String,
        durationInSec: Int,
        image: String = ""
    ) {
        this.Text = text
        this.Weight = weight
        this.Magnetron_on_time = magnetronOnTime
        this.Magnetron_power = magnetronPower
        this.Induction_on_time = inductionOntime
        this.Induction_power = inductionPower
        this.durationInSec = durationInSec
        this.image = image
    }

    override fun toString(): String {
        return super.toString()
    }
}