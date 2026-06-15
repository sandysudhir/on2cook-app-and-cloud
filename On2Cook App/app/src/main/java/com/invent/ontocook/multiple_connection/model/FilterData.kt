package com.invent.ontocook.multiple_connection.model

import androidx.room.ColumnInfo

data class FilterData(
//    @ColumnInfo(name = "magCurrentStart")
    var magCurrentStart: Float = 0f,
//    @ColumnInfo(name = "magCurrentEnd")
    var magCurrentEnd: Float = 0f,
//    @ColumnInfo(name = "indCurrentStart")
    var indCurrentStart: Float = 0f,
//    @ColumnInfo(name = "indCurrentEnd")
    var indCurrentEnd: Float = 0f,
//    @ColumnInfo(name = "magTempStart")
    var magTempStart: Float = 0f,
//    @ColumnInfo(name = "magTempEnd")
    var magTempEnd: Float = 0f,
//    @ColumnInfo(name = "coilTempStart")
    var coilTempStart: Float = 0f,
//    @ColumnInfo(name = "coilTempEnd")
    var coilTempEnd: Float = 0f,
//    @ColumnInfo(name = "ambientTempStart")
    var ambientTempStart: Float = 0f,
//    @ColumnInfo(name = "ambientTempEnd")
    var ambientTempEnd: Float = 0f,
//    @ColumnInfo(name = "panTempStart")
    var panTempStart: Float = 0f,
//    @ColumnInfo(name = "panTempEnd")
    var panTempEnd: Float = 0f,
//    @ColumnInfo(name = "pcbTempStart")
    var pcbTempStart: Float = 0f,
//    @ColumnInfo(name = "pcbTempEnd")
    var pcbTempEnd: Float = 0f,
//    @ColumnInfo(name = "oilTempStart")
    var oilTempStart: Float = 0f,
//    @ColumnInfo(name = "oilTempEnd")
    var oilTempEnd: Float = 0f,
//    @ColumnInfo(name = "deviceOnTimeStart")
    var deviceOnTimeStart: Int = 0,
//    @ColumnInfo(name = "deviceOnTimeEnd")
    var deviceOnTimeEnd: Int = 0,
//    @ColumnInfo(name = "deviceOffTimeStart")
    var deviceOffTimeStart: Int = 0,
//    @ColumnInfo(name = "deviceOffTimeEnd")
    var deviceOffTimeEnd: Int = 0,
//    @ColumnInfo(name = "indTimeStart")
    var indTimeStart: Int = 0,
//    @ColumnInfo(name = "indTimeEnd")
    var indTimeEnd: Int = 0,
//    @ColumnInfo(name = "magTimeStart")
    var magTimeStart: Int = 0,
//    @ColumnInfo(name = "magTimeEnd")
    var magTimeEnd: Int = 0
)