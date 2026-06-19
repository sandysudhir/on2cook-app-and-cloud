package com.invent.ontocook.multiple_connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.SharedPreferencesManager
import org.json.JSONObject

class CloudWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var bleService: BleService? = null
    private var isBleBound = false
    private val pendingConnectSlots = linkedMapOf<Int, String?>()
    private val slotToMac = linkedMapOf<Int, String>()
    private val macToSlot = linkedMapOf<String, Int>()
    private val foundDuringScan = linkedSetOf<String>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val pendingWebEvents = mutableListOf<String>()
    private var autoReconnectEnabled = false
    private var isWebPageReady = false

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            filePathCallback = null
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            callback.onReceiveValue(uris ?: emptyArray())
        }

    private val bleConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as? BleService.LocalBinder)?.getService()
            isBleBound = bleService != null
            dispatchNativeBleEvent("bridge-ready")
            dispatchConnectedSnapshot()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBleBound = false
            bleService = null
            dispatchNativeBleEvent("bridge-unavailable")
        }
    }

    private val bleConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            when (receivedIntent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> handleFoundDevice(receivedIntent)
                Constants.EVENT_BLE_CONNECTION_SUCCESS -> handleConnectionSuccess(receivedIntent)
                Constants.EVENT_BLE_CONNECTION_ABORT,
                Constants.EVENT_BLE_CONNECTION_ERROR -> handleConnectionLost(receivedIntent)
            }
        }
    }

    private val bleCommunicationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mac = intent?.getStringExtra(Constants.MAC_ADDRESS).orEmpty()
            val slot = macToSlot[mac] ?: slotToMac.entries.firstOrNull { it.value == mac }?.key ?: 1
            val action = intent?.getStringExtra(Constants.EVENT_BLE_ACTION).orEmpty()
            val message = intent?.getStringExtra(Constants.EVENT_MESSAGE)
                ?: intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE)
                ?: ""
            when (action) {
                Constants.EVENT_BLE_NOTIFICATION -> dispatchNativeBleEvent(
                    "message",
                    slot,
                    mac,
                    message = message,
                    channel = "command"
                )

                Constants.EVENT_BLE_WRITE_FAIL -> dispatchNativeBleEvent(
                    "error",
                    slot,
                    mac,
                    message = message.ifBlank { "Native BLE write failed." }
                )

                Constants.FILE_UPLOAD_SUCCESS -> dispatchNativeBleEvent(
                    "file-upload-success",
                    slot,
                    mac,
                    message = message
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        requestCloudPermissions()
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(webView)
        configureWebView()
        bindNativeBleService()
        registerBleReceivers()
        webView.loadUrl(CLOUD_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = false
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            userAgentString = "$userAgentString On2CookCloudApk/1.0"
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.addJavascriptInterface(NativeBleBridge(), "On2CookNativeBle")

        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.setOnTouchListener { view, _ ->
            view.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebPageReady = true
                flushPendingWebEvents()
                dispatchConnectedSnapshot()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("https://www.on2cook.net") || url.startsWith("https://on2cook.net")) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Cloud page load error: ${error?.description}")
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e(TAG, "Cloud WebView renderer gone. Reloading. didCrash=${detail?.didCrash()}")
                isWebPageReady = false
                webView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        webView.loadUrl(CLOUD_URL)
                    }
                }, 500)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@CloudWebActivity.filePathCallback?.onReceiveValue(null)
                this@CloudWebActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try {
                    filePickerLauncher.launch(intent)
                    true
                } catch (error: Exception) {
                    this@CloudWebActivity.filePathCallback = null
                    false
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }

    private fun bindNativeBleService() {
        val intent = Intent(this, BleService::class.java)
        startService(intent)
        bindService(intent, bleConnection, Context.BIND_AUTO_CREATE)
    }

    private fun registerBleReceivers() {
        val manager = LocalBroadcastManager.getInstance(this)
        manager.registerReceiver(bleConnectionReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION))
        manager.registerReceiver(bleCommunicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION))
    }

    private fun handleFoundDevice(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Constants.DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Constants.DEVICE)
        } ?: return
        if (!foundDuringScan.add(device.address)) return

        val paired = SharedPreferencesManager.getMacAddressList(applicationContext)
        val pairedForDevice = paired.firstOrNull { it.macAddress == device.address }
        val slot = when {
            pairedForDevice != null && pendingConnectSlots.containsKey(pairedForDevice.id) -> {
                pairedForDevice.id
            }
            pendingConnectSlots.entries.any { it.value == device.address } -> {
                val entry = pendingConnectSlots.entries.first { it.value == device.address }
                entry.key
            }
            else -> {
                pendingConnectSlots.entries.firstOrNull { (slot, targetMac) ->
                    targetMac == null && slotToMac[slot].isNullOrBlank()
                }?.key
            }
        } ?: return
        pendingConnectSlots.remove(slot)
        slotToMac[slot] = device.address
        macToSlot[device.address] = slot
        if (bleService?.isDeviceConnected(device.address) == true) {
            handleConnectionSuccess(
                Intent().putExtra(Constants.MAC_ADDRESS, device.address)
                    .putExtra(Constants.DEVICE_NAME, device.name.orEmpty())
            )
        } else {
            bleService?.connect(device)
        }
    }

    private fun handleConnectionSuccess(intent: Intent) {
        val mac = intent.getStringExtra(Constants.MAC_ADDRESS).orEmpty()
        if (mac.isBlank()) return
        val slot = macToSlot[mac]
            ?: SharedPreferencesManager.getMacAddressList(applicationContext).firstOrNull { it.macAddress == mac }?.id
            ?: nextAvailableSlot()
        val name = intent.getStringExtra(Constants.DEVICE_NAME).orEmpty()
        slotToMac[slot] = mac
        macToSlot[mac] = slot
        ensurePairedDevice(slot, mac, name.ifBlank { "On2Cook-${slot.toString().padStart(2, '0')}" })
        pendingConnectSlots.remove(slot)
        dispatchNativeBleEvent("connected", slot, mac, bluetoothName = name.ifBlank { mac.takeLast(5) })
    }

    private fun handleConnectionLost(intent: Intent?) {
        val mac = intent?.getStringExtra(Constants.MAC_ADDRESS).orEmpty()
        val slot = macToSlot[mac] ?: slotToMac.entries.firstOrNull { it.value == mac }?.key ?: 1
        if (mac.isNotBlank()) {
            macToSlot.remove(mac)
            slotToMac.remove(slot)
        }
        val message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE).orEmpty()
        dispatchNativeBleEvent("disconnected", slot, mac, message = message)
        if (autoReconnectEnabled && mac.isNotBlank()) {
            scheduleReconnect(slot, mac)
        }
    }

    private fun scheduleReconnect(slot: Int, mac: String) {
        reconnectHandler.removeCallbacksAndMessages("reconnect-$slot")
        reconnectHandler.postDelayed({
            val service = bleService ?: return@postDelayed
            if (service.isDeviceConnected(mac)) return@postDelayed
            pendingConnectSlots[slot] = mac
            foundDuringScan.clear()
            service.stopScan()
            service.startScan()
            dispatchNativeBleEvent("scan-started", slot, mac, message = "Auto reconnect scan started")
        }, 1800)
    }

    private fun ensurePairedDevice(slot: Int, mac: String, name: String) {
        val devices = SharedPreferencesManager.getMacAddressList(applicationContext)
        val existing = devices.firstOrNull { it.macAddress == mac }
        if (existing != null) {
            existing.id = slot
            existing.name = name
            existing.isConnected = true
        } else {
            devices.add(PairedDeviceData(mac, slot, name, isEdit = false, isConnected = true))
        }
        SharedPreferencesManager.updateMacAddressList(applicationContext, devices)
    }

    private fun nextAvailableSlot(): Int {
        val used = slotToMac.keys.toSet()
        return (1..5).firstOrNull { it !in used } ?: 1
    }

    private fun dispatchConnectedSnapshot() {
        val service = bleService ?: return
        val paired = SharedPreferencesManager.getMacAddressList(applicationContext)
        val knownAddresses = linkedSetOf<String>()
        knownAddresses.addAll(service.getConnectedDeviceAddresses())
        paired.forEach { device ->
            if (service.isDeviceConnected(device.macAddress)) {
                knownAddresses.add(device.macAddress)
            }
        }
        knownAddresses.take(5).forEach { mac ->
            val pairedDevice = paired.firstOrNull { it.macAddress == mac }
            val slot = macToSlot[mac]
                ?: pairedDevice?.id
                ?: slotToMac.entries.firstOrNull { it.value == mac }?.key
                ?: nextAvailableSlot()
            slotToMac[slot] = mac
            macToSlot[mac] = slot
            dispatchNativeBleEvent(
                "connected",
                slot,
                mac,
                bluetoothName = pairedDevice?.name?.ifBlank { service.getConnectedDeviceName(mac) }
                    ?: service.getConnectedDeviceName(mac)
            )
        }
    }

    private fun enqueueOrRunWebScript(script: String) {
        if (!isWebPageReady) {
            pendingWebEvents.add(script)
            return
        }
        webView.post {
            runCatching { webView.evaluateJavascript(script, null) }
                .onFailure { Log.e(TAG, "Unable to dispatch native BLE event to WebView", it) }
        }
    }

    private fun flushPendingWebEvents() {
        if (!isWebPageReady || pendingWebEvents.isEmpty()) return
        val scripts = pendingWebEvents.toList()
        pendingWebEvents.clear()
        scripts.forEach { enqueueOrRunWebScript(it) }
    }

    private fun dispatchNativeBleEvent(
        type: String,
        slot: Int = 0,
        macAddress: String = "",
        bluetoothName: String = "",
        message: String = "",
        channel: String = "command"
    ) {
        if (!this::webView.isInitialized) return
        val detail = JSONObject()
            .put("type", type)
            .put("slot", slot)
            .put("macAddress", macAddress)
            .put("browserDeviceId", if (macAddress.isBlank()) "" else "native:$macAddress")
            .put("bluetoothName", bluetoothName)
            .put("message", message)
            .put("channel", channel)
            .put("at", System.currentTimeMillis())
        val script =
            "window.dispatchEvent(new CustomEvent('on2cook-native-ble',{detail:$detail}));"
        enqueueOrRunWebScript(script)
    }

    inner class NativeBleBridge {
        @JavascriptInterface
        fun isAvailable(): Boolean = true

        @JavascriptInterface
        fun connect(slot: Int): String = runCatching {
            val safeSlot = slot.coerceIn(1, 5)
            val service = bleService
            if (!isBleBound || service == null) {
                return@runCatching JSONObject().put("ok", false).put("error", "Native BLE service is not ready.").toString()
            }
            autoReconnectEnabled = true

            val paired = SharedPreferencesManager.getMacAddressList(applicationContext)
            val target = paired.firstOrNull { it.id == safeSlot }
            if (target != null && service.isDeviceConnected(target.macAddress)) {
                slotToMac[safeSlot] = target.macAddress
                macToSlot[target.macAddress] = safeSlot
                dispatchNativeBleEvent(
                    "connected",
                    safeSlot,
                    target.macAddress,
                    bluetoothName = target.name.ifBlank { service.getConnectedDeviceName(target.macAddress) }
                )
                return@runCatching JSONObject().put("ok", true).put("mode", "already-connected").toString()
            }

            pendingConnectSlots[safeSlot] = target?.macAddress
            foundDuringScan.clear()
            service.stopScan()
            service.startScan()
            dispatchNativeBleEvent("scan-started", safeSlot, target?.macAddress.orEmpty())
            JSONObject().put("ok", true).put("mode", "scan").toString()
        }.getOrElse { errorJson(it) }

        @JavascriptInterface
        fun connectAll(): String = runCatching {
            val service = bleService
            if (!isBleBound || service == null) {
                return@runCatching JSONObject().put("ok", false).put("error", "Native BLE service is not ready.").toString()
            }
            autoReconnectEnabled = true
            val paired = SharedPreferencesManager.getMacAddressList(applicationContext)
            pendingConnectSlots.clear()
            (1..5).forEach { slot ->
                val target = paired.firstOrNull { it.id == slot }
                if (target != null && service.isDeviceConnected(target.macAddress)) {
                    slotToMac[slot] = target.macAddress
                    macToSlot[target.macAddress] = slot
                    dispatchNativeBleEvent(
                        "connected",
                        slot,
                        target.macAddress,
                        bluetoothName = target.name.ifBlank { service.getConnectedDeviceName(target.macAddress) }
                    )
                } else {
                    pendingConnectSlots[slot] = target?.macAddress
                }
            }
            foundDuringScan.clear()
            service.stopScan()
            service.startScan()
            dispatchNativeBleEvent("scan-started", 0, message = "Connect all scan started")
            JSONObject().put("ok", true).put("mode", "scan-all").toString()
        }.getOrElse { errorJson(it) }

        @JavascriptInterface
        fun disconnect(slot: Int): String = runCatching {
            val safeSlot = slot.coerceIn(1, 5)
            val mac = slotToMac[safeSlot].orEmpty()
            if (mac.isNotBlank()) {
                pendingConnectSlots.remove(safeSlot)
                bleService?.disconnect(mac)
                macToSlot.remove(mac)
                slotToMac.remove(safeSlot)
            }
            dispatchNativeBleEvent("disconnected", safeSlot, mac)
            JSONObject().put("ok", true).toString()
        }.getOrElse { errorJson(it) }

        @JavascriptInterface
        fun sendCommand(slot: Int, message: String): String = runCatching {
            val mac = slotToMac[slot].orEmpty()
            if (mac.isBlank()) {
                return@runCatching JSONObject().put("ok", false).put("error", "No native BLE device is mapped to this slot.").toString()
            }
            bleService?.writeData(mac, message.toByteArray(Charsets.UTF_8))
            JSONObject().put("ok", true).toString()
        }.getOrElse { errorJson(it) }

        @JavascriptInterface
        fun sendFile(slot: Int, message: String): String = runCatching {
            val mac = slotToMac[slot].orEmpty()
            if (mac.isBlank()) {
                return@runCatching JSONObject().put("ok", false).put("error", "No native BLE device is mapped to this slot.").toString()
            }
            bleService?.writeFileData(mac, message.toByteArray(Charsets.UTF_8))
            JSONObject().put("ok", true).toString()
        }.getOrElse { errorJson(it) }

        @JavascriptInterface
        fun setOrientation(mode: String): String = runCatching {
            val requested = if (mode.equals("landscape", ignoreCase = true)) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            runOnUiThread {
                requestedOrientation = requested
            }
            JSONObject().put("ok", true).put("orientation", mode).toString()
        }.getOrElse { errorJson(it) }
    }

    private fun errorJson(error: Throwable): String {
        Log.e(TAG, "Native BLE bridge error", error)
        return JSONObject()
            .put("ok", false)
            .put("error", error.message ?: "Native BLE bridge error.")
            .toString()
    }

    private fun requestCloudPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 7001)
        }
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        runCatching {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(bleConnectionReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(bleCommunicationReceiver)
        }
        reconnectHandler.removeCallbacksAndMessages(null)
        if (isBleBound) {
            runCatching { unbindService(bleConnection) }
            isBleBound = false
        }
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CloudWebActivity"
        private const val CLOUD_URL = "https://www.on2cook.net/?apk=1"
    }
}
