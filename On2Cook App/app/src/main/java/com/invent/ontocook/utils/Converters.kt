package com.invent.ontocook.utils

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.invent.ontocook.extension.fromJson
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.models.Instructions

@ProvidedTypeConverter
class Converters {

    @TypeConverter
    fun fromStringArrayList(value: ArrayList<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringArrayList(value: String): ArrayList<String> {
        return try {
            Gson().fromJson(value) //using extension function
        } catch (e: Exception) {
            arrayListOf()
        }
    }

    @TypeConverter
    fun fromStringInstructionArrayList(value: ArrayList<Instructions>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringInstructionArrayList(value: String): ArrayList<Instructions> {
        return try {
            Gson().fromJson<ArrayList<Instructions>>(value) //using extension function
        } catch (e: Exception) {
            arrayListOf()
        }
    }

    @TypeConverter
    fun fromStringIngredientsArrayList(value: ArrayList<Ingredients>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringIngredientsArrayList(value: String): ArrayList<Ingredients> {
        return try {
            Gson().fromJson<ArrayList<Ingredients>>(value) //using extension function
        } catch (e: Exception) {
            arrayListOf()
        }
    }
}