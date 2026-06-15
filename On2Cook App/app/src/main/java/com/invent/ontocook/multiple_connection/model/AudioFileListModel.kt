package com.invent.ontocook.multiple_connection.model


class AudioFileListModel(
    var id: Int = 0,
    var name: String,
    var isSelected: Boolean = false,
    var fileModel: List<AudioFileModel> = ArrayList()
)