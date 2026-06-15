package com.invent.ontocook.floatingsearch

import com.arlib.floatingsearchview.FloatingSearchView
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class RxSearchObservable {
    companion object{
        fun fromView(searchView: FloatingSearchView): Observable<String?>? {
            val subject: PublishSubject<String?> = PublishSubject.create()
            searchView.setOnQueryChangeListener { _, newQuery ->
                subject.onNext(newQuery)
            }
            return subject
        }
    }
}