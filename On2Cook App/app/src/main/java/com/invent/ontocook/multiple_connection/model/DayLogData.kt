package com.invent.ontocook.multiple_connection.model


class DayLogData(
    var date:String, // date of Log File
    val dateFile: HashMap<Int, ByteArray> = HashMap(),
    var dateFilesRowDataIndex: Int = 0, // Used to set in Hashmap
)