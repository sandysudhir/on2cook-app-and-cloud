package com.invent.ontocook.dialog

import android.Manifest
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import co.lujun.androidtagview.TagView.OnTagClickListener
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.DialogRecipeListBinding
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.adapter.FilePagerAdapter
import com.invent.ontocook.multiple_connection.adapter.LocalRecipeAdapter
import com.invent.ontocook.multiple_connection.data_source.FilePagingDataSource
import com.invent.ontocook.multiple_connection.data_source.PagingDataSource
import com.invent.ontocook.multiple_connection.ftp.MyFTPClientFunctions
import com.invent.ontocook.multiple_connection.model.FileModel
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.createTempTextFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class FTPDialog(val macAddress: String) : DialogFragment() {
    private val deviceRecipeList: ArrayList<String?> by lazy { ArrayList() }
    private val deviceAudioList: ArrayList<String?> by lazy { ArrayList() }
    private val audioList: ArrayList<FileModel> by lazy { ArrayList() }
//    private val audioFileList: ArrayList<InputStream> = ArrayList()

    //    private var selectedRecipes = mutableListOf<Recipe>()
    private val selectedRecipes by lazy { mutableListOf<Recipe>() }

    private val selectedDeviceRecipes by lazy { mutableListOf<FileModel>() }

    //    private var selectedAudio = mutableListOf<FileModel>()
    private val selectedAudio by lazy { mutableListOf<FileModel>() }
    private val selectedDeviceAudio by lazy { mutableListOf<FileModel>() }

    //    private var selectedDeviceAudio = mutableListOf<FileModel>()
    lateinit var context: AppCompatActivity
    private var selectedTab = Constants.RECIPE
    private var isDelete = false
    private var isSendRequest = false
    private var isSelectAllProgrammatic = false
    private lateinit var service: BleService
    private lateinit var ftpClient: MyFTPClientFunctions
    private var ssid: String = ""
    private var pass: String = ""
    private lateinit var reservationG: WifiManager.LocalOnlyHotspotReservation
    private lateinit var communicationReceiver: BroadcastReceiver
    private lateinit var binding: DialogRecipeListBinding

    //    private lateinit var fileAdapter: FileListAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val localRecipeAdapter: LocalRecipeAdapter by lazy {
        LocalRecipeAdapter { it, pos ->
            if (it.isSelected) {
                DebugLog.e("FileListAdapter Size ${selectedRecipes.size}")
                binding.rvSelectedLocalRecipeTags.addTag(it.name[0])
                selectedRecipes.add(it)
            } else {
                val foundedIndex =
                    selectedRecipes.indices.find { pos -> selectedRecipes[pos].name[0] == it.name[0] }
                foundedIndex?.let { index ->
                    binding.rvSelectedLocalRecipeTags.removeTag(index)
                    selectedRecipes.remove(it)
                }
                //-------UnNecessary loop commented by Prince-------//
                /*selectedRecipes.forEach {
                    DebugLog.e("listOfSelectedRecipes ${it.name[0]}")
                }*/
            }
        }
    }
    private val audioAdapter: FilePagerAdapter by lazy {
        FilePagerAdapter(Constants.FILE_LIST_TYPE.LOCAL_AUDIO) { it, pos ->
            if (it.isSelected) {
                DebugLog.e("FileListAdapter Size ${selectedRecipes.size}")
                binding.rvLocalAudioTags.addTag(it.fileName)
                selectedAudio.add(it)
            } else {
                val foundedIndex =
                    selectedAudio.indices.find { pos -> selectedAudio[pos].fileName == it.fileName }
                foundedIndex?.let { index ->
                    binding.rvLocalAudioTags.removeTag(index)
                    selectedAudio.removeAt(index)
                }
                //-------Unnecessary loop commented by PrinceEww-----//
                /*selectedAudio.forEach {
                    DebugLog.e("listOfSelectedRecipes ${it.fileName}")
                }*/
            }
        }
    }
    private val deviceRecipeAdapter: FilePagerAdapter by lazy {
        FilePagerAdapter(Constants.FILE_LIST_TYPE.DEVICE_RECIPE) { it, pos ->
            if (it.isSelected) {
                DebugLog.e("FileListAdapter Size ${selectedRecipes.size}")
                binding.rvDeviceRecipeTags.addTag(it.fileName)
                selectedDeviceRecipes.add(it)
            } else {
                val foundedIndex =
                    selectedDeviceRecipes.indices.find { pos -> selectedDeviceRecipes[pos].fileName == it.fileName }
                foundedIndex?.let { index ->
                    binding.rvDeviceRecipeTags.removeTag(index)
                    selectedDeviceRecipes.remove(it)
                }
            }
        }
    }
    private val deviceAudioAdapter: FilePagerAdapter by lazy {
        FilePagerAdapter(Constants.FILE_LIST_TYPE.DEVICE_AUDIO) { it, pos ->
            if (it.isSelected) {
                DebugLog.e("FileListAdapter Size ${selectedRecipes.size}")
                binding.rvDeviceAudioTags.addTag(it.fileName)
                selectedDeviceAudio.add(it)
            } else {
                coroutineScope.launch {
//                    var foundIndex = -1
//                    run breaking@{
//                        selectedDeviceAudio.forEachIndexed { index, taggable ->
//                            DebugLog.e("selectedDeviceAudio $index")
//                            if (taggable.fileName == it.fileName) {
//                                DebugLog.e("selectedDeviceAudio $index")
//                                foundIndex = index
//                                return@breaking
//                            }
//                        }
                    val foundedIndex =
                        selectedDeviceAudio.indices.find { pos -> selectedDeviceAudio[pos].fileName == it.fileName }
                    foundedIndex?.let {
                        binding.rvDeviceAudioTags.removeTag(foundedIndex)
                        selectedDeviceAudio.removeAt(foundedIndex)
                    }
                }
//                }

            }
        }

    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
//            if (service.isDeviceConnected(macAddress))
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_recipe_list, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FullScreenDialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
        checkPermissionAndStartHotspot()
//        openDialog()
    }

    private fun openDialog() {
        if (isAdded) {
            if (service.isDeviceConnected(macAddress))
                service.writeData(
                    macAddress,
                    ("ssid=${ssid},password=${pass},status=off").toByteArray(
                        Charsets.UTF_8
                    )
                )
            if (this::reservationG.isInitialized)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    reservationG.close()
                }
            dismiss()
            coroutineScope.cancel()
        }
