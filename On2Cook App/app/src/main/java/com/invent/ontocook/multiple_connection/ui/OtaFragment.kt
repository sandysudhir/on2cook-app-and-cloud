package com.invent.ontocook.multiple_connection.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentOtaBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.makeFileCopyInCacheDir
import com.invent.ontocook.utils.notNullAndNotEmpty
import com.invent.ontocook.utils.onSafeClick
import com.invent.ontocook.utils.openPermissionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private const val ARG_PARAM1 = Constants.MAC_ADDRESS

// WiFi AP credentials — must match softAP config in BLE_Controller.cpp
private const val WIFI_OTA_SSID     = "ON2COOK_OTA"
private const val WIFI_OTA_PASSWORD = "12345678"
private const val OTA_UPDATE_URL    = "http://192.168.4.1/update"
private const val OTA_HEALTH_URL    = "http://192.168.4.1/"

// FIX 2: Increased from 8 000 ms to 12 000 ms.
// The ESP32 needs time to: finish BLE teardown (~3 s semaphore timeout),
// start the WiFi AP (softAP + 2 s delay in firmware), and bring up the
// HTTP server. 8 s was too tight on loaded hardware; 12 s is reliable.
private const val WIFI_OTA_INITIAL_DELAY_MS = 12_000L

private const val TAG = "OtaFragment>>>"

class OtaFragment : Fragment() {

    private lateinit var binding: FragmentOtaBinding
    private var macAddress: String = ""

    private lateinit var service: BleService

    // Firmware bytes kept at fragment scope so they survive the BLE→WiFi handover
    private var pendingFirmwareBytes: ByteArray? = null
    private var pendingFileName: String = "firmware.bin"

    // FIX 4: Track whether an OTA is actively in progress so onPause does not
    // unregister receivers or unbind the service mid-transfer.
    private var otaInProgress: Boolean = false

    // WiFi network objects (API 29+)
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var otaWifiNetwork: Network? = null

    private lateinit var communicationReceiver: BroadcastReceiver
    lateinit var broadcastReceiver: BroadcastReceiver

