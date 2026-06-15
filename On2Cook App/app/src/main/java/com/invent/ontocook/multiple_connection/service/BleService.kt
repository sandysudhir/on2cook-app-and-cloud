package com.invent.ontocook.multiple_connection.service

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.text.TextUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.invent.ontocook.MainActivity
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.model.DayLogData
import com.invent.ontocook.multiple_connection.model.DeviceData
import com.invent.ontocook.multiple_connection.model.DeviceLogData
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import com.invent.ontocook.multiple_connection.ui.CookingFragment
import com.invent.ontocook.multiple_connection.ui.DashboardFragment
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DateTimeHelper.getDate
import com.invent.ontocook.utils.DateTimeHelper.getDeviceTime
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.SharedPreferencesManager
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BleService : Service() {
    private val binder = LocalBinder()
    private var scan: Disposable? = null
    private val hashmapBleConnection = HashMap<String, Observable<RxBleConnection>>()
    private val hashmapDisposable = HashMap<String, CompositeDisposable>()
    private val connectedAddressOrder = linkedSetOf<String>()
    private val connectedDeviceNames = HashMap<String, String>()
    private var uuidForFileTransfer = ""
    private var uuidForWrite = ""
    private val disconnectTriggerSubject = PublishSubject.create<Boolean>()
    private val TAG: String = BleService::class.java.simpleName
    private val mapDataToWriteFile: HashMap<Int, ByteArray> =
        HashMap()      // Hashmap of Audio File
    private val handler = Handler(Looper.getMainLooper())   // Legacy handler for older flows
    private val transferRetryHandlers = HashMap<String, Handler>()
    val mutableLiveData = MutableLiveData<Int>()
    private var acknowledgementIndex: Int = 0     // 0 Initialise

    private var isBinFile = false
    private var totalSize = 0
    private var percentage = 0

    private val deviceHashmap: HashMap<String, DeviceData> =
        HashMap()      // Hashmap for Device Data
    private val deviceLogHashmap: HashMap<String, DeviceLogData> = HashMap()
    private val dateList = ArrayList<String>()
    var startByteIndex = 0
    var endingByteIndex = 450
    var isSending = false  // is Sending In Process
    private var audioFileIndex: Int = 0     // 0 Initialise
    internal val isScanning: Boolean
        get() = scan != null

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }


    fun startScan() {
        scan?.dispose()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build()
        val scanFilter =
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Constants.SERVICE_UUID)))
                .build()
        scan = OnToCookApplication.rxBleClient.scanBleDevices(scanSettings, scanFilter)
            .timeout(10, TimeUnit.SECONDS).onErrorResumeNext(Observable.empty()).doOnError {
                println("doOnError error 1 ${it.localizedMessage}")
            }.take(10, TimeUnit.SECONDS).observeOn(Schedulers.io()).doOnError {
                println("connection error 1 ${it.localizedMessage}")
                onConnectionFailure(it)
            }.subscribe({ result ->
                sendBroadcast(
                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE, result.bleDevice
                )
                println("device found ${result?.bleDevice?.name} (${result?.bleDevice?.macAddress})")

            }, {
                it.message?.let { it1 -> DebugLog.e(it1) }
            }, {
                DebugLog.e("Stop Scan")
                val intent = Intent(Constants.EVENT_STOP_SCAN)
//                intent.putExtra(Constants.EVENT_BLE_ACTION, Constants.EVENT_STOP_SCAN)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        //disconnect devices and dispose scan
        hashmapDisposable.forEach {
            it.value.clear()
        }
        scan?.dispose()
        DebugLog.e("Service onDestroy")
    }

    private fun onConnectionFailure(throwable: Throwable) {
        println("connection error  ${throwable.localizedMessage}")
        handleError(throwable)
    }

    private fun handleError(throwable: Throwable) {
        Log.e(TAG, "handleError: ${throwable.localizedMessage}")
    }

    private fun sendBroadcast(action: String, bleDevice: RxBleDevice?) {
        val intent = Intent(Constants.EVENT_BLE_CONNECTION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, action)
        intent.putExtra(Constants.DEVICE, bleDevice?.bluetoothDevice)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun doSendBroadcast(
        action: String, message: String = "", address: String
    ) {
        val intent = Intent(Constants.EVENT_BLE_CONNECTION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, action)
        intent.putExtra(Constants.MAC_ADDRESS, address)
        if (!TextUtils.isEmpty(message)) {
            intent.putExtra(Constants.EVENT_ERROR_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun stopScan() {
        scan?.dispose()
    }

    fun connect(bleDevice: BluetoothDevice) {
        DebugLog.e("connect TestScan Tab ${bleDevice.address}")
        val rxBleDevice = OnToCookApplication.rxBleClient.getBleDevice(bleDevice.address)
        hashmapBleConnection[bleDevice.address] =
            rxBleDevice.establishConnection(false).doOnError {
                Log.e(TAG, "doOnError establishConnection Tab ${it.message}")
            }.takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance())
        if (isDeviceConnected(rxBleDevice.macAddress)) return
        val dispo =
            rxBleDevice.observeConnectionStateChanges().doOnError {
                Log.e(TAG, "doOnError TestScan Tab ${it.message}")
            }.observeOn(AndroidSchedulers.mainThread())/*.takeUntil(disconnectTriggerSubject)*/
                .subscribe({
                    when (it) {
                        RxBleConnection.RxBleConnectionState.CONNECTING -> {
                            Log.e(TAG, "onBindconnect:1 CONNECTING TestScan Tab")
                        }

                        RxBleConnection.RxBleConnectionState.CONNECTED -> {
                            Log.e(TAG, "onBindconnect:3 CONNECTED TestScan Tab")
//                            establishConnection(rxBleDevice.bluetoothDevice)
                        }

                        RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
                            Log.e(TAG, "onBindconnect:3 DISCONNECTING TestScan Tab$bleDevice")
                        }

                        RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                            markDeviceDisconnected(bleDevice.address)
                            doSendBroadcast(
                                Constants.EVENT_BLE_CONNECTION_ABORT, address = bleDevice.address
                            )
                            Log.e(TAG, "onBind:connect3 DISCONNECTED TestScan Tab $bleDevice")
                        }
                    }
                }, {
                    println("connection completed...${it.printStackTrace()}")
                }, {
                    println("connection completed")
                })
        hashmapDisposable[bleDevice.address] = CompositeDisposable()
        hashmapDisposable[bleDevice.address]?.add(dispo)

        val connectionDisposable =
            hashmapBleConnection[bleDevice.address]?.doOnError {
                DebugLog.e("Error ${it.message}")
            }?.subscribeOn(Schedulers.io())?.subscribe({ rxBleConnection ->
                requestMtu(hashmapBleConnection[bleDevice.address], bleDevice.address)
                rxBleConnection.discoverServices().subscribeOn(Schedulers.io())
                    .subscribe({ service ->
                        service.bluetoothGattServices.forEach { services ->
                            Log.e(TAG, "establishConnection:uuid ${services.uuid}")
                            services.characteristics.forEach {
                                if (isCharacteristicNotifiable(it) && isCharacteristicWriteable(
                                        it
                                    )
                                ) {
                                    Log.e(TAG, "establishNotifyObserver: ${it.uuid}")
                                    establishNotifyObserver(it, bleDevice.address)
                                }
                            }
                        }
                        onConnectionFinished(bleDevice)
                    }, {

                    })
            }) { throwable ->

            }
        connectionDisposable?.let {
            hashmapDisposable[bleDevice.address]?.add(connectionDisposable)
        }

    }

    fun connectMac(bleDevice: String) {
        val rxBleDevice = OnToCookApplication.rxBleClient.getBleDevice(bleDevice)

        hashmapBleConnection[rxBleDevice.macAddress] =
            rxBleDevice.establishConnection(true).takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance())
        requestMtu(hashmapBleConnection[rxBleDevice.macAddress], bleDevice)
        establishConnection(rxBleDevice.bluetoothDevice)
        val connectionDisposable =
            rxBleDevice.observeConnectionStateChanges().observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    Log.e(TAG, "error observeConnectionStateChanges ${it.message}")
                }
                .subscribe({
                    when (it) {
                        RxBleConnection.RxBleConnectionState.CONNECTING -> {
                            Log.e(TAG, "onBind:1 CONNECTING")
                        }

                        RxBleConnection.RxBleConnectionState.CONNECTED -> {
                            Log.e(TAG, "onBind:3 CONNECTED")
//                            establishConnection(rxBleDevice.bluetoothDevice)
                        }

                        RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
                            Log.e(TAG, "onBind:3 DISCONNECTING $bleDevice")
                        }

                        RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                            markDeviceDisconnected(bleDevice)
                            doSendBroadcast(
                                Constants.EVENT_BLE_CONNECTION_ABORT, address = bleDevice
                            )
                            Log.e(TAG, "onBind:3 DISCONNECTED $bleDevice")
                        }
                    }
                }, {
                    println("connection completed...${it.printStackTrace()}")
                }, {
                    println("connection completed")
                })
        hashmapDisposable[rxBleDevice.macAddress]?.add(connectionDisposable)
    }

    fun disconnect(bleDevice: String) {
        markDeviceDisconnected(bleDevice)
        hashmapDisposable[bleDevice]?.dispose()
        hashmapDisposable[bleDevice]?.clear()
//        disconnectTriggerSubject.onNext(true)
    }

    private fun requestMtu(observable: Observable<RxBleConnection>?, address: String) {
        val connectionDisposable = observable!!.firstOrError()
            .flatMap { rxBleConnection -> rxBleConnection.requestMtu(517) }
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ mtu ->
                println(mtu)
            }, this::onReadFailure)
        hashmapDisposable[address]?.add(connectionDisposable)
    }

    private fun onReadFailure(throwable: Throwable) {
        println("Read error: $throwable")
    }

    private fun isCharacteristicNotifiable(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    private fun isCharacteristicReadable(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    }

    private fun isCharacteristicWriteable(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }

    private fun establishConnection(bleDevice: BluetoothDevice) {
//        val connectionObservable = hashmap[bleDevice.address]
//        connectionObservable!!
//            .flatMapSingle(RxBleConnection::discoverServices).subscribe({service->
//                Log.e(TAG, "establishConnection: ${service.bluetoothGattServices}")
//                service.bluetoothGattServices.forEach {services->
//                    Log.e(TAG, "establishConnection:uuid ${services.uuid}")
//                    services.characteristics.forEach {
//                        if (isCharacteristicNotifiable(it) && isCharacteristicWriteable(
//                                it
//                            )
//                        ){
//                            Log.e(TAG, "establishNotifyObserver: ${it.uuid}" )
//                            establishNotifyObserver(it, bleDevice.address)
//                        }
//                    }
//                }
//                onConnectionFinished(bleDevice.address)
//            }, {
//
//            },{
//                Log.e(TAG, "establishConnection: Complete" )
//            })
        val connectionObservable = hashmapBleConnection[bleDevice.address]
        connectionObservable!!.flatMapSingle(RxBleConnection::discoverServices).flatMapSingle {
            Log.e(TAG, "connect: $it")
            for (service in it.bluetoothGattServices) {
                println("service uuid    ${service.uuid}    ${service.type}")
                for (char in service.characteristics) {
                    if (isCharacteristicNotifiable(char) && isCharacteristicWriteable(
                            char
                        )
                    ) {
                        if (char.uuid.toString() == Constants.UUID_FOR_WRITE) {
                            SharedPreferencesManager.insertData(
                                applicationContext, "CharUUIDWrite", char.uuid.toString()
                            )
                            uuidForWrite = char.uuid.toString()
                        }
                        if (char.uuid.toString() == Constants.UUID_FOR_FileTransfer) {
                            SharedPreferencesManager.insertData(
                                applicationContext, "CharUUIDFileTransfer", char.uuid.toString()
                            )
                            uuidForFileTransfer = char.uuid.toString()
                        }
                    }
                }
            }
            it.getCharacteristic(UUID.fromString(uuidForWrite))
        }.observeOn(AndroidSchedulers.mainThread()).doOnError {
            println("connection error 2 ${it.localizedMessage}")
            println("error   ${it.localizedMessage}")
//                onConnectionFailure(it)
        }.doFinally {
            println("connection completed...")
        }.subscribe({
            Log.e(TAG, "establishConnection: Finish Next${it.uuid}")
            establishNotifyObserver(it, bleDevice.address, false)
            establishNotifyObserver(it, bleDevice.address, true)
            onConnectionFinished(bleDevice)
        }, {
            println("connection error 3 ${it.localizedMessage}")
//                onConnectionFailure(it)
        }, {
            println("establishConnection: Finish")
            onConnectionFinished(bleDevice)
        })
    }

    private fun onConnectionFinished(address: BluetoothDevice) {
        connectedAddressOrder.remove(address.address)
        connectedAddressOrder.add(address.address)
        if (!address.name.isNullOrEmpty()) {
            connectedDeviceNames[address.address] = address.name
        }
        println("Connection Success")
        Log.e(TAG, "establishConnection: Complete")
        val intent = Intent(Constants.EVENT_BLE_CONNECTION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, Constants.EVENT_BLE_CONNECTION_SUCCESS)
        intent.putExtra(Constants.MAC_ADDRESS, address.address)
        if (!address.name.isNullOrEmpty()){
            intent.putExtra(Constants.DEVICE_NAME, address.name)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

//        if (SharedPreferencesManager.retriveData(
//                applicationContext,
//                Constants.MAC_ADDRESS_LIST
//            ) != null && SharedPreferencesManager.retriveData(
//                applicationContext,
//                Constants.MAC_ADDRESS_LIST
//            )!!.isNotEmpty()
//        ) {
//            val strMacList = SharedPreferencesManager.retriveData(
//                applicationContext,
//                Constants.MAC_ADDRESS_LIST
//            )
//            val nameType = object : TypeToken<ArrayList<String>>() {}.type
//            val nameList: ArrayList<String> = Gson().fromJson(strMacList, nameType)
//            Log.e(TAG, "onConnectionFinished: ${address in nameList}")
//            if (!nameList.any { it == address })
//                nameList.add(address)
//            SharedPreferencesManager.insertData(
//                applicationContext,
//                Constants.MAC_ADDRESS_LIST,
//                Gson().toJson(nameList)
//            )
//        } else {
//            val nameList: ArrayList<String> = ArrayList()
//            nameList.add(address)
//            SharedPreferencesManager.insertData(
//                applicationContext,
//                Constants.MAC_ADDRESS_LIST,
//                Gson().toJson(nameList)
//            )
//        }
    }

    private fun sendNextData(deviceData: DeviceData, macAddress: String) {
        val mapDataToWrite = deviceData.mapDataToWrite
        when (deviceData.acknowledgementIndex) {
            mapDataToWrite.size + 1 -> {
                writeFileData(
                    macAddress, "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            mapDataToWrite.size + 2 -> {
                deviceData.isSendingFileType = Constants.COMPLETE_FILE
                doSendMessageBroadcast(Constants.FILE_UPLOAD_SUCCESS, macAddress)
            }

            else -> {
                val pnoNumber = deviceData.acknowledgementIndex
                if (mapDataToWrite.contains(pnoNumber)) {
                    val data = mapDataToWrite[pnoNumber]
//                    if (isBinFile) writeFileData(
//                        macAddress, data!!.toByteArray(
//                            Charsets.UTF_8
//                        )
//                    )
//                    else
                    writeFileData(
                        macAddress, "PNO=${pnoNumber},DATA=${data}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            }
        }
    }

    private fun sendBinData(deviceData: DeviceData, macAddress: String) {
        DebugLog.e("sendBinData: ${deviceData.acknowledgementIndex}")
        val mapDataToWrite = deviceData.mapDataToWriteOTA
        when (deviceData.acknowledgementIndex) {
            mapDataToWrite.size + 1 -> {
                DebugLog.e("sendBinData: Complete")
                writeData(
                    macAddress, "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
                if (percentage != 100) {
                    mutableLiveData.postValue(100)
                    percentage = 0
                    totalSize = 0
                }
                doSendMessageBroadcast(Constants.FILE_UPLOAD_SUCCESS, macAddress)
            }

            else -> {
                val pnoNumber = deviceData.acknowledgementIndex
                if (totalSize != 0 && deviceData.acknowledgementIndex % totalSize == 0) {
                    percentage++
                    mutableLiveData.postValue(percentage)
                }
                if (mapDataToWrite.contains(pnoNumber)) {
                    val data = mapDataToWrite[pnoNumber]
                    writeFileData(
                        macAddress, data!!
                    )
                }
                val totalCount = mapDataToWrite.size / 100

            }
        }
    }

    private fun doSendMessageBroadcast(
        action: String, macAddress: String
    ) {
        val intent = Intent(Constants.EVENT_BLE_COMMUNICATION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, action)
        intent.putExtra(Constants.MAC_ADDRESS, macAddress)
//        if (!TextUtils.isEmpty(message)) {
//            intent.putExtra(Constants.EVENT_MESSAGE, message)
//        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    //    private fun sendAudioData() {
//        Log.e(TAG, "sendAudioData: $acknowledgementIndex")
//
//        when (acknowledgementIndex) {
//            mapDataToWriteFile.size + 1 -> {
//                OnToCookApplication.instance.bleBoundService.writeFileData(
//                    "COMPLETE".toByteArray(
//                        Charsets.UTF_8
//                    )
//                )
//            }
//            mapDataToWriteFile.size + 2 -> {
//                audioFileIndex++
//                sendAudioFile()
//                Log.e(TAG, "sendAudioData: Complete$audioFileIndex")
//            }
//            else -> {
//                if (isAudioSending) {
//                    val pnoNumber = acknowledgementIndex
//                    Log.e(TAG, "sendAudioData: $pnoNumber")
//                    if (mapDataToWriteFile.contains(pnoNumber)) {
//                        val data = mapDataToWriteFile[pnoNumber]
//                        val firstBytes: ByteArray = "PNO=${pnoNumber},DATA=".toByteArray(
//                            Charsets.UTF_8
//                        )
//
//                        Log.e(TAG, "sendAudioData: $firstBytes")
//
//                        val dataToWrite = ByteArray(firstBytes.size + data!!.size)
//                        System.arraycopy(firstBytes, 0, dataToWrite, 0, firstBytes.size)
//                        System.arraycopy(data, 0, dataToWrite, firstBytes.size, data.size)
//                        Log.e(TAG, "sendAudioData: Data ${data.size}")
//                        Log.e(TAG, "sendAudioData:Total ${dataToWrite.size}")
//
////                    val outputStream = ByteArrayOutputStream()
////                    outputStream.write(a)
////                    outputStream.write(data)
////                    val dataToWrite = outputStream.toByteArray()
//                        writeFileData(
//                        macAddress = ,
//                            dataToWrite
//                        )
//                    }
//                }
//            }
//        }
//    }
    fun sendBinFile(macAddress: String, bytes: ByteArray) {
        if (!deviceHashmap.contains(macAddress)) {
            deviceHashmap[macAddress] = DeviceData()
        }
        val deviceItem: DeviceData = deviceHashmap[macAddress]!!
        deviceItem.isSendingFileType = Constants.BIN_FILE
        deviceItem.acknowledgementIndex = 0
        val mapDataToWriteOTA = deviceItem.mapDataToWriteOTA
        mapDataToWriteOTA.clear()
        val totalSizeToSend = bytes.size
        var startByteIndex = 0
        var endingByteIndex = 510
        if (totalSizeToSend > 510) {
            val sendingSizePerPacket = 510
            var packets = totalSizeToSend / sendingSizePerPacket
            val lastByteSizeToSend = totalSizeToSend % sendingSizePerPacket
            if (lastByteSizeToSend != 0) {
                packets++
            }
            for (i in 0 until packets) {
                val pnoNumber = i + 1
                if (i == packets - 1 && lastByteSizeToSend != 0) {
                    endingByteIndex -= sendingSizePerPacket
                    endingByteIndex += lastByteSizeToSend
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    mapDataToWriteOTA[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteOTA[pnoNumber] = dataToWrite
                }
            }
            mapDataToWriteOTA.forEach { (i, s) ->
                Log.e(TAG, "Key $i Value ${s.size}")
            }
        }
        percentage = 0
        mutableLiveData.postValue(0)
        totalSize = mapDataToWriteOTA.size.div(100)
    }


    private fun onNotificationReceived(bytes: ByteArray, macAddress: String) {
        val message = String(bytes, StandardCharsets.UTF_8)
        println("Change: message $message")
        Log.e("PrinceEWW>>>", "BleService - onNotificationReceived: ${String(bytes, StandardCharsets.UTF_8)}")

        // *** NEW: intercept LOGFILE= and LISTLOG=COMPLETE before the .txt early-return ***
        if (message.startsWith("LOGFILE=") ||
            message == "LISTLOG=COMPLETE" ||
            message == "LISTLOGS=COMPLETE" ||
            message == "LISTLOGS=ERROR"
        ) {
            val intent = Intent(Constants.EVENT_BLE_COMMUNICATION)
            intent.putExtra(Constants.EVENT_BLE_ACTION, Constants.EVENT_BLE_NOTIFICATION)
            intent.putExtra(Constants.MAC_ADDRESS, macAddress)
            intent.putExtra(Constants.EVENT_MESSAGE, message)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            return
        }
        // *** END NEW ***

        if (message.equals(Constants.LISTEND, true)) {
            totalSize = dateList.size.div(100)
            deviceLogHashmap[macAddress]!!.add = false
            deviceLogHashmap[macAddress]!!.dateIndex = 0
            fetchLogData(macAddress)
            return
        }

        // FIX 2: Only apply the legacy .txt filter when we are NOT in a READLOG transfer.
        // Previously any packet whose content happened to contain ".txt" was eaten here,
        // which could corrupt READLOG content mid-stream.
        if (message.contains(".txt", true) && !message.startsWith("READLOG=")) {
            if (deviceLogHashmap[macAddress] != null && deviceLogHashmap[macAddress]!!.add) {
                if (!message.contains("_") || DateTimeHelper.getFormatedDate(
                        message.replace(".txt", "")
                    ).isEmpty()
                )
                    return
                val checkValue = message.split("_")
                if (checkValue.size < 2 && checkValue[0].toInt() > 31 && checkValue[1].toInt() > 12)
                    return
                var found = false
                run breaking@{
                    dateList.forEachIndexed { index, s ->
                        if (s.isNotEmpty() && DateTimeHelper.getFormatedDate(
                                message.replace(".txt", "")
                            ).contains(s) &&
                            !DateTimeHelper.getFormatedDate(
                                message.replace(".txt", "")
                            ).contains(
                                DateTimeHelper.getDate(
                                    System.currentTimeMillis(),
                                    Constants.DATE_FORMAT
                                )!!
                            )
                        ) {
                            found = true
                            return@breaking
                        }
                    }
                }
                if (found) return
                val deviceLogItem = deviceLogHashmap[macAddress]!!
                deviceLogItem.singleDaysHashData[deviceLogItem.dateIndex] =
                    DayLogData(message, HashMap(), 0)
                deviceLogItem.dateIndex++
            }
            return
        }

        if (message == Constants.LOGIDLE || message == Constants.LOGBUSY) {
            val intent = Intent(Constants.EVENT_LOG)
            intent.putExtra(Constants.EVENT_LOG_ACTION, message)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            return
        }

        if (message.contains(Constants.DEVICE_NAME_CHECK, false)) {
            val intent = Intent(Constants.CHANGE_DEVICE_NAME)
            intent.putExtra(Constants.EVENT_MESSAGE, message)
            intent.putExtra(Constants.MAC_ADDRESS, macAddress)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            return
        }

        if (message.contains(Constants.DATETIME, false)) {
            Log.e("PrinceEWW>>>", "DATETIME LOG - BLEService onRecevied: $message")
            val intent = Intent(Constants.SET_DEVICE_RTCTIME)
            intent.putExtra(Constants.EVENT_MESSAGE, message)
            intent.putExtra(Constants.MAC_ADDRESS, macAddress)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            return
        }

        if (message.lowercase() == Constants.ACK) {
            // FIX 3: Only run file-transfer ACK logic if we have an active transfer.
            // Otherwise fall through and broadcast ACK to LogViewerActivity too.
            if (deviceHashmap.contains(macAddress)) {
                val deviceItem: DeviceData = deviceHashmap[macAddress]!!
                if (deviceItem.isSendingFileType == Constants.JSON_FILE) {
                    deviceItem.acknowledgementIndex++
                    sendNextData(deviceItem, macAddress)
                    val retryHandler = transferRetryHandlers.getOrPut(macAddress) { Handler(Looper.getMainLooper()) }
                    retryHandler.removeCallbacksAndMessages(null)
                    val lastDataSent = deviceItem.acknowledgementIndex
                    retryHandler.postDelayed({
                        if (lastDataSent == deviceItem.acknowledgementIndex && lastDataSent != deviceItem.mapDataToWrite.size + 2) {
                            Log.e(TAG, "doSendMessageBroadcast: Resend json mac=$macAddress")
                            sendNextData(deviceItem, macAddress)
                        }
                    }, 3000)
                } else if (deviceItem.isSendingFileType == Constants.BIN_FILE) {
                    deviceItem.acknowledgementIndex++
                    sendBinData(deviceItem, macAddress)
                    val retryHandler = transferRetryHandlers.getOrPut(macAddress) { Handler(Looper.getMainLooper()) }
                    retryHandler.removeCallbacksAndMessages(null)
                    val lastDataSent = deviceItem.acknowledgementIndex
                    retryHandler.postDelayed({
                        Log.e(TAG, "doSendMessageBroadcast: Resend isBinFile$lastDataSent")
                        Log.e(TAG, "doSendMessageBroadcast: Resend isBinFile${deviceItem.mapDataToWriteOTA.size + 1}")
                        if (lastDataSent == deviceItem.acknowledgementIndex && lastDataSent != deviceItem.mapDataToWriteOTA.size + 1) {
                            Log.e(TAG, "doSendMessageBroadcast: Resend bin mac=$macAddress")
                            sendBinData(deviceItem, macAddress)
                        }
                    }, 10000)
                }
            }
            // fall through → always broadcast ACK so LogViewerActivity receives it
        }

        val intent = Intent(Constants.EVENT_BLE_COMMUNICATION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, Constants.EVENT_BLE_NOTIFICATION)
        intent.putExtra(Constants.MAC_ADDRESS, macAddress)
        if (!TextUtils.isEmpty(message)) {
            intent.putExtra(Constants.EVENT_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        println("Change:End $macAddress")
    }
    private fun fetchLogData(macAddress: String) {
        val item = deviceLogHashmap[macAddress]
        if (item != null && item.singleDaysHashData.isNotEmpty() && item.singleDaysHashData.size > item.dateIndex) {
            if (item.singleDaysHashData[item.dateIndex] != null && item.singleDaysHashData[item.dateIndex]!!.date.contains(
                    "_"
                )
            ) {
                val dayLogHashMap = item.singleDaysHashData[item.dateIndex]!!
                val checkValue = dayLogHashMap.date.split("_")
                if (checkValue.size >= 2 && checkValue[0].toInt() <= 31 && checkValue[1].toInt() <= 12) {
                    DebugLog.e("Readlogfile=${dayLogHashMap.date}")
                    writeData(
                        macAddress, "Readlogfile=${dayLogHashMap.date}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    deviceLogHashmap[macAddress]!!.dateIndex++
                    fetchLogData(macAddress)
                }
            } else {
                deviceLogHashmap[macAddress]!!.dateIndex++
                fetchLogData(macAddress)
            }
        } else {
            if (dateList.isEmpty()) {
                val intent = Intent(Constants.FILE_RECEIVE_SUCCESS)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }

    private fun establishNotifyObserver(
        int: BluetoothGattCharacteristic, macAddress: String, b: Boolean
    ) {
        val connectionObservable = hashmapBleConnection[macAddress]
        val disposable = connectionObservable!!.flatMap { rxBleConnection ->
            println("Notifications  ${int.uuid}")
            println("Notifications uuidForWrite ${UUID.fromString(uuidForWrite)}")
            println("Notifications uuidForFileTransfer ${UUID.fromString(uuidForFileTransfer)}")
//            rxBleConnection.setupNotification(UUID.fromString(uuidForWrite))
            rxBleConnection.setupNotification(UUID.fromString(if (b) uuidForFileTransfer else uuidForWrite))
        }.doOnNext { _ ->
            println("Notifications has been set up $int")
        }.flatMap { notificationObservable ->
            notificationObservable
        }.subscribeOn(Schedulers.io()).subscribe({
            DebugLog.e("subscribe 1 size=${it.size} lastByte=${it.lastOrNull()}")
            // FIX 1: Always forward every packet to onNotificationReceived.
            // Previously packets of size==480 or ending with '>' (62) were silently
            // dropped here, which swallowed every READLOG content chunk.
            onNotificationReceived(it, macAddress)
        }, {
            Log.e(TAG, "establishNotifyObserver: OnError ${it.localizedMessage}")
        }, {
            Log.e(TAG, "establishNotifyObserver: OnFINISH")
        })
    }

    private fun establishNotifyObserver(
        int: BluetoothGattCharacteristic, macAddress: String
    ) {
        val connectionObservable = hashmapBleConnection[macAddress]
        val disposable = connectionObservable!!.flatMap { rxBleConnection ->
            println("setupNotification  ${int.uuid}")
            rxBleConnection.setupNotification(int.uuid)
        }.doOnNext { _ ->
            println("Notifications has been set up $int")
        }.flatMap { notificationObservable ->
            notificationObservable
        }.subscribeOn(Schedulers.io())/*observeOn(AndroidSchedulers.mainThread())*/.subscribe({
            if (it.isEmpty()) return@subscribe
            if (it.size == 480 || it[it.size - 1] == 62.toByte()) {
                val item =
                    deviceLogHashmap[macAddress]!!.singleDaysHashData[deviceLogHashmap[macAddress]!!.dateIndex]
                if (it[it.size - 1] == 62.toByte()) {
                    item!!.dateFile[item.dateFilesRowDataIndex] = it
                    deviceLogHashmap[macAddress]!!.dateIndex++
                    fetchLogData(macAddress)
                    Thread {
                        if (item != null) {
                            insertIntoDatabase(item, macAddress)
                        }
                    }.start()
                } else {
//                    if (item!!.index == 0) {
//                        val origin = it
//                        val new = origin.copyOfRange(1, origin.size)
//                        item!!.logHashmap[item.index] = new
//                        item.index++
//                    } else {
                    item!!.dateFile[item.dateFilesRowDataIndex] = it
                    item.dateFilesRowDataIndex++
//                    }
                }
            } else {
                onNotificationReceived(it, macAddress)
            }
        }, {
            Log.e(TAG, "establishNotifyObserver: OnError ${it.localizedMessage}")
        }, {
            Log.e(TAG, "establishNotifyObserver: OnFINISH")
        })
        hashmapDisposable[macAddress]?.add(disposable)
    }

    private fun insertIntoDatabase(item: DayLogData, macAddress: String) {
        Executors.newSingleThreadExecutor().execute {
            val date = DateTimeHelper.getFormatedDate(item.date.replace(".txt", ""))
            if (dateList.isNotEmpty() && date == DateTimeHelper.getDate(
                    System.currentTimeMillis(),
                    Constants.DATE_FORMAT
                )!!
            ) {
                OnToCookApplication.dbInstance.logDao()
                    .delete(
                        macAddress, date
                    ).apply {
                        val string: StringBuilder = StringBuilder()
                        DebugLog.e("insertIntoDatabaseDatelogHashmap ${item.dateFile.size}")
                        item.dateFile.forEach { string.append(String(it.value, Charsets.UTF_8)) }
                            .apply {
                                val sizeTwo = string.split("\n")
                                var firstDeviceTimeItem: List<String> = emptyList()
                                var deviceOnTime = 0
                                var deviceOffTime = 0
                                var indFirstStartTime = 0
                                var magOldTime = 0
                                sizeTwo.forEach {
                                    val strLogItem = it.split(",").toMutableList()
                                    if (strLogItem.size == Constants.LogSizeField) {
                                        strLogItem[0] = strLogItem[0].replace(".", "")
                                        if (firstDeviceTimeItem.isNotEmpty())
                                            firstDeviceTimeItem[0].replace(".", "")
                                        if (firstDeviceTimeItem.isEmpty() || firstDeviceTimeItem[2] != strLogItem[2]) {
                                            if (firstDeviceTimeItem.isNotEmpty())
                                                deviceOffTime = getDeviceTime(
                                                    strLogItem[0], firstDeviceTimeItem[0]
                                                ) - deviceOnTime
                                            firstDeviceTimeItem = strLogItem
                                        }
                                        deviceOnTime = getDeviceTime(
                                            strLogItem[0], firstDeviceTimeItem[0]
                                        )
                                        if (indFirstStartTime == 0 && strLogItem[14] == "1") {
                                            indFirstStartTime = try {
                                                strLogItem[0].toInt()
                                            } catch (e: Exception) {
                                                0
                                            }
                                        } else if (strLogItem[14] == "0") {
                                            indFirstStartTime = 0
                                        }
                                        var indTime = indFirstStartTime
                                        if (indFirstStartTime != 0)
                                            indTime = getDeviceTime(
                                                strLogItem[0], indFirstStartTime.toString()
                                            )
                                        if (magOldTime == 0 && strLogItem[13] == "1") {
                                            magOldTime = try {
                                                strLogItem[0].toInt()
                                            } catch (e: Exception) {
                                                0
                                            }
                                        } else if (strLogItem[13] == "0") {
                                            magOldTime = 0
                                        }
                                        var magTime = magOldTime

                                        if (magOldTime != 0)
                                            magTime = getDeviceTime(
                                                strLogItem[0], magOldTime.toString()
                                            )
                                        val log = LogDataDb(
                                            deviceOnCounter = strLogItem[2].toIntOrNull() ?: 0,
                                            date = date,
                                            macAddress = macAddress,
                                            utcTime = strLogItem[0],
                                            igbtTemp = Constants.getTemp(strLogItem[3]),
                                            glassTemp = Constants.getFloat(strLogItem[4]),
                                            deviceOnCounterTime = getDate(
                                                strLogItem[0], "HH:mm:ss"
                                            ),/*change*/
                                            power = strLogItem[8],/*calculate*/
                                            indCurrent = Constants.getFloat(
                                                Constants.getPower(
                                                    strLogItem[7]
                                                )
                                            ),/*calculate*/
                                            magCurrent = Constants.getFloat(
                                                strLogItem[6]
                                            ),/*calculate*/
                                            magTemp = Constants.getFloat(strLogItem[5]),/*Done*/
                                            coilTemp = Constants.getFloat(strLogItem[9]),/*Done*/
                                            ambientTemp = Constants.getFloat(strLogItem[6]),
                                            panTemp = Constants.getFloat(strLogItem[10]),/*Done*/
                                            pcbTemp = Constants.getFloat(strLogItem[11]),/*Done*/
                                            oilTemp = Constants.getFloat(strLogItem[9]),
                                            deviceOnTime = deviceOnTime,/*Done*/
                                            deviceOffTime = deviceOffTime,/*Done*/
                                            indTime = indTime,
                                            magTime = magTime,
                                            recipeName = strLogItem[15],/*Done*/
                                            stepNo = strLogItem[16],/*Done*/
                                            timeRemains = strLogItem[strLogItem.size - 1],/*Done*/
                                            stepName = strLogItem[17],
                                            totalSteps = strLogItem[17],
                                            recipeTime = strLogItem[17],
                                            elapsedTime = strLogItem[17]
                                        )
                                        DebugLog.e("magCurrent ${log.magCurrent}")
                                        OnToCookApplication.dbInstance.logDao()
                                            .insert(
                                                log
                                            )
                                    }
                                }
                                val intent = Intent(Constants.FILE_RECEIVE_SUCCESS)
                                LocalBroadcastManager.getInstance(this@BleService)
                                    .sendBroadcast(intent)
                            }
                    }
            } else {
                val string: StringBuilder = StringBuilder()
                item.dateFile.forEach {
                    string.append(String(it.value, Charsets.UTF_8))
                }
                val sizeTwo = string.split("\n")
                var firstDeviceTimeItem: List<String> = emptyList()
                var deviceOnTime = 0
                var deviceOffTime = 0
                var indFirstStartTime = 0
                var magOldTime = 0
                sizeTwo.forEach {
                    val strLogItem = it.split(",").toMutableList()
                    if (strLogItem.size == Constants.LogSizeField) {
                        strLogItem[0] = strLogItem[0].replace(".", "")
                        if (firstDeviceTimeItem.isNotEmpty())
                            firstDeviceTimeItem[0].replace(".", "")
                        if (firstDeviceTimeItem.isEmpty() || firstDeviceTimeItem[2] != strLogItem[2]) {
                            if (firstDeviceTimeItem.isNotEmpty())
                                deviceOffTime = getDeviceTime(
                                    strLogItem[0], firstDeviceTimeItem[0]
                                ) - deviceOnTime
                            firstDeviceTimeItem = strLogItem
                        }
                        deviceOnTime = getDeviceTime(
                            strLogItem[0], firstDeviceTimeItem[0]
                        )
                        if (indFirstStartTime == 0 && strLogItem[14] == "1") {
                            indFirstStartTime = try {
                                strLogItem[0].toInt()
                            } catch (e: Exception) {
                                0
                            }
                        } else if (strLogItem[14] == "0") {
                            indFirstStartTime = 0
                        }
                        var indTime = indFirstStartTime
                        if (indFirstStartTime != 0)
                            indTime = getDeviceTime(
                                strLogItem[0], indFirstStartTime.toString()
                            )
                        if (magOldTime == 0 && strLogItem[13] == "1") {
                            magOldTime = try {
                                strLogItem[0].toInt()
                            } catch (e: Exception) {
                                0
                            }
                        } else if (strLogItem[13] == "0") {
                            magOldTime = 0
                        }
                        var magTime = magOldTime

                        if (magOldTime != 0)
                            magTime = getDeviceTime(
                                strLogItem[0], magOldTime.toString()
                            )
                        val log = LogDataDb(
                            deviceOnCounter = strLogItem[2].toIntOrNull() ?: 0,
                            date = date,
                            macAddress = macAddress,
                            utcTime = strLogItem[0],
                            igbtTemp = Constants.getFloat(strLogItem[3]),
                            glassTemp = Constants.getFloat(strLogItem[4]),
                            deviceOnCounterTime = getDate(
                                strLogItem[0], "HH:mm:ss"
                            ),/*change*/
                            power = strLogItem[8],/*calculate*/
                            indCurrent = Constants.getFloat(Constants.getPower(strLogItem[7])),/*calculate*/
                            magCurrent = Constants.getFloat(strLogItem[6]),/*calculate*/
                            magTemp = Constants.getFloat(strLogItem[5]),/*Done*/
                            coilTemp = Constants.getFloat(strLogItem[9]),/*Done*/
                            ambientTemp = Constants.getFloat(strLogItem[6]),
                            panTemp = Constants.getFloat(strLogItem[10]),/*Done*/
                            pcbTemp = Constants.getFloat(strLogItem[11]),/*Done*/
                            oilTemp = Constants.getFloat(strLogItem[9]),
                            deviceOnTime = deviceOnTime,/*Done*/
                            deviceOffTime = deviceOffTime,/*Done*/
                            indTime = indTime,
                            magTime = magTime,
                            recipeName = strLogItem[15],/*Done*/
                            stepNo = strLogItem[16],/*Done*/
                            timeRemains = strLogItem[strLogItem.size - 1],/*Done*/
                            stepName = strLogItem[17],
                            totalSteps = strLogItem[17],
                            recipeTime = strLogItem[17],
                            elapsedTime = strLogItem[17]
                        )
                        OnToCookApplication.dbInstance.logDao()
                            .insert(
                                log
                            )

                    }
                }
            }
        }
//        Executors.newSingleThreadExecutor().execute {
//            list.reverse()
//            OnToCookApplication.dbInstance.logDao()
//                .insert(LogDataDb(item.date.replace(".txt", ""), Gson().toJson(list)))
//        }

    }

    fun getConnectedDeviceAddresses(): List<String> {
        val knownAddresses = LinkedHashSet<String>()
        knownAddresses.addAll(connectedAddressOrder)
        knownAddresses.addAll(hashmapBleConnection.keys)
        return knownAddresses.filter { address ->
            address.isNotEmpty() && isDeviceConnected(address)
        }
    }

    fun getConnectedDeviceName(macAddress: String): String {
        return connectedDeviceNames[macAddress]
            ?: try {
                OnToCookApplication.rxBleClient.getBleDevice(macAddress).name ?: macAddress.takeLast(5)
            } catch (e: Exception) {
                macAddress.takeLast(5)
            }
    }

    private fun markDeviceDisconnected(macAddress: String) {
        connectedAddressOrder.remove(macAddress)
    }

    fun isDeviceConnected(macAddress: String): Boolean {
        return if (macAddress.isNotEmpty() && macAddress != Constants.DummyMacAddress) {
            (OnToCookApplication.rxBleClient.getBleDevice(macAddress).connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) || (OnToCookApplication.rxBleClient.getBleDevice(
                macAddress
            ).connectionState == RxBleConnection.RxBleConnectionState.CONNECTING)
        } else false
    }

    fun writeData(macAddress: String, writeData: ByteArray) {
        Log.e("PrinceEWW>>>", "BleService - writeData: ${String(writeData, StandardCharsets.UTF_8)}")
        DebugLog.e("Writed Data")
        if (!isDeviceConnected(macAddress)) return
        val connectionObservable = hashmapBleConnection[macAddress]
        if (connectionObservable != null) {
            val disposable = connectionObservable.flatMap { rxBleConnection ->
                rxBleConnection.createNewLongWriteBuilder()
                    .setCharacteristicUuid(UUID.fromString(Constants.UUID_FOR_WRITE))
                    .setMaxBatchSize(512).setBytes(writeData).build()
            }.observeOn(AndroidSchedulers.mainThread())
                .subscribe({ bytes -> onWriteSuccess() }, this::onWriteFailure)
            hashmapDisposable[macAddress]?.add(disposable)
        } else {
            println("write error..")
        }
    }


    // Setting String InHashmap
    /* fun setFileDataInMap(str: String) {
         isSendJsonFile = true
         val size = str.toByteArray(
             Charsets.UTF_8
         ).size
         var start = 0
         var end = 500
         if (size > 500) {
             acknowledgementIndex = 0
             val data = str.toByteArray(Charsets.UTF_8)
             val sendingSize = 500
             var divide = size / sendingSize
             val divider = size % sendingSize
             if (divider != 0) {
                 divide++
             }
             for (i in 0 until divide) {
                 val pnoNumber = i + 1
                 if (i == divide - 1 && divider != 0) {
                     end -= sendingSize
                     end += divider
                     Log.e(TAG, "sendFileDataToDevice: $start")
                     Log.e(TAG, "sendFileDataToDevice: ${end - 1}")
                     val dataToWrite = data.copyOfRange(start, end)
                     Log.e(TAG, "setFileDataInMap: ${dataToWrite.size}" )
                     val s = String(dataToWrite, Charsets.UTF_8)
                     mapDataToWrite[pnoNumber] = s
                 } else {
                     val dataToWrite = data.copyOfRange(start, end)
                     start += sendingSize
                     end += sendingSize
                     Log.e(TAG, "setFileDataInMap: ${dataToWrite.size}" )
                     val s = String(dataToWrite, Charsets.UTF_8)
                     mapDataToWrite[pnoNumber] = s
                 }
             }
             Log.e(TAG, "sendFileDataToDevice: HashMap")
             mapDataToWrite.forEach { (i, s) ->
                 Log.e(TAG, "Key $i Value $s")
             }
         }
         acknowledgementIndex = 0
     }*/
    // Setting ByteArray InHashmap
    fun setJsonFileDataInMap(macAddress: String, str: String) {
        if (!deviceHashmap.contains(macAddress)) {
            deviceHashmap[macAddress] = DeviceData()
        }
        val deviceItem: DeviceData = deviceHashmap[macAddress]!!
        val mapDataToWrite = deviceItem.mapDataToWrite
        mapDataToWrite.clear()
        deviceItem.isSendingFileType = Constants.JSON_FILE
        deviceItem.acknowledgementIndex = 0

        val data = str.toByteArray(Charsets.UTF_8)
        val sendingSize = 500
        val packetCount = maxOf(1, (data.size + sendingSize - 1) / sendingSize)
        for (i in 0 until packetCount) {
            val start = i * sendingSize
            val end = minOf(start + sendingSize, data.size)
            val pnoNumber = i + 1
            val dataToWrite = data.copyOfRange(start, end)
            mapDataToWrite[pnoNumber] = String(dataToWrite, Charsets.UTF_8)
        }
        Log.e(TAG, "setJsonFileDataInMap: mac=$macAddress size=${data.size} packets=${mapDataToWrite.size}")
    }

    fun writeFileData(macAddress: String, writeData: ByteArray) {
        if (!isDeviceConnected(macAddress)) return
        val connectionObservable = hashmapBleConnection[macAddress]
        if (connectionObservable != null) {
            val disposable = connectionObservable.flatMap { rxBleConnection ->
                rxBleConnection.createNewLongWriteBuilder()
                    .setCharacteristicUuid(UUID.fromString(Constants.UUID_FOR_FileTransfer))
                    .setMaxBatchSize(512).setBytes(writeData).build()
            }.observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ -> onWriteSuccess() }, this::onWriteFailure)
            hashmapDisposable[macAddress]?.add(disposable)
        } else {
            println("write error..")
        }
    }

    private fun onWriteSuccess() {
        println("Write success")
//        doSendMessageBroadcast(Constants.EVENT_BLE_WRITE_SUCCESS, "")
    }

    private fun onWriteFailure(throwable: Throwable) {
        println("Write error: $throwable")
        doSendMessageBroadcast(Constants.EVENT_BLE_WRITE_FAIL, throwable.localizedMessage)
    }

    private fun sendNotification(messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name)).setContentText(messageBody)
            .setAutoCancel(true).setSound(defaultSoundUri).setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Channel human readable title", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    fun startTimer(macAddress: String, activity: Activity?) {
        if (!deviceHashmap.contains(macAddress)) {
            deviceHashmap[macAddress] = DeviceData()
        }
        val deviceItem: DeviceData = deviceHashmap[macAddress]!!
        Log.e(TAG, " Dashboard prepareNotifyTimer: ")
        deviceItem.timer?.cancel()
        deviceItem.timer = Timer()
        deviceItem.timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (Constants.IS_TABLET) {
                    val fragment = (activity as HomeTvActivity).getFragmentFromMac(
                        macAddress
                    ) ?: return
                    val navController = (fragment as DashboardFragment).navController
                    navController?.let { _ ->
                        navController.currentDestination?.let {
                            if (it.id == R.id.cookingFragment) {
                                (fragment.getCurrentFragment() as CookingFragment).performHandlerAction()
                            } else {
                                stopTimer(macAddress)
                            }
                        }
                    }
                } else {
                    /*val fragment = (activity as HomeActivity).viewPagerAdapter.getFragmentFromMac(
                        macAddress
                    ) ?: return*/
                    /*if (!isAdded) {
                        Log.e("Fragment", "Fragment is not added to an activity")
                        return
                    }*/
                    //-------PrinceEWW, to prevent null pointer exception for activity-------//
                    val homeActivity = activity as? HomeActivity
                    if (homeActivity == null) {
                        Log.e("Fragment", "Activity is not HomeActivity")
                        return
                    }
                    val fragment = activity.viewPagerAdapter.getFragmentFromMac(macAddress) ?: return

                    val navController = (fragment as DashboardFragment).navController
                    navController?.let { _ ->
                        navController.currentDestination?.let {
                            if (it.id == R.id.cookingFragment) {
                                (fragment.getCurrentFragment() as CookingFragment).performHandlerAction()
                            } else {
                                stopTimer(macAddress)
                            }
                        }
                    }

                }
//                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == DashboardActivity::class.java.simpleName) {
//                    (OnToCookApplication.instance.currentActivity as DashboardActivity).performHandlerAction()
//                } else {
//                    if (OnToCookApplication.instance.isDashboard && OnToCookApplication.instance.dashboardActivity != null) {
//                        OnToCookApplication.instance.dashboardActivity!!.stepProg++
//                    }
//                    Log.e(TAG, "run: Else")
//                    Constants.remainSecond -= 1
//                    Log.e(TAG, "run:Remain CheckTimer55 ${Constants.remainSecond}")
//                    val min = (Constants.remainSecond % 3600) / 60
//                    val second = Constants.remainSecond % 60
//                    notificationBuilder.setContentText(String.format("%02d:%02d", min, second))
//                        .setPriority(NotificationManager.IMPORTANCE_MAX)
//                        .setDefaults(Notification.DEFAULT_ALL)
//                    val stackBuilder = TaskStackBuilder.create(applicationContext)
//                    val intentMain = Intent(applicationContext, MainActivity::class.java)
//                    stackBuilder.addNextIntent(intentMain)
//
//                    //                    stackBuilder.addParentStack(MainActivity::class.java)
//                    val intentDetails = Intent(applicationContext, CookingActivity::class.java)
//                    stackBuilder.addNextIntent(intentDetails)
//                    val activityActionIntent =
//                        Intent(application, DashboardActivity::class.java).apply {
//                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//                        }
//                    activityActionIntent.putExtra("recipe", recipeItemSend)
//                    activityActionIntent.putExtra("isResume", true)
//                    activityActionIntent.putExtra("incValue", incStepsAfterResume)
//                    activityActionIntent.putExtra("remainSec", Constants.remainSecond)
//                    activityActionIntent.putExtra("isPrepareRunning", Constants.isPrepareRunning)
//                    activityActionIntent.putExtra("prepreparestep", Constants.currentPrepareStep)
//                    activityActionIntent.putExtra("currentStep", Constants.currentStep)
//                    activityActionIntent.putExtra("isPlaying", Constants.isPlayStop)
//
//                    if (Constants.isChangeInRecipe) {
//                        activityActionIntent.putExtra(
//                            "recipeList", Gson().toJson(Constants.recipeStepList)
//                        )
//                    }
//
//                    Log.e(TAG, "doSendMessageBroadcast: ${Constants.remainSecond}")
//                    Log.e(TAG, "Constants.currentPrepareStep: ${Constants.currentPrepareStep}")
//                    Log.e(
//                        TAG, "doSendMessageBroadcast:prepreparestep ${Constants.currentPrepareStep}"
//                    )
//                    Log.e(
//                        TAG, "doSendMessageBroadcast:isPrepareRunning ${Constants.isPrepareRunning}"
//                    )
//                    stackBuilder.addNextIntent(activityActionIntent)
//
//                    val activityActionPendingIntent: PendingIntent? = stackBuilder.getPendingIntent(
//                        0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//                    )
//
//                    notificationBuilder.setContentIntent(activityActionPendingIntent)
//                    manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
//
//                    if (Constants.remainSecond == 0) {
//                        scheduleTimer?.cancel()
//                    }
//                }
            }
        }, 0, 1000)
    }

    fun stopTimer(macAddress: String) {
        if (deviceHashmap.contains(macAddress)) {
            val deviceItem: DeviceData = deviceHashmap[macAddress]!!
            deviceItem.timer?.cancel()
        }
    }

    fun notifyContent(toString: String) {

    }

    fun clearLogList(macAddress: String) {
        dateList.clear()
        OnToCookApplication.dbInstance.logDao().getDate(macAddress).subscribe {
            DebugLog.e("dateList ${Gson().toJson(it)}")
            dateList.addAll(it)
        }
        writeData(
            macAddress, Constants.LOGDATA.toByteArray(
                Charsets.UTF_8
            )
        )
        if (deviceLogHashmap[macAddress] == null) {
            deviceLogHashmap[macAddress] = DeviceLogData(
                ArrayList(), true, HashMap(), 0, ArrayList(), false/*,0*/
            )
            return
        }
        deviceLogHashmap[macAddress]!!.dateIndex = 0
        deviceLogHashmap[macAddress]!!.logDataList.clear()
        deviceLogHashmap[macAddress]!!.singleDaysHashData.clear()
        deviceLogHashmap[macAddress]!!.logSampleDataList.clear()
        deviceLogHashmap[macAddress]!!.add = true

    }

}