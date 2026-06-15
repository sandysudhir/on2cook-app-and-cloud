package com.invent.ontocook.models

class User {
    var name = ""
    var description = ""
}

data class UserData(
    val result : List<Result>
)

data class Result(val userId : Int, val title : String)