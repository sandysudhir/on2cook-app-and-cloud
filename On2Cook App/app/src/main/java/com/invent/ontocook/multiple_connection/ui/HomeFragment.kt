package com.invent.ontocook.multiple_connection.ui

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.polly.AmazonPollyClient
import com.amazonaws.services.polly.AmazonPollyPresigningClient
import com.amazonaws.services.polly.model.DescribeVoicesRequest
import com.amazonaws.services.polly.model.OutputFormat
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest
import com.amazonaws.services.polly.model.Voice
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentHomeBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.multiple_connection.CloudWebActivity
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.openPermissionSettings
import com.invent.ontocook.utils.withNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream


private const val ARG_PARAM1 = Constants.MAC_ADDRESS
private const val ARG_PARAM2 = "param2"


private enum class OrderModuleTab { ORDERS, DEVICES, RECIPES, MORE }
private enum class OrderListMode { CURRENT, PREVIOUS }
private enum class OrderStatus { COOKING, PENDING, COMPLETED, SHIPPED, FAILED, CANCELLED }
private enum class DeviceState { COOKING, IDLE, QUEUED, OFFLINE }

private data class KitchenDevice(
    val number: Int,
    val name: String,
    val state: DeviceState,
    val currentItem: String = "",
    val remaining: String = "--:--",
    val power: String = "0%",
    val queueCount: Int = 0,
    val isFryDevice: Boolean = false,
    val macAddress: String = "",
    val liveMode: String = "",
    val liveStep: String = ""
)

private data class KitchenOrderItem(
    val orderId: String,
    val itemName: String,
    val itemCode: String,
    val quantity: String,
    val portion: String,
    val source: String,
    val orderType: String,
    val timeAgo: String,
    val customer: String,
    val total: String,
    val accentColor: String,
    var status: OrderStatus,
    val recipe: String,
    val specialInstruction: String = "",
    var assignedDevice: String = "",
    var remainingTime: String = "",
    val imageRes: Int = R.drawable.ic_recent_item
)

private data class DeviceRuntimeStatus(
    val macAddress: String,
    var connected: Boolean = true,
    var cooking: Boolean = false,
    var recipeName: String = "",
    var mode: String = "Idle",
    var stepNo: Int = 0,
    var indSeconds: Int = 0,
    var magSeconds: Int = 0,
    var indPower: Int = 0,
    var magPower: Int = 0,
    var status: String = "IDLE",
    var stirrer: String = "",
    var pumpOn: Boolean = false,
    var lastMessage: String = "",
    var lastUpdated: Long = System.currentTimeMillis()
)

private data class ModePresetStep(
    val stepNumber: Int,
    var inductionPower: Int,
    var inductionSeconds: Int,
    var microwavePower: Int,
    var microwaveSeconds: Int,
    var powerDropAfterSeconds: Int,
    var powerAfterDrop: Int,
    var stirrerSpeed: String = "Off",
    var pumpSeconds: Int = 0,
    var sprayMl: Int = 0,
    var thresholdTemp: Int = 0,
    var waitRestSeconds: Int = 0,
    var lidStatus: String = "Closed"
)

private data class ModePreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val steps: MutableList<ModePresetStep>,
    val isDeviceMode: Boolean = true
)

class HomeFragment : Fragment() {

    private var recipeScreenFlowTypeForNavigation: Constants.RecipeFragmentScreenFlowType? = null

    private var macAddress: String = ""
    private var param2: String? = null
    private lateinit var binding: FragmentHomeBinding
    private var fabOpen: Animation? = null
    private var fabClose: Animation? = null
    private var fabClock: Animation? = null
    private var fabAntiClock: Animation? = null
    lateinit var service: BleService
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var list: ArrayList<Recipe>
    private lateinit var communicationReceiver: BroadcastReceiver
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
            if (service.isDeviceConnected(macAddress)) {
                //Commented by PrinceEWW, because no need to check status on connect service, because on status idle we navigate user to recipeFragment
//                service.writeData(macAddress, Constants.STATUS.toByteArray(Charsets.UTF_8))
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = ArrayList()
        OnToCookApplication.dbInstance.recipeDao().getAllRecipe1().subscribe({
            list.addAll(it)
        }, {

        })
        arguments?.let {
            macAddress = it.getString(ARG_PARAM1, "")
            param2 = it.getString(ARG_PARAM2)
        }


        activity?.bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
        if (macAddress == Constants.DummyMacAddress)
            if (Constants.IS_TABLET)
                (activity as HomeTvActivity).changeDummyMac.observe(
                    requireActivity()
                ) {
                    DebugLog.e("changeDummyMac $it")
                    macAddress = it
//                    if (this::service.isInitialized && service.isDeviceConnected(macAddress))
//                        service.writeData(
//                            macAddress,
//                            Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
//                        )
                }
            else
                (activity as HomeActivity).changeDummyMac.observe(
                    requireActivity()
                ) {
                    DebugLog.e("changeDummyMac ${Constants.setDateTimeCommand()} ${this::service.isInitialized}")
                    macAddress = it
//                    if (this::service.isInitialized && service.isDeviceConnected(macAddress))
//                        service.writeData(
//                            macAddress,
//                            Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
//                        )
                }
        CoroutineScope(Dispatchers.IO).launch {
//            amazonPolly()
        }

    }

    fun amazonPolly() {
        val COGNITO_POOL_ID = "us-east-1:e056c544-88f5-446c-b05b-73b1f14d3662"

// Region of Amazon Polly.

// Region of Amazon Polly.
        val MY_REGION = Regions.US_EAST_1

// Initialize the Amazon Cognito credentials provider.

// Initialize the Amazon Cognito credentials provider.
        val credentialsProvider = CognitoCachingCredentialsProvider(
            requireContext(),
            COGNITO_POOL_ID,
            MY_REGION
        )

// Create a client that supports generation of presigned URLs.

// Create a client that supports generation of presigned URLs.
        val client = AmazonPollyPresigningClient(credentialsProvider)

        Log.e("TAG", "amazonPolly: ${client.serviceName}")
        val describeVoicesRequest = DescribeVoicesRequest()
        describeVoicesRequest.languageCode = "en-US";
// Synchronously ask Amazon Polly to describe available TTS voices.
        Log.e("TAG", "amazonPolly: ${client.serviceName}")

// Synchronously ask Amazon Polly to describe available TTS voices.
        val describeVoicesResult = client.describeVoices(describeVoicesRequest)
//        Log.e("TAG", "amazonPolly:11 ${client.describeVoices(describeVoicesRequest)}")
        val voices: List<Voice> = describeVoicesResult.voices
        Log.e("TAG", "amazonPolly: Size ${voices.size}")
        val synthesizeSpeechPresignRequest: SynthesizeSpeechPresignRequest =
            SynthesizeSpeechPresignRequest() // Set the text to synthesize.
                .withText("Hello world!") // Select voice for synthesis.
                .withVoiceId(voices[0].id) // "Joanna"
                // Set format to MP3.
                .withOutputFormat(OutputFormat.Mp3)

// Get the presigned URL for synthesized speech audio stream.

// Get the presigned URL for synthesized speech audio stream.
        val presignedSynthesizeSpeechUrl =
            client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest)
        Log.e("TAG", "amazonPolly:File $presignedSynthesizeSpeechUrl")
        Log.e("TAG", "amazonPolly:Bytes ${presignedSynthesizeSpeechUrl.readBytes()}")
        Log.e("TAG", "amazonPolly:File ${presignedSynthesizeSpeechUrl.file}")
        val `is` = presignedSynthesizeSpeechUrl.openStream()

        val dis = DataInputStream(`is`)

        val buffer = ByteArray(1024)
        var length: Int
        val root = File(requireContext().externalCacheDir, "Audio")
        if (!root.exists())
            root.mkdir()
        val fos = FileOutputStream(root)
        while (dis.read(buffer).also { length = it } > 0) {
            fos.write(buffer, 0, length)
        }
        dis.close()
        fos.close()
        Log.e("TAG", "amazonPolly:File ${presignedSynthesizeSpeechUrl.openStream()}")
        Log.e("TAG", "amazonPolly: $presignedSynthesizeSpeechUrl")
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

        try {
            // Set media player's data source to previously obtained URL.
            mediaPlayer.setDataSource(presignedSynthesizeSpeechUrl.toString())
        } catch (e: IOException) {
            Log.e("TAG", "Unable to set data source for the media player! " + e.message)
        }

// Prepare the MediaPlayer asynchronously (since the data source is a network stream).

// Prepare the MediaPlayer asynchronously (since the data source is a network stream).
        mediaPlayer.prepareAsync()

// Set the callback to start the MediaPlayer when it's prepared.

// Set the callback to start the MediaPlayer when it's prepared.
        mediaPlayer.setOnPreparedListener { mp ->
            DebugLog.e("amazonPolly: ${mp.duration}")
            mp.start()
        }

// Set the callback to release the MediaPlayer after playback is completed.

// Set the callback to release the MediaPlayer after playback is completed.
        mediaPlayer.setOnCompletionListener { mp ->
            DebugLog.e("amazonPolly: ${mp.duration}")
            mp.release()
        }
//    CoroutineScope(Dispatchers.IO).launch {
//        try {
//            /*  val clientConfiguration = ClientConfiguration()
//              val also = 10.also { clientConfiguration.maxErrorRetry = it }
//              clientConfiguration.connectionTimeout =
//                  5 * 60 * 1000 // default is 10 setCurrentBleState
//              clientConfiguration.socketTimeout = 5 * 60 * 1000 // default is 50 secs
//              // Initialize the Amazon Cognito credentials provider.
//              // Initialize the Amazon Cognito credentials provider.
//
//              //o2c
////                    val basicAWSCredentials = BasicAWSCredentials(
////                        "AKIAX6MDCCR2GA6L4YU3", "oyqqJoQfI2m7Qkuuv2AGagZ4HQ8UocPWx27ULOT8"
////                    )
//              //praveen
//              val basicAWSCredentials = BasicAWSCredentials(
//                  "AKIA2S23KD267CX23GS5", "tRwYCVhA60oaJA8UxwQu2YG3KAATf8oT1TLtqjm5"
//              )
//
////                    val client = AmazonPollyPresigningClient(AWSCredentialsProvider)
//              // Create a client that supports generation of presigned URLs.
//              // Create a client that supports generation of presigned URLs.
//              val client = AmazonPollyClient(basicAWSCredentials)
//              val describeVoicesRequest = DescribeVoicesRequest()
//
//// Synchronously ask Amazon Polly to describe available TTS voices.
//
//// Synchronously ask Amazon Polly to describe available TTS voices.
//              val describeVoicesResult = client.describeVoices(describeVoicesRequest)
//              val voices: List<Voice> = describeVoicesResult.voices
//              DebugLog.e("client  ${voices.size}")*/
//            val accessKey = "AKIAX6MDCCR2BBSHV4DV"
//            val secretKey = "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"
//            val credentials = BasicAWSCredentials(accessKey, secretKey)
//
//            val pollyClient = AmazonPollyClient(credentials)
//// Perform text-to-speech synthesis
//            pollyClient.setRegion(Region.getRegion(Regions.US_EAST_1))
//// Perform text-to-speech synthesis
//            val textToSynthesize = "Hello, world!"
//            val synthesizeSpeechRequest = SynthesizeSpeechRequest()
//                .withText(textToSynthesize)
//                .withVoiceId("Joanna") // Choose the desired voice
//                .withOutputFormat("mp3") // Specify the desired audio format
//
//
//            val synthesizeSpeechResult =
//                pollyClient.synthesizeSpeech(synthesizeSpeechRequest)
//
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//
//    }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.e("destroy")

        if (Constants.IS_TABLET)
            (activity as HomeTvActivity).changeDummyMac.removeObservers(this)
        else
            (activity as HomeActivity).changeDummyMac.removeObservers(this)
        activity?.unbindService(
            mConnection
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding =
            DataBindingUtil.inflate(
                layoutInflater,
                R.layout.fragment_home,
                container,
                false
            )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
        setupOrderModuleTab()
    }

    fun set() {
//        if (isBluetoothOn() && service.isDeviceConnected(macAddress!!)) {
//            (requireActivity() as HomeActivity).isConnected(true)
//            binding.tvStatus.text = RxBleConnection.RxBleConnectionState.CONNECTED.name
//            Log.e(TAG, "setCurrentBleState: CONNECTED")
//            binding.btnConnect.visibility = View.GONE
//        } else {
//            if (isBluetoothOn()) {
//                binding.tvStatus.text = RxBleConnection.RxBleConnectionState.DISCONNECTED.name
//                binding.btnConnect.visibility = View.VISIBLE
//            }
//            Log.e(TAG, "setCurrentBleState: CONNECTED")
//        }
    }

