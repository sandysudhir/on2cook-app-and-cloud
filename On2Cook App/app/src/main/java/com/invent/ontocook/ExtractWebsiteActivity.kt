package com.invent.ontocook

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.invent.ontocook.models.ExtractRecipe
import com.invent.ontocook.models.RecipeObject
import com.invent.ontocook.utils.Constants
import com.rx2androidnetworking.Rx2AndroidNetworking
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ExtractWebsiteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extract_website)

        init()
    }

    @SuppressLint("CheckResult")
    private fun init(){
        Rx2AndroidNetworking
            .get(Constants.EXTRACT_RECIPE_URL)
            .addPathParameter("query", "boiled eggs")
            .build()
            .getObjectObservable(ExtractRecipe::class.java)
            .flatMap {
                println("found url    ${it.items.first()}")
                Observable.fromIterable(it.items)
            }
            .flatMap {
                println("calling url    ${it.link}")
                Rx2AndroidNetworking
                    .get(Constants.READ_RECIPE_PAGE_URL)
                    .addPathParameter("extract_url", it.link)
                    .build()
                    .getObjectObservable(RecipeObject::class.java)
            }
            .takeUntil {
                it.title != ""
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if(it.title != ""){
                    println("done.....")
                }
            }, {
                println("Error   ${it.localizedMessage}")
            })
    }
}