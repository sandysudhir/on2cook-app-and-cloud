package com.invent.ontocook.services

import android.R
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.invent.ontocook.CookingActivity
import com.invent.ontocook.DashboardActivity
import com.invent.ontocook.MainActivity
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.RecipeSteps
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.FINISH
import com.invent.ontocook.utils.Constants.INDQUICKSTART
import com.invent.ontocook.utils.Constants.INDQUICKSTOP
import com.invent.ontocook.utils.Constants.isPlayStop
import com.invent.ontocook.utils.SharedPreferencesManager
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit


class BLEBoundService : Service(), QueueReleaseInterface {
    private val TAG: String = this::class.java.simpleName
    private val binder = LocalBinder()
    var uuidForFileTransfer = ""
    var uuidForWrite = ""
    var macAddress = ""
    var bleDevice: RxBleDevice? = null
    private var connectionObservable: Observable<RxBleConnection>? = null
    private val disconnectTriggerSubject = PublishSubject.create<Boolean>()
    var compositeDisposable = CompositeDisposable()
    var isTerminateService: Boolean = false
    val mutableLiveData = MutableLiveData<Int>()
    private var acknowledgementIndex: Int = 0     // 0 Initialise
    private var audioFileIndex: Int = 0     // 0 Initialise
    var isSendJsonFile = false
    var isBinFile = false
    var isSending = false  // is Sending In Process
    val mapDataToWrite: HashMap<Int, String> = HashMap()      // Hashmap of Json File
    val mapDataToWriteFile: HashMap<Int, ByteArray> = HashMap()      // Hashmap of Audio File
    val handler = Handler(Looper.getMainLooper())   // Handler For Resend
    val handlerForInit = Handler(Looper.getMainLooper())   // Handler For Resend
    val handlerForFirstTime = Handler(Looper.getMainLooper())   // Handler For Resend
    var isAudioSending = false
    var haveToSendData: ByteArray? = null
    val audioFileNameList: ArrayList<String> = ArrayList()
    var incStepsAfterResume = 0
    var isPrepareRunning = true
    var totalSize = 0
    var percentage = 0
    var startByteIndex = 0
    var endingByteIndex = 450

    inner class LocalBinder : Binder() {
        fun getService(): BLEBoundService = this@BLEBoundService
    }

    var manager: NotificationManager? = null
    var NOTIFICATION_CHANNEL_ID = ""
    var NOTIFICATION_CHANNEL_ID_HIGH = ""

    var scanDisposable: Disposable? = null
    private var indStr = ""
    private var magStr = ""
    lateinit var notificationBuilder: NotificationCompat.Builder
    override fun onCreate() {
        println("on create ${Thread.currentThread().name}")
        NOTIFICATION_CHANNEL_ID = packageName
        NOTIFICATION_CHANNEL_ID_HIGH = "2$packageName"
        val channelName = "My Background Service"
        val chan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val chan2 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID_HIGH, channelName, NotificationManager.IMPORTANCE_HIGH
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        chan2.lightColor = Color.BLUE
        chan2.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        manager!!.createNotificationChannel(chan)
        manager!!.createNotificationChannel(chan2)
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification1 =
            notificationBuilder.setOngoing(true).setSmallIcon(R.drawable.sym_def_app_icon)
                .setContentTitle("On2Cook").setContentText("Running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE).build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification1)
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder {
        uuidForFileTransfer =
            SharedPreferencesManager.retriveData(applicationContext, "CharUUID") ?: ""
        uuidForWrite =
            SharedPreferencesManager.retriveData(applicationContext, "CharUUIDWrite") ?: ""
        macAddress = SharedPreferencesManager.retriveData(applicationContext, "MacAddress") ?: ""
//        macAddress = "F4:12:FA:DA:8A:ED"
        if (scanDisposable == null) {
            val scanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build()

            val scanFilter =
//                if (macAddress.isEmpty())
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(UUID.fromString(Constants.SERVICE_UUID))).build()
//                else ScanFilter.Builder()
//                    .setServiceUuid(ParcelUuid(UUID.fromString(Constants.
//                    SERVICE_UUID)))
//                    .setDeviceAddress(macAddress)
//                    .build()
            doSendBroadcast(Constants.EVENT_BLE_CONNECTION_INIT, "")
            scanDisposable =
                OnToCookApplication.rxBleClient.scanBleDevices(scanSettings, scanFilter)
                    .timeout(getTimeOut(), TimeUnit.MILLISECONDS).takeUntil {
//                        if (macAddress.isNotEmpty()) it.bleDevice.macAddress == macAddress
//                        else
                            it.scanRecord.serviceUuids?.contains(
                                ParcelUuid(
                                    UUID.fromString(
                                        Constants.SERVICE_UUID
                                    )
                                )
                            ) ?: false
                    }.observeOn(AndroidSchedulers.mainThread()).doOnError {
                        println("connection error 1 ${it.localizedMessage}")
                        onConnectionFailure(it)
                    }.doOnNext {
                        println("device found ${it?.bleDevice?.name} (${it?.bleDevice?.macAddress})")
                        bleDevice = it.bleDevice
                        macAddress = it.bleDevice.macAddress
                        SharedPreferencesManager.insertData(
                            applicationContext, "MacAddress", macAddress
                        )
                    }.subscribe({ it ->
                        it.bleDevice.macAddress

                        println("uuid   $uuidForFileTransfer   device ${bleDevice!!.macAddress}")
                        println("uuid   device ${isConnected()}")
                        doSendBroadcast(
                            Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE, bleDevice!!.name.toString()
                        )
                        println("uuid   device ${bleDevice?.connectionState}")
                        if (scanDisposable != null && !scanDisposable!!.isDisposed) {
                            scanDisposable!!.dispose()
                        }
                        if (bleDevice != null) bleDevice!!.observeConnectionStateChanges()
                            .subscribe({
                                when (bleDevice!!.connectionState) {
                                    RxBleConnection.RxBleConnectionState.CONNECTING -> {
                                        Log.e(TAG, "onBind:1 CONNECTING")
                                    }
                                    RxBleConnection.RxBleConnectionState.CONNECTED -> {
                                        Log.e(TAG, "onBind:2 CONNECTED")
                                        doSendBroadcast(
                                            Constants.EVENT_BLE_CONNECTION_SUCCESS,
                                            "Connection Successfully"
                                        )
                                    }
                                    RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
                                        Log.e(TAG, "onBind:3 DISCONNECTING")
                                        notifyResetRecipe()
                                    }
                                    RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                                        cancelNotifyTimer()
                                        notifyResetRecipe()
                                        disconnectTriggerSubject.onNext(true)
                                    }
                                }
                            }, {

                            }, {

                            })

                        if (bleDevice != null) when (bleDevice!!.connectionState) {
                            RxBleConnection.RxBleConnectionState.CONNECTING -> {
                                Log.e(TAG, "onBind: CONNECTING")
                            }
                            RxBleConnection.RxBleConnectionState.CONNECTED -> {
                                Log.e(TAG, "onBind: CONNECTED")
                                doSendBroadcast(
                                    Constants.EVENT_BLE_CONNECTION_SUCCESS,
                                    "Connection Successfully"
                                )
                            }
                            RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
                                Log.e(TAG, "onBind: DISCONNECTING")
                            }
                            RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                                Log.e(TAG, "onBind: DISCONNECTED")
                                establishConnection(it.bleDevice)
                            }
                        } else establishConnection(it.bleDevice)
                    }, {
                        Log.e(TAG, "establishNotifyObserver: OnError ${it.localizedMessage}")
                        //onConnectionFailure(it)
                    })
        } else {
            Log.e("scan", "already running")
        }
