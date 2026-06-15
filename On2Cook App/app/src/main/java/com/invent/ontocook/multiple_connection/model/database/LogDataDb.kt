package com.invent.ontocook.multiple_connection.model.database

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "LOG")
class LogDataDb(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var deviceOnCounter: Int = 0,
    var macAddress: String,
    var igbtTemp: Float,
    var glassTemp: Float,
    var utcTime: String,
    var date: String,
    var deviceOnCounterTime: String,
    var power: String,
    var indCurrent: Float,
    var magCurrent: Float,
    var magTemp: Float,
    var coilTemp: Float,
    var ambientTemp: Float,
    var panTemp: Float,
    var pcbTemp: Float,
    var oilTemp: Float,
    var deviceOnTime: Int,
    var deviceOffTime: Int,
    var indTime: Int,
    var magTime: Int,
    var recipeName: String = "",
    var stepNo: String = "",
    var timeRemains: String = "",
    var stepName: String = "",
    var totalSteps: String = "",
    var recipeTime: String = "",
    var elapsedTime: String = ""
){
    constructor() : this(deviceOnCounter = 0,
        date = "18-07-23",
        macAddress = "",
        utcTime = "1679665293",
        igbtTemp = 123f,
        glassTemp = 100f,
        deviceOnCounterTime = "3",/*change*/
        power = "50",/*calculate*/
        indCurrent = 10f,/*calculate*/
        magCurrent = 20f,/*calculate*/
        magTemp = 20f,/*Done*/
        coilTemp = 20f,/*Done*/
        ambientTemp = 20f,
        panTemp = 20f,/*Done*/
        pcbTemp = 20f,/*Done*/
        oilTemp = 20f,
        deviceOnTime = 10,/*Done*/
        deviceOffTime = 20,/*Done*/
        indTime = 10,
        magTime = 10,
        recipeName = "Aloo",/*Done*/
        stepNo = "2",/*Done*/
        timeRemains = "10",/*Done*/
        stepName = "Cook",
        totalSteps = "2",
        recipeTime = "4",
        elapsedTime = "5"
    )
}