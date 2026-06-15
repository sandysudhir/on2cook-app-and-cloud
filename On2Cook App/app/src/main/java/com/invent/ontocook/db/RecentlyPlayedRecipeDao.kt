package com.invent.ontocook.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@Dao
interface RecentlyPlayedRecipeDao {

//    @Insert
//    fun insert(note: RecentlyPlayedRecipe): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(note: RecentlyPlayedRecipe)

    @Update
    fun update(note: RecentlyPlayedRecipe)

//    @Query("UPDATE PLAYLIST set name=:name WHERE id=:playListId")
//    suspend fun update(name: String, playListId: String)
//
//    @Query("DELETE from PLAYLIST WHERE id=:playListId")
//    suspend fun delete( playListId: String)
//
//    @Query("UPDATE PLAYLIST set songJSon=:playList, songsCount = :songsCounts WHERE id=:playListId")
//    suspend fun update(playList: String, playListId: String, songsCounts: String)

    @Delete
    fun delete(note: RecentlyPlayedRecipe)

    @Query("DELETE FROM RECENTLY_PLAYED_RECIPE")
    fun deleteAll()

    //-------Get all recipes for dummy macaddress-------//
    @Query("SELECT * FROM RECENTLY_PLAYED_RECIPE ORDER BY insertTime DESC LIMIT 5")
    fun getAllRecipe(): Flowable <List<RecentlyPlayedRecipe>>

    //-------Get all recipes, mac address wise-------//
    @Query("SELECT * FROM RECENTLY_PLAYED_RECIPE WHERE macAddress=:mac ORDER BY insertTime DESC LIMIT 5")
    fun getAllRecipe(mac:String): Flowable <List<RecentlyPlayedRecipe>>

    @Query("SELECT * FROM RECENTLY_PLAYED_RECIPE")
    fun getAllRecipeList(): List<RecentlyPlayedRecipe>
    @Query("SELECT * FROM RECENTLY_PLAYED_RECIPE")
    fun getAllLiveRecipe(): LiveData<List<RecentlyPlayedRecipe>>

    @Query("SELECT * FROM RECENTLY_PLAYED_RECIPE WHERE id=:playListId LIMIT 1")
    fun getRecipe(playListId: Int): Single<RecentlyPlayedRecipe>

//    @Query("UPDATE * FROM RECIPE WHERE name=:playListId")
//    fun updateByName(playListId: String): Single<RecentlyPlayedRecipe>
//
//    @Query("SELECT * FROM RECIPE WHERE id=:playListId LIMIT 1")
//    fun getPlayList(playListId: String?): LiveData<RecentlyPlayedRecipe>
//
//    @Query("SELECT songJSon FROM RECIPE WHERE id=:playListId LIMIT 1")
//    fun getPlayListJson(playListId: String?): String
}