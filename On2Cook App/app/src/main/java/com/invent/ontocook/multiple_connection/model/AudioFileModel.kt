package com.invent.ontocook.multiple_connection.model

import com.invent.ontocook.utils.Constants
import java.io.File


class AudioFileModel(
    var id: Int = 0,
    var fileName: String = "",
    var filePath: String?,
    var type: Constants.FILE_TYPE = Constants.FILE_TYPE.STATIC,
    var isSelected: Boolean = false,
    val file: File?,
    val fileSize: Long?=null,
)