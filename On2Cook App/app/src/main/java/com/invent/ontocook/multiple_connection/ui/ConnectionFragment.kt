package com.invent.ontocook.multiple_connection.ui

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentConnectionBinding
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.isBluetoothOn
import com.polidea.rxandroidble2.RxBleConnection

private const val ARG_PARAM1 = "MAC_ADDRESS"
private const val ARG_PARAM2 = "param2"

class ConnectionFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var macAddress: String? = null
    private var param2: String? = null
    lateinit var service: BleService

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
//            service.startScan()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    lateinit var broadcastReceiver: BroadcastReceiver

    private val TAG = this::class.java.simpleName
    private lateinit var strBuilder: StringBuilder
    private lateinit var binding: FragmentConnectionBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.bindService(
            Intent(requireContext(), BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )
        strBuilder = java.lang.StringBuilder()
        arguments?.let {
            macAddress = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.unbindService(
            mConnection
        )
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(
                layoutInflater,
                com.invent.ontocook.R.layout.fragment_connection,
                container,
                false
            )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun init() {
        binding.tvMac.text = "Mac:- $macAddress"
        macAddress?.let { (requireActivity() as HomeTvActivity).setToolbar(it,true) }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    appendData(intent)
                } else {
//                    val fragment = intent.getStringExtra(Constants.MAC_ADDRESS)?.let {
//                        (activity as HomeTvActivity).viewPagerAdapter.getFragmentFromMac(
//                            it
//                        )
//                    }
//                    if ((fragment as DashboardFragment).navController.currentDestination!!.id == R.id.connectionFragment) {
//                        (fragment.getCurrentFragment() as ConnectionFragment).appendData(intent)
//                    }
                }
            }
        }
    }

    private fun appendData(intent: Intent) {
        when (intent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                val message = intent.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                println("$TAG message  $message")
//                strBuilder.append("\n")
//                strBuilder.append("${System.currentTimeMillis()} :- $message")
                binding.tvReceive.text = message
            }
            Constants.EVENT_BLE_CONNECTION_ERROR -> {
                set()
                val message =
                    intent.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
//                        bleStateView1.state = BluetoothState.State.OFF

            }
            Constants.EVENT_BLE_CONNECTION_ABORT -> {
                set()
            }
            Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                set()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver)
    }

    fun set() {
        if (isBluetoothOn() && service.isDeviceConnected(macAddress!!)) {
            binding.tvStatus.text = RxBleConnection.RxBleConnectionState.CONNECTED.name
            Log.e(TAG, "setCurrentBleState: CONNECTED")
            binding.btnConnect.visibility = View.GONE
        } else {
            if (isBluetoothOn()) {
                binding.tvStatus.text = RxBleConnection.RxBleConnectionState.DISCONNECTED.name
                binding.btnConnect.visibility = View.VISIBLE
            }
            Log.e(TAG, "setCurrentBleState: CONNECTED")
        }
    }

    override fun onResume() {
        super.onResume()

        binding.tvReceive.text = strBuilder.toString()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
    }

    private fun initListener() {
        binding.btnSend.setOnClickListener {
            if (binding.etSend.text!!.isNotEmpty()) {
                macAddress?.let { it1 ->
                    service.writeData(
                        it1,
                        "${binding.etSend.text}".toByteArray(Charsets.UTF_8)
                    )
                }
                binding.etSend.text!!.clear()
            } else {
                Toast.makeText(context, "Please Enter Text", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnConnect.setOnClickListener {
            if (isBluetoothOn()) {
                macAddress?.let { it1 -> service.connectMac(it1) }
            } else {
                Toast.makeText(context, "Please Enable Ble", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConnectionFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ConnectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}