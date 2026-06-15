package com.invent.ontocook.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.polidea.rxandroidble2.RxBleClient

private const val REQUEST_PERMISSION_BLE_SCAN = 101
private const val REQUEST_PERMISSION_EXTERNAL = 103
private const val REQUEST_PERMISSION_SYSTEM_WINDOWS = 102

internal fun Activity.requestBluetoothPermission(client: RxBleClient) =
    ActivityCompat.requestPermissions(
        this,
        arrayOf(client.recommendedScanRuntimePermissions[0]),
        REQUEST_PERMISSION_BLE_SCAN
    )

internal fun Activity.requestExternalStoragePermission() =
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_PERMISSION_EXTERNAL
    )

internal fun Activity.requestSystemWindowPermission() =
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW),
        REQUEST_PERMISSION_SYSTEM_WINDOWS
    )

internal fun isBluetoothPermissionGranted(requestCode: Int, grantResults: IntArray) =
    requestCode == REQUEST_PERMISSION_BLE_SCAN && grantResults[0] == PackageManager.PERMISSION_GRANTED

internal fun isSystemWindowsPermissionGranted(requestCode: Int, grantResults: IntArray) =
    requestCode == REQUEST_PERMISSION_SYSTEM_WINDOWS && grantResults[0] == PackageManager.PERMISSION_GRANTED