//            DialogUtils().commonDialog(requireContext(),
//                getString(R.string.txt_delete_device),
//                "Ftp Connection failed please retry",
//                getString(R.string.button_retry),
//                getString(R.string.button_cancel),
//                true,
//                isCancelable = false,
//                {
//                    startHotspot()
//                },
//                {
//                    if (service.isDeviceConnected(macAddress))
//                        service.writeData(
//                            macAddress,
//                            ("ssid=${ssid},password=${pass},status=off").toByteArray(
//                                Charsets.UTF_8
//                            )
//                        )
//                    if (this::reservationG.isInitialized)
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                            reservationG.close()
//                        }
//                    dismiss()
//                })
    }

    private fun checkPermissionAndStartHotspot() {
        val version13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        DebugLog.e("version13 $version13")

        if (!version13) {
            startHotspot()
            return
        }
        val permissionList = arrayListOf(
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
        )

        PermissionManagerUtils.checkPermission(
            requireContext(),
            requireActivity(),
            permissionList,
            PermissionManagerUtils.PermissionSessionManager(requireActivity()),
            object : PermissionManagerUtils.PermissionAskListener {
                override fun onNeedPermission() {
                    DebugLog.e("onNeedPermission")
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissionList.toTypedArray(),
                        Constants.REQUEST_NEARBY_WIFI_DEVICES
                    )
                }

                override fun onPermissionPreviouslyDenied() {
                    DebugLog.e("onPermissionPreviouslyDenied")
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissionList.toTypedArray(),
                        1
                    )
                }

                override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                    DebugLog.e("onPermissionPreviouslyDeniedWithNeverAskAgain")

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
                            requireContext().openPermissionSettings()
                        },
                        callbackNegative = {
                            //---------Negative CallBack----------//
                        })
                }

                override fun onPermissionGranted() {
                    DebugLog.e("onPermissionGranted")

                    startHotspot()
                }

            })
    }

    private fun initListener() {
        binding.nestedScrollForRecipe.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
            if (scrollY > oldScrollY) {
                DebugLog.e("Scroll DOWN")
            }
            if (scrollY < oldScrollY) {
                DebugLog.e("Scroll UP")
            }
            if (scrollY == 0) {
                DebugLog.e("TOP SCROLL")
            }
            if (scrollY == v.getChildAt(0).measuredHeight - v.measuredHeight) {
                DebugLog.e("BOTTOM SCROLL")
                // here where the trick is going
            }
        } as NestedScrollView.OnScrollChangeListener)

        binding.imgBack.setOnClickListener {
            dismiss()
        }
        binding.imgRetry.setOnClickListener {
            service.writeData(
                macAddress,
                ("ssid=${ssid},password=${pass},status=off").toByteArray(
                    Charsets.UTF_8
                )
            )
            startHotspot()
        }
        binding.tvRecipes.setOnClickListener {
            selectedTab = Constants.RECIPE
            if (isDelete)
                setDeleteUi()
            else
                setSendUi(true)
        }
        binding.tvAudio.setOnClickListener {
            selectedTab = Constants.AUDIO
            if (isDelete)
                setDeleteUi()
            else
                setSendUi(true)
        }

        binding.btnDelete.setOnClickListener {
//            CoroutineScope(Dispatchers.IO).launch {
            coroutineScope.launch {
                delay(200)
                val directory = ftpClient.ftpGetCurrentWorkingDirectory()
                val loop = async {
                    var iteration = 0
                    DebugLog.e(
                        "Handler changeDirectory iteration $iteration Size{${selectedRecipes.size}}"
                    )
                    val list = ArrayList<FileModel>()
                    if (selectedTab == Constants.RECIPE) {
                        requireActivity().runOnUiThread {
                            LoadingUtils.showLoadingWithText(
                                requireContext(),
                                true,
                                requireContext().getString(R.string.txt_deleting_recipe_device)
                            )
                        }
                        list.addAll(selectedDeviceRecipes)
                        if (directory != "/recipe") {
                            if (directory == "/audio")
                                ftpClient.ftpParentDirectory()
                            ftpClient.ftpChangeDirectory("recipe")
                            delay(300)
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            LoadingUtils.showLoadingWithText(
                                requireContext(),
                                true,
                                requireContext().getString(R.string.txt_deleting_audio_device)
                            )
                        }
                        list.addAll(selectedDeviceAudio)
                        if (directory != "/audio") {
                            if (directory == "/recipe")
                                ftpClient.ftpParentDirectory()
                            ftpClient.ftpChangeDirectory("audio")
                            delay(300)
                        }
                    }

                    while (iteration < list.size) {
                        withContext(Dispatchers.IO) {
                            if (isFtpConnected()) {
                                val status = ftpClient.ftpRemoveFile(
                                    list[iteration].fileName.replace("File :: ", ""),
                                )
                                DebugLog.e("ftpUploadFile $status iteration $iteration")
                                if (status)
                                    iteration++
                            }
                        }
                    }
                }
                DebugLog.e("ftpUploadFile loop Wait")
                try {
                    loop.await()
                } catch (e: Exception) {
                    loop.cancel()
                }
                DebugLog.e("ftpUploadFile loop end")
                val list = ftpClient.ftpPrintAllFilesList()
                if (selectedTab == Constants.RECIPE) {
                    deviceRecipeList.clear()
                    selectedDeviceRecipes.clear()
                    DebugLog.e("ftpUploadFile Check ${list?.size} ... ${list?.isNotEmpty() == true}")
                    if (list?.isNotEmpty() == true)
                        deviceRecipeList.addAll(list.toList())
                    if(isAdded) { //PrinceEWW, condition added, to prevent not attached to an activity issue
                        requireActivity().runOnUiThread {
                            isSelectAllProgrammatic = true
                            binding.cbSelectAllRecipe.isChecked = false
                            isSelectAllProgrammatic = false
                            binding.rvDeviceRecipeTags.removeAllTags()
//                        deviceRecipeList.clear()
                            setRecipeToUI()
                        }
                    }
                } else {
                    deviceAudioList.clear()
                    selectedDeviceAudio.clear()
                    if (list?.isNotEmpty() == true)
                        deviceAudioList.addAll(list.toList())
                    requireActivity().runOnUiThread {
                        isSelectAllProgrammatic = true
                        binding.cbSelectAllAudio.isChecked = false
                        isSelectAllProgrammatic = false
                        binding.rvDeviceAudioTags.removeAllTags()
                        setAudioToUI()
                    }
                }

                if(isAdded) { //PrinceEWW, condition added, to prevent not attached to an activity issue
                    requireActivity().runOnUiThread {
                        DebugLog.e("hideDialog 4")
                        LoadingUtils.hideDialog()
                        Toast.makeText(
                            requireContext(),
                            requireContext().getString(R.string.txt_delete_successfully),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }


//            isDelete = true
//            setDeleteUi()
        }
        binding.btnSend.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            if (this::ftpClient.isInitialized && ftpClient.isConnected()) {
//                CoroutineScope(Dispatchers.IO).launch {
                coroutineScope.launch {
                    delay(200)
                    val directory = ftpClient.ftpGetCurrentWorkingDirectory()
                    val loop = async {
                        DebugLog.e(
                            "Handler changeDirectory directory $directory"
                        )
                        if (selectedTab == Constants.RECIPE) {
                            requireActivity().runOnUiThread {
                                LoadingUtils.showLoadingWithText(
                                    requireContext(),
                                    true,
                                    requireContext().getString(R.string.txt_uploading_recipe_device)
                                )
                            }
                            if (directory != "/recipe") {
                                if (directory == "/audio")
                                    ftpClient.ftpParentDirectory()
                                ftpClient.ftpChangeDirectory("recipe")
                                delay(300)
                            }
                            var iteration = 0
                            while (iteration < selectedRecipes.size) {
                                DebugLog.e(
                                    "Handler changeDirectory iteration $iteration Size{${selectedRecipes.size}}"
                                )
                                withContext(Dispatchers.IO) {
                                    if (!ftpClient.isConnected())
                                        dismiss()
                                    val outputFile = requireContext().createTempTextFile(
                                        selectedRecipes[iteration].name[0],
                                        ".txt"
                                    )
                                    val writer = FileOutputStream(outputFile)
                                    writer.write(
                                        Gson().toJson(selectedRecipes[iteration]).toByteArray()
                                    )
                                    writer.flush()
                                    writer.close()
                                    delay(200)
                                    val srcFileStream = FileInputStream(outputFile)
                                    DebugLog.e("srcFileStream ${srcFileStream.read()}")
                                    DebugLog.e("srcFileStream ${outputFile.name}")
                                    val status = ftpClient.ftpUpload(
                                        outputFile,
                                        "${selectedRecipes[iteration].name[0]}.txt",
                                        "recipe",
                                        requireContext()
                                    )
                                    DebugLog.e("ftpUploadFile $status iteration $iteration")
                                    if (status)
                                        iteration++
                                }
                            }
                        } else {
                            requireActivity().runOnUiThread {
                                LoadingUtils.showLoadingWithText(
                                    requireContext(),
                                    true,
                                    requireContext().getString(R.string.txt_uploading_audio_device)
                                )
                            }
                            DebugLog.e("directory $directory")
                            DebugLog.e("directory ${directory != "/audio"}")
                            if (directory != "/audio") {
                                val x = async {
                                    DebugLog.e("directory start ${directory == "/recipe"}")
                                    if (directory == "/recipe") {
                                        val change = ftpClient.ftpParentDirectory()
                                        delay(200)
                                        DebugLog.e("directory change $change")
                                    }
                                }
                                x.await()
                                ftpClient.ftpChangeDirectory("audio")
                                delay(300)
                            }
                            var iteration = 0
                            DebugLog.e(
                                "Handler changeDirectory iteration $iteration Size{${selectedAudio.size}}"
                            )
                            while (iteration < selectedAudio.size) {
                                withContext(Dispatchers.IO) {
//                                    val status = ftpClient.ftpUploadFile(
//                                        selectedAudio[iteration].fileName,
//                                        selectedAudio[iteration].assetsPath,
//                                        requireContext()
//                                    )
                                    if (!ftpClient.isConnected())
                                        dismiss()
                                    var status: Boolean =
                                        if (selectedAudio[iteration].type == Constants.FILE_TYPE.STATIC)
                                            ftpClient.ftpUploadFile(
                                                "${selectedAudio[iteration].fileName}",
                                                selectedAudio[iteration].assetsPath,
                                                requireContext()
                                            )
                                        else {
                                            ftpClient.ftpUpload(
                                                selectedAudio[iteration].file!!,
                                                "${selectedAudio[iteration].fileName}",
                                                "audio",
                                                requireContext()
                                            )
                                        }

                                    DebugLog.e("ftpUploadAudioFile $status iteration $iteration")
                                    if (status)
                                        iteration++
                                }
                            }
                        }
                    }
                    DebugLog.e("ftpUploadFile loop Wait")
                    try {
                        loop.await()
                    } catch (e: Exception) {
                        loop.cancel()
                    }
                    DebugLog.e("ftpUploadFile loop end")
                    val list = ftpClient.ftpPrintAllFilesList()
                    if (list?.isNotEmpty() == true) {
                        if (selectedTab == Constants.RECIPE) {
                            selectedRecipes.clear()
                            deviceRecipeList.clear()
                            deviceRecipeList.addAll(list.toList())
                            requireActivity().runOnUiThread {
                                binding.rvSelectedLocalRecipeTags.removeAllTags()
                                searchRecipe()
                                DebugLog.e("hideDialog 3")
                                LoadingUtils.hideDialog()
                                Toast.makeText(
                                    requireContext(),
                                    requireContext().getString(R.string.txt_sent_successfully),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            selectedAudio.clear()
                            deviceAudioList.clear()
                            deviceAudioList.addAll(list.toList())
                            requireActivity().runOnUiThread {
                                binding.rvLocalAudioTags.removeAllTags()
                                audioList.clear()
                                audioList.addAll(readRawData())
                                searchAudio()
                                DebugLog.e("hideDialog 2")
                                Toast.makeText(
                                    requireContext(),
                                    requireContext().getString(R.string.txt_sent_successfully),
                                    Toast.LENGTH_SHORT
                                ).show()
                                LoadingUtils.hideDialog()
                            }
                        }
                    }
                }
                isDelete = false
                DebugLog.e("Handler Start")
                setSendUi(false)
            } else {
                openDialog()
            }
//            if (!this@RecipeListDialog::ftpClient.isInitialized)
//                return@setOnClickListener
//            DebugLog.e("Handler return")
//
//            CoroutineScope(Dispatchers.IO).launch {
//                val directory = ftpClient.ftpGetCurrentWorkingDirectory()
//                DebugLog.e("Handler CurrentWorking $directory")
//                if (directory == "/") {
//                    delay(200)
//                    if (selectedTab == Constants.RECIPE) {
//                        val changeDirectory = ftpClient.ftpChangeDirectory(
//                            "recipe"
//                        )
//                        DebugLog.e(
//                            "Handler changeDirectory $changeDirectory"
//                        )
//
//                    } else {
//                        val changeDirectory = ftpClient.ftpChangeDirectory(
//                            "audio"
//                        )
//                        DebugLog.e(
//                            "Handler changeDirectory $changeDirectory"
//                        )
//                        delay(400)
//                        val list = ftpClient.ftpPrintAllFilesList()
//                        if (list?.isNotEmpty() == true) {
//                            audioList.clear()
//                            audioList.addAll(list.toList())
//                            setAudioToUI()
//                        }
//                    }
//                }
//
//                delay(400)
//
//                val loop = async {
//                    var iteration = 0
//                    DebugLog.e(
//                        "Handler changeDirectory iteration $iteration Size{${listOfSelectedRecipes.size}}"
//                    )
//                    while (iteration < listOfSelectedRecipes.size) {
//                        withContext(Dispatchers.IO) {
//                            val outputFile = requireContext().createTempTextFile(
//                                listOfSelectedRecipes[iteration].name[0],
//                                ".txt"
//                            )
//                            val writer = FileOutputStream(outputFile)
//                            writer.write(
//                                Gson().toJson(listOfSelectedRecipes[iteration]).toByteArray()
//                            )
//                            writer.flush()
//                            writer.close()
//                            delay(200)
//                            val srcFileStream = FileInputStream(outputFile)
//                            DebugLog.e("srcFileStream ${srcFileStream.read()}")
//                            DebugLog.e("srcFileStream ${outputFile.name}")
//                            val status = ftpClient.ftpUpload(
//                                outputFile,
//                                "${listOfSelectedRecipes[iteration].name[0]}.txt",
//                                "recipe",
//                                requireContext()
//                            )
//                            DebugLog.e("ftpUploadFile $status iteration $iteration")
//                            if (status)
//                                iteration++
//                        }
//                    }
//                }
//                DebugLog.e("ftpUploadFile loop Wait")
//                loop.await()
//                DebugLog.e("ftpUploadFile loop end")
//                val list = ftpClient.ftpPrintAllFilesList()
//
//            }
        }

        binding.btnDeleteRight.setOnClickListener {
            if (this::ftpClient.isInitialized && ftpClient.isConnected()) {
                isDelete = true
                selectedTab = Constants.RECIPE
                setDeleteUi()
            } else {
                openDialog()
            }

        }
        binding.btnSendBack.setOnClickListener {
            isDelete = false
            selectedTab = Constants.RECIPE
            setSendUi(true)
        }

        binding.cbSelectAllRecipe.setOnCheckedChangeListener { _, isChecked ->
            if (!isSelectAllProgrammatic) selectAllDeviceRecipes(isChecked)
        }

        binding.cbSelectAllAudio.setOnCheckedChangeListener { _, isChecked ->
            if (!isSelectAllProgrammatic) selectAllDeviceAudio(isChecked)
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DebugLog.e("onReceive ${intent?.getStringExtra(Constants.EVENT_MESSAGE)}")
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    when (intent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                        Constants.EVENT_BLE_NOTIFICATION -> {
                            val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                            when {
                                message.uppercase().contains("HOST=") -> {
                                    if (isAdded)
                                        LoadingUtils.hideDialog()
                                    isSendRequest = false
                                    if (this@FTPDialog::ftpClient.isInitialized)
                                        ftpClient.ftpDisconnect()
                                    ftpClient = MyFTPClientFunctions() {
                                        DebugLog.e("MyFTPClientFunctions Testing 123 SocketException Disconnetion")
                                        coroutineScope.cancel()
                                    }
                                    coroutineScope.launch {
                                        ftpClient.ftpConnect(
                                            message.replace("HOST=", ""),
                                            "ftp",
                                            "ftp",
                                            21
                                        )
                                        delay(200)
                                        DebugLog.e("Testing 123 ${ftpClient.getTimeout()}")
                                        //-------------code for getting data on connection of ftp
                                        /* delay(200)
                                         val directory = ftpClient.ftpGetCurrentWorkingDirectory()
                                         DebugLog.e("Handler CurrentWorking $directory")
                                         if (directory == "/") {
                                             delay(200)
                                             if (selectedTab == Constants.RECIPE) {
                                                 val changeDirectory = ftpClient.ftpChangeDirectory(
                                                     "recipe"
                                                 )
                                                 DebugLog.e(
                                                     "Handler changeDirectory Recipe $changeDirectory"
                                                 )
                                                 delay(400)
                                                 val list = ftpClient.ftpPrintAllFilesList()
                                                 if (list?.isNotEmpty() == true) {
                                                     deviceRecipeList.clear()
                                                     deviceRecipeList.addAll(list.toList())
                                                     setRecipeToUI()
                                                 }
                                             } else {
                                                 val changeDirectory = ftpClient.ftpChangeDirectory(
                                                     "audio"
                                                 )
                                                 DebugLog.e(
                                                     "Handler changeDirectory Audio $changeDirectory"
                                                 )
                                                 delay(400)
                                                 val list = ftpClient.ftpPrintAllFilesList()
                                                 if (list?.isNotEmpty() == true) {
                                                     deviceAudioList.clear()
                                                     deviceAudioList.addAll(list.toList())
                                                     setAudioToUI()
                                                 }
                                             }
                                         }*/

                                    }
                                }

                                message == Constants.EVENT_FTP_DISCONNECTED -> {
                                    coroutineScope.cancel()
                                    dismiss()
                                }
                            }
                        }
                    }
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
//                    val fragment = (activity as HomeActivity).viewPagerAdapter.getFragmentFromMac(
//                        intent.getStringExtra(Constants.MAC_ADDRESS)!!
//                    )
//                    val navController = (fragment as DashboardFragment).navController
//                    val macAddress = intent.getStringExtra(Constants.MAC_ADDRESS)
//                    when (navController.currentDestination!!.id) {
//                        R.id.recipeFragment -> {
//                            (fragment.getCurrentFragment() as RecipeFragment).parseData(intent)
//                        }
//
//                        R.id.homeFragment -> {
//                            navController.navigate(
//                                HomeFragmentDirections.actionHomeFragmentToRecipeFragment(macAddress!!)
//                            )
//                        }
//                        else -> {
//                            DebugLog.e("popBackStack")
////                            navController.popBackStack(R.id.recipeFragment, true)
//                        }
//                    }
                }
            }
        }
        binding.etLocalAudio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchAudio(query)
                DebugLog.e("query $query")

            }
        })
        binding.etLocalRecipe.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchRecipe(query)
