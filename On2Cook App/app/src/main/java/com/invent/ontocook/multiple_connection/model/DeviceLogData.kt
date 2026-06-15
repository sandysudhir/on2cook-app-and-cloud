package com.invent.ontocook.multiple_connection.model


data class DeviceLogData(
    val logDataList: ArrayList<String> = ArrayList(),//Unused might delete later
    var add: Boolean = false,
    val singleDaysHashData: HashMap<Int, DayLogData> = HashMap(), //used for Single Date's Data
    var dateIndex: Int = 0,
    val logSampleDataList: ArrayList<DayLogData> = ArrayList(), //Unused might delete later
    var logStarted: Boolean = false  //Unused might delete later
)