package com.invent.ontocook.multiple_connection

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.core.view.setMargins
import androidx.databinding.DataBindingUtil 
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.databinding.ActivityHomeTvBinding
import com.invent.ontocook.databinding.DialogScanDeviceListBinding
import com.invent.ontocook.dialog.ChooseDeviceForSetupDialog
import com.invent.ontocook.dialog.FTPDialog
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.extension.openWifiPermissionSettings
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.showSnackBarLong
import com.invent.ontocook.extension.showSnackBarShort
import com.google.android.material.snackbar.Snackbar
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.multiple_connection.adapter.AvailableDevicesAdapter
import com.invent.ontocook.multiple_connection.adapter.PairedDevicesAdapter
import com.invent.ontocook.multiple_connection.model.PairedDeviceData
import com.invent.ontocook.multiple_connection.model.TabFragmentData
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.ui.CookingFragment
import com.invent.ontocook.multiple_connection.ui.DashboardFragment
import com.invent.ontocook.multiple_connection.ui.HomeFragment
import com.invent.ontocook.multiple_connection.ui.LogFragment
import com.invent.ontocook.multiple_connection.ui.RecipeDetailFragment
import com.invent.ontocook.multiple_connection.ui.RecipeFragment
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.SharedPreferencesManager
import com.invent.ontocook.utils.getTextFromFile
import com.invent.ontocook.utils.getZipFileFromFiles
import com.invent.ontocook.utils.gone
import com.invent.ontocook.utils.goneIfOrVisible
import com.invent.ontocook.utils.makeFileCopyInCacheDir
import com.invent.ontocook.utils.notNullAndNotEmpty
import com.invent.ontocook.utils.onSafeClick
import com.invent.ontocook.utils.putEnum
import com.invent.ontocook.utils.shareZipFile
import com.invent.ontocook.utils.visible
import com.invent.ontocook.utils.visibleIfOrGone
import com.invent.ontocook.utils.withNotNull
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eo.view.bluetoothstate.BluetoothState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class HomeTvActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeTvBinding
    var myFragmentMap = HashMap<String, TabFragmentData>()
    private var allRecipeList = mutableListOf<Recipe>()
    private var zipFileShare = ArrayList<File>()
    private var fabOpen: Animation? = null
    private var fabClose: Animation? = null
    private var fabClock: Animation? = null
    private var fabAntiClock: Animation? = null
    private lateinit var service: BleService
    var myFragments = ArrayList<Fragment>()
    val changeDummyMac = MutableLiveData<String>()

    var macAddressList = ArrayList<String>()
    lateinit var broadcastReceiver: BroadcastReceiver
    private var doubleBackToExitPressedOnce = false
    var openDialog = false
    val hashmap = HashMap<String, BluetoothDevice>()

    private lateinit var dialog: Dialog
    private lateinit var adapter: AvailableDevicesAdapter
    private var availableDevicesList: MutableList<BluetoothDevice> = mutableListOf()
    private var pairedDevicesList: MutableList<PairedDeviceData> = mutableListOf()
    private lateinit var pairedDeviceAdapter: PairedDevicesAdapter
    private lateinit var dialogScanDeviceListBinding: DialogScanDeviceListBinding

    //-------This macAddress is use for FTP functionalities(Device Setup and Upload Audio)-------//
    private var macAddress: String = ""
    //-------This broadcast is use for receive data of BLE Notification(EVENT_BLE_COMMUNICATION)(For FTP)-------//
    private lateinit var communicationReceiver: BroadcastReceiver

    //-------Need to check device is free(in idle condition or not), before perform some task, like setupDevice, etc...-------//
    private var checkStatus = Constants.CheckStatus.DEFAULT

    //-------In this screen, we need to pick zip from file picker for "upload recipe" and "upload audio", so set "selectedZipType" accordingly and manage functionality for picked recipe and picked audio in "resultLauncher"-------//
    private var selectedZipType = ""

    //-------"selectedType" indicate types of audio for upload audio feature, selectedType should be, "enum class AUDIO_TYPE"-------//
    private lateinit var selectedType: Constants.AUDIO_TYPE

    private var resultLauncher =
        (this as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent = result.data!!
                data.data?.also { uri ->
                    //Permission needed if you want to retain access even after reboot
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    // Perform operations on the document using its URI.
                    if (selectedZipType == Constants.AUDIO) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runOnUiThread {
                                LoadingUtils.showLoading(this@HomeTvActivity, false)
                            }
                            val fileName = Constants.getFileName(
                                this@HomeTvActivity, uri = uri
                            )
                            DebugLog.e("position  $fileName")
                            val path = makeFileCopyInCacheDir(this@HomeTvActivity, uri)
                            val file = File(path)

                            val externalStorageDir = filesDir

                            val folder = File(
                                externalStorageDir, when (selectedType) {
                                    Constants.AUDIO_TYPE.FEEDBACK -> {
                                        Constants.FEEDBACK_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.ERROR -> {
                                        Constants.ERROR_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.TIME -> {
                                        Constants.TIME_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.QUANTITY -> {
                                        Constants.QTY_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.ACTION -> {
                                        Constants.ACTION_AUDIO_PATH
                                    }

                                    Constants.AUDIO_TYPE.INGREDIENTS -> {
                                        Constants.INGREDIENTS_AUDIO_PATH
                                    }
                                }
                            )

                            if (!folder.exists()) {
                                folder.mkdirs()
                            }
                            unZipAudio(file, folder.path).also {
                                runOnUiThread {
                                    LoadingUtils.hideDialog()
                                }
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            val path = makeFileCopyInCacheDir(this@HomeTvActivity, uri)
                            val rootDes = File(externalCacheDir, "Temp")
                            if (!rootDes.exists()) {
                                rootDes.mkdir()
                            }
                            val file = File(path)
                            runOnUiThread {
                                LoadingUtils.showLoading(this@HomeTvActivity, false)
                            }
                            val externalStorageDir = getExternalFilesDir(null)

                            // Create a new folder in the external storage directory with the name of your app.

                            // Create a new folder in the external storage directory with the name of your app.
                            val folder = File(externalStorageDir, "OnToCook")

                            val root = File(externalCacheDir, "Recipes")

                            if (!root.exists()) {
                                root.mkdirs()
                            }
                            val subRoot = File(folder, file.nameWithoutExtension)
                            if (!subRoot.exists()) {
                                subRoot.mkdirs()
                            }
                            //test
//                        val subRoot = File(root, file!!.nameWithoutExtension)
//                        if (!subRoot.exists()) {
//                            subRoot.mkdirs()
//                        }
//

                            unZipNew(file, subRoot.path).also {
                                runOnUiThread {
                                    LoadingUtils.hideDialog()

                                    //-------If user upload new recipe need to update recipe list in RecipeFragment-------//
                                    updateAllRecipeListInRecipeFragment()
                                }
                            }
                        }
                    }
                }
            }
        }

    //-------Launcher while navigate to create recipe, if user edit recipe this recipe this will launcher will be call-------//
    private var createNewRecipeResultLauncher =
        (this as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.e("PrinceEWW>>>", "RESULT_OK - New Recipe Created")

                //-------If user create new recipe need to update recipe list in RecipeFragment-------//
                updateAllRecipeListInRecipeFragment()
            }
        }

    private val getResultForBLE = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }
    private val getResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home_tv)
        DebugLog.e("onCreate")
        //UnComment for 7 device connectivity
//        setWidthOfAllViews() //Set margin of all views Programatically

//        val list = ArrayList<PairedDeviceData>()
//        list.add(PairedDeviceData("1", "1", false))
//        list.add(0, PairedDeviceData("1", "2", false))
////        list.add(PairedDeviceData("1", "3", false))
////        list.add(PairedDeviceData("1", "4", false))
////        list.add(PairedDeviceData("1", "5", false))
//        val int = list.minBy { it.name.toInt() }
//        DebugLog.e("list Size ${list.size} ${list.toString()}")
//        var oldValue = 0
//        run breaking@{
//            list.forEachIndexed { index, pairedDeviceData ->
//                if (pairedDeviceData.name.toInt() - oldValue > 1) {
//                    DebugLog.e("Answer $index old value $oldValue value ${pairedDeviceData.name.toInt()}")
//                    return@breaking
//                }
//                oldValue = pairedDeviceData.name.toInt()
//            }
//        }
        checkPermission()
        init()
        initListener()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.EVENT_STOP_SCAN)
        intentFilter.addAction(Constants.FILE_RECEIVE_SUCCESS)
        intentFilter.addAction(Constants.CHANGE_DEVICE_NAME)
        intentFilter.addAction(Constants.EVENT_BLE_CONNECTION)
        intentFilter.addAction(Constants.EVENT_LOG)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, intentFilter
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
    }

    private fun initListener() {
        //-------Need to observe touch event of "constraintLayoutMenuBackground", on tap this layout, hide this layout and menu-------//
        binding.constraintLayoutMenuBackground.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_CANCEL ||event.action == MotionEvent.ACTION_UP) {
                toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        binding.btnAction.setOnClickListener {
            toggleFloatingActionButton(!binding.clMenu.isVisible) //Toggle of floating action menu visibility
            /*Log.e("PrinceEWW>>>", "btnAction test myFragmentMap: - $myFragmentMap")
            Log.e("PrinceEWW>>>", "btnAction test pairedDevicesList: - $pairedDevicesList")
            Log.e("PrinceEWW>>>", "btnAction test macAddressList: - $macAddressList")
            Log.e("PrinceEWW>>>", "btnAction test macAddressList from preference: - ${SharedPreferencesManager.getMacAddressList(this@HomeTvActivity)}")*/
        }


        //-------Device setup-------//
        binding.clSetUpDevice.onSafeClick {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            if (pairedDevicesList.notNullAndNotEmpty()) {
                //-------If pairedDevicesList is not empty, check the size of pairedDevicesList-------//
                if (pairedDevicesList.size > Constants.DEFAULT_ONE) {
                    //-------If there is more than one paired devices in pairedDevicesList, then open dialog for select device for FTP-------//
                    openChooseDeviceForSetupDialog()
                } else {
                    //-------If there is only single item in pairedDevicesList, then do process of FTP with that device-------//
                    val deviceForSetupDataBean = pairedDevicesList[Constants.DEFAULT_ZERO]
                    if (service.isDeviceConnected(deviceForSetupDataBean.macAddress)) {
                        //-------If device is connected, then do process of FTP-------//
                        macAddress = deviceForSetupDataBean.macAddress
                        checkStatus(Constants.CheckStatus.FTPCONNECTION)
                    } else {
                        //-------If device is not connected, then show toast for connect device-------//
                        Toast.makeText(
                            this@HomeTvActivity,
                            getString(R.string.strConnect),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                //-------If pairedDevicesList is empty, show toast for Connect device-------//
                Toast.makeText(
                    this@HomeTvActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Device setup-------//
        binding.btnSetUp.onSafeClick {
            binding.clSetUpDevice.callOnClick()
        }



        binding.clUploadRecipe.setSafeOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openFilePicker(Constants.RECIPE)
            else PermissionManagerUtils.checkPermission(applicationContext,
                this,
                arrayListOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PermissionManagerUtils.PermissionSessionManager(applicationContext),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            this@HomeTvActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            this@HomeTvActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), 1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------Here open dialog for ask user to provide permission if they previously denied-------//
                        DialogUtils().commonDialog(context = this@HomeTvActivity,
                            title = getString(R.string.title_permission_open_settings),
                            message = getString(R.string.message_storage_camera_permission),
                            positiveButton = getString(R.string.button_setting),
                            negativeButton = getString(R.string.button_cancel),
                            isAppLogoDisplay = true,
                            isCancelable = true,
                            callbackSuccess = {
                                //------Positive CallBack----//
                                openPermissionSettings()
                            },
                            callbackNegative = {
                                //---------Negative CallBack----------//
                            })
                    }

                    override fun onPermissionGranted() {
                        openFilePicker(Constants.RECIPE)
                    }

                })
        }

        binding.clUploadAudio onSafeClick {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openAudioDialog()
            else PermissionManagerUtils.checkPermission(this@HomeTvActivity,
                this@HomeTvActivity,
                arrayListOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PermissionManagerUtils.PermissionSessionManager(this@HomeTvActivity),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            this@HomeTvActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            this@HomeTvActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), 1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------Here open dialog for ask user to provide permission if they previously denied-------//
                        DialogUtils().commonDialog(context = this@HomeTvActivity,
                            title = getString(R.string.title_permission_open_settings),
                            message = getString(R.string.message_storage_camera_permission),
                            positiveButton = getString(R.string.button_setting),
                            negativeButton = getString(R.string.button_cancel),
                            isAppLogoDisplay = true,
                            isCancelable = true,
                            callbackSuccess = {
                                //------Positive CallBack----//
                                this@HomeTvActivity.openPermissionSettings()
                            },
                            callbackNegative = {
                                //---------Negative CallBack----------//
                            })
                    }

                    override fun onPermissionGranted() {
                        openAudioDialog()
                    }

                })
        }

        binding.btnUploadAudio onSafeClick {
            binding.clUploadAudio.callOnClick()
        }


        binding.clShareRecipe.setSafeOnClickListener {
            LoadingUtils.showLoading(this@HomeTvActivity, false, "Please Wait")
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            zipFileShare.clear()
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    allRecipeList.forEach { recipe ->
                        val root = File(externalCacheDir, "Temp")
                        if (root.exists())
                            root.deleteOnExit()
                        root.mkdirs()

                        val listOfFiles = arrayListOf<File>()
                        val recipeTextFile = File(root, recipe.name[0] + ".txt")
                        val writer = FileOutputStream(recipeTextFile)
                        writer.write(Gson().toJson(recipe).toByteArray())
                        writer.flush()
                        writer.close()
                        listOfFiles.add(recipeTextFile)
                        if (recipe.imageUrl.isNotEmpty()) {
                            try {
                                val imageFile = File(root, recipe.name[0] + ".jpg")
                                val imgWriter = FileOutputStream(imageFile)
                                val iStream: InputStream? =
                                    contentResolver.openInputStream(Uri.parse(recipe.imageUrl))
                                if (iStream != null) {
                                    val inputData: ByteArray = Constants.getBytes(iStream)!!
                                    imgWriter.write(inputData)
                                    imgWriter.flush()
                                    imgWriter.close()
                                    listOfFiles.add(imageFile)
                                }
                            } catch (e: FileNotFoundException) {
                                e.printStackTrace()
                                // File not found or other exception occurred
                                // Handle the exception accordingly
                            } catch (e: IOException) {
                                e.printStackTrace()
                                // IOException occurred while reading the file
                                // Handle the exception accordingly
                            }
                        }
                        recipe.Ingredients.forEachIndexed { index, it ->
                            if (it.image.isNotEmpty()) {
                                var imageFile = File(root, it.title + "_" + index + ".jpg")
                                try {
                                    val iStream: InputStream? =
                                        contentResolver.openInputStream(Uri.parse(it.image))
                                    if (iStream != null) {
                                        if (!imageFile.exists()) {
                                            imageFile.createNewFile()
                                        }
                                        val imgWriter = FileOutputStream(imageFile)
                                        val inputData: ByteArray = Constants.getBytes(iStream)!!
                                        imgWriter.write(inputData)
                                        imgWriter.flush()
                                        imgWriter.close()
                                        listOfFiles.add(imageFile)
                                    }
                                } catch (e: FileNotFoundException) {
                                    e.printStackTrace()
                                    // File not found or other exception occurred
                                    // Handle the exception accordingly
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    // IOException occurred while reading the file
                                    // Handle the exception accordingly
                                }
                            }
                        }
                        zipFileShare.add(
                            getZipFileFromFiles(
                                listOfFiles, recipe
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    shareZipFile(zipFileShare, "Recipe")
                    LoadingUtils.hideDialog()
                }
            }

        }

        //-------Create recipe click listener-------//
        binding.clCreateRecipe.setOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            /*val list = ArrayList<Recipe>()
            list.addAll(allRecipeList)*/
            val intent = Intent(this, CreateNewRecipe::class.java)
//            intent.putExtra(Constants.RECIPE_LIST, Gson().toJson(list)) //by GaurangEWW
            intent.putExtras(Bundle().apply {
                putEnum(
                    Constants.BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE,
                    Constants.CreateRecipeScreenFlowType.CREATE_RECIPE
                )//Differentiate screen flow type
            })
//            startActivity(intent)
            createNewRecipeResultLauncher.launch(intent)
        }

        //-------Create fry recipe click listener-------//
        binding.clCreateFryRecipe.setOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            val intent = Intent(this, CreateNewRecipe::class.java)
            intent.putExtras(Bundle().apply {
                putEnum(
                    Constants.BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE,
                    Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE
                )//Differentiate screen flow type
            })