//                searchAudio(query)
                DebugLog.e("query $query")
            }
        })
        binding.etDeviceAudio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                setAudioToUI(query)
//                searchDeviceAudio(query)
                DebugLog.e("query $query")
            }
        })
        binding.etDeviceRecipe.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                setRecipeToUI(query)
//                searchDeviceAudio(query)
                DebugLog.e("query $query")
            }
        })
    }

    private fun isFtpConnected(): Boolean {
        return if (!ftpClient.isConnected()) {
            dismiss()
            coroutineScope.cancel()
            false
        } else
            true
    }

    private fun setDeleteUi() {
        // Reset select-all checkboxes whenever delete UI is shown
        isSelectAllProgrammatic = true
        binding.cbSelectAllRecipe.isChecked = false
        binding.cbSelectAllAudio.isChecked = false
        isSelectAllProgrammatic = false
        if (selectedTab == Constants.RECIPE) {
            binding.tvRecipes.background =
                requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvAudio.background =
                requireContext().getDrawable(R.drawable.tab_round_unselected)
            binding.tvRecipes.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.tvAudio.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.black
                )
            )
            binding.sendAudio.viewGone()
            binding.deleteAudio.viewGone()
            binding.sendRecipe.viewGone()
            binding.deleteRecipe.viewShow()
            if (deviceRecipeAdapter.itemCount == 0 && deviceRecipeList.isNotEmpty()) {
                setRecipeToUI()
            }
            if (deviceRecipeList.isNotEmpty())
                return
            coroutineScope.launch {
                requireActivity().runOnUiThread {
                    LoadingUtils.showLoadingWithText(
                        requireContext(),
                        true,
                        requireContext().getString(R.string.txt_fetching_recipe_device)
                    )
                }
                val directory = ftpClient.ftpGetCurrentWorkingDirectory()
                if (directory != "/recipe") {
                    if (directory == "/audio") {
                        val homeDirectory = ftpClient.ftpParentDirectory()
                        DebugLog.e("Start Else 1 $homeDirectory")
                    }
                    delay(200)
                    val changeDirectory = ftpClient.ftpChangeDirectory(
                        "recipe"
                    )

                    DebugLog.e("Start Dir$changeDirectory")

                }
                delay(200)
                DebugLog.e("Start Else 2${ftpClient.ftpGetCurrentWorkingDirectory()}")

                delay(200)
                val list = ftpClient.ftpPrintAllFilesList()
                if (list?.isNotEmpty() == true) {
                    deviceRecipeList.clear()
                    deviceRecipeList.addAll(list.toList())
                    requireActivity().runOnUiThread {
                        setRecipeToUI()
                    }
                }
                DebugLog.e("hideDialog 7")
                requireActivity().runOnUiThread {
                    LoadingUtils.hideDialog()
                }
            }
        } else {
            binding.tvRecipes.background = requireContext().getDrawable(R.drawable.tab_white_left)
            binding.tvAudio.background = requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvAudio.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvRecipes.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.sendAudio.viewGone()
            binding.deleteRecipe.viewGone()
            binding.sendRecipe.viewGone()
            binding.deleteAudio.viewShow()
            DebugLog.e("Start deviceAudioList ${deviceAudioList.size}")
            DebugLog.e("Start deviceAudioList ${deviceAudioAdapter.itemCount}")
            if (deviceAudioAdapter.itemCount == 0 && deviceAudioList.isNotEmpty()) {
                setAudioToUI()
            }
            if (deviceAudioList.isNotEmpty())
                return

            coroutineScope.launch {
                requireActivity().runOnUiThread {
                    LoadingUtils.showLoadingWithText(
                        requireContext(),
                        true,
                        requireContext().getString(R.string.txt_fetching_audio_device)
                    )
                }
                DebugLog.e("Start")

                DebugLog.e("End Else${ftpClient.ftpGetCurrentWorkingDirectory()}")
                delay(200)
                val homeDirectory = ftpClient.ftpParentDirectory()
                DebugLog.e("End home Directory $homeDirectory")
                delay(100)
                val changeDirectory = ftpClient.ftpChangeDirectory(
                    "audio"
                )
                DebugLog.e("End changeDirectory $changeDirectory")
                delay(100)
                if (!changeDirectory)
                    DebugLog.e("End Else${ftpClient.ftpGetCurrentWorkingDirectory()}")
                delay(100)
                val list = ftpClient.ftpPrintAllFilesList()
                if (list?.isNotEmpty() == true) {
                    deviceAudioList.clear()
                    deviceAudioList.addAll(list.toList())
                    requireActivity().runOnUiThread {
                        setAudioToUI()
                    }
                }
                requireActivity().runOnUiThread {
                    DebugLog.e("hideDialog 5")
                    LoadingUtils.hideDialog()
                }
            }
        }
    }

    private fun setSendUi(isBack: Boolean) {
        if (selectedTab == Constants.RECIPE) {
            binding.tvRecipes.background =
                requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvAudio.background =
                requireContext().getDrawable(R.drawable.tab_round_unselected)
            binding.tvRecipes.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.tvAudio.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.black
                )
            )
            binding.sendAudio.viewGone()
            binding.deleteAudio.viewGone()
            binding.deleteRecipe.viewGone()
            binding.sendRecipe.viewShow()
        } else {
            binding.tvRecipes.background =
                requireContext().getDrawable(com.invent.ontocook.R.drawable.tab_white_left)
            binding.tvAudio.background =
                requireContext().getDrawable(R.drawable.tab_blue_left)
            binding.tvAudio.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.tvRecipes.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.black
                )
            )
            binding.sendRecipe.viewGone()
            binding.deleteAudio.viewGone()
            binding.deleteRecipe.viewGone()
            binding.sendAudio.viewShow()

            if (audioList.isEmpty()) {
                audioList.clear()
                audioList.addAll(readRawData())
                searchAudio()
            }
        }
    }


    override fun onResume() {
        super.onResume()

        DebugLog.e("onResume")
        if (OnToCookApplication.rxBleClient.isScanRuntimePermissionGranted) {
//            if (isBluetoothOn() && !OnToCookApplication.instance.isDeviceConnected())
//                Constants.prepareScanObserver()
        }
        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(
            communicationReceiver
        )
    }

    private fun startHotspot() {
        isSendRequest = true
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            LoadingUtils.hideDialog()
            if (isSendRequest) {
                openDialog()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }, 7000)
        CoroutineScope(Dispatchers.Main).launch {
            LoadingUtils.showLoadingWithText(
                requireContext(),
                true,
                requireContext().getString(R.string.txt_connecting)
            )
        }
        if (this::reservationG.isInitialized)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reservationG.close()
            }
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
            if (this::reservationG.isInitialized)
                handler.removeCallbacksAndMessages(null)
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
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
//                    }
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
                }, handler
            )


