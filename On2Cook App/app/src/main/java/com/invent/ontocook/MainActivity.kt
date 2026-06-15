package com.invent.ontocook

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.databinding.ActivityMainBinding
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.utils.*
import com.invent.ontocook.utils.Constants.COOKING_MODE
import com.invent.ontocook.utils.Constants.INGREDIENT_MODE
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.canhub.cropper.options
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.yalantis.ucrop.UCrop
import eo.view.bluetoothstate.BluetoothState
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bottom_sheet_view.*
import kotlinx.android.synthetic.main.item_prepare.view.*
import kotlinx.android.synthetic.main.view_footer.*
import kotlinx.android.synthetic.main.view_header.*
import java.io.*
import java.net.*
import java.util.*


class MainActivity : AppCompatActivity() {

    private val rxBleClient = OnToCookApplication.rxBleClient
    private var deviceScanDialog: DeviceScanDialog? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private val TAG = this::class.java.simpleName
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var communicationReceiver: BroadcastReceiver
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        init()
    }

    private fun toggleDeviceScanDialog(isShow: Boolean = false) {
        if (deviceScanDialog != null) {
            deviceScanDialog!!.dismiss()
            deviceScanDialog = null
            return
        }
        if (isShow) {
            deviceScanDialog = DeviceScanDialog()
            deviceScanDialog!!.context = this
            deviceScanDialog!!.show(supportFragmentManager, "")
        }
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        if (isBluetoothPermissionGranted(requestCode, grantResults)) {
//            prepareScanObserver()
//        }
//    }

    @SuppressLint("CheckResult")
    private fun prepareScanObserver() {
        if (rxBleClient.isScanRuntimePermissionGranted) {
            OnToCookApplication.instance.isBluetoothConnected()
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    OnToCookApplication.instance.startBoundService(true)
                }
        } else {
            requestBluetoothPermission(rxBleClient)
        }
    }

    private fun setCurrentBleState() {
        Log.e(
            TAG, "setCurrentBleState: CONNECTED ${OnToCookApplication.instance.isDeviceConnected()}"
        )
        if (OnToCookApplication.instance.isDeviceConnected()) {
            Log.e(TAG, "setCurrentBleState: CONNECTED")
            bleStateView.state = BluetoothState.State.CONNECTED
        } else {
            Log.e(TAG, "setCurrentBleState: CONNECTED OFf")
            bleStateView.state = BluetoothState.State.OFF
        }
    }

    override fun onResume() {
        super.onResume()
        setCurrentBleState()
        if (rxBleClient.isScanRuntimePermissionGranted) {
            Log.e(TAG, "init: Granted")
            if (isBluetoothOn() && !OnToCookApplication.instance.isDeviceConnected()) OnToCookApplication.instance.isBluetoothConnected()
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    OnToCookApplication.instance.startBoundService(true)
                }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isBluetoothOn() && OnToCookApplication.instance.isDeviceConnected()) OnToCookApplication.instance.bleBoundService.writeData(
                "STATUS=?".toByteArray(
                    Charsets.UTF_8
                )
            )
        }, 100)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
//        val totalCount = 2222
//        LoadingUtils.showDialog2(this@MainActivity, false, "Firmware Updating..")
//        var progress = 0
//        Handler(Looper.getMainLooper()).postDelayed({
//            for (i in 0 until totalCount.toInt()) {
//                Log.e(TAG, "init: $progress")
//                Log.e(TAG, "init: progress ${(totalCount.toInt()/100) % 5}")
//                if ((totalCount.toInt()/100) % 5 == 0) {
//                    Log.e(TAG, "init:Update $progress")
//                    progress++
//                    runOnUiThread { LoadingUtils.updateText("$progress%") }
//                }
//            }
//        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(communicationReceiver)
    }

    @Throws(SocketException::class)
    private fun getWifiIp(): String? {
        val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf: NetworkInterface = en.nextElement()
            if (intf.isLoopback) {
                continue
            }
            if (intf.isVirtual) {
                continue
            }
            if (!intf.isUp) {
                continue
            }
            if (intf.isPointToPoint) {
                continue
            }
            if (intf.hardwareAddress == null) {
                continue
            }
            val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress: InetAddress = enumIpAddr.nextElement()
                if (inetAddress.address.size == 4) {
                    return inetAddress.hostAddress
                }
            }
        }
        return null
    }

    private fun init() {
        ivLeft.visibility = View.GONE
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e("TAG", "onReceive: ${intent?.getStringExtra(Constants.EVENT_BLE_ACTION)}")
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        var message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Log.e(TAG, "onReceive: $message")
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        toggleDeviceScanDialog()
                        setCurrentBleState()
//                        prepareScanObserver()
                    }
                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        var message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        toggleDeviceScanDialog()
                        setCurrentBleState()
                        Constants.prepareScanObserver()
                    }
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        toggleDeviceScanDialog()
                        setCurrentBleState()
