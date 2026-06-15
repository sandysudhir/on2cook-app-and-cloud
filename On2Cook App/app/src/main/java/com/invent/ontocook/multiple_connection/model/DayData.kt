package com.invent.ontocook.multiple_connection.model

import com.invent.ontocook.multiple_connection.model.database.LogDataDb


class DayData(
    var date:String,
    val listOfItems: ArrayList<LogDataDb> = arrayListOf()
)