//            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        coroutineScope.cancel()
        if (service.isDeviceConnected(macAddress))
            service.writeData(
                macAddress,
                ("ssid=${ssid},password=${pass},status=off").toByteArray(
                    Charsets.UTF_8
                )
            )
        if (this::reservationG.isInitialized)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reservationG.close()
            }
    }

    private fun init() {
        binding.sendAudio.viewGone()
        binding.deleteAudio.viewGone()
        binding.deleteRecipe.viewGone()
        binding.sendRecipe.viewShow()
//        localRecipeAdapter = LocalRecipeAdapter { it, pos ->
//            if (it.isSelected) {
//                DebugLog.e("FileListAdapter Size ${selectedRecipes.size}")
//                binding.rvSelectedLocalRecipeTags.addTag(it.name[0])
//                selectedRecipes.add(it)
//            } else {
//                var foundIndex = -1
//                run breaking@{
//                    selectedRecipes.forEachIndexed { index, taggable ->
//                        if (taggable.name[0] == it.name[0]) {
//                            foundIndex = index
//                            return@breaking
//                        }
//                    }
//                }
//                if (foundIndex != -1) {
////                    listOfSelectedRecipes.removeAt(foundIndex)
//                    binding.rvSelectedLocalRecipeTags.removeTag(foundIndex)
//                    selectedRecipes.remove(it)
//                }
//                selectedRecipes.forEach {
//                    DebugLog.e("listOfSelectedRecipes ${it.name[0]}")
//                }
//            }
//        }


        binding.rvLocalRecipe.apply {
            adapter = localRecipeAdapter
        }
        binding.rvDeviceAudio.apply {
            adapter = deviceAudioAdapter
        }
        binding.rvLocalAudio.apply {
            adapter = audioAdapter
        }
        binding.rvDeviceRecipe.apply {
            adapter = deviceRecipeAdapter
        }
        searchRecipe()

        DebugLog.e("FileListAdapter Init")
        binding.rvSelectedLocalRecipeTags.setOnTagClickListener(object : OnTagClickListener {
            override fun onTagClick(position: Int, text: String) {
                // ...
            }

            override fun onTagLongClick(position: Int, text: String) {
                // ...
            }

            override fun onSelectedTagDrag(position: Int, text: String) {
                // ...
            }

            override fun onTagCrossClick(position: Int) {
                binding.rvSelectedLocalRecipeTags.removeTag(position)
            }
        })
    }

    private fun setAudioToUI(query: String = "") {
        DebugLog.e("setAudioToUI")

        coroutineScope.launch {
            val list: ArrayList<FileModel> = ArrayList()
            deviceAudioList.forEachIndexed { index, it ->
                if (it?.isNotEmpty() == true)
                    list.add(FileModel(index, it, Constants.FILE_TYPE.STATIC, false))
            }
            val audioPager = Pager(config = PagingConfig(
                pageSize = Constants.PAGE_SIZE,
                initialLoadSize = Constants.PAGE_SIZE,
                enablePlaceholders = false
            ),
                pagingSourceFactory = { FilePagingDataSource(list, query) }
            )
            val audio =
                audioPager.flow/*.map { it.filter { item -> item.id in 51..59 || item.id in 101..119 } }*/
                    .cachedIn(CoroutineScope(Dispatchers.IO))
            audio.collectLatest {
                deviceAudioAdapter.submitData(it)
            }
        }
    }

    private fun setRecipeToUI(query: String = "") {
        DebugLog.e("setRecipeToUI")
        val list: ArrayList<FileModel> = ArrayList()
        DebugLog.e("setRecipeToUI Check device RecipeList ${deviceRecipeList.size}")
        deviceRecipeList.forEachIndexed { index, it ->
            DebugLog.e("setRecipeToUI Check device RecipeList ${deviceRecipeList.size} .. check ${it?.isNotEmpty() == true}")
            if (it?.isNotEmpty() == true)
                list.add(FileModel(index, it, Constants.FILE_TYPE.STATIC, false))
        }
        DebugLog.e("Init FilePagingDataSource ${list.size}")
        val pagingSourceFactory = FilePagingDataSource(list, query)
        val recipePager = Pager(config = PagingConfig(
            pageSize = Constants.PAGE_SIZE,
            initialLoadSize = Constants.PAGE_SIZE
        ),
            pagingSourceFactory = { pagingSourceFactory }
        )
        val audio =
            recipePager.flow/*.map { it.filter { item -> item.id in 51..59 || item.id in 101..119 } }*/
                .cachedIn(CoroutineScope(Dispatchers.IO))
        coroutineScope.launch {
            audio.collectLatest {
                DebugLog.e("Init FilePagingDataSourceSubmit Data")
                deviceRecipeAdapter.submitData(it)
            }
        }
    }

    private fun readRawData(): ArrayList<FileModel> {
        val data: ArrayList<FileModel> = ArrayList()
        try {
//            audioFileList.clear()
            requireContext().assets.list("audio")?.forEachIndexed { index, item ->
                Log.e("PrinceEWW>>>", "item: $item")
                val assetManager = requireContext().assets
                if (assetManager.list("audio/$item")!!.isNotEmpty()) {
                    Log.e("PrinceEWW>>>", "item: $item & itemSize: ${assetManager.list("audio/$item")!!.size}")
                    assetManager.list("audio/$item")!!.forEachIndexed { index, it ->
                        data.add(
                            FileModel(
                                index,
                                it,
                                Constants.FILE_TYPE.STATIC,
                                false,
                                "audio/$item/$it"
                            )
                        )
                    }
                }
            }
            val folder = File(requireContext().filesDir, "Audio")
            Log.e("PrinceEWW>>>", "audio data size before folder exist: ${data.size}")
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
//                    val list2 = folder.listFiles()?.sortBy { it.lastModified() }
//                    DebugLog.e("List....${listSorted.toList().toTypedArray().contentToString()}")
                    folder.listFiles()?.forEachIndexed { index, file ->
                        data.add(
                            FileModel(
                                id = index,
                                fileName = file.name,
                                assetsPath = file.path, type = Constants.FILE_TYPE.UPLOADED,
                                isSelected = false,
                                file = file, fileSize = file.length()
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
                data.sortWith(Comparator { arg0: FileModel, arg1: FileModel ->
                    if (arg0.fileName != arg1.fileName) {
                        return@Comparator if (arg0.fileName < arg1.fileName) -1 else 1
                    }
                    0
                })
//                Collections.sort(
//                    list
//                ) { o1, o2 -> o1.name.compareTo(o2.name) }
//                data.sortBy { it.fileName }
                Log.e("PrinceEWW>>>", "audio data size inside folder exist: ${data.size}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return data
    }

//    fun getAudioFile(audioName: String): InputStream {
//        val inputStream = requireContext().assets.open("audio/${audioName}")
//        val reader = BufferedReader(InputStreamReader(inputStream))
//        var line: String = ""
//        if (reader.readLine().also { if (it != null) line = it } != null) {
//            audioFileList.add(inputStream)
//            println(audioName)
//            reader.close()
//        }
//        return inputStream
//    }

    private fun searchAudio(query: String = "") {
        val pager = Pager(config = PagingConfig(
            pageSize = Constants.PAGE_SIZE,
            initialLoadSize = Constants.PAGE_SIZE
        ),
            pagingSourceFactory = { FilePagingDataSource(audioList, query) }
        )
        val recipe =
            pager.flow/*.map { it.filter { item -> item.id in 51..59 || item.id in 101..119 } }*/
                .cachedIn(CoroutineScope(Dispatchers.IO))
        coroutineScope.launch {
            recipe.collectLatest {
                Log.e("Collect", "submitData")
                audioAdapter.submitData(it)
            }
        }
    }

    private fun selectAllDeviceRecipes(selectAll: Boolean) {
        selectedDeviceRecipes.clear()
        if (selectAll) {
            deviceRecipeList.forEach { fileName ->
                if (fileName?.isNotEmpty() == true) {
                    val fileModel = FileModel(
                        deviceRecipeList.indexOf(fileName),
                        fileName,
                        Constants.FILE_TYPE.STATIC,
                        true
                    )
                    selectedDeviceRecipes.add(fileModel)
                }
            }
        }
        requireActivity().runOnUiThread {
            binding.rvDeviceRecipeTags.removeAllTags()
            if (selectAll) {
                selectedDeviceRecipes.forEach { binding.rvDeviceRecipeTags.addTag(it.fileName) }
            }
            deviceRecipeAdapter.snapshot().items.forEach { it.isSelected = selectAll }
            deviceRecipeAdapter.notifyDataSetChanged()
        }
    }

    private fun selectAllDeviceAudio(selectAll: Boolean) {
        selectedDeviceAudio.clear()
        if (selectAll) {
            deviceAudioList.forEach { fileName ->
                if (fileName?.isNotEmpty() == true) {
                    val fileModel = FileModel(
                        deviceAudioList.indexOf(fileName),
                        fileName,
                        Constants.FILE_TYPE.STATIC,
                        true
                    )
                    selectedDeviceAudio.add(fileModel)
                }
            }
        }
        requireActivity().runOnUiThread {
            binding.rvDeviceAudioTags.removeAllTags()
            if (selectAll) {
                selectedDeviceAudio.forEach { binding.rvDeviceAudioTags.addTag(it.fileName) }
            }
            deviceAudioAdapter.snapshot().items.forEach { it.isSelected = selectAll }
            deviceAudioAdapter.notifyDataSetChanged()
        }
    }

    private fun searchRecipe(query: String = "") {
        val pager = Pager(config = PagingConfig(
            pageSize = Constants.PAGE_SIZE,
            initialLoadSize = Constants.PAGE_SIZE
        ),
            pagingSourceFactory = { PagingDataSource(query) }
        )
        val recipe =
            pager.flow/*.map { it.filter { item -> item.id in 51..59 || item.id in 101..119 } }*/
                .cachedIn(CoroutineScope(Dispatchers.IO))
        coroutineScope.launch {
            recipe.collectLatest {
                Log.e("Collect", "submitData")
                localRecipeAdapter.submitData(it)
            }
        }
    }
}