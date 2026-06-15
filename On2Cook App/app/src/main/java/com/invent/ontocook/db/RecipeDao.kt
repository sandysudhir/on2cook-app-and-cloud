package com.invent.ontocook.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.invent.ontocook.multiple_connection.model.database.Recipe
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.Flow
import android.database.Cursor

@Dao
interface RecipeDao {

//    @Insert
//    fun insert(note: RecipeDb): Completable

    @Insert(/*onConflict = OnConflictStrategy.REPLACE*/)
    fun insert(note: Recipe)


    @Update
    fun update(note: Recipe)

//    @Query("UPDATE PLAYLIST set name=:name WHERE id=:playListId")
//    suspend fun update(name: String, playListId: String)
//
//    @Query("DELETE from PLAYLIST WHERE id=:playListId")
//    suspend fun delete( playListId: String)
//
//    @Query("UPDATE PLAYLIST set songJSon=:playList, songsCount = :songsCounts WHERE id=:playListId")
//    suspend fun update(playList: String, playListId: String, songsCounts: String)

    @Delete
    fun delete(note: Recipe)

    //    @Query("SELECT * FROM RECIPE")
//    fun getAllRecipe(): Single<List<RecipeDb>>
    @Query("SELECT * FROM RECIPE")
    fun getAllRecipe1(): Flowable<List<Recipe>>

    @Query("SELECT * FROM RECIPE ORDER BY RANDOM() LIMIT 2")
    fun getRandomRecipe(): List<Recipe>

    @Query("SELECT * FROM RECIPE")
    fun getAllRecipeList(): List<Recipe>

    @Query("SELECT * FROM RECIPE WHERE name=:query LIMIT :size OFFSET :offset")
    fun get(size: Int, offset: Int, query: ArrayList<String>): List<Recipe>

    @Query("SELECT * FROM RECIPE WHERE name LIKE '%' || :itemName || '%'")
    fun isSameNameItemAlreadyExist(itemName: String): List<Recipe>

    @Query("SELECT * FROM RECIPE ORDER BY name ASC LIMIT :size OFFSET :offset")
    fun getAlphabetical(size: Int, offset: Int): List<Recipe>

    @Query("SELECT * FROM RECIPE LIMIT :size OFFSET :offset")
    fun get(size: Int, offset: Int): List<Recipe>

    @Query("SELECT * FROM RECIPE LIMIT 10 OFFSET :offset")
    fun getAllLiveRecipePage(offset: Int): LiveData<List<Recipe>>

    //-------Get All Recipes without Pagination-------//
    @Query("SELECT * FROM RECIPE")
    fun getAllLiveRecipe(): LiveData<List<Recipe>>

    //-------Get All Recipes with Pagination-------//
    @Query("SELECT * FROM RECIPE ORDER BY name LIMIT :size OFFSET :offset")
//    @Query("SELECT * FROM RECIPE LIMIT :size OFFSET :offset")
    suspend fun getAllLiveRecipeWithPagination(size: Int, offset: Int): List<Recipe>

    @Query("SELECT COUNT(*) FROM RECIPE")
    fun getAllRecipesCount(): Int

    @Query("SELECT * FROM RECIPE")
    suspend fun getAllRecipe(): List<Recipe>
    @Query("SELECT * FROM RECIPE WHERE id=:playListId LIMIT 1")
    fun getRecipe(playListId: Int): Single<Recipe>

//    @Query("UPDATE * FROM RECIPE WHERE name=:playListId")
//    fun updateByName(playListId: String): Single<RecipeDb>
//
//    @Query("SELECT * FROM RECIPE WHERE id=:playListId LIMIT 1")
//    fun getPlayList(playListId: String?): LiveData<RecipeDb>
//
//    @Query("SELECT songJSon FROM RECIPE WHERE id=:playListId LIMIT 1")
//    fun getPlayListJson(playListId: String?): String
}