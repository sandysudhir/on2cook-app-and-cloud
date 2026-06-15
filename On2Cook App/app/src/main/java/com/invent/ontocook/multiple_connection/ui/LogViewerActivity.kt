package com.invent.ontocook.multiple_connection.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ActivityLogViewerBinding
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.shareCSvFile
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter

class LogViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MAC          = "extra_mac"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_RAW_FILENAME = "extra_raw_filename"

        fun start(context: Context, mac: String, displayName: String, rawFileName: String) {
            context.startActivity(
                Intent(context, LogViewerActivity::class.java).apply {
                    putExtra(EXTRA_MAC,          mac)
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                    putExtra(EXTRA_RAW_FILENAME, rawFileName)
                }
            )
        }
    }

    private lateinit var binding: ActivityLogViewerBinding

    private var macAddress  = ""
    private var displayName = ""
    private var rawFileName = ""

    // ── Transfer state ────────────────────────────────────────────────────────
    private val rawBuffer          = StringBuilder()
    private var transferStarted    = false
    private var transferEnded      = false
    private var totalBytesReceived = 0
    private var retryCount         = 0
    private val MAX_RETRIES        = 3

    // 30-second watchdog: show whatever arrived even if END never comes
    private val timeoutHandler  = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (!transferEnded) {
            DebugLog.e("LogViewer: timeout — ${rawBuffer.length} chars buffered")
            runOnUiThread { showBufferNow() }
        }
    }

    // ── BLE broadcast receiver ────────────────────────────────────────────────
    private val bleReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: return
            val mac    = intent.getStringExtra(Constants.MAC_ADDRESS)        ?: ""
            if (mac != macAddress)                           return
            if (action != Constants.EVENT_BLE_NOTIFICATION) return
            val data = intent.getStringExtra(Constants.EVENT_MESSAGE) ?: return
            handleBleData(data)
        }
    }

    // ── Service connection ────────────────────────────────────────────────────
    private var bleService: BleService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as BleService.LocalBinder).getService()
            Handler(Looper.getMainLooper()).postDelayed({ sendReadLogCommand() }, 500)
        }
        override fun onServiceDisconnected(name: ComponentName?) { bleService = null }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        macAddress  = intent.getStringExtra(EXTRA_MAC)          ?: ""
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Log File"
        rawFileName = intent.getStringExtra(EXTRA_RAW_FILENAME) ?: ""

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvToolbarTitle.text = displayName

        binding.btnExportCsv.setOnClickListener { exportRaw() }
        binding.btnCopyRaw.setOnClickListener   { copyRaw()   }

        setStatus("Waiting…")
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            bleReceiver, android.content.IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
        bindService(
            Intent(this, BleService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onPause() {
        super.onPause()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bleReceiver)
        try { unbindService(serviceConnection) } catch (_: Exception) {}
    }

    // ── Send READLOG command ───────────────────────────────────────────────────
    private fun sendReadLogCommand() {
        val svc = bleService
        if (svc == null || !svc.isDeviceConnected(macAddress)) {
            setStatus("⚠ Device not connected")
            return
        }

        rawBuffer.clear()
        transferStarted    = false
        transferEnded      = false
        totalBytesReceived = 0

        binding.progressTransfer.visibility = View.VISIBLE
        binding.scrollRaw.visibility        = View.VISIBLE
        binding.rvLogContent.visibility     = View.GONE
        binding.tvEmptyState.visibility     = View.GONE
        binding.tvRawContent.text           = ""

        // FIX 4: Send the filename exactly as received from the device via LOGFILE=.
        // Do NOT prepend /log/ — Flutter sends it bare and it works; adding a slash
        // causes a "File not found" error on the firmware side.
        val cmd = "READLOG=$rawFileName"
        DebugLog.e("LogViewer → $cmd")
        setStatus("Sending $cmd…")

        try {
            svc.writeData(macAddress, cmd.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            DebugLog.e("LogViewer write error: ${e.message}")
            if (retryCount < MAX_RETRIES) {
                retryCount++
                Handler(Looper.getMainLooper()).postDelayed({ sendReadLogCommand() }, 300)
            } else {
                setStatus("⚠ Write failed: ${e.message}")
            }
            return
        }

        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, 30_000)
    }

    // ── Handle every BLE notification ─────────────────────────────────────────
    /**
     * Protocol (device firmware HandleReadLog):
     *   READLOG=START:<path>   → start of file transfer
     *   <raw content chunks>   → file bytes, NO prefix  ← just append these
     *   READLOG=END:<stats>    → transfer finished
     *   READLOG=ERROR:<msg>    → device error
     *   ACK                    → command acknowledged
     *
     * Rule (from Flutter reference):
     *   Only accumulate packets where started==true AND ended==false.
     *   The START packet itself is NOT content.
     */
    private fun handleBleData(data: String) {
        DebugLog.e("LogViewer ← [${data.length}] ${data.take(60)}")

        when {
            data.startsWith("READLOG=START:") -> {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                transferStarted    = true
                transferEnded      = false
                totalBytesReceived = 0
                rawBuffer.clear()
                retryCount = 0
                timeoutHandler.postDelayed(timeoutRunnable, 30_000)
                runOnUiThread {
                    binding.progressTransfer.visibility = View.VISIBLE
                    binding.tvRawContent.text           = ""
                    // REMOVED byte references here
                    setStatus("Receiving log data...")
                }
            }

            // Update the CONTENT CHUNK block as well
            transferStarted && !transferEnded -> {
                rawBuffer.append(data)
                runOnUiThread {
                    binding.tvRawContent.text = rawBuffer.toString()
                    // REMOVED binding.tvBytesReceived.text update
                    setStatus("Receiving...")
                }
            }
        }
    }

    // ── Show whatever is in the buffer right now ──────────────────────────────
    private fun showBufferNow() {
        binding.progressTransfer.visibility = View.GONE
        binding.scrollRaw.visibility        = View.VISIBLE
        binding.tvRawContent.text           = rawBuffer.toString()
        setStatus("✓ Transfer complete") // REMOVED formatBytes call
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun setStatus(msg: String) {
        binding.tvTransferStatus.text = msg
    }

    // ── Export raw text as CSV / share ────────────────────────────────────────
    private fun exportRaw() {
        val text = rawBuffer.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, getString(R.string.log_viewer_no_data), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val root = File(externalCacheDir, "Temp").apply { mkdirs() }
            val file = File(root, "${displayName}_${macAddress}.csv")
            CSVWriter(FileWriter(file)).use { writer ->
                text.lines().forEach { writer.writeNext(arrayOf(it)) }
            }
            shareCSvFile(file)
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Copy raw to clipboard ─────────────────────────────────────────────────
    private fun copyRaw() {
        val text = rawBuffer.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, getString(R.string.log_viewer_no_data), Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("log_data", text))
        Toast.makeText(this, getString(R.string.log_viewer_copied), Toast.LENGTH_SHORT).show()
    }

    private fun formatBytes(bytes: Int): String = when {
        bytes < 1_024     -> "$bytes B"
        bytes < 1_048_576 -> "${"%.1f".format(bytes / 1_024f)} KB"
        else              -> "${"%.2f".format(bytes / 1_048_576f)} MB"
    }
}