//                        val foundRecipe =
//                            Constants.RECIPES.filter { it.name.equals("Butter Popcorn",true)  }
//
//                        val intent =
//                            Intent(
//                                applicationContext,
//                                DashboardActivity::class.java
//                            )
//                        intent.putExtra("recipe", foundRecipe[0])
//                        intent.putExtra("isResume", true)
//                        intent.putExtra("currentStep", 4)
//                        intent.putExtra("isPrepareRunning", false)
//                        intent.putExtra("isPlaying", true)
//                        intent.putExtra("changeTime", 20)
//                        startActivity(intent)
                    }
                    Constants.EVENT_BLE_CONNECTION_INIT -> {
                        toggleDeviceScanDialog(true)
                    }
                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
                        var message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        if (deviceScanDialog != null) {
                            deviceScanDialog!!.updateResult(
                                "Connecting to $message"
                            )
                        }
                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        if (message.lowercase().contains("info=")) {
                            var messageChunk =
                                message.lowercase().toString().replace("info=", "").split(",")

                            println("receive message..... $message")

                            val recipeNo = messageChunk[0]
                            val ingredient =
                                if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                            val stepno = if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                            val second = if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                            val foundRecipe =
                                Constants.RECIPES.filter { it.id.toString() == recipeNo }
                            if (foundRecipe.isNotEmpty()) {
                                val intent =
                                    Intent(this@MainActivity, DashboardActivity::class.java)
                                intent.putExtra("recipe", foundRecipe[0])
                                intent.putExtra("prepreparestep", ingredient)
                                intent.putExtra("currentstep", stepno)
                                intent.putExtra("remainTime", second)
                                startActivity(intent)
                            }
                        }
                        if (message.lowercase().contains("recipe=")) {
                            if (!message.contains(",")) {
                                var foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.lowercase()
                                        .replace("recipe=", "")
                                }
                                if (foundRecipe.isNotEmpty()) {
                                    Log.e(TAG, "onReceive: ${Gson().toJson(foundRecipe[0])}")
                                    var intent =
                                        Intent(applicationContext, DashboardActivity::class.java)
                                    intent.putExtra("recipe", foundRecipe[0])
                                    intent.putExtra("isResume", false)
                                    startActivity(intent)
                                } else {
                                    OnToCookApplication.dbInstance.recipeDao().getAllRecipe1()
                                        .subscribe({
                                            it.mapIndexed { index, recipeDb ->
                                                if (message.split(",")[0].lowercase().replace(
                                                        "recipe=", ""
                                                    ) == recipeDb.name[0].lowercase()
                                                ) {
                                                    val recentItem = Constants.getRecentItemFromDb(
                                                        recipeDb
                                                    )
                                                    val recipe = Constants.convertNewJsonToOldJson(
                                                        recipeDb
                                                    )
                                                    recentItem.recipe = Gson().toJson(recipe)
                                                    var intent = Intent(
                                                        applicationContext,
                                                        DashboardActivity::class.java
                                                    )
                                                    intent.putExtra("isResume", false)
                                                    intent.putExtra("recipe", recentItem)
                                                    startActivity(intent)
                                                }
                                            }
                                        }, {

                                        })
                                }
                            } else {
                                val foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.split(",")[0].lowercase()
                                        .replace("recipe=", "")
                                }
                                val cmdSize: Int = message.split(",").size

                                val stepNo = if (cmdSize > 2) message.split(",")[2].lowercase()
                                    .replace("stepno=", "") else "0"
                                if (cmdSize > 1) when (message.split(",")[1].toUpperCase()
                                    .replace("MODE=", "").toUpperCase()) {

                                    INGREDIENT_MODE -> {
                                        if (foundRecipe.isNotEmpty()) {
                                            val intent = Intent(
                                                applicationContext, DashboardActivity::class.java
                                            )
                                            intent.putExtra("recipe", foundRecipe[0])
                                            intent.putExtra("isResume", true)
                                            intent.putExtra(
                                                "prepreparestep", stepNo.toInt() - 1
                                            )
                                            Log.e(
                                                TAG,
                                                "onReceive: ${Gson().toJson(foundRecipe[0])}",
                                            )
                                            intent.putExtra("isPrepareRunning", true)
                                            startActivity(intent)
                                        } else {
                                            OnToCookApplication.dbInstance.recipeDao()
                                                .getAllRecipe1().subscribe({
                                                    it.mapIndexed { index, recipeDb ->
                                                        if (message.split(",")[0].lowercase()
                                                                .replace(
                                                                    "recipe=", ""
                                                                ) == recipeDb.name[0].lowercase()
                                                        ) {
                                                            val recentItem =
                                                                Constants.getRecentItemFromDb(
                                                                    recipeDb
                                                                )
                                                            val recipe =
                                                                Constants.convertNewJsonToOldJson(
                                                                    recipeDb
                                                                )
                                                            recentItem.recipe =
                                                                Gson().toJson(recipe)
                                                            var intent = Intent(
                                                                applicationContext,
                                                                DashboardActivity::class.java
                                                            )
                                                            intent.putExtra("recipe", recentItem)
                                                            intent.putExtra("isResume", true)
                                                            intent.putExtra(
                                                                "prepreparestep", stepNo.toInt() - 1
                                                            )
                                                            intent.putExtra(
                                                                "isPrepareRunning", true
                                                            )
                                                            startActivity(intent)

                                                        }
                                                    }
                                                }, {

                                                })
                                        }
                                    }
                                    COOKING_MODE -> {
                                        if (foundRecipe.isNotEmpty()) {
                                            val indTime = message.split(",")[3].lowercase()
                                                .replace("ind_run=", "")
                                            val magTime = message.split(",")[4].lowercase()
                                                .replace("mag_run=", "")
                                            val status = message.split(",")[5].lowercase()
                                                .replace("status=", "")
                                            val intent = Intent(
                                                applicationContext, DashboardActivity::class.java
                                            )
                                            intent.putExtra("recipe", foundRecipe[0])
                                            intent.putExtra("isResume", true)
                                            intent.putExtra("currentStep", stepNo.toInt())
                                            intent.putExtra("isPrepareRunning", false)
//                                                if (status != "pause")
                                            intent.putExtra(
                                                "isPlaying",
                                                indTime.toInt()
                                                    .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                            )
                                            Log.e(
                                                TAG, "onReceive:CheckStatus ${
                                                    indTime.toInt()
                                                        .coerceAtLeast(magTime.toInt()) != 0
                                                }"
                                            )
                                            Log.e(TAG, "onReceive:CheckStatus ${status != "pause"}")

                                            Log.e(
                                                TAG,
                                                "onReceive: ${Gson().toJson(foundRecipe[0])}",
                                            )

                                            intent.putExtra(
                                                "changeTime",
                                                indTime.toInt().coerceAtLeast(magTime.toInt())
                                            )
                                            startActivity(intent)
                                        } else {
                                            OnToCookApplication.dbInstance.recipeDao()
                                                .getAllRecipe1().subscribe({
                                                    it.mapIndexed { index, recipeDb ->
                                                        if (message.split(",")[0].lowercase()
                                                                .replace(
                                                                    "recipe=", ""
                                                                ) == recipeDb.name[0].lowercase() && message.split(
                                                                ","
                                                            ).size > 4
                                                        ) {
                                                            val indTime =
                                                                message.split(",")[3].lowercase()
                                                                    .replace("ind_run=", "")
                                                            val magTime =
                                                                message.split(",")[4].lowercase()
                                                                    .replace("mag_run=", "")
                                                            val status =
                                                                message.split(",")[5].lowercase()
                                                                    .replace("status=", "")
                                                            val recentItem =
                                                                Constants.getRecentItemFromDb(
                                                                    recipeDb
                                                                )
                                                            val recipe =
                                                                Constants.convertNewJsonToOldJson(
                                                                    recipeDb
                                                                )
                                                            recentItem.recipe =
                                                                Gson().toJson(recipe)
                                                            var intent = Intent(
                                                                applicationContext,
                                                                DashboardActivity::class.java
                                                            )
                                                            intent.putExtra("recipe", recentItem)
                                                            intent.putExtra("isResume", true)
                                                            intent.putExtra(
                                                                "prepreparestep", stepNo.toInt() - 1
                                                            )
                                                            intent.putExtra(
                                                                "currentStep", stepNo.toInt()
                                                            )
                                                            intent.putExtra(
                                                                "isPrepareRunning", false
                                                            )
                                                            Log.e(
                                                                TAG, "onReceive:CheckStatus ${
                                                                    indTime.toInt()
                                                                        .coerceAtLeast(magTime.toInt()) != 0
                                                                }"
                                                            )
                                                            Log.e(
                                                                TAG,
                                                                "onReceive:CheckStatus ${status != "pause"}"
                                                            )

//                                                if (status != "pause")
                                                            intent.putExtra(
                                                                "isPlaying",
                                                                indTime.toInt()
                                                                    .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                                            )
                                                            intent.putExtra(
                                                                "changeTime",
                                                                indTime.toInt()
                                                                    .coerceAtLeast(magTime.toInt())
                                                            )
                                                            startActivity(intent)
                                                        }
                                                    }
                                                }, {

                                                })
                                        }
                                    }
                                    else -> {
                                        OnToCookApplication.instance.bleBoundService.cancelNotifyTimer()
                                    }
                                }
                            }
                        }
                        if (message.toUpperCase()
                                .contains("INDQUICKSTART=") && Constants.checkNavigation(message)
                        ) {
                            val intent = Intent(this@MainActivity, CookingActivity::class.java)
                            startActivity(intent)
                        }

                    }
                }
            }
        }

        llBleConnect.setOnClickListener {
            if (OnToCookApplication.instance.isDeviceConnected()) {
//                OnToCookApplication.instance.stopBoundService(true)
                println("connected...")
            } else {
                checkPermission()
//                prepareScanObserver()
            }
        }

        tvPageTitle.text = ""

        ivGear.setOnClickListener {
            var intent = Intent(this, FileChooserActivity::class.java)
            startActivity(intent)
        }
        clCook.setOnClickListener {
            var intent = Intent(this, CookingActivity::class.java)
            startActivity(intent)
        }

        clExplore.setOnClickListener {
            var intent = Intent(this, SDCardReaderActivity::class.java)
            startActivity(intent)
        }
        binding.clShop.setOnClickListener {
//            startCrop()
        }

        bottomSheetBehavior = BottomSheetBehavior.from(constraintBottomSheet)
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                toggleTopArrow(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
//                val bottomSheetVisibleHeight = bottomSheet.height - bottomSheet.top
//                println("height   ${bottomSheetVisibleHeight}")
//                constraintLayout3.translationY =
//                    (bottomSheetVisibleHeight - constraintLayout3.height).toFloat()
//                println("height   ${constraintLayout3.translationY}")
            }
        })

        ivNotification.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            }
        }

    }

    var tts: TextToSpeech? = null
    private fun generateAudio() {
        tts = TextToSpeech(this) { i ->
            if (i == TextToSpeech.SUCCESS) {
                val locale = Locale("en", "US")
                tts?.language = locale
                val utteranceId = "myTestId2"
                val destinationFile = File(externalCacheDir, "$utteranceId.mp3")
                val params = HashMap<String, String>()
                tts!!.synthesizeToFile("textToConverts", null, destinationFile, utteranceId)
                tts!!.setOnUtteranceCompletedListener(OnUtteranceCompletedListener { s ->
                    Log.e(TAG, "generateAudio: $s")
                    if (s == utteranceId) {
                        // start playing the audio file defined at myTestingId.wav
                    }
                })
                // speech engine is ready to rock
            } else {
                // speech engine initialization fail
            }
        }
    }

    fun toggleTopArrow(newState: Int) {
//        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
//            ivTopArrow.setVisibility(View.GONE)
//        } else {
//            ivTopArrow.setVisibility(View.VISIBLE)
//        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            201 -> {
                if (grantResults.isNotEmpty() && grantResults[0] === PackageManager.PERMISSION_GRANTED) {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    getResultForBLE.launch(enableIntent)
                }
                return
            }
            111 -> {
                checkPermission()
            }
            else -> {
                checkPermission()
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e("in", "sdk version 31 and above")
            Dexter.withContext(this@MainActivity).withPermissions(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    // check if all permissions are granted
                    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    if (report.areAllPermissionsGranted()) {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@MainActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("Permission", "All Permissions granted")
                            ensureBLESupported()
                            if (!isBLEEnabled()) {
                                Log.e("in", "ble is not enabled")
                                showBLEDialog()
                            } else {
                                Log.e("in", "all permissions granted go for scan")
                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@MainActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")

                            var IS_FINE_LOCATION_DENY_PERMANENT = false
                            var IS_COARSE_LOCATION_DENY_PERMANENT = false
                            var IS_BLUETOOTH_SCAN_DENY_PERMANENT = false
                            var IS_BLUETOOTH_CONNECT_DENY_PERMANENT = false
                            for (i in report.deniedPermissionResponses) {
                                if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION && i.isPermanentlyDenied) {
                                    IS_FINE_LOCATION_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION && i.isPermanentlyDenied) {
                                    IS_COARSE_LOCATION_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.BLUETOOTH_SCAN && i.isPermanentlyDenied) {
                                    IS_BLUETOOTH_SCAN_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.BLUETOOTH_CONNECT && i.isPermanentlyDenied) {
                                    IS_BLUETOOTH_CONNECT_DENY_PERMANENT = true
                                }
                            }
                            var ACCESS_COARSE_LOCATION_ALLOWED = false
                            var ACCESS_FINE_LOCATION_ALLOWED = false
                            var BLUETOOTH_NEARBY_ALLOWED = false
                            for (i in report.grantedPermissionResponses) {
                                if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION) {
                                    ACCESS_COARSE_LOCATION_ALLOWED = true
                                } else if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION) {
                                    ACCESS_FINE_LOCATION_ALLOWED = true
                                } else if (i.permissionName == Manifest.permission.BLUETOOTH_SCAN || i.permissionName == Manifest.permission.BLUETOOTH_CONNECT) {
                                    BLUETOOTH_NEARBY_ALLOWED = true
                                }
                            }

                            if (IS_COARSE_LOCATION_DENY_PERMANENT && IS_FINE_LOCATION_DENY_PERMANENT && (IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Nearby devices and Location Permission is Permanently denied, GO TO Settings"
                                )
                            } else if (IS_COARSE_LOCATION_DENY_PERMANENT && IS_FINE_LOCATION_DENY_PERMANENT) {
                                showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Location Permission is Permanently denied, GO TO Settings"
                                )
                            } else if (ACCESS_COARSE_LOCATION_ALLOWED && !IS_FINE_LOCATION_DENY_PERMANENT && !ACCESS_FINE_LOCATION_ALLOWED) {
                                Log.e(
                                    "Permission",
                                    "Precise Location Permission is not allowed, Again ask for permission"
                                )
                                checkPermission()
                            } else if (IS_FINE_LOCATION_DENY_PERMANENT && (IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_precise_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Location Permission allowed but Precise Location and nearby devices is Permanently denied, GO TO Settings"
                                )
                            } else if ((IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_permissions))
                                Log.e(
                                    "Permission",
                                    "Nearby devices is Permanently denied, GO TO Settings"
                                )
                            } else if (IS_FINE_LOCATION_DENY_PERMANENT) {
                                showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Precise Location is Permanently denied, GO TO Settings"
                                )
                            } else {

                                // check for permanent denial of any permission
                                if (report.deniedPermissionResponses.size != 0) {
                                    // show alert dialog navigating to Settings
                                    if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED && !BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices and Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_FINE_LOCATION_ALLOWED && !BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_precise_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices and Precise Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_FINE_LOCATION_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                        Log.e(
                                            "Permission",
                                            " Precise Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else {
                                        showSettingsDialog(getString(R.string.this_app_need_permissions))
                                        Log.e(
                                            "Permission",
                                            "All Permissions Permanently denied, GO TO Settings"
                                        )
                                    }
                                } else {
                                    Log.e("Permission", "No scan results")
//                                        binding.scanAgain.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionRequest: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest();
                }


            }).withErrorListener {
                Log.e("in", "error listener")
            }.onSameThread().check()
        } else {
            Log.e("in", "below sdk version 31")
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    // check if all permissions are granted
                    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    if (report.areAllPermissionsGranted()) {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@MainActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")
                            Log.e("Permission", "All Permissions granted")
//                                scanDeviceList.clear()
                            ensureBLESupported()
                            if (!isBLEEnabled()) {
                                showBLEDialog()
                            } else {
                                Log.e("in", "all permissions granted go for scan")
//                                    startScan()
                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@MainActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")
                            if (report.deniedPermissionResponses.size != 0) {
                                // show alert dialog navigating to Settings
                                var ACCESS_COARSE_LOCATION_ALLOWED = false
                                var ACCESS_FINE_LOCATION_ALLOWED = false

                                for (i in report.grantedPermissionResponses) {
                                    if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION) {
                                        ACCESS_COARSE_LOCATION_ALLOWED = true
                                    } else if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION) {
                                        ACCESS_FINE_LOCATION_ALLOWED = true
                                    }
                                }

                                if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED) {
                                    showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                    Log.e(
                                        "Permission",
                                        "Location Permission is Permanently denied, GO TO Settings"
                                    )
                                } else if (!ACCESS_FINE_LOCATION_ALLOWED) {
                                    showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                    Log.e(
                                        "Permission",
                                        "Precise Location Permission is Permanently denied, GO TO Settings"
                                    )
                                } else {
                                    showSettingsDialog(getString(R.string.this_app_need_permissions))
                                    Log.e(
                                        "Permission",
                                        "All Permissions Permanently denied, GO TO Settings"
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionRequest: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).withErrorListener {
                Log.e("in", "error listener")
            }.onSameThread().check()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e(TAG, "onActivityResult: resultCode$resultCode requestCode$requestCode data$data", )
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val  resultUri = data?.let { UCrop.getOutput(it) }
            Log.e(TAG, "onActivityResult: $resultUri", )
        } else if (resultCode == UCrop.RESULT_ERROR) {
            
        }
//        if (requestCode === CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
//            val result = CropImage.getActivityResult(data)
//            if (resultCode === RESULT_OK) {
//                val resultUri = result.uri
//                Log.e(TAG, "onActivityResult: $resultUri")
//            } else if (resultCode === CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
//                val error = result.error
//            }
//        }
    }

    fun showSettingsDialog(message: String) {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.need_permissions))
            .setMessage(message).setCancelable(false).setPositiveButton(
                getString(R.string.go_to_settings)
            ) { dialogInterface, i ->
                openSettings()
            }.setNegativeButton(
                getString(R.string.cancel)
            ) { dialogInterface, i ->
//                binding.scanAgain.visibility = View.VISIBLE

            }.show()
    }

    private fun hasGPSDevice(context: Context): Boolean {
        val mgr =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager ?: return false
        val providers = mgr.allProviders ?: return false
        return providers.contains(LocationManager.GPS_PROVIDER)
    }


    private lateinit var locationRequest: LocationRequest
    private fun checkLocationSetting() {
        locationRequest = LocationRequest.create()
        locationRequest.apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 2000
        }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> =
            LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        result.addOnCompleteListener {
            try {
                val response: LocationSettingsResponse = it.getResult(ApiException::class.java)
                Log.e("GPS", "is on")
            } catch (e: ApiException) {
                Log.e("in", "exe")
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        val resolvableApiException = e as ResolvableApiException
                        Log.e("permission", "checkSetting: RESOLUTION_REQUIRED")
                        val intentSenderRequest =
                            IntentSenderRequest.Builder(resolvableApiException.resolution).build()
                        resolutionForResult.launch(intentSenderRequest)
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        // USER DEVICE DOES NOT HAVE LOCATION OPTION
                        Log.e("GPS", "Required to use this app")
                    }
                }
            }
        }
    }

    private val resolutionForResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            Log.e("GPS", "enable")
            checkPermission()
        } else {
            Log.e("GPS", "GPS is Required to use this app")
            showGPSSettingsDialog(getString(R.string.this_app_need_gps_permissions))
        }
    }


    private fun showGPSSettingsDialog(message: String) {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.need_permissions))
            .setMessage(message).setCancelable(false).setPositiveButton(
                getString(R.string.go_to_settings)
            ) { dialogInterface, i ->
                getGPSResult.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.setNegativeButton(
                getString(R.string.cancel)
            ) { dialogInterface, i ->
//                binding.scanAgain.visibility = View.VISIBLE
            }.show()
    }

    private val getGPSResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(this)) {
            checkPermission()
        } else {
            showGPSSettingsDialog(getString(R.string.this_app_need_gps_permissions))
        }
    }


    private fun ensureBLESupported() {
        Log.e("in fun", "check ble support")
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("ble", "not supported by device")
            Toast.makeText(this, R.string.no_ble, Toast.LENGTH_LONG).show()
            this.finish()
        }
    }

    protected fun isBLEEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }


    protected fun showBLEDialog() {
        if (!isBLEEnabled()) {
            Log.e("in fun", "ble enabled")
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            getResultForBLE.launch(enableIntent)
        }
    }

    private val getResultForBLE = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        getResult.launch(intent)

    }


    private val getResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }

}