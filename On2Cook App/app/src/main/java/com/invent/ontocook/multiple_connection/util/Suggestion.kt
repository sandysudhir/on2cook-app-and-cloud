package com.invent.ontocook.multiple_connection.util

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion

class Suggestion(var suggestionId: Int = 0, var suggestionText: String = "") :
    SearchSuggestion {

        constructor(parcel: Parcel) : this()

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            TODO("Not yet implemented")
        }

        override fun getBody(): String {
            return suggestionText
        }

        companion object CREATOR : Parcelable.Creator<Suggestion> {
            override fun createFromParcel(parcel: Parcel): Suggestion {
                return Suggestion(parcel)
            }

            override fun newArray(size: Int): Array<Suggestion?> {
                return arrayOfNulls(size)
            }
        }
    }