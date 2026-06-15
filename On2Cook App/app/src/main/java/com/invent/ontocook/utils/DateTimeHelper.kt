package com.invent.ontocook.utils

import android.util.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit


object DateTimeHelper {

    fun convertDateFromUtc(log: String): String {
        val dateStr = "Jul 16, 2013 12:08:59 AM"
        val df = SimpleDateFormat("MMM dd, yyyy HH:mm:ss a", Locale.ENGLISH)
        df.setTimeZone(TimeZone.getTimeZone("UTC"))
        val date: Date = df.parse(dateStr)
        df.setTimeZone(TimeZone.getDefault())
        val formattedDate: String = df.format(date)
        return formattedDate
    }

    fun getDate(milliSeconds: Long, dateFormat: String?): String? {
        val formatter = SimpleDateFormat(dateFormat)
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return try {
            formatter.format(calendar.time)
        } catch (e: NumberFormatException) { // handle your exception
            ""
        }
    }

    fun getDate(milliSeconds: String, dateFormat: String?): String {
        val formatter = SimpleDateFormat(dateFormat)
        return try {
            val dateLarge = Date(milliSeconds.toLong() * 1000L)
            formatter.format(dateLarge.time)
        } catch (e: NumberFormatException) { // handle your exception
            ""
        }
    }

    fun getDateFromPicker(milliSeconds: String, dateFormat: String?): String {
        val formatter = SimpleDateFormat(dateFormat)
        return try {
            val dateLarge = Date(milliSeconds.toLong() * 1000L)
            formatter.format(dateLarge.time)
        } catch (e: NumberFormatException) { // handle your exception
            ""
        }
//        val calendar: Calendar = Calendar.getInstance()
//        try {
//            calendar.timeInMillis = milliSeconds.toLong()
//            return formatter.format(calendar.time)
//        } catch (e: NumberFormatException) { // handle your exception
//            return ""
//        }
    }

    fun getDeviceTime(milliSeconds: String, milliSecondsFirst: String): Int {
        return try {
            val dateLarge = Date(milliSeconds.toLong() * 1000L)
            val dateSmall = Date(milliSecondsFirst.toLong() * 1000L)
            val itemDateStr = SimpleDateFormat("dd-MMM HH:mm").format(dateSmall)
            val diff = dateLarge.time - dateSmall.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            TimeUnit.MILLISECONDS.toSeconds(dateLarge.time - dateSmall.time).toInt()
        } catch (e: NumberFormatException) { // handle your exception
            Log.e("NumberFormatException", "$e")
            0
        }
    }

    fun getFormatedDate(replace: String): String {
        val inputPattern = "dd_MM_yy"
        val outputPattern = Constants.DATE_FORMAT
        val inputFormat = SimpleDateFormat(inputPattern)
        val outputFormat = SimpleDateFormat(outputPattern)
        var str = ""
        try {
            val date = inputFormat.parse(replace)
            str = outputFormat.format(date!!)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return str
    }

    fun getTime(totalSecs: Int): String? {
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return String.format("%02d : %02d : %02d", hours, minutes, seconds)
    }

    fun getTimeInMinSec(totalSecs: Int): String? {
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (seconds == 0)
            String.format("%d", minutes)
        else if (minutes == 0)
            String.format("%02d:%02d", minutes, seconds)
        else if (hours == 0)
            String.format("%02d : %02d ", minutes, seconds)
        else
            String.format("%02d : %02d : %02d ", hours, minutes, seconds)
    }

    fun getTimeCheck(totalSecs: Int): String? {
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours == 0 && minutes == 0)
            String.format(" %02d Sec", seconds)
        else if (hours == 0)
            String.format("%02d : %02d ", minutes, seconds)
        else
            String.format("%02d : %02d : %02d ", hours, minutes, seconds)
    }
}