//        compositeDisposable.add(scanDisposable!!)
        return binder
    }

    private fun getTimeOut(): Long {
        return if (macAddress.isNotEmpty()) {
            120 * 100000
        } else 10 * 1000
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isTerminateService) {
            disconnectTriggerSubject.onNext(true)
            if (scanDisposable != null && !scanDisposable!!.isDisposed) {
                scanDisposable!!.dispose()
            }
            compositeDisposable.clear()
            return true
        }
        return super.onUnbind(intent)
    }

    private fun isConnected(): Boolean {
        return bleDevice!!.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED || bleDevice!!.connectionState == RxBleConnection.RxBleConnectionState.CONNECTING
    }

    private fun prepareConnectionObservable(): Observable<RxBleConnection>? {
        return bleDevice!!.establishConnection(true).takeUntil(disconnectTriggerSubject)
            .compose(ReplayingShare.instance())
    }

    var schedulTimer: Timer? = null
    var FOREGROUND_NOTIFICATION_ID = 1
    internal fun prepareNotifyTimer() {
        Log.e(TAG, " Dashboard prepareNotifyTimer: ")
        schedulTimer?.cancel()
        schedulTimer = Timer()
        schedulTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == DashboardActivity::class.java.simpleName) {
                    (OnToCookApplication.instance.currentActivity as DashboardActivity).performHandlerAction()
                } else {
                    if (OnToCookApplication.instance.isDashboard && OnToCookApplication.instance.dashboardActivity != null) {
                        OnToCookApplication.instance.dashboardActivity!!.stepProg++
                    }
                    Constants.remainSecond -= 1
                    Log.e(TAG, "run:Remain CheckTimer55 ${Constants.remainSecond}")
                    val min = (Constants.remainSecond % 3600) / 60
                    val second = Constants.remainSecond % 60
                    notificationBuilder.setContentText(String.format("%02d:%02d", min, second))
                        .setPriority(NotificationManager.IMPORTANCE_MAX)
                        .setDefaults(Notification.DEFAULT_ALL)
                    val stackBuilder = TaskStackBuilder.create(applicationContext)
                    val intentMain = Intent(applicationContext, MainActivity::class.java)
                    stackBuilder.addNextIntent(intentMain)
                    val intentDetails = Intent(applicationContext, CookingActivity::class.java)
                    stackBuilder.addNextIntent(intentDetails)
                    val activityActionIntent =
                        Intent(application, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    activityActionIntent.putExtra("recipe", recipeItemSend)
                    activityActionIntent.putExtra("isResume", true)
                    activityActionIntent.putExtra("incValue", incStepsAfterResume)
                    activityActionIntent.putExtra("remainSec", Constants.remainSecond)
                    activityActionIntent.putExtra("isPrepareRunning", Constants.isPrepareRunning)
                    activityActionIntent.putExtra("prepreparestep", Constants.currentPrepareStep)
                    activityActionIntent.putExtra("currentStep", Constants.currentStep)
                    activityActionIntent.putExtra("isPlaying", isPlayStop)

                    if (Constants.isChangeInRecipe) {
                        activityActionIntent.putExtra(
                            "recipeList", Gson().toJson(Constants.recipeStepList)
                        )
                    }
                    stackBuilder.addNextIntent(activityActionIntent)

                    val activityActionPendingIntent: PendingIntent? = stackBuilder.getPendingIntent(
                        0, PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
                    )

                    notificationBuilder.setContentIntent(activityActionPendingIntent)
                    manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())

                    if (Constants.remainSecond == 0) {
                        schedulTimer?.cancel()
                    }
                }
            }
        }, 0, 1000)
    }

    private var magTimer: CountDownTimer? = null
    private var indTimer: CountDownTimer? = null

    private fun startIndTimer(long: Long) {
        if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
            (OnToCookApplication.instance.currentActivity as CookingActivity).startInd()
        }
        indTimer?.cancel()
        indTimer = object : CountDownTimer(long, 1000) {
            override fun onTick(millis: Long) {
                val str = Constants.getFormattedTime(millis)
                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                    (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                        str
                    )
                }
                sendQuickStartNotification(indStr1 = str, isProrityHigh = false)
            }

            override fun onFinish() {
                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                    (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                        Constants.FINISH
                    )
                }
                sendQuickStartNotification(indStr1 = Constants.FINISH, isProrityHigh = true)
            }
        }.start()
    }

    private fun startMagTimer(long: Long) {
        if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
            (OnToCookApplication.instance.currentActivity as CookingActivity).startMag()
        }
        magTimer?.cancel()
        magTimer = object : CountDownTimer(long, 1000) {
            override fun onTick(millis: Long) {
                val str = Constants.getFormattedTime(millis)
                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                    (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                        str
                    )
                }
                sendQuickStartNotification(magStr1 = str, isProrityHigh = false)
            }

            override fun onFinish() {
                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                    (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                        Constants.FINISH
                    )
                }
                sendQuickStartNotification(magStr1 = Constants.FINISH, isProrityHigh = true)
            }
        }.start()
    }

    private fun sendQuickStartNotification(
        indStr1: String = "", magStr1: String = "", isProrityHigh: Boolean
    ) {
        manager?.cancelAll()
        notificationBuilder =
            if (isProrityHigh) NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_HIGH)
            else NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        notificationBuilder.setOngoing(true).setSmallIcon(R.drawable.sym_def_app_icon)
            .setPriority(if (isProrityHigh) NotificationManager.IMPORTANCE_MAX else NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE).build()
        val activityActionIntent = Intent(application, CookingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stackBuilder = TaskStackBuilder.create(this)
        val intentMain = Intent(this, MainActivity::class.java)
        stackBuilder.addNextIntent(intentMain)

//        activityActionIntent.putExtra("recipe", recipeItemSend)

        stackBuilder.addNextIntent(activityActionIntent)

        if (indStr1.isNotEmpty()) {
            indStr = indStr1
        }
        if (magStr1.isNotEmpty()) {
            magStr = magStr1
        }
        val stringBuilder = StringBuilder()
        if (indStr.isNotEmpty() && indStr != FINISH) {
            stringBuilder.append("Ind Time :- $indStr")
            if (magStr.isNotEmpty() && magStr != FINISH) {
                stringBuilder.append(System.getProperty("line.separator"));
                stringBuilder.append(" | Mag Time :- $magStr")
            } else {
                stringBuilder.append(System.getProperty("line.separator"));
                stringBuilder.append(" | Mag Stopped")
            }
        } else {
            if (indStr.isNotEmpty() && indStr == FINISH) {
                stringBuilder.append("Ind Stopped")
                if (magStr.isNotEmpty() && magStr != FINISH) {
                    stringBuilder.append(System.getProperty("line.separator"));
                    stringBuilder.append("| Mag Time :- $magStr")
                } else {
                    stringBuilder.append(System.getProperty("line.separator"));
                    stringBuilder.append("| Mag Stopped")
                }
            } else {
                if (magStr.isNotEmpty() && magStr != FINISH) {
                    stringBuilder.append("Mag Time :- $magStr")
                } else {
                    stringBuilder.append("Mag Stopped")
                }
            }

        }

        val activityActionPendingIntent: PendingIntent? =
            stackBuilder.getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)


        notificationBuilder.setContentTitle("Quick Start")
        notificationBuilder.setContentText(stringBuilder.toString())
        notificationBuilder.setContentIntent(activityActionPendingIntent)
        manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
    }

    internal fun cancelNotifyTimer() {
        Log.e(TAG, "cancelNotifyTimer:  Dashboard")
        schedulTimer?.cancel()
    }

    var recipeItemSend: RecentItem? = null
    fun initRecipe(recipeItem: RecentItem) {
        recipeItemSend = recipeItem
    }

    fun notifyContent(title: String) {
        notificationBuilder.priority = NotificationManager.IMPORTANCE_MIN
        sentNotificationWithData(title, false)
    }

    fun sendNotWithContent(title: String, content: String) {
        notificationBuilder.priority = NotificationManager.IMPORTANCE_MAX
        sentNotificationWithData("Prepare ", false, title)
    }

    fun notifyManualMode() {
        notificationBuilder.priority = NotificationManager.IMPORTANCE_MAX
        val title = "Manual Mode Started"
        val stackBuilder = TaskStackBuilder.create(this)

        val intentDetails = Intent(this, MainActivity::class.java)
        stackBuilder.addNextIntent(intentDetails)
        val activityActionPendingIntent: PendingIntent? =
            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)

        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentText("")

        notificationBuilder.setContentIntent(activityActionPendingIntent)