//            startActivity(intent)
            createNewRecipeResultLauncher.launch(intent)
        }



        binding.ivAddDevice.setOnClickListener {
            openDialog = true
            checkPermission()
        }
        binding.ivScan.setOnClickListener {
            scanDialog()
        }
        binding.ivStop.setOnClickListener {
//            val fragment =
//                viewPagerAdapter.getFragmentFromMac(macAddress[binding.myViewPager2.currentItem])
//                    ?: return@setOnClickListener
//            val navController = (fragment as DashboardFragment).navController
//            if (navController.currentDestination == null) return@setOnClickListener
//            when (navController.currentDestination!!.id) {
//                R.id.cookingFragment -> {
//                    (fragment.getCurrentFragment() as CookingFragment).openCloseDialog()
//                }
//            }
        }
        binding.ivLeft.setOnClickListener {
//            (myFragments[binding.myViewPager2.currentItem] as DashboardFragment).onBackPress()
        }
        binding.bleStateView1.setOnClickListener {
//            service.connectMac(macAddress[binding.myViewPager2.currentItem])
            //-------Below function call by PrinceEWW for start bluetooth device scanning-------//
            startScan(false)
        }

        binding.toolbar1.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(0) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        binding.toolbar2.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(1) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        binding.toolbar3.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(2) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        binding.toolbar4.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(3) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        binding.toolbar5.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(4) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        /*binding.toolbar6.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(5) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }
        binding.toolbar7.ivLeft.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(6) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { _ ->
                    navController.popBackStack()
                }
            }
        }*/

        binding.toolbar1.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(0, Constants.STOP)
        }
        binding.toolbar2.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(1, Constants.STOP)
        }
        binding.toolbar3.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(2, Constants.STOP)
        }
        binding.toolbar4.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(3, Constants.STOP)
        }
        binding.toolbar5.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(4, Constants.STOP)
        }
        //UnComment for 7 device connectivity
        /*binding.toolbar6.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(5, Constants.STOP)
        }
        binding.toolbar7.ivStop.setSafeOnClickListener {
            sendCommandBasedOnIndex(6, Constants.STOP)
        }*/

        binding.toolbar1.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(0) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                            }
                        }
                    }
                }
            }
        }
        binding.toolbar2.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(1) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                            }
                        }
                    }
                }
            }
        }
        binding.toolbar3.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(2) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                            }
                        }
                    }
                }
            }
        }
        binding.toolbar4.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(3) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                            }
                        }
                    }
                }
            }
        }
        binding.toolbar5.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(4) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openDatePicker()
                            }
                        }
                    }
                }
            }
        }
        //UnComment for 7 device connectivity
        /*binding.toolbar6.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(5) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openFilterDialog()
                            }
                        }
                    }
                }
            }
        }
        binding.toolbar7.ivFilter.setSafeOnClickListener {
            val fragment = getFragmentFromIndex(6) ?: return@setSafeOnClickListener
            val navController = (fragment as DashboardFragment).navController
            navController?.let {
                it.currentDestination.let { des ->
                    des.let {
                        when (navController.currentDestination!!.id) {
                            R.id.logFragment -> {
                                (fragment.getCurrentFragment() as LogFragment).openFilterDialog()
                            }
                        }
                    }
                }
            }
        }*/

        binding.toolbar1.ivFetch.setSafeOnClickListener {
            refreshOrSendCommand(0)
        }
        binding.toolbar2.ivFetch.setSafeOnClickListener {
            refreshOrSendCommand(1)
        }
        binding.toolbar3.ivFetch.setSafeOnClickListener {
            refreshOrSendCommand(2)
        }
        binding.toolbar4.ivFetch.setSafeOnClickListener {
            refreshOrSendCommand(3)
        }
        binding.toolbar5.ivFetch.setSafeOnClickListener {
            refreshOrSendCommand(4)
        }
        //UnComment for 7 device connectivity
        /*binding.toolbar6.ivFetch.setSafeOnClickListener {
            sendCommandBasedOnIndex(5, Constants.CHECKLOGSTATUS)
        }
        binding.toolbar7.ivFetch.setSafeOnClickListener {
            sendCommandBasedOnIndex(6, Constants.CHECKLOGSTATUS)
        }*/

        binding.toolbar1.ivMenu.setSafeOnClickListener {
            openMenu(0, it)
        }
        binding.toolbar2.ivMenu.setSafeOnClickListener {
            openMenu(1, it)
        }
        binding.toolbar3.ivMenu.setSafeOnClickListener {
            openMenu(2, it)
        }
        binding.toolbar4.ivMenu.setSafeOnClickListener {
            openMenu(3, it)
        }
        binding.toolbar5.ivMenu.setSafeOnClickListener {
            openMenu(4, it)
        }
        //UnComment for 7 device connectivity
        /*binding.toolbar6.ivMenu.setSafeOnClickListener {
            openMenu(5, it)
        }
        binding.toolbar7.ivMenu.setSafeOnClickListener {
            openMenu(6, it)
        }*/
    }

    //-------Need to set width of all views Programatically-------//
    private fun setWidthOfAllViews() {
        val displayMetrics = resources.displayMetrics
        val deviceWidth = displayMetrics.widthPixels
        val itemWidth = deviceWidth / 5 //On screen 5 views should be visible, if there are 5 or morethan 5 devices are connected

        val params = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        params.setMargins(2) //Set margin from all side
        binding.clFragment1.layoutParams = params
        binding.clFragment2.layoutParams = params
        binding.clFragment3.layoutParams = params
        binding.clFragment4.layoutParams = params
        binding.clFragment5.layoutParams = params
        binding.clFragment6.layoutParams = params
        binding.clFragment7.layoutParams = params
    }

    //-------In this function, we manage commands, which we got from firmware device-------//
    internal fun parseData(intent: Intent?) {
        when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                if (message.lowercase() == Constants.IDLE_DEVICE) {
                    //-------If device status is idle, do process for why we check status of device-------//
                    isAvailable()
                }
            }
        }
    }

    //-------Open dialog of choose device for device setup(FTP), when there is more than one devices are connected-------//
    private fun openChooseDeviceForSetupDialog() {
        ChooseDeviceForSetupDialog.newInstance(
            devicesArrayList = pairedDevicesList as ArrayList<PairedDeviceData>
        ).apply {
            deviceForSetupItemClickCallback = { position, deviceForSetupDataBean ->
                dismiss()
                //-------On tap device, check clicked device is connected or not-------//
                if (service.isDeviceConnected(deviceForSetupDataBean.macAddress)) {
                    //-------If selected device is connected, do process for device Setup (FTP)-------//
                    macAddress = deviceForSetupDataBean.macAddress
                    checkStatus(Constants.CheckStatus.FTPCONNECTION)
                } else {
                    //-------If selected device is not connected, then show toast that "Selected device is not connected-------//
                    Toast.makeText(
                        this@HomeTvActivity, getString(R.string.strSelectedDeviceIsNotConnected), Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }.show(supportFragmentManager, ChooseDeviceForSetupDialog::class.simpleName)
    }

    //-------Check status command-------//
    //-------Need to check device is free(in idle condition or not), before perform some task, like setupDevice, etc...-------//
    private fun checkStatus(status: Constants.CheckStatus) {
        checkStatus = status
        if (macAddress != Constants.DummyMacAddress) {
            service.writeData(
                macAddress, Constants.STATUS.toByteArray(
                    Charsets.UTF_8
                )
            )
        }
    }

    //-------Check status after successfully connection, for navigate to cooking screen if any recipe is running-------//
    private fun checkStatusAfterConnectionSuccess(connectedMac: String) {
        if (::service.isInitialized) {
            service.writeData(
                connectedMac, Constants.STATUS.toByteArray(
                    Charsets.UTF_8
                )
            )
        }
    }

    //-------After check status of device and device is available(free(in idle state)), need to perform task, for which we check status-------//
    private fun isAvailable() {
        when (checkStatus) {
            Constants.CheckStatus.FTPCONNECTION -> { //Indicate we check status for FTP process
                //-------For device setup - indicate device is ready for setup-------//
                val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (wifiManager.isWifiEnabled) {
                    //-------Wifi is on, so need to show dialog for turn off wifi-------//
                    DialogUtils().commonDialog(this@HomeTvActivity,
                        getString(R.string.txt_delete_device),
                        getString(R.string.txt_to_enable_ftp_connection_please_turn_off_wifi),
                        getString(R.string.button_yes),
                        getString(R.string.button_cancel),
                        true,
                        isCancelable = true,
                        {
                            //-------Open setting of wifi, for turn off wifi-------//
                            openWifiPermissionSettings()
                        },
                        {

                        })
                } else {
                    //-------If wifi is off, open recipe/audio listing dialog for device setup (FTP)-------//
                    openRecipeListDialog()
                }

            }

            else -> {

            }
        }
        //-------Need to do "checkStatus" DEFAULT after use of check status, for prevent conflict on receive idle command from firmware device-------//
        checkStatus = Constants.CheckStatus.DEFAULT
    }

    //-------Open dialog for list of recipes and audios for setup device-------//
    private fun openRecipeListDialog() {
//        runOnUiThread {
        val FTPDialog = FTPDialog(macAddress)
        FTPDialog.show(supportFragmentManager, "")
//        }
    }

    //-------Toggle of floating action menu visibility-------//
    private fun toggleFloatingActionButton(showMenu: Boolean) { //showMenu = true/false, true for show menu, false for hide menu
        if (showMenu) {
            binding.clMenu.viewShow()
            binding.constraintLayoutMenuBackground.viewShow()
            binding.clItem.alpha = 0.5f
            binding.btnUpload.startAnimation(fabOpen)
            binding.btnUploadAudio.startAnimation(fabOpen)
            binding.btnCreateFryRecipe.startAnimation(fabOpen)
            binding.btnCreate.startAnimation(fabOpen)
            binding.btnShare.startAnimation(fabOpen)
            binding.btnSetUp.startAnimation(fabOpen)
            binding.btnAction.startAnimation(fabClock)
            binding.btnOn2CookAI.startAnimation(fabOpen)
        } else {
            binding.clMenu.viewGone()
            binding.constraintLayoutMenuBackground.viewGone()
            binding.clItem.alpha = 1f
            binding.btnUpload.startAnimation(fabClose)
            binding.btnUploadAudio.startAnimation(fabClose)
            binding.btnCreateFryRecipe.startAnimation(fabClose)
            binding.btnCreate.startAnimation(fabClose)
            binding.btnShare.startAnimation(fabClose)
            binding.btnSetUp.startAnimation(fabOpen)
            binding.btnOn2CookAI.startAnimation(fabClose)
            binding.btnAction.startAnimation(fabAntiClock)
        }
    }

    //-------If user upload/create new recipe need to update recipe list in RecipeFragment-------//
    private fun updateAllRecipeListInRecipeFragment() {
        myFragmentMap.forEach { t, u ->
            try {
                val fragment = myFragmentMap[t]?.fragment ?: return@forEach
                val navController = (fragment as DashboardFragment).navController
                navController?.let { _ ->
                    navController.currentDestination?.let {
                        when (it.id) {
                            R.id.recipeFragment -> {
                                //-------If user upload/create new recipe need to update recipe list in RecipeFragment-------//
                                (fragment.getCurrentFragment() as RecipeFragment).newRecipeCreatedInTablet()
                            }

                        }
                    }

                }

            } catch (e: Exception) {
                DebugLog.e("findAndParseData Exception ${e.message}")
            }
        }
    }

    private fun sendCommandBasedOnIndex(index: Int, message: String) {
        val mac = getMacFromIndex(index)
        mac?.let {
            if (service.isDeviceConnected(mac)) service.writeData(
                mac, message.toByteArray(
                    Charsets.UTF_8
                )
            )
        }
    }

    private fun refreshOrSendCommand(index: Int) {
        val fragment = getFragmentFromMac(macAddressList[index]) ?: return
        val navController = (fragment as DashboardFragment).navController
        navController?.let { navigationController ->
            if (navigationController.currentDestination == null) return
            when (navigationController.currentDestination!!.id) {
                R.id.logFragment -> {
                    // On LogFragment, refresh log files
                    (fragment.getCurrentFragment() as LogFragment).refreshLogFiles()
                }
                else -> {
                    // On other fragments, keep existing behavior
                    sendCommandBasedOnIndex(index, Constants.CHECKLOGSTATUS)
                }
            }
        }
    }

    private fun openMenu(i: Int, it: View) {
        val popupMenu = PopupMenu(this, it)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
        popupMenu.menu.findItem(R.id.java).isVisible = false
        popupMenu.setOnMenuItemClickListener { item ->
            if (item != null) {
                when (item.itemId) {
                    R.id.addDevice -> {
                        openDialog = true
                        checkPermission()
                    }

                    R.id.log -> {
                        DebugLog.e("openMenu $i")
//                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        val mac = getMacFromIndex(i)
                        if (mac != Constants.DummyMacAddress) {
                            val fragment = getFragmentFromIndex(i)
                            if (fragment != null) {
                                val navController =
                                    (fragment as DashboardFragment).navController
                                navController?.let {
                                    it.currentDestination.let {
                                        navController.navigate(
                                            R.id.logFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                            })
                                    }
                                }

                            }
                        }
                    }

                    R.id.liveLog -> {
                        val mac = getMacFromIndex(i)
                        if (mac != Constants.DummyMacAddress) {
                            val fragment = getFragmentFromIndex(i)
                            if (fragment != null) {
                                val navController =
                                    (fragment as DashboardFragment).navController
                                navController?.let {
                                    it.currentDestination.let {
                                        navController.navigate(
                                            R.id.liveLogFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                            })
                                    }
                                }
                            }
                        }
                    }

                    R.id.ota -> {
                        val mac = getMacFromIndex(i)
                        if (mac != Constants.DummyMacAddress) {
                            val fragment = getFragmentFromIndex(i)
                            if (fragment != null) {
                                val navController =
                                    (fragment as DashboardFragment).navController
                                navController?.let {
                                    it.currentDestination.let {
                                        navController.navigate(
                                            R.id.otaFragment,
                                            Bundle().apply {
                                                putString(Constants.MAC_ADDRESS, mac)
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        }
        // Showing the popup menu
        popupMenu.show()

    }

    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            finishAffinity()
            return
        }
        this.doubleBackToExitPressedOnce = true
        showSnackBarShort(resources.getText(R.string.backpress_msg))
        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }

    private fun scanDialog() {
        hashmap.clear()
//        service.startScan()
        dialog = Dialog(this).apply {
            val dialogQrScanBinding: com.invent.ontocook.databinding.DialogQrScanBinding =
                com.invent.ontocook.databinding.DialogQrScanBinding.inflate(
                    LayoutInflater.from(
                        context
                    )
                )
            setContentView(dialogQrScanBinding.root)
            val codeScanner = CodeScanner(context, dialogQrScanBinding.scannerView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window!!.setLayout(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.MATCH_PARENT
            )
            codeScanner.camera = CodeScanner.CAMERA_BACK // or CAMERA_FRONT or specific camera id
            codeScanner.formats = CodeScanner.ALL_FORMATS // list of type BarcodeFormat,
            // ex. listOf(BarcodeFormat.QR_CODE)
            codeScanner.autoFocusMode = AutoFocusMode.SAFE // or CONTINUOUS
            codeScanner.scanMode = ScanMode.SINGLE // or CONTINUOUS or PREVIEW
            codeScanner.isAutoFocusEnabled = true // Whether to enable auto focus or not
            codeScanner.isFlashEnabled = false // Whether to enable flash or not

            // Callbacks
            codeScanner.decodeCallback = DecodeCallback {
                service.connectMac(it.text)
                runOnUiThread {
                    Toast.makeText(context, "Scan result: ${it.text}", Toast.LENGTH_LONG).show()
                }
            }
            codeScanner.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
                runOnUiThread {
                    Toast.makeText(
                        context, "Camera initialization error: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            dialogQrScanBinding.scannerView.setOnClickListener {
                codeScanner.startPreview()
            }
        }
        dialog.show()
    }

    private fun openDialog() {
//        startScan()
//        if (this::dialog.isInitialized && dialog.isShowing) return
        DebugLog.e("openDialog Condition ${this::dialog.isInitialized && dialog.isShowing}")
        if (this::dialog.isInitialized && dialog.isShowing) {
            startScan(false)
            return
        }
        dialog = Dialog(this).apply {
            dialogScanDeviceListBinding =
                DialogScanDeviceListBinding.inflate(LayoutInflater.from(context))
            setContentView(dialogScanDeviceListBinding.root)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window!!.setLayout(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.MATCH_PARENT
            )
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            adapter = AvailableDevicesAdapter(availableDevicesList) { pos, device ->
                DebugLog.e("TestScan Tab Click Connect :- ${device.address}")
                DebugLog.e("AvailableDevicesAdapter_6_7 $pos ${device.address}")
                CoroutineScope(Dispatchers.IO).launch {
//                    val stop = async {
//                        service.stopScan()
//                    }
//                    stop.await()
//                    delay(500)
                    var size = -1
                    val task = async {
                        val savedList = SharedPreferencesManager.getMacAddressList(
                            applicationContext
                        )
                        size = savedList.size
                    }
                    task.await()
                    if (size < 5) {//Set 7 for 7 device connectivity //UnComment for 7 device connectivity
                        runOnUiThread {
                            LoadingUtils.showLoading(this@HomeTvActivity, false, "Please Wait")
                        }
                        service.connect(device)
//                        connectionSuccess(device.address,device.name) //PrinceEWW - Commented by PrinceEWW, connection success should be when we got connection success from bluetooth command (Same as HomeActivity (for Mobile))
                    } else {
                        //-------by PrinceEWW, because we can not bind view on IO thread-------//
                        runOnUiThread {
                            DialogUtils().commonDialog(this@HomeTvActivity,
                                getString(R.string.txt_delete_device),
                                "To connect selected on2cook device delete device",
                                getString(R.string.txt_delete),
                                getString(R.string.button_cancel),
                                true,
                                isCancelable = true,
                                {
//                                    CoroutineScope(Dispatchers.IO).launch {
                                    openDeleteDeviceDialog()
//                                    }
                                },
                                {

                                })
                        }
                    }
//                    delay(700)
//                    service.startScan()
//                    runOnUiThread {
//                        binding.ivAddDevice.viewShow()
//                        if (this@HomeTvActivity::dialogScanDeviceListBinding.isInitialized && dialog.isShowing) {
//                            dialogScanDeviceListBinding.bleStateView.state =
//                                BluetoothState.State.OFF
//                        }
//                    }
                    DebugLog.e("TestScan Tab Click Connect :- ${device.address}")
                }
            }

            dialogScanDeviceListBinding.bleStateView.setSafeOnClickListener {
                if (dialogScanDeviceListBinding.bleStateView.state == BluetoothState.State.OFF) checkPermission()
            }
            dialogScanDeviceListBinding.ivLeft.setSafeOnClickListener {
                dialog.dismiss()
            }

            pairedDeviceAdapter = PairedDevicesAdapter(
                pairedDevicesList
            ) { id, position, pairedDeviceData, editedName ->
                when (id) {
                    R.id.ivDelete -> {
                        DialogUtils().commonDialog(this@HomeTvActivity,
                            getString(R.string.txt_delete_device),
                            "Are you sure you want to delete ${pairedDeviceData.name} ?",
                            getString(R.string.button_yes),
                            getString(R.string.button_no),
                            true,
                            isCancelable = true,
                            {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val pairedDeviceList =
                                        SharedPreferencesManager.getMacAddressList(
                                            applicationContext
                                        )
                                    var removeIndex = -1
                                    val task = async {
                                        run breaking@{
                                            pairedDeviceList.forEachIndexed { index, pairedDeviceData1 ->
                                                if (pairedDeviceData.macAddress == pairedDeviceData1.macAddress) {
                                                    removeIndex = index
                                                    return@breaking
                                                }
                                            }
                                        }
                                    }
                                    task.await()
                                    if (removeIndex != -1) pairedDeviceList.removeAt(removeIndex)
                                    delay(300)
                                    val sharedPref = async {
                                        SharedPreferencesManager.updateMacAddressList(
                                            this@HomeTvActivity, pairedDeviceList
                                        )
                                        service.disconnect(pairedDeviceData.macAddress)
                                    }
                                    runOnUiThread {
                                        pairedDevicesList.removeAt(position)
                                        myFragmentMap.remove(pairedDeviceData.macAddress) //PrinceEWW, need to remove to prevent glitch of fragment replacement
                                        pairedDeviceAdapter.notifyItemRemoved(position)
                                        try {
                                            getId(pairedDeviceData.id, false)
                                        } catch (e: java.lang.Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    DebugLog.e("macAddressCheking_6_7 $position")
                                    macAddressList.remove(pairedDeviceData.macAddress)
                                    hashmap.remove(pairedDeviceData.macAddress)

                                    DebugLog.e("TestScan Tab Disconnect Pos $position")
//                                    myFragments.removeAt(position)

                                    //add
                                    sharedPref.await()
                                    startScan(false)
                                    DebugLog.e("macAddressCheking_6_7 ${macAddressList.isNotEmpty()}")
                                    if (macAddressList.isNotEmpty())
                                        return@launch
                                    //-------If all macaddress are removed, need to add Dummy MacAddress-------//
                                    runOnUiThread {
                                        addDummyMacAddress()
                                    }
                                }
//                                val pairedDeviceList =
//                                    SharedPreferencesManager.getMacAddressList(applicationContext)
//                                var removeIndex = -1
//                                run breaking@{
//                                    pairedDeviceList.forEachIndexed { index, pairedDeviceData ->
//                                        if (pairedDeviceData.macAddress == bluetoothDevice.macAddress) {
//                                            removeIndex = index
//                                            return@breaking
//                                        }
//                                    }
//                                }
//                                if (removeIndex != -1) pairedDeviceList.removeAt(removeIndex)
//                                SharedPreferencesManager.updateMacAddressList(
//                                    this@HomeTvActivity, pairedDeviceList
//                                )
//                                pairedDevicesList.removeAt(position)
//                                pairedDeviceAdapter.notifyItemRemoved(position)
//                                myFragments.removeAt(position)
//                                macAddressList.remove(bluetoothDevice.macAddress)
//                                hashmap.remove(bluetoothDevice.macAddress)
//                                service.disconnect(bluetoothDevice.macAddress)
//
//                                viewPagerAdapter.remove(position, bluetoothDevice.macAddress)
//                                //add
//                                DebugLog.e("macAddressCheking ${macAddress.isNotEmpty()}")
//                                if (macAddressList.isNotEmpty())
//                                    return@commonDialog
//                                addDummyMacAddress()
                            },
                            {

                            })
                    }

                    //-------PrinceEWW - For tablet below code is not working feature is not working(Which is done by previous developer), So do same code as Home Activity(parallel screen for mobile)-------//
                    /*R.id.ivEdit -> {
                        if (!pairedDeviceData.isEdit) {
                            val pairedDeviceList =
                                SharedPreferencesManager.getMacAddressList(applicationContext)
                            run breaking@{
                                pairedDeviceList.forEachIndexed { index, pairedDeviceData ->
                                    if (pairedDeviceData.macAddress == pairedDeviceData.macAddress) {
                                        pairedDeviceData.name = pairedDeviceData.name
                                        return@breaking
                                    }
                                }
                            }
                            SharedPreferencesManager.updateMacAddressList(
                                this@HomeTvActivity,
                                pairedDeviceList
                            )
//                            if (position == binding.myViewPager2.currentItem)
//                                binding.tvPageTitle.text = pairedDevicesList[position].name
                        }
                        pairedDeviceAdapter.notifyItemChanged(position)
                    }*/

                    R.id.ivEdit -> {
                        Log.e("PrinceEWW>>>", "onTapSaveForEditedName - pairedDevicesList $pairedDevicesList")
                        if (!pairedDeviceData.isEdit) {
                            DebugLog.e(
                                "Name Write Data ${
                                    pairedDevicesList.toTypedArray().contentToString()
                                } Edit ${pairedDeviceData.isEdit}"
                            )
                            DebugLog.e("Name Write Data ${pairedDevicesList.any { it.name == editedName }} Hey ${editedName}")
                            run breaking@{
                                if (pairedDevicesList.any { it.name == editedName }) {
                                    Toast.makeText(
                                        this@HomeTvActivity,
                                        "${resources.getText(R.string.txt_already_exits)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@breaking
                                }
                                DebugLog.e("Name Write Data ${pairedDeviceData.macAddress} Hey ${editedName}")
                                service.writeData(
                                    pairedDeviceData.macAddress,
                                    "Devicename=${editedName}".toByteArray(Charsets.UTF_8)
                                )
                            }
//                            run breaking@{
//                                pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
//                                    if (pairedDeviceData.macAddress == pairedDevice.macAddress) {
//                                        pairedDeviceData.name = pairedDevice.name
//                                        pairedDeviceData.name = pairedDevice.name
//                                        return@breaking
//                                    }
//                                }
//                            }
//                            SharedPreferencesManager.updateMacAddressList(
//                                this@HomeActivity, ArrayList(pairedDevicesList)
//                            )
//                            if (position == binding.myViewPager2.currentItem) binding.tvPageTitle.text =
//                                savedDevicesList[position].name
                        }
                        pairedDeviceAdapter.notifyItemChanged(position)
                    }
                }
            }
            dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.SEARCHING
            dialogScanDeviceListBinding.rvDevices.adapter = adapter
            dialogScanDeviceListBinding.rvPairedDevices.adapter = pairedDeviceAdapter
        }
        dialog.show()
        startScan(false)
    }

    private fun addDummyMacAddress() {
        macAddressList.add(Constants.DummyMacAddress)
        changeDummyMac.postValue(Constants.DummyMacAddress)
        val dashboardFragment = DashboardFragment.newInstance(
            Constants.DummyMacAddress
        )
        //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
        binding.toolbar1.imageViewAppBanner.visible()
        binding.toolbar1.tvPageTitle.gone()
        binding.toolbar1.tvPageTitle.text = getString(R.string.txt_home)
        binding.toolbar1.ivLeft.viewGone()
        getId(1, true)
//        viewPagerAdapter.addFragment(dashboardFragment, Constants.DummyMacAddress)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment1, dashboardFragment)
//            .commitNow() //Use commitNow() if you want to perform the transaction immediately:
            .commit() //Use commit() for a more standard approach, allowing the transaction to be committed at the next opportunity on the main thread
//        viewPagerAdapter.notifyItemInserted(macAddress.size)
        myFragmentMap[Constants.DummyMacAddress] =
            TabFragmentData(dashboardFragment, 1, "Home", Constants.DummyMacAddress)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::service.isInitialized) {
            unbindService(
                mConnection
            )
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            broadcastReceiver
        )
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(communicationReceiver)
    }

    override fun onResume() {
        super.onResume()

    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e("in", "sdk version 31 and above")
            Dexter.withContext(this@HomeTvActivity).withPermissions(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    // check if all permissions are granted
                    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    if (report.areAllPermissionsGranted()) {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeTvActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("Permission", "All Permissions granted")
                            ensureBLESupported()
                            if (!isBLEEnabled()) {
                                Log.e("in", "ble is not enabled")
                                showBLEDialog()
                            } else {
                                Log.e("in", "all permissions granted go for scan")
                                startService()
//                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeTvActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")

                            var IS_FINE_LOCATION_DENY_PERMANENT = false
                            var IS_COARSE_LOCATION_DENY_PERMANENT = false
                            var IS_BLUETOOTH_SCAN_DENY_PERMANENT = false
                            var IS_BLUETOOTH_CONNECT_DENY_PERMANENT = false
                            for (i in report.deniedPermissionResponses) {
                                if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION && i.isPermanentlyDenied) {
                                    IS_FINE_LOCATION_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION && i.isPermanentlyDenied) {
                                    IS_COARSE_LOCATION_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.BLUETOOTH_SCAN && i.isPermanentlyDenied) {
                                    IS_BLUETOOTH_SCAN_DENY_PERMANENT = true
                                }
                                if (i.permissionName == Manifest.permission.BLUETOOTH_CONNECT && i.isPermanentlyDenied) {
                                    IS_BLUETOOTH_CONNECT_DENY_PERMANENT = true
                                }
                            }
                            var ACCESS_COARSE_LOCATION_ALLOWED = false
                            var ACCESS_FINE_LOCATION_ALLOWED = false
                            var BLUETOOTH_NEARBY_ALLOWED = false
                            for (i in report.grantedPermissionResponses) {
                                if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION) {
                                    ACCESS_COARSE_LOCATION_ALLOWED = true
                                } else if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION) {
                                    ACCESS_FINE_LOCATION_ALLOWED = true
                                } else if (i.permissionName == Manifest.permission.BLUETOOTH_SCAN || i.permissionName == Manifest.permission.BLUETOOTH_CONNECT) {
                                    BLUETOOTH_NEARBY_ALLOWED = true
                                }
                            }

                            if (IS_COARSE_LOCATION_DENY_PERMANENT && IS_FINE_LOCATION_DENY_PERMANENT && (IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Nearby devices and Location Permission is Permanently denied, GO TO Settings"
                                )
                            } else if (IS_COARSE_LOCATION_DENY_PERMANENT && IS_FINE_LOCATION_DENY_PERMANENT) {
                                showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Location Permission is Permanently denied, GO TO Settings"
                                )
                            } else if (ACCESS_COARSE_LOCATION_ALLOWED && !IS_FINE_LOCATION_DENY_PERMANENT && !ACCESS_FINE_LOCATION_ALLOWED) {
                                Log.e(
                                    "Permission",
                                    "Precise Location Permission is not allowed, Again ask for permission"
                                )
                                checkPermission()
                            } else if (IS_FINE_LOCATION_DENY_PERMANENT && (IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_precise_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Location Permission allowed but Precise Location and nearby devices is Permanently denied, GO TO Settings"
                                )
                            } else if ((IS_BLUETOOTH_SCAN_DENY_PERMANENT || IS_BLUETOOTH_CONNECT_DENY_PERMANENT)) {
                                showSettingsDialog(getString(R.string.this_app_need_nearby_devices_permissions))
                                Log.e(
                                    "Permission",
                                    "Nearby devices is Permanently denied, GO TO Settings"
                                )
                            } else if (IS_FINE_LOCATION_DENY_PERMANENT) {
                                showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                Log.e(
                                    "Permission",
                                    "Precise Location is Permanently denied, GO TO Settings"
                                )
                            } else {

                                // check for permanent denial of any permission
                                if (report.deniedPermissionResponses.size != 0) {
                                    // show alert dialog navigating to Settings
                                    if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED && !BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices and Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_FINE_LOCATION_ALLOWED && !BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_precise_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices and Precise Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!BLUETOOTH_NEARBY_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_nearby_devices_permissions))
                                        Log.e(
                                            "Permission",
                                            "Nearby devices is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                        Log.e(
                                            "Permission",
                                            "Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else if (!ACCESS_FINE_LOCATION_ALLOWED) {
                                        showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                        Log.e(
                                            "Permission",
                                            " Precise Location Permission is Permanently denied, GO TO Settings"
                                        )
                                    } else {
                                        showSettingsDialog(getString(R.string.this_app_need_permissions))
                                        Log.e(
                                            "Permission",
                                            "All Permissions Permanently denied, GO TO Settings"
                                        )
                                    }
                                } else {
                                    Log.e("Permission", "No scan results")
//                                        binding.scanAgain.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionRequest: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?,
                ) {
                    token?.continuePermissionRequest();
                }


            }).withErrorListener {
                Log.e("in", "error listener $it")
            }.onSameThread().check()
        } else {
            Log.e("in", "below sdk version 31")
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    // check if all permissions are granted
                    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    if (report.areAllPermissionsGranted()) {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeTvActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")
                            Log.e("Permission", "All Permissions granted")
//                                scanDeviceList.clear()
                            ensureBLESupported()
                            if (!isBLEEnabled()) {
                                showBLEDialog()
                            } else {
                                Log.e("in", "all permissions granted go for scan")
                                startService()
//                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@HomeTvActivity
                            )
                        ) {
                            Log.e("permission", "Gps not enabled")
                            checkLocationSetting()
                        } else {
                            Log.e("permission", "Gps already enabled")
                            if (report.deniedPermissionResponses.size != 0) {
                                // show alert dialog navigating to Settings
                                var ACCESS_COARSE_LOCATION_ALLOWED = false
                                var ACCESS_FINE_LOCATION_ALLOWED = false

                                for (i in report.grantedPermissionResponses) {
                                    if (i.permissionName == Manifest.permission.ACCESS_COARSE_LOCATION) {
                                        ACCESS_COARSE_LOCATION_ALLOWED = true
                                    } else if (i.permissionName == Manifest.permission.ACCESS_FINE_LOCATION) {
                                        ACCESS_FINE_LOCATION_ALLOWED = true
                                    }
                                }

                                if (!ACCESS_COARSE_LOCATION_ALLOWED && !ACCESS_FINE_LOCATION_ALLOWED) {
                                    showSettingsDialog(getString(R.string.this_app_need_location_permissions))
                                    Log.e(
                                        "Permission",
                                        "Location Permission is Permanently denied, GO TO Settings"
                                    )
                                } else if (!ACCESS_FINE_LOCATION_ALLOWED) {
                                    showSettingsDialog(getString(R.string.this_app_need_precise_location_permissions))
                                    Log.e(
                                        "Permission",
                                        "Precise Location Permission is Permanently denied, GO TO Settings"
                                    )
                                } else {
                                    showSettingsDialog(getString(R.string.this_app_need_permissions))
                                    Log.e(
                                        "Permission",
                                        "All Permissions Permanently denied, GO TO Settings"
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionRequest: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?,
                ) {
                    token?.continuePermissionRequest()
                }
            }).withErrorListener {
                Log.e("in", "error listener ${it.name}")
            }.onSameThread().check()
        }
    }


    fun showSettingsDialog(message: String) {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.need_permissions))
            .setMessage(message).setCancelable(false).setPositiveButton(
                getString(R.string.go_to_settings)
            ) { dialogInterface, i ->
                openSettings()
            }.setNegativeButton(
                getString(R.string.cancel)
            ) { dialogInterface, i ->
//                binding.scanAgain.visibility = View.VISIBLE

            }.show()
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        getResult.launch(intent)
    }

    protected fun showBLEDialog() {
        if (!isBLEEnabled()) {
            Log.e("in fun", "ble enabled")
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            getResultForBLE.launch(enableIntent)
        }
    }

    private fun init() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DebugLog.e("onReceive Action Scan${intent?.action}")
                if (intent?.action == Constants.EVENT_STOP_SCAN) {
                    DebugLog.e("Stop Scan TestScan Tab")
//                    binding.ivAddDevice.viewShow() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
                    if (this@HomeTvActivity::dialogScanDeviceListBinding.isInitialized && dialog.isShowing) {
                        dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.OFF
                    }
//                    binding.bleStateView1.state = BluetoothState.State.SEARCH //PrinceEWW - here(onStopScan), need to show state OFF instead of Search
                    binding.bleStateView1.state = BluetoothState.State.OFF //PrinceEWW
//                    startScan(disconnect = false) //PrinceEWW - This line is add and commented by PrinceEWW

                    //-------On EVENT_STOP_SCAN, If any device is not currently paired but it is in pairingList of preference, we need to remove that device from pairingList of preference-------//
                    val pairedDevicesListFromPref =
                        SharedPreferencesManager.getMacAddressList(this@HomeTvActivity)
                    if (pairedDevicesList.size < pairedDevicesListFromPref.size) {
                        val toBeRemoved = mutableListOf<Int>() //Add index for remove from list, because during for loop we can not remove any item from its list

                        pairedDevicesListFromPref.forEachIndexed { index, pairedDeviceData ->
                            if (!pairedDevicesList.contains(pairedDeviceData)) {
                                toBeRemoved.add(index)
                            }

                            //-------When we reach last item, start operation for remove from list-------//
                            if (pairedDevicesListFromPref.last() == pairedDeviceData) {
                                toBeRemoved.sortedDescending().forEach { pairedDeviceListIndex ->
                                    val pairedDeviceData1 =
                                        pairedDevicesListFromPref[pairedDeviceListIndex]
                                    pairedDevicesListFromPref.removeAt(pairedDeviceListIndex)
                                    //-------Update list in preference, after remove last item-------//
                                    if (toBeRemoved.first() == pairedDeviceListIndex) { //Here, first item will be removed last, because we do sortedDescending before forEach
                                        //-------In case user connect single device and user turn off device and launch application, and connect another device before complete bluetooth searching, view of first device with id=1 is visible on screen-------
                                        //-------To prevent above issue, we need to hide 1st view if we found any id in pairingDeviceList in preference (pairedDevicesListFromPref), and after that update preference-------//
                                        if (pairedDevicesListFromPref.isNotEmpty() && !pairedDevicesListFromPref.any { it.id == 1 }){
                                            getId(1, false)
                                            val deviceWithId1 = SharedPreferencesManager.getMacAddressList(this@HomeTvActivity).find { it.id == 1 }
                                            showSnackBarLong(
                                                String.format(
                                                    resources.getString(R.string.dynamic_message_previously_paired_device_not_found),
                                                    deviceWithId1?.name ?: Constants.DEFAULT_EMPTY_STRING
                                                )
                                            )
                                        }
                                        SharedPreferencesManager.updateMacAddressList(
                                            this@HomeTvActivity,
                                            ArrayList(pairedDevicesListFromPref)
                                        ) //Update list of pairingDevice in preference at last
                                    }
                                }
                            }
                        }
                    }

                }

                if (intent?.action == Constants.CHANGE_DEVICE_NAME) {
                    val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                    val macAddress = intent?.getStringExtra(Constants.MAC_ADDRESS) ?: ""
                    DebugLog.e("Message $message $macAddress")
                    run breaking@{
                        pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                            if (pairedDeviceData.macAddress == macAddress) {
                                pairedDeviceData.name =
                                    message.replace("${Constants.DEVICE_NAME_CHECK}", "", true)
                                return@breaking
                            }
                        }
                    }
                    SharedPreferencesManager.updateMacAddressList(
                        this@HomeTvActivity, ArrayList(pairedDevicesList)
                    )
                    var updateIndex = -1
                    run breaking@{
                        pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                            if (macAddress == pairedDeviceData.macAddress) {
                                pairedDeviceData.name =
                                    message.replace("${Constants.DEVICE_NAME_CHECK}", "", true)
                                updateIndex = index
                            }
                        }
                        if (updateIndex == -1) return@breaking
                        getIndexFromMac(macAddress)?.let { int ->
                            DebugLog.e("Connection Success Index $int")
                            val keys = myFragmentMap.filterValues { it.id == int }.keys
                            myFragmentMap[keys.first()]?.name = pairedDevicesList[updateIndex].name
                            setToolBarTitle(true, int)
                        }
                        pairedDevicesList[updateIndex].name
                        pairedDeviceAdapter.notifyItemChanged(updateIndex)
                    }
                    return
                }
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        val message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Log.e("TAG", "onReceive: $message")
                        Toast.makeText(this@HomeTvActivity, message, Toast.LENGTH_LONG).show()
                    }

                    //-------PrinceEWW>>> - Below condition is commented by PrinceEWW, Because below condition is different then "HomeActivity"(For Mobile), and it is working fine in mobile-------//
                    /*Constants.EVENT_BLE_CONNECTION_ABORT -> {
//                        binding.bleStateView1.state = BluetoothState.State.OFF

                        val disconnectedMac = intent?.getStringExtra(Constants.MAC_ADDRESS)
                        DebugLog.e("EVENT_BLE_CONNECTION_ABORT$disconnectedMac TestScan Tab")

                        disconnectedMac?.let {
                            service.stopTimer(it)
                            DebugLog.e("disconnectedMac ${hashmap.containsKey(it)} TestScan Tab")
//                            if (hashmap.containsKey(it))
//                            if (service.isScanning)
                            startScan(true)
                            val index = getIndexFromMac(disconnectedMac)
                            index?.let { it ->
                                setToolBarTitle(false, it)
                            }
                        }
                    }*/

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        LoadingUtils.hideDialog()
                        binding.bleStateView1.state = BluetoothState.State.OFF
                        DebugLog.e("EVENT_BLE_CONNECTION_ABORT ${intent?.getStringExtra(Constants.MAC_ADDRESS)}")
                        DebugLog.e(
                            "EVENT_BLE_CONNECTION_ABORT ${
                                hashmap.containsKey(
                                    intent?.getStringExtra(
                                        Constants.MAC_ADDRESS
                                    )
                                )
                            }"
                        )
                        intent?.getStringExtra(Constants.MAC_ADDRESS)?.let {
                            DebugLog.e("hashmap.containsKey(it)${hashmap.containsKey(it)}")
                            service.stopTimer(it)
//                            startScan(true)
                            val index = getIndexFromMac(it)
                            index?.let { it ->
                                setToolBarTitle(false, it)
                            }

                            //-------Below code is from home activity-------//
                            run breaking@{
                                pairedDevicesList.mapIndexed { index, pairedDeviceDataList ->
                                    pairedDeviceDataList.withNotNull {pairedDeviceData ->
                                        if (pairedDeviceData.macAddress == it) {
                                            pairedDeviceData.isConnected = false
                                            //------After disconnect device, update isConnected flag to false and notify adapter(only if it isInitialized)-------//
                                            if (::pairedDeviceAdapter.isInitialized) {
                                                pairedDeviceAdapter.notifyItemChanged(index)
                                            }
                                            showSnackBarShort(
                                                String.format(
                                                    resources.getString(R.string.dynamic_message_disconnected_device_no),
                                                    pairedDeviceData.name
                                                )
                                            )
                                            return@breaking
                                        }
                                    }
                                }
                            }

                            //-------After disconnect device need to pressBack if user is in cooking screen-------//
                            val fragment = getFragmentFromMac(it)
                            fragment.withNotNull {fragmentNotNull ->
                                val navController = (fragmentNotNull as DashboardFragment).navController
                                navController?.let {navContro ->
                                    navContro.currentDestination.let { destination ->
                                        if (destination?.id == R.id.cookingFragment || destination?.id == R.id.recipeDetailFragment) {
                                            navController.popBackStack(R.id.homeFragment, false) //Navigate back to homeFragment and clear between stacks
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                setToolbar(it, true)
                                            }, 100)
                                        }
                                    }
                                }
                            }
                            //TODO Remove Scanning Condition
                            if (hashmap.containsKey(it)) startScan(false)
                        }
                    }

                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        //-------PrinceEWW, Commented by PrinceEWW, because in "HomeActivity" it is working fine, so do same code as "HomeActivity"-------//
                        /*CoroutineScope(Dispatchers.IO).launch {
                            intent?.getStringExtra(Constants.MAC_ADDRESS)?.let { it ->
//                                connectionSuccess(it)
                                  service.writeData(mac, Constants.CHECKLOGSTATUS.toByteArray(Charsets.UTF_8))
                                getIndexFromMac(it)?.let { int ->
                                    DebugLog.e("Connection Success Index $int")

                                    setToolBarTitle(true, int)
                                }
                            }
                        }*/

                        LoadingUtils.hideDialog()
                        intent?.getStringExtra(Constants.MAC_ADDRESS)?.let { mac ->
                            //-------If device already previously paired, we have to set .connected = true-------//
                            var indexUpdate = -1
                            run breaking@{
                                pairedDevicesList.withNotNull { pairedDeviceList ->
                                    pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                        pairedDeviceData.withNotNull { pairedData ->
                                            if (pairedData.macAddress == mac) {
                                                indexUpdate = index
                                                pairedDevicesList[index].isConnected = true
                                                if (::pairedDeviceAdapter.isInitialized) {
                                                    pairedDeviceAdapter.notifyItemChanged(index)
                                                }
                                                return@breaking
                                            }
                                        }
                                    }
                                }

                                /*if (indexUpdate != -1) {
                                    pairedDevicesList[indexUpdate].isConnected = true //If we found device in pairedDeviceList, Need to update .isConnected = true to show device is connected in pairedDevice View
                                    //------After Connect device, update isConnected flag to true and notify adapter(only if it isInitialized)-------//
                                    if (::pairedDeviceAdapter.isInitialized){
                                        pairedDeviceAdapter.notifyItemChanged(indexUpdate)
                                    }
                                }*/
                            }

                            intent.getStringExtra(Constants.DEVICE_NAME)?.let {
                                connectionSuccess(
                                    mac,
                                    it
                                )
                                service.writeData(mac, Constants.CHECKLOGSTATUS.toByteArray(Charsets.UTF_8))
                            }
                            getIndexFromMac(mac)?.let { int ->
                                DebugLog.e("Connection Success Index $int")
//                                checkStatusAfterConnectionSuccess(connectedMac = mac)
                                setToolBarTitle(true, int)
                            }
                        }
                    }

                    Constants.EVENT_BLE_CONNECTION_INIT -> {

                    }

                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
                        DebugLog.e("EVENT_BLE_CONNECTION_FOUND_DEVICE")
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val device =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        intent!!.getParcelableExtra(
                                            Constants.DEVICE, BluetoothDevice::class.java
                                        )
                                    } else {
                                        intent!!.getParcelableExtra(
                                            Constants.DEVICE
                                        )
                                    }
                                if (device != null && !hashmap.containsKey(device.address)) {
                                    hashmap[device.address] = device
                                    val pairedDeviceList =
                                        SharedPreferencesManager.getMacAddressList(
                                            applicationContext
                                        )
                                    Log.e("PrinceEWW>>>", "onReceive - EVENT_BLE_CONNECTION_FOUND_DEVICE - pairedDeviceList:$pairedDeviceList")
                                    var pairedDeviceItem: PairedDeviceData? = null
                                    run breaking@{
                                        pairedDeviceList.forEachIndexed { index, pairedDeviceData ->
                                            if (pairedDeviceData.macAddress == device.address) {
                                                pairedDeviceItem = pairedDeviceData
                                                return@breaking
                                            }
                                        }
                                    }
                                    if (pairedDeviceItem != null) {
//                                    val stop = async {
//                                        service.stopScan()
//                                    }
//                                    stop.await()
//                                    delay(500)

//                                        DebugLog.e("Device Found hashmap Not contain TestScan Tab pairedDeviceItem ${pairedDeviceItem?.macAddress} Device ${device.address}")
//                                        DebugLog.e("Paired TestScan Tab ${!pairedDevicesList.any { it.macAddress == pairedDeviceItem!!.macAddress }}")
                                        service.connect(device)
//                                        connectionSuccess(device.address,device.name) //PrinceEWW - Commented by PrinceEWW, connection success should be when we got connection success from bluetooth command (Same as HomeActivity (for Mobile))
                                        if (!pairedDevicesList.any { it.macAddress == pairedDeviceItem!!.macAddress }) {
                                            pairedDevicesList.add(pairedDeviceItem!!)
                                            runOnUiThread {
                                                if (this@HomeTvActivity::dialog.isInitialized && dialog.isShowing)
                                                    pairedDeviceAdapter.notifyItemInserted(
                                                        pairedDevicesList.size
                                                    )
                                            }
                                            val dashboardFragment = DashboardFragment.newInstance(
                                                device.address
                                            )
//                myFragments.add(dashboardFragment)
                                            myFragmentMap[device.address] = TabFragmentData(
                                                dashboardFragment,
                                                pairedDeviceItem!!.id,
                                                pairedDeviceItem!!.name,
                                                device.address
                                            )
                                            DebugLog.e("TabFragmentData Add 2 ${device.address} Id ${myFragmentMap[device.address]?.id}")
                                            DebugLog.e("TabFragmentData Add 2 Check 1 ${myFragmentMap.isNotEmpty()} Check 2 ${pairedDeviceItem!!.id.toInt() != 1} Check 3${!pairedDevicesList.any { it.id.toInt() == 1 }}")

                                            if (myFragmentMap.isNotEmpty() && pairedDeviceItem!!.id != 1 && !pairedDevicesList.any { it.id.toInt() == 1 }) {
                                                runOnUiThread { getId(1, false) }
                                            }
                                            delay(200)
//                                            DebugLog.e("TabFragmentData Add 2 ${device.address} Id ${myFragmentMap[device.address]?.id}")
//                                            DebugLog.e("TabFragmentData Add 2 Test${pairedDeviceItem!!.id}")
                                            runOnUiThread {
                                                try {
                                                    val id =
                                                        getId(pairedDeviceItem!!.id, true)
                                                    if (id != 0)
                                                        supportFragmentManager.beginTransaction()
                                                            .replace(id, dashboardFragment)
                                                            .commitNow()
                                                } catch (e: Exception) {
                                                    DebugLog.e("pairedDevicesList ${e.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        runOnUiThread {
                                            if (this@HomeTvActivity::dialog.isInitialized && dialog.isShowing) {
                                                DebugLog.e(" availableDevicesList TestScan Tab ${device.address}")
                                                DebugLog.e(" availableDevicesList TestScan Tab ${!availableDevicesList.any { it.address == device.address }}")
                                                if (!availableDevicesList.any { it.address == device.address }) {
                                                    availableDevicesList.add(device)
                                                    adapter.notifyItemInserted(availableDevicesList.size)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (device != null && hashmap.containsKey(device.address)) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            hashmap[device.address] = device
                                            var updateIndex = -1
                                            run breaking@{
                                                availableDevicesList.mapIndexed { index, bluetoothDevice ->
                                                    if (bluetoothDevice.address == device.address) {
                                                        updateIndex = index
                                                        return@breaking // Exiting the run block early
                                                    }
                                                }
                                            }
                                            if (updateIndex != -1)
                                                availableDevicesList[updateIndex] = device
                                        }
                                    }
                                    //Update
                                }
                            } catch (e: Exception) {
                                DebugLog.e("EVENT_BLE_CONNECTION_FOUND_DEVICE Exception ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    parseData(intent)
                } /*else { //Comment by PrinceEWW, because I copy this code from recipe fragment for copy "device setup" functionality
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
                }*/
            }
        }

        //used when no device connected
        addDummyMacAddress()

        //fab icon's animations
        fabClose = AnimationUtils.loadAnimation(this, R.anim.fab_close)
        fabOpen = AnimationUtils.loadAnimation(this, R.anim.fab_open)
        fabClock = AnimationUtils.loadAnimation(this, R.anim.fab_rotate_clock)
        fabAntiClock = AnimationUtils.loadAnimation(this, R.anim.fab_rotate_anticlock)
        CoroutineScope(Dispatchers.IO).launch {
            allRecipeList.clear()
            allRecipeList.addAll(
                OnToCookApplication.dbInstance.recipeDao().getAllRecipe().sortedBy { it.name[0] })
        }

    }

    private fun connectionSuccess(connectedMac: String, name: String) {
        /*Log.e("PrinceEWW>>>", "function connectionSuccess - Method call")
        Log.e("PrinceEWW>>>", "function connectionSuccess - connectedMac: $connectedMac")
        Log.e("PrinceEWW>>>", "function connectionSuccess - macAddressList: $macAddressList")
        Log.e("PrinceEWW>>>", "function connectionSuccess - pairedDevicesList: $pairedDevicesList")*/
        //-------Need to show toast on new device connect-------//
        showSnackBarShort(
            String.format(
                resources.getString(R.string.dynamic_message_device_connected),
                name
            )
        )
        try {
            if (!macAddressList.contains(connectedMac)) {
                if (macAddressList[0] == Constants.DummyMacAddress) {
                    DebugLog.e("EVENT_BLE_CONNECTION_SUCCESS $connectedMac")
                    changeDummyMac.postValue(connectedMac)
                    if (myFragmentMap.containsKey(Constants.DummyMacAddress)) {
                        val temp =
                            myFragmentMap.remove(Constants.DummyMacAddress) as TabFragmentData
                        myFragmentMap[connectedMac] = temp
                        myFragmentMap[connectedMac]?.mac = connectedMac
//                        myFragmentMap[connectedMac]?.name = pairedDevicesList.find { it.macAddress == connectedMac }?.name ?: Constants.DEFAULT_EMPTY_STRING //Added by PrinceEWW, Because facing issue on toolbar name when user start app and already device is connected, it was set "Home"(Which is name of dummy mac address), it should be name of connected device
                        myFragmentMap[connectedMac]?.name = name //Added by PrinceEWW, Because facing issue on toolbar name when user start app and already device is connected, it was set "Home"(Which is name of dummy mac address), it should be name of connected device
                        //-------PrinceEWW, Need to update id also, to prevent issue of name in toolbar, because we show name from ID in setToolBarTitle() function-------//
                        pairedDevicesList.find { it.macAddress == connectedMac }.withNotNull {
                            myFragmentMap[connectedMac]?.id = it.id
                        }
                        //-------Need to replace dashboardFragment with Dummy MacAddress-------//
                        val dashboardFragment = DashboardFragment.newInstance(
                            connectedMac
                        )
                        supportFragmentManager.beginTransaction()
                            .replace(getId(myFragmentMap[connectedMac]?.id ?: 1, true), dashboardFragment)
                            .commitNow()

                        myFragmentMap[connectedMac]?.fragment = dashboardFragment
                    }
                    DebugLog.e("TabFragmentData Change $connectedMac Id ${myFragmentMap[connectedMac]?.id}")
                    macAddressList[0] = connectedMac
                    if (pairedDevicesList.isNotEmpty() && pairedDevicesList[0].macAddress != connectedMac) {
                        var indexUpdate = -1
                        run breaking@{
                            pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                if (pairedDeviceData.macAddress == connectedMac) {
                                    indexUpdate = index
                                    return@breaking
                                }
                            }
                        }
                        if (indexUpdate != -1) {
                            val item = pairedDevicesList.removeAt(indexUpdate)
                            item.isConnected = true
                            pairedDevicesList.add(0, item)
                            myFragmentMap[connectedMac]?.name = pairedDevicesList[0].name
                            Log.e("PrinceEWW>>>", "function connectionSuccess - pairedDevicesList: $pairedDevicesList")
                        }
                    }
                } else {
                    macAddressList.add(connectedMac)
                    if (pairedDevicesList.isNotEmpty() && pairedDevicesList.size > macAddressList.size - 1 && pairedDevicesList[macAddressList.size - 1].macAddress != connectedMac) {
                        var indexUpdate = -1
                        run breaking@{
                            pairedDevicesList.forEachIndexed { index, pairedDeviceData ->
                                if (pairedDeviceData.macAddress == connectedMac) {
                                    indexUpdate = index
                                    return@breaking
                                }
                            }
                        }
                        val item = pairedDevicesList.removeAt(indexUpdate)
                        item.isConnected = true //If we found device in pairedDeviceList, Need to update .isConnected = true to show device is connected in pairedDevice View
                        pairedDevicesList.add(macAddressList.size - 1, item)
                        DebugLog.e("Test 1 Addd")
                        val dashboardFragment = DashboardFragment.newInstance(
                            connectedMac
                        )
//                myFragments.add(dashboardFragment)
                        myFragmentMap[connectedMac] = TabFragmentData(
                            dashboardFragment,
                            item.id,
                            item.name,
                            connectedMac
                        )
                        DebugLog.e("TabFragmentData Add $connectedMac Id ${myFragmentMap[connectedMac]?.id}")
                        runOnUiThread {
                            try {
                                val id = getId(item.id, true)
                                if (id != 0)
                                    supportFragmentManager.beginTransaction()
                                        .replace(id, dashboardFragment)
                                        .commitNow()
                            } catch (e: Exception) {
                                DebugLog.e("pairedDevicesList ${e.message}")
                            }
                        }
                    }
//                setToolBarTitle(true)
                }
                if (this@HomeTvActivity::dialog.isInitialized && dialog.isShowing) {
                    var indexUpdate = -1
                    run breaking@{
                        availableDevicesList.forEachIndexed { index, bluetoothDevice ->
                            if (bluetoothDevice.address == connectedMac) {
                                indexUpdate = index
                                return@breaking
                            }
                        }
                    }
                    if (indexUpdate == -1) return
                    availableDevicesList.removeAt(indexUpdate)
                    runOnUiThread {
                        adapter.notifyItemRemoved(indexUpdate)
                    }
                    val device = SharedPreferencesManager.insertDevice(
                        this@HomeTvActivity,
                        connectedMac, name
                    )
                    if (device != null) {
                        pairedDevicesList.add(device)
                        runOnUiThread {
                            pairedDeviceAdapter.notifyItemInserted(
                                pairedDevicesList.size
                            )
                        }
                        val dashboardFragment = DashboardFragment.newInstance(
                            connectedMac
                        )
                        myFragmentMap[connectedMac] = TabFragmentData(
                            dashboardFragment,
                            device.id,
                            device.name,
                            connectedMac
                        )
                        DebugLog.e("TabFragmentData Add 3 $connectedMac Id ${myFragmentMap[connectedMac]?.id}")

                        runOnUiThread {
                            try {
                                val id = getId(device.id, true)
                                if (id != 0)
                                    supportFragmentManager.beginTransaction()
                                        .replace(id, dashboardFragment)
                                        .commitNow()
                            } catch (e: Exception) {
                                DebugLog.e("pairedDevicesList ${e.message}")
                            }
                        }

                    }
                }
            } else {
                binding.bleStateView1.state = BluetoothState.State.CONNECTED
            }
        } catch (e: Exception) {
            DebugLog.e("connectionSuccess Exception ${e.message}")
        }
    }

    private fun setToolBarTitle(isConnected: Boolean, macIndex: Int = -1) {
        runOnUiThread {
            /*Log.e("PrinceEWW>>>", "function setToolBarTitle - isConnected: $isConnected")
            Log.e("PrinceEWW>>>", "function setToolBarTitle - macIndex: $macIndex")
            Log.e("PrinceEWW>>>", "function setToolBarTitle - myFragmentMap: ${myFragmentMap.toList()}")
            Log.e("PrinceEWW>>>", "function setToolBarTitle - pairedDevicesList: ${pairedDevicesList.toList()}")
            Log.e("PrinceEWW>>>", "function setToolBarTitle - pairedDevicesList Size: ${pairedDevicesList.size}, macAddressList Size: ${macAddressList.size}")*/
            when (if (macIndex == -1) macAddressList.size else macIndex) {
                1 -> {
                    try {
                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
//                        val keys = myFragmentMap.filterValues { it.id == macIndex && pairedDevicesList.any { paired -> paired.macAddress == it.mac }}.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar1.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar1.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus1.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar1.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus1.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.e("1st Display Error ${e.printStackTrace()}")
                    }
                    if (isConnected) binding.toolbar1.bleStateView1.state =
                        BluetoothState.State.CONNECTED
                    else {
                        binding.toolbar1.bleStateView1.state = BluetoothState.State.OFF
                    }
                }

                0 -> {
//                    binding.toolbar1.tvPageTitle.text = pairedDevicesList[macIndex-1].name
                    if (isConnected) binding.toolbar1.bleStateView1.state =
                        BluetoothState.State.CONNECTED
                    else binding.toolbar1.bleStateView1.state = BluetoothState.State.OFF
                }

                2 -> {
                    try {
                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
//                        val keys = myFragmentMap.filterValues { it.id == macIndex && pairedDevicesList.any { paired -> paired.macAddress == it.mac }}.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar2.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar2.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus2.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar2.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus2.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.e("TabFragmentData setToolBarTitle Exception ${e.message} ")
                    }
                    if (isConnected) binding.toolbar2.bleStateView1.state =
                        BluetoothState.State.CONNECTED
                    else binding.toolbar2.bleStateView1.state = BluetoothState.State.OFF
                }

                3 -> {
                    try {
                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar3.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar3.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus3.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar3.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus3.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                        if (isConnected) binding.toolbar3.bleStateView1.state =
                            BluetoothState.State.CONNECTED
                        else binding.toolbar3.bleStateView1.state = BluetoothState.State.OFF
                    } catch (e: Exception) {
                        DebugLog.e("${e.printStackTrace()}")
                    }
                }

                4 -> {
                    try {
                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar4.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar4.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus4.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar4.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus4.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                        if (isConnected) binding.toolbar4.bleStateView1.state =
                            BluetoothState.State.CONNECTED
                        else binding.toolbar4.bleStateView1.state = BluetoothState.State.OFF
                    } catch (e: Exception) {
                        DebugLog.e("${e.printStackTrace()}")
                    }
                }

                5 -> {
                    try {

                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar5.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar5.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus5.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar5.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus5.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                        if (isConnected) binding.toolbar5.bleStateView1.state =
                            BluetoothState.State.CONNECTED
                        else binding.toolbar5.bleStateView1.state = BluetoothState.State.OFF
                    } catch (e: Exception) {
                        DebugLog.e("${e.printStackTrace()}")
                    }
                }

                6 -> {
                    //UnComment for 7 device connectivity
                    /*try {

                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar6.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar6.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus5.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar6.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus5.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                        if (isConnected) binding.toolbar6.bleStateView1.state =
                            BluetoothState.State.CONNECTED
                        else binding.toolbar6.bleStateView1.state = BluetoothState.State.OFF
                    } catch (e: Exception) {
                        DebugLog.e("${e.printStackTrace()}")
                    }*/
                }

                7 -> {
                    //UnComment for 7 device connectivity
                    /*try {

                        val keys = myFragmentMap.filterValues { it.id == macIndex }.keys
                        if (keys.isNotEmpty()) {
                            //-------We have to show name of connected device, and if device is not connected shoe appBanner and hide textView-------//
                            binding.toolbar7.imageViewAppBanner.goneIfOrVisible(isConnected)
                            binding.toolbar7.tvPageTitle.visibleIfOrGone(isConnected)
                            if (isConnected) {
//                                binding.tvPageStatus5.viewGone() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                                binding.toolbar7.tvPageTitle.text =
                                    myFragmentMap[keys.first()]?.name
                            } else {
//                                binding.tvPageStatus5.viewShow() //Commented by PrinceEWW, because According client's requirement, "disconnected" text will be hide permanently, So no need tom manage visibility
                            }
                        }
                        if (isConnected) binding.toolbar7.bleStateView1.state =
                            BluetoothState.State.CONNECTED
                        else binding.toolbar7.bleStateView1.state = BluetoothState.State.OFF
                    } catch (e: Exception) {
                        DebugLog.e("${e.printStackTrace()}")
                    }*/
                }
            }
        }
    }

    private fun getTitleId(size: Int): Any {
        return when (size) {
            2 -> {
                DebugLog.e("fragment2")
                binding.clFragment2.viewShow()
                R.id.fragment2
            }

            3 -> {
                DebugLog.e("fragment1")
                binding.clFragment3.viewShow()
                R.id.fragment3
            }

            4 -> {
                DebugLog.e("fragment3")
                binding.clFragment4.viewShow()
                R.id.fragment4
            }

            5 -> {
                DebugLog.e("fragment4")
                binding.clFragment5.viewShow()
                R.id.fragment5
            }

            6 -> {
                DebugLog.e("fragment5")
                binding.clFragment6.viewShow()
                R.id.fragment6
            }

            7 -> {
                DebugLog.e("fragment6")
                binding.clFragment7.viewShow()
                R.id.fragment7
            }

            else -> {
                DebugLog.e("fragment  00")
                0
            }
        }
    }

    private fun getId(size: Int, visible: Boolean): Int {
        DebugLog.e("fragmentSize_6_7 Size $size Add $visible")
        DebugLog.e("TabFragmentData Size $size ")

        return when (size) {
            2 -> {
                DebugLog.e("fragment2")
                if (visible)
                    binding.clFragment2.viewShow()
                else
                    binding.clFragment2.viewGone()
                R.id.fragment2
            }

            1 -> {
                DebugLog.e("fragment1")
                if (visible)
                    binding.clFragment1.viewShow()
                else
                    binding.clFragment1.viewGone()
                R.id.fragment1
            }

            3 -> {
                DebugLog.e("fragment1")
                if (visible)
                    binding.clFragment3.viewShow()
                else
                    binding.clFragment3.viewGone()
                R.id.fragment3
            }

            4 -> {
                DebugLog.e("fragment3")
                if (visible)
                    binding.clFragment4.viewShow()
                else
                    binding.clFragment4.viewGone()
                R.id.fragment4
            }

            5 -> {
                DebugLog.e("fragment4")
                if (visible)
                    binding.clFragment5.viewShow()
                else
                    binding.clFragment5.viewGone()

                R.id.fragment5
            }

            6 -> {
                DebugLog.e("fragment5")
                if (visible)
                    binding.clFragment6.viewShow()
                else
                    binding.clFragment6.viewGone()
                R.id.fragment6
            }

            7 -> {
                DebugLog.e("fragment6")
                if (visible)
                    binding.clFragment7.viewShow()
                else
                    binding.clFragment7.viewGone()
                R.id.fragment7
            }

            else -> {
                DebugLog.e("fragment  00")
                0
            }
        }
    }

    private fun startService() {
        if (!this::service.isInitialized) {
            bindService(
                Intent(this, BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
            )
        } else {
            if (openDialog) openDialog()
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            CoroutineScope(Dispatchers.IO).launch {
                val bindService = async {
                    service = (iBinder as BleService.LocalBinder).getService()
                }
                bindService.await()
                runOnUiThread {
                    if (openDialog)
                        openDialog()
                    else {
                        if (SharedPreferencesManager.getMacAddressList(applicationContext)
                                .isNotEmpty()
                        )
                            startScan(false)
                    }

                }
            }

        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    /*private fun startScan() {
        DebugLog.e("Start Scan")
        service.stopScan()
        binding.bleStateView1.state = BluetoothState.State.SEARCHING
        hashmap.clear()
//        binding.ivAddDevice.viewGone() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
        Thread {
            service.startScan()
        }.start()
        if (this::dialog.isInitialized && dialog.isShowing) {
            availableDevicesList.clear()
            adapter.notifyDataSetChanged()
            dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.SEARCHING
        }
    }*/

    private fun startScan(disconnect: Boolean) {
        DebugLog.e("startScan TestScan Tab")
        CoroutineScope(Dispatchers.IO).launch {
            val stop = async {
                service.stopScan()
            }
            stop.await()
            hashmap.clear()
            runOnUiThread {
//                binding.ivAddDevice.viewGone() //Commented by PrinceEWW, because according client requirement, plus icon should be always visible, so comment all the condition of visible and hide this view
                binding.bleStateView1.state = BluetoothState.State.SEARCHING
                if (this@HomeTvActivity::dialog.isInitialized && dialog.isShowing && !disconnect) {
                    availableDevicesList.clear()
                    adapter.notifyDataSetChanged()
                    dialogScanDeviceListBinding.bleStateView.state = BluetoothState.State.SEARCHING
                }
            }
            delay(800)
            service.startScan()
        }

    }


    private lateinit var locationRequest: LocationRequest
    private fun checkLocationSetting() {
        locationRequest = LocationRequest.create()
        locationRequest.apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 2000
        }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> =
            LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        result.addOnCompleteListener {
            try {
                val response: LocationSettingsResponse = it.getResult(ApiException::class.java)
                Log.e("GPS", "is on")
            } catch (e: ApiException) {
                Log.e("in", "exe")
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        val resolvableApiException = e as ResolvableApiException
                        Log.e("permission", "checkSetting: RESOLUTION_REQUIRED")
                        val intentSenderRequest =
                            IntentSenderRequest.Builder(resolvableApiException.resolution).build()
                        resolutionForResult.launch(intentSenderRequest)
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        // USER DEVICE DOES NOT HAVE LOCATION OPTION
                        Log.e("GPS", "Required to use this app")
                    }
                }
            }
        }
    }

    private val resolutionForResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            Log.e("GPS", "enable")
            checkPermission()
        } else {
            Log.e("GPS", "GPS is Required to use this app")
            showGPSSettingsDialog(getString(R.string.this_app_need_gps_permissions))
        }
    }


    private fun showGPSSettingsDialog(message: String) {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.need_permissions))
            .setMessage(message).setCancelable(false).setPositiveButton(
                getString(R.string.go_to_settings)
            ) { dialogInterface, i ->
                getGPSResult.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.setNegativeButton(
                getString(R.string.cancel)
            ) { dialogInterface, i ->
//                binding.scanAgain.visibility = View.VISIBLE
            }.show()
    }

    private val getGPSResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(this)) {
            checkPermission()
        } else {
            showGPSSettingsDialog(getString(R.string.this_app_need_gps_permissions))
        }
    }


    private fun ensureBLESupported() {
        Log.e("in fun", "check ble support")
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("ble", "not supported by device")
            Toast.makeText(this, R.string.no_ble, Toast.LENGTH_LONG).show()
            this.finish()
        }
    }

    protected fun isBLEEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    private fun hasGPSDevice(context: Context): Boolean {
        val mgr =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager ?: return false
        val providers = mgr.allProviders ?: return false
        return providers.contains(LocationManager.GPS_PROVIDER)
    }

    fun setToolbar(macAddress: String, isVisible: Boolean) {
        run breaking@{
            DebugLog.e("setToolbar $macAddress")
            val fragment = myFragmentMap[macAddress]?.fragment ?: return@breaking
            val navController = (fragment as DashboardFragment).navController ?: return@breaking
            CoroutineScope(Dispatchers.IO).launch {
                val index = getIndexFromMac(macAddress)
                index?.let { it ->
                    DebugLog.e("setToolbar Index $it MAc $macAddress")
                    navController.currentDestination?.let { dest ->
                        setToolBarStop(dest.id, it)
                    }
                }
//                var foundIndex = -1
//                run breaking@{
//                    macAddressList.forEachIndexed { index, s ->
//                        DebugLog.e("macAddressList Loop $index value $s")
//                        if (s == macAddress) {
//                            foundIndex = index
//                            setToolBarStop(navController.currentDestination!!.id, foundIndex)
//                            return@breaking
//                        }
//                    }
//                }
            }

        }
    }

    private fun setToolBarStop(id: Int, foundIndex: Int) {
        DebugLog.e("setToolBarStop $foundIndex")
        runOnUiThread {
            when (foundIndex) {
                1 -> {
                    when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar1.root.viewShow()
                            binding.toolbar1.ivLeft.viewGone()
                            binding.toolbar1.ivMenu.viewShow()
                            binding.toolbar1.ivStop.viewGone()
                            binding.toolbar1.ivFilter.viewGone()
                            binding.toolbar1.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar1.root.viewShow()
                            binding.toolbar1.ivLeft.viewShow()
                            binding.toolbar1.ivMenu.viewShow()
                            binding.toolbar1.ivStop.viewGone()
                            binding.toolbar1.ivFilter.viewGone()
                            binding.toolbar1.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar1.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar1.root.viewShow()
                            binding.toolbar1.ivLeft.viewShow()
                            binding.toolbar1.ivFilter.viewGone()
                            binding.toolbar1.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar1.root.viewShow()
                            binding.toolbar1.ivLeft.viewShow()
                            binding.toolbar1.ivMenu.viewGone()
                            binding.toolbar1.ivStop.viewGone()
                            binding.toolbar1.ivFilter.viewShow()
                            binding.toolbar1.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar1.ivLeft.viewShow()
                            binding.toolbar1.ivMenu.viewShow()
                            binding.toolbar1.ivStop.viewGone()
                            binding.toolbar1.ivFilter.viewGone()
                            binding.toolbar1.ivFetch.viewGone()
                        }
                    }
                }

                2 -> {
                    when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar2.root.viewShow()
                            binding.toolbar2.ivLeft.viewGone()
                            binding.toolbar2.ivMenu.viewShow()
                            binding.toolbar2.ivStop.viewGone()
                            binding.toolbar2.ivFilter.viewGone()
                            binding.toolbar2.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar2.root.viewShow()
                            binding.toolbar2.ivLeft.viewShow()
                            binding.toolbar2.ivMenu.viewShow()
                            binding.toolbar2.ivStop.viewGone()
                            binding.toolbar2.ivFilter.viewGone()
                            binding.toolbar2.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar2.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar2.root.viewShow()
                            binding.toolbar2.ivLeft.viewShow()
                            binding.toolbar2.ivFilter.viewGone()
                            binding.toolbar2.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar2.root.viewShow()
                            binding.toolbar2.ivLeft.viewShow()
                            binding.toolbar2.ivMenu.viewGone()
                            binding.toolbar2.ivStop.viewGone()
                            binding.toolbar2.ivFilter.viewShow()
                            binding.toolbar2.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar2.ivLeft.viewShow()
                            binding.toolbar2.ivMenu.viewShow()
                            binding.toolbar2.ivStop.viewGone()
                            binding.toolbar2.ivFilter.viewGone()
                            binding.toolbar2.ivFetch.viewGone()
                        }
                    }
                }

                3 -> {
                    when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar3.root.viewShow()
                            binding.toolbar3.ivLeft.viewGone()
                            binding.toolbar3.ivMenu.viewShow()
                            binding.toolbar3.ivStop.viewGone()
                            binding.toolbar3.ivFilter.viewGone()
                            binding.toolbar3.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar3.root.viewShow()
                            binding.toolbar3.ivLeft.viewShow()
                            binding.toolbar3.ivMenu.viewShow()
                            binding.toolbar3.ivStop.viewGone()
                            binding.toolbar3.ivFilter.viewGone()
                            binding.toolbar3.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar3.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar3.root.viewShow()
                            binding.toolbar3.ivLeft.viewShow()
                            binding.toolbar3.ivFilter.viewGone()
                            binding.toolbar3.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar3.root.viewShow()
                            binding.toolbar3.ivLeft.viewShow()
                            binding.toolbar3.ivMenu.viewGone()
                            binding.toolbar3.ivStop.viewGone()
                            binding.toolbar3.ivFilter.viewShow()
                            binding.toolbar3.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar3.ivLeft.viewShow()
                            binding.toolbar3.ivMenu.viewShow()
                            binding.toolbar3.ivStop.viewGone()
                            binding.toolbar3.ivFilter.viewGone()
                            binding.toolbar3.ivFetch.viewGone()
                        }
                    }
                }

                4 -> {
                    when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar4.root.viewShow()
                            binding.toolbar4.ivLeft.viewGone()
                            binding.toolbar4.ivMenu.viewShow()
                            binding.toolbar4.ivStop.viewGone()
                            binding.toolbar4.ivFilter.viewGone()
                            binding.toolbar4.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar4.root.viewShow()
                            binding.toolbar4.ivLeft.viewShow()
                            binding.toolbar4.ivMenu.viewShow()
                            binding.toolbar4.ivStop.viewGone()
                            binding.toolbar4.ivFilter.viewGone()
                            binding.toolbar4.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar4.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar4.root.viewShow()
                            binding.toolbar4.ivLeft.viewShow()
                            binding.toolbar4.ivFilter.viewGone()
                            binding.toolbar4.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar4.root.viewShow()
                            binding.toolbar4.ivLeft.viewShow()
                            binding.toolbar4.ivMenu.viewGone()
                            binding.toolbar4.ivStop.viewGone()
                            binding.toolbar4.ivFilter.viewShow()
                            binding.toolbar4.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar4.ivLeft.viewShow()
                            binding.toolbar4.ivMenu.viewShow()
                            binding.toolbar4.ivStop.viewGone()
                            binding.toolbar4.ivFilter.viewGone()
                            binding.toolbar4.ivFetch.viewGone()
                        }
                    }
                }

                5 -> {
                    when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar5.root.viewShow()
                            binding.toolbar5.ivLeft.viewGone()
                            binding.toolbar5.ivMenu.viewShow()
                            binding.toolbar5.ivStop.viewGone()
                            binding.toolbar5.ivFilter.viewGone()
                            binding.toolbar5.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar5.root.viewShow()
                            binding.toolbar5.ivLeft.viewShow()
                            binding.toolbar5.ivMenu.viewShow()
                            binding.toolbar5.ivStop.viewGone()
                            binding.toolbar5.ivFilter.viewGone()
                            binding.toolbar5.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar5.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar5.root.viewShow()
                            binding.toolbar5.ivLeft.viewShow()
                            binding.toolbar5.ivFilter.viewGone()
                            binding.toolbar5.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar5.root.viewShow()
                            binding.toolbar5.ivLeft.viewShow()
                            binding.toolbar5.ivMenu.viewGone()
                            binding.toolbar5.ivStop.viewGone()
                            binding.toolbar5.ivFilter.viewShow()
                            binding.toolbar5.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar5.ivLeft.viewShow()
                            binding.toolbar5.ivMenu.viewShow()
                            binding.toolbar5.ivStop.viewGone()
                            binding.toolbar5.ivFilter.viewGone()
                            binding.toolbar5.ivFetch.viewGone()
                        }
                    }
                }

                6 -> {
                    //UnComment for 7 device connectivity
                    /*when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar6.root.viewShow()
                            binding.toolbar6.ivLeft.viewGone()
                            binding.toolbar6.ivMenu.viewShow()
                            binding.toolbar6.ivStop.viewGone()
                            binding.toolbar6.ivFilter.viewGone()
                            binding.toolbar6.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar6.root.viewShow()
                            binding.toolbar6.ivLeft.viewShow()
                            binding.toolbar6.ivMenu.viewShow()
                            binding.toolbar6.ivStop.viewGone()
                            binding.toolbar6.ivFilter.viewGone()
                            binding.toolbar6.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar6.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar6.root.viewShow()
                            binding.toolbar6.ivLeft.viewShow()
                            binding.toolbar6.ivFilter.viewGone()
                            binding.toolbar6.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar6.root.viewShow()
                            binding.toolbar6.ivLeft.viewShow()
                            binding.toolbar6.ivMenu.viewGone()
                            binding.toolbar6.ivStop.viewGone()
                            binding.toolbar6.ivFilter.viewShow()
                            binding.toolbar6.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar6.ivLeft.viewShow()
                            binding.toolbar6.ivMenu.viewShow()
                            binding.toolbar6.ivStop.viewGone()
                            binding.toolbar6.ivFilter.viewGone()
                            binding.toolbar6.ivFetch.viewGone()
                        }
                    }*/
                }

                7 -> {
                    //UnComment for 7 device connectivity
                    /*when (id) {
                        R.id.homeFragment -> {
                            binding.toolbar7.root.viewShow()
                            binding.toolbar7.ivLeft.viewGone()
                            binding.toolbar7.ivMenu.viewShow()
                            binding.toolbar7.ivStop.viewGone()
                            binding.toolbar7.ivFilter.viewGone()
                            binding.toolbar7.ivFetch.viewGone()
                        }

                        R.id.recipeFragment -> {
                            binding.toolbar7.root.viewShow()
                            binding.toolbar7.ivLeft.viewShow()
                            binding.toolbar7.ivMenu.viewShow()
                            binding.toolbar7.ivStop.viewGone()
                            binding.toolbar7.ivFilter.viewGone()
                            binding.toolbar7.ivFetch.viewGone()
                        }

                        R.id.recipeDetailFragment -> {
                            binding.toolbar7.root.viewGone()
                        }

                        R.id.cookingFragment -> {
                            binding.toolbar7.root.viewShow()
                            binding.toolbar7.ivLeft.viewShow()
                            binding.toolbar7.ivFilter.viewGone()
                            binding.toolbar7.ivStop.viewShow()
                        }

                        R.id.logFragment -> {
                            binding.toolbar7.root.viewShow()
                            binding.toolbar7.ivLeft.viewShow()
                            binding.toolbar7.ivMenu.viewGone()
                            binding.toolbar7.ivStop.viewGone()
                            binding.toolbar7.ivFilter.viewShow()
                            binding.toolbar7.ivFetch.viewShow()
                        }

                        else -> {
                            binding.toolbar7.ivLeft.viewShow()
                            binding.toolbar7.ivMenu.viewShow()
                            binding.toolbar7.ivStop.viewGone()
                            binding.toolbar7.ivFilter.viewGone()
                            binding.toolbar7.ivFetch.viewGone()
                        }
                    }*/
                }
            }
        }
    }

    internal fun findAndParseData(macAddress: String, data: Intent) {
        try {
            val fragment = myFragmentMap[macAddress]?.fragment ?: return
            val navController = (fragment as DashboardFragment).navController
            navController?.let { _ ->
                navController.currentDestination?.let {
                    when (it.id) {
                        R.id.homeFragment -> {
                            (fragment.getCurrentFragment() as HomeFragment).parseData(data)
                        }

                        R.id.recipeFragment -> {
                            (fragment.getCurrentFragment() as RecipeFragment).parseData(data)
                        }

                        R.id.recipeDetailFragment -> {
                            (fragment.getCurrentFragment() as RecipeDetailFragment).parseData(data)
                        }

                        R.id.cookingFragment -> {
                            (fragment.getCurrentFragment() as CookingFragment).parseData(data)
                        }
                    }
                }

            }

        } catch (e: Exception) {
            DebugLog.e("findAndParseData Exception ${e.message}")
        }
    }

    private fun openAudioDialog() {
        DialogUtils().selectAudioTypeDialog(
            this@HomeTvActivity,
            isAppLogoDisplay = true,
            isCancelable = true
        ) {
            selectedType = it
            openFilePicker(selectedZip = Constants.AUDIO)
        }
    }

    private fun openFilePicker(selectedZip: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedZipType = selectedZip
        resultLauncher.launch(intent)
    }

    fun isConnected(isVisible: Boolean) {
        if (isVisible) binding.toolBar.visibility = View.VISIBLE else binding.toolBar.visibility =
            View.GONE
    }

    //-------PrinceEWW, Create function with same name below, for improve speed of import recipe and recipe name issue-------/
    /*private fun unZipNew(zipFile: File, location: String) {
        try {
            DebugLog.e("unzipNew: $location Name:- ${zipFile.name}")
            val f = File(location)
            if (!f.isDirectory) {
                f.mkdirs()
            }
            val zin = ZipInputStream(FileInputStream(zipFile))
            try {
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {
                    DebugLog.e("unZipNew: ${ze!!.name.contains(".zip")}")
                    val path = File(location, ze!!.name)
                    if (ze!!.isDirectory) {
                        val unzipFile = File(location + ze!!.name)
                        if (!unzipFile.isDirectory) {
                            unzipFile.mkdirs()
                        }
                    } else {
                        val fout = FileOutputStream(path, false)
                        try {
                            var c = zin.read()
                            while (c != -1) {
                                fout.write(c)
                                c = zin.read()
                            }
                            zin.closeEntry()
                        } finally {
                            fout.close()
                            if (ze!!.name.contains(".zip")) {
                                val subRoot = File(location, ze!!.name.replace(".zip", ""))
                                if (!subRoot.exists()) {
                                    subRoot.mkdirs()
                                }
                                val zipFile = File(location, "${ze!!.name}")
                                if (zipFile.exists()) {
                                    unZipNew(zipFile, subRoot.path)
                                }
                                DebugLog.e("unZipNew: exists${zipFile.exists()}")
                            }
                        }
                    }
                }
            } finally {
                validateRecipeAndInsert(location, zipFile.nameWithoutExtension)
                zin.close()
            }
        } catch (e: java.lang.Exception) {
            DebugLog.e("Unzip exception $e")
        }
    }*/

    private fun unZipNew(zipFile: File, location: String) {
        try {
            DebugLog.e("unzipNew: $location Name:- ${zipFile.name}")
            val f = File(location)
            if (!f.isDirectory) {
                f.mkdirs()
            }
            val zin = ZipInputStream(BufferedInputStream(FileInputStream(zipFile)))
            var txtFileName: String? = null // Variable to store the name of the .txt file
            try {
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {
                    DebugLog.e("unZipNew: ${ze!!.name.contains(".zip")}")
                    val path = File(location, ze!!.name)
                    if (ze!!.isDirectory) {
                        val unzipFile = File(location + ze!!.name)
                        if (!unzipFile.isDirectory) {
                            unzipFile.mkdirs()
                        }
                    } else {
                        BufferedOutputStream(FileOutputStream(path)).use { fout ->
                            val buffer = ByteArray(1024)
                            var count: Int
                            while (zin.read(buffer).also { count = it } != -1) {
                                fout.write(buffer, 0, count)
                            }
                            zin.closeEntry()
                        }
                        Log.e("PrinceEWW>>>", "unZipNew - ze.name ${ze!!.name}")
                        if (ze!!.name.contains(".zip")) {
                            val subRoot = File(location, ze!!.name.replace(".zip", ""))
                            if (!subRoot.exists()) {
                                subRoot.mkdirs()
                            }
                            val nestedZipFile = File(location, "${ze!!.name}")
                            if (nestedZipFile.exists()) {
                                unZipNew(nestedZipFile, subRoot.path)
                            }
                            DebugLog.e("unZipNew: exists${nestedZipFile.exists()}")
                        } else if (ze!!.name.endsWith(".txt")) {
                            // Store the .txt file name
                            txtFileName = ze!!.name
                            Log.e("PrinceEWW>>>", "unZipNew - Store name of recipe in variable txtFileName - $txtFileName")
                        }
                    }
                }
            } finally {
                Log.e("PrinceEWW>>>", "unZipNew - Execute finally for validateRecipeAndInsert")
                // Use the stored .txt file name, if available, for validation
                txtFileName?.let {
                    validateRecipeAndInsert(location, it.removeSuffix(".txt"))
                }
                zin.close()
            }
        } catch (e: java.lang.Exception) {
            DebugLog.e("Unzip exception $e")
        }
    }

    private fun unZipAudio(zipFile: File, location: String) {
        try {
            DebugLog.e("unzipNew: $location Name:- ${zipFile.name}")
            val f = File(location)
            if (!f.isDirectory) {
                f.mkdirs()
            }
            val zin = ZipInputStream(FileInputStream(zipFile))
            try {
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {
                    DebugLog.e("unZipNew: ${ze!!.name.contains(".zip")}")
                    val path = File(location, ze!!.name)
                    if (ze!!.isDirectory) {
                        val unzipFile = File(location + ze!!.name)
                        if (!unzipFile.isDirectory) {
                            unzipFile.mkdirs()
                        }
                    } else {
                        val fout = FileOutputStream(path, false)
                        try {
                            var c = zin.read()
                            while (c != -1) {
                                fout.write(c)
                                c = zin.read()
                            }
                            zin.closeEntry()
                        } finally {
                            fout.close()
//                            if (ze!!.name.contains(".zip")) {
//                                val subRoot = File(location, ze!!.name.replace(".zip", ""))
//                                if (!subRoot.exists()) {
//                                    subRoot.mkdirs()
//                                }
//                                val zipFile = File(location, "${ze!!.name}")
//                                if (zipFile.exists()) {
//                                    unZipNew(zipFile, subRoot.path)
//                                }
//                                DebugLog.e("unZipNew: exists${zipFile.exists()}")
//                            }
                        }
                    }
                }
            } finally {
//                validateRecipeAndInsert(location, zipFile.nameWithoutExtension)
                zin.close()
            }
        } catch (e: java.lang.Exception) {
            DebugLog.e("Unzip exception $e")
        }
    }

    private fun isSameNameRecipeAvailable(name: String): Boolean {
        try {
            var isAvailable = false
            runBlocking {
                CoroutineScope(Dispatchers.IO).launch {
                    isAvailable = OnToCookApplication.dbInstance.recipeDao()
                        .isSameNameItemAlreadyExist(name) //Here, when user enter "Al", we got "Al", "Aloo Matar", "Aloo Paneer"
                        .any { recipe -> recipe.name[0] == name } //So we need to return true if same name(Al) is exist or not, else false
                }.join() // Wait for the coroutine to finish
            }
            return isAvailable
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(
                    this, "Invalid Text File", Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
    }

    private fun validateRecipeAndInsert(location: String, name: String) {
//Read text from file
        //Read text from file
        run breaking@{
//            try {
            val textFile = File(location, "$name.txt")
            DebugLog.e("validateRecipeAndInsert $location Name.. $name Exits ${textFile.exists()} extension ${textFile.extension}")
            DebugLog.e("validateRecipeAndInsert $location Name.. $name Exits ${textFile.extension != "txt"}")

            if (textFile.exists() && textFile.extension != "txt") {
                DebugLog.e("validateRecipeAndInsert Innner")
                Toast.makeText(this@HomeTvActivity, "Invalid Type", Toast.LENGTH_SHORT).show()
                return@breaking
            }
            DebugLog.e("validateRecipeAndInsert Innner After")

            val recipe = Gson().fromJson(getTextFromFile(textFile), Recipe::class.java)
            if (!isSameNameRecipeAvailable(recipe.name[0])) {
                Executors.newSingleThreadExecutor().execute {
                    if (recipe.imageUrl.isNotEmpty()) {
                        val imgFile = File(location, "$name.jpg")
                        recipe.imageUrl = Uri.fromFile(imgFile).toString()
                    }
                    recipe.Ingredients.forEachIndexed { index, it ->
                        if (it.image.isNotEmpty()) {
                            var imgFile = File(location, "${it.title + "_" + index}.jpg")
                            if (!imgFile.exists()) {
                                imgFile = File(location, "${it.title}.jpg")
                            }
                            it.image = Uri.fromFile(imgFile).toString()
                        }
                    }
                    recipe.Instruction.forEachIndexed { index, it ->
                        if (it.durationInSec == 0) {
                            try {
                                it.durationInSec = it.Induction_on_time.toInt()
                                    .coerceAtLeast(it.Magnetron_on_time.toInt())
                            } catch (e: Exception) {
                                DebugLog.e("Exception ${e.message}")
                            }
                        }
                    }
                    recipe.id = 0
                    DebugLog.e("Recipe Id ${recipe.id}")
                    OnToCookApplication.dbInstance.recipeDao().insert(recipe)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, String.format(
                        resources.getString(R.string.dynamic_message_recipe_already_added),
                        recipe.name[0]
                    ), Toast.LENGTH_SHORT).show()
                }
            }
//            DebugLog.e("checkAndInsert: ${Gson().toJson(recipe)}")
//            } catch (e: IOException) {
//                DebugLog.e("checkAndInsert: IOException${e.message}")
//                DebugLog.e("checkAndInsert:IOException ${e.printStackTrace()}")
//                //You'll need to add proper error handling here
//            }
        }


    }

    fun getFragmentFromMac(macAddress: String): Fragment? {
        return if (myFragmentMap.containsKey(macAddress))
            myFragmentMap[macAddress]!!.fragment
        else
            null
    }

    private fun getFragmentFromIndex(macAddress: Int): Fragment? {
        val keys = myFragmentMap.filterValues { it.id == macAddress + 1 }.keys
        return if (keys.isNotEmpty())
            myFragmentMap[keys.first()]?.fragment
        else null
    }

    private fun getMacFromIndex(macAddress: Int): String? {
        val keys = myFragmentMap.filterValues { it.id == macAddress + 1 }.keys
        return if (keys.isNotEmpty())
            myFragmentMap[keys.first()]?.mac
        else null
    }

    fun getIndexFromMac(macAddress: String): Int? {
        //-------PrinceEWW, try catch by PrinceEWW-------//
        try {
            // Code that may throw ConcurrentModificationException
            if (macAddress == Constants.DummyMacAddress) return 1
            val keys = myFragmentMap.filterValues { it.mac == macAddress }.keys
//                myFragmentMap[keys.first()]?.id //Commented by PrinceEWW, because it always return first value from "myFragmentMap"
            return if (keys.isNotEmpty()) {
                myFragmentMap.getValue(keys.first()).id
            }
            else null
        } catch (e: ConcurrentModificationException) {
            // Handle the exception gracefully
            // Log the exception or perform any necessary cleanup
            // Optionally, retry the operation or take corrective action
            Log.e("PrinceEWW>>>", "ConcurrentModificationException: ${e.message}")
            return null
        }
    }

    private fun openDeleteDeviceDialog() {
//        service.startScan()
        val pairedDeviceList =
            SharedPreferencesManager.getMacAddressList(
                applicationContext
            )
        lateinit var connectedDeviceAdapter: PairedDevicesAdapter
        val dialog = Dialog(this).apply {
            val dialogSavedDeviceListBinding: com.invent.ontocook.databinding.DialogSavedDeviceListBinding =
                com.invent.ontocook.databinding.DialogSavedDeviceListBinding.inflate(
                    LayoutInflater.from(
                        context
                    )
                )
            setContentView(dialogSavedDeviceListBinding.root)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window!!.setLayout(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.MATCH_PARENT
            )

            //-------No need to show bluetooth icon here, because this dialog is only for connected device-------//
            dialogSavedDeviceListBinding.bleStateView.gone()

            dialogSavedDeviceListBinding.ivLeft.onSafeClick {
                this.dismiss()
            }

            connectedDeviceAdapter = PairedDevicesAdapter(
                pairedDeviceList,
                true
            ) { id, position, pairedDeviceData, editedName ->
                when (id) {
                    //-------PrinceEWW, comment below code, because we face crash because of indexing issue, so need to improve this code, Improved code is just below this commented code-------//
                    R.id.ivDelete -> {
                        DialogUtils().commonDialog(this@HomeTvActivity,
                            getString(R.string.txt_delete_device),
                            "Are you sure you want to delete ${pairedDeviceData.name} ?",
                            getString(R.string.button_yes),
                            getString(R.string.button_no),
                            true,
                            isCancelable = true,
                            {
                                try {
                                    CoroutineScope(Dispatchers.IO).launch {
//                                        var removeIndex = -1
                                        var indexForRemoveFromConnectedDeviceList = -1
                                        var indexForRemoveFromPairedDevicesList = -1
                                        val task = async {
                                            /*run breaking@{
                                                pairedDeviceList.forEachIndexed { index, pairedDeviceData1 ->
                                                    if (pairedDeviceData.macAddress == pairedDeviceData1.macAddress) {
                                                        removeIndex = index
                                                        return@breaking
                                                    }
                                                }
                                            }*/
                                            indexForRemoveFromConnectedDeviceList = pairedDeviceList.indexOfFirst { it.macAddress == pairedDeviceData.macAddress }
                                            indexForRemoveFromPairedDevicesList = pairedDevicesList.indexOfFirst { it.macAddress == pairedDeviceData.macAddress }
                                        }
                                        task.await()
                                        /*if (removeIndex != -1) {
                                            pairedDeviceList.removeAt(removeIndex)
                                            pairedDevicesList.removeAt(removeIndex) //Remove item from pairedDevicesList (which is on previous dialog)
                                            runOnUiThread {
                                            connectedDeviceAdapter.notifyItemRemoved(removeIndex)
                                            pairedDeviceAdapter.notifyItemRemoved(removeIndex) //Remove item from pairedDeviceAdapter (which is on previous dialog)
                                                try {
                                                    getId(pairedDeviceData.id, false) // Hide fragment
                                                } catch (e: java.lang.Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }*/
                                        //-------Above code is commented by PrinceEWW, because there is crash when user try to delete device which is paired in past but  currently not connected-------//
                                        if (indexForRemoveFromConnectedDeviceList != -1) {
                                            pairedDeviceList.removeAt(
                                                indexForRemoveFromConnectedDeviceList
                                            )
                                            runOnUiThread {
                                                connectedDeviceAdapter.notifyItemRemoved(
                                                    indexForRemoveFromConnectedDeviceList
                                                )
                                            }
                                        }

                                        if (indexForRemoveFromPairedDevicesList != -1) {
                                            pairedDevicesList.removeAt(
                                                indexForRemoveFromPairedDevicesList
                                            ) //Remove item from pairedDevicesList (which is on previous dialog)
                                            myFragmentMap.remove(pairedDeviceData.macAddress) //PrinceEWW, need to remove to prevent glitch of fragment replacement
                                            runOnUiThread {
                                                pairedDeviceAdapter.notifyItemRemoved(
                                                    indexForRemoveFromPairedDevicesList
                                                ) //Remove item from pairedDeviceAdapter (which is on previous dialog)
                                                try {
                                                    getId(
                                                        pairedDeviceData.id,
                                                        false
                                                    ) // Hide fragment
                                                } catch (e: java.lang.Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        delay(300)
                                        val sharedPref = async {
                                            SharedPreferencesManager.updateMacAddressList(
                                                this@HomeTvActivity, pairedDeviceList
                                            )
                                            if (service.isDeviceConnected(pairedDeviceData.macAddress))
                                                service.disconnect(pairedDeviceData.macAddress)
                                        }
                                        runOnUiThread {
                                            if (pairedDeviceList.any { it.macAddress == pairedDeviceData.macAddress }) {
                                                pairedDeviceList.removeAt(position)
                                                connectedDeviceAdapter.notifyItemRemoved(position)
                                                try {
                                                    getId(pairedDeviceData.id, false)
                                                } catch (e: java.lang.Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }

                                        DebugLog.e("macAddressCheking_6_7 $position")
                                        if (macAddressList.any { it == pairedDeviceData.macAddress }) {
                                            macAddressList.remove(pairedDeviceData.macAddress)
                                            hashmap.remove(pairedDeviceData.macAddress)

                                            DebugLog.e("TestScan Tab Disconnect Pos $position")
                                        }
                                        //add
                                        sharedPref.await()
                                        startScan(false) //Start Scan after disconnect device
                                        DebugLog.e("macAddressCheking_6_7 ${macAddressList.isNotEmpty()}")
                                        if (macAddressList.isNotEmpty())
                                            return@launch
                                        runOnUiThread {
                                            addDummyMacAddress()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    DebugLog.e("Error in Delete")
                                }
                            },
                            {

                            })
                    }
                }
            }
            dialogSavedDeviceListBinding.rvPairedDevices.adapter = connectedDeviceAdapter
        }
        dialog.show()
    }

}