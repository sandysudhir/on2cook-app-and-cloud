package com.invent.ontocook.multiple_connection.data_source

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.multiple_connection.model.database.Recipe
import kotlinx.coroutines.delay

class PagingDataSource(private val currentQuery: String) : PagingSource<Int, Recipe>() {

    private companion object {
        const val STARTING_PAGE_INDEX = 0
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Recipe> {
        Log.e("CharactersPagingDataSource", "load: ${params.loadSize}")
        Log.e("CharactersPagingDataSource", "load: ${params.placeholdersEnabled}")
        Log.e(
            "CharactersPagingDataSource",
            "paramsKey: ${params.key} STARTING_PAGE_INDEX $STARTING_PAGE_INDEX"
        )
        val pageIndex = params.key ?: STARTING_PAGE_INDEX
        val position = params.key ?: STARTING_PAGE_INDEX
        Log.e(
            "CharactersPagingDataSource",
            "paramsKey: ${params.key} STARTING_PAGE_INDEX Condition ${if (position == STARTING_PAGE_INDEX) null else position - 1}"
        )
        val page = params.key ?: 0
        val list = ArrayList<Recipe>()
        if (currentQuery.isEmpty())
            list.addAll(
                OnToCookApplication.dbInstance.recipeDao()
                    .get(params.loadSize, page * params.loadSize)
            )
        else{
            list.addAll(
                OnToCookApplication.dbInstance.recipeDao()
                    .get(params.loadSize, page * params.loadSize, arrayListOf(currentQuery))
            )
        }
//        withContext(Dispatchers.IO){
//            Log.e("CharactersPagingDataSource", "Dispatchers.IO livePosts ${params.loadSize} loadSize livePosts ${page * params.loadSize}" )
//            mainActivity.runOnUiThread {
//                OnToCookApplication.dbInstance.logDao().allDbByName(params.loadSize,page * params.loadSize)
//            }
//        }

        Log.e("CharactersPagingDataSource", "load pageIndex: $page Size ${list.size}")
        Log.e("CharactersPagingDataSource", "params.loadSize try ${page * params.loadSize}")
        return try {
//            MyApplication.dbInstance.logDao().get().collect {
//                it.forEach { i ->
//                    NopCollector.emit(i.value)
//                    delay(100)
//                }
//                Log.e("Flow", "Value $it")
//            }
            if (page != 0) delay(1000)

            LoadResult.Page(
                list,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (list.isEmpty()) null else page + 1
            )
//            LoadResult.Page(arrayListOf("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26"),2,3)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Recipe>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}