//        notificationBuilder.mActions.clear()
//        notificationBuilder.addAction(
//            R.drawable.sym_def_app_icon, title,
//            activityActionPendingIntent
//        )
        manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun notifyCompleteRecipe() {
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        notificationBuilder.setOngoing(true).setSmallIcon(R.drawable.sym_def_app_icon)
        notificationBuilder.priority = NotificationManager.IMPORTANCE_MAX
        val title = "Recipe Completed"
        val stackBuilder = TaskStackBuilder.create(this)
        val activityActionIntent = Intent(application, CookingActivity::class.java).apply {

        }
        stackBuilder.addParentStack(MainActivity::class.java)
        val intentDetails = Intent(this, MainActivity::class.java)
        stackBuilder.addNextIntent(intentDetails)
        stackBuilder.addNextIntent(activityActionIntent)
        val activityActionPendingIntent: PendingIntent? =
            stackBuilder.getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentText("")

        notificationBuilder.setContentIntent(activityActionPendingIntent)
//        notificationBuilder.addAction(
//            R.drawable.sym_def_app_icon, title,
//            activityActionPendingIntent
//        )
        manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
    }

    fun notifyResetRecipe() {
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.sym_def_app_icon)

        notificationBuilder.priority = NotificationManager.IMPORTANCE_MAX
        val title = "OnToCook"
        val stackBuilder = TaskStackBuilder.create(this)

        val intentDetails = Intent(this, MainActivity::class.java)
        stackBuilder.addNextIntent(intentDetails)
        val activityActionPendingIntent: PendingIntent? =
            stackBuilder.getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)

        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentText("Running In Background")

        notificationBuilder.setContentIntent(activityActionPendingIntent)
