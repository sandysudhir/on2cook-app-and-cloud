package com.invent.ontocook.multiple_connection.ui

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentLogBinding
import com.invent.ontocook.dialog.FilterDialog
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.adapter.LogDataAdapter
import com.invent.ontocook.multiple_connection.model.FilterData
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.shareCSvFile
import com.opencsv.CSVWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.invent.ontocook.utils.SupabaseLogUploader

private const val ARG_PARAM1 = Constants.MAC_ADDRESS
private const val ARG_PARAM2 = "device_name"

// ─────────────────────────────────────────────────────────────────────────────
// Model — holds BOTH the formatted display name AND the original raw filename
// so we can send the correct READLOG command to the device.
// ─────────────────────────────────────────────────────────────────────────────
data class LogFileItem(
    val displayName: String,   // e.g. "S3W_D_17/02/26_14"
    val rawFileName: String    // e.g. "device_log_17_2_26_14.txt"
)

// ─────────────────────────────────────────────────────────────────────────────
// Adapter for the Logs-tab file list
// ─────────────────────────────────────────────────────────────────────────────
class LogFileAdapter(
    private val list: MutableList<LogFileItem>,
    private val onItemClick: (LogFileItem, Int) -> Unit
) : RecyclerView.Adapter<LogFileAdapter.ViewHolder>() {

    /** -1 means no row is loading. */
    private var loadingPosition: Int = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFileName:  TextView    = itemView.findViewById(R.id.tvFileName)
        val ivStatus:    android.widget.ImageView = itemView.findViewById(R.id.ivStatus)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item      = list[position]
        val isLoading = (position == loadingPosition)

        holder.tvFileName.text            = item.displayName
        holder.ivStatus.visibility        = if (isLoading) View.GONE  else View.VISIBLE
        holder.progressBar.visibility     = if (isLoading) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onItemClick(item, position) }
    }

    override fun getItemCount(): Int = list.size

    /**
     * Show a spinner on [position]; pass -1 to clear all spinners.
     */
    fun setLoadingPosition(position: Int) {
        val prev = loadingPosition
        loadingPosition = position
        if (prev >= 0 && prev < list.size)      notifyItemChanged(prev)
        if (position >= 0 && position < list.size) notifyItemChanged(position)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fragment
// ─────────────────────────────────────────────────────────────────────────────
class LogFragment : Fragment() {

    private var macAddress: String = ""
    private var deviceName: String = "S3W"   // fallback
    private lateinit var binding: FragmentLogBinding

    // ── BLE file list ────────────────────────────────────────────────────────
    private val logFileList = mutableListOf<LogFileItem>()
    private lateinit var logFileAdapter: LogFileAdapter

    // ── Alert / week log state ───────────────────────────────────────────────
    private var isFilterAppliedOnAlert = false
    private var isFilterAppliedOnWeek  = false
    private val allLogList       = mutableListOf<LogDataDb>()
    private val singleDayLogList = mutableListOf<LogDataDb>()

    private lateinit var logDataAdapter:         LogDataAdapter
    private lateinit var singleDayAdapter:       LogDataAdapter
    private lateinit var filterData:             FilterData
    private lateinit var filterDataValueForWeek: FilterData
    private lateinit var minMaxFilterData:       FilterData
    private lateinit var selectedDates:          Pair<Long, Long>
    private lateinit var service:                BleService

    private var loadingAlerts         = true
    private var pastVisibleAlertItems = 0
    private var visibleAlertsCount    = 0
    private var totalAlertCount       = 0
    private var alertPageIndex        = 0
    private var selectedTabPosition   = 0

    private var loadingWeek          = true
    private var pastVisibleWeekItems = 0
    private var visibleWeekCount     = 0
    private var totalWeekCount       = 0
    private var weekPageIndex        = 0
    private var defaultLogStatus     = Constants.LogData.All

    // ── Filename formatter ───────────────────────────────────────────────────
    /**
     * Converts "device_log_17_2_26_17.txt" → "S3W_D_17/02/26_17"
     * Returns the original string unchanged if parsing fails.
     */
    private fun formatLogFileName(rawFileName: String): String {
        return try {
            val cleaned = rawFileName.replace(".txt", "").replace("._", "")
            val parts   = cleaned.split("_")
            if (parts.size < 4) return rawFileName

            val logType = when {
                parts[0] == "device" && parts[1] == "log"  -> "D"
                parts[0] == "recipe" && parts[1] == "log"  -> "R"
                else -> return rawFileName
            }

            val day   = parts[2].padStart(2, '0')
            val month = parts[3].padStart(2, '0')

            val yearAndHour = when (parts.size) {
                6    -> "${parts[4].padStart(2,'0')}_${parts[5].padStart(2,'0')}"
                5    -> {
                    val p = parts[4]
                    if (p.contains("-")) {
                        val sub = p.split("-")
                        if (sub.size >= 2 && sub[1].contains("_")) {
                            val sp = sub[1].split("_")
                            "${sp[0].padStart(2,'0')}_${sp.getOrElse(1){"00"}.padStart(2,'0')}"
                        } else "26_00"
                    } else {
                        val sp = p.split("_")
                        if (sp.size == 2) "${sp[0].padStart(2,'0')}_${sp[1].padStart(2,'0')}"
                        else "${p.padStart(2,'0')}_00"
                    }
                }
                else -> "26_00"
            }

            val year = yearAndHour.split("_")[0]
            val hour = yearAndHour.split("_").getOrElse(1) { "00" }

            "${deviceName}_${logType}_${day}/${month}/${year}_${hour}"

        } catch (e: Exception) {
            DebugLog.e("formatLogFileName error: ${e.message}")
            rawFileName
        }
    }

    // ── BLE broadcast receiver ────────────────────────────────────────────────
    private val bleDataReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: return
            val mac    = intent.getStringExtra(Constants.MAC_ADDRESS)        ?: ""
            if (mac != macAddress)                          return
            if (action != Constants.EVENT_BLE_NOTIFICATION) return

            val data = intent.getStringExtra(Constants.EVENT_MESSAGE) ?: return

            when {
                // ── A filename was received from the device ───────────────────
                data.startsWith("LOGFILE=") -> {
                    val rawName  = data.removePrefix("LOGFILE=").trim()
                    val fileName = rawName.substringAfterLast("/")
                    if (fileName.isNotEmpty() && !fileName.startsWith("._")) {
                        val display = formatLogFileName(fileName)
                        logFileList.add(LogFileItem(displayName = display, rawFileName = fileName))
                        requireActivity().runOnUiThread {
                            logFileAdapter.notifyItemInserted(logFileList.size - 1)
                        }
                        DebugLog.e("LOGFILE: $fileName  →  $display")
                    }
                }

                // ── Device has finished sending the full file list ────────────
                data == "LISTLOG=COMPLETE" || data == "LISTLOGS=COMPLETE" -> {
                    DebugLog.e("Log list complete. Total: ${logFileList.size}")

                    // ── SILENT BACKGROUND UPLOAD ──────────────────────────────
                    // As soon as we have the full list, kick off uploads for
                    // any file not yet pushed to Supabase.
                    // This runs entirely in the background; the user sees nothing.
                    if (logFileList.isNotEmpty() && ::service.isInitialized) {
                        val deviceDisplayName = macAddress // or pass a real device name if available

                        SupabaseLogUploader(
                            context       = requireContext(),
                            bleService    = service,
                            macAddress    = macAddress,
                            displayName   = deviceDisplayName,
                            filesToUpload = logFileList.toList(),
                            onAllDone     = {
                                DebugLog.e("SupabaseUploader: finished all uploads for $macAddress")
                            }
                        ).start()
                    }

                    requireActivity().runOnUiThread {
                        if (logFileList.isEmpty()) {
                            Toast.makeText(requireContext(), "No log files found on device", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                data == "LISTLOGS=ERROR" -> {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to list log files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (it.containsKey(ARG_PARAM1)) macAddress = it.getString(ARG_PARAM1)!!
            if (it.containsKey(ARG_PARAM2)) deviceName = it.getString(ARG_PARAM2)!!
        }
        OnToCookApplication.dbInstance.logDao().getMinMaxValue(macAddress).subscribe(
            { minMaxFilterData = it },
            { DebugLog.e("getMinMax error: ${it.message}") }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_log, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initLogFileList()   // sets up Logs tab RecyclerView with click handling
        init()
        initListener()
    }

    override fun onResume() {
        super.onResume()

        // Clear any stale spinner when returning from LogViewerActivity
        if (::logFileAdapter.isInitialized) {
            logFileAdapter.setLoadingPosition(-1)
        }

        if (Constants.IS_TABLET) {
            (requireActivity() as HomeTvActivity).setToolbar(macAddress, true)
        } else {
            (requireActivity() as HomeActivity).setToolbar(macAddress, true)
        }

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(bleDataReceiver, android.content.IntentFilter(Constants.EVENT_BLE_COMMUNICATION))

        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection, Context.BIND_AUTO_CREATE
        )

        Handler(Looper.getMainLooper()).postDelayed({
            if (::service.isInitialized && service.isDeviceConnected(macAddress)) {
                logFileList.clear()
                logFileAdapter.notifyDataSetChanged()
                sendListLogCommand()
            }
        }, 300)
    }

    override fun onPause() {
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(bleDataReceiver)
        } catch (e: Exception) { e.printStackTrace() }
        try {
            requireActivity().unbindService(mConnection)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Initialise the Logs-tab file list ─────────────────────────────────────
    private fun initLogFileList() {
        logFileAdapter = LogFileAdapter(logFileList) { item, position ->
            // 1. Show a spinner on the tapped row immediately
            logFileAdapter.setLoadingPosition(position)

            // 2. Auto-clear spinner after 2 s in case the activity launch is slow
            Handler(Looper.getMainLooper()).postDelayed({
                if (::logFileAdapter.isInitialized) logFileAdapter.setLoadingPosition(-1)
            }, 2_000)

            // 3. Open LogViewerActivity — passes display name + raw filename
            LogViewerActivity.start(
                context     = requireContext(),
                mac         = macAddress,
                displayName = item.displayName,
                rawFileName = item.rawFileName
            )
        }

        binding.rvAlert.adapter       = logFileAdapter
        binding.rvAlert.layoutManager = LinearLayoutManager(requireContext())

        // Show the file list; hide week panel and calendar until a tab is tapped
        binding.rvAlert.viewShow()
        binding.rvWeek.viewGone()
        binding.weekGroup.viewGone()
        binding.ivCalender.viewGone()
    }

    private fun sendListLogCommand() {
        service.writeData(macAddress, "LISTLOGS".toByteArray(Charsets.UTF_8))
        DebugLog.e("LISTLOGS command sent")
    }

    // ── Init alert / week adapters and scroll listeners ───────────────────────
    private fun init() {
        logDataAdapter = LogDataAdapter(allLogList, object : LogDataAdapter.ItemClickListener {
            override fun onItemClick(position: Int, recipeDb: BluetoothDevice) {}
        })

        getNewData(Constants.LogData.All)

        val mLayoutManager = LinearLayoutManager(requireContext())
        binding.rvAlert.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    visibleAlertsCount    = mLayoutManager.childCount
                    totalAlertCount       = mLayoutManager.itemCount
                    pastVisibleAlertItems = mLayoutManager.findFirstVisibleItemPosition()
                    if (loadingAlerts && (visibleAlertsCount + pastVisibleAlertItems) >= totalAlertCount) {
                        loadingAlerts = false
                        alertPageIndex++
                        getNewData(defaultLogStatus)
                    }
                }
            }
        })

        val mLayoutManagerForWeek = LinearLayoutManager(requireContext())
        binding.rvWeek.layoutManager = mLayoutManagerForWeek
        binding.rvWeek.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    visibleWeekCount     = mLayoutManagerForWeek.childCount
                    totalWeekCount       = mLayoutManagerForWeek.itemCount
                    pastVisibleWeekItems = mLayoutManagerForWeek.findFirstVisibleItemPosition()
                    if (loadingWeek && (visibleWeekCount + pastVisibleWeekItems) >= totalWeekCount) {
                        loadingWeek = false
                        weekPageIndex++
                        getNewData(defaultLogStatus)
                    }
                }
            }
        })
    }

    private fun getNewData(singleDay: Constants.LogData) {
        CoroutineScope(Dispatchers.IO).launch {
            defaultLogStatus = singleDay
            when (singleDay) {
                Constants.LogData.SINGLE_DAY -> {
                    val listSelected = getDateList(selectedDates)
                    val list = OnToCookApplication.dbInstance.logDao()
                        .getAllLogByDate(listSelected, macAddress)
                    if (listSelected.size > 1) {
                        requireActivity().runOnUiThread {
                            binding.tvTitleWeekDate.text = "${listSelected[0]} - ${listSelected[1]}"
                        }
                        calculateAndDisplayTotal(list)
                    } else {
                        singleDayLogList.clear()
                        singleDayLogList.addAll(list)
                        requireActivity().runOnUiThread { singleDayAdapter.notifyDataSetChanged() }
                    }
                }

                Constants.LogData.SINGLE_DAY_FILTERED -> {
                    val listSelected = getDateList(selectedDates)
                    val list = OnToCookApplication.dbInstance.logDao().getAllWeekFilterLog(
                        macAddress           = macAddress,
                        date                 = listSelected,
                        magCurrentStart      = if (filterDataValueForWeek.magCurrentStart  == 0f) minMaxFilterData.magCurrentStart  else filterDataValueForWeek.magCurrentStart,
                        magCurrentEnd        = if (filterDataValueForWeek.magCurrentEnd    == 0f) minMaxFilterData.magCurrentEnd    else filterDataValueForWeek.magCurrentEnd,
                        indCurrentStart      = if (filterDataValueForWeek.indCurrentStart  == 0f) minMaxFilterData.indCurrentStart  else filterDataValueForWeek.indCurrentStart,
                        indCurrentEnd        = if (filterDataValueForWeek.indCurrentEnd    == 0f) minMaxFilterData.indCurrentEnd    else filterDataValueForWeek.indCurrentEnd,
                        magTempStart         = if (filterDataValueForWeek.magTempStart     == 0f) minMaxFilterData.magTempStart     else filterDataValueForWeek.magTempStart,
                        magTempEnd           = if (filterDataValueForWeek.magTempEnd       == 0f) minMaxFilterData.magTempEnd       else filterDataValueForWeek.magTempEnd,
                        coilTempStart        = if (filterDataValueForWeek.coilTempStart    == 0f) minMaxFilterData.coilTempStart    else filterDataValueForWeek.coilTempStart,
                        coilTempEnd          = if (filterDataValueForWeek.coilTempEnd      == 0f) minMaxFilterData.coilTempEnd      else filterDataValueForWeek.coilTempEnd,
                        ambientTempStart     = if (filterDataValueForWeek.ambientTempStart == 0f) minMaxFilterData.ambientTempStart else filterDataValueForWeek.ambientTempStart,
                        ambientTempEnd       = if (filterDataValueForWeek.ambientTempEnd   == 0f) minMaxFilterData.ambientTempEnd   else filterDataValueForWeek.ambientTempEnd,
                        panTempStart         = if (filterDataValueForWeek.panTempStart     == 0f) minMaxFilterData.panTempStart     else filterDataValueForWeek.panTempStart,
                        panTempEnd           = if (filterDataValueForWeek.panTempEnd       == 0f) minMaxFilterData.panTempEnd       else filterDataValueForWeek.panTempEnd,
                        pcbTempStart         = if (filterDataValueForWeek.pcbTempStart     == 0f) minMaxFilterData.pcbTempStart     else filterDataValueForWeek.pcbTempStart,
                        pcbTempEnd           = if (filterDataValueForWeek.pcbTempEnd       == 0f) minMaxFilterData.pcbTempEnd       else filterDataValueForWeek.pcbTempEnd,
                        oilTempStart         = if (filterDataValueForWeek.oilTempStart     == 0f) minMaxFilterData.oilTempStart     else filterDataValueForWeek.oilTempStart,
                        oilTempEnd           = if (filterDataValueForWeek.oilTempEnd       == 0f) minMaxFilterData.oilTempEnd       else filterDataValueForWeek.oilTempEnd,
                        deviceOnTimeStart    = if (filterDataValueForWeek.deviceOnTimeStart  == 0) minMaxFilterData.deviceOnTimeStart  else filterDataValueForWeek.deviceOnTimeStart,
                        deviceOnTimeEnd      = if (filterDataValueForWeek.deviceOnTimeEnd    == 0) minMaxFilterData.deviceOnTimeEnd    else filterDataValueForWeek.deviceOnTimeEnd,
                        deviceOffTimeStart   = if (filterDataValueForWeek.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterDataValueForWeek.deviceOffTimeStart,
                        deviceOffTimeEnd     = if (filterDataValueForWeek.deviceOffTimeEnd   == 0) minMaxFilterData.deviceOffTimeEnd   else filterDataValueForWeek.deviceOffTimeEnd,
                        indTimeStart         = if (filterDataValueForWeek.indTimeStart == 0) minMaxFilterData.indTimeStart else filterDataValueForWeek.indTimeStart,
                        indTimeEnd           = if (filterDataValueForWeek.indTimeEnd   == 0) minMaxFilterData.indTimeEnd   else filterDataValueForWeek.indTimeEnd,
                        magTimeStart         = if (filterDataValueForWeek.magTimeStart == 0) minMaxFilterData.magTimeStart else filterDataValueForWeek.magTimeStart,
                        magTimeEnd           = if (filterDataValueForWeek.magTimeEnd   == 0) minMaxFilterData.magTimeEnd   else filterDataValueForWeek.magTimeEnd
                    )
                    if (listSelected.size > 1) {
                        requireActivity().runOnUiThread {
                            binding.tvTitleWeekDate.text = "${listSelected[1]} - ${listSelected[0]}"
                        }
                        calculateAndDisplayTotal(list)
                    } else {
                        singleDayLogList.clear()
                        singleDayLogList.addAll(list)
                        requireActivity().runOnUiThread { singleDayAdapter.notifyDataSetChanged() }
                    }
                }

                Constants.LogData.All -> {
                    allLogList.clear()
                    allLogList.addAll(OnToCookApplication.dbInstance.logDao().getAllLogList())
                    requireActivity().runOnUiThread { logDataAdapter.notifyDataSetChanged() }
                }

                Constants.LogData.Filtered -> {
                    allLogList.clear()
                    allLogList.addAll(
                        OnToCookApplication.dbInstance.logDao().getAllFilterLog(
                            macAddress        = macAddress,
                            magCurrentStart   = if (filterData.magCurrentStart  == 0f) minMaxFilterData.magCurrentStart  else filterData.magCurrentStart,
                            magCurrentEnd     = if (filterData.magCurrentEnd    == 0f) minMaxFilterData.magCurrentEnd    else filterData.magCurrentEnd,
                            indCurrentStart   = if (filterData.indCurrentStart  == 0f) minMaxFilterData.indCurrentStart  else filterData.indCurrentStart,
                            indCurrentEnd     = if (filterData.indCurrentEnd    == 0f) minMaxFilterData.indCurrentEnd    else filterData.indCurrentEnd,
                            magTempStart      = if (filterData.magTempStart     == 0f) minMaxFilterData.magTempStart     else filterData.magTempStart,
                            magTempEnd        = if (filterData.magTempEnd       == 0f) minMaxFilterData.magTempEnd       else filterData.magTempEnd,
                            coilTempStart     = if (filterData.coilTempStart    == 0f) minMaxFilterData.coilTempStart    else filterData.coilTempStart,
                            coilTempEnd       = if (filterData.coilTempEnd      == 0f) minMaxFilterData.coilTempEnd      else filterData.coilTempEnd,
                            ambientTempStart  = if (filterData.ambientTempStart == 0f) minMaxFilterData.ambientTempStart else filterData.ambientTempStart,
                            ambientTempEnd    = if (filterData.ambientTempEnd   == 0f) minMaxFilterData.ambientTempEnd   else filterData.ambientTempEnd,
                            panTempStart      = if (filterData.panTempStart     == 0f) minMaxFilterData.panTempStart     else filterData.panTempStart,
                            panTempEnd        = if (filterData.panTempEnd       == 0f) minMaxFilterData.panTempEnd       else filterData.panTempEnd,
                            pcbTempStart      = if (filterData.pcbTempStart     == 0f) minMaxFilterData.pcbTempStart     else filterData.pcbTempStart,
                            pcbTempEnd        = if (filterData.pcbTempEnd       == 0f) minMaxFilterData.pcbTempEnd       else filterData.pcbTempEnd,
                            oilTempStart      = if (filterData.oilTempStart     == 0f) minMaxFilterData.oilTempStart     else filterData.oilTempStart,
                            oilTempEnd        = if (filterData.oilTempEnd       == 0f) minMaxFilterData.oilTempEnd       else filterData.oilTempEnd,
                            deviceOnTimeStart = if (filterData.deviceOnTimeStart  == 0) minMaxFilterData.deviceOnTimeStart  else filterData.deviceOnTimeStart,
                            deviceOnTimeEnd   = if (filterData.deviceOnTimeEnd    == 0) minMaxFilterData.deviceOnTimeEnd    else filterData.deviceOnTimeEnd,
                            deviceOffTimeStart= if (filterData.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterData.deviceOffTimeStart,
                            deviceOffTimeEnd  = if (filterData.deviceOffTimeEnd   == 0) minMaxFilterData.deviceOffTimeEnd   else filterData.deviceOffTimeEnd,
                            indTimeStart      = if (filterData.indTimeStart == 0) minMaxFilterData.indTimeStart else filterData.indTimeStart,
                            indTimeEnd        = if (filterData.indTimeEnd   == 0) minMaxFilterData.indTimeEnd   else filterData.indTimeEnd,
                            magTimeStart      = if (filterData.magTimeStart == 0) minMaxFilterData.magTimeStart else filterData.magTimeStart,
                            magTimeEnd        = if (filterData.magTimeEnd   == 0) minMaxFilterData.magTimeEnd   else filterData.magTimeEnd
                        )
                    )
                    requireActivity().runOnUiThread { logDataAdapter.notifyDataSetChanged() }
                }
            }
        }
    }

    private fun calculateAndDisplayTotal(logDataDbList: List<LogDataDb>) {
        Executors.newSingleThreadExecutor().execute {
            var totalCounter = 0
            if (logDataDbList.isNotEmpty()) {
                totalCounter = logDataDbList.last().deviceOnCounter - logDataDbList.first().deviceOnCounter
            }
            var totalIndTime = 0
            var totalMagTime = 0
            logDataDbList.forEachIndexed { index, logDataDb ->
                if (logDataDb.magTime == 0 && index != 0 && logDataDbList[index - 1].magTime != 0)
                    totalMagTime += logDataDbList[index - 1].magTime
                if (logDataDb.indTime == 0 && index != 0 && logDataDbList[index - 1].indTime != 0)
                    totalIndTime += logDataDbList[index - 1].indTime
                if (index == logDataDbList.size - 1) {
                    requireActivity().runOnUiThread {
                        binding.tvValueTotalDeviceOn.text        = "$totalCounter"
                        binding.tvValueTotalTimeInduction.text   = DateTimeHelper.getTime(totalIndTime)
                        binding.tvValueTotalTimeMicrowave.text   = DateTimeHelper.getTime(totalMagTime)
                        binding.tvValueMaximumInductionOn.text   = DateTimeHelper.getTime(logDataDbList.maxBy { it.indTime }.indTime)
                        binding.tvValueMaximumMicrowaveOn.text   = DateTimeHelper.getTime(logDataDbList.maxBy { it.magTime }.magTime)
                    }
                }
            }
        }
    }

    private fun getDateList(selectedDates: Pair<Long, Long>): List<String> {
        val list = mutableListOf<String>()
        if (selectedDates.first != null) {
            DateTimeHelper.getDate(selectedDates.first, Constants.DATE_FORMAT)?.let { list.add(it) }
        }
        var l = selectedDates.first + 86_401_000L
        while (l < selectedDates.second) {
            DateTimeHelper.getDate(l, Constants.DATE_FORMAT)?.let { list.add(it) }
            l += 86_401_000L
        }
        val daysDiff = TimeUnit.MILLISECONDS.toDays(selectedDates.second - selectedDates.first)
        if (selectedDates.second != null && daysDiff >= 1) {
            DateTimeHelper.getDate(selectedDates.second, Constants.DATE_FORMAT)?.let { list.add(it) }
        }
        return list
    }

    private fun initListener() {
        binding.tvAlert.setSafeOnClickListener {
            // Switch rvAlert back to the DB log adapter
            binding.rvAlert.adapter       = logDataAdapter
            binding.rvAlert.layoutManager = LinearLayoutManager(requireContext())
            binding.btnReset.visibility   = if (isFilterAppliedOnAlert) View.VISIBLE else View.GONE
            selectedTabPosition = 0
            setSelected()
        }

        binding.tvWeek.setSafeOnClickListener {
            binding.btnReset.visibility = if (isFilterAppliedOnWeek) View.VISIBLE else View.GONE
            selectedTabPosition = 1
            setSelected()
        }

        binding.ivCalender.setSafeOnClickListener { openRangePicker() }

        binding.btnReset.setSafeOnClickListener {
            if (selectedTabPosition == 0) {
                isFilterAppliedOnAlert = false
                allLogList.clear()
                alertPageIndex = 0
                logDataAdapter.notifyDataSetChanged()
                getNewData(Constants.LogData.All)
            } else {
                isFilterAppliedOnWeek = false
                singleDayLogList.clear()
                if (::singleDayAdapter.isInitialized) singleDayAdapter.notifyDataSetChanged()
                weekPageIndex = 0
                getNewData(Constants.LogData.SINGLE_DAY)
            }
            binding.btnReset.viewGone()
        }

        binding.btnExportCsv.setSafeOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                when (defaultLogStatus) {
                    Constants.LogData.SINGLE_DAY -> {
                        val listSelected = getDateList(selectedDates)
                        val name = if (listSelected.size > 1) "${listSelected[0]} - ${listSelected[1]}_" else "${listSelected[0]}_"
                        exportData(OnToCookApplication.dbInstance.logDao().getAllLogByDate(listSelected, macAddress), name)
                    }
                    Constants.LogData.SINGLE_DAY_FILTERED -> {
                        val listSelected = getDateList(selectedDates)
                        val name = if (listSelected.size > 1) "${listSelected[0]} - ${listSelected[1]}_" else "${listSelected[0]}_"
                        exportData(
                            OnToCookApplication.dbInstance.logDao().getAllWeekFilterLog(
                                macAddress = macAddress, date = listSelected,
                                magCurrentStart = if (filterDataValueForWeek.magCurrentStart == 0f) minMaxFilterData.magCurrentStart else filterDataValueForWeek.magCurrentStart,
                                magCurrentEnd   = if (filterDataValueForWeek.magCurrentEnd   == 0f) minMaxFilterData.magCurrentEnd   else filterDataValueForWeek.magCurrentEnd,
                                indCurrentStart = if (filterDataValueForWeek.indCurrentStart == 0f) minMaxFilterData.indCurrentStart else filterDataValueForWeek.indCurrentStart,
                                indCurrentEnd   = if (filterDataValueForWeek.indCurrentEnd   == 0f) minMaxFilterData.indCurrentEnd   else filterDataValueForWeek.indCurrentEnd,
                                magTempStart    = if (filterDataValueForWeek.magTempStart    == 0f) minMaxFilterData.magTempStart    else filterDataValueForWeek.magTempStart,
                                magTempEnd      = if (filterDataValueForWeek.magTempEnd      == 0f) minMaxFilterData.magTempEnd      else filterDataValueForWeek.magTempEnd,
                                coilTempStart   = if (filterDataValueForWeek.coilTempStart   == 0f) minMaxFilterData.coilTempStart   else filterDataValueForWeek.coilTempStart,
                                coilTempEnd     = if (filterDataValueForWeek.coilTempEnd     == 0f) minMaxFilterData.coilTempEnd     else filterDataValueForWeek.coilTempEnd,
                                ambientTempStart= if (filterDataValueForWeek.ambientTempStart== 0f) minMaxFilterData.ambientTempStart else filterDataValueForWeek.ambientTempStart,
                                ambientTempEnd  = if (filterDataValueForWeek.ambientTempEnd  == 0f) minMaxFilterData.ambientTempEnd  else filterDataValueForWeek.ambientTempEnd,
                                panTempStart    = if (filterDataValueForWeek.panTempStart    == 0f) minMaxFilterData.panTempStart    else filterDataValueForWeek.panTempStart,
                                panTempEnd      = if (filterDataValueForWeek.panTempEnd      == 0f) minMaxFilterData.panTempEnd      else filterDataValueForWeek.panTempEnd,
                                pcbTempStart    = if (filterDataValueForWeek.pcbTempStart    == 0f) minMaxFilterData.pcbTempStart    else filterDataValueForWeek.pcbTempStart,
                                pcbTempEnd      = if (filterDataValueForWeek.pcbTempEnd      == 0f) minMaxFilterData.pcbTempEnd      else filterDataValueForWeek.pcbTempEnd,
                                oilTempStart    = if (filterDataValueForWeek.oilTempStart    == 0f) minMaxFilterData.oilTempStart    else filterDataValueForWeek.oilTempStart,
                                oilTempEnd      = if (filterDataValueForWeek.oilTempEnd      == 0f) minMaxFilterData.oilTempEnd      else filterDataValueForWeek.oilTempEnd,
                                deviceOnTimeStart  = if (filterDataValueForWeek.deviceOnTimeStart  == 0) minMaxFilterData.deviceOnTimeStart  else filterDataValueForWeek.deviceOnTimeStart,
                                deviceOnTimeEnd    = if (filterDataValueForWeek.deviceOnTimeEnd    == 0) minMaxFilterData.deviceOnTimeEnd    else filterDataValueForWeek.deviceOnTimeEnd,
                                deviceOffTimeStart = if (filterDataValueForWeek.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterDataValueForWeek.deviceOffTimeStart,
                                deviceOffTimeEnd   = if (filterDataValueForWeek.deviceOffTimeEnd   == 0) minMaxFilterData.deviceOffTimeEnd   else filterDataValueForWeek.deviceOffTimeEnd,
                                indTimeStart = if (filterDataValueForWeek.indTimeStart == 0) minMaxFilterData.indTimeStart else filterDataValueForWeek.indTimeStart,
                                indTimeEnd   = if (filterDataValueForWeek.indTimeEnd   == 0) minMaxFilterData.indTimeEnd   else filterDataValueForWeek.indTimeEnd,
                                magTimeStart = if (filterDataValueForWeek.magTimeStart == 0) minMaxFilterData.magTimeStart else filterDataValueForWeek.magTimeStart,
                                magTimeEnd   = if (filterDataValueForWeek.magTimeEnd   == 0) minMaxFilterData.magTimeEnd   else filterDataValueForWeek.magTimeEnd
                            ), name
                        )
                    }
                    Constants.LogData.All      -> exportData(OnToCookApplication.dbInstance.logDao().getAllLogList())
                    Constants.LogData.Filtered -> exportData(
                        OnToCookApplication.dbInstance.logDao().getAllFilterLog(
                            macAddress = macAddress,
                            magCurrentStart   = if (filterData.magCurrentStart  == 0f) minMaxFilterData.magCurrentStart  else filterData.magCurrentStart,
                            magCurrentEnd     = if (filterData.magCurrentEnd    == 0f) minMaxFilterData.magCurrentEnd    else filterData.magCurrentEnd,
                            indCurrentStart   = if (filterData.indCurrentStart  == 0f) minMaxFilterData.indCurrentStart  else filterData.indCurrentStart,
                            indCurrentEnd     = if (filterData.indCurrentEnd    == 0f) minMaxFilterData.indCurrentEnd    else filterData.indCurrentEnd,
                            magTempStart      = if (filterData.magTempStart     == 0f) minMaxFilterData.magTempStart     else filterData.magTempStart,
                            magTempEnd        = if (filterData.magTempEnd       == 0f) minMaxFilterData.magTempEnd       else filterData.magTempEnd,
                            coilTempStart     = if (filterData.coilTempStart    == 0f) minMaxFilterData.coilTempStart    else filterData.coilTempStart,
                            coilTempEnd       = if (filterData.coilTempEnd      == 0f) minMaxFilterData.coilTempEnd      else filterData.coilTempEnd,
                            ambientTempStart  = if (filterData.ambientTempStart == 0f) minMaxFilterData.ambientTempStart else filterData.ambientTempStart,
                            ambientTempEnd    = if (filterData.ambientTempEnd   == 0f) minMaxFilterData.ambientTempEnd   else filterData.ambientTempEnd,
                            panTempStart      = if (filterData.panTempStart     == 0f) minMaxFilterData.panTempStart     else filterData.panTempStart,
                            panTempEnd        = if (filterData.panTempEnd       == 0f) minMaxFilterData.panTempEnd       else filterData.panTempEnd,
                            pcbTempStart      = if (filterData.pcbTempStart     == 0f) minMaxFilterData.pcbTempStart     else filterData.pcbTempStart,
                            pcbTempEnd        = if (filterData.pcbTempEnd       == 0f) minMaxFilterData.pcbTempEnd       else filterData.pcbTempEnd,
                            oilTempStart      = if (filterData.oilTempStart     == 0f) minMaxFilterData.oilTempStart     else filterData.oilTempStart,
                            oilTempEnd        = if (filterData.oilTempEnd       == 0f) minMaxFilterData.oilTempEnd       else filterData.oilTempEnd,
                            deviceOnTimeStart = if (filterData.deviceOnTimeStart  == 0) minMaxFilterData.deviceOnTimeStart  else filterData.deviceOnTimeStart,
                            deviceOnTimeEnd   = if (filterData.deviceOnTimeEnd    == 0) minMaxFilterData.deviceOnTimeEnd    else filterData.deviceOnTimeEnd,
                            deviceOffTimeStart= if (filterData.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterData.deviceOffTimeStart,
                            deviceOffTimeEnd  = if (filterData.deviceOffTimeEnd   == 0) minMaxFilterData.deviceOffTimeEnd   else filterData.deviceOffTimeEnd,
                            indTimeStart      = if (filterData.indTimeStart == 0) minMaxFilterData.indTimeStart else filterData.indTimeStart,
                            indTimeEnd        = if (filterData.indTimeEnd   == 0) minMaxFilterData.indTimeEnd   else filterData.indTimeEnd,
                            magTimeStart      = if (filterData.magTimeStart == 0) minMaxFilterData.magTimeStart else filterData.magTimeStart,
                            magTimeEnd        = if (filterData.magTimeEnd   == 0) minMaxFilterData.magTimeEnd   else filterData.magTimeEnd
                        )
                    )
                }
            }
        }
    }

    fun openDatePicker() {
        if (!::selectedDates.isInitialized) openRangePicker()
        else { selectedTabPosition = 1; setSelected(); openRangePicker() }
    }

    private fun setSelected() {
        if (selectedTabPosition == 0) {
            // Logs tab — restore the file list adapter
            binding.rvAlert.adapter       = logFileAdapter
            binding.rvAlert.layoutManager = LinearLayoutManager(requireContext())

            binding.tvAlert.background = requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvWeek.background  = requireContext().getDrawable(R.drawable.tab_round_unselected)
            binding.tvAlert.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvWeek.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.ivCalender.viewGone()
            binding.rvAlert.viewShow()
            binding.rvWeek.viewGone()
            binding.weekGroup.viewGone()
        } else {
            // Alerts tab
            if (::selectedDates.isInitialized) {
                val listSelected = getDateList(selectedDates)
                if (listSelected.size > 1) { binding.weekGroup.viewShow(); binding.rvWeek.viewGone() }
                else                       { binding.weekGroup.viewGone(); binding.rvWeek.viewShow() }
            } else {
                binding.weekGroup.viewGone(); binding.rvWeek.viewGone()
            }
            binding.ivCalender.viewShow()
            binding.rvAlert.viewGone()
            binding.tvAlert.background = requireContext().getDrawable(R.drawable.tab_white_left)
            binding.tvWeek.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvAlert.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.tvWeek.background  = requireContext().getDrawable(R.drawable.tab_blue_left)
        }
    }

    private fun openRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setSelection(Pair(System.currentTimeMillis(), System.currentTimeMillis()))
        builder.setTheme(R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)
        val today    = MaterialDatePicker.todayInUtcMilliseconds()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).also { it.timeInMillis = today }
        builder.setCalendarConstraints(CalendarConstraints.Builder().setEnd(calendar.timeInMillis).build())
        val picker = builder.build()
        picker.show(activity?.supportFragmentManager!!, picker.toString())
        picker.addOnPositiveButtonClickListener { it ->
            selectedDates   = it
            selectedTabPosition = 1
            if (it.first != null && it.second != null &&
                DateTimeHelper.getDate(it.first!!, Constants.DATE_FORMAT) ==
                DateTimeHelper.getDate(it.second!!, Constants.DATE_FORMAT)) {
                singleDayAdapter = LogDataAdapter(singleDayLogList, object : LogDataAdapter.ItemClickListener {
                    override fun onItemClick(position: Int, recipeDb: BluetoothDevice) {}
                })
                binding.rvWeek.adapter = singleDayAdapter
            }
            setSelected()
            getNewData(Constants.LogData.SINGLE_DAY)
        }
    }

    fun refreshLogFiles() {
        if (::service.isInitialized && service.isDeviceConnected(macAddress)) {
            logFileList.clear()
            logFileAdapter.notifyDataSetChanged()
            sendListLogCommand()
            Toast.makeText(requireContext(), "Refreshing files…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Device not connected", Toast.LENGTH_SHORT).show()
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as BleService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    internal fun openFilterDialog() {
        val filterDataToPass = if (selectedTabPosition == 0) {
            if (isFilterAppliedOnAlert) filterData else null
        } else {
            if (isFilterAppliedOnWeek) filterDataValueForWeek else null
        }
        FilterDialog(
            filterDataToPass,
            if (::minMaxFilterData.isInitialized) minMaxFilterData else null
        ) {
            if (selectedTabPosition == 0) {
                allLogList.clear(); logDataAdapter.notifyDataSetChanged()
                isFilterAppliedOnAlert = true; filterData = it; alertPageIndex = 0
                getNewData(Constants.LogData.Filtered)
            } else {
                singleDayLogList.clear()
                if (::singleDayAdapter.isInitialized) singleDayAdapter.notifyDataSetChanged()
                isFilterAppliedOnWeek = true; filterDataValueForWeek = it; weekPageIndex = 0
                getNewData(Constants.LogData.SINGLE_DAY_FILTERED)
            }
            binding.btnReset.viewShow()
        }.show(childFragmentManager, "")
    }

    fun exportData(entities: List<LogDataDb>, name: String = "") {
        if (entities.isEmpty()) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "No log data — fetch from device first", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val root    = File(requireActivity().externalCacheDir, "Temp").apply { if (exists()) deleteOnExit(); mkdirs() }
        val csvFile = File(root, "$name${macAddress}_db.csv")
        CSVWriter(FileWriter(csvFile)).use { writer ->
            writer.writeNext(arrayOf(
                "Date","Time","Voltage","Induction","Microwave",
                "Igbt Temp","Glass Temp","Microwave Temp","Pan Temp",
                "Device on","Induction Time","Microwave Time","Device off",
                "Recipe Name","Step no","Total steps","time remains"
            ))
            entities.forEach {
                writer.writeNext(arrayOf(
                    it.date, it.deviceOnCounterTime, it.power,
                    "${it.indCurrent}", "${it.magCurrent}", "${it.igbtTemp}",
                    "${it.glassTemp}", "${it.magTemp}", "${it.panTemp}",
                    "${it.deviceOnTime}", "${it.indTime}", "${it.magTime}",
                    "${it.deviceOffTime}", it.recipeName, it.stepNo,
                    it.totalSteps, it.timeRemains
                ))
            }
        }
        requireContext().shareCSvFile(csvFile)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = LogFragment().apply {
            arguments = Bundle().apply { putString(ARG_PARAM1, param1) }
        }
    }
}