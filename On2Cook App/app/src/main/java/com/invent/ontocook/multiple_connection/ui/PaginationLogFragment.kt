package com.invent.ontocook.multiple_connection.ui

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
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
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.shareCSvFile
import com.opencsv.CSVWriter
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
private const val ARG_PARAM1 = Constants.MAC_ADDRESS

class PaginationLogFragment : Fragment() {
    private var macAddress: String = ""
    private lateinit var binding: FragmentLogBinding
    private var isFilterAppliedOnAlert = false
    private var isFilterAppliedOnWeek = false
    private var allLogList: MutableList<LogDataDb> = mutableListOf()
    private var singleDayLogList: MutableList<LogDataDb> = mutableListOf()

    private lateinit var logDataAdapter: LogDataAdapter
    private lateinit var singleDayAdapter: LogDataAdapter
    private lateinit var filterData: FilterData
    private lateinit var filterDataValueForWeek: FilterData
    private lateinit var minMaxFilterData: FilterData
    private lateinit var selectedDates:
            Pair<Long, Long>

    private var loadingAlerts = true
    var pastVisibleAlertItems = 0
    var visibleAlertsCount: Int = 0
    var totalAlertCount: Int = 0
    var alertPageIndex = 0
    var selectedTabPosition = 0

    private var loadingWeek = true
    var pastVisibleWeekItems = 0
    var visibleWeekCount: Int = 0
    var totalWeekCount: Int = 0
    var weekPageIndex = 0
    var defaultLogStatus = Constants.LogData.All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (it.containsKey(ARG_PARAM1))
                macAddress = it.getString(ARG_PARAM1)!!
        }
        OnToCookApplication.dbInstance.logDao()
            .getMinMaxValue(macAddress)
            .subscribe({
                minMaxFilterData = it
            }, {
                DebugLog.e("getMaxIndPower Message ${it.message}")
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_log, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    override fun onResume() {
        super.onResume()
        if (Constants.IS_TABLET)
            macAddress?.let { (requireActivity() as HomeTvActivity).setToolbar(it, true) }
        else
            macAddress?.let { (requireActivity() as HomeActivity).setToolbar(it, true) }
    }

    private fun init() {

        val log = LogDataDb(
            deviceOnCounter = 150,
            date = "25_07_23",
            macAddress = macAddress,
            utcTime = "1679665293",
            igbtTemp = 123f,
            glassTemp = 100f,
            deviceOnCounterTime = DateTimeHelper.getDate(
                "1679665293", "HH:mm:ss"
            ),/*change*/
            power = "50",/*calculate*/
            indCurrent = 10f,/*calculate*/
            magCurrent = 20f,/*calculate*/
            magTemp = 20f,/*Done*/
            coilTemp = 20f,/*Done*/
            ambientTemp = 20f,
            panTemp = 20f,/*Done*/
            pcbTemp = 20f,/*Done*/
            oilTemp = 20f,
            deviceOnTime = 10,/*Done*/
            deviceOffTime = 20,/*Done*/
            indTime = 10,
            magTime = 10,
            recipeName = "Aloo",/*Done*/
            stepNo = "2",/*Done*/
            timeRemains = "10",/*Done*/
            stepName = "Cook",
            totalSteps = "2",
            recipeTime = "4",
            elapsedTime = "5"
        )
        Handler(Looper.getMainLooper()).postDelayed({
            CoroutineScope(Dispatchers.IO).launch {
                OnToCookApplication.dbInstance.logDao().insert(log)
                allLogList.add(log)
                requireActivity().runOnUiThread {
                    logDataAdapter.notifyDataSetChanged()
                }
            }
        }, 3000)
        CoroutineScope(Dispatchers.IO).launch {
            val list = OnToCookApplication.dbInstance.logDao()
                .getAllLogList()
            DebugLog.e("CoroutineScope ${list.size}")
        }


        logDataAdapter =
            LogDataAdapter(allLogList, object : LogDataAdapter.ItemClickListener {
                override fun onItemClick(position: Int, recipeDb: BluetoothDevice) {

                }

            })
        binding.rvAlert.adapter = logDataAdapter
        getNewData(Constants.LogData.All)
        val mLayoutManager: LinearLayoutManager = LinearLayoutManager(requireContext())
        binding.rvAlert.layoutManager = mLayoutManager
        binding.rvAlert.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { //check for scroll down
                    visibleAlertsCount = mLayoutManager.childCount;
                    totalAlertCount = mLayoutManager.itemCount;
                    pastVisibleAlertItems = mLayoutManager.findFirstVisibleItemPosition();

                    if (loadingAlerts) {
                        if ((visibleAlertsCount + pastVisibleAlertItems) >= totalAlertCount) {
                            loadingAlerts = false
                            alertPageIndex++
                            getNewData(defaultLogStatus)
                        }
                    }
                }
            }
        })

        val mLayoutManagerForWeek: LinearLayoutManager = LinearLayoutManager(requireContext())
        binding.rvWeek.layoutManager = mLayoutManagerForWeek
        binding.rvWeek.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { //check for scroll down
                    visibleWeekCount = mLayoutManagerForWeek.childCount
                    totalWeekCount = mLayoutManagerForWeek.itemCount
                    pastVisibleWeekItems = mLayoutManagerForWeek.findFirstVisibleItemPosition()
                    if (loadingWeek) {
                        if ((visibleWeekCount + pastVisibleWeekItems) >= totalWeekCount) {
                            loadingWeek = false
                            DebugLog.e("Last Item Wow ! $defaultLogStatus")
                            weekPageIndex++
                            getNewData(defaultLogStatus)
                        }
                    }
                }
            }
        })
    }

    private fun getNewData(singleDay: Constants.LogData) {
        defaultLogStatus = singleDay
        when (singleDay) {
            Constants.LogData.SINGLE_DAY -> {
                val listSelected = getDateList(selectedDates)
                OnToCookApplication.dbInstance.logDao()
                    .getLogByDate(
                        listSelected,
                        macAddress,
                        if (listSelected.size > 1) -1 else 10,
                        if (weekPageIndex != 0) weekPageIndex * 10 else 0
                    )
                    .subscribe({
                        if (listSelected.size > 1) {
                            binding.tvTitleWeekDate.text = "${listSelected[0]} - ${listSelected[1]}"
                            calculateAndDisplayTotal(it)
                        } else {
                            singleDayLogList.addAll(it)
                            requireActivity().runOnUiThread {
                                if (it.size != 0)
                                    singleDayAdapter.notifyItemRangeInserted(
                                        if (singleDayLogList.size - it.size != 0) singleDayLogList.size - it.size else singleDayLogList.size,
                                        singleDayLogList.size
                                    )
                            }
                            if (it.size >= 10) {
                                loadingWeek = true
                            }
                        }
                    }, {
                        DebugLog.e("$it")
                    })
            }

            Constants.LogData.SINGLE_DAY_FILTERED -> {
                val listSelected = getDateList(selectedDates)
                OnToCookApplication.dbInstance.logDao()
                    .getWeekFilterLog(
                        macAddress = macAddress,
                        date = listSelected,
                        magCurrentStart = if (filterDataValueForWeek.magCurrentStart == 0f) minMaxFilterData.magCurrentStart else filterDataValueForWeek.magCurrentStart,
                        magCurrentEnd = if (filterDataValueForWeek.magCurrentEnd == 0f) minMaxFilterData.magCurrentEnd else filterDataValueForWeek.magCurrentEnd,
                        indCurrentStart = if (filterDataValueForWeek.indCurrentStart == 0f) minMaxFilterData.indCurrentStart else filterDataValueForWeek.indCurrentStart,
                        indCurrentEnd = if (filterDataValueForWeek.indCurrentEnd == 0f) minMaxFilterData.indCurrentEnd else filterDataValueForWeek.indCurrentEnd,
                        magTempStart = if (filterDataValueForWeek.magTempStart == 0f) minMaxFilterData.magTempStart else filterDataValueForWeek.magTempStart,
                        magTempEnd = if (filterDataValueForWeek.magTempEnd == 0f) minMaxFilterData.magTempEnd else filterDataValueForWeek.magTempEnd,
                        coilTempStart = if (filterDataValueForWeek.coilTempStart == 0f) minMaxFilterData.coilTempStart else filterDataValueForWeek.coilTempStart,
                        coilTempEnd = if (filterDataValueForWeek.coilTempEnd == 0f) minMaxFilterData.coilTempEnd else filterDataValueForWeek.coilTempEnd,
                        ambientTempStart = if (filterDataValueForWeek.ambientTempStart == 0f) minMaxFilterData.ambientTempStart else filterDataValueForWeek.ambientTempStart,
                        ambientTempEnd = if (filterDataValueForWeek.ambientTempEnd == 0f) minMaxFilterData.ambientTempEnd else filterDataValueForWeek.ambientTempEnd,
                        panTempStart = if (filterDataValueForWeek.panTempStart == 0f) minMaxFilterData.panTempStart else filterDataValueForWeek.panTempStart,
                        panTempEnd = if (filterDataValueForWeek.panTempEnd == 0f) minMaxFilterData.panTempEnd else filterDataValueForWeek.panTempEnd,
                        pcbTempStart = if (filterDataValueForWeek.pcbTempStart == 0f) minMaxFilterData.pcbTempStart else filterDataValueForWeek.pcbTempStart,
                        pcbTempEnd = if (filterDataValueForWeek.pcbTempEnd == 0f) minMaxFilterData.pcbTempEnd else filterDataValueForWeek.pcbTempEnd,
                        oilTempStart = if (filterDataValueForWeek.oilTempStart == 0f) minMaxFilterData.oilTempStart else filterDataValueForWeek.oilTempStart,
                        oilTempEnd = if (filterDataValueForWeek.oilTempEnd == 0f) minMaxFilterData.oilTempEnd else filterDataValueForWeek.oilTempEnd,
                        deviceOnTimeStart = if (filterDataValueForWeek.deviceOnTimeStart == 0) minMaxFilterData.deviceOnTimeStart else filterDataValueForWeek.deviceOnTimeStart,
                        deviceOnTimeEnd = if (filterDataValueForWeek.deviceOnTimeEnd == 0) minMaxFilterData.deviceOnTimeEnd else filterDataValueForWeek.deviceOnTimeEnd,
                        deviceOffTimeStart = if (filterDataValueForWeek.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterDataValueForWeek.deviceOffTimeStart,
                        deviceOffTimeEnd = if (filterDataValueForWeek.deviceOffTimeEnd == 0) minMaxFilterData.deviceOffTimeEnd else filterDataValueForWeek.deviceOffTimeEnd,
                        indTimeStart = if (filterDataValueForWeek.indTimeStart == 0) minMaxFilterData.indTimeStart else filterDataValueForWeek.indTimeStart,
                        indTimeEnd = if (filterDataValueForWeek.indTimeEnd == 0) minMaxFilterData.indTimeEnd else filterDataValueForWeek.indTimeEnd,
                        magTimeStart = if (filterDataValueForWeek.magTimeStart == 0) minMaxFilterData.magTimeStart else filterDataValueForWeek.magTimeStart,
                        magTimeEnd = if (filterDataValueForWeek.magTimeEnd == 0) minMaxFilterData.magTimeEnd else filterDataValueForWeek.magTimeEnd,
                        limit = if (listSelected.size > 1) -1 else 10,
                        offset = if (weekPageIndex != 0) weekPageIndex * 10 else 0
                    )
                    .subscribe({
                        if (listSelected.size > 1) {
                            binding.tvTitleWeekDate.text = "${listSelected[1]}- ${listSelected[0]}"
                            calculateAndDisplayTotal(it)
                        } else {
                            singleDayLogList.addAll(it)
                            requireActivity().runOnUiThread {
                                if (it.size != 0)
                                    singleDayAdapter.notifyItemRangeInserted(
                                        if (singleDayLogList.size - it.size != 0) singleDayLogList.size - it.size else singleDayLogList.size,
                                        singleDayLogList.size
                                    )
                            }
                            if (it.size >= 10) {
                                loadingWeek = true
                            }
                        }
//                        DebugLog.e("Subscribe Check Day Filter List ${it.size}")
//                        allLogList.addAll(it)
//                        requireActivity().runOnUiThread {
//                            if (it.size != 0)
//                                logDataAdapter.notifyItemRangeInserted(
//                                    if (allLogList.size - it.size != 0) allLogList.size - it.size else allLogList.size,
//                                    allLogList.size
//                                )
//                        }
//                        requireActivity().runOnUiThread {
//                            if (allLogList.isNotEmpty() && allLogList.size > 10)
//                                logDataAdapter.notifyItemRangeInserted(
//                                    allLogList.size - 10,
//                                    allLogList.size
//                                )
//                            else
//                                logDataAdapter.notifyItemRangeInserted(
//                                    allLogList.size,
//                                    allLogList.size
//                                )
//                        }
                        if (it.size >= 10) {
                            loadingAlerts = true
                        }
                    }, {
                        DebugLog.e("Error $it")
                    })
            }

            Constants.LogData.All -> {
                var observable: Disposable? = null
                observable = OnToCookApplication.dbInstance.logDao()
                    .getLog(if (alertPageIndex != 0) alertPageIndex * 10 else 0, macAddress)
                    .subscribe({
                        if (it.size >= 10 && alertPageIndex == 0)
                            observable?.dispose()
                        DebugLog.e("allLogList ${it.size}")
                        if ((alertPageIndex == 0 && it.size >= 10) || alertPageIndex != 0)
                            allLogList.addAll(it)
                        requireActivity().runOnUiThread {
                            if (allLogList.isNotEmpty()) logDataAdapter.notifyItemRangeInserted(
                                if (allLogList.size - it.size != 0) allLogList.size - it.size else allLogList.size,
                                allLogList.size
                            )
                        }
                        if (it.size >= 10) {
                            loadingAlerts = true
                        }
                    }, {

                    })

            }

            Constants.LogData.Filtered -> {
                DebugLog.e("filterData ${Gson().toJson(filterData)}")
                OnToCookApplication.dbInstance.logDao()
                    .getFilterLog(
                        macAddress = macAddress,
                        magCurrentStart = if (filterData.magCurrentStart == 0f) minMaxFilterData.magCurrentStart else filterData.magCurrentStart,
                        magCurrentEnd = if (filterData.magCurrentEnd == 0f) minMaxFilterData.magCurrentEnd else filterData.magCurrentEnd,
                        indCurrentStart = if (filterData.indCurrentStart == 0f) minMaxFilterData.indCurrentStart else filterData.indCurrentStart,
                        indCurrentEnd = if (filterData.indCurrentEnd == 0f) minMaxFilterData.indCurrentEnd else filterData.indCurrentEnd,
                        magTempStart = if (filterData.magTempStart == 0f) minMaxFilterData.magTempStart else filterData.magTempStart,
                        magTempEnd = if (filterData.magTempEnd == 0f) minMaxFilterData.magTempEnd else filterData.magTempEnd,
                        coilTempStart = if (filterData.coilTempStart == 0f) minMaxFilterData.coilTempStart else filterData.coilTempStart,
                        coilTempEnd = if (filterData.coilTempEnd == 0f) minMaxFilterData.coilTempEnd else filterData.coilTempEnd,
                        ambientTempStart = if (filterData.ambientTempStart == 0f) minMaxFilterData.ambientTempStart else filterData.ambientTempStart,
                        ambientTempEnd = if (filterData.ambientTempEnd == 0f) minMaxFilterData.ambientTempEnd else filterData.ambientTempEnd,
                        panTempStart = if (filterData.panTempStart == 0f) minMaxFilterData.panTempStart else filterData.panTempStart,
                        panTempEnd = if (filterData.panTempEnd == 0f) minMaxFilterData.panTempEnd else filterData.panTempEnd,
                        pcbTempStart = if (filterData.pcbTempStart == 0f) minMaxFilterData.pcbTempStart else filterData.pcbTempStart,
                        pcbTempEnd = if (filterData.pcbTempEnd == 0f) minMaxFilterData.pcbTempEnd else filterData.pcbTempEnd,
                        oilTempStart = if (filterData.oilTempStart == 0f) minMaxFilterData.oilTempStart else filterData.oilTempStart,
                        oilTempEnd = if (filterData.oilTempEnd == 0f) minMaxFilterData.oilTempEnd else filterData.oilTempEnd,
                        deviceOnTimeStart = if (filterData.deviceOnTimeStart == 0) minMaxFilterData.deviceOnTimeStart else filterData.deviceOnTimeStart,
                        deviceOnTimeEnd = if (filterData.deviceOnTimeEnd == 0) minMaxFilterData.deviceOnTimeEnd else filterData.deviceOnTimeEnd,
                        deviceOffTimeStart = if (filterData.deviceOffTimeStart == 0) minMaxFilterData.deviceOffTimeStart else filterData.deviceOffTimeStart,
                        deviceOffTimeEnd = if (filterData.deviceOffTimeEnd == 0) minMaxFilterData.deviceOffTimeEnd else filterData.deviceOffTimeEnd,
                        indTimeStart = if (filterData.indTimeStart == 0) minMaxFilterData.indTimeStart else filterData.indTimeStart,
                        indTimeEnd = if (filterData.indTimeEnd == 0) minMaxFilterData.indTimeEnd else filterData.indTimeEnd,
                        magTimeStart = if (filterData.magTimeStart == 0) minMaxFilterData.magTimeStart else filterData.magTimeStart,
                        magTimeEnd = if (filterData.magTimeEnd == 0) minMaxFilterData.magTimeEnd else filterData.magTimeEnd,
                        offset = if (alertPageIndex != 0) alertPageIndex * 10 else 0
                    )
                    .subscribe({
                        DebugLog.e("Subscribe Check Filter List ${it.size}")
                        allLogList.addAll(it)
                        requireActivity().runOnUiThread {
                            if (it.size != 0)
                                logDataAdapter.notifyItemRangeInserted(
                                    if (allLogList.size - it.size != 0) allLogList.size - it.size else allLogList.size,
                                    allLogList.size
                                )
                        }
//                        requireActivity().runOnUiThread {
//                            if (allLogList.isNotEmpty() && allLogList.size > 10)
//                                logDataAdapter.notifyItemRangeInserted(
//                                    allLogList.size - 10,
//                                    allLogList.size
//                                )
//                            else
//                                logDataAdapter.notifyItemRangeInserted(
//                                    allLogList.size,
//                                    allLogList.size
//                                )
//                        }
                        if (it.size >= 10) {
                            loadingAlerts = true
                        }
                    }, {
                        DebugLog.e("Error $it")
                    })
            }
        }
    }

    private fun calculateAndDisplayTotal(logDataDbList: List<LogDataDb>) {
        Executors.newSingleThreadExecutor().execute {
            var totalCounter = 0
            if (logDataDbList.isNotEmpty()) {
                totalCounter =
                    logDataDbList[logDataDbList.size - 1].deviceOnCounter - logDataDbList[0].deviceOnCounter
            }
            var totalIndTime = 0
            var totalMagTime = 0
            if (logDataDbList.isNotEmpty())
                logDataDbList.forEachIndexed { index, logDataDb ->
                    DebugLog.e("TotalTime magTime ${logDataDb.magTime} ")
                    if (logDataDb.magTime == 0 && index != 0 && logDataDbList[index - 1].magTime != 0) {
                        totalMagTime += logDataDbList[index - 1].magTime
                    }
                    if (logDataDb.indTime == 0 && index != 0 && logDataDbList[index - 1].indTime != 0) {
                        totalIndTime += logDataDbList[index - 1].indTime
                    }
                    if (index == logDataDbList.size - 1) {
                        if (totalMagTime == 0) {
//                            totalMagTime += logDataDbList[index - 1].magTime
                        }
                        requireActivity().runOnUiThread {
                            binding.tvValueTotalDeviceOn.text = "$totalCounter"
                            binding.tvValueTotalTimeInduction.text =
                                DateTimeHelper.getTime(totalIndTime)
                            binding.tvValueTotalTimeMicrowave.text =
                                DateTimeHelper.getTime(totalMagTime)
                            binding.tvValueMaximumInductionOn.text =
                                DateTimeHelper.getTime(logDataDbList.maxBy { it.indTime }.indTime)
                            binding.tvValueMaximumMicrowaveOn.text =
                                DateTimeHelper.getTime(logDataDbList.maxBy { it.magTime }.magTime)
                        }
                    }
                }
        }
    }

    private fun getDateList(selectedDates: Pair<Long, Long>): List<String> {
        val list = ArrayList<String>()
        if (selectedDates.first != null) {
            DateTimeHelper.getDate(selectedDates.first, Constants.DATE_FORMAT)?.let {
                list.add(it)
            }
        }
        var l: Long = selectedDates.first + 86401000
        while (l < selectedDates.second) {
            val string: String? = DateTimeHelper.getDate(l, Constants.DATE_FORMAT)
            if (string != null)
                list.add(string)
            l += 86401000
        }
        val msDiff = selectedDates.second - selectedDates.first
        val daysDiff: Long = TimeUnit.MILLISECONDS.toDays(msDiff)

        if (selectedDates.second != null && daysDiff >= 1) {
            DateTimeHelper.getDate(selectedDates.second, Constants.DATE_FORMAT)
                ?.let {
                    list.add(it)
                }
        }
        return list
    }


    private fun initListener() {
        binding.tvAlert.setSafeOnClickListener {
            if (isFilterAppliedOnAlert)
                binding.btnReset.viewShow()
            else
                binding.btnReset.viewGone()
            selectedTabPosition = 0
            setSelected()
        }
        binding.btnExportCsv.setSafeOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                exportData(OnToCookApplication.dbInstance.logDao().getAllLogList())
            }
        }
        binding.tvWeek.setSafeOnClickListener {
            if (isFilterAppliedOnWeek)
                binding.btnReset.viewShow()
            else
                binding.btnReset.viewGone()
            if (!this::selectedDates.isInitialized)
                openRangePicker()
            else {
                selectedTabPosition = 1
                setSelected()
            }
        }
        binding.ivCalender.setSafeOnClickListener {
            openRangePicker()
        }
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
                if (this::singleDayAdapter.isInitialized)
                    singleDayAdapter.notifyDataSetChanged()
                weekPageIndex = 0
                getNewData(Constants.LogData.SINGLE_DAY)
            }
            binding.btnReset.viewGone()
        }
    }

    private fun setSelected() {
        if (selectedTabPosition == 0) {
            binding.tvAlert.background = requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvWeek.background =
                requireContext().getDrawable(R.drawable.tab_round_unselected)
            binding.tvAlert.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvWeek.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.ivCalender.viewGone()
            binding.rvAlert.viewShow()
            binding.rvWeek.viewGone()
            binding.weekGroup.viewGone()
        } else {
            val listSelected = getDateList(selectedDates)
            if (listSelected.size > 1) {
                binding.weekGroup.viewShow()
                binding.rvWeek.viewGone()
            } else {
                binding.weekGroup.viewGone()
                binding.rvWeek.viewShow()
            }
            binding.ivCalender.viewShow()
            binding.rvAlert.viewGone()
            binding.tvAlert.background = requireContext().getDrawable(R.drawable.tab_white_left)
            binding.tvWeek.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvAlert.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.tvWeek.background = requireContext().getDrawable(R.drawable.tab_blue_left)
        }
    }

    private fun openRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setSelection(
            Pair(
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )
        )
        builder.setTheme(R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)

        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = today
        val decThisYear = calendar.timeInMillis

