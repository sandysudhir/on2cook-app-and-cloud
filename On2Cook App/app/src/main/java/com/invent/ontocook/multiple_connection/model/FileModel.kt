package com.invent.ontocook.multiple_connection.model

import com.invent.ontocook.utils.Constants
import java.io.File


class FileModel(
    var id: Int = 0,
    var fileName: String = "",
    var type: Constants.FILE_TYPE = Constants.FILE_TYPE.STATIC,
    var isSelected: Boolean = false,
    var assetsPath: String? = null,
    val file: File?=null,
    val fileSize: Long?=null,
)