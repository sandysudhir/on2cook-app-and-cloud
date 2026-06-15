package com.invent.ontocook

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.google.android.material.snackbar.Snackbar
import com.invent.ontocook.db.AppDatabase
import com.invent.ontocook.dialog.BLEErrorDialog
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.SplashActivity
import com.invent.ontocook.services.BLEBoundService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.isBluetoothOn
import com.invent.ontocook.utils.toggleBluetooth
import com.mapzen.speakerbox.Speakerbox
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import eo.view.bluetoothstate.BluetoothState
import io.reactivex.Observable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException


class OnToCookApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val TAG: String = this::class.java.simpleName
    lateinit var bleBoundService: BLEBoundService
    var isBound: Boolean = false
    var isDashboard: Boolean = false
    private var numberOfActivity = 0
    var broadcastReceiver: BroadcastReceiver? = null
    var currentActivity: AppCompatActivity? = null
    var dashboardActivity: DashboardActivity? = null
    private var snackbar: Snackbar? = null
    private var bleErrorDialog: BLEErrorDialog? = null
    private var skipActivity = listOf<String>("MainActivity")

    private lateinit var speakerBox: Speakerbox

    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            Log.e(TAG, "onServiceDisconnected: $isBound")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BLEBoundService.LocalBinder
            bleBoundService = binder.getService()
            isBound = true

            var intent = Intent(Constants.EVENT_SERVICE_STARTED)
            LocalBroadcastManager.getInstance(this@OnToCookApplication).sendBroadcast(intent)
        }
    }

    fun isDeviceConnected(): Boolean {
        return isServiceInitialize()
                && bleBoundService.bleDevice != null
                && bleBoundService.bleDevice!!.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED
    }

    private fun isServiceInitialize(): Boolean {
        return this@OnToCookApplication::bleBoundService.isInitialized
    }

    fun isBluetoothConnected(): Observable<Boolean> {
        return Observable.create {
            if (isBluetoothOn()) {
                it.onNext(true)
                it.onComplete()
            } else {
                val broadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                            when (intent?.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR
                            )) {
                                BluetoothAdapter.STATE_ON -> {
                                    it.onNext(true)
                                    this@OnToCookApplication.unregisterReceiver(this)
                                    it.onComplete()
                                }
                                BluetoothAdapter.STATE_OFF -> {
                                    it.onNext(false)
                                    this@OnToCookApplication.unregisterReceiver(this)
                                    it.onComplete()
                                }
                                BluetoothAdapter.STATE_TURNING_ON -> {
                                    println("state turning on")
                                }
                                BluetoothAdapter.STATE_TURNING_OFF -> {
                                    println("state turning off")
                                }
                            }
                        }
                    }
                }
                this@OnToCookApplication.registerReceiver(
                    broadcastReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                )
                toggleBluetooth(true)
            }
        }
    }

    fun speak(message: String) {
        if (this@OnToCookApplication::speakerBox.isInitialized) {
            speakerBox.play(message.replace("_", " "))
        }
    }

    fun startBoundService(isForceStart: Boolean = false) {
        println("start bound service,, $isBound")
        if (isForceStart) {
            stopBoundService(true)
            isBound = false
        }
        if (!isBound) {
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(applicationContext, BLEBoundService::class.java)
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }, 100)
        }
    }

    internal fun stopBoundService(isTerminate: Boolean = false) {
        if (isBound) {
            println("stop bounds service..")
            isBound = false
//            bleBoundService.notificationBuilder.setOngoing(false)
            val notificationManager =
                applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
//            bleBoundService.stopForeground(bleBoundService.FOREGROUND_NOTIFICATION_ID)
//            bleBoundService.stopSelf(bleBoundService.FOREGROUND_NOTIFICATION_ID)
            bleBoundService.manager?.cancel(bleBoundService.FOREGROUND_NOTIFICATION_ID)
            bleBoundService.manager?.cancelAll()
            bleBoundService.stopSelf()
            bleBoundService.isTerminateService = isTerminate
            unbindService(connection)
        }
    }

    companion object {
        lateinit var rxBleClient: RxBleClient
            private set
        lateinit var instance: OnToCookApplication
            private set
        lateinit var dbInstance: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
//        this.registerActivityLifecycleCallbacks(this@OnToCookApplication)

        CaocConfig.Builder.create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
            .showErrorDetails(true) //default: true
            .showRestartButton(true) //default: true
            .trackActivities(true)
            .restartActivity(SplashActivity::class.java)
            .apply()
        dbInstance = AppDatabase.getDatabase(this)
        instance = this
        speakerBox = Speakerbox(this)
        rxBleClient = RxBleClient.create(this)
//        RxBleClient.updateLogOptions(
//            LogOptions.Builder().setLogLevel(LogConstants.INFO)
//                .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
//                .setUuidsLogSetting(LogConstants.UUIDS_FULL)
//                .setShouldLogAttributeValues(true)
//                .build()
//        )

        //-------PrinceEWW, to prevent crash issue of reactivex.exceptions.UndeliverableException, This is Rx java plugin - link for reference: https://github.com/PhilipsHue/flutter_reactive_ble/issues/768------//
        RxJavaPlugins.setErrorHandler { e ->
            var throwable = e
            if (throwable is UndeliverableException) {
                throwable = throwable.cause
            }
            if (throwable is IOException || throwable is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (throwable is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (throwable is NullPointerException || throwable is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler
                    ?.uncaughtException(Thread.currentThread(), throwable)
                return@setErrorHandler
            }
            if (throwable is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler
                    ?.uncaughtException(Thread.currentThread(), throwable)
                return@setErrorHandler
            }
            // Log undeliverable exceptions to the console
            throwable?.printStackTrace()
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        Log.e(TAG, "onReceive: EVENT_BLE_CONNECTION_ABORT")
                        if (currentActivity != null) {
                            if (currentActivity!!.localClassName != "CookingActivity") {
//                                prepareErrorDialog()
                            }
                        }
                    }
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        val message =
                            intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        if (bleErrorDialog != null) {
                            bleErrorDialog?.setConnectionState(
                                message = message,
                                state = BluetoothState.State.OFF
                            )
                        } else {
                            showSnackBar(message)
                        }
                    }
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        if (bleErrorDialog != null) {
                            bleErrorDialog?.setConnectionState(
                                title = "Connecting",
                                message = "Connecting with device...",
                                state = BluetoothState.State.CONNECTING
                            )
                        } else {
                            showSnackBar("Connection Established....")
                        }
                    }
                }
            }
        }
    }

    private fun hideSnackBar() {
        if (snackbar != null) {
            snackbar?.dismiss()
            snackbar = null
        }
    }

    private fun showSnackBar(message: String, length: Int = Snackbar.LENGTH_LONG) {
        if (currentActivity != null) {
            if (snackbar != null) {
                snackbar?.dismiss()
                snackbar = null
            }
            snackbar = Snackbar.make(
                currentActivity!!.findViewById(android.R.id.content),
                message,
                length
            )
//            .setAction("Reconnect") {
//            GunDrawerApplication.instance.startBoundService()
//            snackbar?.dismiss()
//            snackbar = null
//        }
            snackbar?.show()
        }
    }

    private fun prepareErrorDialog() {
        if (currentActivity != null) {
            if (bleErrorDialog != null) {
                bleErrorDialog!!.dismiss()
                bleErrorDialog = null
            }
            bleErrorDialog = BLEErrorDialog()
            bleErrorDialog!!.context = currentActivity!!
            bleErrorDialog!!.show(currentActivity!!.supportFragmentManager, "")
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (!activity.localClassName.equals("com.karumi.dexter.DexterActivity")) {
            if (!skipActivity.contains(activity.localClassName)) {
                println("activity pause  ${activity.localClassName}")
                numberOfActivity--
                if (numberOfActivity == 0) {
                    currentActivity = null
                    println("unregister receiver")
                    LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver!!)
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        println("activity destroy ${activity.localClassName}")
        if (activity.localClassName == DashboardActivity::class.java.simpleName) {
            isDashboard = false
        }
        if (activity.localClassName.lowercase() == "mainactivity") {
            dashboardActivity = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.localClassName != "com.karumi.dexter.DexterActivity") {
            if (!skipActivity.contains(activity.localClassName)) {
                println("activity resume ${activity.localClassName}")
                if (numberOfActivity == 0) {
                    currentActivity = activity as AppCompatActivity
                    println("Register receiver")
                    LocalBroadcastManager.getInstance(this)
                        .registerReceiver(
                            broadcastReceiver!!,
                            IntentFilter(Constants.EVENT_BLE_CONNECTION)
                        )
                }
                numberOfActivity++
                if (activity.localClassName == DashboardActivity::class.java.simpleName) {
                    dashboardActivity = activity as DashboardActivity
                    isDashboard = true
                }
            }
        }
    }
}