// Build constraints.
        val constraintsBuilder =
            CalendarConstraints.Builder()
                .setEnd(decThisYear)
        builder.setCalendarConstraints(constraintsBuilder.build())
        val picker = builder.build()

        picker.show(activity?.supportFragmentManager!!, picker.toString())

        picker.addOnPositiveButtonClickListener { it ->
            selectedDates = it
            selectedTabPosition = 1
            if (it.first != null && it.second != null && DateTimeHelper.getDate(
                    it.first!!,
                    Constants.DATE_FORMAT
                ) == DateTimeHelper.getDate(it.second!!, Constants.DATE_FORMAT)
            ) {
                singleDayAdapter =
                    LogDataAdapter(singleDayLogList, object : LogDataAdapter.ItemClickListener {
                        override fun onItemClick(position: Int, recipeDb: BluetoothDevice) {

                        }
                    })
                binding.rvWeek.adapter = singleDayAdapter
                DebugLog.e("Single Selection")
            } /*else {
                DebugLog.e("Multiple Single Selection")
                binding.rvWeek.viewGone()
                binding.rvAlert.viewGone()
                binding.weekGroup.viewShow()
            }*/
            setSelected()
            getNewData(Constants.LogData.SINGLE_DAY)
        }
    }

    internal fun openFilterDialog() {
        val filterDataToPass = if (selectedTabPosition == 0) {
            if (isFilterAppliedOnAlert)
                filterData
            else null
        } else {
            if (isFilterAppliedOnWeek)
                filterDataValueForWeek
            else null
        }
        val previewDialog = FilterDialog(
            filterDataToPass, if (this::minMaxFilterData.isInitialized) minMaxFilterData else null
        ) {
            if (selectedTabPosition == 0) {
                allLogList.clear()
                logDataAdapter.notifyDataSetChanged()
                isFilterAppliedOnAlert = true
                filterData = it
                alertPageIndex = 0
                getNewData(Constants.LogData.Filtered)
            } else {
                singleDayLogList.clear()
                if (this::singleDayAdapter.isInitialized)
                    singleDayAdapter.notifyDataSetChanged()
                isFilterAppliedOnWeek = true
                filterDataValueForWeek = it
                weekPageIndex = 0
                getNewData(Constants.LogData.SINGLE_DAY_FILTERED)
            }
            binding.btnReset.viewShow()
        }
        previewDialog.show(childFragmentManager, "")
    }

    fun exportData(entities: List<LogDataDb>) {
        val root = File(requireActivity().externalCacheDir, "Temp")
        if (root.exists())
            root.deleteOnExit()
        root.mkdirs()
        val csvFile = File(root, "${macAddress}_db.csv")
        val csvWriter = CSVWriter(FileWriter(csvFile))
        csvWriter.writeNext(
            arrayOf<String>(
                "Date",
                "Time",
                "Voltage",
                "Induction",
                "Microwave",
                "Igbt Temp",
                "Glass Temp",
                "Microwave Temp",
                "Pan Temp",
                "Device on",
                "Induction Time",
                "Microwave Time",
                "Device off",
                "Recipe Name",
                "Step no",
                "Total steps",
                "time remains"
            )
        )
        entities.forEach {
            csvWriter.writeNext(
                arrayOf<String>(
                    it.date,
                    it.deviceOnCounterTime,
                    it.power,
                    "${it.indCurrent}",
                    "${it.magCurrent}",
                    "${it.igbtTemp}",
                    "${it.glassTemp}",
                    "${it.magTemp}",
                    "${it.panTemp}",
                    "${it.deviceOnTime}",
                    "${it.indTime}",
                    "${it.magTime}",
                    "${it.deviceOffTime}",
                    it.recipeName,
                    it.stepNo,
                    it.totalSteps,
                    it.timeRemains
                )
            )
        }
        csvWriter.close()
        requireContext().shareCSvFile(csvFile)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = PaginationLogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
            }
        }
    }
}