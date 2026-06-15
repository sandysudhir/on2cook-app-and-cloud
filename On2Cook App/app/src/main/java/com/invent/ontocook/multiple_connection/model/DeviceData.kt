package com.invent.ontocook.multiple_connection.model

import com.invent.ontocook.utils.Constants
import java.util.Timer


class DeviceData {
    var timer : Timer? = null
    var isSendingFileType : Int = Constants.JSON_FILE
    val mapDataToWrite: HashMap<Int, String> = HashMap()
    val mapDataToWriteOTA: HashMap<Int, ByteArray> = HashMap()
    var acknowledgementIndex: Int = 0
}