//        notificationBuilder.addAction(
//            R.drawable.sym_def_app_icon, title,
//            activityActionPendingIntent
//        )
        manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
    }

    fun sendFileDataToDevice(str: String) {
        isSendJsonFile = true
        val size = str.toByteArray(
            Charsets.UTF_8
        ).size
        var start = 0
        var end = 500
        if (size > 500) {
            acknowledgementIndex = 0
            val data = str.toByteArray(Charsets.UTF_8)
            var sendingSize = 500
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
                    val s = String(dataToWrite, Charsets.UTF_8)
                    mapDataToWrite[pnoNumber] = s
                } else {
                    val dataToWrite = data.copyOfRange(start, end)
                    start += sendingSize
                    end += sendingSize
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
    }

    fun sendBinFile(bytes: ByteArray) {
        isBinFile = true
        isSendJsonFile = false

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
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                }
            }
            mapDataToWriteFile.forEach { (i, s) ->
                Log.e(TAG, "Key $i Value ${s.size}")
            }
        }
        acknowledgementIndex = 0
        mutableLiveData.postValue(percentage)
        totalSize = mapDataToWriteFile.size.div(100)
    }


    internal fun sendAudioFileFromChoose(bytes: ByteArray, nameWithoutExtension: String) {
        audioFileNameList.clear()
        val totalSizeToSend = bytes.size
        var startByteIndex = 0
        var endingByteIndex = 450
        if (totalSizeToSend > 450) {
            val sendingSizePerPacket = 450
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
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                }
            }
        }
    }

    private fun establishConnection(bleDevice: RxBleDevice) {
        if (this.bleDevice != null && !isConnected()) {
            connectionObservable = prepareConnectionObservable()

            requestMtu()
            val connectionDisposable =
                connectionObservable!!.flatMapSingle(RxBleConnection::discoverServices)
                    .flatMapSingle {
                        if (uuidForFileTransfer == "") {
                            for (service in it.bluetoothGattServices) {
                                println("service uuid    ${service.uuid}    ${service.type}")
                                for (char in service.characteristics) {
                                    if (isCharacteristicNotifiable(char) && isCharacteristicWriteable(
                                            char
                                        )
                                    ) {
                                        if (char.uuid.toString() == Constants.UUID_FOR_WRITE) {
                                            SharedPreferencesManager.insertData(
                                                applicationContext,
                                                "CharUUIDWrite",
                                                char.uuid.toString()
                                            )
                                            uuidForWrite = char.uuid.toString()
                                        }
                                        SharedPreferencesManager.insertData(
                                            applicationContext, "CharUUID", char.uuid.toString()
                                        )
                                        uuidForFileTransfer = char.uuid.toString()
                                    }
                                    println("char   ${describeProperties(char)}  ${char.uuid}")
                                }
                            }
                            println("char  uuid ${uuidForFileTransfer}")
                            println("char  uuidForWrite ${uuidForWrite}")

                        }
                        it.getCharacteristic(UUID.fromString(uuidForWrite))
                    }.observeOn(AndroidSchedulers.mainThread()).doOnError {
                        println("connection error 2 ${it.localizedMessage}")
                        println("error   ${it.localizedMessage}")
                        onConnectionFailure(it)
                    }.doFinally {
                        println("connection completed...")
                    }.subscribe({
                        Log.e(TAG, "establishConnection: Finish Next")
                        onConnectionFinished()
                        establishNotifyObserver(it, true, bleDevice.macAddress)
                        establishNotifyObserver(it, false, bleDevice.macAddress)
                        Handler(Looper.getMainLooper()).postDelayed({
                            writeData(
                                "STATUS=?".toByteArray(
                                    Charsets.UTF_8
                                )
                            )
                        }, 100)
                    }, {
                        println("connection error 3 ${it.localizedMessage}")

                        onConnectionFailure(it)
                    }, {
                        Log.e(TAG, "establishConnection: Finish")
                        onConnectionFinished()
                    })
            compositeDisposable.add(connectionDisposable)
        }
    }

    private fun describeProperties(characteristic: BluetoothGattCharacteristic): String? {
        val properties: MutableList<String?> = ArrayList()
        if (isCharacteristicReadable(characteristic)) properties.add("Read")
        if (isCharacteristicWriteable(characteristic)) properties.add("Write")
        if (isCharacteristicNotifiable(characteristic)) properties.add("Notify")
        return TextUtils.join(" ", properties)
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

    private fun onConnectionFinished() {
        println("Connection Success")
        doSendBroadcast(Constants.EVENT_BLE_CONNECTION_SUCCESS, "Connection Successfully")
    }

    private fun onConnectionFailure(throwable: Throwable) {
        println("connection error  ${throwable.localizedMessage}")
        handleError(throwable)
    }

    private fun handleError(throwable: Throwable) {
        Log.e(TAG, "handleError: ${throwable.localizedMessage}")
        if (throwable.localizedMessage.lowercase().contains("disconnected from mac")) {
            doSendBroadcast(
                Constants.EVENT_BLE_CONNECTION_ABORT, throwable.localizedMessage!!
            )
            stopIndTimer(false)
            stopMagTimer(false)
            cancelNotifyTimer()
        } else {
            doSendBroadcast(Constants.EVENT_BLE_CONNECTION_ERROR, throwable.localizedMessage)
        }
    }

    fun readData() {
        connectionObservable!!.firstOrError().flatMap { rxBleConnection ->
            rxBleConnection.readCharacteristic(
                UUID.fromString(
                    uuidForFileTransfer
                )
            )
        }.observeOn(AndroidSchedulers.mainThread()).subscribe({ bytes ->
            println("String ${String(bytes, StandardCharsets.UTF_8)}")
        }, this::onReadFailure)
    }

    private fun requestMtu() {
        connectionObservable!!.firstOrError()
            .flatMap { rxBleConnection -> rxBleConnection.requestMtu(517) }
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ mtu ->
                println(mtu)
            }, this::onReadFailure)
    }

    private fun doSendBroadcast(
        action: String, message: String
    ) {
        var intent = Intent(Constants.EVENT_BLE_CONNECTION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, action)
        if (!TextUtils.isEmpty(message)) {
            intent.putExtra(Constants.EVENT_ERROR_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun doSendMessageBroadcast(
        action: String, message: String
    ) {
        val intent = Intent(Constants.EVENT_BLE_COMMUNICATION)
        intent.putExtra(Constants.EVENT_BLE_ACTION, action)
        if (!TextUtils.isEmpty(message)) {
            intent.putExtra(Constants.EVENT_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        isSending = false
        val updateUI =
            OnToCookApplication.instance.currentActivity == null || (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName != DashboardActivity::class.java.simpleName)
        // Reset Timer
        if (message.lowercase() == Constants.STOP || message.lowercase() == Constants.IDLE_DEVICE) {
            resetGlobal()
            cancelNotifyTimer()
            notifyResetRecipe()
        }
        if (message.lowercase() == Constants.MANUAL_MODE) {
            resetGlobal()
            notifyManualMode()
            cancelNotifyTimer()
        }
        if (message.uppercase() == "RECIPE=COMPLETE") {
//            resetGlobal()
            cancelNotifyTimer()
            notifyCompleteRecipe()
        }
        if (message.lowercase().contains("recipe=")) {
            val recipeName = message.split(",")[0].lowercase()
            val cmdSize: Int = message.split(",").size
            val mode = if (cmdSize > 1) message.split(",")[1].uppercase().replace("MODE=", "")
                .uppercase() else ""
            if (mode == Constants.REC_SEL_MODE || mode == Constants.INGREDIENT_MODE) {
                cancelNotifyTimer()
                if (mode == Constants.REC_SEL_MODE)
                    notifyResetRecipe()
            }
        }

        if (message.lowercase() == Constants.ACK) {
            Log.e(TAG, "doSendMessageBroadcast: ACK $message")
            Log.e(TAG, "doSendMessageBroadcast: ACK $isSendJsonFile")
            Log.e(TAG, "doSendMessageBroadcast: ACK $isBinFile")
            if (isSendJsonFile) {
                acknowledgementIndex++
                sendNextData()
                handler.removeCallbacksAndMessages(null)
                val lastDataSent = acknowledgementIndex
                handler.postDelayed({
                    if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWrite.size + 2) {
                        Log.e(TAG, "doSendMessageBroadcast: Resend")
                        sendNextData()
                    }
                }, 3000)
            } else if (isBinFile) {
                Log.e(TAG, "doSendMessageBroadcast: Resend isBinFile$acknowledgementIndex")
                acknowledgementIndex++
                sendBinData()
                handler.removeCallbacksAndMessages(null)
                val lastDataSent = acknowledgementIndex
                handler.postDelayed({
                    if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWriteFile.size + 2) {
                        Log.e(TAG, "doSendMessageBroadcast: Resend")
                        sendBinData()
                    }
                }, 3000)
            } else {
                if (haveToSendData != null) {
                    Log.e(TAG, "doSendMessageBroadcast: haveToSendData")
                    writeData(haveToSendData!!)
                } else {
                    Log.e(TAG, "doSendMessageBroadcast: else")
                    if (!isSending) {
                        Log.e(TAG, "doSendMessageBroadcast: else haveToSendData")
                        handlerForFirstTime.removeCallbacksAndMessages(null)
                        handlerForFirstTime.postDelayed({
                            acknowledgementIndex++
                            sendAudioData()
                            handler.removeCallbacksAndMessages(null)
                            val lastDataSent = acknowledgementIndex
                            handler.postDelayed({
                                if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWriteFile.size + 2) {
                                    Log.e(TAG, "doSendMessageBroadcast: Resend")
                                    sendAudioData()
                                }
                            }, 3000)
                        }, 10)
                    }
                }
            }
            Log.e("TAG", "onReceive: $message")
        }
        if (message.lowercase() == Constants.ACK_CANCEL) {
            Log.e(TAG, "doSendMessageBroadcast: ACK $message")
            isSending = false
            acknowledgementIndex = 0
        }
        if (message.lowercase() == Constants.ACK_COMMAND) {
            Log.e(TAG, "doSendMessageBroadcast: ACKCMD $message")
            haveToSendData = null
            isSending = false
            if (isAudioSending) {
                acknowledgementIndex++
                sendAudioData()
                handler.removeCallbacksAndMessages(null)
                val lastDataSent = acknowledgementIndex
                handler.postDelayed({
                    if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWriteFile.size + 2) {
                        Log.e(TAG, "doSendMessageBroadcast: Resend")
                        sendAudioData()
                    }
                }, 3000)
            }
            Log.e("TAG", "onReceive: $message")
        }

        if (message.uppercase() == Constants.MAGQUICKSTART) {
            magTimer?.cancel()
            startMagTimer(180000)
        }
        if (message.uppercase() == Constants.MAGQUICKSTOP) {
            stopMagTimer(true)
        }
        if (message.uppercase() == Constants.MAG_PAUSE) {
            magTimer?.cancel()
            if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                    Constants.PAUSE
                )
            }
        }
        if (message.uppercase() == Constants.IND_PAUSE) {
            indTimer?.cancel()
            if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                    Constants.PAUSE
                )
            }
        }

        if (message.uppercase().contains("AUDIO_NAME=")) {
            Log.e(TAG, "doSendMessageBroadcast: audio_name $message")
            isSendJsonFile = false
            isAudioSending = true
            audioFileNameList.clear()
            val audioNames = message.replace("audio_name=", "")
            if (audioNames.contains(",")) {
                audioFileNameList.addAll(audioNames.split(","))
            } else {
                audioFileNameList.add(audioNames)
            }

            sendAudioFile()

        }
        //For Update Recipe In Background
        if (message.lowercase().contains("magtime=")) {
            Log.e(TAG, "isDashboard: ")
            if (updateUI && OnToCookApplication.instance.isDashboard && OnToCookApplication.instance.dashboardActivity != null) {
                Log.e(TAG, "isDashboard: @")
                val magnetronOnTime =
                    message.lowercase().split(",")[0].replace("magtime=", "").toInt()
                val inductionOnTime =
                    message.lowercase().split(",")[1].replace("indtime=", "").toInt()
                OnToCookApplication.instance.dashboardActivity!!.updateOnlyRecipe(
                    Constants.currentStep - 1,
                    inductionOnTime.coerceAtLeast(magnetronOnTime),
                    inductionOnTime,
                    magnetronOnTime
                )
            }
        }

        if (message.uppercase().contains("INSTR_RUN=")) {
            Log.e(TAG, "doSendMessageBroadcast: ${OnToCookApplication.instance.currentActivity}")
            val status = message.uppercase().replace("INSTR_RUN=", "")
            updateStatus(status, updateUI)
        }
        if (message.uppercase().contains("INDPROCESSTIME=")) {
            indTimer?.cancel()
            val long = message.replace("INDPROCESSTIME=", "").toLong()
            Log.e("TAG", "onReceive: INDPROCESSTIME$long")
            startIndTimer(long * 1000)
        }
        if (message.uppercase().contains("MAGPROCESSTIME=")) {
            magTimer?.cancel()
            val long = message.replace("MAGPROCESSTIME=", "").toLong()
            Log.e("TAG", "onReceive: MAGPROCESSTIME$long")
            startMagTimer(long * 1000)
        }


        if (message.uppercase().contains("MAG_RUN=")) {
            if (message.contains(",")) {
                if (message.split(",")[0].uppercase().replace("MAG_RUN=", "") == "PAUSE") {
                    if (updateUI) cancelNotifyTimer()
                } else {
                    if (message.split(",")[0].uppercase()
                            .replace("MAG_RUN=", "") == "START"
                    ) if (updateUI) prepareNotifyTimer()
                }
            }
        }
        if (message.uppercase().contains("INDQUICKSTART=")) {
            if (message.contains(",")) {
                val messageSize = message.split(",").size
                if (messageSize > 0) {
                    val indStatus = message.uppercase().split(",")[0].replace(
                        "INDQUICKSTART=", ""
                    )
                    if (indStatus == Constants.IDLE) {
                        val indTime = message.uppercase().split(",")[1].replace(
                            "IND_RUN=", ""
                        ).toLong() * 1000
                        if (indTime.toInt() != 0) {
                            indStr = Constants.getFormattedTime(indTime)
                            if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                                (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                                    Constants.PAUSE
                                )
                                (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                                    indStr
                                )
                            }
                        } else stopIndTimer(false)
                        if (messageSize > 2) {
                            val magStatus = message.uppercase().split(",")[2].replace(
                                "MAGQUICKSTART=", ""
                            )
                            if (magStatus != Constants.IDLE) {
                                if (messageSize > 3) {
                                    val magTime = message.uppercase().split(",")[3].replace(
                                        "MAG_RUN=", ""
                                    )
                                    magTimer?.cancel()
                                    startMagTimer(magTime.toLong() * 1000)
                                }
                            } else {
                                Log.e(TAG, "doSendMessageBroadcast: CookingActivity0")
                                if (messageSize > 3) {
                                    Log.e(TAG, "doSendMessageBroadcast: CookingActivity1")
                                    val magTime = message.uppercase().split(",")[3].replace(
                                        "MAG_RUN=", ""
                                    ).toLong() * 1000
                                    if (magTime.toInt() != 0) {
                                        magStr = Constants.getFormattedTime(magTime)
                                        if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                                            Log.e(TAG, "doSendMessageBroadcast: CookingActivity3")
                                            (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                                                Constants.PAUSE
                                            )
                                            (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                                                magStr
                                            )
                                        }
                                    } else {
                                        stopMagTimer(false)
                                    }
                                }
                            }

                        }
                    } else {
                        if (messageSize > 1) {
                            val indTime = message.uppercase().split(",")[1].replace(
                                "IND_RUN=", ""
                            )
                            indTimer?.cancel()
                            startIndTimer(indTime.toLong() * 1000)
                            if (messageSize > 2) {
                                val magStatus = message.uppercase().split(",")[2].replace(
                                    "MAGQUICKSTART=", ""
                                )
                                if (messageSize > 3) {
                                    val magTime = message.uppercase().split(",")[3].replace(
                                        "MAG_RUN=", ""
                                    ).toLong() * 1000
                                    if (magStatus != Constants.IDLE) {
                                        if (messageSize > 3) {
                                            val magTime = message.uppercase().split(",")[3].replace(
                                                "MAG_RUN=", ""
                                            )
                                            magTimer?.cancel()
                                            startMagTimer(magTime.toLong() * 1000)
                                        }
                                    } else {
                                        if (magTime.toInt() != 0) {
                                            magStr = Constants.getFormattedTime(magTime)
                                            if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {

                                                (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                                                    Constants.PAUSE
                                                )
                                                (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                                                    magStr
                                                )
                                            }
                                        }
                                        stopMagTimer(false)
                                    }
                                }

                            }
                            if (messageSize > 4) {
                                val indPower = message.uppercase().split(",")[4].replace(
                                    "INDPOWER=", ""
                                )
//                                println(TAG,indPower)
                                if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
                                    (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndPower(
                                        indPower
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (message.uppercase() == INDQUICKSTART) {
                    indTimer?.cancel()
                    startIndTimer(180000)
                } else if (message.uppercase() == INDQUICKSTOP) {
                    indTimer?.cancel()
                    stopIndTimer(true)
                }
            }
        }


    }

    private fun stopIndTimer(sendNotification: Boolean) {
        indTimer?.cancel()

        if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
            (OnToCookApplication.instance.currentActivity as CookingActivity).updateIndTime(
                Constants.FINISH
            )
        }
        if (sendNotification) sendQuickStartNotification(
            indStr1 = Constants.FINISH, isProrityHigh = true
        )

    }

    private fun stopMagTimer(sendNotification: Boolean) {
        magTimer?.cancel()
        if (OnToCookApplication.instance.currentActivity != null && OnToCookApplication.instance.currentActivity!!.localClassName == CookingActivity::class.java.simpleName) {
            (OnToCookApplication.instance.currentActivity as CookingActivity).updateMagTime(
                Constants.FINISH
            )
        }
        if (sendNotification) sendQuickStartNotification(
            magStr1 = Constants.FINISH, isProrityHigh = true
        )

    }

    private fun resetGlobal() {
        Constants.remainSecond = -1
        isPlayStop = false
        Constants.isChangeInRecipe = false
        Constants.currentStep = 1
        Constants.nextDescription = ""
        Constants.isPrepareRunning = true
        Constants.currentPrepareStep = 0
    }

    private fun updateStatus(status: String, updateUI: Boolean) {
        Log.e(TAG, "updateUI:status $updateUI")

        if (status == Constants.START) {
//                incStepsAfterResume++
            isPlayStop = true
            if (updateUI) prepareNotifyTimer()
        } else if (status == Constants.COMPLETE) {
            Log.e(TAG, "doSendMessageBroadcast:cancelNotifyTimer $recipeItemSend")
            Log.e(
                TAG, "doSendMessageBroadcast:cancelNotifyTimer ${Constants.remainSecond}"
            )
            isPlayStop = false
            if (updateUI) {
                Constants.currentStep++
                cancelNotifyTimer()
                Log.e(TAG, "updateStatus: ${OnToCookApplication.instance.currentActivity == null}")
                Constants.remainSecond = Constants.setRound(Constants.remainSecond)
                notificationBuilder.priority = NotificationManager.IMPORTANCE_MAX
                notificationBuilder.setDefaults(Notification.DEFAULT_ALL)
                if (Constants.nextDescription == "")
                    sentNotificationWithData("Recipe Completed", true)
                else sentNotificationWithData(Constants.nextDescription, true)
            }
        }
    }

    private fun sentNotificationWithData(
        title: String, isProrityHigh: Boolean, content: String = ""
    ) {
        manager?.cancelAll()
        Log.e(TAG, "sentNotificationWithData: $title")
        notificationBuilder =
            if (isProrityHigh) NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_HIGH)
            else NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        notificationBuilder.setOngoing(true).setSmallIcon(R.drawable.sym_def_app_icon)
            .setPriority(if (isProrityHigh) NotificationManager.IMPORTANCE_MAX else NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE).build()
        val activityActionIntent = Intent(application, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stackBuilder = TaskStackBuilder.create(this)
        val intentMain = Intent(this, MainActivity::class.java)
        stackBuilder.addNextIntent(intentMain)

//        stackBuilder.addParentStack(MainActivity::class.java)
        val intentDetails = Intent(this, CookingActivity::class.java)
        stackBuilder.addNextIntent(intentDetails)
        activityActionIntent.putExtra("recipe", recipeItemSend)
        activityActionIntent.putExtra("isResume", true)
        activityActionIntent.putExtra("incValue", incStepsAfterResume)
        activityActionIntent.putExtra("remainSec", Constants.remainSecond)
        activityActionIntent.putExtra("isPrepareRunning", Constants.isPrepareRunning)
        activityActionIntent.putExtra("prepreparestep", Constants.currentPrepareStep)
        activityActionIntent.putExtra("currentStep", Constants.currentStep)
        activityActionIntent.putExtra("isPlaying", isPlayStop)
        if (Constants.isChangeInRecipe) {
            activityActionIntent.putExtra("recipeList", Gson().toJson(Constants.recipeStepList))
        }
        stackBuilder.addNextIntent(activityActionIntent)

        val activityActionPendingIntent: PendingIntent? =
            stackBuilder.getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)

        val min = (Constants.remainSecond % 3600) / 60
        val second = Constants.remainSecond % 60
        if (Constants.remainSecond < 0) {
            Constants.remainSecond = 0
        }
        if (Constants.remainSecond == 0) {
            notificationBuilder.setContentTitle(title)

        } else {
            notificationBuilder.setContentTitle(title)
//                .setContentText(String.format("%02d:%02d", min, second))
        }
        if (Constants.isPrepareRunning) {
            if (content.isNotEmpty()) {
                notificationBuilder.setContentText(content)
            }
        } else {
            notificationBuilder.setContentText(String.format("%02d:%02d", min, second))
        }
        notificationBuilder.setContentIntent(activityActionPendingIntent)
        manager?.notify(FOREGROUND_NOTIFICATION_ID, notificationBuilder.build())
        Log.e(TAG, "prepareTimer: CheckTimer22 ${Constants.remainSecond}")

    }

    private fun sendAudioFile() {
        if (audioFileNameList.size > audioFileIndex) {
            val audioNames = audioFileNameList[audioFileIndex]
            try {
                val file = assets.open("$audioNames.mp3")
                acknowledgementIndex = 0
                val size = file.available()
                handlerForInit.postDelayed({
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        "{\"AUDIO\":\"${audioNames}\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }, 50)
                val bytes = ByteArray(size)
                file.read(bytes)
                file.close()
                OnToCookApplication.instance.bleBoundService.prepareAudioFile(bytes)
            } catch (e: Exception) {
                val file = Constants.generateAudioFile(audioNames)
//                file.observeForever { file ->
//                    if (file != null) {
//                        acknowledgementIndex = 0
//                        Log.e(TAG, "writeFileData: observeForever $audioNames")
//
//                        val size = file.length().toInt()
//                        val bytes = ByteArray(size)
//                        try {
//                            val buf = BufferedInputStream(FileInputStream(file))
//                            buf.read(bytes, 0, bytes.size)
//                            val bytes2 = file.readBytes()
//                            Log.e(TAG, "onCreate: bytes2 ${bytes2.size}")
//                            buf.close()
//                            OnToCookApplication.instance.bleBoundService.prepareAudioFile(bytes2)
//                        } catch (e: FileNotFoundException) {
//                            e.printStackTrace()
//                        } catch (e: IOException) {
//                            e.printStackTrace()
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//                        handlerForInit.postDelayed({
//                            OnToCookApplication.instance.bleBoundService.writeFileData(
//                                "{\"AUDIO\":\"${audioNames}\",\"SIZE\":\"${size} \",\"SAVE\":\"1\"}".toByteArray(
//                                    Charsets.UTF_8
//                                )
//                            )
//                        }, 50)
//
//                    } else {
//                        e.printStackTrace()
//                        audioFileIndex++
//                        sendAudioFile()
//                    }
//
//                }
//                Log.e(TAG, "sendAudioFile: Catch $file")
                e.printStackTrace()
                audioFileIndex++
                sendAudioFile()

            }
        } else {
            isAudioSending = false
        }
    }

    private fun sendNextData() {
        Log.e(TAG, "sendNextData: $acknowledgementIndex")
        when (acknowledgementIndex) {
            mapDataToWrite.size + 1 -> {
                Log.e("TAG", "writeFileData :COMPLETE ")
                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
            mapDataToWrite.size + 2 -> {
                Log.e("TAG", "writeFileData :FILE_UPLOAD_SUCCESS ")
                doSendMessageBroadcast(Constants.FILE_UPLOAD_SUCCESS, "")
            }
            else -> {
                val pnoNumber = acknowledgementIndex
                if (mapDataToWrite.contains(pnoNumber)) {
                    val data = mapDataToWrite[pnoNumber]
                    Log.e("TAG", "onReceive: File PnoNumber $pnoNumber")

                    if (isBinFile) OnToCookApplication.instance.bleBoundService.writeFileData(
                        data!!.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else OnToCookApplication.instance.bleBoundService.writeFileData(
                        "PNO=${pnoNumber},DATA=${data}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            }
        }
    }

    private fun sendAudioData() {
        Log.e(TAG, "sendAudioData: $acknowledgementIndex")

        when (acknowledgementIndex) {
            mapDataToWriteFile.size + 1 -> {
                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
            mapDataToWriteFile.size + 2 -> {

//                Toast.makeText(
//                    this,
//                    "File uploaded successfully",
//                    Toast.LENGTH_SHORT
//                ).show()
                audioFileIndex++
//                LoadingUtils.hideDialog()
                sendAudioFile()
                Log.e(TAG, "sendAudioData: Complete$audioFileIndex")
            }
            else -> {
                if (isAudioSending) {
                    val pnoNumber = acknowledgementIndex
                    Log.e(TAG, "sendAudioData: $pnoNumber")
                    if (mapDataToWriteFile.contains(pnoNumber)) {
                        val data = mapDataToWriteFile[pnoNumber]
                        val firstBytes: ByteArray = "PNO=${pnoNumber},DATA=".toByteArray(
                            Charsets.UTF_8
                        )

                        Log.e(TAG, "sendAudioData: $firstBytes")

                        val dataToWrite = ByteArray(firstBytes.size + data!!.size)
                        System.arraycopy(firstBytes, 0, dataToWrite, 0, firstBytes.size)
                        System.arraycopy(data, 0, dataToWrite, firstBytes.size, data.size)
                        Log.e(TAG, "sendAudioData: Data ${data.size}")
                        Log.e(TAG, "sendAudioData:Total ${dataToWrite.size}")

//                    val outputStream = ByteArrayOutputStream()
//                    outputStream.write(a)
//                    outputStream.write(data)
//                    val dataToWrite = outputStream.toByteArray()
                        OnToCookApplication.instance.bleBoundService.writeFileData(
                            dataToWrite
                        )
                    }
                }
            }
        }
    }

    private fun sendBinData() {
        Log.e(TAG, "sendAudioData: $acknowledgementIndex")

        when (acknowledgementIndex) {
            mapDataToWriteFile.size + 1 -> {
                OnToCookApplication.instance.bleBoundService.writeData(
                    "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
                if (percentage != 100) {
                    mutableLiveData.postValue(100)
                    percentage = 0
                    totalSize = 0
                }
                doSendMessageBroadcast(Constants.FILE_UPLOAD_SUCCESS, "")
            }
            else -> {
                val pnoNumber = acknowledgementIndex
                Log.e(TAG, "sendAudioData: $pnoNumber")
                Log.e(TAG, "sendAudioData:totalSize $totalSize")
                if (totalSize != 0 && acknowledgementIndex % totalSize == 0) {
                    percentage++
                    mutableLiveData.postValue(percentage)
                }
                if (mapDataToWriteFile.contains(pnoNumber)) {
                    val data = mapDataToWriteFile[pnoNumber]
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        data!!
                    )
                }

                val totalCount = mapDataToWriteFile.size / 100

            }
        }
    }

    internal fun receiveLogData(bytes: ByteArray) {
        Log.e(TAG, "sendAudioData: $acknowledgementIndex")

        val totalSizeToSend = bytes.size
        if (totalSizeToSend > 450) {
            val sendingSizePerPacket = 450
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
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                }
                Log.e(TAG, "sendLogData:Index $pnoNumber")
            }
            val byteArray: ByteArray = ByteArray(1000)
            mapDataToWriteFile.forEach { (t, u) ->
                Log.e(TAG, "receiveLogData:Index $t")
                Log.e(TAG, "receiveLogData: ${u.size}")
                byteArray.copyOf()
            }
        }
    }

    private fun prepareAudioFile(bytes: ByteArray) {
        val totalSizeToSend = bytes.size
        var startByteIndex = 0
        var endingByteIndex = 490
        if (totalSizeToSend > 490) {
            val sendingSizePerPacket = 490
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
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteFile[pnoNumber] = dataToWrite
                }
            }
            Log.e(TAG, "sendFileDataToDevice: ${mapDataToWriteFile.size}")
            mapDataToWriteFile.forEach { (i, s) ->
                Log.e(TAG, "Key $i Value $s")
            }
        }
    }

    private fun onReadFailure(throwable: Throwable) {
        println("Read error: $throwable")
    }

    fun writeData(writeData: ByteArray) {
        Log.e(TAG, "writeData: $isSending")
        if (!isSending) if (connectionObservable != null) {
            val disposable = connectionObservable!!
//                .firstOrError()
                .flatMap { rxBleConnection ->
                    //rxBleConnection.writeCharacteristic(UUID.fromString(uuid), writeData)
                    isSending = true
                    rxBleConnection.createNewLongWriteBuilder()
                        .setCharacteristicUuid(UUID.fromString(uuidForWrite)).setMaxBatchSize(512)
                        .setBytes(writeData).build()
//                    rxBleConnection.writeCharacteristic(UUID.fromString(uuidForWrite),writeData)
                }.observeOn(AndroidSchedulers.mainThread()).doOnError {
                    println("Write Fail ${it.localizedMessage}")
                }
                .subscribe({ bytes -> onWriteSuccess() }, this::onWriteFailure)
            compositeDisposable.add(disposable)
        } else {
            println("write error..")
        }
        else {
            haveToSendData = writeData
        }
    }

    fun writeFileData(writeData: ByteArray) {
        if (connectionObservable != null) {
            Log.e(TAG, "writeFileData: ")
            val disposable = connectionObservable!!
                //.firstOrError()
                .flatMap { rxBleConnection ->
                    //rxBleConnection.writeCharacteristic(UUID.fromString(uuid), writeData)
                    isSending = true
                    rxBleConnection.createNewLongWriteBuilder()
                        .setCharacteristicUuid(UUID.fromString(uuidForFileTransfer))
                        .setMaxBatchSize(512).setBytes(writeData).build()
                }.observeOn(AndroidSchedulers.mainThread()).doOnError {
                    println("Write Fail ${it.localizedMessage}")
                }
                .subscribe({ bytes -> onWriteSuccess() }, this::onWriteFailure)

            compositeDisposable.add(disposable)
        } else {
            println("write error..")
        }
    }

    private fun onWriteSuccess() {
        println("Write success")
        doSendMessageBroadcast(Constants.EVENT_BLE_WRITE_SUCCESS, "")
    }

    private fun onWriteFailure(throwable: Throwable) {
        println("Write error: $throwable")
        doSendMessageBroadcast(Constants.EVENT_BLE_WRITE_FAIL, throwable.localizedMessage)
    }

    private fun establishNotifyObserver(
        int: BluetoothGattCharacteristic, b: Boolean, macAddress: String
    ) {

        val disposable = connectionObservable!!.flatMap { rxBleConnection ->
            println("Notifications  ${int.uuid}")
            rxBleConnection.setupNotification(UUID.fromString(if (b) uuidForWrite else uuidForFileTransfer))
        }.doOnNext { _ ->
            println("Notifications has been set up $int")
        }.flatMap { notificationObservable ->
            notificationObservable
        }.observeOn(AndroidSchedulers.mainThread()).doOnError {
            println("connection error 1 ${it.localizedMessage}")
        }.subscribe({
            onNotificationReceived(it, macAddress)
        }, {
            Log.e(TAG, "establishNotifyObserver: OnError ${it.localizedMessage}")
        }, {
            Log.e(TAG, "establishNotifyObserver: OnFINISH")
        })
//            .subscribe(this::onNotificationReceived, this::onNotificationSetupFailure)
        compositeDisposable.add(disposable)
    }

    private fun onNotificationReceived(bytes: ByteArray, macAddress: String) {
        var message = String(bytes, StandardCharsets.UTF_8)
        doSendMessageBroadcast(Constants.EVENT_BLE_NOTIFICATION, message)
        println("Change: " + String(bytes, StandardCharsets.UTF_8))
        println("Change: $macAddress")
        Log.e("PrinceEWW>>>", "BLEBoundService - onNotificationReceived: ${String(bytes, StandardCharsets.UTF_8)}")
    }

    private fun onNotificationSetupFailure(throwable: Throwable) {
        println("Notifications error: $throwable")
        handleError(throwable)
    }

    override fun onDestroy() {
        println("on destroy ${Thread.currentThread().name}")
        super.onDestroy()
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    override fun release() {
        Log.e(TAG, "release: ")
    }

    fun changeRecipeList(recipeStepList: MutableList<RecipeSteps>) {
        Constants.recipeStepList.clear()
        Constants.recipeStepList.addAll(recipeStepList)
    }

    fun updateRecipeItem(recipe: Recipe) {
        if (recipeItemSend != null) recipeItemSend!!.recipe = Gson().toJson(recipe)
    }
}