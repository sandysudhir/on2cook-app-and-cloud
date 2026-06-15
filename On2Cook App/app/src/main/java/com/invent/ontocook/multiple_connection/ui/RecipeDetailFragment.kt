package com.invent.ontocook.multiple_connection.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.IngredientsListAdapter
import com.invent.ontocook.adapter.NutritionalListAdapter
import com.invent.ontocook.adapter.RecommendItemListAdapter
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.databinding.FragmentRecipeDetailBinding
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.dialog.PreviewDialog
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.IngredientsSteps
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.models.RecipeNew
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.ftp.MyFTPClientFunctions
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DateTimeHelper
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.shareZipFile
import com.invent.ontocook.utils.withNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "recipeItem"
private const val ARG_PARAM2 = "MAC_ADDRESS"

class RecipeDetailFragment : Fragment() {
    private var macAddress: String = ""
    private var isVisible: Boolean = false
    private var isDeleteRecipeFromMobileDevice: Boolean = false //This variable indicate, true -  delete from "mobile application device", false - delete from "On2Cook device"

    lateinit var reservationG: WifiManager.LocalOnlyHotspotReservation
    private lateinit var ingredientsListAdapter: IngredientsListAdapter
    private lateinit var nutritionalListAdapter: NutritionalListAdapter
    private lateinit var recommendItemListAdapter: RecommendItemListAdapter
    var recentItem: RecentItem? = null
    val listRecipe: ArrayList<Recipe> = ArrayList()

