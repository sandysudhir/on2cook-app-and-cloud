package com.invent.ontocook.multiple_connection.model

import androidx.fragment.app.Fragment


data class TabFragmentData (
    var fragment : Fragment,
    var id: Int = 0,
    var name: String = "",
    var mac: String = "",
)