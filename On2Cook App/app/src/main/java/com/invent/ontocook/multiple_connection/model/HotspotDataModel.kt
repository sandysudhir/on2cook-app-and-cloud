package com.invent.ontocook.multiple_connection.model

import android.net.wifi.WifiManager
import com.invent.ontocook.multiple_connection.model.database.LogDataDb


class HotspotDataModel(
    var ssid:String,
    var pass:String,
    val reservation: WifiManager.LocalOnlyHotspotReservation
)