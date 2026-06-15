package com.invent.ontocook.multiple_connection.model.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.models.Instructions
import java.io.Serializable


@Entity(tableName = "RECENTLY_PLAYED_RECIPE")
class RecentlyPlayedRecipe : Serializable {
    @ColumnInfo(name = "name") var name: ArrayList<String> = ArrayList()
    @ColumnInfo(name = "audio1") var audio1: ArrayList<String> = ArrayList()
    @ColumnInfo(name = "audio2") var audio2: ArrayList<String> = ArrayList()

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
    var macAddress = ""
    var description: String = ""
    var imageUrl: String = ""
    var tags: String = ""
    var difficulty: String = ""
    var category: String = ""
    var subCategories: String = ""
    var insertTime: Long = 0
//    var Ingredients: String = ""/* =  ArrayList<Ingredients>()*/
//    var Instruction: String = "" /*=  ArrayList<Instructions>()*/,
    @ColumnInfo(name = "Instruction") var Instruction: ArrayList<Instructions> = ArrayList()
    @ColumnInfo(name = "Ingredients") var Ingredients: ArrayList<Ingredients> = ArrayList()
    fun toMap(): Map<Any, Any> {
        val result = HashMap<Any, Any>()
        result["name"] = name
        result["description"] = description
        result["image"] = imageUrl
        result["tags"] = tags
        result["difficulty"] = difficulty
        result["category"] = category
        result["subCategories"] = subCategories
        return result
    }
}