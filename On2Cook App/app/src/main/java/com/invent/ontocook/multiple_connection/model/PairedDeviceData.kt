package com.invent.ontocook.multiple_connection.model


data class PairedDeviceData (
    var macAddress: String ,
    var id: Int,
    var name: String,
    var isEdit: Boolean,
    var isConnected: Boolean,
)