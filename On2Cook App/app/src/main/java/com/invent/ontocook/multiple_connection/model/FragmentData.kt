package com.invent.ontocook.multiple_connection.model

import androidx.fragment.app.Fragment


data class FragmentData (
    var fragment : Fragment,
    var index: Int = 0,
    var mac:String,
    var id: Int = 0
)