package com.invent.ontocook.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import com.invent.ontocook.utils.Converters

@Database(
    entities = [Recipe::class, LogDataDb::class, RecentlyPlayedRecipe::class],
    version = 5
)
@TypeConverters(Converters::class)

abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedRecipeDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    instance = it
                }
            }

        private fun buildDatabase(appContext: Context) =
            Room.databaseBuilder(appContext, AppDatabase::class.java, "On2Cook")
                .fallbackToDestructiveMigration()/*.allowMainThreadQueries()*/
                .addTypeConverter(Converters())
                .build()
    }

}