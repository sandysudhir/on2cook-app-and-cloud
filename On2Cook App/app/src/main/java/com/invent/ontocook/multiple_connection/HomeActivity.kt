package com.invent.ontocook.multiple_connection

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.widget.ViewPager2
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ActivityHomeBinding
import com.invent.ontocook.databinding.DialogQrScanBinding
import com.invent.ontocook.databinding.DialogScanDeviceListBinding
import com.invent.ontocook.dialog.EditNameDialog
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.showSnackBarLong
import com.invent.ontocook.extension.showSnackBarShort
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.multiple_connection.adapter.AvailableDevicesAdapter
import com.invent.ontocook.multiple_connection.adapter.PairedDevicesAdapter
import com.invent.ontocook.multiple_connection.adapter.ViewpagerAdapter
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.ui.CookingFragment
import com.invent.ontocook.multiple_connection.ui.DashboardFragment
import com.invent.ontocook.multiple_connection.ui.HomeFragment
import com.invent.ontocook.multiple_connection.ui.LogFragment
import com.invent.ontocook.multiple_connection.ui.RecipeDetailFragment
import com.invent.ontocook.multiple_connection.ui.RecipeFragment
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.SharedPreferencesManager
import com.invent.ontocook.utils.createAudioFile
import com.invent.ontocook.utils.gone
import com.invent.ontocook.utils.goneIfOrVisible
import com.invent.ontocook.utils.makeFileCopyInCacheDir
import com.invent.ontocook.utils.onClick
import com.invent.ontocook.utils.onSafeClick
import com.invent.ontocook.utils.visible
import com.invent.ontocook.utils.visibleIfOrGone
import com.invent.ontocook.utils.withNotNull
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eo.view.bluetoothstate.BluetoothState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var service: BleService
    internal lateinit var viewPagerAdapter: ViewpagerAdapter
    private var macAddressList = ArrayList<String>()
    val changeDummyMac = MutableLiveData<String>()
    lateinit var broadcastReceiver: BroadcastReceiver
    lateinit var selectedType: Constants.AUDIO_TYPE
    private var mediaRecord: MediaRecorder? = null
    var openDialog = false
    val deviceHashmap = HashMap<String, BluetoothDevice>()

    private lateinit var dialog: Dialog
    private lateinit var availableDevicesAdapter: AvailableDevicesAdapter
    private var availableDevicesList: MutableList<BluetoothDevice> = mutableListOf()
    private var connectedDevicesList: MutableList<PairedDeviceData> = mutableListOf()
    private var pairedDevicesList: MutableList<PairedDeviceData> = mutableListOf()
    private val heartbeatJobs = mutableMapOf<String, Job>()

    private val SUPABASE_DEVICE_STATUS_URL =
        "https://jumrjxzgovexuzpefalg.supabase.co/functions/v1/smart-action"

    private val DEVICE_PING_API_KEY =
        "on2cook-device-ping-12345"
    private lateinit var pairedDeviceAdapter: PairedDevicesAdapter
    private lateinit var dialogScanDeviceListBinding: DialogScanDeviceListBinding

    private lateinit var locationRequest: LocationRequest
    private val getResultForBLE = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }
    private var resultLauncher =
        (this as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent = result.data!!
                data.data?.also { uri ->
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        uri.let {
                            val fileName = Constants.getFileName(
                                this@HomeActivity, uri = it
                            )
                            DebugLog.e("position  $fileName")
                            val path = makeFileCopyInCacheDir(this@HomeActivity, uri)
                            val file = File(path)
                            runOnUiThread {
                                LoadingUtils.showLoading(this@HomeActivity, false)
                            }
                            val externalStorageDir = filesDir

                            val folder = File(
                                externalStorageDir, when (selectedType) {
                                    Constants.AUDIO_TYPE.FEEDBACK -> {
                                        Constants.FEEDBACK_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.ERROR -> {
                                        Constants.ERROR_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.TIME -> {
                                        Constants.ERROR_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.QUANTITY -> {
                                        Constants.ERROR_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.ACTION -> {
                                        Constants.ERROR_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.INGREDIENTS -> {
                                        Constants.INGREDIENTS_AUDIO_PATH
                                    }
                                }
                            )

                            val root = File(externalCacheDir, fileName)
                            if (!root.exists()) {
                                root.mkdirs()
                            }
                            val subRoot = File(folder, "test")
                            if (!folder.exists()) {
                                folder.mkdirs()
                            }

//                            Record.convertID3Tags(
//                                file.path,
//                                "gaurang",
//                                "artist",
//                                "album",
//                                this@HomeActivity
//                            )
                            delay(5000)

//                            val mp3file2 = Mp3File("${folder}/test.mp3")
//                            println("Track: Mp3" + mp3file2.hasId3v1Tag())
//                            if (mp3file2.hasId3v1Tag()) {
//                                val id3v1Tag = mp3file2.id3v1Tag
//                                println("Track: " + id3v1Tag.track)
//                                println("Artist: " + id3v1Tag.artist)
//                                println("Title: " + id3v1Tag.title)
//                                println("Album: " + id3v1Tag.album)
//                                println("Year: " + id3v1Tag.year)
//                                println("Genre: " + id3v1Tag.genre + " (" + id3v1Tag.genreDescription + ")")
//                                println("Comment: " + id3v1Tag.comment)
//                            }
//                            delay(5000)
//                            val mp3file1 = Mp3File(file.path)
//                            if (mp3file1.hasId3v1Tag()) {
//                                val id3v1Tag = mp3file1.id3v1Tag
//                                id3v1Tag.track = "Gaurang"
//                                id3v1Tag.artist = "IOP"
//                                id3v1Tag.year = "1222"
//                                id3v1Tag.title = "title"
//                                id3v1Tag.track = "Gaurang"
//                                mp3file1.id3v1Tag
//                                mp3file1.id3v1Tag = id3v1Tag
//                            }

//                            unZipAudio(file, folder.path).also {
//                                runOnUiThread {
//                                    LoadingUtils.hideDialog()
//                                }
//                            }
                        }
                    }
                }
            }
        }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
            if (openDialog) openDialog()
            else {
                if (SharedPreferencesManager.getMacAddressList(applicationContext)
                        .isNotEmpty()
                ) startScan(false)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    private val getResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        init()
        initListener()
//        readRawData()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.EVENT_STOP_SCAN)
        intentFilter.addAction(Constants.FILE_RECEIVE_SUCCESS)
        intentFilter.addAction(Constants.EVENT_BLE_CONNECTION)
        intentFilter.addAction(Constants.CHANGE_DEVICE_NAME)
        intentFilter.addAction(Constants.SET_DEVICE_RTCTIME)
        intentFilter.addAction(Constants.EVENT_LOG)
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, intentFilter
        )
    }

    private fun markDeviceInactive(macAddress: String, reason: String) {
        Log.e("SUPABASE_STATUS", "markDeviceInactive called mac=$macAddress reason=$reason")

        stopDeviceHeartbeat(macAddress)

        sendDeviceStatusToServer(
            macAddress = macAddress,
            status = "inactive",
            eventType = "disconnect"
        )
    }

    private fun readRawData(): ArrayList<AudioFileModel> {
        val data: ArrayList<AudioFileModel> = ArrayList()
        try {
            //addding from local memory

            val assetManager = assets
            try {
                // List all files and folders in the root path of assets
                val list = assetManager.list("audio")
                if (list != null) {
                    for (item in list) {
                        // Check if the item is a folder (subdirectory)
                        if (assetManager.list("audio/$item")!!.isNotEmpty()) {
                            assetManager.list("audio/$item")!!.forEachIndexed { index, it ->
                                data.add(
                                    AudioFileModel(
                                        index, it, "audio/$item/", Constants.FILE_TYPE.STATIC,
                                        false, null
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Log.e("TAG", "readRawData: ${data.size}")
            val externalStorageDir = filesDir

            val folder = File(externalStorageDir, "Audio")
            if (folder.exists()) {
                folder.list()?.forEach { it ->
                    val folder = File(folder, "/$it")
//                    folder.listFiles().sorted()
//                        .map {
//                            it.lastModified()
//                        }
//                    val listSorted = folder.listFiles()
//                    if (listSorted != null && listSorted.isNotEmpty()) {
//                        Arrays.sort(
//                            listSorted
//                        ) { object1, object2 -> (if (object1.lastModified() > object2.lastModified()) object1.lastModified() else object2.lastModified()).toInt() }
//                    }
                    val list2 = folder.listFiles().sortBy { it.lastModified() }
//                    DebugLog.e("List....${listSorted.toList().toTypedArray().contentToString()}")
                    folder.listFiles()?.forEachIndexed { index, file ->
                        data.add(
                            AudioFileModel(
                                id = index,
                                fileName = file.name,
                                filePath = null, type = Constants.FILE_TYPE.UPLOADED,
                                isSelected = false,
                                file = file
                            )
//                            AudioFileListModel(
//                                id = data.size,
//                                name = "$it File",
//                                false,
//                                fileModel = fileModelList.toList()
//                            )
                        )
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    data.forEach {
                        DebugLog.e("Print Data Before ${it.fileName}")
                    }
                    delay(3000)
                    data.sortWith(Comparator { arg0: AudioFileModel, arg1: AudioFileModel ->
                        if (arg0.fileName != arg1.fileName) {
                            return@Comparator if (arg0.fileName < arg1.fileName) -1 else 1
                        }
                        0
                    })
                    delay(3000)
                    data.forEach {
                        DebugLog.e("Print Data After ${it.fileName}")
                    }
                }

                //sort all the transitions by first character

//                Collections.sort(
//                    list
//                ) { o1, o2 -> o1.name.compareTo(o2.name) }
//                data.sortBy { it.fileName }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return data
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
//        Clear the Activity's bundle of the subsidiary fragments' bundles.
        outState.clear()
    }

    override fun onDestroy() {
        super.onDestroy()

        stopAllDeviceHeartbeats()

        if (this::service.isInitialized) {
            unbindService(
                mConnection
            )
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            broadcastReceiver
        )
    }

    override fun onBackPressed() {
//        if (myFragments.isNotEmpty()) (myFragments[binding.myViewPager2.currentItem] as DashboardFragment).onBackPress()
//        else {
//            if (doubleBackToExitPressedOnce) {
//                onBackPressedDispatcher.onBackPressed()
//                return
//            }
//            this.doubleBackToExitPressedOnce = true
//            showSnackBarShort(resources.getText(R.string.backpress_msg))
//            Handler(Looper.getMainLooper()).postDelayed(Runnable {
//                doubleBackToExitPressedOnce = false
//            }, 2000)
//        }

        (viewPagerAdapter.getFragment(binding.myViewPager2.currentItem) as DashboardFragment).onBackPress()
    }
    private fun sendDeviceStatusToServer(
        macAddress: String,
        status: String,
        eventType: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null

            try {
                val jsonBody = JSONObject().apply {
                    put("mac_id", macAddress)
                    put("status", status)
                    put("event_type", eventType)
                }

                val url = URL(SUPABASE_DEVICE_STATUS_URL)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", DEVICE_PING_API_KEY)
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                val responseBody = try {
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                } catch (e: Exception) {
                    ""
                }

                Log.e(
                    "SUPABASE_STATUS",
                    "mac=$macAddress, status=$status, event=$eventType, responseCode=$responseCode, response=$responseBody"
                )

            } catch (e: Exception) {
                Log.e(
                    "SUPABASE_STATUS",
                    "Failed mac=$macAddress, status=$status, event=$eventType, error=${e.message}"
                )
            } finally {
                connection?.disconnect()
            }
        }
    }
    private fun startDeviceHeartbeat(macAddress: String) {
        if (heartbeatJobs[macAddress]?.isActive == true) {
            return
        }

        heartbeatJobs[macAddress] = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(60_000)

                if (!isActive) {
                    break
                }

                sendDeviceStatusToServer(
                    macAddress = macAddress,
                    status = "active",
                    eventType = "heartbeat"
                )
            }
        }
    }
    private fun stopDeviceHeartbeat(macAddress: String) {
        heartbeatJobs[macAddress]?.cancel()
        heartbeatJobs.remove(macAddress)
    }
    private fun stopAllDeviceHeartbeats() {
        heartbeatJobs.values.forEach { job ->
            job.cancel()
        }

        heartbeatJobs.clear()
    }

    private fun init() {
//        Executors.newSingleThreadExecutor().execute {
//            OnToCookApplication.dbInstance.recentlyPlayedDao().deleteAll()
//
//        }
        /*val assetManager = assets
        assetManager.list(Constants.STATIC_ERROR_AUDIO_PATH)?.let { list ->
            list.forEachIndexed { index, it ->
                DebugLog.e("Indexx $it $index")
            }
        }*/

        /*
                val inSamplerate = 8000

                val minBuffer = AudioRecord.getMinBufferSize(
                    inSamplerate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val builder = LameBuilder()
                    .setInSampleRate(inSamplerate)
                    .setOutChannels(1)
                    .setOutBitrate(32)
                    .setOutSampleRate(8000)
                    .setMode(mode)
                    .setQuality(quality)
                    .setVbrMode(vbrMode)
                    .setVbrQuality(vbrQuality)
                    .setScaleInput(scaleInput)
                    .setId3tagTitle(title)
                    .setId3tagAlbum(album)
                    .setId3tagArtist(artist)
                    .setId3tagYear(year)
                    .setId3tagComment(comment)
                    .setLowpassFreqency(freq)
                    .setHighpassFreqency(freq)
                    .setAbrMeanBitrate(meanBitRate)

                val androidLame = builder.build() //use this
                val androidLame1 = AndroidLame(builder)
        */
        checkPermission()
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DebugLog.e("EVENT_STOP_SCAN ${intent?.action}")
                if (intent?.action == Constants.EVENT_STOP_SCAN) {
                    DebugLog.e("EVENT_STOP_SCAN")
//                    binding.ivAddDevice.viewShow()
                    if (this@HomeActivity::dialogScanDeviceListBinding.isInitialized && dialog.isShowing) {
                        dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.OFF
                    }
                    if (!this@HomeActivity::service.isInitialized) return
                    macAddressList.withNotNull {//PrinceEWW - withNotNull condition added by PrinceEWW, for prevent index out of bound error
                        if (service.isDeviceConnected(macAddressList[binding.myViewPager2.currentItem])) {
                            binding.bleStateView1.state = BluetoothState.State.CONNECTED
                            setConnected(macAddressList[binding.myViewPager2.currentItem], true)
                        } else {
                            setConnected(macAddressList[binding.myViewPager2.currentItem], false)
                            binding.bleStateView1.state = BluetoothState.State.OFF
                        }
                    }

                    //-------On EVENT_STOP_SCAN, If any device is not currently paired but it is in pairingList of preference, we need to remove that device from pairingList of preference-------//
                    val pairedDevicesListFromPref =
                        SharedPreferencesManager.getMacAddressList(this@HomeActivity)
                    if (connectedDevicesList.size < pairedDevicesListFromPref.size) {
                        val toBeRemoved = mutableListOf<Int>() //Add index for remove from list, because during for loop we can not remove any item from its list

                        pairedDevicesListFromPref.forEachIndexed { index, pairedDeviceData ->
                            if (!connectedDevicesList.contains(pairedDeviceData)) {
                                toBeRemoved.add(index)
                            }

                            //-------When we reach last item, start operation for remove from list-------//
                            if (pairedDevicesListFromPref.last() == pairedDeviceData) {
                                toBeRemoved.sortedDescending().forEach { pairedDeviceListIndex ->
                                    val pairedDeviceData1 =
                                        pairedDevicesListFromPref[pairedDeviceListIndex]
                                    pairedDevicesListFromPref.removeAt(pairedDeviceListIndex)
                                    //-------Update list in preference, after remove last item-------//
                                    if (toBeRemoved.first() == pairedDeviceListIndex) { //Here, first item will be removed last, because we do sortedDescending before forEach

                                        //-------In case user connect single device and user turn off device and launch application, and connect another device before complete bluetooth searching, view of first device with id=1 is visible on screen-------
                                        //-------To prevent above issue, we need to hide 1st view if we found any id in pairingDeviceList in preference (pairedDevicesListFromPref), and after that update preference-------//
                                        /*if (pairedDevicesListFromPref.isNotEmpty() && !pairedDevicesListFromPref.any { it.id == 1 }){
                                            getId(1, false)
                                            val deviceWithId1 = SharedPreferencesManager.getMacAddressList(this@HomeActivity).find { it.id == 1 }
                                            showSnackBarLong(
                                                String.format(
                                                    resources.getString(R.string.dynamic_message_previously_paired_device_not_found),
                                                    deviceWithId1?.name ?: Constants.DEFAULT_EMPTY_STRING
                                                )
                                            )
                                        }*/
                                        pairedDevicesList.apply {
                                            clear()
                                            addAll(pairedDevicesListFromPref)
                                        }
                                        SharedPreferencesManager.updateMacAddressList(
                                            this@HomeActivity,
                                            ArrayList(pairedDevicesListFromPref)
                                        ) //Update list of pairingDevice in preference at last
                                    }
                                }
                            }
                        }
                    }
                    return
                }
                if (intent?.action == Constants.CHANGE_DEVICE_NAME) {
                    val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                    val macAddress = intent?.getStringExtra(Constants.MAC_ADDRESS) ?: ""
                    DebugLog.e("Message $message $macAddress")
                    run breaking@{
                        pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                            if (pairedDeviceData.macAddress == macAddress) {
                                pairedDeviceData.name =
                                    message.replace("${Constants.DEVICE_NAME_CHECK}", "", true)
                                return@breaking
                            }
                        }
                    }
                    SharedPreferencesManager.updateMacAddressList(
                        this@HomeActivity, ArrayList(pairedDevicesList)
                    )
                    var updateIndex = -1
                    run breaking@{
                        connectedDevicesList.forEachIndexed { index, pairedDeviceData ->
                            if (macAddress == pairedDeviceData.macAddress) {
                                pairedDeviceData.name =
                                    message.replace("${Constants.DEVICE_NAME_CHECK}", "", true)
                                updateIndex = index
                            }
                        }
                        if (updateIndex == -1) return@breaking
                        if (updateIndex == binding.myViewPager2.currentItem) {
                            //-------We have to show name of connected device, and if device is not connected show appBanner and hide textView-------//
                            binding.imageViewAppBanner.gone()
                            binding.tvPageTitle.visible()
                            binding.tvPageTitle.text =
                                connectedDevicesList[updateIndex].name
                        }
                        pairedDeviceAdapter.notifyItemChanged(updateIndex)
                    }
                    return
                }
                if (intent?.action == Constants.SET_DEVICE_RTCTIME) {
                    val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                    Log.e("PrinceEWW>>>", "DATETIME LOG - HomeActivity receivedFromFirmware: ${message.replace("${Constants.DATETIME}", "").toLong()}")
                    Log.e("PrinceEWW>>>", "DATETIME LOG - System.currentTimeMillis/1000: ${System.currentTimeMillis() / 1000}")
                    /*if (message.replace("${Constants.DATETIME}", "")
                            .toLong() != System.currentTimeMillis() / 1000
                    ) {

                        Toast.makeText(
                            this@HomeActivity,
                            "${resources.getText(R.string.txt_warn_utc)}",
                            Toast.LENGTH_LONG
                        ).show()
                    }*/
//                    val timeDifference = System.currentTimeMillis() / 1000  - message.replace("${Constants.DATETIME}", "").toLong()
//                    if (timeDifference == 0.toLong() || timeDifference == 1.toLong()){
//
//                    } else {
//                        Toast.makeText(
//                            this@HomeActivity,
//                            "${resources.getText(R.string.txt_warn_utc)}",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
                    return
                }
                if (intent?.action == Constants.FILE_RECEIVE_SUCCESS) {
                    LoadingUtils.hideDialog()
                    return
                }
                if (intent?.action == Constants.EVENT_LOG) {
                    when (intent?.getStringExtra(Constants.EVENT_LOG_ACTION) ?: "") {
                        Constants.LOGIDLE -> {
                            runOnUiThread { LoadingUtils.showLoading(this@HomeActivity, false) }
                            service.clearLogList(macAddressList[binding.myViewPager2.currentItem])
                        }

                        Constants.LOGBUSY -> {
                            runOnUiThread {
                                Toast.makeText(
                                    this@HomeActivity,
                                    getString(R.string.txt_busy),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    return
                }
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        val message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Log.e("TAG", "onReceive: $message")
                        Toast.makeText(this@HomeActivity, message, Toast.LENGTH_LONG).show()
                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        LoadingUtils.hideDialog()
                        binding.bleStateView1.state = BluetoothState.State.OFF

                        val mac = intent?.getStringExtra(Constants.MAC_ADDRESS)

                        Log.e("SUPABASE_STATUS", "EVENT_BLE_CONNECTION_ABORT received mac=$mac")

                        if (mac.isNullOrEmpty()) {
                            Log.e("SUPABASE_STATUS", "Disconnect failed because MAC is null")
                            return
                        }

                        service.stopTimer(mac)

                        // MOST IMPORTANT
                        markDeviceInactive(mac, "EVENT_BLE_CONNECTION_ABORT")

                        run breaking@{
                            connectedDevicesList.mapIndexed { index, pairedDeviceData ->
                                if (pairedDeviceData.macAddress == mac) {
                                    pairedDeviceData.isConnected = false

                                    if (
                                        connectedDevicesList.isNotEmpty() &&
                                        binding.myViewPager2.currentItem < connectedDevicesList.size &&
                                        connectedDevicesList[binding.myViewPager2.currentItem].macAddress == mac
                                    ) {
                                        binding.imageViewAppBanner.visible()
                                        binding.tvPageTitle.gone()
                                    }

                                    if (::pairedDeviceAdapter.isInitialized) {
                                        pairedDeviceAdapter.notifyItemChanged(index)
                                    }

                                    showSnackBarShort(
                                        String.format(
                                            resources.getString(R.string.dynamic_message_disconnected_device_no),
                                            pairedDeviceData.name
                                        )
                                    )

                                    return@breaking
                                }
                            }
                        }

                        val fragment = viewPagerAdapter.getFragmentFromMac(mac)
                        fragment.withNotNull { fragmentNotNull ->
                            val navController = (fragmentNotNull as DashboardFragment).navController
                            navController?.let { navContro ->
                                navContro.currentDestination.let { destination ->
                                    if (
                                        destination?.id == R.id.cookingFragment ||
                                        destination?.id == R.id.recipeDetailFragment
                                    ) {
                                        navController.popBackStack(R.id.homeFragment, false)
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            setToolbar(mac, true)
                                        }, 100)
                                    }
                                }
                            }
                        }

                        if (deviceHashmap.containsKey(mac)) {
                            startScan(false)
                        }
                    }

                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        LoadingUtils.hideDialog()
                        intent?.getStringExtra(Constants.MAC_ADDRESS)?.let { mac ->
                            //-------If device already previously paired, we have to set .connected = true-------//
                            var indexUpdate = -1
                            run breaking@{
                                pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                    if (pairedDeviceData.macAddress == mac) {
                                        indexUpdate = index
                                        return@breaking
                                    }
                                }
                            }

                            if (indexUpdate != -1) {
                                pairedDevicesList[indexUpdate].isConnected = true
                            }

                            var indexUpdateForConnectedList = -1
                            run breaking@{
                                connectedDevicesList.forEachIndexed { index, connectedDeviceData ->
                                    if (connectedDeviceData.macAddress == mac) {
                                        indexUpdateForConnectedList = index
                                        return@breaking
                                    }
                                }
                            }

                            if (indexUpdateForConnectedList != -1) {
                                connectedDevicesList[indexUpdateForConnectedList].isConnected = true

                                if (::pairedDeviceAdapter.isInitialized) {
                                    pairedDeviceAdapter.notifyItemChanged(indexUpdateForConnectedList)
                                }
                            }

                            // ---------- SUPABASE ACTIVE PING START ----------
                            sendDeviceStatusToServer(
                                macAddress = mac,
                                status = "active",
                                eventType = "connect"
                            )

                            startDeviceHeartbeat(mac)
                            // ---------- SUPABASE ACTIVE PING END ----------

                            intent.getStringExtra(Constants.DEVICE_NAME)?.let {
                                connectionSuccess(
                                    mac,
                                    it
                                )
                            }
                        }
                    }

                    Constants.EVENT_BLE_CONNECTION_INIT -> {

                    }

                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent!!.getParcelableExtra(
                                Constants.DEVICE, BluetoothDevice::class.java
                            )
                        } else {
                            intent!!.getParcelableExtra(
                                Constants.DEVICE
                            )
                        }
//                        DebugLog.e("EVENT_BLE_CONNECTION_FOUND_DEVICE MAc ${device!!.address}")

                        if (device != null && !deviceHashmap.containsKey(device.address)) {
                            deviceHashmap[device.address] = device
                            val pairedDeviceList =
                                SharedPreferencesManager.getMacAddressList(applicationContext)
                            var pairedDeviceItem: PairedDeviceData? = null
                            run breaking@{
                                pairedDeviceList.forEachIndexed { index, pairedDeviceData ->
                                    if (pairedDeviceData.macAddress == device.address) {
                                        pairedDeviceItem = pairedDeviceData
                                        return@breaking
                                    }
                                }
                            }
                            DebugLog.e("Device Found hashmap Not contain ${device.address}")
                            if (pairedDeviceItem != null) {
                                service.connect(device)
                                if (!connectedDevicesList.any { it.macAddress == pairedDeviceItem!!.macAddress }) {
                                    connectedDevicesList.add(pairedDeviceItem!!)
                                    if (this@HomeActivity::dialog.isInitialized && dialog.isShowing) pairedDeviceAdapter.notifyItemInserted(
                                        connectedDevicesList.size
                                    )
                                }
                            } else {
                                if (this@HomeActivity::dialog.isInitialized && dialog.isShowing) {
                                    if (!availableDevicesList.any { it.address == device.address }) {
                                        availableDevicesList.add(device)
                                        availableDevicesAdapter.notifyItemInserted(
                                            availableDevicesList.size
                                        )
                                    }
                                }
                            }
                            Log.e("PrinceEWW>>>", "availableDevicesList: $availableDevicesList")
                            Log.e("PrinceEWW>>>", "connectedDevicesList: $connectedDevicesList")
                        } else {
                            //Update
                        }
                    }
                }
            }
        }
        pairedDevicesList.addAll(SharedPreferencesManager.getMacAddressList(this@HomeActivity))
        Log.e("PrinceEWW>>>", "onINit-AfterGetDataFromPreference - pairedDevicesList $pairedDevicesList")
        //adding dummy mac address to run application without connecting to on2cook device
        addDummyMacAddress()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultLauncher.launch(intent)
    }


    private fun initListener() {
        binding.toolBar.onSafeClick {
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            /*Log.e("PrinceEWW>>>", "btnAction test pairedDevicesList: - $pairedDevicesList")
            Log.e("PrinceEWW>>>", "btnAction test connectedDevicesList: - $connectedDevicesList")
            Log.e("PrinceEWW>>>", "btnAction test macAddressList: - $macAddressList")
            Log.e("PrinceEWW>>>", "btnAction test macAddressList from preference: - ${SharedPreferencesManager.getMacAddressList(this@HomeActivity)}")*/
        }

        binding.ivAddDevice.setOnClickListener {
            openDialog = true
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            checkPermission()
        }
        val file =
            createAudioFile(
                this,
                "Today.mp3",
                Constants.AUDIO_TYPE.TIME
            )
        file?.let {
            if (!it.exists()) {
                DebugLog.e("File create ")
                it.createNewFile()
                it.setWritable(true)
            }
        }
        binding.ivMenu.setOnClickListener {
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            val popupMenu = PopupMenu(this, it)
            popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
            popupMenu.menu.findItem(R.id.java).isVisible = false
            popupMenu.setOnMenuItemClickListener { item ->
                if (item != null) {
                    when (item.itemId) {
                        R.id.addDevice -> {
                            openDialog = true
                            checkPermission()
                        }

                        R.id.log -> {
                            val mac = macAddressList[binding.myViewPager2.currentItem]
                            if (mac != Constants.DummyMacAddress) {
                                val fragment = viewPagerAdapter.getFragmentFromMac(mac)
                                if (fragment != null) {
                                    val navController =
                                        (fragment as DashboardFragment).navController
                                    navController?.let { navigationController ->
                                        if (navigationController.currentDestination != null) navigationController.navigate(
                                            R.id.logFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                                putString("device_name", connectedDevicesList.find { it.macAddress == mac }?.name ?: "S3W")  // ← ADD THIS LINE
                                            })
                                    }

                                }
                            }
                        }

                        R.id.liveLog -> {
                            val mac = macAddressList[binding.myViewPager2.currentItem]
                            if (mac != Constants.DummyMacAddress) {
                                val fragment = viewPagerAdapter.getFragmentFromMac(mac)
                                if (fragment != null) {
                                    val navController =
                                        (fragment as DashboardFragment).navController
                                    navController?.let { navigationController ->
                                        if (navigationController.currentDestination != null) navigationController.navigate(
                                            R.id.liveLogFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                            })
                                    }
                                }
                            }
                        }

                        R.id.ota -> {
                            val mac = macAddressList[binding.myViewPager2.currentItem]
                            if (mac != Constants.DummyMacAddress) {
                                val fragment = viewPagerAdapter.getFragmentFromMac(mac)
                                if (fragment != null) {
                                    val navController =
                                        (fragment as DashboardFragment).navController
                                    navController?.let { navigationController ->
                                        if (navigationController.currentDestination != null) navigationController.navigate(
                                            R.id.otaFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
                true
            }
            // Showing the popup menu
            popupMenu.show()
        }

        binding.ivFilter.setOnClickListener {
            hideFloatingMenuInRecipeFragment()
            val fragment =
                viewPagerAdapter.getFragmentFromMac(macAddressList[binding.myViewPager2.currentItem])
                    ?: return@setOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let { navigationController ->
                if (navigationController.currentDestination == null) return@setOnClickListener
                when (navigationController.currentDestination!!.id) {
                    R.id.logFragment -> {
                        (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                    }
                }
            }
        }

        binding.ivFetch.setSafeOnClickListener {
            hideFloatingMenuInRecipeFragment()
            val fragment =
                viewPagerAdapter.getFragmentFromMac(macAddressList[binding.myViewPager2.currentItem])
                    ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let { navigationController ->
                if (navigationController.currentDestination == null) return@setSafeOnClickListener
                when (navigationController.currentDestination!!.id) {
                    R.id.logFragment -> {
                        (fragment.getCurrentFragment() as LogFragment).refreshLogFiles()
                    }
                    else -> {
                        // Keep old behavior for other fragments
                        if (service.isDeviceConnected(macAddressList[binding.myViewPager2.currentItem])) {
                            service.writeData(
                                macAddressList[binding.myViewPager2.currentItem],
                                Constants.CHECKLOGSTATUS.toByteArray(Charsets.UTF_8)
                            )
                        }
                    }
                }
            }
        }

        binding.ivScan.setSafeOnClickListener {
            hideFloatingMenuInRecipeFragment()
            val fragment =
                viewPagerAdapter.getFragmentFromMac(macAddressList[binding.myViewPager2.currentItem])
                    ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let { navigationController ->
                if (navigationController.currentDestination == null) return@setSafeOnClickListener
                when (navigationController.currentDestination!!.id) {
                    R.id.logFragment -> {
                        (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                    }
                }
            }
        }

        binding.ivStop.setOnClickListener {
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            val fragment =
                viewPagerAdapter.getFragmentFromMac(macAddressList[binding.myViewPager2.currentItem])
                    ?: return@setOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let { navigationController ->
                if (navigationController.currentDestination == null) return@setOnClickListener
                when (navigationController.currentDestination!!.id) {
                    R.id.cookingFragment -> {
                        (fragment.getCurrentFragment() as CookingFragment).openCloseDialog()
                    }
                }
            }
        }

        binding.ivLeft.setOnClickListener {
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            (viewPagerAdapter.getFragment(binding.myViewPager2.currentItem) as DashboardFragment).onBackPress()
        }

        binding.bleStateView1.setOnClickListener {
            hideFloatingMenuInRecipeFragment() //On tap any view of toolbar, need to hide floating menu, if it is visible
            startScan(false)
//            service.disconnect(macAddress[binding.myViewPager2.currentItem])
        }

        binding.myViewPager2.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                try {
                    DebugLog.e("Page Selected $position")
                    if (macAddressList.isNotEmpty() && macAddressList[position] == Constants.DummyMacAddress) {
                        binding.bleStateView1.state = BluetoothState.State.OFF
                        binding.tvPageTitle.text = getString(R.string.txt_home)
                        //-------We have to show name of connected device, and if device is not connected show appBanner and hide textView-------//
                        binding.imageViewAppBanner.visible()
                        binding.tvPageTitle.gone()
                        return
                    }
                    //-------We have to show name of connected device, and if device is not connected show appBanner and hide textView-------//
                    binding.imageViewAppBanner.gone()
                    binding.tvPageTitle.visible()
                    if (connectedDevicesList.isNotEmpty()) binding.tvPageTitle.text =
                        connectedDevicesList[position].name
                    if (macAddressList.isNotEmpty()) if (service.isDeviceConnected(macAddressList[position])) {
                        setConnected(macAddressList[position], true)
                    } else {
                        setConnected(macAddressList[position], false)
                    }
                } catch (e: Exception) {
                    Log.e("Error", "onPageSelected: ")
                }

            }

            override fun onPageScrolled(
                position: Int, positionOffset: Float, positionOffsetPixels: Int
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }
        })
    }

    private fun openDialog() {
//        startScan()
        DebugLog.e("openDialog Condition ${this::dialog.isInitialized && dialog.isShowing}")
        if (this::dialog.isInitialized && dialog.isShowing) {
            startScan(false)
            return
        }
        DebugLog.e("openDialog Not Return")
        dialog = Dialog(this).apply {
            dialogScanDeviceListBinding =
                DialogScanDeviceListBinding.inflate(LayoutInflater.from(context))
            setContentView(dialogScanDeviceListBinding.root)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window!!.setLayout(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.MATCH_PARENT
            )
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            availableDevicesList.clear()
            availableDevicesAdapter = AvailableDevicesAdapter(
                availableDevicesList
            ) { pos, device ->
                CoroutineScope(Dispatchers.IO).launch {
//                    service.stopScan()
//                    runOnUiThread {
//                        if (this@HomeActivity::dialogScanDeviceListBinding.isInitialized && dialog.isShowing) {
//                            dialogScanDeviceListBinding.bleStateView.state =
//                                BluetoothState.State.OFF
//                        }
//                    }
//                    delay(100)
                    runOnUiThread {
                        LoadingUtils.showLoading(this@HomeActivity, false, "Please Wait")
                    }
                    service.connect(device)
                }
            }
            dialogScanDeviceListBinding.bleStateView.setSafeOnClickListener {
                if (dialogScanDeviceListBinding.bleStateView.state == BluetoothState.State.OFF) checkPermission()
            }
            dialogScanDeviceListBinding.ivLeft.setSafeOnClickListener {
                dialog.dismiss()
            }
            pairedDeviceAdapter = PairedDevicesAdapter(
                connectedDevicesList
            ) { id, position, pairedDevice, editedName ->
                when (id) {
                    R.id.ivDelete -> {
                        DialogUtils().commonDialog(this@HomeActivity,
                            getString(R.string.txt_delete_device),
                            "Are you sure you want to delete ${pairedDevice.name} ?",
                            getString(R.string.button_yes),
                            getString(R.string.button_no),
                            true,
                            isCancelable = true,
                            {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val pairedDeviceList =
                                        SharedPreferencesManager.getMacAddressList(
                                            applicationContext
                                        )
                                    Log.e("PrinceEWW>>>", "pairedDeviceList: $pairedDeviceList")
                                    var removeIndex = -1
                                    val prefTask = async {
                                        run breaking@{
                                            pairedDeviceList.forEachIndexed { index, pairedDeviceData ->
                                                if (pairedDeviceData.macAddress == pairedDevice.macAddress) {
                                                    removeIndex = index
                                                    return@breaking
                                                }
                                            }
                                        }
                                    }
                                    prefTask.await()
//                                    delay(300)
                                    if (removeIndex != -1) pairedDeviceList.removeAt(removeIndex)
                                    val sharedUpdate = async {
                                        SharedPreferencesManager.updateMacAddressList(
                                            this@HomeActivity, pairedDeviceList
                                        )
                                        service.disconnect(pairedDevice.macAddress)
                                        markDeviceInactive(pairedDevice.macAddress, "manual_delete_disconnect")
                                    }
                                    sharedUpdate.await()
                                    DebugLog.e("Remove Position $position")
                                    connectedDevicesList.removeAt(position)
                                    pairedDeviceList.removeIf { it.macAddress == pairedDevice.macAddress }
                                    runOnUiThread {
                                        pairedDeviceAdapter.notifyItemRemoved(position)
                                        viewPagerAdapter.remove(position, pairedDevice.macAddress)
                                    }
                                    macAddressList.remove(pairedDevice.macAddress)
                                    deviceHashmap.remove(pairedDevice.macAddress)
                                    startScan(false)
                                    //add

                                    DebugLog.e("macAddressCheking ${macAddressList.isNotEmpty()}")
                                    if (macAddressList.isNotEmpty()) return@launch
                                    runOnUiThread {
                                        addDummyMacAddress()
                                        binding.tvPageTitle.text = getString(R.string.txt_home)
                                        //-------We have to show name of connected device, and if device is not connected show appBanner and hide textView-------//
                                        binding.imageViewAppBanner.visible()
                                        binding.tvPageTitle.gone()
                                    }
                                }
                            },
                            {
                                DebugLog.e("viewPagerAdapter${viewPagerAdapter.hasStableIds()}")
                            })
                    }

                    R.id.ivEdit -> {
                        Log.e("PrinceEWW>>>", "onTapSaveForEditedName - pairedDevicesList $pairedDevicesList")
                        if (!pairedDevice.isEdit) {
                            DebugLog.e(
                                "Name Write Data ${
                                    pairedDevicesList.toTypedArray().contentToString()
                                } Edit ${pairedDevice.isEdit}"
                            )
                            DebugLog.e("Name Write Data ${pairedDevicesList.any { it.name == editedName }} Hey ${editedName}")
                            run breaking@{
                                if (pairedDevicesList.any { it.name == editedName }) {
                                    Toast.makeText(
                                        this@HomeActivity,
                                        "${resources.getText(R.string.txt_already_exits)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@breaking
                                }
                                DebugLog.e("Name Write Data ${pairedDevice.macAddress} Hey ${editedName}")
                                service.writeData(
                                    pairedDevice.macAddress,
                                    "Devicename=${editedName}".toByteArray(Charsets.UTF_8)
                                )
                            }
//                            run breaking@{
//                                pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
//                                    if (pairedDeviceData.macAddress == pairedDevice.macAddress) {
//                                        pairedDeviceData.name = pairedDevice.name
//                                        pairedDeviceData.name = pairedDevice.name
//                                        return@breaking
//                                    }
//                                }
//                            }
//                            SharedPreferencesManager.updateMacAddressList(
//                                this@HomeActivity, ArrayList(pairedDevicesList)
//                            )
//                            if (position == binding.myViewPager2.currentItem) binding.tvPageTitle.text =
//                                savedDevicesList[position].name
                        }
                        pairedDeviceAdapter.notifyItemChanged(position)
                    }
                }
            }
            dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.SEARCHING
            dialogScanDeviceListBinding.rvDevices.adapter = availableDevicesAdapter
            dialogScanDeviceListBinding.rvPairedDevices.adapter = pairedDeviceAdapter
        }
        dialog.show()

        startScan(false)
    }

    //-------On tap any view of toolbar, need to hide floating menu, if it is visible-------//
    private fun hideFloatingMenuInRecipeFragment() {
        val fragment = viewPagerAdapter.getFragment(binding.myViewPager2.currentItem)

        val navController = (fragment as DashboardFragment).navController
        DebugLog.e("2nd Check")
        navController?.let { navigationController ->
            if (navigationController.currentDestination == null) return
            DebugLog.e("3rd Check")
            navigationController.currentDestination?.let {
                when (it.id) {
                    R.id.recipeFragment -> {
                        (fragment.getCurrentFragment() as RecipeFragment).hideFloatingActionMenu() //Hide floating action menu if it is visible
                    }
                }
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e("in", "sdk version 31 and above")
            Dexter.withContext(this@HomeActivity).withPermissions(
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
                                this@HomeActivity
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
                                startService()
//                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeActivity
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
                Log.e("in", "error listener $it")
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
                                this@HomeActivity
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
                                startService()
//                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeActivity
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

    private fun startService() {
        if (!this::service.isInitialized) {
            bindService(
                Intent(this, BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
            )
        } else {
            if (openDialog) openDialog()
        }
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

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        getResult.launch(intent)
    }


    private fun addDummyMacAddress() {

        viewPagerAdapter = ViewpagerAdapter(supportFragmentManager, lifecycle)
        // add Fragments in your ViewPagerFragmentAdapter class

        binding.myViewPager2.offscreenPageLimit = 5
        // set Orientation in your ViewPager2
        binding.myViewPager2.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.myViewPager2.adapter = viewPagerAdapter
        macAddressList.add(Constants.DummyMacAddress)
//        changeDummyMac.postValue(Constants.DummyMacAddress)
        changeDummyMac.value = Constants.DummyMacAddress
        val dashboardFragment = DashboardFragment.newInstance(
            Constants.DummyMacAddress
        )
        viewPagerAdapter.addFragment(dashboardFragment, Constants.DummyMacAddress)
        DebugLog.e("addDummyMacAddress ${macAddressList.size}")
        viewPagerAdapter.notifyItemInserted(macAddressList.size)
    }

    private fun connectionSuccess(connectedMacAddress: String, name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!macAddressList.contains(connectedMacAddress)) {
                val asyncTask = async {
                    if (macAddressList[0] == Constants.DummyMacAddress) {
                        DebugLog.e("ChangeMac $connectedMacAddress")
                        DebugLog.e("EVENT_BLE_CONNECTION_SUCCESS $connectedMacAddress")
                        changeDummyMac.postValue(connectedMacAddress)
//                changeDummyMac.value = Constants.DummyMacAddress
                        viewPagerAdapter.change(connectedMacAddress)
                        runOnUiThread {
                            setConnected(connectedMacAddress, true)
                        }
                        macAddressList[0] = connectedMacAddress
                        DebugLog.e("Check3 ${connectedDevicesList.isNotEmpty()}")
                        DebugLog.e("Check4 ${connectedDevicesList.isNotEmpty() && connectedDevicesList[0].macAddress != connectedMacAddress}")
                        //if clicked 2 device & 2nd device connected first
                        if (connectedDevicesList.isNotEmpty() && connectedDevicesList[0].macAddress != connectedMacAddress) {
                            var indexUpdate = -1
                            run breaking@{
                                connectedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                    if (pairedDeviceData.macAddress == connectedMacAddress) {
                                        indexUpdate = index
                                        return@breaking
                                    }
                                }
                            }
                            DebugLog.e("Add Paired")
                            val item = connectedDevicesList.removeAt(indexUpdate)
                            item.isConnected = true
//                            if (name.contains("on2cook", true)) {
//                                service.writeData(
//                                    connectedMacAddress,
//                                    "Devicename=${item.name}".toByteArray(Charsets.UTF_8)
//                                )
//                            }
                            connectedDevicesList.add(0, item)
                        }
                    } else {
                        macAddressList.add(connectedMacAddress)
                        val dashboardFragment = DashboardFragment.newInstance(
                            connectedMacAddress
                        )
                        viewPagerAdapter.addFragment(dashboardFragment, connectedMacAddress)
                        runOnUiThread {
                            viewPagerAdapter.notifyItemInserted(macAddressList.size)
                        }
                        //if clicked 2 device & 2nd device connected first
                        DebugLog.e("Check2 ${connectedDevicesList.isNotEmpty() && connectedDevicesList.size > macAddressList.size - 1 && connectedDevicesList[macAddressList.size - 1].macAddress != connectedMacAddress}")
                        DebugLog.e("Check2 ${connectedDevicesList.isNotEmpty()}")

                        if (connectedDevicesList.isNotEmpty() && connectedDevicesList.size > macAddressList.size - 1 && connectedDevicesList[macAddressList.size - 1].macAddress != connectedMacAddress) {
                            var indexUpdate = -1
                            run breaking@{
                                connectedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                    if (pairedDeviceData.macAddress == connectedMacAddress) {
                                        indexUpdate = index
                                        return@breaking
                                    }
                                }
                            }
                            DebugLog.e("Add Paired")
                            val item = connectedDevicesList.removeAt(indexUpdate)
//                            if (name.contains("on2cook", true)) {
//                                service.writeData(
//                                    connectedMacAddress,
//                                    "Devicename=${item.name}".toByteArray(Charsets.UTF_8)
//                                )
//                            }
                            connectedDevicesList.add(macAddressList.size - 1, item)
                        }
                    }
                    if (this@HomeActivity::dialog.isInitialized && dialog.isShowing) {
                        var indexUpdate = -1
                        run breaking@{
                            availableDevicesList.forEachIndexed { index, bluetoothDevice ->
                                if (bluetoothDevice.address == connectedMacAddress) {
                                    indexUpdate = index
                                    return@breaking
                                }
                            }
                        }
                        if (indexUpdate != -1) {
                            availableDevicesList.removeAt(indexUpdate)
                            runOnUiThread {
                                availableDevicesAdapter.notifyItemRemoved(indexUpdate)
                            }
                            val device = SharedPreferencesManager.insertDevice(
                                this@HomeActivity, connectedMacAddress, name
                            )
                            DebugLog.e("DEviceNAme ${device?.name}")
                            if (device != null) {
//                                if (name.contains("on2cook", true)) {
//                                    service.writeData(
//                                        connectedMacAddress,
//                                        "Devicename=${device.name}".toByteArray(Charsets.UTF_8)
//                                    )
//                                }
                                connectedDevicesList.add(device)
                                pairedDevicesList.removeAll { it.macAddress == device.macAddress }
                                device.isConnected = true
                                pairedDevicesList.add(device)
                                Log.e("PrinceEWW>>>", "onConnectionSuccess - pairedDevicesList $pairedDevicesList")
                                runOnUiThread {
                                    pairedDeviceAdapter.notifyItemInserted(
                                        connectedDevicesList.size
                                    )
                                }
                            } else {
                                openNamingDialog(connectedMacAddress)
                            }
                        }
                    }
                }
                asyncTask.await()
                try {
                    runOnUiThread {
                        connectedDevicesList.withNotNull {
                            Log.e("PrinceEWW>>>", "connectedDevicesList: $connectedDevicesList")
                            Log.e("PrinceEWW>>>", "binding.myViewPager2.currentItem: ${binding.myViewPager2.currentItem}")
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            if (connectedDevicesList[binding.myViewPager2.currentItem].macAddress == connectedMacAddress) { //Manage visibility only if macaddress of current visible device and connected device are same
                                binding.imageViewAppBanner.gone()
                                binding.tvPageTitle.visible()
                                binding.tvPageTitle.text =
                                    connectedDevicesList[binding.myViewPager2.currentItem].name

                                //-------Need to show toast on new device connect-------//
                                showSnackBarShort(
                                    String.format(
                                        resources.getString(R.string.dynamic_message_device_connected),
                                        name
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    DebugLog.e("Showing Page Title Exception ${e.message}")
                }
            } else {
                DebugLog.e("Else Connect")
                runOnUiThread {
                    binding.bleStateView1.state = BluetoothState.State.CONNECTED
                    //--------Need to show toast, on reconnect device-------//
                    showSnackBarShort(
                        String.format(
                            resources.getString(R.string.dynamic_message_device_connected),
                            name
                        )
                    )
                    //-------We also manage visibility of app banner and title text on receive command from broadcast, but it take time so we manage visibility here also-------//
                    if (connectedDevicesList[binding.myViewPager2.currentItem].macAddress == connectedMacAddress) { //Manage visibility only if macaddress of current visible device and reconnected device are same
                        binding.imageViewAppBanner.gone()
                        binding.tvPageTitle.visible()
                    }
                }
            }
        }
        if (this::service.isInitialized && service.isDeviceConnected(connectedMacAddress))
            service.writeData(
                connectedMacAddress,
                Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
            )
    }

    private fun openNamingDialog(macAddress: String) {
        runOnUiThread {
            val editNameDialog = EditNameDialog(ArrayList(pairedDevicesList)) {
                service.writeData(
                    macAddress,
                    "Devicename=${it}".toByteArray(Charsets.UTF_8)
                )
            }
            editNameDialog.show(supportFragmentManager, "")
        }
    }

    private fun setConnected(macAddress: String, isConnected: Boolean) {
        Log.e("PrinceEWW>>>", "Set Connected function - CurrentItem of ViewPager: ${binding.myViewPager2.currentItem}")
        //-------To prevent issue of visibility of imageViewBanner & tvTitle when multiple device is connected and single device is disconnect on change viewpager-------//
        //-------We have to show name of connected device, and if device is not connected show appBanner and hide textView-------//
        binding.imageViewAppBanner.goneIfOrVisible(isConnected)
        binding.tvPageTitle.visibleIfOrGone(isConnected)
        if (isConnected) {
            binding.ivMenu.viewShow()
//            binding.ivAddDevice.viewGone() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
            binding.bleStateView1.state = BluetoothState.State.CONNECTED
//            binding.tvDisconnect.viewGone() //According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
        } else {
            binding.ivMenu.viewGone()
//            binding.ivAddDevice.viewShow() //Commented by PrinceEWW, because Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
            if (pairedDevicesList.isNotEmpty())
                binding.bleStateView1.state = BluetoothState.State.OFF
//                binding.tvDisconnect.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
        }
    }

    private fun startScan(disconnect: Boolean) {
        DebugLog.e("startScan ")
        CoroutineScope(Dispatchers.IO).launch {
            val stop = async {
                service.stopScan()
            }
            stop.await()
            deviceHashmap.clear()
            DebugLog.e("StartScan")
            delay(500)
            service.startScan()
            runOnUiThread {
//                binding.ivAddDevice.viewGone() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
                binding.bleStateView1.state = BluetoothState.State.SEARCHING
                if (this@HomeActivity::dialog.isInitialized && dialog.isShowing) {
                    availableDevicesList.clear()
                    availableDevicesAdapter.notifyDataSetChanged()
                    dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.SEARCHING
                }
            }
        }
    }

    private fun showBLEDialog() {
        if (!isBLEEnabled()) {
            Log.e("in fun", "ble enabled")
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            getResultForBLE.launch(enableIntent)
        }
    }

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

    private fun isBLEEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    private fun hasGPSDevice(context: Context): Boolean {
        val mgr =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager ?: return false
        val providers = mgr.allProviders ?: return false
        return providers.contains(LocationManager.GPS_PROVIDER)
    }

    fun setToolbar(macAddress: String, isVisible: Boolean) {
        if (isVisible) {
            binding.toolBar.viewShow()
        } else binding.toolBar.viewGone()
        if (this::service.isInitialized && service.isDeviceConnected(macAddress)) {
            binding.ivMenu.viewShow()
        } else {
//            binding.ivAddDevice.viewShow() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
        }
        DebugLog.e("Set Toolbar")
        val fragment = viewPagerAdapter.getFragmentFromMac(macAddress) ?: return
        DebugLog.e("1st Check")
        try {
            val navController = (fragment as DashboardFragment).navController
            DebugLog.e("2nd Check")
            navController?.let { navigationController ->
                if (navigationController.currentDestination == null) return
                DebugLog.e("3rd Check")
                navigationController.currentDestination?.let {
                    when (it.id) {
                        R.id.homeFragment -> {
                            binding.ivFetch.viewGone()
                            binding.ivFilter.viewGone()
                            binding.ivLeft.viewGone()
                            binding.ivStop.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.ivFetch.viewGone()
                            binding.ivFilter.viewGone()
                            binding.ivMenu.viewShow()
                            binding.ivLeft.viewShow()
                            binding.ivStop.viewShow()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.ivFetch.viewGone()
                            binding.ivFilter.viewGone()
                            binding.ivMenu.viewShow()
                            if (connectedDevicesList.isNotEmpty()) {
                                (fragment.getCurrentFragment() as RecipeDetailFragment).setTitle(
                                    connectedDevicesList[binding.myViewPager2.currentItem].name
                                )
                            }
                        }

                        R.id.logFragment -> {
//                binding.ivLog.viewGone()
                            binding.ivFetch.viewShow()
                            binding.ivFilter.viewShow()
                            binding.ivMenu.viewGone()
                            binding.ivLeft.viewShow()
                        }

                        else -> {
                            binding.ivFetch.viewGone()
                            binding.ivFilter.viewGone()
                            binding.ivMenu.viewShow()
                            binding.ivStop.viewGone()
                            binding.ivLeft.viewShow()
                        }
                    }
                }

            }

        } catch (e: Exception) {
            e.printStackTrace()
        }


    }

    //used to send data to other fragment
    internal fun findAndParseData(macAddress: String, data: Intent) {
        val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
        DebugLog.e("Message ${message.contains("${Constants.DEVICE_NAME_CHECK}", true)}")
        val fragment = viewPagerAdapter.getFragmentFromMac(macAddress) ?: return

        val navController = (fragment as DashboardFragment).navController
        navController?.let {
            if (navController.currentDestination == null) return
            navController.currentDestination?.let {
                when (it.id) {
                    R.id.homeFragment -> {
                        (fragment.getCurrentFragment() as HomeFragment).parseData(data)
                    }

                    R.id.recipeFragment -> {
                        (fragment.getCurrentFragment() as RecipeFragment).parseData(data)
                    }

                    R.id.recipeDetailFragment -> {
                        (fragment.getCurrentFragment() as RecipeDetailFragment).parseData(data)
                    }

                    R.id.cookingFragment -> {
                        (fragment.getCurrentFragment() as CookingFragment).parseData(data)
                    }
                }
            }
        }

    }

    private fun startHotspot() {

        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                DebugLog.e("Permission")
                return
            }
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    Log.e("TAG", "onStarted: $reservation")
                    Log.e("TAG", "onStarted: ${reservation.wifiConfiguration?.preSharedKey}")
                    Log.e("TAG", "onStarted: ${reservation.wifiConfiguration?.hiddenSSID}")
                    Log.e("TAG", "onStarted: ${reservation.wifiConfiguration?.SSID}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.e(
                            "TAG",
                            "onStarted: ${reservation.wifiConfiguration?.randomizedMacAddress}"
                        )
                        try {

                        } catch (e: Exception) {
                            DebugLog.e("Exception $e")
                            e.printStackTrace()
                        }

                    } else {
//                        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
//                            .setSsid(yourSsid!!)
//                            .setWpa2Passphrase(password)
//                            .build()
//
//                        val networkRequest = NetworkRequest.Builder()
//                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//                            .setNetworkSpecifier(wifiNetworkSpecifier)
//                            .build()
//
//                        connectivityManager =
//                            Boron.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
//
//                        networkCallback = object : ConnectivityManager.NetworkCallback() {
//                            override fun onUnavailable() {
//                                super.onUnavailable()
//                            }
//
//                            override fun onLosing(network: Network, maxMsToLive: Int) {
//                                super.onLosing(network, maxMsToLive)
//
//                            }
//
//                            override fun onAvailable(network: Network) {
//                                super.onAvailable(network)
//                                connectivityManager?.bindProcessToNetwork(network)
//                            }
//
//                            override fun onLost(network: Network) {
//                                super.onLost(network)
//
//                            }
//                        }
//                        connectivityManager?.requestNetwork(networkRequest, networkCallback)
                    }
                }

                override fun onStopped() {
                    super.onStopped()
                    Log.v("DANG", "Local Hotspot Stopped")
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    Log.v("DANG", "Local Hotspot failed to start")
                }
            }, Handler(Looper.getMainLooper()))
//            }
        }
    }

    private fun scanDialog() {
        deviceHashmap.clear()
        dialog = Dialog(this).apply {
            val dialogQrScanBinding: DialogQrScanBinding =
                DialogQrScanBinding.inflate(LayoutInflater.from(context))
            setContentView(dialogQrScanBinding.root)
            val codeScanner = CodeScanner(context, dialogQrScanBinding.scannerView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window!!.setLayout(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.MATCH_PARENT
            )
            codeScanner.camera = CodeScanner.CAMERA_BACK // or CAMERA_FRONT or specific camera id
            codeScanner.formats = CodeScanner.ALL_FORMATS // list of type BarcodeFormat,
            // ex. listOf(BarcodeFormat.QR_CODE)
            codeScanner.autoFocusMode = AutoFocusMode.SAFE // or CONTINUOUS
            codeScanner.scanMode = ScanMode.SINGLE // or CONTINUOUS or PREVIEW
            codeScanner.isAutoFocusEnabled = true // Whether to enable auto focus or not
            codeScanner.isFlashEnabled = false // Whether to enable flash or not

            // Callbacks
            codeScanner.decodeCallback = DecodeCallback {
                service.connectMac(it.text)
                runOnUiThread {
                    Toast.makeText(context, "Scan result: ${it.text}", Toast.LENGTH_LONG).show()
                }
            }
            codeScanner.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
                runOnUiThread {
                    Toast.makeText(
                        context, "Camera initialization error: ${it.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }

            dialogQrScanBinding.scannerView.setOnClickListener {
                codeScanner.startPreview()
            }
        }
        dialog.show()
    }

    private fun unZipAudio(zipFile: File, location: String) {
        try {
            DebugLog.e("unzipNew: $location Name:- ${zipFile.name}")
            val f = File(location)
            if (!f.isDirectory) {
                f.mkdirs()
            }
            val zin = ZipInputStream(FileInputStream(zipFile))
            zin.use { zin ->
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {
                    DebugLog.e("unZipNew: ${ze!!.name.contains(".zip")}")
                    val path = File(location, ze!!.name)
                    if (ze!!.isDirectory) {
                        val unzipFile = File(location + ze!!.name)
                        if (!unzipFile.isDirectory) {
                            unzipFile.mkdirs()
                        }
                    } else {
                        val fout = FileOutputStream(path, false)
                        fout.use { fout ->
                            var c = zin.read()
                            while (c != -1) {
                                fout.write(c)
                                c = zin.read()
                            }
                            zin.closeEntry()
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            DebugLog.e("Unzip exception $e")
        }
    }

    private fun startRecording(qtyFile: File?) {
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
//        mediaRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            MediaRecorder(this)
//        } else {
//            MediaRecorder()
//        }

        mediaRecord?.let {
            it.setAudioSource(MediaRecorder.AudioSource.MIC) // Set audio source (change as needed)
            it.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB) // Set output format (change as needed)
            it.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) // Set audio encoder (change as needed)
            it.setOutputFile(qtyFile)
            it.setAudioEncodingBitRate(44100)
            it.setVideoEncodingBitRate(3000 * 1000)
            it.prepare()
            it.start()
        }
//        val cmd = arrayOf(
//            "-i", "input_file.mp4",  // Replace with your recorded file path
//            "-codec:a", "libmp3lame",  // MP3 codec
//            "-q:a", "2",  // Audio quality (0-9, 2 is good quality)
//            "output_file.mp3" // Output MP3 file path
//        )

//        FFmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
//            fun onFailure(message: String) {
//                Log.e(TAG, "FFmpeg conversion failed: $message")
//            }
//
//            fun onSuccess(message: String) {
//                Log.d(TAG, "FFmpeg conversion successful: $message")
//            }
//        })
    }

    private fun stopRecording(file: File?) {
        DebugLog.e("File SizeConverting ${file!!.length()}")
        Toast.makeText(this, "Stoped", Toast.LENGTH_SHORT).show()
        mediaRecord?.let {
            try {
                it.stop()
            } catch (stopException: RuntimeException) {
                DebugLog.e("File Size stop Exception $stopException")
            }
            it.release()
        }
    }

    private fun stopRecordingNew(file: File?) {
        DebugLog.e("File SizeConverting ${file!!.length()}")
        Toast.makeText(this, "Stoped", Toast.LENGTH_SHORT).show()
        mediaRecord?.let {
            try {
                it.stop()
            } catch (stopException: RuntimeException) {

            }
            it.release()
        }

//        TagOptionSingleton.getInstance().isAndroid = true
//        val audioFile = AudioFileIO.read(file)
//        val newTag: Tag = audioFile.tag
//        newTag.setField(FieldKey.ALBUM, "October")
//        newTag.setField(FieldKey.ARTIST, "U2")
//        audioFile.commit()
//        val contentValues: ContentValues = ContentValues().apply {
//            put(Audio.Media.TITLE, "Today")
//            put(Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
//            put(Audio.Media.MIME_TYPE, "audio/amr")
//            put(Audio.Media.DATA, file.name)
//        }
//        val uri = contentResolver.insert(Audio.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE), uri)
    }
}