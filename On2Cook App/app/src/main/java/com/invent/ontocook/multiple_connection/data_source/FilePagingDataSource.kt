package com.invent.ontocook.multiple_connection.data_source

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.invent.ontocook.multiple_connection.model.FileModel
import com.invent.ontocook.utils.DebugLog
import kotlinx.coroutines.delay

class FilePagingDataSource(
    private val fileList: ArrayList<FileModel>,
    private val currentQuery: String
) : PagingSource<Int, FileModel>() {
    private var filterList: ArrayList<FileModel> = ArrayList()

    private companion object {
        const val STARTING_PAGE_INDEX = 1
    }

    init {
        filterList.addAll(fileList)
    }


    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileModel> {
        val page = params.key ?: 0
//        val randomList = OnToCookApplication.dbInstance.recipeDao().get(params.loadSize,page * params.loadSize)
        DebugLog.e("FilePagingDataSource: ${page * params.loadSize} End  ${(page * params.loadSize) + 30} Condition ${fileList.size >= ((page * params.loadSize) + 30)} Lis Size ${fileList.size}")
        return try {
            if (page != 0) delay(1000)
            val list = ArrayList<FileModel>()
            var filterListSize = 0
            if (currentQuery.isNotEmpty()) {
                filterListSize = fileList.filter { it.fileName.contains(currentQuery) }.size
            }
            if ((currentQuery.isEmpty() && fileList.size >= ((page * params.loadSize) + 30))
                || (currentQuery.isNotEmpty() && filterListSize >= ((page * params.loadSize) + 30))
            ) {
                list.addAll(getList(page * params.loadSize, (page * params.loadSize) + 30))
            } else {
                list.addAll(
                    getList(
                        page * params.loadSize,
                        if (currentQuery.isEmpty()) fileList.size else
                            filterListSize
                    )
                )
            }
            list.forEach {
                DebugLog.e("File ${it.fileName}")
            }
            LoadResult.Page(
                list,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (fileList.size < ((page * params.loadSize) + 30)) null else page + 1
            )
//            LoadResult.Page(arrayListOf("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26"),2,3)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private fun getList(startIndex: Int, endIndex: Int): List<FileModel> {
        DebugLog.e("${fileList.filter { it.fileName.contains(currentQuery) }.size}")
        return fileList.filter { it.fileName.contains(currentQuery) }.subList(
            startIndex,
            endIndex
        )
    }

    override fun getRefreshKey(state: PagingState<Int, FileModel>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}