    override fun onResume() {
        super.onResume()
        DebugLog.e("onResume")
        if (Constants.IS_TABLET)
            (requireActivity() as HomeTvActivity).setToolbar(macAddress, true)
        else (requireActivity() as HomeActivity).setToolbar(macAddress, true)
        initializeService() //PrinceEWW
        context?.let {
            LocalBroadcastManager.getInstance(it).registerReceiver(
                broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION)
            )
        }
        context?.let {
            LocalBroadcastManager.getInstance(it).registerReceiver(
                communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(communicationReceiver)
    }

    private fun init() {
//        binding.guidelineLeft.setGuidelinePercent(0.36f)
//        binding.guidelineRight.setGuidelinePercent(0.64f)
        //TODO Only For Testing

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {

                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        DebugLog.e("EVENT_BLE_CONNECTION_ABORT")
                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val eventMac = intent.getStringExtra(Constants.MAC_ADDRESS).orEmpty()
                val action = intent.getStringExtra(Constants.EVENT_BLE_ACTION).orEmpty()
                if (action == Constants.EVENT_BLE_NOTIFICATION) {
                    updateLiveDeviceStatusFromMessage(eventMac, intent.getStringExtra(Constants.EVENT_MESSAGE).orEmpty())
                }
                if (action == Constants.FILE_UPLOAD_SUCCESS && pendingOrderRecipeRunByMac.containsKey(eventMac)) {
                    handleOrderRecipeUploadSuccess(eventMac)
                    return
                }
                if (eventMac == macAddress) {
                    parseData(intent)
                }
            }
        }


//        Log.e("Homef", "init: Home macAddress$macAddress" )
//        if (macAddress == "123") {
//            val bundle = Bundle().apply {
//                putSerializable(Constants.RECIPE, Constants.RECIPES[0])
//                putString(Constants.MAC_ADDRESS, macAddress)
//                putBoolean(Constants.IS_PREPARE_RUNNING, false)
//                putBoolean(Constants.IS_RESUME, false)
//            }
//            findNavController().navigate(
//                R.id.cookingFragment2, bundle
//            )
//        } else {
//            val bundle = Bundle().apply {
//                putSerializable(Constants.RECIPE, Constants.RECIPES[1])
//                putString(Constants.MAC_ADDRESS, macAddress)
//                putBoolean(Constants.IS_PREPARE_RUNNING, false)
//                putBoolean(Constants.IS_RESUME, true)
//                putString(Constants.MAC_ADDRESS, macAddress)
//                putInt(Constants.CURRENT_STEP, 2)
//                putInt(
//                    Constants.CHANGE_TIME,
//                    40
//                )
//                putBoolean(
//                    Constants.IS_PLAYING, true
//                )
//            }
//            findNavController().navigate(
//                R.id.cookingFragment, bundle
//            )
//        }
        DebugLog.e(macAddress)
        fabClose = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_close)
        fabOpen = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_open)
        fabClock = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_rotate_clock)
        fabAntiClock = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_rotate_anticlock)
    }

    private fun initListener() {
        binding.clCook.setOnClickListener {
            recipeScreenFlowTypeForNavigation = Constants.RecipeFragmentScreenFlowType.MANUAL_MODE
            /*findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToRecipeFragment(
                    macAddress,  recipeScreenFlowTypeForNavigation?.type ?: Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type
                )
            )*/
            if (!::service.isInitialized) {
                initializeService(binding.clFryMode)
                navigateToRecipeFragment()
                return@setOnClickListener
            }

            if (service.isDeviceConnected(macAddress)) {
                service.writeData(
                    macAddress, Constants.STATUS.toByteArray(
                        Charsets.UTF_8
                    )
                )
            } else {
                navigateToRecipeFragment()
            }
        }
        binding.clFryMode.setOnClickListener {
            recipeScreenFlowTypeForNavigation =
                Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE
            if (!::service.isInitialized) {
                initializeService(binding.clFryMode)
                navigateToRecipeFragment()
                return@setOnClickListener
            }

            if (service.isDeviceConnected(macAddress)) {
                service.writeData(
                    macAddress, Constants.STATUS.toByteArray(
                        Charsets.UTF_8
                    )
                )
            } else {
                navigateToRecipeFragment()
            }
        }
        binding.btnFtp.setSafeOnClickListener {

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
//                    .setSsid("TP-Link_EWW")
//                    .setWpa2Passphrase("EWW@#123890")
//                    .build()
//                val networkRequest: NetworkRequest = NetworkRequest.Builder()
//                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//                    .setNetworkSpecifier(wifiNetworkSpecifier)
//                    .build()
//                val connectivityManager = requireContext().getSystemService(
//                    Context.CONNECTIVITY_SERVICE
//                ) as ConnectivityManager
//                connectivityManager.requestNetwork(networkRequest, object :
//                    NetworkCallback() {
//                    override fun onAvailable(network: Network) {
//                        super.onAvailable(network)
//                        DebugLog.e("requestNetwork onAvailable $network")
//                    }
//
//                    override fun onLosing(network: Network, maxMsToLive: Int) {
//                        super.onLosing(network, maxMsToLive)
//                        DebugLog.e("requestNetwork onLosing $network")
//
//                    }
//
//                    override fun onLost(network: Network) {
//                        super.onLost(network)
//                        DebugLog.e("requestNetwork onLost $network")
//                    }
//
//                    override fun onUnavailable() {
//                        super.onUnavailable()
//                        DebugLog.e("requestNetwork onUnavailable ")
//                    }
//
//                    override fun onCapabilitiesChanged(
//                        network: Network,
//                        networkCapabilities: NetworkCapabilities
//                    ) {
//                        super.onCapabilitiesChanged(network, networkCapabilities)
//                        DebugLog.e("requestNetwork onCapabilitiesChanged $network")
//                    }
//
//                    override fun onLinkPropertiesChanged(
//                        network: Network,
//                        linkProperties: LinkProperties
//                    ) {
//                        super.onLinkPropertiesChanged(network, linkProperties)
//                        DebugLog.e("requestNetwork onLinkPropertiesChanged $network")
//                    }
//
//                    override fun onBlockedStatusChanged(
//                        network: Network,
//                        blocked: Boolean
//                    ) {
//                        super.onBlockedStatusChanged(network, blocked)
//                        DebugLog.e("requestNetwork onBlockedStatusChanged $network")
//                    }
//                })
//
//            }
            val permissionList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            } else {
                arrayListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            PermissionManagerUtils.checkPermission(
                requireContext(),
                requireActivity(),
                permissionList,
                PermissionManagerUtils.PermissionSessionManager(requireActivity()),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            permissionList.toTypedArray(),
                            Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            permissionList.toTypedArray(),
                            1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------HERE OPEN
                        DialogUtils().commonDialog(
                            context = requireContext(),
                            title = getString(R.string.title_permission_open_settings),
                            message = getString(R.string.message_storage_camera_permission),
                            positiveButton = getString(R.string.button_setting),
                            negativeButton = getString(R.string.button_cancel),
                            isAppLogoDisplay = true,
                            isCancelable = true,
                            callbackSuccess = {
                                //------Positive CallBack----//
                                context?.openPermissionSettings()
                            },
                            callbackNegative = {
                                //---------Negative CallBack----------//
                            })
                    }

                    override fun onPermissionGranted() {
                        startHotspot()
                    }

                })
        }
        binding.btnPolly?.setSafeOnClickListener {

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    /*  val clientConfiguration = ClientConfiguration()
                      val also = 10.also { clientConfiguration.maxErrorRetry = it }
                      clientConfiguration.connectionTimeout =
                          5 * 60 * 1000 // default is 10 setCurrentBleState
                      clientConfiguration.socketTimeout = 5 * 60 * 1000 // default is 50 secs
                      // Initialize the Amazon Cognito credentials provider.
                      // Initialize the Amazon Cognito credentials provider.

                      //o2c
  //                    val basicAWSCredentials = BasicAWSCredentials(
  //                        "AKIAX6MDCCR2GA6L4YU3", "oyqqJoQfI2m7Qkuuv2AGagZ4HQ8UocPWx27ULOT8"
  //                    )
                      //praveen
                      val basicAWSCredentials = BasicAWSCredentials(
                          "AKIA2S23KD267CX23GS5", "tRwYCVhA60oaJA8UxwQu2YG3KAATf8oT1TLtqjm5"
                      )

  //                    val client = AmazonPollyPresigningClient(AWSCredentialsProvider)
                      // Create a client that supports generation of presigned URLs.
                      // Create a client that supports generation of presigned URLs.
                      val client = AmazonPollyClient(basicAWSCredentials)
                      val describeVoicesRequest = DescribeVoicesRequest()

  // Synchronously ask Amazon Polly to describe available TTS voices.

  // Synchronously ask Amazon Polly to describe available TTS voices.
                      val describeVoicesResult = client.describeVoices(describeVoicesRequest)
                      val voices: List<Voice> = describeVoicesResult.voices
                      DebugLog.e("client  ${voices.size}")*/
                    val accessKey = "AKIA2S23KD267CX23GS5"
                    val secretKey = "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"
                    val credentials = BasicAWSCredentials(accessKey, secretKey)

                    val pollyClient = AmazonPollyClient(credentials)
// Perform text-to-speech synthesis
                    pollyClient.setRegion(Region.getRegion(Regions.US_EAST_1))
// Perform text-to-speech synthesis
                    val textToSynthesize = "Hello, world!"
                    val synthesizeSpeechRequest = SynthesizeSpeechRequest()
                        .withText(textToSynthesize)
                        .withVoiceId("Joanna") // Choose the desired voice
                        .withOutputFormat("mp3") // Specify the desired audio format


                    val synthesizeSpeechResult =
                        pollyClient.synthesizeSpeech(synthesizeSpeechRequest)


                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }
        binding.btnFtpStop.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (this::reservationG.isInitialized)
                    reservationG.close()
            }
            service.writeData(
                macAddress,
                ("ssid=${ssid},password=${pass},status=off").toByteArray(
                    Charsets.UTF_8
                )
            )
        }

    }

    var pass = ""
    lateinit var reservationG: WifiManager.LocalOnlyHotspotReservation
    var ssid = ""
    private fun startHotspot() {

        val wifiManager = requireActivity().getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    DebugLog.e("Permission")
                    return
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    DebugLog.e("Permission")
                    return
                }
            }
            val cls = Class.forName("android.net.wifi.WifiManager")
            DebugLog.e("is5GhzSupported: ${wifiManager.is5GHzBandSupported}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DebugLog.e("is24GHzBandSupported: ${wifiManager.is24GHzBandSupported}")
            }
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    reservationG = reservation
                    DebugLog.e("onStarted: $reservation")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.preSharedKey}")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.hiddenSSID}")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.SSID}")
                    try {
                        ssid = reservation.wifiConfiguration?.SSID!!
                        pass = reservation.wifiConfiguration?.preSharedKey!!
                        service.writeData(
                            macAddress,
                            ("ssid=${reservation.wifiConfiguration?.SSID},password=${reservation.wifiConfiguration?.preSharedKey},status=on").toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } catch (e: Exception) {
                        DebugLog.e("Exception $e")
                        e.printStackTrace()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        DebugLog.e("softApConfiguration${reservation.softApConfiguration}")
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
        }
    }

    private fun initializeService(imgPumpPower: View? = null) {
        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    internal fun parseData(intent: Intent) {
        when (intent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.FILE_UPLOAD_SUCCESS -> {
                handleModePresetUploadSuccess(intent.getStringExtra(Constants.MAC_ADDRESS).orEmpty())
            }
            Constants.EVENT_BLE_WRITE_FAIL -> {
                Toast.makeText(requireContext(), "Device write failed. Please check BLE connection.", Toast.LENGTH_SHORT).show()
            }
            Constants.EVENT_BLE_NOTIFICATION -> {
//                val mac = intent.getStringExtra(Constants.MAC_ADDRESS)
                val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                if (message.lowercase().contains("info=")) {
                    val messageChunk =
                        message.lowercase().toString().replace("info=", "").split(",")

                    println("receive message..... $message")

                    val recipeNo = messageChunk[0]
                    val ingredient = if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                    val stepno = if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                    val second = if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                    val foundRecipe = Constants.RECIPES.filter { it.id.toString() == recipeNo }
                    if (foundRecipe.isNotEmpty()) {
                        val intent = Intent(requireContext(), OldDashboardActivity::class.java)
                        intent.putExtra("recipe", foundRecipe[0])
                        intent.putExtra("prepreparestep", ingredient)
                        intent.putExtra("currentstep", stepno)
                        intent.putExtra("remainTime", second)
                        startActivity(intent)
                    }
                }
//                if (message.contains(Constants.DATETIME, true)) {
//                    if (message.replace(
//                            Constants.DATETIME,
//                            ""
//                        ).toInt() < System.currentTimeMillis() / 1000
//                    ) {
//                        Toast.makeText(
//                            requireContext(),
//                            "${requireContext().resources.getText(R.string.txt_warn_utc)}",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                    service.writeData(macAddress, Constants.STATUS.toByteArray(Charsets.UTF_8))
//                    return
//                }
                /*if (message.uppercase().contains("HOST=")) {
                    Toast.makeText(requireContext(), "Connected Host", Toast.LENGTH_SHORT).show()
                    val ftpClient = MyFTPClientFunctions()
                    CoroutineScope(Dispatchers.IO).launch {
                        ftpClient.ftpConnect(message.replace("HOST=", ""), "ftp", "ftp", 21)
                        delay(100)
                        DebugLog.e("Handler CurrentWorking ${ftpClient.ftpGetCurrentWorkingDirectory()}")
                        delay(100)
//                        ftpClient.ftpPrintAllFilesList()
                        delay(500)
                        DebugLog.e(
                            "Handler ftpChangeDirectory ${
                                ftpClient.ftpChangeDirectory(
                                    "audio"
                                )
                            }"
                        )
                        delay(100)
//                        ftpClient.ftpPrintAllFilesList()

//                        delay(800)
//                        val check = ftpClient.checkFileExists("Water.mp3")
//                        DebugLog.e(
//                            "Handler checkFileExists Operation$check"
//                        )
//                        if (check) {
//                            delay(100)
//
//                            val checkDelete =
//                                ftpClient.mFTPClient.deleteFile("Water.mp3")
//                            DebugLog.e(
//                                "Handler checkDelete Operation$checkDelete"
//                            )
//                        }
//                        delay(200)
//
//                        val check2 = ftpClient.checkFileExists("Vinegar.mp3")
//                        DebugLog.e(
//                            "Handler checkFileExists Operation$check2"
//                        )
//                        if (check2) {
//                            delay(200)
//
//                            val checkDelete =
//                                ftpClient.mFTPClient.deleteFile("Vinegar.mp3")
//
//                            DebugLog.e(
//                                "Handler checkDelete Operation$checkDelete"
//                            )
//                        }
                        delay(1000)
                        val root =
                            File(OnToCookApplication.instance.externalCacheDir, "Files")
                        DebugLog.e("Checking ${root.exists()}")
                        val zipFile = File(root, "ChilliPaneerSamosa.mp3")
                        val inputStream = FileInputStream(zipFile)
                        val outputFile = File(root, "ChilliPaneerSamosa1.mp3")
                        val outputStream = FileOutputStream(outputFile)
                        var read = 0
                        val maxBufferSize = 1 * 1024 * 1024
                        val bytesAvailable = inputStream!!.available()

                        //int bufferSize = 1024;
                        val bufferSize = bytesAvailable.coerceAtMost(maxBufferSize)
                        val buffers = ByteArray(bufferSize)
                        while (inputStream.read(buffers).also { read = it } != -1) {
                            outputStream.write(buffers, 0, read)
                        }
                        inputStream.close()
                        outputStream.close()
                        delay(3000)
//                        ftpClient.ftpUploadFile(outputFile, "ChilliPaneerSamosa1.mp3")
                        DebugLog.e("ftpUploadFile Path${outputFile.path}")

                        val status = ftpClient.ftpUpload(
                            outputFile,
                            "ChilliPaneerSamosa1.mp3",
                            "audio",
                            requireContext()
                        )
                        DebugLog.e("ftpUploadFile $status")

                        delay(3000)
                        ftpClient.ftpPrintAllFilesList()
                    }

                }*/


                if (message.uppercase() == Constants.MAGQUICKSTART || message.uppercase() == Constants.INDQUICKSTART || (message.uppercase()
                        .contains("INDQUICKSTART=") && Constants.checkNavigation(message))
                ) {
                    //-------We set condition below for navigation-------//
                    /*if (findNavController().currentDestination?.id != R.id.recipeFragment)
                        recipeScreenFlowTypeForNavigation.withNotNull {
                            findNavController().navigate(
                                HomeFragmentDirections.actionHomeFragmentToRecipeFragment(
                                    macAddress, it.type
                                )
                            )
                        }*/
                }
                if (message.lowercase().contains("recipe=")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val foundRecipe = list.filter {
                            it.name[0] == Constants.getRecipeNameFromCommand(message)
                        }
                        DebugLog.e("getMode ${Constants.getMode(message)}")
                        if (Constants.getMode(message))
                            return@launch
                    if (foundRecipe.isEmpty()) {
                        val foundRecipe = Constants.RECIPES.filter {
                            it.name == Constants.getRecipeNameFromCommand(message)
                        }
                        if (foundRecipe.isNotEmpty()) {
                            findNavController().navigate(
                                R.id.cookingFragment,
                                Constants.getBundleFromCommand(message).apply {
                                    putSerializable(Constants.RECIPE, foundRecipe[0])
                                    putString(Constants.MAC_ADDRESS, macAddress)
                                })
                            return@launch
                        }
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), String.format(
                                    resources.getString(R.string.dynamic_message_recipe_not_found_in_this_device),
                                Constants.getRecipeNameFromCommand(message)
                                ), Toast.LENGTH_SHORT)
                                .show()
                        }
                        return@launch
                    }
                            val recentItem = Constants.getRecentItemFromDb(
                                foundRecipe[0]
                            )

                            recentItem.recipe =
                                Gson().toJson(foundRecipe[0])
                            DebugLog.e("If $macAddress")
                            requireActivity().runOnUiThread {
                                findNavController().navigate(
                                    R.id.cookingFragment,
                                    Constants.getBundleFromCommand(message).apply {
                                        putSerializable(Constants.RECIPE, recentItem)
                                        putString(Constants.MAC_ADDRESS, macAddress)
                                    })
                            }
                    }
                }

                if (message.uppercase().contains("WORKSTATUS=IDLE")) {
                    navigateToRecipeFragment()
                }

                if (message.uppercase().startsWith("FRYQUICKSTART=")) {
                    recipeScreenFlowTypeForNavigation.withNotNull {
                        if (it == Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE) {
                            navigateToRecipeFragment()
                        } else {
                            Toast.makeText(
                                requireActivity(),
                                "Device is busy with Fry mode",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                if (message.uppercase().startsWith("INDQUICKSTART=")) {
                    recipeScreenFlowTypeForNavigation.withNotNull {
                        if (it == Constants.RecipeFragmentScreenFlowType.MANUAL_MODE) {
                            navigateToRecipeFragment()
                        } else {
                            Toast.makeText(
                                requireActivity(),
                                "Device is busy with Manual mode",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Order module v5
    // This module deliberately lives inside HomeFragment so the existing Activity toolbar
    // (BLE symbol, On2Cook logo/device title, add-device button and all firmware-facing logic)
    // remains untouched.
    // ---------------------------------------------------------------------------------------------

    private var orderListMode: OrderListMode = OrderListMode.CURRENT
    private var selectedAssignDevice: KitchenDevice? = null
    private val selectedQuickDeviceForOrder: MutableMap<String, Int> = mutableMapOf()
    private val pinnedRecipeCodes: LinkedHashSet<String> = linkedSetOf("CK01", "VGP01", "DK01")
    private var settingsLoggedIn: Boolean = false
    private val presetStirrerSpeeds = listOf("Off", "Low", "Medium", "High", "V High")
    private val presetLidStates = listOf("Closed", "Open", "Any")
    private var pendingPresetRecipeNameToRun: String? = null
    private val syncedPresetRecipeNames: LinkedHashSet<String> = linkedSetOf()
    private val runtimePresetRecipes: MutableMap<String, Recipe> = mutableMapOf()
    private val liveDeviceStatusByMac: MutableMap<String, DeviceRuntimeStatus> = mutableMapOf()
    private val localCookingItemByMac: MutableMap<String, KitchenOrderItem> = mutableMapOf()
    private val localDeviceQueueByMac: MutableMap<String, MutableList<KitchenOrderItem>> = mutableMapOf()
    private val localDeviceCookedHistoryByMac: MutableMap<String, MutableList<KitchenOrderItem>> = mutableMapOf()
    private val pendingOrderRecipeRunByMac: MutableMap<String, String> = mutableMapOf()
    private val pendingIngredientAutoStartByMac: MutableMap<String, String> = mutableMapOf()
    private val assetRecipeCache: MutableList<Recipe> = mutableListOf()
    private val recipeUploadInProgressByMac: MutableMap<String, String> = mutableMapOf()
    private var activeOrderModuleTab: OrderModuleTab? = null
    private var selectedDeviceDetailNumber: Int? = null
    private var lastDeviceStatusRequestAt: Long = 0L

    private val modePresets: MutableList<ModePreset> by lazy {
        mutableListOf(
            ModePreset(
                "start", "Start (Manual)", "Manual quick-start preset used by the knob", "▶",
                mutableListOf(
                    ModePresetStep(1, 100, 240, 100, 240, 40, 40, "Medium"),
                    ModePresetStep(2, 80, 120, 80, 120, 0, 0, "Medium")
                )
            ),
            ModePreset(
                "recipe", "Recipe", "Recipe-mode ingredient and instruction flow", "▤",
                mutableListOf(ModePresetStep(1, 0, 0, 0, 0, 0, 0, "Off")),
                isDeviceMode = false
            ),
            ModePreset(
                "bake", "Bake", "Multi-step baking preset", "▣",
                mutableListOf(
                    ModePresetStep(1, 50, 180, 100, 360, 0, 0, "Medium", thresholdTemp = 100),
                    ModePresetStep(2, 40, 240, 100, 600, 0, 0, "Medium", thresholdTemp = 100),
                    ModePresetStep(3, 30, 180, 80, 180, 0, 0, "Low", thresholdTemp = 90)
                )
            ),
            ModePreset(
                "grill", "Grill", "Four compact recipe-like grill steps", "▥",
                mutableListOf(
                    ModePresetStep(1, 40, 120, 100, 120, 90, 25, "Off", thresholdTemp = 100),
                    ModePresetStep(2, 30, 180, 100, 180, 120, 20, "Off", thresholdTemp = 100),
                    ModePresetStep(3, 20, 180, 80, 180, 90, 15, "Off", thresholdTemp = 100),
                    ModePresetStep(4, 10, 120, 50, 120, 60, 15, "Off", thresholdTemp = 80)
                )
            ),
            ModePreset(
                "steam", "Steam", "Steam mode with pump/spray defaults", "☁",
                mutableListOf(
                    ModePresetStep(1, 70, 180, 100, 180, 40, 40, "Medium", pumpSeconds = 10, sprayMl = 5, thresholdTemp = 100),
                    ModePresetStep(2, 60, 300, 100, 300, 0, 0, "Medium", pumpSeconds = 10, sprayMl = 5, thresholdTemp = 100),
                    ModePresetStep(3, 40, 120, 100, 120, 0, 0, "Low", pumpSeconds = 5, sprayMl = 3, thresholdTemp = 90)
                )
            ),
            ModePreset(
                "reheat", "Reheat", "Reheat preset for cooked food", "▨",
                mutableListOf(
                    ModePresetStep(1, 40, 120, 100, 120, 50, 30, "Low", thresholdTemp = 80),
                    ModePresetStep(2, 30, 120, 80, 120, 0, 0, "Low", thresholdTemp = 80),
                    ModePresetStep(3, 20, 120, 60, 120, 0, 0, "Off", thresholdTemp = 70)
                )
            ),
            ModePreset(
                "fry", "Fry (Frying Device)", "Fry preset for the assigned fry device", "♨",
                mutableListOf(
                    ModePresetStep(1, 60, 300, 0, 0, 0, 0, "Off", thresholdTemp = 180),
                    ModePresetStep(2, 50, 300, 0, 0, 0, 0, "Off", thresholdTemp = 180),
                    ModePresetStep(3, 40, 180, 0, 0, 0, 0, "Off", thresholdTemp = 160),
                    ModePresetStep(4, 30, 120, 0, 0, 0, 0, "Off", thresholdTemp = 150)
                )
            )
        )
    }

    private val demoLoggedInUserName = "Ravi Sharma"
    private val demoLoggedInUserEmail = "ravi.sharma@on2cook.com"

    private val demoDevices: ArrayList<KitchenDevice> by lazy {
        arrayListOf(
            KitchenDevice(1, "On2Cook-01", DeviceState.COOKING, "Veg Biryani", "03:15", "52%", queueCount = 2),
            KitchenDevice(2, "On2Cook-02", DeviceState.COOKING, "Chicken Nuggets (500g)", "02:30", "80%"),
            KitchenDevice(3, "On2Cook-03", DeviceState.COOKING, "Paneer Butter Masala", "04:15", "60%", queueCount = 1),
            KitchenDevice(4, "On2Cook-04", DeviceState.IDLE),
            KitchenDevice(5, "On2Cook-05", DeviceState.IDLE),
            KitchenDevice(6, "On2Cook-06", DeviceState.OFFLINE),
            KitchenDevice(7, "On2Cook-07", DeviceState.OFFLINE)
        )
    }

    private val currentOrderItems: ArrayList<KitchenOrderItem> by lazy {
        arrayListOf(
            // Four functional dummy KOT/order items based on the AppSumo/POS reference: one independent card per item.
            KitchenOrderItem("#84", "Chicken Nuggets", "0G31", "500 g", "", "Zomato", "Delivery", "2 min ago", "Test", "₹260.86", "#EE2E46", OrderStatus.PENDING, "Chicken Nuggets", "Make it less spicy", imageRes = R.drawable.ic_fried_chicken),
            KitchenOrderItem("#85", "Paneer Butter Masala", "PBM01", "500 g", "", "Swiggy", "Delivery", "5 min ago", "Rajesh", "₹512.40", "#FF7A1A", OrderStatus.PENDING, "Paneer Butter Masala", "Less oil", imageRes = R.drawable.ic_vegetable),
            KitchenOrderItem("#86", "Veg Pulao", "VGP01", "500 g", "", "POS", "Takeaway", "9 min ago", "Counter", "₹210.00", "#1167D8", OrderStatus.PENDING, "Veg Pulao", imageRes = R.drawable.ic_veg_saute),
            KitchenOrderItem("#87", "Dal Khichdi", "DK01", "500 g", "", "Zomato", "Delivery", "12 min ago", "Priya", "₹185.30", "#8E44AD", OrderStatus.PENDING, "Dal Khichdi", "No onion", imageRes = R.drawable.ic_thick_poha)
        )
    }

    private val previousOrderItems: ArrayList<KitchenOrderItem> by lazy {
        arrayListOf(
            KitchenOrderItem("#77", "Hakka Noodles", "HN01", "300 g", "", "POS", "Takeaway", "11:25 AM", "Amit", "₹230.50", "#2E7D32", OrderStatus.COMPLETED, "Hakka Noodles", assignedDevice = "On2Cook-04", imageRes = R.drawable.ic_veg_saute),
            KitchenOrderItem("#76", "Veg Fried Rice", "FR01", "500 g", "", "Swiggy", "Delivery", "11:05 AM", "Rajesh", "₹325.00", "#FF7A1A", OrderStatus.COMPLETED, "Veg Fried Rice", assignedDevice = "On2Cook-02", imageRes = R.drawable.ic_veg_saute),
            KitchenOrderItem("#75", "Chicken Biryani", "CB01", "500 g", "", "Zomato", "Delivery", "10:55 AM", "Test", "₹260.86", "#EE2E46", OrderStatus.SHIPPED, "Chicken Biryani", assignedDevice = "On2Cook-01", imageRes = R.drawable.ic_fried_chicken),
            KitchenOrderItem("#66", "Veg Pulao", "VGP01", "500 g", "", "Zomato", "Delivery", "10:35 AM", "Amit", "₹210.00", "#EE2E46", OrderStatus.FAILED, "Veg Pulao", "No onion", "On2Cook-03", imageRes = R.drawable.ic_veg_saute),
            KitchenOrderItem("#65", "Fried Rice", "FR02", "300 g", "", "POS", "Dine-in", "10:10 AM", "Counter", "₹195.00", "#1167D8", OrderStatus.CANCELLED, "Fried Rice", imageRes = R.drawable.ic_thick_poha)
        )
    }

    private fun setupOrderModuleTab() {
        // Keep the existing Home screen as the default screen.
        // The Orders module opens only when the operator taps the Orders tile/tab.
        binding.legacyHomeContent!!.visibility = View.VISIBLE
        binding.orderModuleHost!!.visibility = View.GONE
        binding.homeBottomNavigation!!.visibility = View.GONE

        binding.clShop.setOnClickListener { openCloudOrderModule() }
        binding.bottomTabOrders!!.setOnClickListener { selectOrderModuleTab(OrderModuleTab.ORDERS) }
        binding.bottomTabDevices!!.setOnClickListener { selectOrderModuleTab(OrderModuleTab.DEVICES) }
        binding.bottomTabRecipes!!.setOnClickListener { selectOrderModuleTab(OrderModuleTab.RECIPES) }
        binding.bottomTabMore!!.setOnClickListener { selectOrderModuleTab(OrderModuleTab.MORE) }
    }

    private fun openCloudOrderModule() {
        startActivity(Intent(requireContext(), CloudWebActivity::class.java))
    }

    private fun openOrderModule() {
        selectOrderModuleTab(OrderModuleTab.ORDERS)
    }

    private fun returnToExistingHomeScreen() {
        binding.orderModuleHost!!.visibility = View.GONE
        binding.homeBottomNavigation!!.visibility = View.GONE
        binding.legacyHomeContent!!.visibility = View.VISIBLE
    }

    private fun selectOrderModuleTab(tab: OrderModuleTab) {
        selectedDeviceDetailNumber = null
        activeOrderModuleTab = tab
        setBottomTabColors(tab)
        binding.legacyHomeContent!!.visibility = View.GONE
        binding.orderModuleHost!!.visibility = View.VISIBLE
        binding.homeBottomNavigation!!.visibility = View.VISIBLE
        when (tab) {
            OrderModuleTab.ORDERS -> renderOrdersScreen(orderListMode)
            OrderModuleTab.DEVICES -> renderDevicesTab()
            OrderModuleTab.RECIPES -> renderRecipesTab()
            OrderModuleTab.MORE -> renderMoreTab()
        }
    }

    private fun setBottomTabColors(selected: OrderModuleTab) {
        val active = Color.parseColor("#FF6B35")
        val normal = Color.parseColor("#666666")
        fun apply(text: TextView, icon: TextView, isActive: Boolean) {
            text.setTextColor(if (isActive) active else normal)
            icon.setTextColor(if (isActive) active else normal)
            text.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        apply(binding.tvBottomOrders!!, binding.tvBottomOrdersIcon!!, selected == OrderModuleTab.ORDERS)
        apply(binding.tvBottomDevices!!, binding.tvBottomDevicesIcon!!, selected == OrderModuleTab.DEVICES)
        apply(binding.tvBottomRecipes!!, binding.tvBottomRecipesIcon!!, selected == OrderModuleTab.RECIPES)
        apply(binding.tvBottomMore!!, binding.tvBottomMoreIcon!!, selected == OrderModuleTab.MORE)
    }

    private fun renderOrdersScreen(mode: OrderListMode) {
        orderListMode = mode
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout

        content.addView(orderTopToggle(mode))
        content.addView(refreshRow())

        if (mode == OrderListMode.CURRENT) {
            sectionCard(content, "IN EXECUTION", currentOrderItems.filter { it.status == OrderStatus.COOKING }, Color.parseColor("#1167D8"), "Currently cooking on On2Cook devices")
            sectionCard(content, "PENDING / TO DO", currentOrderItems.filter { it.status == OrderStatus.PENDING }, Color.parseColor("#FF9800"), "Tap an ON device chip for quick cook/queue")
            sectionCard(content, "COMPLETED", currentOrderItems.filter { it.status == OrderStatus.COMPLETED }, Color.parseColor("#1FA33B"), "Cooked but not yet closed")
            content.addView(quickDevicesCard())
            content.addView(bottomSpacer())
        } else {
            content.addView(previousOrderFilters())
            previousOrderItems.forEach { item -> content.addView(previousOrderCard(item)) }
            content.addView(actionTextButton("Load More", "#FFFFFF", "#333333", "#D0D0D0") { })
            content.addView(bottomSpacer())
        }
        setHost(root)
    }

    private fun renderOrderDetails(item: KitchenOrderItem) {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Order Details", true) { renderOrdersScreen(orderListMode) })
        content.addView(orderDetailHeader(item))
        content.addView(orderItemDetails(item))
        when (item.status) {
            OrderStatus.COOKING -> content.addView(cookingStatusBlock(item))
            OrderStatus.PENDING -> content.addView(pendingAssignmentBlock(item))
            OrderStatus.COMPLETED, OrderStatus.SHIPPED -> content.addView(completedInfoBlock(item))
            OrderStatus.FAILED, OrderStatus.CANCELLED -> content.addView(failureInfoBlock(item))
        }
        content.addView(orderSummaryBlock(item))
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderSelectDevice(item: KitchenOrderItem) {
        requestAllConnectedDeviceStatuses(force = true)
        syncSelectedAssignDeviceForOrder(item)
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Order Workspace", true) { renderOrdersScreen(orderListMode) })
        content.addView(selectDeviceOrderHeader(item))
        content.addView(label("ORDER TO DEVICE FLOW - ${connectedDeviceMacs().size} CONNECTED", 14, "#111111", true).apply { setPadding(dp(16), dp(12), dp(16), dp(4)) })
        content.addView(label("Swipe left or right on the device card, or use the arrows, to move across devices while keeping this order in focus.", 12, "#666666", false).apply { setPadding(dp(16), 0, dp(16), dp(8)) })
        content.addView(orderWorkspaceDevicePager(item))
        content.addView(label("ALL DEVICES", 13, "#111111", true).apply { setPadding(dp(16), dp(10), dp(16), dp(4)) })
        (idleOnDevices() + busyOnDevices() + allKitchenDevices().filter { it.state == DeviceState.OFFLINE }).forEach { device -> content.addView(selectDeviceRow(item, device)) }
        val note = label("Free devices start immediately. Busy devices receive this item in queue. Offline devices are disabled until connected over Bluetooth.", 12, "#666666", false)
        note.setPadding(dp(16), dp(10), dp(16), dp(4))
        content.addView(note)
        val assignButton = actionTextButton(assignButtonText(), "#FF6B35", "#FFFFFF", "#FF6B35") {
            assignSelectedDevice(item)
        }
        assignButton.setPadding(dp(16), dp(8), dp(16), dp(8))
        content.addView(assignButton)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun assignButtonText(): String {
        val selected = selectedAssignDevice ?: return "Select Device"
        return when (selected.state) {
            DeviceState.IDLE -> "Cook Now on Device ${selected.number}"
            DeviceState.COOKING, DeviceState.QUEUED -> "Add to Queue on Device ${selected.number}"
            DeviceState.OFFLINE -> "Device ${selected.number} Offline"
        }
    }

    private fun assignSelectedDevice(item: KitchenOrderItem) {
        val selected = selectedAssignDevice
        if (selected == null || selected.state == DeviceState.OFFLINE || selected.macAddress.isBlank()) {
            Toast.makeText(requireContext(), "Select a connected On2Cook device first", Toast.LENGTH_SHORT).show()
            return
        }
        selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = selected.number
        if (selected.state == DeviceState.IDLE) {
            startOrderItemOnDevice(item, selected)
        } else {
            queueOrderItemOnDevice(item, selected)
        }
    }

    private fun startOrderItemOnDevice(item: KitchenOrderItem, device: KitchenDevice) {
        if (!::service.isInitialized || device.macAddress.isBlank() || !service.isDeviceConnected(device.macAddress)) {
            Toast.makeText(requireContext(), "Device ${device.number} is not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val recipeName = item.recipe.ifBlank { item.itemName }.trim()
        if (recipeName.isBlank()) {
            Toast.makeText(requireContext(), "No recipe mapped for ${item.itemName}", Toast.LENGTH_SHORT).show()
            return
        }
        item.status = OrderStatus.COOKING
        item.assignedDevice = "Device ${device.number}"
        item.remainingTime = "--:--"
        localCookingItemByMac[device.macAddress] = item
        liveDeviceStatusByMac[device.macAddress] = DeviceRuntimeStatus(
            macAddress = device.macAddress,
            cooking = true,
            recipeName = recipeName,
            mode = "Starting",
            status = "START",
            lastMessage = "APP_START=$recipeName"
        )
        val localRecipe = findLocalRecipeByName(recipeName)
        if (localRecipe != null) {
            uploadRecipeToDeviceAndRun(device.macAddress, localRecipe, recipeName)
        } else {
            sendRecipeRunCommand(device.macAddress, recipeName, autoStartAfterIngredient = true)
        }
        Toast.makeText(requireContext(), "${item.itemName} sent to Device ${device.number}", Toast.LENGTH_SHORT).show()
        requestAllConnectedDeviceStatuses(force = true)
        if (activeOrderModuleTab == OrderModuleTab.DEVICES) {
            renderDeviceDetail(allKitchenDevices().firstOrNull { it.macAddress == device.macAddress } ?: device)
        } else if (activeOrderModuleTab == OrderModuleTab.ORDERS) {
            renderSelectDevice(item)
        } else {
            renderOrdersScreen(OrderListMode.CURRENT)
        }
    }

    private fun queueOrderItemOnDevice(item: KitchenOrderItem, device: KitchenDevice) {
        if (device.macAddress.isBlank()) {
            Toast.makeText(requireContext(), "Device ${device.number} is not connected", Toast.LENGTH_SHORT).show()
            return
        }
        localDeviceQueueByMac.getOrPut(device.macAddress) { mutableListOf() }.add(item)
        item.assignedDevice = "Queue: Device ${device.number}"
        Toast.makeText(requireContext(), "${item.itemName} added to Device ${device.number} queue", Toast.LENGTH_SHORT).show()
        if (activeOrderModuleTab == OrderModuleTab.DEVICES) {
            renderDeviceDetail(allKitchenDevices().firstOrNull { it.macAddress == device.macAddress } ?: device)
        } else if (activeOrderModuleTab == OrderModuleTab.ORDERS) {
            renderSelectDevice(item)
        } else {
            renderOrdersScreen(OrderListMode.CURRENT)
        }
    }

    private fun availableFirmwareRecipes(): List<Recipe> {
        val recipes = LinkedHashMap<String, Recipe>()
        fun addRecipe(recipe: Recipe) {
            val name = recipe.name.firstOrNull()?.trim().orEmpty()
            if (name.isNotBlank() && name.length < 32 && !recipes.containsKey(name.lowercase())) {
                recipes[name.lowercase()] = recipe
            }
        }
        list.forEach { addRecipe(it) }
        loadAssetRecipeZips().forEach { addRecipe(it) }
        if (recipes.isEmpty()) {
            fallbackRecipeNames().forEach { addRecipe(createFallbackRecipe(it)) }
        }
        return recipes.values.take(10)
    }

    private fun fallbackRecipeNames(): List<String> = listOf(
        "Chicken Nuggets", "Veg Pulao", "Paneer Butter Masala", "Dal Khichdi", "Masala Dosa",
        "French Fries", "Hakka Noodles", "Veg Fried Rice", "Chicken Biryani", "Sabudana Vada"
    )

    private fun loadAssetRecipeZips(): List<Recipe> {
        if (assetRecipeCache.isNotEmpty()) return assetRecipeCache
        try {
            val names = requireContext().assets.list("order_recipes").orEmpty()
                .filter { it.endsWith(".zip", true) }
                .sorted()
            names.forEach { assetName ->
                requireContext().assets.open("order_recipes/$assetName").use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".json", true)) {
                                val json = String(zip.readBytes(), Charsets.UTF_8)
                                val recipe = Gson().fromJson(json, Recipe::class.java)
                                if (recipe != null && recipe.name.isNotEmpty()) assetRecipeCache.add(recipe)
                                break
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OrderModule", "Unable to load asset recipe zips", e)
        }
        return assetRecipeCache
    }

    private fun createFallbackRecipe(recipeName: String): Recipe {
        val recipe = Recipe()
        recipe.name.add(sanitizeFirmwareRecipeName(recipeName))
        recipe.description = "On2Cook fallback recipe for firmware queue testing"
        recipe.category = "Orders"
        recipe.Ingredients.add(Ingredients().apply {
            id = 1
            title = sanitizeShortText(recipeName, 18)
            pan_type = "STD"
            text = "Main ingredient"
            weight = "500 grams"
        })
        recipe.Ingredients.add(Ingredients().apply {
            id = 2
            title = "Oil"
            pan_type = "STD"
            text = "Oil or water"
            weight = "30 milimetre"
        })
        recipe.Instruction.add(Instructions().apply {
            id = 1
            Text = "${sanitizeShortText(recipeName, 12)} S1"
            Induction_on_time = "120"
            Induction_power = "60"
            Magnetron_on_time = "120"
            Magnetron_power = "80"
            lid = "Closed"
            threshold = "0"
            warm_time = "0"
            wait_time = "0"
            stirrer_on = "Medium"
            pump_on = "0"
            purge_on = "0"
            skip = "0"
            durationInSec = 120
        })
        recipe.Instruction.add(Instructions().apply {
            id = 2
            Text = "${sanitizeShortText(recipeName, 12)} S2"
            Induction_on_time = "120"
            Induction_power = "40"
            Magnetron_on_time = "120"
            Magnetron_power = "60"
            lid = "Closed"
            threshold = "0"
            warm_time = "0"
            wait_time = "0"
            stirrer_on = "Low"
            pump_on = "0"
            purge_on = "0"
            skip = "0"
            durationInSec = 120
        })
        return recipe
    }

    private fun sanitizeFirmwareRecipeName(raw: String): String {
        val safe = raw.replace(Regex("[^A-Za-z0-9 ()_-]"), "").trim()
        return safe.take(30).ifBlank { "APP_RECIPE" }
    }

    private fun sanitizeShortText(raw: String, max: Int): String = raw.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(max).ifBlank { "Step" }

    private fun findLocalRecipeByName(recipeName: String): Recipe? {
        val key = recipeName.trim()
        runtimePresetRecipes[key]?.let { return it }
        availableFirmwareRecipes().firstOrNull { recipe -> recipe.name.any { it.equals(key, true) } }?.let { return it }
        list.firstOrNull { recipe -> recipe.name.any { it.equals(key, true) } }?.let { return it }
        return if (fallbackRecipeNames().any { it.equals(key, true) }) createFallbackRecipe(key) else null
    }

    private fun uploadRecipeToDeviceAndRun(targetMac: String, recipe: Recipe, recipeName: String) {
        val firmwareName = sanitizeFirmwareRecipeName(recipeName)
        if (recipe.name.isEmpty()) recipe.name.add(firmwareName) else recipe.name[0] = firmwareName
        val recipeJson = Gson().toJson(recipe)
        val size = recipeJson.toByteArray(Charsets.UTF_8).size
        pendingOrderRecipeRunByMac[targetMac] = firmwareName
        recipeUploadInProgressByMac[targetMac] = firmwareName
        service.setJsonFileDataInMap(targetMac, recipeJson)
        service.writeData(targetMac, "DELETE=$firmwareName".toByteArray(Charsets.UTF_8))
        Handler(Looper.getMainLooper()).postDelayed({
            if (::service.isInitialized && service.isDeviceConnected(targetMac)) {
                service.writeFileData(
                    targetMac,
                    "{\"RECIPE\":\"$firmwareName\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(Charsets.UTF_8)
                )
            }
        }, 450)
    }

    private fun handleOrderRecipeUploadSuccess(targetMac: String) {
        val recipeName = pendingOrderRecipeRunByMac.remove(targetMac) ?: return
        recipeUploadInProgressByMac.remove(targetMac)
        sendRecipeRunCommand(targetMac, recipeName, autoStartAfterIngredient = true)
    }

    private fun sendRecipeRunCommand(targetMac: String, recipeName: String, autoStartAfterIngredient: Boolean) {
        if (!::service.isInitialized || !service.isDeviceConnected(targetMac)) {
            Toast.makeText(requireContext(), "Selected device is not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val firmwareName = sanitizeFirmwareRecipeName(recipeName)
        if (autoStartAfterIngredient) pendingIngredientAutoStartByMac[targetMac] = firmwareName
        service.writeData(targetMac, "recipe=$firmwareName".toByteArray(Charsets.UTF_8))
        Handler(Looper.getMainLooper()).postDelayed({ requestAllConnectedDeviceStatuses(force = true) }, 650)
        if (autoStartAfterIngredient) {
            Handler(Looper.getMainLooper()).postDelayed({
                // Fallback for firmware builds that do not send a separate Ingredient-mode notification.
                if (pendingIngredientAutoStartByMac[targetMac] == firmwareName && ::service.isInitialized && service.isDeviceConnected(targetMac)) {
                    service.writeData(targetMac, "ingredients=100".toByteArray(Charsets.UTF_8))
                    pendingIngredientAutoStartByMac.remove(targetMac)
                }
            }, 1800)
        }
    }

    private fun renderDevicesTab() {
        requestAllConnectedDeviceStatuses()
        val devices = allKitchenDevices()
        val connectedCount = connectedDeviceMacs().size
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Devices", false, null))
        content.addView(label("$connectedCount connected On2Cook device(s) · real-time status from firmware", 13, "#666666", false).apply { setPadding(dp(16), 0, dp(16), dp(8)) })
        content.addView(deviceRealtimeSummaryCard(devices))
        devices.forEach { device -> content.addView(deviceStatusCard(device)) }
        content.addView(legendCard())
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun deviceRealtimeSummaryCard(devices: List<KitchenDevice>): LinearLayout {
        val card = cardContainer()
        card.addView(label("REAL-TIME DEVICE MAP", 13, "#111111", true))
        card.addView(label("Device numbers are assigned in Bluetooth connection order. Device 1 = first connected device, Device 2 = second connected device, and so on.", 11, "#555555", false).apply { setPadding(0, dp(4), 0, dp(8)) })
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        devices.take(7).forEach { device ->
            row.addView(deviceMiniStatusPill(device), LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(4) })
        }
        card.addView(row)
        return card
    }

    private fun deviceMiniStatusPill(device: KitchenDevice): LinearLayout {
        val inUse = isDeviceInUse(device)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            alpha = if (device.state == DeviceState.OFFLINE) 0.45f else 1f
            background = roundedDrawable(
                when {
                    inUse -> Color.parseColor("#FFF1F1")
                    device.state == DeviceState.IDLE -> Color.WHITE
                    else -> Color.parseColor("#F6F6F6")
                },
                when {
                    inUse -> Color.parseColor("#D73C50")
                    device.state == DeviceState.IDLE -> Color.parseColor("#90A4AE")
                    else -> Color.parseColor("#C8C8C8")
                },
                if (inUse) 2 else 1,
                8
            )
            addView(label(device.number.toString(), 15, if (inUse) "#D73C50" else "#263238", true))
            addView(label(if (device.state == DeviceState.OFFLINE) "Off" else if (inUse) "Busy" else "Free", 9, if (inUse) "#D73C50" else "#455A64", true))
        }
    }

    private fun renderDeviceDetail(device: KitchenDevice) {
        selectedDeviceDetailNumber = device.number
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Device ${device.number} · ${device.name}", true) { renderDevicesTab() })
        content.addView(deviceHeroCard(device))
        content.addView(actionTextButton(if (device.state == DeviceState.OFFLINE) "Connect Device to Assign Recipe" else "Assign Recipe / Add to Queue", if (device.state == DeviceState.OFFLINE) "#F4F4F4" else "#FF6B35", if (device.state == DeviceState.OFFLINE) "#777777" else "#FFFFFF", if (device.state == DeviceState.OFFLINE) "#D0D0D0" else "#FF6B35") { if (device.state != DeviceState.OFFLINE) renderDeviceRecipePicker(device) })
        content.addView(deviceLiveQueueCard(device))
        content.addView(deviceTodayStatsCard(device))
        content.addView(deviceTopRecipesCard(device))
        content.addView(deviceCountersCard(device))
        content.addView(deviceIdentityFirmwareCard(device))
        content.addView(deviceInstallationCard())
        content.addView(deviceAccessoriesCard())
        content.addView(deviceHealthLogsCard())
        content.addView(actionTextButton("Edit Device Settings", "#37484A", "#FFFFFF", "#37484A") {
            Toast.makeText(requireContext(), "Device settings will open from Settings module", Toast.LENGTH_SHORT).show()
        })
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderDeviceRecipePicker(device: KitchenDevice) {
        selectedDeviceDetailNumber = device.number
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Device ${device.number} · Assign Recipe", true) { renderDeviceDetail(device) })
        content.addView(label(if (device.state == DeviceState.IDLE) "Choose any recipe to start immediately on this connected On2Cook device." else "Device is busy. Choosing a recipe will add it to this device queue.", 13, "#555555", false).apply { setPadding(dp(16), 0, dp(16), dp(10)) })
        val recipes = manualRecipeItems()
        recipes.forEach { item ->
            val card = cardContainer()
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(foodImage(item.imageRes), LinearLayout.LayoutParams(dp(72), dp(72)).apply { marginEnd = dp(12) })
            val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(label(item.itemName, 15, "#111111", true))
            val recipe = findLocalRecipeByName(item.recipe)
            val mins = ((recipe?.let { recipeDurationSeconds(it) } ?: 600) / 60).coerceAtLeast(1)
            mid.addView(label("${recipe?.Instruction?.size ?: 2} steps · $mins min · ${item.quantity}", 12, "#555555", false).apply { setPadding(0, dp(4), 0, 0) })
            row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(smallColoredButton(if (device.state == DeviceState.IDLE) "Cook Now" else "Add Queue", "#FF6B35", "#FFFFFF", "#FF6B35") {
                if (device.state == DeviceState.IDLE) startOrderItemOnDevice(item, device) else queueOrderItemOnDevice(item, device)
            }, LinearLayout.LayoutParams(dp(96), dp(36)))
            card.addView(row)
            content.addView(card)
        }
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun deviceHeroCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label(if (device.state == DeviceState.COOKING) "Cooking: ${device.currentItem}" else statusText(device.state), 15, "#111111", true))
        mid.addView(label("${device.liveMode.ifBlank { if (device.state == DeviceState.IDLE) "Ready" else statusText(device.state) }} ${device.liveStep}".trim(), 12, "#4A4A4A", false).apply { setPadding(0, dp(4), 0, 0) })
        if (device.state == DeviceState.COOKING) {
            mid.addView(label("${device.remaining} left now · Queue ${queueWaitText(device)} · Total 25 min", 12, "#2E7D32", true).apply { setPadding(0, dp(4), 0, 0) })
        } else {
            mid.addView(label(deviceStatusLine(device), 12, "#4A4A4A", false).apply { setPadding(0, dp(4), 0, 0) })
        }
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip(deviceHealthText(device), lightenColor(deviceColor(device.state)), colorToHex(deviceColor(device.state))))
        card.addView(row)
        if (device.state == DeviceState.COOKING || device.state == DeviceState.QUEUED) card.addView(progressBar(if (device.state == DeviceState.COOKING) 0.52f else 0.25f, deviceColor(device.state)))
        return card
    }

    private fun deviceLiveQueueCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.addView(label("DEVICE QUEUE SLIDER", 13, "#111111", true))
        card.addView(label("Scroll up for items cooked on this device. The NOW anchor separates history from upcoming queue.", 11, "#555555", false).apply { setPadding(0, dp(4), 0, dp(8)) })
        card.addView(label("↑ COOKED HISTORY", 11, "#777777", true).apply { setPadding(0, 0, 0, dp(4)) })
        completedHistoryForDevice(device).forEach { item ->
            card.addView(timelineRow("✓", item.first, item.second, "#444444", "#F6F7F8"))
        }
        val nowText = if (device.currentItem.isNotBlank()) device.currentItem else "No item cooking"
        val nowTime = if (device.state == DeviceState.COOKING) "${device.remaining} left" else "Ready now"
        card.addView(timelineAnchorRow("NOW", nowText, nowTime, device.state == DeviceState.IDLE))
        val queueItems = queuedItemsForDevice(device)
        if (queueItems.isEmpty()) {
            card.addView(timelineRow("＋", "No queued items", "", "#666666", "#FFFFFF"))
        } else {
            card.addView(label("↓ UPCOMING QUEUE", 11, "#777777", true).apply { setPadding(0, dp(8), 0, dp(4)) })
            queueItems.forEachIndexed { index, item ->
                card.addView(timelineRow("${index + 1}", item.first, item.second, "#D73C50", "#FFF1F1"))
            }
        }
        return card
    }

    private fun timelineRow(prefix: String, title: String, time: String, color: String, fill: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.parseColor(fill), Color.parseColor("#E3E8E8"), 1, 8)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            addView(label(prefix, 12, color, true), LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(label(title, 12, "#222222", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(time, 11, "#555555", false))
        }.apply { layoutParams = cardLp(0, 2, 0, 4) }
    }

    private fun timelineAnchorRow(prefix: String, title: String, time: String, idle: Boolean): LinearLayout {
        val border = if (idle) Color.parseColor("#90A4AE") else Color.parseColor("#D73C50")
        val fill = if (idle) Color.WHITE else Color.parseColor("#FFF1F1")
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(fill, border, 2, 10)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(label(prefix, 12, colorToHex(border), true), LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT))
            val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(label(title, 14, "#111111", true))
            mid.addView(label(time, 12, colorToHex(border), true).apply { setPadding(0, dp(3), 0, 0) })
            addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }.apply { layoutParams = cardLp(0, 8, 0, 8) }
    }

    private fun deviceTodayStatsCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.addView(label("LIVE DEVICE STATS", 13, "#111111", true))
        val historyCount = localDeviceCookedHistoryByMac[device.macAddress]?.size ?: 0
        val queueCount = localDeviceQueueByMac[device.macAddress]?.size ?: 0
        val live = liveDeviceStatusByMac[device.macAddress]
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(statBox(if (live?.indSeconds ?: 0 > 0) secondsLabel(live?.indSeconds ?: 0) else "--", "Ind remaining"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(statBox(if (live?.magSeconds ?: 0 > 0) secondsLabel(live?.magSeconds ?: 0) else "--", "Mag remaining"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(statBox(historyCount.toString(), "Cooked in app"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(statBox(queueCount.toString(), "Queue items"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun deviceTopRecipesCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.addView(label("RECIPES ON THIS DEVICE", 13, "#111111", true))
        val names = mutableListOf<String>()
        if (device.currentItem.isNotBlank()) names.add(device.currentItem)
        names.addAll(localDeviceQueueByMac[device.macAddress].orEmpty().map { it.itemName })
        names.addAll(localDeviceCookedHistoryByMac[device.macAddress].orEmpty().map { it.itemName })
        val grouped = names.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)
        if (grouped.isEmpty()) {
            card.addView(label("No live recipe history yet. Assign a recipe to this device to start tracking.", 12, "#666666", false).apply { setPadding(0, dp(8), 0, 0) })
        } else {
            grouped.forEachIndexed { index, item -> card.addView(recipeBarRow("${index + 1}. ${item.first} (${item.second})", (1f - (index * 0.12f)).coerceAtLeast(0.35f), Color.parseColor("#FF6B35"))) }
        }
        return card
    }

    private fun deviceCountersCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.addView(label("SESSION COUNTERS", 13, "#111111", true))
        val cooked = localDeviceCookedHistoryByMac[device.macAddress]?.size ?: 0
        val queued = localDeviceQueueByMac[device.macAddress]?.size ?: 0
        val running = if (localCookingItemByMac.containsKey(device.macAddress)) 1 else 0
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(statBox(running.toString(), "Running now"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(statBox(queued.toString(), "Queued"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(statBox(cooked.toString(), "Completed"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun deviceIdentityFirmwareCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.addView(label("DEVICE IDENTITY + FIRMWARE", 13, "#111111", true))
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        val identity = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.parseColor("#F8FAFA"), Color.parseColor("#E3E8E8"), 1, 10)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(label("Device number sticker", 11, "#666666", false))
            addView(label("O2C-IND-01-2407", 14, "#111111", true).apply { setPadding(0, dp(4), 0, 0) })
            addView(label("MAC ID: ${device.macAddress.ifBlank { "Not connected" }}", 11, "#555555", false).apply { setPadding(0, dp(4), 0, 0) })
            addView(smallColoredButton("Take sticker photo + OCR", "#37484A", "#FFFFFF", "#37484A") { Toast.makeText(requireContext(), "Camera/OCR screen pending", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(8) })
        }
        row.addView(identity, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        val firmware = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.parseColor("#F8FAFA"), Color.parseColor("#E3E8E8"), 1, 10)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(label("Current firmware", 11, "#666666", false))
            addView(label("IN-V9-251124", 14, "#111111", true).apply { setPadding(0, dp(4), 0, 0) })
            addView(label("Last live message: ${liveDeviceStatusByMac[device.macAddress]?.status ?: "Not read"}", 11, "#555555", false).apply { setPadding(0, dp(4), 0, 0) })
            addView(smallOutlineButton("Earlier versions") { Toast.makeText(requireContext(), "Firmware history", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(8) })
        }
        row.addView(firmware, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun deviceInstallationCard(): LinearLayout {
        val card = cardContainer()
        card.addView(label("INSTALLATION PHOTO + EXHAUST PIPE", 13, "#111111", true))
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        val imageBox = TextView(requireContext()).apply {
            text = "Stored\nsetup image"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#6F7779"))
            background = roundedDrawable(Color.parseColor("#EEF3F4"), Color.parseColor("#D9E0E2"), 1, 10)
        }
        row.addView(imageBox, LinearLayout.LayoutParams(dp(108), dp(78)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(chip("Steam exhaust pipe: Installed", Color.parseColor("#E6F5EA"), "#1FA33B"))
        mid.addView(label("The completed installation photo is stored for reference and location matching.", 12, "#555555", false).apply { setPadding(0, dp(8), 0, dp(8)) })
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(smallOutlineButton("Add installation photo") { Toast.makeText(requireContext(), "Add installation photo", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(8) })
        actions.addView(smallOutlineButton("Manual override") { Toast.makeText(requireContext(), "Manual override", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(36), 1f))
        mid.addView(actions)
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun deviceAccessoriesCard(): LinearLayout {
        val card = cardContainer()
        card.addView(label("ACCESSORIES ASSIGNED TO THIS DEVICE", 13, "#111111", true))
        listOf(
            Triple("SS Pan", "2", "Assigned"),
            Triple("Stirrer", "2", "Assigned"),
            Triple("Rice Stirrer", "1", "Assigned"),
            Triple("Grill Pan", "1", "Assigned"),
            Triple("Momo Basket", "0", "Not assigned"),
            Triple("Frying Basket", "0", "Not assigned"),
            Triple("Cake Mould", "0", "Not assigned")
        ).forEach { card.addView(accessoryRow(it.first, it.second, it.third)) }
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        actions.addView(smallOutlineButton("Edit accessories") { Toast.makeText(requireContext(), "Edit accessories", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) })
        actions.addView(smallOutlineButton("Add accessory photo") { Toast.makeText(requireContext(), "Add accessory photo", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        card.addView(actions)
        return card
    }

    private fun deviceHealthLogsCard(): LinearLayout {
        val card = cardContainer()
        card.addView(label("HEALTH + SERVICE LOGS", 13, "#111111", true))
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(chip("Health: Good", Color.parseColor("#E6F5EA"), "#1FA33B"), LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(6) })
        row.addView(chip("Last error: none today", Color.parseColor("#F7F7F7"), "#555555"), LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(6) })
        row.addView(chip("Service due: 42 days", Color.parseColor("#FFF5DB"), "#A06A00"), LinearLayout.LayoutParams(0, dp(30), 1f))
        card.addView(row)
        card.addView(label("Tap logs to view recipe history, error history and service visits for this device.", 12, "#666666", false).apply { setPadding(0, dp(8), 0, 0) })
        return card
    }

    private fun queueItemRow(name: String, time: String, color: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(label("●", 11, color, true).apply { setPadding(0, 0, dp(4), 0) })
            addView(label(name, 11, "#333333", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(time, 11, "#555555", false))
        }
    }

    private fun statBox(value: String, caption: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.parseColor("#F8FAFA"), Color.parseColor("#E3E8E8"), 1, 10)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(label(value, 16, "#111111", true))
            addView(label(caption, 10, "#666666", false).apply { setPadding(0, dp(4), 0, 0) })
        }
    }

    private fun recipeBarRow(name: String, progress: Float, color: Int): LinearLayout {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        row.addView(label(name, 12, "#333333", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedDrawable(Color.parseColor("#E8ECEC"), Color.parseColor("#E8ECEC"), 0, 4)
        }
        bar.addView(View(requireContext()).apply { background = roundedDrawable(color, color, 0, 4) }, LinearLayout.LayoutParams(0, dp(6), progress))
        bar.addView(Space(requireContext()), LinearLayout.LayoutParams(0, dp(6), 1f - progress))
        row.addView(bar, LinearLayout.LayoutParams(dp(120), dp(6)))
        return row
    }

    private fun accessoryRow(name: String, qty: String, status: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(label(name, 12, "#333333", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(qty, 12, "#111111", true), LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT))
            val ok = status == "Assigned"
            addView(chip(status, if (ok) Color.parseColor("#E6F5EA") else Color.parseColor("#F1F1F1"), if (ok) "#1FA33B" else "#777777"), LinearLayout.LayoutParams(dp(92), dp(26)))
        }
    }

    private fun queueWaitText(device: KitchenDevice): String = when {
        device.queueCount > 0 -> "${device.queueCount * 7 + 15} min"
        device.state == DeviceState.COOKING -> "22 min"
        else -> "0 min"
    }

    private fun deviceHealthText(device: KitchenDevice): String = when (device.state) {
        DeviceState.OFFLINE -> "Offline"
        DeviceState.COOKING -> "Good"
        DeviceState.QUEUED -> "Busy"
        DeviceState.IDLE -> "Ready"
    }

    private fun connectedDeviceMacs(): List<String> {
        val macs = linkedSetOf<String>()
        if (::service.isInitialized) {
            macs.addAll(service.getConnectedDeviceAddresses())
        }
        if (macAddress.isNotBlank() && macAddress != Constants.DummyMacAddress && ::service.isInitialized && service.isDeviceConnected(macAddress)) {
            macs.add(macAddress)
        }
        return macs.toList()
    }

    private fun allKitchenDevices(): List<KitchenDevice> {
        val connected = connectedDeviceMacs().take(7)
        return (1..7).map { number ->
            val mac = connected.getOrNull(number - 1)
            if (mac.isNullOrBlank()) {
                KitchenDevice(number, "Device $number", DeviceState.OFFLINE)
            } else {
                val live = liveDeviceStatusByMac[mac]
                val localCurrent = localCookingItemByMac[mac]
                val queued = localDeviceQueueByMac[mac].orEmpty()
                val isCooking = live?.cooking == true || localCurrent != null
                val state = when {
                    isCooking -> DeviceState.COOKING
                    queued.isNotEmpty() -> DeviceState.QUEUED
                    else -> DeviceState.IDLE
                }
                val itemName = live?.recipeName?.ifBlank { localCurrent?.itemName.orEmpty() } ?: localCurrent?.itemName.orEmpty()
                val remainingSeconds = maxOf(live?.indSeconds ?: 0, live?.magSeconds ?: 0)
                val remainingText = if (remainingSeconds > 0) secondsLabel(remainingSeconds) else localCurrent?.remainingTime?.ifBlank { "--:--" } ?: "--:--"
                val powerValue = maxOf(live?.indPower ?: 0, live?.magPower ?: 0)
                KitchenDevice(
                    number = number,
                    name = "Device $number",
                    state = state,
                    currentItem = itemName,
                    remaining = remainingText,
                    power = if (powerValue > 0) "$powerValue%" else if (state == DeviceState.IDLE) "Ready" else "--",
                    queueCount = queued.size,
                    isFryDevice = number == 1 && itemName.contains("fry", true),
                    macAddress = mac,
                    liveMode = live?.mode.orEmpty(),
                    liveStep = if ((live?.stepNo ?: 0) > 0) "Step ${live?.stepNo}" else ""
                )
            }
        }
    }

    private fun onDevices(): List<KitchenDevice> = allKitchenDevices().filter { it.state != DeviceState.OFFLINE }

    private fun idleOnDevices(): List<KitchenDevice> = onDevices().filter { it.state == DeviceState.IDLE }

    private fun busyOnDevices(): List<KitchenDevice> = onDevices().filter { it.state != DeviceState.IDLE }

    private fun sortedQuickDevices(): List<KitchenDevice> = idleOnDevices() + busyOnDevices()

    private fun selectedQuickDevice(item: KitchenOrderItem): KitchenDevice? {
        val selectedNumber = selectedQuickDeviceForOrder[orderWorkspaceKey(item)]
        return onDevices().firstOrNull { it.number == selectedNumber } ?: idleOnDevices().firstOrNull()
    }

    private fun orderWorkspaceKey(item: KitchenOrderItem): String = item.orderId + item.itemCode

    private fun workspaceDevices(): List<KitchenDevice> = sortedQuickDevices().ifEmpty { allKitchenDevices() }

    private fun syncSelectedAssignDeviceForOrder(item: KitchenOrderItem) {
        val devices = workspaceDevices()
        if (devices.isEmpty()) {
            selectedAssignDevice = null
            return
        }
        val selectedNumber = selectedQuickDeviceForOrder[orderWorkspaceKey(item)] ?: selectedAssignDevice?.number
        selectedAssignDevice = devices.firstOrNull { it.number == selectedNumber } ?: devices.first()
        selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = selectedAssignDevice?.number ?: return
    }

    private fun shiftSelectedWorkspaceDevice(item: KitchenOrderItem, delta: Int) {
        val devices = workspaceDevices()
        if (devices.isEmpty()) return
        val currentNumber = selectedAssignDevice?.number ?: devices.first().number
        val currentIndex = devices.indexOfFirst { it.number == currentNumber }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + delta).coerceIn(0, devices.lastIndex)
        val target = devices[nextIndex]
        selectedAssignDevice = target
        selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = target.number
        renderSelectDevice(item)
    }

    private fun requestAllConnectedDeviceStatuses(force: Boolean = false) {
        if (!::service.isInitialized) return
        val now = System.currentTimeMillis()
        if (!force && now - lastDeviceStatusRequestAt < 1500) return
        lastDeviceStatusRequestAt = now
        connectedDeviceMacs().forEach { mac ->
            if (service.isDeviceConnected(mac)) {
                service.writeData(mac, Constants.STATUS.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun updateLiveDeviceStatusFromMessage(eventMac: String, message: String) {
        if (eventMac.isBlank() || message.isBlank()) return
        val upper = message.uppercase()
        val status = liveDeviceStatusByMac.getOrPut(eventMac) { DeviceRuntimeStatus(eventMac) }
        status.lastMessage = message
        status.lastUpdated = System.currentTimeMillis()
        status.connected = true

        when {
            upper == "RECIPE=NONE" -> {
                status.cooking = false
                status.recipeName = ""
                status.mode = "Recipe missing"
                status.status = "RECIPE_NONE"
                pendingIngredientAutoStartByMac.remove(eventMac)
                val item = localCookingItemByMac.remove(eventMac)
                if (item != null) {
                    item.status = OrderStatus.FAILED
                    item.remainingTime = "Recipe missing"
                    Toast.makeText(requireContext(), "Recipe not found on Device ${deviceNumberForMac(eventMac)}. Upload/mapping required.", Toast.LENGTH_LONG).show()
                }
            }
            upper.contains("RECIPE=COMPLETE") || upper.contains("INSTR_RUN=COMPLETE") || upper == "BAKE=COMPLETE" || upper == "GRILL=COMPLETE" || upper == "STEAM=COMPLETE" || upper == "REHEAT=COMPLETE" || upper == "FRY=COMPLETE" -> {
                status.cooking = false
                status.mode = "Completed"
                status.status = "COMPLETE"
                status.indSeconds = 0
                status.magSeconds = 0
                handleDeviceRecipeCompleted(eventMac)
            }
            upper.contains("WORKSTATUS=IDLE") || upper == "STATUS=IDLE" -> {
                status.cooking = false
                status.recipeName = ""
                status.mode = "Idle"
                status.stepNo = 0
                status.indSeconds = 0
                status.magSeconds = 0
                status.status = "IDLE"
                if (localCookingItemByMac.containsKey(eventMac)) handleDeviceRecipeCompleted(eventMac)
            }
            upper.startsWith("RECIPE=") || upper.contains("RECIPE=") -> {
                status.recipeName = extractBleValue(message, "RECIPE").ifBlank { status.recipeName }
                status.mode = extractBleValue(message, "MODE").ifBlank { "Recipe" }
                status.stepNo = extractBleValue(message, "STEPNO").toIntOrNull() ?: status.stepNo
                status.indSeconds = extractBleValue(message, "IND_RUN").toIntOrNull() ?: status.indSeconds
                status.magSeconds = extractBleValue(message, "MAG_RUN").toIntOrNull() ?: status.magSeconds
                status.indPower = extractBleValue(message, "INDPOWER").toIntOrNull() ?: status.indPower
                status.magPower = extractBleValue(message, "MAGPOWER").toIntOrNull() ?: status.magPower
                status.status = extractBleValue(message, "STATUS").ifBlank { if (status.mode.contains("Ingredient", true)) "INGREDIENT" else "START" }
                status.stirrer = extractBleValue(message, "STIRRER")
                status.pumpOn = extractBleValue(message, "PUMP") == "1"
                status.cooking = status.status.equals("START", true) || status.mode.contains("Cooking", true) || status.indSeconds > 0 || status.magSeconds > 0
                if (status.mode.contains("Ingredient", true)) {
                    val pendingRecipe = pendingIngredientAutoStartByMac.remove(eventMac)
                    if (!pendingRecipe.isNullOrBlank() && ::service.isInitialized && service.isDeviceConnected(eventMac)) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            service.writeData(eventMac, "ingredients=100".toByteArray(Charsets.UTF_8))
                        }, 350)
                    }
                }
            }
            upper.startsWith("FRYQUICKSTART=") -> {
                status.recipeName = "Fry"
                status.mode = "Fry"
                status.status = extractBleValue(message, "FRYQUICKSTART").ifBlank { "RUN" }
                status.indSeconds = extractBleValue(message, "FRYSEC").toIntOrNull() ?: status.indSeconds
                status.magSeconds = extractBleValue(message, "MICROWAVESEC").toIntOrNull() ?: status.magSeconds
                status.indPower = extractBleValue(message, "INDPOWER").toIntOrNull() ?: status.indPower
                status.magPower = extractBleValue(message, "MAGPOWER").toIntOrNull() ?: status.magPower
                status.cooking = !status.status.equals("IDLE", true)
            }
            upper.startsWith("INDQUICKSTART=") || upper.contains("MAGQUICKSTART=") -> {
                status.recipeName = "Manual"
                status.mode = "Manual"
                status.status = if (upper.contains("RUN")) "RUN" else "IDLE"
                status.indSeconds = extractBleValue(message, "IND_RUN").toIntOrNull() ?: status.indSeconds
                status.magSeconds = extractBleValue(message, "MAG_RUN").toIntOrNull() ?: status.magSeconds
                status.indPower = extractBleValue(message, "INDPOWER").toIntOrNull() ?: status.indPower
                status.magPower = extractBleValue(message, "MAGPOWER").toIntOrNull() ?: status.magPower
                status.cooking = status.indSeconds > 0 || status.magSeconds > 0
            }
        }

        if (activeOrderModuleTab == OrderModuleTab.DEVICES) {
            Handler(Looper.getMainLooper()).post {
                val selectedNumber = selectedDeviceDetailNumber
                if (selectedNumber != null) {
                    allKitchenDevices().firstOrNull { it.number == selectedNumber }?.let { renderDeviceDetail(it) } ?: renderDevicesTab()
                } else renderDevicesTab()
            }
        }
    }

    private fun handleDeviceRecipeCompleted(mac: String) {
        val finishedItem = localCookingItemByMac.remove(mac) ?: return
        finishedItem.status = OrderStatus.COMPLETED
        finishedItem.remainingTime = "00:00"
        localDeviceCookedHistoryByMac.getOrPut(mac) { mutableListOf() }.add(0, finishedItem)
        pendingIngredientAutoStartByMac.remove(mac)
        showRecipeCompletedPopup(mac, finishedItem)
    }

    private fun showRecipeCompletedPopup(mac: String, finishedItem: KitchenOrderItem) {
        val deviceNo = deviceNumberForMac(mac)
        val queueCount = localDeviceQueueByMac[mac]?.size ?: 0
        val message = buildString {
            append("Device $deviceNo completed: ${finishedItem.itemName}")
            if (queueCount > 0) append("\n\n$queueCount queued recipe(s) are waiting. Press OK to send the next recipe to Device $deviceNo.")
            else append("\n\nNo queued recipe is pending for this device.")
        }
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Recipe Completed")
            .setMessage(message)
            .setPositiveButton(if (queueCount > 0) "OK · Send Next" else "OK") { _, _ ->
                if (queueCount > 0) startNextQueuedItem(mac)
                else requestAllConnectedDeviceStatuses(force = true)
            }
            .show()
    }

    private fun deviceNumberForMac(mac: String): Int = allKitchenDevices().firstOrNull { it.macAddress == mac }?.number ?: 1

    private fun startNextQueuedItem(mac: String) {
        val queue = localDeviceQueueByMac[mac] ?: return
        if (queue.isEmpty()) return
        val next = queue.removeAt(0)
        val device = allKitchenDevices().firstOrNull { it.macAddress == mac } ?: return
        startOrderItemOnDevice(next, device.copy(state = DeviceState.IDLE))
    }

    private fun extractBleValue(message: String, key: String): String {
        val parts = message.split(",")
        parts.forEach { part ->
            val index = part.indexOf('=')
            if (index > 0 && part.substring(0, index).trim().equals(key, true)) {
                return part.substring(index + 1).trim()
            }
        }
        return ""
    }

    private fun promoteQueuedItemIfDeviceIsIdle(mac: String) {
        // Kept for compatibility with older internal calls. v11 starts the next queue item
        // only after the completion popup is acknowledged by the operator.
        if ((localDeviceQueueByMac[mac]?.size ?: 0) > 0) requestAllConnectedDeviceStatuses(force = true)
    }

    private fun factsLine(item: KitchenOrderItem): String {
        val values = mutableListOf("Qty: ${item.quantity}")
        if (item.portion.isNotBlank()) values.add("Portion: ${item.portion}")
        values.add("Code: ${item.itemCode}")
        return values.joinToString("  |  ")
    }

    private fun pendingPrimaryActionText(item: KitchenOrderItem): String {
        val selected = selectedQuickDevice(item)
        return when {
            idleOnDevices().isEmpty() -> "View Devices"
            selected?.state == DeviceState.IDLE -> "Cook Now"
            selected != null -> "Add to Queue"
            else -> "View Devices"
        }
    }

    private fun handlePendingPrimaryAction(item: KitchenOrderItem) {
        renderSelectDevice(item)
    }

    private fun isDeviceInUse(device: KitchenDevice): Boolean = device.state == DeviceState.COOKING || device.state == DeviceState.QUEUED

    private fun completedHistoryForDevice(device: KitchenDevice): List<Pair<String, String>> {
        val local = localDeviceCookedHistoryByMac[device.macAddress].orEmpty().map { it.itemName to "Done" }
        if (local.isNotEmpty()) return local
        return when (device.number) {
            1 -> listOf("Veg Pulao" to "12:10", "Dal Khichdi" to "12:32", "Butter Chicken" to "12:58")
            2 -> listOf("Masala Poha" to "11:45", "Chicken Nuggets" to "12:05")
            3 -> listOf("Fried Rice" to "11:30", "Paneer Tikka Bowl" to "12:20")
            else -> emptyList()
        }
    }

    private fun queuedItemsForDevice(device: KitchenDevice): List<Pair<String, String>> {
        val local = localDeviceQueueByMac[device.macAddress].orEmpty().mapIndexed { index, item -> item.itemName to "Queue ${index + 1}" }
        return local
    }

    private fun manualRecipeItems(): List<KitchenOrderItem> {
        return availableFirmwareRecipes().mapIndexed { index, recipe -> recipeOrderItemFromRecipe(recipe, index) }
    }

    private fun recipeOrderItemFromRecipe(recipe: Recipe, index: Int): KitchenOrderItem {
        val name = recipe.name.firstOrNull()?.trim().orEmpty().ifBlank { "Recipe ${index + 1}" }
        val code = name.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(6).ifBlank { "R${index + 1}" }
        val image = when {
            name.contains("chicken", true) -> R.drawable.ic_fried_chicken
            name.contains("paneer", true) -> R.drawable.ic_vegetable
            name.contains("rice", true) || name.contains("pulao", true) || name.contains("biryani", true) -> R.drawable.ic_veg_saute
            name.contains("dal", true) || name.contains("khichdi", true) -> R.drawable.ic_urad_dal
            name.contains("poha", true) || name.contains("dosa", true) || name.contains("vada", true) -> R.drawable.ic_thick_poha
            else -> R.drawable.ic_recent_item
        }
        return KitchenOrderItem(
            orderId = "MENU",
            itemName = name,
            itemCode = code,
            quantity = "1 recipe",
            portion = "",
            source = "Menu",
            orderType = "Manual",
            timeAgo = "",
            customer = "",
            total = "",
            accentColor = "#FF6B35",
            status = OrderStatus.PENDING,
            recipe = name,
            imageRes = image
        )
    }

    private fun recipeDurationSeconds(recipe: Recipe): Int {
        return recipe.Instruction.sumOf { instruction ->
            maxOf(
                instruction.Induction_on_time.toIntOrNull() ?: 0,
                instruction.Magnetron_on_time.toIntOrNull() ?: 0,
                instruction.durationInSec
            )
        }.takeIf { it > 0 } ?: 600
    }

    private fun favoriteRecipesGrid(): LinearLayout {
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val recipes = manualRecipeItems().sortedWith(
            compareByDescending<KitchenOrderItem> { pinnedRecipeCodes.contains(it.itemCode) }
                .thenBy { it.itemName }
        )
        val hint = label("Tap the star to pin or unpin recipes. Pinned recipes remain on top.", 11, "#555555", false).apply {
            setPadding(0, 0, 0, dp(8))
        }
        wrap.addView(hint)
        recipes.chunked(2).forEach { pair ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                row.addView(recipeTile(item), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (index == 0) marginEnd = dp(8) })
            }
            wrap.addView(row)
        }
        return wrap
    }

    private fun recipeTile(item: KitchenOrderItem): LinearLayout {
        val pinned = pinnedRecipeCodes.contains(item.itemCode)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, if (pinned) Color.parseColor("#FF6B35") else Color.parseColor("#E0E0E0"), if (pinned) 2 else 1, 12)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(2).toFloat()
            setOnClickListener { renderRecipeDetail(item) }
            val imageFrame = FrameLayout(requireContext())
            imageFrame.addView(foodImage(item.imageRes), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(92)))
            val star = TextView(requireContext()).apply {
                text = if (pinned) "★" else "☆"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#FF6B35"))
                background = roundedDrawable(Color.WHITE, Color.parseColor("#FF6B35"), 1, 18)
                setOnClickListener {
                    if (pinnedRecipeCodes.contains(item.itemCode)) pinnedRecipeCodes.remove(item.itemCode) else pinnedRecipeCodes.add(item.itemCode)
                    renderRecipesTab()
                }
            }
            imageFrame.addView(star, FrameLayout.LayoutParams(dp(34), dp(34), android.view.Gravity.TOP or android.view.Gravity.END).apply { topMargin = dp(4); rightMargin = dp(4) })
            addView(imageFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(92)))
            addView(label(item.itemName, 13, "#111111", true).apply { setPadding(0, dp(8), 0, dp(6)) })
            val infoRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val cookMins = ((findLocalRecipeByName(item.recipe)?.let { recipeDurationSeconds(it) } ?: 600) / 60).coerceAtLeast(1)
            infoRow.addView(chip("$cookMins min", Color.parseColor("#FFF4F0"), "#FF6B35"), LinearLayout.LayoutParams(dp(76), dp(28)).apply { marginEnd = dp(6) })
            if (pinned) infoRow.addView(chip("Pinned", Color.parseColor("#FFF4F0"), "#FF6B35"), LinearLayout.LayoutParams(dp(76), dp(28)))
            addView(infoRow)
            addView(smallColoredButton("Cook Now", "#FF6B35", "#FFFFFF", "#FF6B35") { renderSelectDevice(item) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(8) })
        }.apply { layoutParams = cardLp(0, 4, 0, 10) }
    }

    private fun renderRecipeDetail(item: KitchenOrderItem) {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Recipe Details", true) { renderRecipesTab() })
        val card = cardContainer()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(foodImage(item.imageRes), LinearLayout.LayoutParams(dp(96), dp(96)).apply { marginEnd = dp(14) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label(item.itemName, 17, "#111111", true))
        val recipe = findLocalRecipeByName(item.recipe)
        val cookMins = ((recipe?.let { recipeDurationSeconds(it) } ?: 600) / 60).coerceAtLeast(1)
        mid.addView(label("Cook time: $cookMins min · ${item.quantity}", 12, "#555555", false).apply { setPadding(0, dp(6), 0, 0) })
        val hasStirrer = recipe?.Instruction?.any { it.stirrer_on.isNotBlank() && !it.stirrer_on.equals("Off", true) && it.stirrer_on != "0" } == true
        mid.addView(label(if (hasStirrer) "Accessory: Stirrer / splash guard as per recipe" else "Accessory: Standard pan", 12, "#FF6B35", true).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        card.addView(label("Ingredients", 14, "#111111", true).apply { setPadding(0, dp(12), 0, dp(6)) })
        val ingredients = recipe?.Ingredients?.take(5)?.map { "${it.title} – ${it.weight}" }.orEmpty()
        (if (ingredients.isNotEmpty()) ingredients else listOf("Main ingredient – 500 g", "Oil / water – as per recipe")).forEach {
            card.addView(label("• $it", 12, "#444444", false).apply { setPadding(0, dp(3), 0, 0) })
        }
        card.addView(label("Steps: ${recipe?.Instruction?.size ?: 2}", 12, "#555555", true).apply { setPadding(0, dp(8), 0, 0) })
        card.addView(actionTextButton("Cook Now / Select Device", "#FF6B35", "#FFFFFF", "#FF6B35") { renderSelectDevice(item) })
        content.addView(card)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderRecipesTab() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Recipes", false, null))
        content.addView(label("Current waiting orders stay first. Manual recipes remain available below.", 13, "#666666", false).apply { setPadding(dp(16), 0, dp(16), dp(10)) })
        content.addView(recipeWaitingOrdersStrip())
        content.addView(label("FAVORITE RECIPES", 13, "#111111", true).apply { setPadding(dp(16), dp(8), dp(16), dp(4)) })
        content.addView(favoriteRecipesGrid())
        content.addView(actionTextButton("Open Existing Recipe Library", "#FFFFFF", "#FF6B35", "#FF6B35") {
            binding.clCook.performClick()
        })
        content.addView(actionTextButton("Open Fry Mode", "#FFFFFF", "#FF6B35", "#FF6B35") {
            binding.clFryMode.performClick()
        })
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderMoreTab() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("More", false, null))
        content.addView(moreOption("Login / Settings", if (settingsLoggedIn) "Open account, preferences and cooking preset settings" else "Login is required before settings can be opened") { openSettingsGate() })
        content.addView(moreOption("Manual Control + Preset Modes", "Manual controls and preset modes routed through device selection") { renderManualControlWithPresets() })
        content.addView(moreOption("Back to Opening Screen", "Return to the original Cook / Orders / Fry mode screen") { returnToExistingHomeScreen() })
        content.addView(moreOption("FTP / SD Card Transfer", "Use the existing transfer flow without changing firmware communication") { binding.btnFtp.performClick() })
        content.addView(moreOption("Logs", "Existing log screens and live logs remain in the original app flow") { Toast.makeText(requireContext(), "Use existing Log menu", Toast.LENGTH_SHORT).show() })
        content.addView(moreOption("OTA", "Firmware update remains in the existing app flow") { Toast.makeText(requireContext(), "Use existing OTA menu", Toast.LENGTH_SHORT).show() })
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun openSettingsGate() {
        if (settingsLoggedIn) renderSettingsScreen() else renderLoginScreen()
    }

    private fun renderLoginScreen() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Login", true) { renderMoreTab() })
        val card = cardContainer()
        card.addView(TextView(requireContext()).apply {
            text = "♨"
            textSize = 46f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#FF6B35"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
        card.addView(label("Welcome to On2Cook", 20, "#111111", true).apply { gravity = android.view.Gravity.CENTER; setPadding(0, dp(10), 0, dp(4)) })
        card.addView(label("Login to access Settings, presets and kitchen preferences", 13, "#666666", false).apply { gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, dp(18)) })
        card.addView(loginField("Email / Phone", "admin@on2cook.com"))
        card.addView(loginField("Password", "••••••••"))
        card.addView(actionTextButton("Login", "#FF6B35", "#FFFFFF", "#FF6B35") {
            settingsLoggedIn = true
            renderSettingsScreen()
        })
        card.addView(actionTextButton("Continue as Demo Admin", "#FFFFFF", "#FF6B35", "#FF6B35") {
            settingsLoggedIn = true
            renderSettingsScreen()
        })
        content.addView(card)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun loginField(title: String, value: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
            addView(label(title, 12, "#333333", true).apply { setPadding(0, 0, 0, dp(4)) })
            addView(label(value, 14, "#777777", false).apply {
                background = roundedDrawable(Color.WHITE, Color.parseColor("#D5D5D5"), 1, 8)
                setPadding(dp(12), 0, dp(12), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
        }
    }

    private fun renderSettingsScreen() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Settings", true) { renderMoreTab() })
        content.addView(accountSettingsCard())
        content.addView(settingsSectionLabel("APP SETTINGS"))
        content.addView(settingsOption("Preferences", "App preferences") { renderSimpleSettingsSubScreen("Preferences", listOf("Default opening: Cook / Orders / Fry", "Show recipes below orders", "Order card image size: Large", "Special instruction popup: During cooking")) })
        content.addView(settingsOption("Orders", "Order and queue preferences") { renderSimpleSettingsSubScreen("Orders", listOf("Source priority: Swiggy, Zomato, POS", "Same-order color grouping: On", "Quick chips: ON devices only", "All busy: View Devices")) })
        content.addView(settingsOption("Devices", "Manage devices") { renderSimpleSettingsSubScreen("Devices", listOf("Maximum devices: 7", "Device role: Normal / Fry Device", "Offline devices visible only in full list", "Device queue slider enabled")) })
        content.addView(settingsOption("Recipes", "Recipe and mapping preferences") { renderSimpleSettingsSubScreen("Recipes", listOf("Pin/unpin favorites", "POS item code → recipe mapping", "Missing recipe: show Map Recipe", "Recipe JSON / asset zip sync enabled")) })
        content.addView(settingsOption("Cooking Presets / Mode Presets", "Set time and power for each knob mode") { renderCookingPresetsList() })
        content.addView(settingsOption("Fry Settings", "Fry device and temperature settings") { renderModePresetScreen(modePresets.first { it.key == "fry" }, 0) })
        content.addView(settingsOption("Notifications", "Alert and notification settings") { renderSimpleSettingsSubScreen("Notifications", listOf("New order alert: On", "Cooking complete alert: On", "Device error alert: On", "Queue delay alert: On")) })
        content.addView(settingsOption("Security", "Manage security and access") { renderSimpleSettingsSubScreen("Security", listOf("Require login for settings", "Operator access: Orders + Devices", "Service access: Device health + logs", "Admin can edit presets")) })
        content.addView(settingsSectionLabel("GENERAL"))
        content.addView(settingsOption("About On2Cook", "App and firmware information") { renderVersionInfoScreen() })
        content.addView(settingsOption("Logout", "Sign out from this account", "#E00000") { settingsLoggedIn = false; renderLoginScreen() })
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun accountSettingsCard(): LinearLayout {
        val card = cardContainer()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(TextView(requireContext()).apply {
            text = "RS"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedDrawable(Color.parseColor("#FF6B35"), Color.parseColor("#FF6B35"), 0, 28)
        }, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(14) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label(demoLoggedInUserName, 16, "#111111", true))
        mid.addView(label(demoLoggedInUserEmail, 12, "#666666", false).apply { setPadding(0, dp(4), 0, 0) })
        mid.addView(label("Role: Admin · Outlet: Main Kitchen", 11, "#777777", false).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 28, "#777777", false))
        card.addView(row)
        return card
    }

    private fun settingsSectionLabel(text: String): TextView {
        return label(text, 11, "#777777", true).apply { setPadding(dp(18), dp(12), dp(18), dp(6)) }
    }

    private fun settingsOption(title: String, subtitle: String, titleColor: String = "#111111", action: () -> Unit): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E4E4E4"), 1, 10)
            setPadding(dp(14), dp(12), dp(10), dp(12))
            setOnClickListener { action() }
            val textBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            textBox.addView(label(title, 14, titleColor, true))
            textBox.addView(label(subtitle, 11, "#666666", false).apply { setPadding(0, dp(3), 0, 0) })
            addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("›", 24, "#777777", false))
            layoutParams = cardLp(16, 2, 16, 6)
        }
    }

    private fun renderSimpleSettingsSubScreen(title: String, rows: List<String>) {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader(title, true) { renderSettingsScreen() })
        rows.forEach {
            val card = cardContainer()
            card.addView(label(it, 14, "#333333", false))
            content.addView(card)
        }
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderCookingPresetsList() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Cooking Presets / Mode Presets", true) { renderSettingsScreen() })
        content.addView(label("Create compact multi-step presets for the modes available on the knob. These are converted into hidden recipe JSON files and sent through the existing firmware recipe engine.", 12, "#555555", false).apply { setPadding(dp(16), 0, dp(16), dp(10)) })
        modePresets.forEach { preset ->
            content.addView(settingsOption("${preset.icon}  ${preset.title}", if (preset.isDeviceMode) "${preset.steps.size} steps" else preset.subtitle) {
                if (preset.isDeviceMode) renderModePresetScreen(preset, 0) else renderRecipeModeInfoScreen()
            })
        }
        content.addView(settingsOption("🔊  Volume", "Sound settings") { renderVolumeSettingsScreen() })
        content.addView(settingsOption("ⓘ  Version", "Firmware and hardware information") { renderVersionInfoScreen() })
        val note = cardContainer()
        note.background = roundedDrawable(Color.parseColor("#FFF4EC"), Color.parseColor("#FFE1D2"), 1, 12)
        note.addView(label("Save uploads the preset to the On2Cook device. Run uploads if needed, selects the preset recipe on firmware, and skips the ingredient screen to start cooking.", 12, "#7A3A18", false))
        content.addView(note)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderModePresetScreen(preset: ModePreset, activeStepIndex: Int) {
        val safeIndex = activeStepIndex.coerceIn(0, (preset.steps.size - 1).coerceAtLeast(0))
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("${preset.title} Preset", true) { renderCookingPresetsList() })
        content.addView(stepCountRow(preset, safeIndex))
        content.addView(stepChipsRow(preset, safeIndex))
        preset.steps.forEachIndexed { index, step ->
            content.addView(compactStepCard(preset, step, index, index == safeIndex))
        }
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(8), dp(16), dp(4)) }
        actions.addView(smallOutlineButton("Reset to Default") { Toast.makeText(requireContext(), "Default values retained; editable values stay in this session until backend persistence is added", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        actions.addView(smallFilledButton("Edit Step ${safeIndex + 1}") { renderStepEditor(preset, safeIndex) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(actions)
        content.addView(actionTextButton("Save Preset to Device", "#FFFFFF", "#FF6B35", "#FF6B35") { renderPresetDeviceActionScreen(preset) })
        content.addView(actionTextButton("Run Preset on Device", "#FF6B35", "#FFFFFF", "#FF6B35") { renderSelectDevice(modePresetToOrderItem(preset)) })
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderPresetDeviceActionScreen(preset: ModePreset) {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Save ${preset.title}", true) { renderModePresetScreen(preset, 0) })
        content.addView(label("Choose the connected device that should receive this preset recipe. This only saves the preset; Run uses the normal Select Device flow.", 12, "#555555", false).apply { setPadding(dp(16), 0, dp(16), dp(10)) })
        val devices = onDevices()
        if (devices.isEmpty()) {
            content.addView(cardContainer().apply { addView(label("No On2Cook device connected. Connect a Bluetooth device first.", 13, "#D73C50", true)) })
        } else {
            devices.forEach { device ->
                val card = cardContainer()
                val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                row.addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
                val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                mid.addView(label("Device ${device.number} · ${device.macAddress.takeLast(5)}", 14, "#111111", true))
                mid.addView(label(deviceStatusLine(device), 12, "#555555", false).apply { setPadding(0, dp(4), 0, 0) })
                row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(smallColoredButton("Save", "#FF6B35", "#FFFFFF", "#FF6B35") { saveModePresetToSpecificDevice(preset, device.macAddress) }, LinearLayout.LayoutParams(dp(82), dp(36)))
                card.addView(row)
                content.addView(card)
            }
        }
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun saveModePresetToSpecificDevice(preset: ModePreset, targetMac: String) {
        val recipe = buildFirmwareRecipeFromPreset(preset)
        val recipeName = recipe.name.firstOrNull().orEmpty()
        runtimePresetRecipes[recipeName] = recipe
        if (!::service.isInitialized || !service.isDeviceConnected(targetMac)) {
            Toast.makeText(requireContext(), "Selected device is not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val recipeJson = Gson().toJson(recipe)
        val size = recipeJson.toByteArray(Charsets.UTF_8).size
        service.setJsonFileDataInMap(targetMac, recipeJson)
        service.writeData(targetMac, "DELETE=$recipeName".toByteArray(Charsets.UTF_8))
        Handler(Looper.getMainLooper()).postDelayed({
            service.writeFileData(targetMac, "{\"RECIPE\":\"$recipeName\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(Charsets.UTF_8))
            Toast.makeText(requireContext(), "Saving ${preset.title} to Device ${deviceNumberForMac(targetMac)}", Toast.LENGTH_SHORT).show()
        }, 450)
    }

    private fun stepCountRow(preset: ModePreset, activeIndex: Int): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
            addView(label("Number of Steps", 13, "#333333", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stepCountButton("−") {
                if (preset.steps.size > 1) { preset.steps.removeAt(preset.steps.lastIndex); renderModePresetScreen(preset, activeIndex.coerceAtMost(preset.steps.lastIndex)) }
            }, LinearLayout.LayoutParams(dp(44), dp(38)))
            addView(label(preset.steps.size.toString(), 16, "#111111", true).apply { gravity = android.view.Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(38)))
            addView(stepCountButton("+") {
                if (preset.steps.size < 6) { preset.steps.add(defaultStepFor(preset.steps.size + 1)); renderModePresetScreen(preset, preset.steps.lastIndex) }
            }, LinearLayout.LayoutParams(dp(44), dp(38)))
        }
    }

    private fun stepCountButton(text: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#D7D7D7"), 1, 8)
            setOnClickListener { action() }
        }
    }

    private fun stepChipsRow(preset: ModePreset, activeIndex: Int): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), 0, dp(16), dp(8))
            preset.steps.forEachIndexed { index, _ ->
                addView(TextView(requireContext()).apply {
                    text = "${index + 1}"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setTextColor(if (index == activeIndex) Color.WHITE else Color.parseColor("#555555"))
                    background = roundedDrawable(if (index == activeIndex) Color.parseColor("#FF6B35") else Color.parseColor("#E5E5E5"), Color.TRANSPARENT, 0, 16)
                    setOnClickListener { renderModePresetScreen(preset, index) }
                }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = if (index < preset.steps.lastIndex) dp(8) else 0 })
            }
        }
    }

    private fun compactStepCard(preset: ModePreset, step: ModePresetStep, index: Int, active: Boolean): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, if (active) Color.parseColor("#FF6B35") else Color.parseColor("#E0E0E0"), if (active) 2 else 1, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { renderModePresetScreen(preset, index) }
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(label("Step ${index + 1}", 14, "#111111", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(label("›", 22, if (active) "#FF6B35" else "#777777", true))
            })
            addView(label("Induction: ${secondsLabel(step.inductionSeconds)} · ${step.inductionPower}%    |    Microwave: ${secondsLabel(step.microwaveSeconds)} · ${step.microwavePower}%", 12, "#222222", false).apply { setPadding(0, dp(7), 0, 0) })
            addView(label("Stirrer: ${step.stirrerSpeed} · Pump: ${step.pumpSeconds}s · Spray: ${step.sprayMl}ml · Wait: ${secondsLabel(step.waitRestSeconds)}", 11, "#666666", false).apply { setPadding(0, dp(4), 0, 0) })
            layoutParams = cardLp(16, 2, 16, 8)
        }
    }

    private fun renderStepEditor(preset: ModePreset, index: Int) {
        val step = preset.steps[index]
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Step ${index + 1} of ${preset.steps.size} – ${preset.title}", true) { renderModePresetScreen(preset, index) })
        content.addView(label("Compact step editor. These values are converted into firmware-readable recipe instructions when you save or run the preset.", 12, "#555555", false).apply { setPadding(dp(16), 0, dp(16), dp(8)) })
        val grid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        grid.addView(twoColumnValueRow(
            valueBox("Induction Power", "${step.inductionPower}%", { adjustStep(step, "indPower", -10); renderStepEditor(preset, index) }, { adjustStep(step, "indPower", 10); renderStepEditor(preset, index) }),
            valueBox("Induction Time", secondsLabel(step.inductionSeconds), { adjustStep(step, "indTime", -10); renderStepEditor(preset, index) }, { adjustStep(step, "indTime", 10); renderStepEditor(preset, index) })
        ))
        grid.addView(twoColumnValueRow(
            valueBox("Microwave Power", "${step.microwavePower}%", { adjustStep(step, "magPower", -10); renderStepEditor(preset, index) }, { adjustStep(step, "magPower", 10); renderStepEditor(preset, index) }),
            valueBox("Microwave Time", secondsLabel(step.microwaveSeconds), { adjustStep(step, "magTime", -10); renderStepEditor(preset, index) }, { adjustStep(step, "magTime", 10); renderStepEditor(preset, index) })
        ))
        grid.addView(twoColumnValueRow(
            valueBox("Power Drop After", secondsLabel(step.powerDropAfterSeconds), { adjustStep(step, "dropAfter", -10); renderStepEditor(preset, index) }, { adjustStep(step, "dropAfter", 10); renderStepEditor(preset, index) }),
            valueBox("Power After Drop", "${step.powerAfterDrop}%", { adjustStep(step, "dropPower", -5); renderStepEditor(preset, index) }, { adjustStep(step, "dropPower", 5); renderStepEditor(preset, index) })
        ))
        grid.addView(twoColumnValueRow(
            valueBox("Pump Duration", "${step.pumpSeconds}s", { adjustStep(step, "pump", -1); renderStepEditor(preset, index) }, { adjustStep(step, "pump", 1); renderStepEditor(preset, index) }),
            valueBox("Spray", "${step.sprayMl} ml", { adjustStep(step, "spray", -1); renderStepEditor(preset, index) }, { adjustStep(step, "spray", 1); renderStepEditor(preset, index) })
        ))
        grid.addView(twoColumnValueRow(
            valueBox("Threshold Temp", "${step.thresholdTemp}°C", { adjustStep(step, "threshold", -5); renderStepEditor(preset, index) }, { adjustStep(step, "threshold", 5); renderStepEditor(preset, index) }),
            valueBox("Wait / Rest", secondsLabel(step.waitRestSeconds), { adjustStep(step, "wait", -10); renderStepEditor(preset, index) }, { adjustStep(step, "wait", 10); renderStepEditor(preset, index) })
        ))
        grid.addView(twoColumnValueRow(
            choiceBox("Stirrer Speed", step.stirrerSpeed) { step.stirrerSpeed = nextChoice(presetStirrerSpeeds, step.stirrerSpeed); renderStepEditor(preset, index) },
            choiceBox("Lid Status", step.lidStatus) { step.lidStatus = nextChoice(presetLidStates, step.lidStatus); renderStepEditor(preset, index) }
        ))
        content.addView(grid)
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(10), dp(16), dp(4)) }
        actions.addView(smallOutlineButton("Cancel") { renderModePresetScreen(preset, index) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        actions.addView(smallFilledButton("Save Step") { renderModePresetScreen(preset, index) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(actions)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun twoColumnValueRow(left: View, right: View): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
    }

    private fun valueBox(title: String, value: String, minus: () -> Unit, plus: () -> Unit): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(label(title, 11, "#555555", false))
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            row.addView(label(value, 14, "#111111", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tinyAdjustButton("−") { minus() }, LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(4) })
            row.addView(tinyAdjustButton("+") { plus() }, LinearLayout.LayoutParams(dp(32), dp(28)))
            addView(row)
        }
    }

    private fun choiceBox(title: String, value: String, action: () -> Unit): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { action() }
            addView(label(title, 11, "#555555", false))
            addView(label("$value  ▾", 14, "#111111", true).apply { setPadding(0, dp(8), 0, 0) })
        }
    }

    private fun tinyAdjustButton(text: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
            background = roundedDrawable(Color.parseColor("#F7F7F7"), Color.parseColor("#D5D5D5"), 1, 7)
            setOnClickListener { action() }
        }
    }

    private fun adjustStep(step: ModePresetStep, field: String, delta: Int) {
        when (field) {
            "indPower" -> step.inductionPower = (step.inductionPower + delta).coerceIn(0, 100)
            "indTime" -> step.inductionSeconds = (step.inductionSeconds + delta).coerceIn(0, 7200)
            "magPower" -> step.microwavePower = (step.microwavePower + delta).coerceIn(0, 100)
            "magTime" -> step.microwaveSeconds = (step.microwaveSeconds + delta).coerceIn(0, 7200)
            "dropAfter" -> step.powerDropAfterSeconds = (step.powerDropAfterSeconds + delta).coerceIn(0, 7200)
            "dropPower" -> step.powerAfterDrop = (step.powerAfterDrop + delta).coerceIn(0, 100)
            "pump" -> step.pumpSeconds = (step.pumpSeconds + delta).coerceIn(0, 120)
            "spray" -> step.sprayMl = (step.sprayMl + delta).coerceIn(0, 100)
            "threshold" -> step.thresholdTemp = (step.thresholdTemp + delta).coerceIn(0, 240)
            "wait" -> step.waitRestSeconds = (step.waitRestSeconds + delta).coerceIn(0, 7200)
        }
    }

    private fun nextChoice(values: List<String>, current: String): String {
        val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
        return values[(index + 1) % values.size]
    }

    private fun defaultStepFor(stepNumber: Int): ModePresetStep {
        return ModePresetStep(stepNumber, 40, 120, 80, 120, 0, 0, "Medium")
    }

    private fun secondsLabel(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }


    private fun handleModePresetUploadSuccess(sourceMac: String = macAddress) {
        val recipeName = pendingPresetRecipeNameToRun
        if (recipeName.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Preset saved to device", Toast.LENGTH_SHORT).show()
            return
        }
        syncedPresetRecipeNames.add(recipeName)
        pendingPresetRecipeNameToRun = null
        Toast.makeText(requireContext(), "Preset uploaded. Starting $recipeName", Toast.LENGTH_SHORT).show()
        sendPresetRecipeRunCommand(recipeName, autoStartAfterIngredient = true)
    }

    private fun runModePresetOnDevice(preset: ModePreset) {
        val recipeName = presetFirmwareRecipeName(preset)
        if (syncedPresetRecipeNames.contains(recipeName)) {
            sendPresetRecipeRunCommand(recipeName, autoStartAfterIngredient = true)
        } else {
            uploadModePresetToDevice(preset, runAfterUpload = true)
        }
    }

    private fun uploadModePresetToDevice(preset: ModePreset, runAfterUpload: Boolean) {
        if (!preset.isDeviceMode) {
            Toast.makeText(requireContext(), "Recipe mode uses normal recipe files", Toast.LENGTH_SHORT).show()
            return
        }
        if (!::service.isInitialized) {
            initializeService()
            Toast.makeText(requireContext(), "Connecting to device. Please tap again once connected.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!service.isDeviceConnected(macAddress)) {
            Toast.makeText(requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT).show()
            return
        }

        val recipe = buildFirmwareRecipeFromPreset(preset)
        val recipeName = recipe.name.firstOrNull().orEmpty()
        val recipeJson = Gson().toJson(recipe)
        val size = recipeJson.toByteArray(Charsets.UTF_8).size
        if (recipeName.length >= 32) {
            Toast.makeText(requireContext(), "Preset name too long for firmware", Toast.LENGTH_SHORT).show()
            return
        }

        runtimePresetRecipes[recipeName] = recipe
        val existingIndex = list.indexOfFirst { it.name.firstOrNull() == recipeName }
        if (existingIndex >= 0) list[existingIndex] = recipe else list.add(recipe)
        if (runAfterUpload) pendingPresetRecipeNameToRun = recipeName

        // Re-save support: the firmware may reject an existing recipe name. Delete first, then upload.
        service.writeData(macAddress, "DELETE=$recipeName".toByteArray(Charsets.UTF_8))
        Handler(Looper.getMainLooper()).postDelayed({
            service.writeFileData(
                macAddress,
                "{\"RECIPE\":\"$recipeName\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(Charsets.UTF_8)
            )
            service.setJsonFileDataInMap(macAddress, recipeJson)
            Toast.makeText(requireContext(), if (runAfterUpload) "Uploading preset to device…" else "Saving preset to device…", Toast.LENGTH_SHORT).show()
        }, 450)
    }

    private fun sendPresetRecipeRunCommand(recipeName: String, autoStartAfterIngredient: Boolean) {
        if (!::service.isInitialized || !service.isDeviceConnected(macAddress)) {
            Toast.makeText(requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT).show()
            return
        }
        service.writeData(macAddress, "recipe=$recipeName".toByteArray(Charsets.UTF_8))
        if (autoStartAfterIngredient) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (::service.isInitialized && service.isDeviceConnected(macAddress)) {
                    // Existing firmware command: this advances through the recipe ingredient stage.
                    service.writeData(macAddress, "ingredients=100".toByteArray(Charsets.UTF_8))
                }
            }, 1200)
        }
    }

    private fun presetFirmwareRecipeName(preset: ModePreset): String {
        val cleanKey = preset.key.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
        return ("APP_" + cleanKey).take(30)
    }

    private fun buildFirmwareRecipeFromPreset(preset: ModePreset): Recipe {
        val recipeName = presetFirmwareRecipeName(preset)
        val recipe = Recipe().apply {
            name = arrayListOf(recipeName)
            audio1 = arrayListOf("")
            audio2 = arrayListOf("")
            description = "On2Cook app preset generated from ${preset.title}"
            imageUrl = ""
            tags = "app-preset,${preset.key}"
            difficulty = "preset"
            category = if (preset.key == "fry") "1" else "0"
            subCategories = "mode-preset"
            Ingredients = arrayListOf(
                Ingredients(1, "Prepare ${preset.title}", "Keep pan/vessel ready for ${preset.title}", ""),
                Ingredients(2, "Now Cook", "App preset will run ${preset.steps.size} configured step(s)", "")
            )
            Instruction = buildFirmwareInstructionsFromPreset(preset)
        }
        return recipe
    }

    private fun buildFirmwareInstructionsFromPreset(preset: ModePreset): ArrayList<Instructions> {
        val instructions = arrayListOf<Instructions>()
        preset.steps.forEach { step ->
            val firstDuration = step.powerDropAfterSeconds
            val maxDuration = maxOf(step.inductionSeconds, step.microwaveSeconds)
            if (firstDuration > 0 && firstDuration < maxDuration && step.powerAfterDrop >= 0) {
                instructions.add(firmwareInstructionForStep(preset, step, instructions.size + 1, 0, firstDuration, false))
                instructions.add(firmwareInstructionForStep(preset, step, instructions.size + 1, firstDuration, maxDuration - firstDuration, true))
            } else {
                instructions.add(firmwareInstructionForStep(preset, step, instructions.size + 1, 0, maxDuration, false))
            }
        }
        if (instructions.isEmpty()) {
            instructions.add(firmwareInstructionForStep(preset, defaultStepFor(1), 1, 0, 120, false))
        }
        return instructions
    }

    private fun firmwareInstructionForStep(
        preset: ModePreset,
        step: ModePresetStep,
        instructionId: Int,
        offsetSeconds: Int,
        segmentSeconds: Int,
        useDropPower: Boolean
    ): Instructions {
        val indTime = segmentRemaining(step.inductionSeconds, offsetSeconds, segmentSeconds)
        val magTime = segmentRemaining(step.microwaveSeconds, offsetSeconds, segmentSeconds)
        val dropPower = step.powerAfterDrop.coerceIn(0, 100)
        val indPower = if (useDropPower && step.inductionPower > 0) dropPower else step.inductionPower
        val magPower = if (useDropPower && step.microwavePower > 0) dropPower else step.microwavePower
        return Instructions().apply {
            id = instructionId
            Text = "${preset.title} Step ${step.stepNumber}${if (useDropPower) " drop" else ""}"
            app_audio = Text
            Weight = ""
            Magnetron_on_time = magTime.toString()
            Magnetron_power = magPower.coerceIn(0, 100).toString()
            Induction_on_time = indTime.toString()
            Induction_power = indPower.coerceIn(0, 100).toString()
            lid = when (step.lidStatus.lowercase()) {
                "open" -> "open"
                else -> "close"
            }
            threshold = step.thresholdTemp.toString()
            stirrer_on = firmwareStirrerCode(step.stirrerSpeed)
            pump_on = step.pumpSeconds.toString()
            purge_on = "0"
            wait_time = step.waitRestSeconds.toString()
            warm_time = "0"
            skip = "false"
            mag_severity = "low"
            Indtime_lid_con = "0"
            durationInSec = maxOf(indTime, magTime) + step.waitRestSeconds
            audioP = ""
            audioQ = ""
            audioI = ""
            audioU = ""
        }
    }

    private fun segmentRemaining(totalSeconds: Int, offsetSeconds: Int, segmentSeconds: Int): Int {
        if (totalSeconds <= offsetSeconds) return 0
        return minOf(totalSeconds - offsetSeconds, segmentSeconds).coerceAtLeast(0)
    }

    private fun firmwareStirrerCode(speed: String): String {
        return when (speed.lowercase().replace(" ", "")) {
            "low" -> "1"
            "medium", "med" -> "2"
            "high" -> "3"
            "vhigh", "veryhigh" -> "4"
            else -> "0"
        }
    }

    private fun renderRecipeModeInfoScreen() {
        renderSimpleSettingsSubScreen("Recipe Mode", listOf(
            "Recipe mode uses recipe JSON instructions, not a fixed one-row preset.",
            "Ingredients and instruction steps come from recipe files.",
            "Recipe steps include induction, microwave, wait, warm, stirrer, pump, lid and threshold data.",
            "Use Recipes settings for pinning, mapping and JSON sync."
        ))
    }

    private fun renderVolumeSettingsScreen() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Volume", true) { renderCookingPresetsList() })
        val card = cardContainer()
        card.addView(label("Master Volume", 14, "#111111", true))
        card.addView(progressBar(0.8f, Color.parseColor("#FF6B35")))
        listOf("Button Beep: On", "Alert Sound: On", "Cooking Complete Sound: On", "Error Sound: On", "Reminder Sound: On", "Voice Prompts: On").forEach { card.addView(label(it, 13, "#333333", false).apply { setPadding(0, dp(10), 0, 0) }) }
        card.addView(actionTextButton("Save", "#FF6B35", "#FFFFFF", "#FF6B35") { Toast.makeText(requireContext(), "Volume settings saved", Toast.LENGTH_SHORT).show() })
        content.addView(card)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderVersionInfoScreen() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Version (Info)", true) { renderSettingsScreen() })
        val card = cardContainer()
        listOf(
            "App Version" to "2.1.0",
            "Build Number" to "210",
            "Device Name" to "On2Cook-02",
            "MAC Address" to "D4:6F:6E:2A:11:02",
            "Device Type" to "On2Cook",
            "Hardware Version" to "v1.3",
            "Firmware Version" to "IN-V9-251124",
            "Firmware Date" to "25 Nov 2024"
        ).forEach { card.addView(infoRow(it.first, it.second)) }
        card.addView(actionTextButton("Check for Firmware Update", "#FFFFFF", "#FF6B35", "#FF6B35") { Toast.makeText(requireContext(), "Uses existing OTA flow", Toast.LENGTH_SHORT).show() })
        content.addView(card)
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun renderManualControlWithPresets() {
        val root = baseScrollRoot()
        val content = root.getChildAt(0) as LinearLayout
        content.addView(screenHeader("Manual Control", true) { renderMoreTab() })
        val card = cardContainer()
        card.addView(label("MANUAL CONTROL", 11, "#777777", true))
        card.addView(manualControlRow("Induction", "100%", "10:00", true))
        card.addView(manualControlRow("Microwave", "100%", "10:00", true))
        card.addView(label("Stirrer Speed", 13, "#111111", true).apply { setPadding(0, dp(10), 0, dp(6)) })
        val stirRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Off", "Low", "Med", "High", "V.High").forEach { speed ->
            stirRow.addView(filterPill(speed, speed == "Med"), LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) })
        }
        card.addView(stirRow)
        card.addView(manualControlRow("Pump", "0 sec", "", false))
        card.addView(manualControlRow("Spray", "0 ml", "", false))
        content.addView(card)

        content.addView(label("PRESET MODES", 12, "#555555", true).apply { setPadding(dp(16), dp(8), dp(16), dp(4)) })
        val modeGrid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        modePresets.filter { it.isDeviceMode }.chunked(3).forEach { rowItems ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { preset ->
                row.addView(presetModeTile(preset), LinearLayout.LayoutParams(0, dp(92), 1f).apply { marginEnd = dp(8) })
            }
            modeGrid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(98)))
        }
        content.addView(modeGrid)
        content.addView(actionTextButton("View / Edit Mode Presets", "#FFFFFF", "#FF6B35", "#FF6B35") { renderCookingPresetsList() })
        content.addView(label("RECOMMENDED RECIPES", 12, "#555555", true).apply { setPadding(dp(16), dp(8), dp(16), dp(4)) })
        content.addView(favoriteRecipesGrid())
        content.addView(bottomSpacer())
        setHost(root)
    }

    private fun manualControlRow(title: String, value: String, time: String, showPower: Boolean): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(label(title, 14, if (showPower) "#FF6B35" else "#333333", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(value, 13, "#222222", true), LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
            if (time.isNotBlank()) addView(label(time, 13, "#222222", false), LinearLayout.LayoutParams(dp(66), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(tinyAdjustButton("−") {}, LinearLayout.LayoutParams(dp(34), dp(30)).apply { marginEnd = dp(4) })
            addView(tinyAdjustButton("+") {}, LinearLayout.LayoutParams(dp(34), dp(30)))
        }
    }

    private fun presetModeTile(preset: ModePreset): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 10)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { renderSelectDevice(modePresetToOrderItem(preset)) }
            addView(label(preset.icon, 22, "#FF6B35", true).apply { gravity = android.view.Gravity.CENTER })
            addView(label(preset.title, 11, "#111111", true).apply { gravity = android.view.Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun modePresetToOrderItem(preset: ModePreset): KitchenOrderItem {
        val recipe = buildFirmwareRecipeFromPreset(preset)
        val name = presetFirmwareRecipeName(preset)
        runtimePresetRecipes[name] = recipe
        return KitchenOrderItem(
            orderId = "PRESET",
            itemName = preset.title,
            itemCode = preset.key.uppercase().take(8),
            quantity = "${preset.steps.size} steps",
            portion = "",
            source = "Preset",
            orderType = "Manual",
            timeAgo = "",
            customer = "",
            total = "",
            accentColor = "#FF6B35",
            status = OrderStatus.PENDING,
            recipe = name,
            imageRes = R.drawable.ic_recent_item
        )
    }

    private fun orderTopToggle(mode: OrderListMode): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        val current = tabButton("CURRENT ORDERS", mode == OrderListMode.CURRENT) { renderOrdersScreen(OrderListMode.CURRENT) }
        val previous = tabButton("PREVIOUS ORDERS", mode == OrderListMode.PREVIOUS) { renderOrdersScreen(OrderListMode.PREVIOUS) }
        row.addView(current, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        row.addView(previous, LinearLayout.LayoutParams(0, dp(42), 1f))
        return row
    }

    private fun refreshRow(): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(2), dp(18), dp(8))
        }
        row.addView(label("Last updated: Just now   ↻", 12, "#333333", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("Auto refresh", 12, "#333333", false).apply { setPadding(0, 0, dp(8), 0) })
        row.addView(TextView(requireContext()).apply {
            text = ""
            background = roundedDrawable(Color.parseColor("#FF8A55"), Color.parseColor("#FF8A55"), 0, 14)
        }, LinearLayout.LayoutParams(dp(42), dp(24)))
        return row
    }

    private fun sectionCard(parent: LinearLayout, title: String, items: List<KitchenOrderItem>, color: Int, subtitle: String) {
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }
        header.addView(label("● $title (${items.size})", 15, String.format("#%06X", 0xFFFFFF and color), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (subtitle.isNotBlank()) {
            header.addView(label(subtitle, 11, "#555555", false))
        }
        parent.addView(header)
        items.forEach { parent.addView(orderItemCard(it)) }
    }

    private fun orderItemCard(item: KitchenOrderItem): LinearLayout {
        val accent = Color.parseColor(item.accentColor)
        val statusColorValue = statusColor(item.status)
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 12)
            setPadding(0, 0, 0, dp(10))
            elevation = dp(2).toFloat()
        }

        val main = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, dp(10), 0)
        }
        main.addView(View(requireContext()).apply { background = roundedDrawable(accent, accent, 0, 12) }, LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT))

        val sourceCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(6), dp(8))
        }
        sourceCol.addView(sourceCircle(item.source), LinearLayout.LayoutParams(dp(44), dp(44)))
        sourceCol.addView(label(item.source, 10, colorToHex(sourceColor(item.source)), true).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        main.addView(sourceCol, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))

        val middle = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val firstLine = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        firstLine.addView(label("Order ID: ${item.orderId}", 13, item.accentColor, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        firstLine.addView(label(item.timeAgo, 11, "#333333", false))
        middle.addView(firstLine)
        middle.addView(label(item.itemName, 16, "#111111", true).apply { setPadding(0, dp(3), 0, dp(2)) })
        middle.addView(label(factsLine(item), 12, "#333333", false))
        if (item.assignedDevice.isNotBlank()) {
            middle.addView(label("Device: ${item.assignedDevice}   ${item.remainingTime} remaining", 12, "#333333", false).apply { setPadding(0, dp(2), 0, 0) })
        }
        if (item.specialInstruction.isNotBlank()) {
            val instruction = label("⚑ Special Instruction: ${item.specialInstruction}", 12, "#9D1D1D", true).apply {
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = roundedDrawable(Color.parseColor("#FFF4F0"), Color.parseColor("#D94B4B"), 1, 6)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            middle.addView(instruction, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        main.addView(middle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val right = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), 0, 0)
        }
        right.addView(chip(compactStatusText(item.status).uppercase(), lightenColor(statusColorValue), colorToHex(statusColorValue)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)))
        right.addView(foodImage(item.imageRes), LinearLayout.LayoutParams(dp(78), dp(78)).apply { topMargin = dp(6) })
        main.addView(right, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(main)

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), 0)
        }
        actionRow.addView(smallOutlineButton("▯  View Details") { renderOrderDetails(item) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) })
        when (item.status) {
            OrderStatus.COOKING -> {
                actionRow.addView(smallOutlineButton("▤  ${item.assignedDevice.ifBlank { "Device" }}") { renderSelectDevice(item) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) })
                actionRow.addView(smallColoredButton("Ⅱ  Pause", "#D73C50", "#FFFFFF", "#D73C50") { Toast.makeText(requireContext(), "Pause ${item.orderId}", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(38), 0.75f))
            }
            OrderStatus.COMPLETED, OrderStatus.SHIPPED -> {
                actionRow.addView(smallOutlineButton("↻  Repeat") { renderSelectDevice(item) }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            OrderStatus.FAILED, OrderStatus.CANCELLED -> {
                actionRow.addView(smallColoredButton("Repeat / Re-cook", "#FF6B35", "#FFFFFF", "#FF6B35") { renderSelectDevice(item) }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            else -> {
                actionRow.addView(quickDeviceChipRow(item), LinearLayout.LayoutParams(0, dp(42), 1.25f).apply { marginEnd = dp(8) })
                val actionText = pendingPrimaryActionText(item)
                actionRow.addView(smallColoredButton(actionText, "#FF6B35", "#FFFFFF", "#FF6B35") { handlePendingPrimaryAction(item) }, LinearLayout.LayoutParams(0, dp(42), 0.85f))
            }
        }
        card.addView(actionRow)
        return card.apply {
            layoutParams = cardLp(14, 4, 14, 10)
            setOnClickListener { renderSelectDevice(item) }
        }
    }

    private fun previousOrderCard(item: KitchenOrderItem): LinearLayout {
        return orderItemCard(item)
    }

    private fun orderIdLine(item: KitchenOrderItem): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(label("Order ID: ${item.orderId}", 12, item.accentColor, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(chip(statusText(item.status), statusColor(item.status), "#FFFFFF"))
        }
    }

    private fun quickDeviceChipRow(item: KitchenOrderItem): LinearLayout {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val hsv = HorizontalScrollView(requireContext()).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        fun arrow(text: String, dx: Int): TextView = TextView(requireContext()).apply {
            this.text = text
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#444444"))
            setOnClickListener { hsv.smoothScrollBy(dx, 0) }
        }
        wrapper.addView(arrow("‹", -dp(96)), LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.MATCH_PARENT))
        sortedQuickDevices().forEach { device ->
            row.addView(quickDeviceChip(item, device), LinearLayout.LayoutParams(dp(44), dp(38)).apply { marginEnd = dp(6) })
        }
        hsv.addView(row)
        wrapper.addView(hsv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        wrapper.addView(arrow("›", dp(96)), LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.MATCH_PARENT))
        return wrapper
    }

    private fun quickDeviceChip(item: KitchenOrderItem, device: KitchenDevice): TextView {
        val selected = selectedQuickDeviceForOrder[item.orderId + item.itemCode] == device.number
        val inUse = isDeviceInUse(device)
        val fill = when {
            selected -> Color.parseColor("#263238")
            inUse -> Color.parseColor("#FFF1F1")
            else -> Color.WHITE
        }
        val stroke = when {
            selected -> Color.parseColor("#263238")
            inUse -> Color.parseColor("#D73C50")
            else -> Color.parseColor("#90A4AE")
        }
        val textColor = when {
            selected -> Color.WHITE
            inUse -> Color.parseColor("#D73C50")
            else -> Color.parseColor("#263238")
        }
        return TextView(requireContext()).apply {
            text = "${device.number}\n${if (inUse) "Busy" else "Free"}"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(textColor)
            background = roundedDrawable(fill, stroke, 1, 6)
            includeFontPadding = false
            setOnClickListener {
                selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = device.number
                selectedAssignDevice = device
                renderSelectDevice(item)
            }
        }
    }

    private fun quickDevicesCard(): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 12)
            setPadding(dp(12), dp(10), dp(12), dp(12))
            elevation = dp(2).toFloat()
        }
        val head = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        head.addView(label("QUICK DEVICES (ON DEVICES ONLY)", 13, "#111111", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(label("View Devices ›", 13, "#FF6B35", true).apply { setOnClickListener { selectOrderModuleTab(OrderModuleTab.DEVICES) } })
        card.addView(head)
        card.addView(label("Free devices are shown first. Busy devices are red and can receive queued items.", 11, "#555555", false).apply { setPadding(0, dp(4), 0, 0) })
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        val devices = sortedQuickDevices()
        if (devices.isEmpty()) {
            card.addView(label("No On2Cook device connected. Connect Bluetooth device to enable Cook Now.", 12, "#D73C50", true).apply { setPadding(0, dp(10), 0, 0) })
        } else {
            devices.forEach { device ->
                row.addView(deviceQuickCard(device), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginEnd = dp(6); topMargin = dp(8) })
            }
            card.addView(row)
        }
        return card.apply { layoutParams = cardLp(14, 0, 14, 10) }
    }

    private fun deviceQuickCard(device: KitchenDevice): LinearLayout {
        val inUse = isDeviceInUse(device)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            background = roundedDrawable(if (inUse) Color.parseColor("#FFF1F1") else Color.WHITE, if (inUse) Color.parseColor("#D73C50") else Color.parseColor("#90A4AE"), 1, 9)
            addView(label("Device ${device.number}", 11, if (inUse) "#D73C50" else "#263238", true))
            addView(label(if (inUse) "In use" else "Free", 11, if (inUse) "#D73C50" else "#263238", true).apply { setPadding(0, dp(4), 0, 0) })
        }
    }

    private fun previousOrderFilters(): LinearLayout {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        listOf("All", "Cooked", "Shipped", "Failed", "Cancelled").forEachIndexed { index, t ->
            row.addView(filterPill(t, index == 0), LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = if (index < 4) dp(6) else 0 })
        }
        return row
    }

    private fun orderDetailHeader(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(sourceCircle(item.source), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label("Order ID: ${item.orderId}", 16, item.accentColor, true))
        mid.addView(label("Ordered: 07 May 2025, 12:41 PM", 12, "#333333", false).apply { setPadding(0, dp(4), 0, 0) })
        mid.addView(label("Source: ${item.source}        Payment: Online", 12, "#333333", false).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip(statusText(item.status), lightenColor(statusColor(item.status)), colorToHex(statusColor(item.status))))
        card.addView(row)
        return card
    }

    private fun orderItemDetails(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("ITEM DETAILS", 13, "#1167D8", true))
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(foodImage(item.imageRes), LinearLayout.LayoutParams(dp(96), dp(96)).apply { marginEnd = dp(14) })
        val details = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        details.addView(label(item.itemName, 15, "#111111", true))
        details.addView(label(factsLine(item), 12, "#333333", false).apply { setPadding(0, dp(4), 0, 0) })
        if (item.specialInstruction.isNotBlank()) {
            details.addView(label("Special Instruction", 12, "#333333", true).apply { setPadding(0, dp(8), 0, dp(3)) })
            details.addView(label(item.specialInstruction, 12, "#9D1D1D", true).apply {
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = roundedDrawable(Color.parseColor("#FFF4F0"), Color.parseColor("#D94B4B"), 1, 6)
            })
        }
        row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun cookingStatusBlock(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("COOKING STATUS", 13, "#1167D8", true))
        card.addView(twoCol("Device", item.assignedDevice.ifBlank { "On2Cook-01" }))
        card.addView(twoCol("Status", "Cooking"))
        card.addView(twoCol("Started At", "12:41 PM"))
        card.addView(twoCol("Elapsed Time", "02:30"))
        card.addView(twoCol("Remaining Time", item.remainingTime.ifBlank { "02:00" }))
        card.addView(progressBar(0.53f, Color.parseColor("#1167D8")))
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(smallOutlineButton("Pause") { Toast.makeText(requireContext(), "Pause command will be connected to BLE service", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) })
        row.addView(smallOutlineButton("Move to Another Device") { renderSelectDevice(item) }, LinearLayout.LayoutParams(0, dp(40), 1.3f))
        card.addView(row)
        card.addView(actionTextButton("Abort / Cancel", "#FFFFFF", "#E00000", "#E00000") { Toast.makeText(requireContext(), "Abort ${item.orderId}", Toast.LENGTH_SHORT).show() })
        return card
    }

    private fun pendingAssignmentBlock(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("DEVICE ASSIGNMENT", 13, "#1167D8", true))
        card.addView(label("Choose an ON device. Free devices appear first; busy devices can receive this item in queue.", 12, "#555555", false).apply { setPadding(0, dp(4), 0, dp(8)) })
        sortedQuickDevices().take(5).forEach { device -> card.addView(compactDeviceAssignRow(item, device)) }
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(smallFilledButton("Assign Device") { renderSelectDevice(item) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        row.addView(smallOutlineButton("Auto Assign") { Toast.makeText(requireContext(), "Auto Assign will follow order settings", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(row)
        return card
    }

    private fun completedInfoBlock(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("COOKING INFO", 13, "#1FA33B", true))
        card.addView(twoCol("Device", item.assignedDevice.ifBlank { "On2Cook-01" }))
        card.addView(twoCol("Started At", "10:48 AM"))
        card.addView(twoCol("Completed At", "11:25 AM"))
        card.addView(twoCol("Cook Time", "00:37"))
        card.addView(actionTextButton("Repeat Order", "#FFFFFF", "#1FA33B", "#1FA33B") { renderSelectDevice(item) })
        return card
    }

    private fun failureInfoBlock(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("FAILURE INFO", 13, "#E00000", true))
        card.addView(twoCol("Reason", if (item.status == OrderStatus.CANCELLED) "Operator cancelled" else "Dish spoiled before completion"))
        card.addView(twoCol("Failed At", "10:35 AM"))
        card.addView(twoCol("Note", item.specialInstruction.ifBlank { "Customer may request repeat" }))
        card.addView(actionTextButton("Repeat Order", "#FFFFFF", "#FF6B35", "#FF6B35") { renderSelectDevice(item) })
        card.addView(actionTextButton("Cancel Item", "#FFFFFF", "#E00000", "#E00000") { Toast.makeText(requireContext(), "Cancel ${item.orderId}", Toast.LENGTH_SHORT).show() })
        return card
    }

    private fun orderSummaryBlock(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        card.addView(label("ORDER SUMMARY", 13, "#1167D8", true))
        card.addView(twoCol("Subtotal (1 item)", item.total))
        card.addView(twoCol("Taxes (GST 2.5% + SGST 2.5%)", "₹12.20"))
        card.addView(twoCol("Packaging Charge", "₹20.00"))
        card.addView(twoCol("Grand Total", item.total, true))
        return card
    }

    private fun selectDeviceOrderHeader(item: KitchenOrderItem): LinearLayout {
        val card = cardContainer()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(foodImage(item.imageRes), LinearLayout.LayoutParams(dp(84), dp(84)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label(item.itemName, 16, "#111111", true))
        mid.addView(label("Order ID: ${item.orderId}     Source: ${item.source}", 12, "#333333", false).apply { setPadding(0, dp(4), 0, 0) })
        mid.addView(label(factsLine(item), 12, "#333333", false))
        if (item.specialInstruction.isNotBlank()) mid.addView(label("Special Instruction: ${item.specialInstruction}", 12, "#9D1D1D", true).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun orderWorkspaceDevicePager(item: KitchenOrderItem): LinearLayout {
        val devices = workspaceDevices()
        val selected = selectedAssignDevice ?: devices.firstOrNull()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
            }
            row.addView(smallOutlineButton("<") { shiftSelectedWorkspaceDevice(item, -1) }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) })
            row.addView(
                if (selected != null) orderWorkspaceSelectedDeviceCard(item, selected) else emptyDeviceWorkspaceCard(),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(smallOutlineButton(">") { shiftSelectedWorkspaceDevice(item, 1) }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) })
            addView(row)
            if (devices.isNotEmpty() && selected != null) {
                addView(orderWorkspaceDots(item, devices, selected), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    topMargin = dp(8)
                })
            }
        }
    }

    private fun orderWorkspaceSelectedDeviceCard(item: KitchenOrderItem, device: KitchenDevice): LinearLayout {
        val card = cardContainer().apply {
            var downX = 0f
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.x - downX
                        when {
                            deltaX > dp(48) -> {
                                shiftSelectedWorkspaceDevice(item, -1)
                                true
                            }
                            deltaX < -dp(48) -> {
                                shiftSelectedWorkspaceDevice(item, 1)
                                true
                            }
                            else -> {
                                view.performClick()
                                true
                            }
                        }
                    }
                    else -> true
                }
            }
            setOnClickListener { renderDeviceDetail(device) }
        }
        val top = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        top.addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label("Device ${device.number} - ${device.name}", 15, "#111111", true))
        mid.addView(label(deviceStatusLine(device), 12, "#4A4A4A", false).apply { setPadding(0, dp(4), 0, 0) })
        if (device.currentItem.isNotBlank()) {
            mid.addView(label("Now: ${device.currentItem}", 12, "#111111", true).apply { setPadding(0, dp(4), 0, 0) })
        }
        top.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(chip(deviceActionLabel(device), if (device.state == DeviceState.OFFLINE) Color.parseColor("#EFEFEF") else lightenColor(deviceColor(device.state)), if (device.state == DeviceState.OFFLINE) "#777777" else colorToHex(deviceColor(device.state))))
        card.addView(top)
        if (device.state == DeviceState.COOKING || device.state == DeviceState.QUEUED) {
            card.addView(progressBar(if (device.state == DeviceState.COOKING) 0.55f else 0.25f, deviceColor(device.state)))
        }
        card.addView(label("This order: ${item.itemName}", 12, "#333333", false).apply { setPadding(0, dp(10), 0, 0) })
        card.addView(label("Device queue: ${queuedItemsForDevice(device).size} item(s)", 12, "#666666", false).apply { setPadding(0, dp(4), 0, 0) })
        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        actionRow.addView(
            smallColoredButton(if (device.state == DeviceState.IDLE) "Cook Now" else "Add Queue", "#FF6B35", "#FFFFFF", "#FF6B35") {
                selectedAssignDevice = device
                selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = device.number
                if (device.state == DeviceState.IDLE) startOrderItemOnDevice(item, device) else queueOrderItemOnDevice(item, device)
            },
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
        )
        actionRow.addView(
            smallOutlineButton("View Device") { renderDeviceDetail(device) },
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )
        card.addView(actionRow)
        return card
    }

    private fun emptyDeviceWorkspaceCard(): LinearLayout {
        return cardContainer().apply {
            addView(label("No device connected yet", 15, "#111111", true))
            addView(label("Connect an On2Cook device over Bluetooth to assign or queue this order.", 12, "#666666", false).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun orderWorkspaceDots(item: KitchenOrderItem, devices: List<KitchenDevice>, selected: KitchenDevice): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            devices.forEach { device ->
                addView(
                    View(requireContext()).apply {
                        background = roundedDrawable(
                            if (device.number == selected.number) Color.parseColor("#FF6B35") else Color.parseColor("#E0E0E0"),
                            if (device.number == selected.number) Color.parseColor("#FF6B35") else Color.parseColor("#E0E0E0"),
                            0,
                            99
                        )
                        setOnClickListener {
                            selectedAssignDevice = device
                            selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = device.number
                            renderSelectDevice(item)
                        }
                    },
                    LinearLayout.LayoutParams(dp(if (device.number == selected.number) 18 else 8), dp(8)).apply { marginEnd = dp(6) }
                )
            }
        }
    }

    private fun selectDeviceRow(item: KitchenOrderItem, device: KitchenDevice): LinearLayout {
        val isSelected = selectedAssignDevice?.number == device.number
        val color = deviceColor(device.state)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, if (isSelected) color else Color.parseColor("#E0E0E0"), if (isSelected) 2 else 1, 10)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            alpha = if (device.state == DeviceState.OFFLINE) 0.55f else 1f
            setOnClickListener {
                if (device.state == DeviceState.OFFLINE) return@setOnClickListener
                selectedAssignDevice = device
                selectedQuickDeviceForOrder[orderWorkspaceKey(item)] = device.number
                renderSelectDevice(item)
            }
        }
        row.addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label(device.name, 14, "#111111", true))
        mid.addView(label(deviceStatusLine(device), 12, "#333333", false).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip(deviceActionLabel(device), if (device.state == DeviceState.OFFLINE) Color.parseColor("#EFEFEF") else lightenColor(color), if (device.state == DeviceState.OFFLINE) "#777777" else colorToHex(color)))
        return row.apply { layoutParams = cardLp(16, 4, 16, 8) }
    }

    private fun compactDeviceAssignRow(item: KitchenOrderItem, device: KitchenDevice): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E6E6E6"), 1, 8)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
            val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(label(device.name, 12, "#111111", true))
            mid.addView(label(deviceStatusLine(device), 11, "#555555", false))
            addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallOutlineButton(if (device.state == DeviceState.IDLE) "Cook Now" else "Add Queue") { if (device.state == DeviceState.IDLE) startOrderItemOnDevice(item, device) else queueOrderItemOnDevice(item, device) }, LinearLayout.LayoutParams(dp(92), dp(36)))
        }.apply { layoutParams = cardLp(0, 2, 0, 6) }
    }

    private fun deviceStatusCard(device: KitchenDevice): LinearLayout {
        val card = cardContainer()
        card.setOnClickListener { renderDeviceDetail(device) }
        card.isClickable = true
        card.isFocusable = true
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(deviceNumberBox(device), LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(12) })
        val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label("Device ${device.number} · ${device.name}", 15, "#111111", true))
        mid.addView(label(deviceStatusLine(device), 12, "#333333", false))
        if (device.currentItem.isNotBlank()) mid.addView(label(device.currentItem, 12, "#111111", true))
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val right = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.END }
        right.addView(label(device.power, 12, "#111111", true))
        right.addView(label(device.remaining, 11, "#555555", false))
        right.addView(label("Details ›", 12, "#FF6B35", true).apply { setPadding(0, dp(6), 0, 0) })
        row.addView(right)
        card.addView(row)
        if (device.state != DeviceState.OFFLINE) {
            card.addView(smallColoredButton(if (device.state == DeviceState.IDLE) "Assign Recipe · Cook Now" else "Assign Recipe · Add Queue", "#FF6B35", "#FFFFFF", "#FF6B35") { renderDeviceRecipePicker(device) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(8) })
        }
        if (device.state == DeviceState.COOKING) card.addView(progressBar(0.55f, deviceColor(device.state)))
        return card
    }

    private fun legendCard(): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 12)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        listOf("Cooking" to DeviceState.COOKING, "Queued" to DeviceState.QUEUED, "Idle" to DeviceState.IDLE, "Offline" to DeviceState.OFFLINE).forEach { pair ->
            row.addView(label("● ${pair.first}", 11, colorToHex(deviceColor(pair.second)), false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row.apply { layoutParams = cardLp(12, 4, 12, 10) }
    }

    private fun recipeWaitingOrdersStrip(): LinearLayout {
        val card = cardContainer()
        card.addView(label("CURRENT WAITING ORDERS", 13, "#111111", true))
        currentOrderItems.filter { it.status == OrderStatus.PENDING }.take(3).forEach { item ->
            card.addView(compactWaitingOrder(item))
        }
        return card
    }

    private fun compactWaitingOrder(item: KitchenOrderItem): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, lightenColor(Color.parseColor(item.accentColor)), 1, 8)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(sourceCircle(item.source), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) })
            val mid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(label("${item.orderId}  ${item.itemName}", 12, "#111111", true))
            mid.addView(label("${item.source} · ${item.timeAgo} · ${factsLine(item)}", 11, "#555555", false))
            addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallOutlineButton("Cook Now") { renderSelectDevice(item) }, LinearLayout.LayoutParams(dp(92), dp(34)))
        }.apply { layoutParams = cardLp(0, 6, 0, 0) }
    }

    private fun moreOption(title: String, subtitle: String, action: () -> Unit): LinearLayout {
        return cardContainer().apply {
            addView(label(title, 15, "#111111", true))
            addView(label(subtitle, 12, "#666666", false).apply { setPadding(0, dp(2), 0, dp(8)) })
            addView(smallOutlineButton("Open") { action() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        }
    }

    private fun baseScrollRoot(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F4F7F8"))
            isFillViewport = true
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(content)
        return scroll
    }

    private fun setHost(view: View) {
        binding.orderModuleHost!!.removeAllViews()
        binding.orderModuleHost!!.addView(view)
    }

    private fun screenHeader(title: String, back: Boolean, onBack: (() -> Unit)?): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            if (back) addView(label("‹", 32, "#222222", false).apply { setOnClickListener { onBack?.invoke() } }, LinearLayout.LayoutParams(dp(42), dp(44)))
            addView(label(title, 18, "#111111", true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun tabButton(text: String, active: Boolean, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = if (text.startsWith("CURRENT")) "$text  ${currentOrderItems.size}" else text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(if (active) Color.WHITE else Color.parseColor("#777777"))
            background = roundedDrawable(if (active) Color.parseColor("#FF6B35") else Color.WHITE, Color.parseColor("#D5D5D5"), 1, 8)
            setOnClickListener { action() }
            includeFontPadding = false
        }
    }

    private fun actionTextButton(text: String, fill: String, textColor: String, stroke: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor(textColor))
            background = roundedDrawable(Color.parseColor(fill), Color.parseColor(stroke), 1, 10)
            setOnClickListener { action() }
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = cardLp(16, 8, 16, 0).apply { height = dp(48) }
        }
    }

    private fun smallOutlineButton(text: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#333333"))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#BFC4C8"), 1, 7)
            setOnClickListener { action() }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
    }

    private fun smallFilledButton(text: String, action: () -> Unit): TextView {
        return smallColoredButton(text, "#FF6B35", "#FFFFFF", "#FF6B35", action)
    }

    private fun smallColoredButton(text: String, fill: String, textColor: String, stroke: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor(textColor))
            background = roundedDrawable(Color.parseColor(fill), Color.parseColor(stroke), 1, 7)
            setOnClickListener { action() }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
    }

    private fun filterPill(text: String, active: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(if (active) Color.WHITE else Color.parseColor("#333333"))
            background = roundedDrawable(if (active) Color.parseColor("#1FA33B") else Color.WHITE, Color.parseColor("#D0D0D0"), 1, 8)
        }
    }

    private fun sourceCircle(source: String): TextView {
        return TextView(requireContext()).apply {
            text = when (source.uppercase()) {
                "ZOMATO" -> "Z"
                "SWIGGY" -> "S"
                "POS" -> "P"
                else -> "O"
            }
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = roundedDrawable(sourceColor(source), sourceColor(source), 0, 24)
            maxLines = 1
            includeFontPadding = false
        }
    }

    private fun foodImage(resId: Int): ImageView {
        return ImageView(requireContext()).apply {
            setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedDrawable(Color.parseColor("#F2F2F2"), Color.parseColor("#E0E0E0"), 1, 8)
            clipToOutline = true
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun chip(text: String, fill: Int, textColor: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor(textColor))
            background = roundedDrawable(fill, fill, 0, 12)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
    }

    private fun sourcePill(source: String): TextView {
        return TextView(requireContext()).apply {
            this.text = source.uppercase()
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(sourceColor(source))
            background = roundedDrawable(Color.WHITE, sourceColor(source), 1, 12)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun label(text: String, sp: Int, color: String, bold: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = sp.toFloat()
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }
    }

    private fun twoCol(left: String, right: String, bold: Boolean = false): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            addView(label(left, 12, "#444444", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(right, 12, "#111111", bold))
        }
    }

    private fun infoRow(left: String, right: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(label(left, 12, "#555555", false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(right, 12, "#111111", true))
        }
    }

    private fun progressBar(progress: Float, color: Int): View {
        val outer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedDrawable(Color.parseColor("#E6E6E6"), Color.parseColor("#E6E6E6"), 0, 4)
            setPadding(0, 0, 0, 0)
        }
        val fill = View(requireContext()).apply { background = roundedDrawable(color, color, 0, 4) }
        outer.addView(fill, LinearLayout.LayoutParams(0, dp(7), progress))
        outer.addView(Space(requireContext()), LinearLayout.LayoutParams(0, dp(7), 1f - progress))
        outer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(8); bottomMargin = dp(6) }
        return outer
    }

    private fun cardContainer(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"), 1, 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(2).toFloat()
            layoutParams = cardLp(16, 6, 16, 8)
        }
    }

    private fun bottomSpacer(): View = Space(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(1, dp(18)) }

    private fun cardLp(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int, strokeWidth: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun sourceColor(source: String): Int = when (source.uppercase()) {
        "ZOMATO" -> Color.parseColor("#E41D2C")
        "SWIGGY" -> Color.parseColor("#FF7A1A")
        "POS" -> Color.parseColor("#0C5AA6")
        else -> Color.parseColor("#777777")
    }

    private fun statusColor(status: OrderStatus): Int = when (status) {
        OrderStatus.COOKING -> Color.parseColor("#1167D8")
        OrderStatus.PENDING -> Color.parseColor("#FF9800")
        OrderStatus.COMPLETED, OrderStatus.SHIPPED -> Color.parseColor("#1FA33B")
        OrderStatus.FAILED, OrderStatus.CANCELLED -> Color.parseColor("#E00000")
    }

    private fun deviceColor(state: DeviceState): Int = when (state) {
        DeviceState.COOKING -> Color.parseColor("#D73C50")
        DeviceState.IDLE -> Color.parseColor("#263238")
        DeviceState.QUEUED -> Color.parseColor("#D73C50")
        DeviceState.OFFLINE -> Color.parseColor("#888888")
    }

    private fun compactStatusText(status: OrderStatus): String = when (status) {
        OrderStatus.COOKING -> "Cooking"
        OrderStatus.PENDING -> "Pending"
        OrderStatus.COMPLETED -> "Completed"
        OrderStatus.SHIPPED -> "Shipped"
        OrderStatus.FAILED -> "Failed"
        OrderStatus.CANCELLED -> "Cancelled"
    }

    private fun statusText(status: OrderStatus): String = when (status) {
        OrderStatus.COOKING -> "IN EXECUTION"
        OrderStatus.PENDING -> "PENDING"
        OrderStatus.COMPLETED -> "COOKED"
        OrderStatus.SHIPPED -> "SHIPPED"
        OrderStatus.FAILED -> "FAILED"
        OrderStatus.CANCELLED -> "CANCELLED"
    }

    private fun statusText(state: DeviceState): String = when (state) {
        DeviceState.COOKING -> "Cooking"
        DeviceState.IDLE -> "Idle"
        DeviceState.QUEUED -> "Queued"
        DeviceState.OFFLINE -> "Offline"
    }

    private fun deviceStatusLine(device: KitchenDevice): String {
        val macTail = if (device.macAddress.isNotBlank()) " · ${device.macAddress.takeLast(5)}" else ""
        return when (device.state) {
            DeviceState.IDLE -> "Connected · Ready to cook$macTail"
            DeviceState.COOKING -> "Cooking: ${device.currentItem.ifBlank { "Recipe" }} · ${device.remaining} remaining · Queue ${device.queueCount}$macTail"
            DeviceState.QUEUED -> "Queue ${device.queueCount} item(s) · tap to manage$macTail"
            DeviceState.OFFLINE -> "Not connected over Bluetooth"
        }
    }

    private fun deviceActionLabel(device: KitchenDevice): String = when (device.state) {
        DeviceState.IDLE -> "Start Now"
        DeviceState.COOKING, DeviceState.QUEUED -> "Add to Queue"
        DeviceState.OFFLINE -> "Unavailable"
    }

    private fun deviceNumberBox(device: KitchenDevice): TextView {
        val color = deviceColor(device.state)
        return TextView(requireContext()).apply {
            text = "${device.number}\n${when (device.state) { DeviceState.OFFLINE -> "Off"; DeviceState.IDLE -> "Free"; else -> "Busy" }}"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (device.state == DeviceState.OFFLINE) Color.parseColor("#777777") else color)
            background = roundedDrawable(lightenColor(color), color, 1, 8)
        }
    }

    private fun lightenColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb((r + 245) / 2, (g + 245) / 2, (b + 245) / 2)
    }

    private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun navigateToRecipeFragment() {
        if (findNavController().currentDestination?.id == R.id.homeFragment) {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToRecipeFragment(
                    macAddress,
                    recipeScreenFlowTypeForNavigation?.type
                        ?: Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type
                )
            )
        }

    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}

