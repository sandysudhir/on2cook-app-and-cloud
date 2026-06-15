package com.invent.ontocook.models

class Ingredients {
    var id : Int = 0
    var title : String = ""
    var pan_type : String? = null
    var text : String = ""
    var weight : String = ""
    var image : String = ""
    var app_audio : String = ""
    var audio : String = ""
    var audioP : String = ""
    var audioQ : String = ""
    var audioI : String = ""
    var audioU : String = ""
    var audioPUrl: String? = null
    var audioQUrl: String? = null
    var audioIUrl: String? = null
    var audioUUrl: String? = null
    constructor()

    constructor(id : Int = 0, title : String, text : String, weight : String, image : String = ""){
        this.id = id
        this.title = title
        this.text = text
        this.weight = weight
        this.image = image
    }
}