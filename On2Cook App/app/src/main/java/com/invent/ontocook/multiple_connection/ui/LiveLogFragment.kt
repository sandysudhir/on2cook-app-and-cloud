package com.invent.ontocook.multiple_connection.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentLiveLogBinding
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.RoundTwo
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog


private const val ARG_PARAM1 = Constants.MAC_ADDRESS

class LiveLogFragment : Fragment() {
    private var macAddress: String? = null
    private lateinit var service: BleService
    private lateinit var binding: FragmentLiveLogBinding
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
            macAddress?.let {
                service.writeData(
                    it,
                    Constants.LIVELOGON.toByteArray(Charsets.UTF_8)
                )
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    private lateinit var communicationReceiver: BroadcastReceiver

    override fun onResume() {
        super.onResume()
        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
    }

    override fun onPause() {
        macAddress?.let { service.writeData(it, Constants.LIVELOGOFF.toByteArray(Charsets.UTF_8)) }
        activity?.unbindService(
            mConnection
        )
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(communicationReceiver)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (it.containsKey(ARG_PARAM1))
                macAddress = it.getString(ARG_PARAM1)!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_live_log, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun init() {
        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DebugLog.e("Receieve message$intent")
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    parseData(intent)
                } else {
                    if (Constants.IS_TABLET)
                        (requireActivity() as HomeTvActivity).findAndParseData(
                            intent.getStringExtra(
                                Constants.MAC_ADDRESS
                            )!!, intent
                        )
                    else
                        (requireActivity() as HomeActivity).findAndParseData(
                            intent.getStringExtra(
                                Constants.MAC_ADDRESS
                            )!!, intent
                        )
                }
            }
        }
    }

    private fun initListener() {

    }

    internal fun parseData(intent: Intent) {
        when (intent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                if (message.contains(",")) {
                    val strLogItem = message.split(",")
                    DebugLog.e("Receieve message ${strLogItem.size}")
                    if (strLogItem.size == Constants.LogSizeField && isVisible) {
                        val log = LogDataDb(
                            deviceOnCounter = strLogItem[2].replace("log=", "").toIntOrNull() ?: 0,
                            date = "",
                            macAddress = "",
                            utcTime = strLogItem[0],
                            igbtTemp = Constants.getTemp(strLogItem[3]),
                            glassTemp = Constants.getFloat(strLogItem[4]),
                            deviceOnCounterTime = DateTimeHelper.getDate(
                                strLogItem[0], "HH:mm:ss"
                            ),/*change*/
                            power = strLogItem[8],/*calculate*/
                            indCurrent = Constants.getFloat(Constants.getPower(strLogItem[7])),/*calculate*/
                            magCurrent = Constants.getFloat(strLogItem[6]),/*calculate*/
                            magTemp = Constants.getFloat(strLogItem[5]),/*Done*/
                            coilTemp = Constants.getFloat(strLogItem[9]),/*Done*/
                            ambientTemp = Constants.getFloat(strLogItem[6]),
                            panTemp = Constants.getFloat(strLogItem[10]),/*Done*/
                            pcbTemp = Constants.getFloat(strLogItem[11]),/*Done*/
                            oilTemp = Constants.getFloat(strLogItem[8]),
                            deviceOnTime = 0,/*Done*/
                            deviceOffTime = 0,/*Done*/
                            indTime = 0,
                            magTime = 0,
                            recipeName = strLogItem[15],/*Done*/
                            stepNo = strLogItem[16],/*Done*/
                            timeRemains = strLogItem[strLogItem.size - 1],/*Done*/
                            stepName = strLogItem[17],
                            totalSteps = strLogItem[17],
                            recipeTime = strLogItem[17],
                            elapsedTime = strLogItem[17]
                        )
                        setData(log)
                    }
                }
            }
        }
    }

    private fun setData(log: LogDataDb) {
        DebugLog.e("Receieve message LogDataDb ${Gson().toJson(log)}")
        requireActivity().runOnUiThread {
            binding.tvCurrent.text =
                "Voltage:- ${log.power} , Ind:- ${log.indCurrent.RoundTwo()} Amps ,Mag:- ${log.magCurrent.RoundTwo()} Amps"
            binding.tvTemp.text =
                "Igbt:- ${log.igbtTemp.RoundTwo()}°C, Glass:- ${log.glassTemp.RoundTwo()}°C , Mag:- ${log.magTemp.RoundTwo()}°C ," +
//                        " Coil:- ${log.coilTemp.RoundTwo()}°C ," +
                        " Pan:- ${log.panTemp.RoundTwo()}°C "
//                        ", Pcb:- ${log.pcbTemp.RoundTwo()}°C "
            binding.tvTime.text =
                "Device On:- ${DateTimeHelper.getTimeCheck(log.deviceOnTime)} , Ind:- ${
                    DateTimeHelper.getTimeCheck(
                        log.indTime
                    )
                } , Mag:- ${DateTimeHelper.getTimeCheck(log.magTime)} , Device Off:- ${
                    DateTimeHelper.getTimeCheck(
                        log.deviceOffTime
                    )
                }"
            if (log.recipeName.isNotEmpty() && log.recipeName != "0") {
                binding.cvRecipe.viewShow()
//                binding.tvRecipe.text =
//                    "Name:- ${log.recipeName} , StepNo:- ${log.stepNo} , StepName:- ${log.stepName} , TotalSteps:- ${log.totalSteps} , RecipeTime:- ${log.recipeTime} , TimeRemains:- ${log.timeRemains}"
                binding.tvRecipe.text =
                    "Name:- ${log.recipeName} , StepNo:- ${log.stepNo} ," +
//                            " /*StepName:- ${log.stepName} , */" +
                            "TotalSteps:- ${log.totalSteps} , " +
//                            "/*RecipeTime:- ${log.recipeTime} , */" +
                            "TimeRemains:- ${log.timeRemains}"

            } else {
                binding.cvRecipe.viewGone()
            }
        }
