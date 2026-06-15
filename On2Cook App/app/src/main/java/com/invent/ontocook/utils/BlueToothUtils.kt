package com.invent.ontocook.utils

import android.bluetooth.BluetoothAdapter

internal fun isBluetoothOn(): Boolean {
    val adapter =
        BluetoothAdapter.getDefaultAdapter() ?: return false
    return adapter.isEnabled
}

internal fun toggleBluetooth(enable: Boolean) {
    val adapter =
        BluetoothAdapter.getDefaultAdapter() ?: return
    if (enable) {
        adapter.enable()
    } else {
        if (adapter.isEnabled) {
            adapter.disable()
        }
    }
}