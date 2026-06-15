package com.invent.ontocook.models

import java.io.Serializable

class Firmware(
    num: String,
    url: String
) : Serializable {
    var num = num
    var url = url
}