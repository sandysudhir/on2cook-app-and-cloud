package com.invent.ontocook.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.ui.LogFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SupabaseLogUploader
 * ───────────────────
 * Silently fetches every pending log file from the BLE device one-by-one,
 * then uploads each as a plain-text file to Supabase Storage.
 *
 * Supabase path layout:
 *   {BUCKET}/{mac}/{rawFileName}.txt
 *   e.g. logs/AA:BB:CC:DD:EE:FF/device_log_17_2_26_14.txt
 *
 * Nothing is shown to the user. All work happens on background threads.
 *
 * ── How to use ────────────────────────────────────────────────────────────────
 *
 *   // In LogFragment, after LISTLOGS=COMPLETE:
 *   SupabaseLogUploader(
 *       context     = requireContext(),
 *       bleService  = service,
 *       macAddress  = macAddress,
 *       displayName = "MyDevice",
 *       filesToUpload = logFileList.toList(),
 *       onAllDone   = { DebugLog.e("All uploads finished") }
 *   ).start()
 *
 * ── Configuration ─────────────────────────────────────────────────────────────
 * Set the four constants below before shipping:
 *   SUPABASE_URL    – your project URL  (e.g. https://xyzxyz.supabase.co)
 *   SUPABASE_KEY    – service-role key  (keep it safe / use BuildConfig)
 *   BUCKET          – storage bucket name
 *   FILE_TIMEOUT_MS – max ms to wait for a single file transfer from device
 */
class SupabaseLogUploader(
    private val context:       Context,
    private val bleService:    BleService,
    private val macAddress:    String,
    private val displayName:   String,
    private val filesToUpload: List<LogFileItem>,
    private val onAllDone:     (() -> Unit)? = null
) {

    // ── ⚙ Configuration — fill these in ──────────────────────────────────────
    companion object {
        private const val SUPABASE_URL    = "https://on2cook-logs.jiobase.com"
        private const val SUPABASE_KEY    = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp1bXJqeHpnb3ZleHV6cGVmYWxnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MTgwMjIyMiwiZXhwIjoyMDg3Mzc4MjIyfQ.mueAt-fEnrCF0hjWyYg2bFygSE2iez8l6SmIjZSOVtw"
        private const val BUCKET          = "Logs"
        private const val FILE_TIMEOUT_MS = 35_000L   // 35 s per file
    }

    // ── Internals ─────────────────────────────────────────────────────────────
    private val tracker      = UploadTracker(context)
    private val mainHandler  = Handler(Looper.getMainLooper())
    private val httpClient   = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // State for the currently-in-progress file fetch
    private val  rawBuffer        = StringBuilder()
    private var  transferStarted  = false
    private var  transferEnded    = false
    private val  timeoutHandler   = Handler(Looper.getMainLooper())
    private var  timeoutRunnable  = Runnable {}
    private var  currentItem: LogFileItem? = null
    private var  pendingQueue     = ArrayDeque<LogFileItem>()

    // BLE broadcast receiver — same protocol as LogViewerActivity
    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: return
            val mac    = intent.getStringExtra(Constants.MAC_ADDRESS)        ?: ""
            if (mac    != macAddress)                           return
            if (action != Constants.EVENT_BLE_NOTIFICATION)    return
            val data   = intent.getStringExtra(Constants.EVENT_MESSAGE) ?: return
            handleBleData(data)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call once after you receive LISTLOGS=COMPLETE and have a full file list. */
    fun start() {
        // Filter out already-uploaded files
        val pending = filesToUpload.filter { !tracker.isUploaded(macAddress, it.rawFileName) }
        if (pending.isEmpty()) {
            DebugLog.e("SupabaseUploader: all ${filesToUpload.size} files already uploaded for $macAddress")
            onAllDone?.invoke()
            return
        }

        DebugLog.e("SupabaseUploader: ${pending.size} files to upload for $macAddress (${filesToUpload.size - pending.size} skipped as already uploaded)")

        pendingQueue = ArrayDeque(pending)

        // Register BLE receiver (unregistered when queue is empty)
        LocalBroadcastManager.getInstance(context).registerReceiver(
            bleReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )

        processNext()
    }

    // ── Queue processing ──────────────────────────────────────────────────────

    private fun processNext() {
        if (pendingQueue.isEmpty()) {
            finish()
            return
        }
        currentItem = pendingQueue.removeFirst()
        fetchFileFromDevice(currentItem!!)
    }

    private fun fetchFileFromDevice(item: LogFileItem) {
        DebugLog.e("SupabaseUploader → READLOG=${item.rawFileName}")

        // Reset per-file state
        rawBuffer.clear()
        transferStarted = false
        transferEnded   = false

        // Safety check
        if (!bleService.isDeviceConnected(macAddress)) {
            DebugLog.e("SupabaseUploader: device not connected, skipping ${item.rawFileName}")
            processNext()
            return
        }

        // Write the BLE command
        try {
            bleService.writeData(macAddress, "READLOG=${item.rawFileName}".toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            DebugLog.e("SupabaseUploader write error for ${item.rawFileName}: ${e.message}")
            processNext()
            return
        }

        // Watchdog: if END never arrives, upload whatever was buffered
        timeoutRunnable = Runnable {
            if (!transferEnded) {
                DebugLog.e("SupabaseUploader: timeout for ${item.rawFileName} — uploading ${rawBuffer.length} chars")
                uploadToSupabase(item, rawBuffer.toString())
            }
        }
        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, FILE_TIMEOUT_MS)
    }

    private fun handleBleData(data: String) {
        val item = currentItem ?: return

        when {
            data.startsWith("READLOG=START:") -> {
                transferStarted = true
                transferEnded   = false
                rawBuffer.clear()
                // Reset the watchdog from the moment START arrives
                timeoutHandler.removeCallbacks(timeoutRunnable)
                timeoutHandler.postDelayed(timeoutRunnable, FILE_TIMEOUT_MS)
                DebugLog.e("SupabaseUploader: START received for ${item.rawFileName}")
            }

            data.startsWith("READLOG=END:") -> {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                transferEnded = true
                DebugLog.e("SupabaseUploader: END received for ${item.rawFileName} — ${rawBuffer.length} chars")
                uploadToSupabase(item, rawBuffer.toString())
            }

            data.startsWith("READLOG=ERROR:") -> {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                DebugLog.e("SupabaseUploader: device error for ${item.rawFileName}: $data")
                // Skip this file and continue
                processNext()
            }

            transferStarted && !transferEnded -> {
                rawBuffer.append(data)
            }
        }
    }

    // ── Supabase upload ───────────────────────────────────────────────────────

    /**
     * Uploads [content] to:
     *   {BUCKET}/{sanitizedMac}/{item.rawFileName}
     *
     * Uses Supabase Storage REST API (upsert = true so re-runs are idempotent).
     * Runs on Dispatchers.IO so it never blocks the main thread.
     */
    private fun uploadToSupabase(item: LogFileItem, content: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sanitize MAC for use as folder name (colons → hyphens)
                val folder   = macAddress.replace(":", "-")
                val fileName = item.rawFileName.let {
                    if (it.endsWith(".txt")) it else "$it.txt"
                }
                val path = "$folder/$fileName"

                // Supabase Storage: PUT /object/{bucket}/{path}?upsert=true
                val url = "$SUPABASE_URL/storage/v1/object/$BUCKET/$path"

                val body = content.toRequestBody("text/plain; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey",        SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                    .addHeader("x-upsert",      "true")   // overwrite if somehow re-uploaded
                    .put(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val code     = response.code
                response.close()

                if (code in 200..299) {
                    DebugLog.e("SupabaseUploader: ✓ uploaded ${item.rawFileName} → $path (HTTP $code)")
                    tracker.markUploaded(macAddress, item.rawFileName)
                } else {
                    DebugLog.e("SupabaseUploader: ✗ upload failed ${item.rawFileName} HTTP $code")
                    // File NOT marked as uploaded → will retry on next app session
                }

            } catch (e: Exception) {
                DebugLog.e("SupabaseUploader: exception uploading ${item.rawFileName}: ${e.message}")
            }

            // Move to next file regardless of success/failure
            mainHandler.post { processNext() }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun finish() {
        DebugLog.e("SupabaseUploader: all done for $macAddress")
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(bleReceiver)
        } catch (_: Exception) {}
        timeoutHandler.removeCallbacks(timeoutRunnable)
        onAllDone?.invoke()
    }
}