    // -------------------------------------------------------------------------
    // File picker
    // -------------------------------------------------------------------------

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent = result.data ?: return@registerForActivityResult
                data.data?.also { uri ->
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val path = makeFileCopyInCacheDir(requireContext(), uri)
                    val file = File(path)
                    pendingFileName      = file.name
                    pendingFirmwareBytes = file.readBytes()

                    if (service.isDeviceConnected(macAddress)) {
                        // Step 1: Tell device we want OTA and the file size
                        service.writeFileData(
                            macAddress,
                            "OTA:true,SIZE:${file.length()}".toByteArray(Charsets.UTF_8)
                        )
                        // Step 2: Begin BLE chunk send — device may reply USE_WIFI
                        service.sendBinFile(macAddress, pendingFirmwareBytes!!)
                        otaInProgress = true
                        LoadingUtils.showLoading(requireContext(), false, "Firmware Updating…")
                        startProgress()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.txt_ota_fail),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    // -------------------------------------------------------------------------
    // Service connection
    // -------------------------------------------------------------------------

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(componentName: ComponentName?) {}
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            macAddress = it.getString(ARG_PARAM1, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_ota, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLog.e("onViewCreated $macAddress")
        initListener()
    }

    override fun onPause() {
        super.onPause()
        DebugLog.e("onPause — otaInProgress=$otaInProgress")

        // FIX 4: Do NOT unbind or unregister if OTA is in progress. Unbinding
        // while waiting for USE_WIFI would drop the notification and leave the
        // ESP32 stuck in OTA mode with no Android client ever connecting.
        if (!otaInProgress) {
            activity?.unbindService(mConnection)
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(communicationReceiver)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Constants.IS_TABLET)
            (requireActivity() as HomeTvActivity).setToolbar(macAddress, true)
        else
            (requireActivity() as HomeActivity).setToolbar(macAddress, true)

        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )

        Handler(Looper.getMainLooper()).postDelayed({
            if (::service.isInitialized) {
                fetchFirmwareVersion()
            }
        }, 200)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWifiNetworkCallback()

        // FIX 4 (cleanup): If we bailed out of onPause without unbinding, clean up now.
        if (otaInProgress) {
            try {
                activity?.unbindService(mConnection)
                LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(communicationReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "cleanup in onDestroy: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // BroadcastReceiver + UI listeners
    // -------------------------------------------------------------------------

    private fun initListener() {
        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {

                    Constants.EVENT_BLE_NOTIFICATION -> {
                        val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        Log.e(TAG, "notification: $message")

                        when {
                            // Device signals it wants WiFi for the actual upload
                            message.uppercase() == "USE_WIFI" -> {
                                Log.e(TAG, "USE_WIFI received — switching to WiFi OTA")
                                LoadingUtils.updateText(requireContext(), "Switching to WiFi…")

                                if (::service.isInitialized) {
                                    service.disconnect(macAddress)
                                }

                                // FIX 2: Wait 12 s before attempting WiFi connect.
                                // The ESP32 needs time to fully tear down BLE, start
                                // the soft-AP, and bring up the HTTP server.
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (isAdded) connectToOtaWifiAndUpload()
                                }, WIFI_OTA_INITIAL_DELAY_MS)
                            }

                            // Device reports firmware version
                            message.uppercase().contains("FIRMWARE=") -> {
                                val firmwareVersion = message.uppercase().replace("FIRMWARE=", "")
                                if (firmwareVersion.notNullAndNotEmpty()) {
                                    binding.textViewSoftwareVersion.text = String.format(
                                        requireActivity().resources.getString(
                                            R.string.label_dynamic_software_version
                                        ),
                                        firmwareVersion
                                    )
                                }
                            }

                            // Device cancelled OTA
                            message.uppercase() == "ACK_CANCEL" -> {
                                otaInProgress = false
                                LoadingUtils.hideDialog()
                                binding.tvFileName.text = ""
                                pendingFirmwareBytes = null
                                Toast.makeText(
                                    requireContext(),
                                    requireContext().getString(R.string.txt_ota_fail),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    Constants.FILE_UPLOAD_SUCCESS -> {
                        otaInProgress = false
                        LoadingUtils.hideDialog()
                        binding.tvFileName.text = ""
                        pendingFirmwareBytes = null
                        Toast.makeText(
                            requireContext(),
                            requireContext().getString(R.string.txt_ota_sucess),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    Constants.EVENT_BLE_WRITE_FAIL -> {
                        otaInProgress = false
                        LoadingUtils.hideDialog()
                        if (OnToCookApplication.instance.isDeviceConnected()) {
                            Toast.makeText(
                                requireContext(),
                                requireContext().getString(R.string.txt_ota_fail),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.imageViewFetchSoftwareVersion.onSafeClick {
            fetchFirmwareVersion()
        }

        binding.btnUploadFile.setSafeOnClickListener {
            if (binding.tvFileName.text.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    requireContext().getText(R.string.warn_file),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnSelectFile.setSafeOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                openFilePicker()
            } else {
                PermissionManagerUtils.checkPermission(
                    requireContext(),
                    requireActivity(),
                    arrayListOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    PermissionManagerUtils.PermissionSessionManager(requireContext()),
                    object : PermissionManagerUtils.PermissionAskListener {
                        override fun onNeedPermission() {
                            ActivityCompat.requestPermissions(
                                requireActivity(), arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ), Constants.REQUEST_CAMERA_PERMISSION
                            )
                        }
                        override fun onPermissionPreviouslyDenied() {
                            ActivityCompat.requestPermissions(
                                requireActivity(), arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ), 1
                            )
                        }
                        override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                            DialogUtils().commonDialog(
                                context = requireActivity(),
                                title = getString(R.string.title_permission_open_settings),
                                message = getString(R.string.message_storage_camera_permission),
                                positiveButton = getString(R.string.button_setting),
                                negativeButton = getString(R.string.button_cancel),
                                isAppLogoDisplay = true,
                                isCancelable = true,
                                callbackSuccess = { requireContext().openPermissionSettings() },
                                callbackNegative = {}
                            )
                        }
                        override fun onPermissionGranted() {
                            openFilePicker()
                        }
                    }
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // BLE helpers
    // -------------------------------------------------------------------------

    private fun fetchFirmwareVersion() {
        service.writeData(macAddress, "Firmware=?".toByteArray(Charsets.UTF_8))
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultLauncher.launch(intent)
    }

    private fun startProgress() {
        service.mutableLiveData.observe(requireActivity()) {
            DebugLog.e("mutableLiveData: sendBinFile $it")
            if (it >= 100) {
                LoadingUtils.hideDialog()
            } else {
                if (isAdded) {
                    LoadingUtils.updateText(requireContext(), "$it %")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // WiFi OTA — entry point
    // -------------------------------------------------------------------------

    /**
     * Called after USE_WIFI is received and the initial delay has elapsed.
     * Dispatches to the correct WiFi connection method based on API level.
     */
    private fun connectToOtaWifiAndUpload() {
        val firmware = pendingFirmwareBytes
        if (firmware == null) {
            showOtaError("No firmware file available for WiFi upload")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiApi29(firmware)
        } else {
            connectWifiLegacy(firmware)
        }
    }

    // -------------------------------------------------------------------------
    // WiFi OTA — Android 10+ (API 29+)
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun connectWifiApi29(firmware: ByteArray, attempt: Int = 1) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!isAdded) return

        val maxAttempts = 5
        Log.e(TAG, "WiFi connect attempt $attempt/$maxAttempts")
        LoadingUtils.updateText(
            requireContext(),
            "Connecting to OTA WiFi… (attempt $attempt/$maxAttempts)"
        )

        // Always release the previous callback before making a new request
        releaseWifiNetworkCallback()

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(WIFI_OTA_SSID)
            .setWpa2Passphrase(WIFI_OTA_PASSWORD)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                otaWifiNetwork = network
                Log.e(TAG, "WiFi OTA network available on attempt $attempt")

                // FIX 5: Wait 2 s after onAvailable for the HTTP server to be fully
                // ready, then ping the health endpoint before uploading. This prevents
                // the POST from arriving before the server's listen() call completes,
                // which would cause a connection-refused error and a misleading
                // "system error" on the device.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isAdded) {
                        pingThenUpload(firmware, network)
                    } else {
                        // FIX 3: Fragment detached — log clearly instead of silently
                        // dropping the upload while the ESP32 waits for a connection.
                        Log.e(TAG, "Fragment detached before upload — OTA aborted")
                        releaseWifiNetworkCallback()
                        otaInProgress = false
                    }
                }, 2000)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e(TAG, "WiFi OTA unavailable on attempt $attempt")
                wifiNetworkCallback = null

                if (attempt < maxAttempts) {
                    // ESP32 AP may not be fully ready — wait 3 s then retry
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isAdded) connectWifiApi29(firmware, attempt + 1)
                    }, 3000)
                } else {
                    requireActivity().runOnUiThread {
                        otaInProgress = false
                        showOtaError(
                            "Could not connect to $WIFI_OTA_SSID after $maxAttempts attempts"
                        )
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.e(TAG, "WiFi OTA network lost")
            }
        }

        wifiNetworkCallback = callback
        connectivityManager.requestNetwork(request, callback)
    }

    // -------------------------------------------------------------------------
    // WiFi OTA — Android 8/9 (API 26–28, legacy WifiManager)
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun connectWifiLegacy(firmware: ByteArray) {
        LoadingUtils.updateText(requireContext(), "Connecting to OTA WiFi…")

        val wifiManager =
            requireContext().applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val wifiConfig = android.net.wifi.WifiConfiguration().apply {
            SSID         = "\"$WIFI_OTA_SSID\""
            preSharedKey = "\"$WIFI_OTA_PASSWORD\""
        }

        val networkId = wifiManager.addNetwork(wifiConfig)
        if (networkId == -1) {
            showOtaError("Failed to add WiFi network (legacy)")
            otaInProgress = false
            return
        }

        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()

        // Poll until connected (timeout ~15 s)
        CoroutineScope(Dispatchers.IO).launch {
            var retries = 0
            val maxRetries = 30
            while (retries < maxRetries) {
                delay(500)
                @Suppress("DEPRECATION")
                val info = wifiManager.connectionInfo
                if (info?.ssid == "\"$WIFI_OTA_SSID\"") {
                    // Give the HTTP server extra time to fully start
                    delay(5000)
                    uploadFirmwareViaWifi(firmware, null)
                    return@launch
                }
                retries++
            }
            withContext(Dispatchers.Main) {
                otaInProgress = false
                showOtaError("Timed out connecting to $WIFI_OTA_SSID")
            }
        }
    }

    // -------------------------------------------------------------------------
    // FIX 5: Health-check ping before the actual firmware upload
    // -------------------------------------------------------------------------

    /**
     * Sends a GET to "/" first. If the ESP32 HTTP server responds with "OTA Ready",
     * proceed with the firmware POST. Otherwise retry up to [maxPingAttempts] times
     * with a 2 s gap, then give up with an error. This eliminates "connection refused"
     * errors that occur when Android connects to the WiFi AP before the WebServer
     * has called begin() on the ESP32.
     */
    private fun pingThenUpload(
        firmware: ByteArray,
        otaNetwork: Network?,
        attempt: Int = 1,
        maxPingAttempts: Int = 5
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                LoadingUtils.updateText(requireContext(), "Waiting for device server… ($attempt/$maxPingAttempts)")
            }

            try {
                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)

                if (otaNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    clientBuilder.socketFactory(otaNetwork.socketFactory)
                }
                val client = clientBuilder.build()

                val pingRequest = Request.Builder().url(OTA_HEALTH_URL).get().build()
                val response    = client.newCall(pingRequest).execute()
                val body        = response.body?.string() ?: ""

                if (response.isSuccessful && body.contains("OTA Ready", ignoreCase = true)) {
                    Log.e(TAG, "Server ready — starting upload")
                    uploadFirmwareViaWifi(firmware, otaNetwork)
                } else {
                    throw Exception("Server not ready (HTTP ${response.code}, body='$body')")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ping attempt $attempt failed: ${e.message}")
                if (attempt < maxPingAttempts) {
                    delay(2000)
                    withContext(Dispatchers.Main) {
                        if (isAdded) pingThenUpload(firmware, otaNetwork, attempt + 1, maxPingAttempts)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        otaInProgress = false
                        releaseWifiNetworkCallback()
                        showOtaError("Device server not reachable after $maxPingAttempts attempts")
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Firmware upload via HTTP multipart POST
    // -------------------------------------------------------------------------

    /**
     * POSTs the firmware binary to the ESP32's /update endpoint using
     * multipart/form-data (required by the ESP32 WebServer upload handler).
     *
     * Traffic is forced through [otaNetwork] via socketFactory on API 29+
     * so it does not leak onto mobile data, regardless of bindProcessToNetwork.
     */
    private fun uploadFirmwareViaWifi(firmware: ByteArray, otaNetwork: Network? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                LoadingUtils.updateText(requireContext(), "Uploading firmware via WiFi…")
            }

            try {
                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)

                // FIX: Force OkHttp to use the OTA WiFi network's socket factory so
                // traffic is not routed over mobile data on API 29+ devices. This is
                // more reliable than bindProcessToNetwork.
                if (otaNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    clientBuilder.socketFactory(otaNetwork.socketFactory)
                }

                val client = clientBuilder.build()

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "update",          // field name the ESP32 WebServer expects
                        pendingFileName,   // filename (informational)
                        firmware.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url(OTA_UPDATE_URL)
                    .post(requestBody)
                    .build()

                Log.e(TAG, "Posting ${firmware.size} bytes to $OTA_UPDATE_URL")
                val response = client.newCall(request).execute()
                Log.e(TAG, "WiFi OTA response: ${response.code} ${response.message}")

                withContext(Dispatchers.Main) {
                    otaInProgress = false
                    LoadingUtils.hideDialog()
                    releaseWifiNetworkCallback()
                    pendingFirmwareBytes = null
                    binding.tvFileName.text = ""

                    if (response.isSuccessful) {
                        Toast.makeText(
                            requireContext(),
                            requireContext().getString(R.string.txt_ota_sucess),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showOtaError("Upload failed: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WiFi OTA upload exception: $e")
                withContext(Dispatchers.Main) {
                    otaInProgress = false
                    LoadingUtils.hideDialog()
                    releaseWifiNetworkCallback()
                    showOtaError("WiFi upload error: ${e.message}")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup helpers
    // -------------------------------------------------------------------------

    private fun releaseWifiNetworkCallback() {
        wifiNetworkCallback?.let { callback ->
            try {
                val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE)
                        as ConnectivityManager
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "releaseWifiNetworkCallback error: ${e.message}")
            }
            wifiNetworkCallback = null
            otaWifiNetwork      = null
        }
    }

    private fun showOtaError(message: String) {
        if (!isAdded) return
        LoadingUtils.hideDialog()
        Log.e(TAG, "OTA error: $message")
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    // -------------------------------------------------------------------------
    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            OtaFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }
            }
    }
}