//            binding.tvDeviceOnCounter.text = log.deviceOnCounterTime
//        binding.tvPower.text = "Power ${log.power}"
//        binding.tvIndPower.text = "IndPower ${log.indCurrent}"
//        binding.tvMagPower.text = "MagPower ${log.magCurrent}"
//        binding.tvMagTemp.text = "MagTemp ${log.magTemp}"
//        binding.tvCoilTemp.text = "CoilTemp ${log.coilTemp}"
//        binding.tvAmbientTemp.text = "AmbientTemp ${log.ambientTemp}"
//        binding.tvPanTemp.text = "PanTemp ${log.panTemp}"
//        binding.tvPcbTemp.text = "PcbTemp ${log.pcbTemp}"
//        binding.tvOilTemp.text = "OilTemp ${log.oilTemp}"
//        binding.tvDeviceOnTime.text = "DeviceOnTime ${log.deviceOnTime}"
//        binding.tvIndTime.text = "IndTime ${log.indTime}"
//        binding.tvMagTime.text = "MagTime ${log.magTime}"
//        if (log.recipeName.isNotEmpty()) {
//            binding.recipeDetail.viewShow()
//            binding.tvRecipeName.text = "RecipeName ${log.recipeName}"
//            binding.tvStepNo.text = "StepNo ${log.stepNo}"
//            binding.tvStepName.text = "StepName ${log.stepName}"
//            binding.tvTotalSteps.text = "TotalSteps ${log.totalSteps}"
//            binding.tvRecipeTime.text = "RecipeTime ${log.recipeTime}"
//            binding.tvTimeRemains.text =
//                "TimeRemains ${log.elapsedTime}\n${log.timeRemains}"
//        } else {
//            binding.recipeDetail.viewGone()
//        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String) =
            LiveLogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }
            }
    }
}