    /*"randomRecipeList" will be set into adapter, in this list we remove recipe if  it contains current opened recipe &
    * "allRandomRecipeList" will contain all recipe which we need to pass on tap randomRecipeListAdapter, because "randomRecipeList" is filtered list do we can not pass that list*/
    val randomRecipeList: ArrayList<Recipe> = ArrayList()
    val allRandomRecipeList: ArrayList<Recipe> = ArrayList()
    private var deviceScanDialog: DeviceScanDialog? = null
    lateinit var broadcastReceiver: BroadcastReceiver
    private val TAG = this::class.java.simpleName
    private var recommendedItems = mutableListOf<RecentItem>()
    private lateinit var communicationReceiver: BroadcastReceiver
    lateinit var binding: FragmentRecipeDetailBinding
    var someActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bundle = Bundle().apply {
                putBoolean(Constants.IS_EDIT, true)
            }
            Log.e("PrinceEWW>>>", "RESULT_OK - setFragmentResult - isEditRecipe")
            //-------this resultLauncher(someActivityResultLauncher) is use when user edit recipe, so we after edit recipe we have to  setFragmentResult here, for update list in recipeList(RecipeFragment)-------//
            parentFragmentManager.setFragmentResult(Constants.RESULT_KEY, bundle)
            Executors.newSingleThreadExecutor().execute {
                recentItem?.let {
                    OnToCookApplication.dbInstance.recipeDao().getRecipe(it.id)
                        .subscribe({ recipeDb ->
                            lateinit var oldRecipe: Recipe
                            var updateIndex = -1
                            run breaking@{
                                //-------Code commented by Prince, unnecessary Code by another developer (issue - Constants.RECIPE_LIST)-------//
                                listRecipe.forEachIndexed { index, recipe ->
                                    if (it.name[0].lowercase() == recipeDb.name[0]) {
                                        oldRecipe = recipe
                                        updateIndex = index
                                        return@breaking
                                    }
                                }
                            }

                            recentItem!!.recipe = Gson().toJson(recipeDb, Recipe::class.java)
                            recentItem!!.ingredients.clear()
                            recipeDb.Ingredients.forEachIndexed { index, ingredients ->
                                val step = IngredientsSteps(
                                    ingredients.image,
                                    ingredients.title,
                                    ingredients.weight,
                                    ingredients.text
                                )
                                recentItem!!.ingredients.add(index, step)
                            }
                            recipeDb.Instruction.forEach {
                                Log.e(TAG, "Instruction:durationInSec ${it.durationInSec}")
                            }
                            recentItem!!.duration =
                                recipeDb.Instruction.sumOf { it1 -> it1.durationInSec }.toString()
                            if (recipeDb.name.isNotEmpty()) {
                                recentItem!!.name = recipeDb.name[0]
                            }
                            recentItem!!.desc = recipeDb.description
                            recentItem!!.difficulty = recipeDb.difficulty
                            requireActivity().runOnUiThread {
                                ingredientsListAdapter.setQuickAccessList(recentItem!!.ingredients)
                                prepareView()
                            }
                        }, {

                        })
                }
            }
        }
    }

    lateinit var service: BleService
    var pass = ""
    var ssid = ""
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            macAddress = it.getString(ARG_PARAM2)!!
            recentItem = it.getSerializable(ARG_PARAM1) as RecentItem?
        }
        DebugLog.e("recentItem ${recentItem?.id}")
        arguments.let {
            if (it != null) {
                Log.e(TAG, "onCreate:Bundle $it")
                if (it.containsKey(Constants.RECIPE)) recentItem =
                    it.getSerializable(Constants.RECIPE) as RecentItem?
                if (it.containsKey(Constants.MAC_ADDRESS)) macAddress =
                    it.getString(Constants.MAC_ADDRESS)!!
                if (it.containsKey(Constants.IS_SHOW)) isVisible = it.getBoolean(Constants.IS_SHOW)

                //-------Commented byu PrinceEWW,-------//
                if (it.containsKey(Constants.RECIPE_LIST)) {
                    //-------By PrinceEWW, Previous developer pass whole list which throw byte size error-------//
                    //-------So we p[ass boolean, which indicate we need to fetch allRecipes from database-------//
                    /*val list = it.getString(Constants.RECIPE_LIST)
                    val type = object :
                        TypeToken<ArrayList<Recipe>>() {}.type
                    val listRecipeJson: ArrayList<Recipe> =
                        Gson().fromJson(list, type)
                    listRecipe.addAll(listRecipeJson)
                    DebugLog.e("RECIPE_LIST $listRecipe")*/


                    OnToCookApplication.dbInstance.recipeDao().getAllRecipe1().subscribe({
                        listRecipe.clear()
                        listRecipe.addAll(it.sortedBy { it -> it.name[0] })
                    }, {
                        Log.e("PrinceEWW>>>", it.message.toString())
                    })
                }

                if (it.containsKey(Constants.RANDOM_RECIPE_LIST)) {
                    val list = it.getString(Constants.RANDOM_RECIPE_LIST)
                    val type = object :
                        TypeToken<ArrayList<Recipe>>() {}.type
                    val listRecipeJson: ArrayList<Recipe> =
                        Gson().fromJson(list, type)
                    allRandomRecipeList.addAll(listRecipeJson)
                    randomRecipeList.addAll(listRecipeJson)
                    //-------"randomRecipeList" list will be set into adapter, so we need to remove same recipe, which we currently open-------//
                    recentItem.withNotNull { recentRecipeItem ->
                        randomRecipeList.removeIf { recentRecipeItem.id == it.id }
                    }
                }

            }
        }
        activity?.bindService(
            Intent(requireContext(), BleService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )
        if (Constants.IS_TABLET)
            if (macAddress == Constants.DummyMacAddress) (activity as HomeTvActivity).changeDummyMac.observe(
                this,
                androidx.lifecycle.Observer {
                    macAddress = it
                })
            else
                if (macAddress == Constants.DummyMacAddress) (activity as HomeActivity).changeDummyMac.observe(
                    this,
                    androidx.lifecycle.Observer {
                        macAddress = it
                    })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Constants.IS_TABLET)
            (activity as HomeTvActivity).changeDummyMac.removeObservers(this)
        else (activity as HomeActivity).changeDummyMac.removeObservers(this)
        activity?.unbindService(mConnection)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_recipe_detail, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun initListener() {
        binding.ivLeft.setOnClickListener {
//            service.writeData(
//                macAddress,
//                ("ssid=${ssid},password=${pass},status=off").toByteArray(
//                    Charsets.UTF_8
//                )
//            )

            findNavController().popBackStack()
//            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.ivDelete.setOnClickListener {
            DialogUtils().deleteRecipeDialog(
                requireContext(),
                "Delete",
                "Delete ${recentItem!!.name} from?",
                true,
                true,
                {
                    //-------Callback of delete recipe from "on2cook device"-------//
                    if (service.isDeviceConnected(macAddress)) {
                        isDeleteRecipeFromMobileDevice = false
                        service.writeData(
                            macAddress, ("DELETE=${recentItem!!.name}").toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
                        Toast.makeText(
                            requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                        ).show()
                    }
                }, {
                    //-------Callback of delete recipe from "application mobile device"-------//
                    isDeleteRecipeFromMobileDevice = true
                    Executors.newSingleThreadExecutor().execute {
                        //-------Delete recipe from local database-------//
                        val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
                        OnToCookApplication.dbInstance.recipeDao().delete(recipe)

                        //-------Delete recently played recipe from local database-------//
                        val recentlyPlayedRecipe =
                            Gson().fromJson(recentItem?.recipe, RecentlyPlayedRecipe::class.java)
                        OnToCookApplication.dbInstance.recentlyPlayedDao()
                            .delete(recentlyPlayedRecipe)
                    }
//                    findNavController().popBackStack()
                    //-------fragment result launcher - PrinceEWW-------//
                    //-------When user delete recipe from recipeDetail screen we need to delete that same recipe in RecipeFragment (recipeListing screen)
                    val result = Bundle().apply {
                        putString(Constants.BUNDLE_DELETE_RECIPE, recentItem?.recipe)
                        //-------Delete recipe from device = true (when we need to delete recipe from mobile), when user delete recipe for on2cook only no need to delete recipe from list-------//
                        putBoolean(
                            Constants.BUNDLE_KEY_DELETE_RECIPE_FROM_DEVICE,
                            isDeleteRecipeFromMobileDevice
                        )
                    }
                    parentFragmentManager.setFragmentResult(Constants.RESULT_KEY, result)
                    findNavController().navigateUp()
                }, {
                    //-------Callback of delete recipe from "Both(On2Cook and Mobile application) devices"-------//
                    if (service.isDeviceConnected(macAddress)) {
                        isDeleteRecipeFromMobileDevice = true
                        service.writeData(
                            macAddress, ("DELETE=${recentItem!!.name}").toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
                        Toast.makeText(
                            requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
        binding.ivShar.setOnClickListener {
            val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
            val fileList: ArrayList<File> = arrayListOf()
            val root = File(requireContext().externalCacheDir, "Temp")
            if (root.exists()) {
                root.deleteOnExit()
            }
            root.mkdirs()

            val recipeTextFile = File(root, recentItem!!.name + ".txt")

            val writer = FileOutputStream(recipeTextFile)
            writer.write(recentItem!!.recipe.toByteArray())
            writer.flush()
            writer.close()
            fileList.add(recipeTextFile)
            val textFileUri = FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".provider", recipeTextFile
            )
            recipe.Ingredients.forEachIndexed { index, it ->
                DebugLog.e("Title Image ${it.image} }")
                if (it.image.isNotEmpty()) {
                    try {
                        val imageFile = File(root, it.title + "_" + index + ".jpg")
                        val iStream: InputStream? =
                            requireContext().contentResolver.openInputStream(Uri.parse(it.image))
                        if (iStream != null) {
                            if (!imageFile.exists()) {
                                imageFile.createNewFile()
                            }
                            val imgWriter = FileOutputStream(imageFile)
                            val inputData: ByteArray = Constants.getBytes(iStream)!!
                            imgWriter.write(inputData)
                            imgWriter.flush()
                            imgWriter.close()
                            fileList.add(imageFile)
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
//                if (!it.audioIUrl.isNullOrEmpty()) {
//                    val audioFile = File(root, it.audioI + ".mp3")
//                    try {
//                        val iStream: InputStream? =
//                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioIUrl))
//                        if (iStream != null) {
//                            DebugLog.e("Print Fail Message Not Null")
//                            if (!audioFile.exists()) {
//                                audioFile.createNewFile()
//                            }
//                            val imgWriter = FileOutputStream(audioFile)
//                            val inputData: ByteArray = Constants.getBytes(iStream)!!
//                            imgWriter.write(inputData)
//                            imgWriter.flush()
//                            imgWriter.close()
//                            fileList.add(audioFile)
//                        }
//                    } catch (e: FileNotFoundException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // File not found or other exception occurred
//                        // Handle the exception accordingly
//                    } catch (e: IOException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // IOException occurred while reading the file
//                        // Handle the exception accordingly
//                    }
//                }
//                if (!it.audioQUrl.isNullOrEmpty()) {
//                    val audioFile = File(root, it.audioQ + ".mp3")
//                    try {
//                        val iStream: InputStream? =
//                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioQUrl))
//                        if (iStream != null) {
//                            DebugLog.e("Print Fail Message Not Null")
//                            if (!audioFile.exists()) {
//                                audioFile.createNewFile()
//                            }
//                            val imgWriter = FileOutputStream(audioFile)
//                            val inputData: ByteArray = Constants.getBytes(iStream)!!
//                            imgWriter.write(inputData)
//                            imgWriter.flush()
//                            imgWriter.close()
//                            fileList.add(audioFile)
//                        }
//                    } catch (e: FileNotFoundException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // File not found or other exception occurred
//                        // Handle the exception accordingly
//                    } catch (e: IOException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // IOException occurred while reading the file
//                        // Handle the exception accordingly
//                    }
//                }
//                if (!it.audioPUrl.isNullOrEmpty()) {
//                    val audioFile = File(root, it.audioP + ".mp3")
//                    try {
//                        val iStream: InputStream? =
//                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioPUrl))
//                        if (iStream != null) {
//                            DebugLog.e("Print Fail Message Not Null")
//                            if (!audioFile.exists()) {
//                                audioFile.createNewFile()
//                            }
//                            val imgWriter = FileOutputStream(audioFile)
//                            val inputData: ByteArray = Constants.getBytes(iStream)!!
//                            imgWriter.write(inputData)
//                            imgWriter.flush()
//                            imgWriter.close()
//                            fileList.add(audioFile)
//                        }
//                    } catch (e: FileNotFoundException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // File not found or other exception occurred
//                        // Handle the exception accordingly
//                    } catch (e: IOException) {
//                        DebugLog.e("Print Fail Message ${e.message}")
//                        e.printStackTrace()
//                        // IOException occurred while reading the file
//                        // Handle the exception accordingly
//                    }
//                }
            }
            recipe.Instruction.forEachIndexed { index, it ->
                DebugLog.e("Title Image ${it.image} }")
                if (!it.audioIUrl.isNullOrEmpty()) {
                    val audioFile = File(root, it.audioI + ".mp3")
                    try {
                        val iStream: InputStream? =
                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioIUrl))
                        if (iStream != null) {
                            DebugLog.e("Print Fail Message Not Null")
                            if (!audioFile.exists()) {
                                audioFile.createNewFile()
                            }
                            val imgWriter = FileOutputStream(audioFile)
                            val inputData: ByteArray = Constants.getBytes(iStream)!!
                            imgWriter.write(inputData)
                            imgWriter.flush()
                            imgWriter.close()
                            fileList.add(audioFile)
                        }
                    } catch (e: FileNotFoundException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // File not found or other exception occurred
                        // Handle the exception accordingly
                    } catch (e: IOException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // IOException occurred while reading the file
                        // Handle the exception accordingly
                    }
                }
                if (!it.audioQUrl.isNullOrEmpty()) {
                    val audioFile = File(root, it.audioQ + ".mp3")
                    try {
                        val iStream: InputStream? =
                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioQUrl))
                        if (iStream != null) {
                            DebugLog.e("Print Fail Message Not Null")
                            if (!audioFile.exists()) {
                                audioFile.createNewFile()
                            }
                            val imgWriter = FileOutputStream(audioFile)
                            val inputData: ByteArray = Constants.getBytes(iStream)!!
                            imgWriter.write(inputData)
                            imgWriter.flush()
                            imgWriter.close()
                            fileList.add(audioFile)
                        }
                    } catch (e: FileNotFoundException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // File not found or other exception occurred
                        // Handle the exception accordingly
                    } catch (e: IOException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // IOException occurred while reading the file
                        // Handle the exception accordingly
                    }
                }
                if (!it.audioPUrl.isNullOrEmpty()) {
                    val audioFile = File(root, it.audioP + ".mp3")
                    try {
                        val iStream: InputStream? =
                            requireContext().contentResolver.openInputStream(Uri.parse(it.audioPUrl))
                        if (iStream != null) {
                            DebugLog.e("Print Fail Message Not Null")
                            if (!audioFile.exists()) {
                                audioFile.createNewFile()
                            }
                            val imgWriter = FileOutputStream(audioFile)
                            val inputData: ByteArray = Constants.getBytes(iStream)!!
                            imgWriter.write(inputData)
                            imgWriter.flush()
                            imgWriter.close()
                            fileList.add(audioFile)
                        }
                    } catch (e: FileNotFoundException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // File not found or other exception occurred
                        // Handle the exception accordingly
                    } catch (e: IOException) {
                        DebugLog.e("Print Fail Message ${e.message}")
                        e.printStackTrace()
                        // IOException occurred while reading the file
                        // Handle the exception accordingly
                    }
                }
            }
            if (recipe.imageUrl.isNotEmpty()) {
                try {
                    val imageFile = File(root, recentItem!!.name + ".jpg")
                    val imgWriter = FileOutputStream(imageFile)
                    val iStream: InputStream? =
                        requireContext().contentResolver.openInputStream(Uri.parse(recipe.imageUrl))
                    if (iStream != null) {
                        val inputData: ByteArray = Constants.getBytes(iStream)!!
                        imgWriter.write(inputData)
                        imgWriter.flush()
                        imgWriter.close()
                        fileList.add(imageFile)
                        requireContext().shareZipFile(
                            fileList, recentItem!!.name
                        )
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
            } else {
                requireContext().shareZipFile(fileList, recentItem!!.name)
            }
//            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.ivEdit.setOnClickListener {
//            Executors.newSingleThreadExecutor().execute {
//                val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
//                val recipeRecent = RecentlyPlayedRecipe()
//                Gson().toJson(recipe)
//                recipeRecent.name = recipe.name
//                recipeRecent.audio1 = recipe.audio1
//                recipeRecent.audio2 = recipe.audio2
//                recipeRecent.Instruction = recipe.Instruction
//                recipeRecent.Ingredients = recipe.Ingredients
//                recipeRecent.macAddress = macAddress
//                recipeRecent.description = recipe.description
//                recipeRecent.id = recipe.id
//                recipeRecent.imageUrl = recipe.imageUrl
//                recipeRecent.tags = recipe.tags
//                recipeRecent.category = recipe.category
//                OnToCookApplication.dbInstance.recentlyPlayedDao().insert(recipeRecent)
//            }
            val intent = Intent(requireActivity(), CreateNewRecipe::class.java)
            intent.putExtra(Constants.RECIPE, recentItem)
            //-------Code commented by Prince, unnecessary Code by another developer (issue - Constants.RECIPE_LIST)-------//
//            intent.putExtra(Constants.RECIPE_LIST, Gson().toJson(listRecipe))
            intent.extras?.putBoolean(
                Constants.RECIPE_LIST,
                true
            ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
            someActivityResultLauncher.launch(intent)

            /*val intent = Intent(requireActivity(), CreateNewRecipe::class.java)
            startActivity(intent)*/
        }
        binding.btnSend.setOnClickListener {
            val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
            val recipeToSend = Gson().toJson(recipe)
            Log.e(TAG, "init: $recipeToSend")
            if (service.isDeviceConnected(macAddress)) {
                if (recipe.name[0].length < 32) {
                    LoadingUtils.showDialog(context, true, "Uploading File..")
                    val size = recipeToSend.toByteArray(
                        Charsets.UTF_8
                    ).size
                    service.writeFileData(
                        macAddress,
                        "{\"RECIPE\":\"${recipe.name[0]}\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    Log.e(TAG, "onCreate: Step 1 Sent Success")
                    service.setJsonFileDataInMap(
                        macAddress, recipeToSend
                    )
                } else Toast.makeText(
                    requireContext(), getString(R.string.strEditRecipe), Toast.LENGTH_SHORT
                ).show()
            } else Toast.makeText(
                requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
            ).show()
        }
        binding.imgPreview.setOnClickListener {
//            val wifiManager =
//                requireActivity().getSystemService(Context.WIFI_SERVICE) as WifiManager
//            DebugLog.e("List Handler")
//            if (ActivityCompat.checkSelfPermission(
//                    requireContext(),
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                return@setOnClickListener
//            }
//            DebugLog.e("List wifiManager")
//            val list = wifiManager.configuredNetworks
//            for (i in list) {
//                DebugLog.e("List Manager ${list.size}")
//                DebugLog.e("List Manager ${Gson().toJson(list)}")
//                if (i.SSID != null && i.SSID == "\"" + "TinyBox" + "\"") {
//                    wifiManager.disconnect()
//                    wifiManager.enableNetwork(i.networkId, true)
//                    wifiManager.reconnect()
//                    break
//                }
//            }
            openDialog()
        }
        binding.btnCookNow.setOnClickListener {
            //TODO
//            val permissionList = arrayListOf(
//                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES
//            )
//            PermissionManagerUtils.checkPermission(
//                requireContext(),
//                requireActivity(),
//                permissionList,
//                PermissionManagerUtils.PermissionSessionManager(requireActivity()),
//                object : PermissionManagerUtils.PermissionAskListener {
//                    override fun onNeedPermission() {
//                        ActivityCompat.requestPermissions(
//                            requireActivity(),
//                            permissionList.toTypedArray(),
//                            Constants.REQUEST_CAMERA_PERMISSION
//                        )
//                    }
//
//                    override fun onPermissionPreviouslyDenied() {
//                        ActivityCompat.requestPermissions(
//                            requireActivity(),
//                            permissionList.toTypedArray(),
//                            1
//                        )
//                    }
//
//                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
//                        //-------------HERE OPEN
//                        DialogUtils().commonDialog(
//                            context = requireContext(),
//                            title = getString(R.string.title_permission_open_settings),
//                            message = getString(R.string.message_storage_camera_permission),
//                            positiveButton = getString(R.string.button_setting),
//                            negativeButton = getString(R.string.button_cancel),
//                            isAppLogoDisplay = true,
//                            isCancelable = true,
//                            callbackSuccess = {
//                                //------Positive CallBack----//
//                                context?.openPermissionSettings()
//                            },
//                            callbackNegative = {
//                                //---------Negative CallBack----------//
//                            })
//                    }
//
//                    override fun onPermissionGranted() {
//                        startHotspot()
//                    }
//
//                })
//
//            return@setOnClickListener
            if (!Constants.IS_PRODUCTION_MODE) {
                redirectToCooking()
                return@setOnClickListener
            }

            if (service.isDeviceConnected(macAddress)) {
                binding.btnCookNow.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.btnCookNow.isEnabled = true
                }, 5000)
                service.writeData(
                    macAddress, Constants.STATUS.toByteArray(
                        Charsets.UTF_8
                    )
                )
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(communicationReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (Constants.IS_TABLET)
            (requireActivity() as HomeTvActivity).setToolbar(macAddress, false)
        else
            (requireActivity() as HomeActivity).setToolbar(macAddress, false)
        if (recentItem != null) {
            prepareView()
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION))
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        intentFilter.addAction(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
        intentFilter.addAction(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
        intentFilter.addAction("android.net.wifi.WIFI_AP_STA_JOIN")
        intentFilter.addAction("android.net.wifi.WIFI_HOTSPOT_CLIENTS_CHANGED")
        intentFilter.addAction("android.intent.action.SERVICE_STATE")
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.e(TAG, "onReceive:Action ${intent?.action}")
                }
            }, intentFilter)
    }

    private fun init() {
        if (isVisible) {
            binding.imgPreview.visibility = View.VISIBLE
            binding.btnSend.visibility = View.VISIBLE
            binding.ivDelete.visibility = View.VISIBLE
            binding.ivEdit.visibility = View.VISIBLE
            binding.ivLike.visibility = View.GONE
//            binding.tvPageTitle.text = (requireActivity() as HomeActivity).getName(macAddress)
//            binding.btnSend.visibility = View.VISIBLE
//            binding.btnPreview.visibility = View.VISIBLE
//            binding.ivDelete.visibility = View.VISIBLE
//            binding.ivEdit.visibility = View.VISIBLE
//            binding.ivLike.visibility = View.GONE
//            binding.ivShar.visibility = View.VISIBLE
//            binding.ivSave.visibility = View.GONE
        } else {
            binding.ivShar.visibility = View.GONE
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {

                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {

                    }

                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        service.writeData(
                            macAddress,
                            Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
                        )
                    }

                    Constants.EVENT_BLE_CONNECTION_INIT -> {
//                        toggleDeviceScanDialog(true)
                    }

                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
//                        var message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
//                        if (deviceScanDialog != null) {
//                            deviceScanDialog!!.updateResult(
//                                "Connecting to $message"
//                            )
//                        }
                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e(
                    TAG,
                    "onReceive:macAddress ${intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress}"
                )
                val recieveDataInMac = intent!!.getStringExtra(Constants.MAC_ADDRESS)
                Log.e("PrinceEWW>>>", "communicationReceiver in RecipeDetailFragment for macAddress: $recieveDataInMac and currentScreen Mac is $macAddress")

                if (intent.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    parseData(intent)
                } /*else {//Commented by PrinceEww, to prevent receive data multiple time issue (reboot on2Cook device)
                    if (Constants.IS_TABLET)
                        (requireActivity() as HomeTvActivity).findAndParseData(
                            intent.getStringExtra(
                                Constants.MAC_ADDRESS
                            )!!, intent
                        )
                    else (requireActivity() as HomeActivity).findAndParseData(
                        intent.getStringExtra(
                            Constants.MAC_ADDRESS
                        )!!, intent
                    )
                }*/
            }
        }


        //reNutritional
        //prepareIngredients()

        binding.reIngredients.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        ingredientsListAdapter = IngredientsListAdapter()
        binding.reIngredients.adapter = ingredientsListAdapter
        ingredientsListAdapter.setQuickAccessList(recentItem!!.ingredients)

//prepareNutritional()

        binding.reNutritional.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        nutritionalListAdapter = NutritionalListAdapter()
        binding.reNutritional.adapter = nutritionalListAdapter
        nutritionalListAdapter.setQuickAccessList(recentItem!!.nutritionalInfo)

        recommendedItems.clear()
        recommendedItems.add(
            RecentItem(
                1, R.drawable.ic_mutton, "Mutton Kofta Curry", "Easy", "9 mins"
            )
        )
        recommendedItems.add(
            RecentItem(
                2, R.drawable.ic_red_saus, "Red Sauce Cheese Penne Pasta", "Medium", "9 mins"
            )
        )

        binding.reRelated.layoutManager = GridLayoutManager(context, 2)
        recommendItemListAdapter = RecommendItemListAdapter(randomRecipeList) { position, recipe ->
            var name = ""
            if (recipe.name.isNotEmpty()) {
                name = recipe.name[0]
            }
            val recipeItem = RecentItem(
                recipe.id,
                R.drawable.ic_mutton,
                name,
                recipe.difficulty,
                "9 mins",
                "Indian|Snacks"
            )
            recipe.Ingredients.forEachIndexed { index, ingredients ->
                val step = IngredientsSteps(
                    ingredients.image, ingredients.title, ingredients.weight, ingredients.text
                )
                recipeItem.ingredients.add(index, step)
            }
            recipeItem.name = name
            recipeItem.recipe = Gson().toJson(recipe, Recipe::class.java)
            recipeItem.desc = recipe.description
            val bundle = Bundle().apply {
                putSerializable(Constants.RECIPE, recipeItem)
//                putString(Constants.RECIPE_LIST, Gson().toJson(listRecipe))
                putBoolean(
                    Constants.RECIPE_LIST,
                    true
                ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                putString(Constants.RANDOM_RECIPE_LIST, Gson().toJson(allRandomRecipeList))
                putString(Constants.MAC_ADDRESS, macAddress)
                putBoolean(Constants.IS_SHOW, true)
            }
            findNavController().navigate(
                R.id.recipeDetailFragment, bundle
            )
        }
//        recommendItemListAdapter.setModesList(randomRecipeList)
//        recommendItemListAdapter.addAll(randomRecipeList)
        binding.reRelated.adapter = recommendItemListAdapter
    }

    internal fun parseData(intent: Intent) {
        when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                if (message.lowercase().contains("recipe=")) {
                    LoadingUtils.hideDialog()
                    binding.btnCookNow.isEnabled = true
                    if (message.uppercase() == Constants.RECIPE_NONE) {
                        Toast.makeText(
                            requireContext(),
                            requireContext().getText(R.string.txt_send_rcipe),
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val foundRecipe = listRecipe.filter {
                        /*DebugLog.e("foundRecipe ${it.name[0]}")
                        DebugLog.e("foundRecipe ${Constants.getRecipeNameFromCommand(message)}")*/
                        it.name[0] == Constants.getRecipeNameFromCommand(message)
                    }
                    if (Constants.getMode(message)) {
                        redirectToCooking()
                        return
                    }
                    if (foundRecipe.isEmpty()) {
                        val foundRecipe1 = Constants.RECIPES.filter {
                            it.name == Constants.getRecipeNameFromCommand(message)
                        }
                        if (foundRecipe1.isNotEmpty()) {
                            findNavController().navigate(R.id.cookingFragment,
                                Constants.getBundleFromCommand(message).apply {
                                    putSerializable(Constants.RECIPE, foundRecipe1[0])
                                    putString(Constants.MAC_ADDRESS, macAddress)
//                                    putString(Constants.RECIPE_LIST, Gson().toJson(listRecipe))
                                    putBoolean(
                                        Constants.RECIPE_LIST,
                                        true
                                    ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                                })
                            return
                        }
                        Toast.makeText(requireContext(), "Recipe Not Found", Toast.LENGTH_SHORT)
                            .show()
                        return
                    }
                    val recentItem = Constants.getRecentItemFromDb(
                        foundRecipe[0]
                    )
                    if (foundRecipe.isNotEmpty())
                        recentItem.recipe = Gson().toJson(foundRecipe[0])
                    findNavController().navigate(R.id.cookingFragment,
                        Constants.getBundleFromCommand(message).apply {
                            putSerializable(Constants.RECIPE, recentItem)
                            putString(Constants.MAC_ADDRESS, macAddress)
                        })
                }
                if (message.uppercase().contains("HOST=")) {
//                    val ftpClient = MyFTPClientFunctions()
//                    CoroutineScope(Dispatchers.IO).launch {
//                        ftpClient.ftpConnect(message.replace("HOST=", ""), "ftp", "ftp", 21)
//                        delay(100)
//                        DebugLog.e("Handler CurrentWorking ${ftpClient.ftpGetCurrentWorkingDirectory()}")
//                        delay(100)
////                        ftpClient.ftpPrintAllFilesList()
//                        delay(500)
//                        DebugLog.e(
//                            "Handler ftpChangeDirectory ${
//                                ftpClient.ftpChangeDirectory(
//                                    "audio"
//                                )
//                            }"
//                        )
//                        delay(100)
////                        ftpClient.ftpPrintAllFilesList()
//
////                        delay(800)
////                        val check = ftpClient.checkFileExists("Water.mp3")
////                        DebugLog.e(
////                            "Handler checkFileExists Operation$check"
////                        )
////                        if (check) {
////                            delay(100)
////
////                            val checkDelete =
////                                ftpClient.mFTPClient.deleteFile("Water.mp3")
////                            DebugLog.e(
////                                "Handler checkDelete Operation$checkDelete"
////                            )
////                        }
////                        delay(200)
////
////                        val check2 = ftpClient.checkFileExists("Vinegar.mp3")
////                        DebugLog.e(
////                            "Handler checkFileExists Operation$check2"
////                        )
////                        if (check2) {
////                            delay(200)
////
////                            val checkDelete =
////                                ftpClient.mFTPClient.deleteFile("Vinegar.mp3")
////
////                            DebugLog.e(
////                                "Handler checkDelete Operation$checkDelete"
////                            )
////                        }
//                        delay(1000)
//                        val root =
//                            File(OnToCookApplication.instance.externalCacheDir, "Files")
//                        DebugLog.e("Checking ${root.exists()}")
//                        val zipFile = File(root, "ChilliPaneerSamosa.mp3")
//                        val inputStream = FileInputStream(zipFile)
//                        val outputFile = File(root, "ChilliPaneerSamosa1.mp3")
//                        val outputStream = FileOutputStream(outputFile)
//                        var read = 0
//                        val maxBufferSize = 1 * 1024 * 1024
//                        val bytesAvailable = inputStream!!.available()
//
//                        //int bufferSize = 1024;
//                        val bufferSize = bytesAvailable.coerceAtMost(maxBufferSize)
//                        val buffers = ByteArray(bufferSize)
//                        while (inputStream.read(buffers).also { read = it } != -1) {
//                            outputStream.write(buffers, 0, read)
//                        }
//                        inputStream.close()
//                        outputStream.close()
//                        delay(3000)
////                        ftpClient.ftpUploadFile(outputFile, "ChilliPaneerSamosa1.mp3")
//                        DebugLog.e("ftpUploadFile Path${outputFile.path}")
//
//                        val status = ftpClient.ftpUpload(
//                            outputFile.path,
//                            "ChilliPaneerSamosa1.mp3",
//                            "audio",
//                            requireContext()
//                        )
//                        DebugLog.e("ftpUploadFile $status")
//
//                        delay(3000)
//                        ftpClient.ftpPrintAllFilesList()
//                    }

                }
//                if (message.lowercase().contains("recipe=")) {
//                    binding.btnCookNow.isEnabled = true
//                    if (!message.contains(",")) {
//                        LoadingUtils.hideDialog()
//                        if (message.lowercase()
//                                .replace("recipe=", "") == "none".lowercase()
//                        ) {
//                            Toast.makeText(
//                                context,
//                                "Please Send Recipe To Device",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                            return
//                        }
//                        var foundRecipe = Constants.RECIPES.filter {
//                            it.name.lowercase() == message.lowercase()
//                                .replace("recipe=", "")
//                        }
//                        if (foundRecipe.isNotEmpty()) {
//                            val bundle = Bundle().apply {
//                                putSerializable(Constants.RECIPE, foundRecipe[0])
//                                putBoolean(Constants.IS_RESUME, false)
//                                putString(Constants.MAC_ADDRESS, macAddress)
//                            }
//                            findNavController().navigate(
//                                R.id.cookingFragment, bundle
//                            )
//                        } else {
//                            var foundRecipe = listRecipe.filter {
//                                Log.e(TAG, "onReceive: ${it.name}")
//                                val nameType =
//                                    object : TypeToken<ArrayList<String>>() {}.type
//                                val nameList: ArrayList<String> =
//                                    Gson().fromJson(it.name, nameType)
//                                nameList[0].lowercase() == message.lowercase()
//                                    .replace("recipe=", "")
//                            }
//                            if (foundRecipe.isNotEmpty()) {
//                                val recipe =
//                                    Constants.convertNewJsonToOldJson(foundRecipe[0])
//                                recentItem!!.recipe = Gson().toJson(recipe)
//                                val bundle = Bundle().apply {
//                                    putSerializable(Constants.RECIPE, recentItem)
//                                    putString(Constants.MAC_ADDRESS, macAddress)
//                                    putBoolean(Constants.IS_RESUME, false)
//                                }
//                                DebugLog.e(macAddress)
//                                findNavController().navigate(
//                                    R.id.cookingFragment, bundle
//                                )
//                            }
//                        }
//                    } else {
//                        val foundRecipe = Constants.RECIPES.filter {
//                            it.name.lowercase() == message.split(",")[0].lowercase()
//                                .replace("recipe=", "")
//                        }
//                        val cmdSize: Int = message.split(",").size
//
//                        val stepNo = if (cmdSize > 2) message.split(",")[2].lowercase()
//                            .replace("stepno=", "") else "0"
//                        LoadingUtils.hideDialog()
//                        if (cmdSize > 1) when (message.split(",")[1].uppercase()
//                            .replace("MODE=", "").uppercase()) {
//                            Constants.INGREDIENT_MODE -> {
//                                if (foundRecipe.isNotEmpty()) {
//                                    val bundle = Bundle().apply {
//                                        putSerializable(
//                                            Constants.RECIPE,
//                                            foundRecipe[0]
//                                        )
//                                        putString(Constants.MAC_ADDRESS, macAddress)
//                                        putBoolean(Constants.IS_RESUME, true)
//                                        putInt(
//                                            Constants.PREPARE_STEP,
//                                            stepNo.toInt() - 1
//                                        )
//                                        putBoolean(Constants.IS_PREPARE_RUNNING, true)
//                                    }
//                                    findNavController().navigate(
//                                        R.id.cookingFragment, bundle
//                                    )
//                                }
//                            }
//
//                            Constants.COOKING_MODE -> {
//                                if (foundRecipe.isNotEmpty()) {
//                                    val indTime = message.split(",")[3].lowercase()
//                                        .replace("ind_run=", "")
//                                    val magTime = message.split(",")[4].lowercase()
//                                        .replace("mag_run=", "")
//                                    val status = message.split(",")[5].lowercase()
//                                        .replace("status=", "")
//                                    val bundle = Bundle().apply {
//                                        putSerializable(
//                                            Constants.RECIPE,
//                                            foundRecipe[0]
//                                        )
//                                        putString(Constants.MAC_ADDRESS, macAddress)
//                                        putBoolean(Constants.IS_RESUME, true)
//                                        putInt(Constants.CURRENT_STEP, stepNo.toInt())
//                                        putInt(
//                                            Constants.CHANGE_TIME,
//                                            indTime.toInt()
//                                                .coerceAtLeast(magTime.toInt())
//                                        )
//                                        putBoolean(Constants.IS_PREPARE_RUNNING, false)
//                                        putBoolean(
//                                            Constants.IS_PLAYING, indTime.toInt()
//                                                .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
//                                        )
//                                    }
//                                    findNavController().navigate(
//                                        R.id.cookingFragment, bundle
//                                    )
//                                }
//                            }
//
//                            else -> {
//                                redirectToCooking()
//                            }
//                        }
//                    }
//                }
                if (message.lowercase().contains("delete=")) {
                    Executors.newSingleThreadExecutor().execute {
                        if (isDeleteRecipeFromMobileDevice) {
                            //-------Delete recipe from local database-------//
                            val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
                            OnToCookApplication.dbInstance.recipeDao().delete(recipe)

                            //-------Delete recently played recipe from local database-------//
                            val recentlyPlayedRecipe =
                                Gson().fromJson(recentItem?.recipe, RecentlyPlayedRecipe::class.java)
                            OnToCookApplication.dbInstance.recentlyPlayedDao()
                                .delete(recentlyPlayedRecipe)
                        }
                        requireActivity().runOnUiThread { /*findNavController().popBackStack()*/
                            //-------When user delete recipe from recipeDetail screen we need to delete that same recipe in RecipeFragment (recipeListing screen)
                            val result = Bundle().apply {
                                putString(Constants.BUNDLE_DELETE_RECIPE, recentItem?.recipe)
                                //-------Delete recipe from device = true (when we need to delete recipe from mobile), when user delete recipe for on2cook only no need to delete recipe from list-------//
                                putBoolean(
                                    Constants.BUNDLE_KEY_DELETE_RECIPE_FROM_DEVICE,
                                    isDeleteRecipeFromMobileDevice
                                )
                            }
                            Toast.makeText(
                                requireActivity(),
                                "Delete From firmware",
                                Toast.LENGTH_SHORT
                            ).show()
                            parentFragmentManager.setFragmentResult(Constants.RESULT_KEY, result)
                            findNavController().navigateUp()
                        }
                    }
                }
                if (message.lowercase() == Constants.IDLE_DEVICE) {
                    redirectToCooking()
                }
                if (message.uppercase() == Constants.RECIPE_EXIST) { //If recipe already exist in On2Cook device, While send recipe
                    LoadingUtils.hideDialog()
                    val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
                    Toast.makeText(
                        context, "${recipe.name[0]} Recipe already exist in On2Cook device", Toast.LENGTH_LONG
                    ).show()
                }
                if (message.uppercase() == "ACK_CANCEL") {
                    LoadingUtils.hideDialog()
                }
                if (message.uppercase() == Constants.MAGQUICKSTART || message.uppercase() == Constants.INDQUICKSTART || (message.uppercase()
                        .contains("INDQUICKSTART=") && Constants.checkNavigation(
                        message
                    ))
                ) {
                    findNavController().popBackStack()
                }
            }

            Constants.FILE_UPLOAD_SUCCESS -> {
                Toast.makeText(
                    context, "File Uploaded Successfully", Toast.LENGTH_LONG
                ).show()
                LoadingUtils.hideDialog()
//                DialogUtils().commonDialog(
//                    requireContext(),
//                    "Would you like to send audio files",
//                    "", getString(R.string.button_cancel),
//                    getString(R.string.button_yes),
//                    true,
//                    isCancelable = false,
//                    callbackSuccess = {
////                        startHotspot()
//                    }, callbackNegative = {
//
//                    }
//                )

            }
        }
    }

    private fun startHotspot() {

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
                    DebugLog.e("Permission")
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
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    reservationG = reservation
                    DebugLog.e("onStarted: $reservation")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.preSharedKey}")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.hiddenSSID}")
                    DebugLog.e("onStarted: ${reservation.wifiConfiguration?.SSID}")
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
//                            .setSsid("AndroidShare_9999")
//                            .setWpa2Passphrase("in265gpz23nd445")
//                            .build()
//                        val networkRequest: NetworkRequest = NetworkRequest.Builder()
//                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//                            .setNetworkSpecifier(wifiNetworkSpecifier)
//                            .build()
//                        val connectivityManager = requireContext().getSystemService(
//                            Context.CONNECTIVITY_SERVICE
//                        ) as ConnectivityManager
//                        connectivityManager.requestNetwork(networkRequest, object :
//                            NetworkCallback() {
//                            override fun onAvailable(network: Network) {
//                                super.onAvailable(network)
//                                DebugLog.e("requestNetwork onAvailable $network")
//                            }
//
//                            override fun onLosing(network: Network, maxMsToLive: Int) {
//                                super.onLosing(network, maxMsToLive)
//                                DebugLog.e("requestNetwork onLosing $network")
//
//                            }
//
//                            override fun onLost(network: Network) {
//                                super.onLost(network)
//                                DebugLog.e("requestNetwork onLost $network")
//                            }
//
//                            override fun onUnavailable() {
//                                super.onUnavailable()
//                                DebugLog.e("requestNetwork onUnavailable ")
//                            }
//
//                            override fun onCapabilitiesChanged(
//                                network: Network,
//                                networkCapabilities: NetworkCapabilities
//                            ) {
//                                super.onCapabilitiesChanged(network, networkCapabilities)
//                                DebugLog.e("requestNetwork onCapabilitiesChanged $network")
//                            }
//
//                            override fun onLinkPropertiesChanged(
//                                network: Network,
//                                linkProperties: LinkProperties
//                            ) {
//                                super.onLinkPropertiesChanged(network, linkProperties)
//                                DebugLog.e("requestNetwork onLinkPropertiesChanged $network")
//                            }
//
//                            override fun onBlockedStatusChanged(
//                                network: Network,
//                                blocked: Boolean
//                            ) {
//                                super.onBlockedStatusChanged(network, blocked)
//                                DebugLog.e("requestNetwork onBlockedStatusChanged $network")
//                            }
//                        })
//
//                    } else {

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
            }, Handler(Looper.getMainLooper()))

        }
    }

    private fun prepareView() {
        val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
        if (recipe.imageUrl.isNotEmpty()) {
            Glide.with(this).load(Uri.parse(recipe.imageUrl)).into(binding.ivRecipeImage)
        } else {
            binding.ivRecipeImage.setImageResource(recentItem!!.image)
        }
        binding.tvName.text = recentItem!!.name
        binding.tvDesc.text = recentItem!!.desc
        binding.tvDifficulty.text = recentItem!!.difficulty
        try {
            binding.tvTime.text =
                "${recentItem?.duration?.let { DateTimeHelper.getTimeInMinSec(it.toInt()) }} mins"
        } catch (e: Exception) {
            DebugLog.e("Error ${e.message}")
        }

        when (binding.tvDifficulty.text.toString().lowercase()) {
            "medium" -> {
                binding.tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            requireContext(), R.color.orange
                        ), BlendModeCompat.SRC_IN
                    )
            }

            "hard" -> {
                binding.tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            requireContext(), R.color.colorFlamColor
                        ), BlendModeCompat.SRC_IN
                    )
            }

            "easy" -> {
                binding.tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            requireContext(), R.color.colorMWColor
                        ), BlendModeCompat.SRC_IN
                    )
            }
        }
    }

    private fun openDialog() {
        val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
        val recipeToSend = Constants.convertRecipeDbToNewJson(recipe)
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(Gson().fromJson(recipeToSend, RecipeNew::class.java))
        val previewDialog = PreviewDialog(json)
        previewDialog.show(childFragmentManager, "")
    }

    fun redirectToCooking() {
        if (Constants.IS_PRODUCTION_MODE) {
            Handler(Looper.getMainLooper()).postDelayed({
                DebugLog.e("${recentItem?.name}")
                service.writeData(
                    macAddress, ("recipe=" + recentItem?.name.toString()).toByteArray(
                        Charsets.UTF_8
                    )
                )
                LoadingUtils.showLoading(context, true, "Please Wait")
            }, 100)
        }
    }

    private fun toggleDeviceScanDialog(isShow: Boolean = false) {
        if (deviceScanDialog != null) {
            deviceScanDialog!!.dismiss()
            deviceScanDialog = null
            return
        }
        if (isShow) {
            deviceScanDialog = DeviceScanDialog()
            deviceScanDialog!!.context = requireActivity() as AppCompatActivity
            deviceScanDialog!!.show(childFragmentManager, "")
        }
    }

    fun setTitle(name: String) {
        binding.tvPageTitle.text = name
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: RecentItem, param2: String) = RecipeDetailFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_PARAM1, param1)
                putString(ARG_PARAM2, param2)
            }
        }
    }
}