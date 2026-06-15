package com.invent.ontocook.multiple_connection.ui

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import android.app.DownloadManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.AddedRecipeAdapter
import com.invent.ontocook.adapter.RecentItemListAdapter
import com.invent.ontocook.adapter.RecommendItemListAdapter
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.databinding.FragmentRecipeListBinding
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.dialog.FTPDialog
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.extension.openWifiPermissionSettings
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.extension.viewShow
import com.invent.ontocook.floatingsearch.RxSearchObservable
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.IngredientsSteps
import com.invent.ontocook.models.Modes
import com.invent.ontocook.models.QuickAccess
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.service.BleService
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.multiple_connection.util.Suggestion
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import com.invent.ontocook.utils.SharedPreferencesManager
import com.invent.ontocook.utils.convertStringToInt
import com.invent.ontocook.utils.getTextFromFile
import com.invent.ontocook.utils.getZipFileFromFiles
import com.invent.ontocook.utils.gone
import com.invent.ontocook.utils.goneIfOrVisible
import com.invent.ontocook.utils.makeFileCopyInCacheDir
import com.invent.ontocook.utils.moveFile
import com.invent.ontocook.utils.notNullAndNotEmpty
import com.invent.ontocook.utils.onTextChange
import com.invent.ontocook.utils.putEnum
import com.invent.ontocook.utils.shareZipFile
import com.invent.ontocook.utils.visible
import com.invent.ontocook.utils.visibleIfOrGone
import com.invent.ontocook.utils.withNotNull
import com.opencsv.CSVWriter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_cooking.floatingSearchView
import kotlinx.android.synthetic.main.activity_cooking.tvMag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val ARG_PARAM1 = Constants.MAC_ADDRESS
private const val ARG_PARAM2 = "param2"

class RecipeFragment : Fragment() {

    //-------Indicate user screen is initialized or not-------//
    //-------set it true when user come first time (For restrict initialise user come back from another fragment)-------//
    private var isViewCreated: Boolean = false

    private var macAddress: String = ""
    private var ssid: String = ""
    private var pass: String = ""
    private var param2: String? = null
    private var recipeFragmentScreenFlowType: String? = null

    private lateinit var communicationReceiver: BroadcastReceiver
    private lateinit var binding: FragmentRecipeListBinding

    private var fabOpen: Animation? = null
    private var fabClose: Animation? = null
    private var fabClock: Animation? = null
    private var fabAntiClock: Animation? = null
    private lateinit var service: BleService
    private var quickAccessList = mutableListOf<QuickAccess>()
    private var magTimer: CountDownTimer? = null
    private var indTimer: CountDownTimer? = null
    private var quickFryModeTimer: CountDownTimer? = null
    private var quickFryModeMicrowaveTimer: CountDownTimer? = null
    val totalFourHoursInMillis: Long =
        2 * 60 * 60 * 1000 // 4 hours in milliseconds, for quick fry mode

    //    val totalFourHoursInMillis: Long = 10 * 1000 // 4 hours in milliseconds, for quick fry mode
    var currentTimeOfFryMode: Long = 0

    //-------We have to plus minus timer before start microwave-------//
    var fryModeMicrowaveTimeLocalTime: Long = 30

    //-------Fry mode power saving time in minute- Default is 30-------//
    var fryModePowerSavingTime: Long = 30

    lateinit var reservationG: WifiManager.LocalOnlyHotspotReservation
    private var zipFileShare = ArrayList<File>()
    private var stirrerOn = false
    private var isQuickFryModeOn = false
    private var fryModeStirrerOn = false
    private var stirrerOnForPump = false
    private var selectedPosition = 0

    //-------Store selected position of oil level toggle, for functionality(User can not change oil level after start fry mode-------//
    //-------As of now this feature(Oil volume level) is removed by client-------//
    var selectedOilLevelPosition: Int = 0

    private var fryModeStirrerSelectedPosition = 0
    private var pumpOn = false
    private var purgeOn = false

    //var recentItemList = mutableListOf<RecentItem>()
    private var modesList = mutableListOf<Modes>()
    private var deviceScanDialog: DeviceScanDialog? = null
    lateinit var broadcastReceiver: BroadcastReceiver
    private var recommendedItems = mutableListOf<RecentItem>()
    private var allRecipeItems = mutableListOf<RecentItem>()
    private var listRecipeForSearch = mutableListOf<Recipe>()
    private var indStatus = Constants.Status.DEFAULT
    private var magStatus = Constants.Status.DEFAULT

    //--------BELOW STATUS FOR FRY MODE-----//
    private var fryModeMagStatus = Constants.Status.DEFAULT
    private var fryModeStatus = Constants.Status.DEFAULT
    private var fryModeIndStatus = Constants.Status.DEFAULT

    private var checkStatus = Constants.CheckStatus.DEFAULT
    private lateinit var selectedType: Constants.AUDIO_TYPE
    private var selectedZipType = ""

    //-------RecyclerView Scroll-------//
    private val SCROLL_DIRECTION_RIGHT = 1
    private val SCROLL_DIRECTION_LEFT = -1
    private val RecyclerView.canScrollRight: Boolean
        get() = canScrollHorizontally(SCROLL_DIRECTION_RIGHT)
    private val RecyclerView.canScrollLeft: Boolean
        get() = canScrollHorizontally(SCROLL_DIRECTION_LEFT)

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder) {
            service = (iBinder as BleService.LocalBinder).getService()
            if (service.isDeviceConnected(macAddress))
                checkStatus(Constants.CheckStatus.DEFAULT)
//                service.writeData(
//                    macAddress,
//                    Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
//                )
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
        }
    }

    private var qrCodeScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intentResult = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            if (intentResult.contents != null) {
                val scannedContent = intentResult.contents

                // Add a check for network connectivity here
                if (!isNetworkAvailable()) {
                    Toast.makeText(requireContext(), "No internet access. Cannot download file.", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult // Stop further execution
                }

                if (scannedContent.startsWith("http://") || scannedContent.startsWith("https://")) {
                    try {
                        val uri = Uri.parse(scannedContent)
                        val request = DownloadManager.Request(uri)
                            .setTitle(uri.lastPathSegment ?: "Downloading File")
                            .setDescription("Downloading file from QR code link...")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.lastPathSegment)

                        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)

                        Toast.makeText(
                            requireContext(),
                            "Download Started.",
                            Toast.LENGTH_LONG
                        ).show()

                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to start download: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Handle non-URL content
                    Toast.makeText(
                        requireContext(),
                        "Scanned content: $scannedContent",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Add this function to check for network availability
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private var onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val downloadedUri = cursor.getString(uriIndex)

                    // Check if the downloaded file is a ZIP file
                    if (downloadedUri.endsWith(".zip", ignoreCase = true)) {
                        // Start the unzipping process
                        Toast.makeText(
                            requireContext(),
                            "Recipe Downloaded on On2Cook",
                            Toast.LENGTH_LONG
                        ).show()
                        unzipDownloadedFile(Uri.parse(downloadedUri))
                    } else {
                        // Handle other file types or show a normal success message
                        Toast.makeText(
                            requireContext(),
                            "File downloaded successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            cursor.close()
        }
    }
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        requireContext().registerReceiver(onDownloadComplete, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(onDownloadComplete)
    }
    private fun unzipDownloadedFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val path = makeFileCopyInCacheDir(requireContext(), uri)
            val rootDes = File(requireContext().externalCacheDir, "Temp")
            if (!rootDes.exists()) {
                rootDes.mkdir()
            }
            val file = File(path)
            requireActivity().runOnUiThread {
                LoadingUtils.showLoading(requireContext(), false)
            }
            val externalStorageDir = requireContext().filesDir
            val folder = File(externalStorageDir, "OnToCook")
            val root = File(requireContext().externalCacheDir, "Recipes")

            if (!root.exists()) {
                root.mkdirs()
            }
            val subRoot = File(folder, file.nameWithoutExtension)
            if (!subRoot.exists()) {
                subRoot.mkdirs()
            }

            unZipNew(file, subRoot.path).also {
                requireActivity().runOnUiThread {
                    LoadingUtils.hideDialog()
                    refreshAllRecipeList()
                }
            }
        }
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent = result.data!!
                data.data?.also { uri ->
                    //Permission needed if you want to retain access even after reboot
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    // Perform operations on the document using its URI.
                    if (selectedZipType == Constants.AUDIO) {
                        CoroutineScope(Dispatchers.IO).launch {
                            requireActivity().runOnUiThread {
                                LoadingUtils.showLoading(requireContext(), false)
                            }
                            val fileName = Constants.getFileName(
                                requireContext(), uri = uri
                            )
                            DebugLog.e("position  $fileName")
                            val path = makeFileCopyInCacheDir(requireContext(), uri)
                            val file = File(path)

                            val externalStorageDir = requireContext().filesDir

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
                                requireActivity().runOnUiThread {
                                    LoadingUtils.hideDialog()
                                }
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            val path = makeFileCopyInCacheDir(requireContext(), uri)
                            val rootDes = File(requireContext().externalCacheDir, "Temp")
                            if (!rootDes.exists()) {
                                rootDes.mkdir()
                            }
                            val file = File(path)
                            requireActivity().runOnUiThread {
                                LoadingUtils.showLoading(requireContext(), false)
                            }
                            val externalStorageDir = requireContext().filesDir

                            // Create a new folder in the external storage directory with the name of your app.

                            // Create a new folder in the external storage directory with the name of your app.
                            val folder = File(externalStorageDir, "OnToCook")

                            val root = File(requireContext().externalCacheDir, "Recipes")

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
                                requireActivity().runOnUiThread {
                                    LoadingUtils.hideDialog()
                                    //-------After upload new recipe need to refresh allRecipeList-------//
                                    refreshAllRecipeList()
                                    /*refreshRecommendedList()
                                    refreshRecentItemList()*/
                                }
                            }
                        }
                    }

//                    Thread {


//                        val externalStorageDir = Environment.getExternalStorageDirectory()
//
//                        // Create a new folder in the external storage directory with the name of your app.
//
//                        // Create a new folder in the external storage directory with the name of your app.
//                        val folder = File(externalStorageDir, "YourApp")
//
//                        // If the folder does not exist, create it.
//
//                        // If the folder does not exist, create it.
//                        if (!folder.exists()) {
//                            folder.mkdirs()
//                        }
//                    }.start()
                }
            }
        }

    private var createNewRecipeResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.e("PrinceEWW>>>", "RESULT_OK - New Recipe Created")
                //-------New recipe created, so need to refresh allRecipeList-------//
                refreshAllRecipeList()
                /*refreshRecommendedList()
                refreshRecentItemList()*/
            }
        }

    //-------All recipe pagination - without paging 3-------//
    private var currentPageAllRecipe = Constants.DEFAULT_ZERO
    private var isFetchingData = false
    private var loadingAllRecipe = true
    private var loadingPaginationAllRecipe = true

    //-------All Recipes Adapter-------//
    var allRecipesTotalCount: Int = Constants.DEFAULT_ZERO //For pagination we need total count
    private var allRecipeList: MutableList<Recipe> = mutableListOf()
    private val allRecipeAdapter: RecommendItemListAdapter by lazy {
        RecommendItemListAdapter(allRecipeList) { position: Int, recipe: Recipe ->
//            DebugLog.e("onItemClick:allRecipeAdapter ${Gson().toJson(allRecipeAdapter.getList())}")
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
            recipeItem.id = recipe.id
            recipeItem.recipe = Gson().toJson(recipe, Recipe::class.java)
            recipeItem.desc = recipe.description
            recipeItem.duration = recipe.Instruction.sumOf { it -> it.durationInSec }.toString()
            val bundle = Bundle().apply {
                putSerializable(Constants.RECIPE, recipeItem)
//                putString(Constants.RECIPE_LIST, Gson().toJson(allRecipeAdapter.getList()))
                putBoolean(
                    Constants.RECIPE_LIST,
                    true
                ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                putString(
                    Constants.RANDOM_RECIPE_LIST,
                    Gson().toJson(recommendItemListAdapter.getList())
                )
                putString(Constants.MAC_ADDRESS, macAddress)
                putBoolean(Constants.IS_SHOW, true)
            }
            findNavController().navigate(
                R.id.recipeDetailFragment, bundle
            )
        }
    }

    //-------Recommended Item Adapter-------//
    private var recommendRecipeList: MutableList<Recipe> = mutableListOf()
    private val recommendItemListAdapter: RecommendItemListAdapter by lazy {
        RecommendItemListAdapter(recommendRecipeList) { i, recipe ->
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
            recipeItem.id = recipe.id
            recipeItem.recipe = Gson().toJson(recipe, Recipe::class.java)
            recipeItem.desc = recipe.description
            recipeItem.duration = recipe.Instruction.sumOf { it -> it.durationInSec }.toString()
            val bundle = Bundle().apply {
                putSerializable(Constants.RECIPE, recipeItem)
//                putString(Constants.RECIPE_LIST, Gson().toJson(allRecipeAdapter.getList()))
                putBoolean(
                    Constants.RECIPE_LIST,
                    true
                ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                putString(
                    Constants.RANDOM_RECIPE_LIST,
                    Gson().toJson(recommendItemListAdapter.getList())
                )
                putString(Constants.MAC_ADDRESS, macAddress)
                putBoolean(Constants.IS_SHOW, true)
            }
            findNavController().navigate(
                R.id.recipeDetailFragment, bundle
            )
        }
    }

    //-------Recent Item Adapter-------//
    private val recentItemListAdapter: RecentItemListAdapter by lazy {
        RecentItemListAdapter(object : RecentItemListAdapter.ItemClickListener {
            override fun onItemClick(
                position: Int,
                recentlyPlayedRecipe: RecentlyPlayedRecipe,
            ) {
                var name = ""
                if (recentlyPlayedRecipe.name.isNotEmpty()) {
                    name = recentlyPlayedRecipe.name[0]
                }
                val recipeItem = RecentItem(
                    recentlyPlayedRecipe.id,
                    R.drawable.ic_mutton,
                    name,
                    recentlyPlayedRecipe.difficulty,
                    "9 mins",
                    "Indian|Snacks"
                )
                recentlyPlayedRecipe.Ingredients.forEachIndexed { index, ingredients ->
                    val step = IngredientsSteps(
                        ingredients.image,
                        ingredients.title,
                        ingredients.weight,
                        ingredients.text
                    )
                    recipeItem.ingredients.add(index, step)
                }
                recipeItem.name = name


                val recipe = Recipe()
                recipe.name = recentlyPlayedRecipe.name
                recipe.audio1 = recentlyPlayedRecipe.audio1
                recipe.audio2 = recentlyPlayedRecipe.audio2
                recipe.id = recentlyPlayedRecipe.id
                recipe.Ingredients = recentlyPlayedRecipe.Ingredients
                recipe.Instruction = recentlyPlayedRecipe.Instruction
                recipe.description = recentlyPlayedRecipe.description
                recipe.tags = recentlyPlayedRecipe.tags
                recipe.category = recentlyPlayedRecipe.category
                recipe.subCategories = recentlyPlayedRecipe.subCategories
                recipe.imageUrl = recentlyPlayedRecipe.imageUrl
                recipeItem.recipe = Gson().toJson(recipe, Recipe::class.java)
                recipeItem.desc = recentlyPlayedRecipe.description
                recipeItem.duration =
                    recipe.Instruction.sumOf { it -> it.durationInSec }.toString()
                val bundle = Bundle().apply {
                    putSerializable(Constants.RECIPE, recipeItem)
//                    putString(Constants.RECIPE_LIST, Gson().toJson(allRecipeAdapter.getList()))
                    putBoolean(
                        Constants.RECIPE_LIST,
                        true
                    ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                    putString(
                        Constants.RANDOM_RECIPE_LIST,
                        Gson().toJson(recommendItemListAdapter.getList())
                    )
                    putString(Constants.MAC_ADDRESS, macAddress)
                    putBoolean(Constants.IS_SHOW, true)
                }
                findNavController().navigate(
                    R.id.recipeDetailFragment, bundle
                )
            }
        })
    }

    private val recentAddedAdapter: AddedRecipeAdapter by lazy {
        AddedRecipeAdapter(object : AddedRecipeAdapter.ItemClickListener {
            override fun onItemClick(position: Int, recipe: Recipe) {
//                DebugLog.e("onItemClick:AddedRecipeAdapter ${allRecipeAdapter.getList()}")
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
                recipeItem.duration = recipe.Instruction.sumOf { it -> it.durationInSec }.toString()
                val bundle = Bundle().apply {
                    putSerializable(Constants.RECIPE, recipeItem)
//                    putString(Constants.RECIPE_LIST, Gson().toJson(allRecipeAdapter.getList()))
                    putBoolean(
                        Constants.RECIPE_LIST,
                        true
                    ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
                    putString(
                        Constants.RANDOM_RECIPE_LIST,
                        Gson().toJson(recommendItemListAdapter.getList())
                    )
                    putString(Constants.MAC_ADDRESS, macAddress)
                    putBoolean(Constants.IS_SHOW, true)
                }
                findNavController().navigate(
                    R.id.recipeDetailFragment, bundle
                )
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.e("init onCreate")
        allRecipeList.clear()
        //-------fragment result launcher - PrinceEWW-------//
        //-------When user delete recipe from recipeDetail screen we need to delete that same recipe in RecipeFragment (recipeListing screen)
        setFragmentResultListener(Constants.RESULT_KEY) { _, result ->
            Log.e("PrinceEWW>>>", "RESULT_OK - setFragmentResultListener() - onCreate")
            if (result.getBoolean(Constants.IS_EDIT)) {
                //-------After edit new Recipe, refresh allRecipeList, recommendedRecipeList and RecentItemList-------//
                refreshAllRecipeList()
                refreshRecommendedList()
                refreshRecentItemList()
            } else {
                //-------Delete recipe from device = true (when we need to delete recipe from mobile), when user delete recipe for on2cook only no need to delete recipe from list-------//
                val deleteRecipeFromList =
                    result.getBoolean(Constants.BUNDLE_KEY_DELETE_RECIPE_FROM_DEVICE)
                //-------Recipe we need to delete-------//
                val recipeString = result.getString(Constants.BUNDLE_DELETE_RECIPE)
                val recipe = Gson().fromJson(recipeString, Recipe::class.java)
                if (deleteRecipeFromList) {
                    var findRecipeFromAllRecipeList: Recipe?
                    var findRecipeFromRecommendedRecipeList: Recipe?
                    var findRecentlyPlayedRecipeFromRecentlyPlayedList: RecentlyPlayedRecipe?
                    CoroutineScope(Dispatchers.Main).launch {
                        //-------Find recipe from list on IO thread and remove from list and notify adapter on Main thread-------//
                        withContext(Dispatchers.IO) {
                            //-------find Recipe for delete from allRecipeList-------//
                            findRecipeFromAllRecipeList = allRecipeList.find { it.id == recipe.id }
                            //-------find recipe for delete for recommendedList-------//
                            findRecipeFromRecommendedRecipeList =
                                recommendRecipeList.find { it.id == recipe.id }
                            findRecentlyPlayedRecipeFromRecentlyPlayedList = recentItemListAdapter.getItemList().withNotNull {recentlyPlayedRecipesList ->
                                recentlyPlayedRecipesList.find {
                                    it.id == recipe.id
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Log.e("PrinceEWW>>>", "recipe: $recipe")
                            Log.e("PrinceEWW>>>", "findRecentlyPlayedRecipeFromRecentlyPlayedList: $findRecentlyPlayedRecipeFromRecentlyPlayedList")
                            //-------Remove recipe from allRecipeList-------//
                            findRecipeFromAllRecipeList.withNotNull {
                                val removeItemPosition =
                                    allRecipeList.indexOf(findRecipeFromAllRecipeList)
                                Log.e(
                                    "PrinceEWW>>>",
                                    " removeItemPosition of allRecipeList: ${removeItemPosition}"
                                )
                                allRecipesTotalCount-- //Need to minus count of all recipe, otherwise, loader will be show in bottom for pagination
                                allRecipeList.removeAt(removeItemPosition)
                                allRecipeAdapter.notifyItemRemoved(removeItemPosition)
                            }
                            //-------Remove recipe from recommendedRecipeList-------//
                            findRecipeFromRecommendedRecipeList.withNotNull {
                                val removeItemPosition =
                                    recommendRecipeList.indexOf(findRecipeFromRecommendedRecipeList)
                                Log.e(
                                    "PrinceEWW>>>",
                                    " removeItemPosition of recommendRecipeList: ${removeItemPosition}"
                                )
                                recommendRecipeList.removeAt(removeItemPosition)
                                recommendItemListAdapter.notifyItemRemoved(removeItemPosition)
                            }
                            //-------Remove recently played recipe from recently played recipe list/adapter-------//
                            findRecentlyPlayedRecipeFromRecentlyPlayedList.withNotNull {
                                val removeItemPosition =
                                    recentItemListAdapter.getItemList().indexOf(findRecentlyPlayedRecipeFromRecentlyPlayedList)
                                Log.e(
                                    "PrinceEWW>>>",
                                    " removeItemPosition of recentlyPlayedList/Adapter: ${removeItemPosition}"
                                )
//                                recentItemListAdapter.getItemList().removeAt(removeItemPosition)
                                recentItemListAdapter.notifyItemRemoved(removeItemPosition)
                            }
                        }
                    }
                }
            }

        }
        arguments?.let {
            macAddress = it.getString(ARG_PARAM1, "")
            param2 = it.getString(ARG_PARAM2)
            recipeFragmentScreenFlowType = it.getString(Constants.recipeFragmentScreenFlowType)
        }
        if (macAddress == Constants.DummyMacAddress)
            if (Constants.IS_TABLET)
                (activity as HomeTvActivity).changeDummyMac.observe(
                    requireActivity(),
                    androidx.lifecycle.Observer {
                        DebugLog.e("changeDummyMac $it")
                        macAddress = it
                    })
            else (activity as HomeActivity).changeDummyMac.observe(
                requireActivity(),
                androidx.lifecycle.Observer {
                    DebugLog.e("changeDummyMac $it")
                    macAddress = it
                })

        OnToCookApplication.dbInstance.recipeDao().getAllRecipe1().subscribe({
            listRecipeForSearch.clear()
            listRecipeForSearch.addAll(it.sortedBy { it -> it.name[0] })
        }, {

        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        DebugLog.e("onDestroy View")
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.e("onDestroy ")
        if (Constants.IS_TABLET)
            (activity as HomeTvActivity).changeDummyMac.removeObservers(this)
        else
            (activity as HomeActivity).changeDummyMac.removeObservers(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        Log.e("PrinceEWW>>>", "onCreateView")
        if (this::binding.isInitialized) {
            binding
        } else {
            binding = DataBindingUtil.inflate(
                layoutInflater, R.layout.fragment_recipe_list, container, false
            )
            binding.lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.e("PrinceEWW>>>", "onViewCreated")

        if (!isViewCreated) {
            //-------Set "isViewCreated" = "true", for restrict again initialize call on back press from another fragment-------//
            isViewCreated = true
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                } else {
                    ActivityCompat.requestPermissions(
                        requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101
                    )
                }
            }

            init()
            initListener()

            if (macAddress == Constants.DummyMacAddress)
                OnToCookApplication.dbInstance.recentlyPlayedDao().getAllRecipe().subscribe({
                    if (it.isNotEmpty()) {
                        requireActivity().runOnUiThread {
                            binding.clRecentAct.viewShow()
                            recentItemListAdapter.setRecentItemList(it)
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            binding.clRecentAct.viewGone()
                        }
                    }
                }, {

                })
            else
                OnToCookApplication.dbInstance.recentlyPlayedDao().getAllRecipe(macAddress)
                    .subscribe({
                        if (it.isNotEmpty()) {
                            requireActivity().runOnUiThread {
                                binding.clRecentAct.viewShow()
                                recentItemListAdapter.setRecentItemList(it)
                            }
                        } else {
                            requireActivity().runOnUiThread { binding.clRecentAct.viewGone() }
                        }
                    }, {

                    })
        }
    }

    @SuppressLint("CheckResult")
    private fun init() {
//        allRecipeList.clear()
//        currentPageAllRecipe = -1
        //-------For smooth scroll of nested scrollView-------//
        binding.nestedScroll.fling(0)
        binding.nestedScroll.smoothScrollBy(0, 0)

        //-------On init change UI according screen flow type-------//
        changeUiAccordingScreenFlowtype()

        Log.e("PrinceEWW>>>", "allRecipeList.size: ${allRecipeList.size}")
        //-------Set adapters-------//
        binding.reRecommend.adapter = recommendItemListAdapter
        binding.reAllRecipe.adapter = allRecipeAdapter
        binding.reRecentItems.adapter = recentItemListAdapter
        binding.reRecentAddedItems.adapter = recentAddedAdapter

        binding.btnAction.goneIfOrVisible(Constants.IS_TABLET)
        DebugLog.e("init difficultyLevels")

        //-------By default, clAllRecipe and progressbar of all recipe should be hide-------//
        binding.clAllRecipe.gone()
        changeAllRecipePaginationProgressBarStatus(visibleProgressBar = false)

        //-------Set animations-------//
        fabClose = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_close)
        fabOpen = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_open)
        fabClock = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_rotate_clock)
        fabAntiClock = AnimationUtils.loadAnimation(requireContext(), R.anim.fab_rotate_anticlock)

        //-------On init we have to fetch power saving time of fry mode from preferences, (default is 30)-------//
        val getPowerTimeFromPref = SharedPreferencesManager.retriveData(
            requireActivity(),
            Constants.PREFFRYMODEPOWERSAVINGTIME
        ) ?: "30"
        if (getPowerTimeFromPref.notNullAndNotEmpty()) {
            fryModePowerSavingTime = getPowerTimeFromPref.toLong()
        }
        binding.etFryModePowerSavingTime.setText(fryModePowerSavingTime.toString())

        //-------Toggle of Stirrer difficulty level-------//
        var difficultyLevels = arrayListOf<String>("Low", "Med", "High", "V High")
        binding.toggleDifficultyLevel.setLabels(difficultyLevels)
        binding.toggleDifficultyLevel.setOnToggleSwitchChangeListener { position, isChecked ->
            if (selectedPosition != position && isChecked && stirrerOn && service.isDeviceConnected(
                    macAddress
                )
            ) {
                DebugLog.e("CheckCommand ${getCommandFromSelection(binding.toggleDifficultyLevel.checkedTogglePosition)}")
                service.writeData(
                    macAddress,
                    getCommandFromSelection(binding.toggleDifficultyLevel.checkedTogglePosition).toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
        }

        //-------As of now this feature(Oil volume level) is removed by client-------//
        /*//-------Toggle of Oil level-------//
        var oilLevels = arrayListOf<String>("1.8", "2", "2.5")
        binding.toggleSwitchFryModeOilLevel.setLabels(oilLevels)
        //-------According requirement, user can change oil level after start fry mode-------//
        binding.toggleSwitchFryModeOilLevel.setOnToggleSwitchChangeListener { position, isChecked ->
            if (fryModeStatus == Constants.Status.START) {
                if (selectedOilLevelPosition != position) {
                    binding.toggleSwitchFryModeOilLevel.checkedTogglePosition =
                        selectedOilLevelPosition
                    Toast.makeText(
                        requireActivity(),
                        getString(R.string.please_stop_fry_mode_for_Stop_oil_level),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                selectedOilLevelPosition = position
            }
        }*/

        //-------Toggle of Fry Mode Stirrer level-------//
        var fryModeStirrerLevels = arrayListOf<String>("Low", "Med", "High", "V High")
        binding.toggleSwitchFryModeStirrerLevel.setLabels(fryModeStirrerLevels)
        binding.toggleSwitchFryModeStirrerLevel.setOnToggleSwitchChangeListener { position, isChecked ->
            if (fryModeStirrerSelectedPosition != position && isChecked && fryModeStirrerOn && service.isDeviceConnected(
                    macAddress
                )
            ) {
                DebugLog.e("CheckCommand ${getCommandFromSelection(binding.toggleSwitchFryModeStirrerLevel.checkedTogglePosition)}")
                service.writeData(
                    macAddress,
                    getCommandFromSelection(binding.toggleSwitchFryModeStirrerLevel.checkedTogglePosition).toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
        }

        //-------Get total recipes count - for pagination-------//
        CoroutineScope(Dispatchers.Main).launch {
            allRecipesTotalCount = withContext(Dispatchers.IO) { getAllRecipeTotalCount() }

            //-------Get All recipes from localDatabase if "allRecipesTotalCount" == "0" && "allRecipeList" isEmpty-------//
            if (allRecipeList.isEmpty() && allRecipesTotalCount == Constants.DEFAULT_ZERO) {
                Log.e("PrinceEWW>>>", "If condition")

            } else {
                Log.e("PrinceEWW>>>", "else condition")
                getAllRecipesWithPagination()
            }
        }

        /*//-------Get All recipes from localDatabase if "allRecipesTotalCount" == "0" && "allRecipeList" isEmpty-------//
        if (allRecipeList.isEmpty() && allRecipesTotalCount == Constants.DEFAULT_ZERO) {
            getAllRecipesWithPagination()
            Log.e("PrinceEWW>>>", "If condition")
        } else {
            binding.clAllRecipe.viewShow()
            Log.e("PrinceEWW>>>", "else condition")
        }*/

        if (recommendRecipeList.isEmpty())
            Executors.newSingleThreadExecutor().execute {
                val recipeDbList = OnToCookApplication.dbInstance.recipeDao().getRandomRecipe()
                requireActivity().runOnUiThread {
                    Log.e("TAG", "getRandomRecipe: ${recipeDbList.size}")
                    if (recipeDbList.isNotEmpty()) {
                        binding.clRecommend.viewShow()
                        recommendItemListAdapter.addAll(recipeDbList)
                    } else {
                        binding.clRecommend.viewGone()
                    }
                }
            }
        else {
            binding.clRecommend.viewShow()
        }

        RxSearchObservable.fromView(binding.floatingSearchView)
            ?.debounce(500, TimeUnit.MILLISECONDS)?.filter(Predicate {
                println("call api  Predicate---   $it")
                return@Predicate it.isNotEmpty()
            })?.distinctUntilChanged()?./*switchMap {
                println("call api  ---   $it")
//                val list = listRecipe.filter { recipe ->
//                    recipe.name[0].lowercase() == it
//                }
//                Rx2AndroidNetworking.get(Constants.RECIPE_LISTING_URL + "&query=${it}").build()
//                    .getObjectListObservable(RecipeList::class.java).subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
            }?.*/subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({
                println("call api  ---   $it")

                binding.floatingSearchView.clearSuggestions()
                val listOfSuggestion = mutableListOf<Suggestion>()
                println("call api  ---   ${listRecipeForSearch.size}")

                val list = listRecipeForSearch.filter { recipe ->
                    println("call api  -filter--   ${recipe.name[0].lowercase()}")
                    println("call api  -filter--   ${it}")
                    recipe.name[0].contains(it!!, true)
                }
                println("call api  ---   ${list.size}")

                for (list in list) {
                    listOfSuggestion.add(Suggestion(list.id, list.name[0]))
                    println("recipe listing   ${list.id}   ${list.name[0]}")
                }
                binding.floatingSearchView.swapSuggestions(listOfSuggestion)
            }, {
                println("Error   ${it.localizedMessage}")
            })

        //Unused Code
        /*
                prepareQuickAccessData()

                binding.reQuickAccess.layoutManager = GridLayoutManager(requireContext(), 2)
                quickAccessListAdapter = QuickAccessListAdapter()
                binding.reQuickAccess.adapter = quickAccessListAdapter
                quickAccessListAdapter.setQuickAccessList(quickAccessList)
        */


        //Unused Code
        /*
                prepareModes()

                binding.reModes.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                modesItemListAdapter = ModesItemListAdapter()
                binding.reModes.adapter = modesItemListAdapter
                modesItemListAdapter.setModesList(modesList)
        */
    }

    private fun initListener() {
        binding.constraintLayoutMenuBackground.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_CANCEL ||event.action == MotionEvent.ACTION_UP) {
                toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        binding.nestedScroll.viewTreeObserver.addOnScrollChangedListener {
            val view = binding.nestedScroll.getChildAt(binding.nestedScroll.childCount - 1) as View
            val preloadThreshold = 5000 // Adjust this value based on your preference
            val diff: Int =
                view.bottom - (binding.nestedScroll.height + binding.nestedScroll.scrollY)
            if (diff == 0 && !isFetchingData && allRecipeList.size < allRecipesTotalCount) {
//            if (diff <= preloadThreshold && !isFetchingData && allRecipeList.size < totalItems) {
                currentPageAllRecipe++
                Log.e("PrinceEWW>>>", "OnScroll: page++")
                getAllRecipesWithPagination()
            }
        }

        binding.btnAction.setOnClickListener {
            toggleFloatingActionButton(!binding.clMenu.isVisible) //Toggle of floating action menu visibility
        }

        binding.btnUploadAudio?.setOnClickListener {
            binding.clUploadAudio?.callOnClick()
        }
        binding.clUploadRecipe.setSafeOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openFilePicker(Constants.RECIPE)
            else PermissionManagerUtils.checkPermission(requireContext(),
                requireActivity(),
                arrayListOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PermissionManagerUtils.PermissionSessionManager(requireContext()),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            requireActivity(), arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            requireActivity(), arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), 1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------Here open dialog for ask user to provide permission if they previously denied-------//
                        DialogUtils().commonDialog(context = requireActivity(),
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
                        openFilePicker(Constants.RECIPE)
                    }
                })
        }
        binding.clUploadAudio?.setSafeOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openAudioDialog()
            else PermissionManagerUtils.checkPermission(requireContext(),
                requireActivity(),
                arrayListOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PermissionManagerUtils.PermissionSessionManager(requireContext()),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            requireActivity(), arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            requireActivity(), arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), 1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------Here open dialog for ask user to provide permission if they previously denied-------//
                        DialogUtils().commonDialog(context = requireActivity(),
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
//                        openFilePicker(Constants.AUDIO) //Commented by PrinceEWW, because in if condition(where no need to ask for permission in android 13 and above) we call "openAudioDialog()" function so also need to show in else condition(after ask permission and inside permission granted permission)
                        openAudioDialog() //Show dialog of audio type selection after permission granted
                    }

                })
        }

        binding.clShareRecipe.setSafeOnClickListener {
            LoadingUtils.showLoading(requireActivity(), false, "Please Wait")
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            zipFileShare.clear()
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    listRecipeForSearch.forEach { recipe ->
                        val root = File(requireContext().externalCacheDir, "Temp")
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
                                    requireContext().contentResolver.openInputStream(
                                        Uri.parse(
                                            recipe.imageUrl
                                        )
                                    )
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
                                val imageFile = File(root, it.title + "_" + index + ".jpg")
                                try {
                                    val iStream: InputStream? =
                                        requireContext().contentResolver.openInputStream(
                                            Uri.parse(
                                                it.image
                                            )
                                        )
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
                        recipe.Instruction.forEachIndexed { index, it ->
                            if (!it.audioPUrl.isNullOrEmpty()) {
                                val audioFile = File(root, it.audioP + ".mp3")
                                try {
                                    val iStream: InputStream? =
                                        requireContext().contentResolver.openInputStream(
                                            Uri.parse(
                                                it.audioPUrl
                                            )
                                        )
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
                                        listOfFiles.add(audioFile)
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
                            if (!it.audioIUrl.isNullOrEmpty()) {
                                val audioFile = File(root, it.audioI + ".mp3")
                                try {
                                    val iStream: InputStream? =
                                        requireContext().contentResolver.openInputStream(
                                            Uri.parse(
                                                it.audioIUrl
                                            )
                                        )
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
                                        listOfFiles.add(audioFile)
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
                                        requireContext().contentResolver.openInputStream(
                                            Uri.parse(
                                                it.audioQUrl
                                            )
                                        )
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
                                        listOfFiles.add(audioFile)
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
                        zipFileShare.add(
                            requireContext().getZipFileFromFiles(
                                listOfFiles, recipe
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    requireContext().shareZipFile(zipFileShare, "Recipe")
                    LoadingUtils.hideDialog()
                }
            }


        }

        binding.clSetUpDevice?.setSafeOnClickListener {
//            CoroutineScope(Dispatchers.IO).launch {
//                val loop = async {
//                    var iteration = 0
//                    DebugLog.e(
//                        "Handler changeDirectory iteration $iteration Size{${listRecipeForSearch.size}}"
//                    )
//                    while (iteration < listRecipeForSearch.size) {
//                        withContext(Dispatchers.IO) {
//                            val outputFile = requireContext().createTempTextFile(
//                                listRecipeForSearch[iteration].name[0],
//                                ".txt"
//                            )
//                            val writer = FileOutputStream(outputFile)
//                            writer.write(Gson().toJson(listRecipeForSearch[iteration]).toByteArray())
//                            writer.flush()
//                            writer.close()
//                            delay(100)
//                            DebugLog.e("ftpUploadFile iteration $iteration")
//                            iteration++
//                        }
//                    }
//                }
//                DebugLog.e("ftpUploadFile loop end")
//                loop.await()
//            }

            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
//            requireActivity().runOnUiThread {
//                val FTPDialog = FTPDialog(macAddress)
//                FTPDialog.show(childFragmentManager, "")
//            }
            if (service.isDeviceConnected(macAddress)) {
                checkStatus(Constants.CheckStatus.FTPCONNECTION)
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

//            if (service.isDeviceConnected(macAddress))
//                startHotspot()
        }
        binding.clOn2CookAI.setSafeOnClickListener {
            toggleFloatingActionButton(showMenu = false)
            PermissionManagerUtils.checkPermission(
                requireContext(),
                requireActivity(),
                arrayListOf(Manifest.permission.CAMERA),
                PermissionManagerUtils.PermissionSessionManager(requireContext()),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.CAMERA),
                            Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.CAMERA),
                            Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        DialogUtils().commonDialog(
                            context = requireActivity(),
                            title = getString(R.string.title_permission_open_settings),
                            message = getString(R.string.message_storage_camera_permission),
                            positiveButton = getString(R.string.button_setting),
                            negativeButton = getString(R.string.button_cancel),
                            isAppLogoDisplay = true,
                            isCancelable = true,
                            callbackSuccess = {
                                requireContext().openPermissionSettings()
                            },
                            callbackNegative = {
                            })
                    }

                    override fun onPermissionGranted() {
                        val integrator = com.google.zxing.integration.android.IntentIntegrator.forSupportFragment(this@RecipeFragment)
                        integrator.setPrompt("Scan a QR code")
                        integrator.setBeepEnabled(true)
                        integrator.setOrientationLocked(true)
                        integrator.setDesiredBarcodeFormats(com.google.zxing.integration.android.IntentIntegrator.QR_CODE)
                        integrator.setCameraId(0)

                        // Launch the QR code scanner using the ActivityResultLauncher
                        qrCodeScannerLauncher.launch(integrator.createScanIntent())
                    }
                }
            )
        }


        binding.clAddNewRecipe.setSafeOnClickListener {
//            var intent = Intent(this, FileChooserActivity::class.java)
//            startActivity(intent)
            //startActivity(Intent(this@CookingActivity, CreateRecipeAddStepsActivity::class.java))
            //startActivity(Intent(this@CookingActivity, ExtractWebsiteActivity::class.java))
            binding.clRecipeSearch.visibility = View.VISIBLE
            binding.floatingSearchView.setSearchFocused(true)
////            var intent = Intent(this, NewCreateRecipe::class.java)
//            startActivity(intent)

            //var locale = Locale("en", "US")
//                if(tts?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE){
//                    tts?.language = locale
//                }else{
//                    val installIntent = Intent()
//                    installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
//                    startActivity(installIntent)
//                }

            //tts.synthesizeToFile()

//            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener(){
//                override fun onDone(utteranceId: String?) {
//                    println("done     $utteranceId")
//                }
//
//                override fun onError(utteranceId: String?) {
//                    println("error     $utteranceId")
//                }
//
//                override fun onStart(utteranceId: String?) {
//                    println("start     $utteranceId")
//                }
//            })
//
//            val hashTts =
//                HashMap<String, String>()
//            hashTts[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "id"
//
//            var file = File(getExternalFilesDir(null), "sound.wav")
//            println("file path      ${file.path}")
//            var status = tts?.synthesizeToFile("Wash Dal & Rice", null, file, "123433")
        }
        binding.clSearchView.setSafeOnClickListener {
//            var intent = Intent(this, FileChooserActivity::class.java)
//            startActivity(intent)
            //startActivity(Intent(this@CookingActivity, CreateRecipeAddStepsActivity::class.java))
            //startActivity(Intent(this@CookingActivity, ExtractWebsiteActivity::class.java))
            if (listRecipeForSearch.isNotEmpty()) {
                binding.clRecipeSearch.visibility = View.VISIBLE
                binding.floatingSearchView.setSearchFocused(true)
            } else {
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(), getString(R.string.strNoRecipes), Toast.LENGTH_SHORT
                    ).show()
                }
            }
////            var intent = Intent(this, NewCreateRecipe::class.java)
//            startActivity(intent)

            //var locale = Locale("en", "US")
//                if(tts?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE){
//                    tts?.language = locale
//                }else{
//                    val installIntent = Intent()
//                    installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
//                    startActivity(installIntent)
//                }

            //tts.synthesizeToFile()

//            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener(){
//                override fun onDone(utteranceId: String?) {
//                    println("done     $utteranceId")
//                }
//
//                override fun onError(utteranceId: String?) {
//                    println("error     $utteranceId")
//                }
//
//                override fun onStart(utteranceId: String?) {
//                    println("start     $utteranceId")
//                }
//            })
//
//            val hashTts =
//                HashMap<String, String>()
//            hashTts[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "id"
//
//            var file = File(getExternalFilesDir(null), "sound.wav")
//            println("file path      ${file.path}")
//            var status = tts?.synthesizeToFile("Wash Dal & Rice", null, file, "123433")
        }
//        binding.clCreateNewRecipe.setOnClickListener {
////            val fragment = (activity as HomeActivity).viewPagerAdapter.getFragmentFromMac(
////                "2"
////            )
////            Log.e("TAG", "onReceive:DD ${fragment!!.id}")
////            if ((fragment as DashboardFragment).navController.currentDestination!!.id == R.id.recipeFragment) {
////                (fragment.getCurrentFragment() as RecipeFragment).binding.tvIndTime.text =
////                    "Finaly Founded"
////                Log.e("TAG", "onReceive: DD Matched")
////            }
//            val intent = Intent(requireContext(), CreateNewRecipe::class.java)
//            intent.putExtra("list", Gson().toJson(recentAddedAdapter.getList()))
//            startActivity(intent)
//        }

        //-------create recipe click listener-------//
        binding.clCreateRecipe.setOnClickListener {
            toggleFloatingActionButton(showMenu = false) //Need to hide floating action menu
            val intent = Intent(requireContext(), CreateNewRecipe::class.java)
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
            val intent = Intent(requireContext(), CreateNewRecipe::class.java)
            intent.putExtras(Bundle().apply {
                putEnum(
                    Constants.BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE,
                    Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE
                )//Differentiate screen flow type
            })
//            startActivity(intent)
            createNewRecipeResultLauncher.launch(intent)
        }



        binding.floatingSearchView.setOnSearchListener(object :
            FloatingSearchView.OnSearchListener {
            override fun onSearchAction(currentQuery: String?) {

            }

            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion?) {
                //var suggestionId = (searchSuggestion as Suggestion).suggestionId
                val suggestionQuery =
                    (searchSuggestion as Suggestion).suggestionText
                DebugLog.e("suggestionQuery $suggestionQuery")
                val foundRecipe = listRecipeForSearch.filter { it ->
                    it.name[0].equals(suggestionQuery, true)
                }
                binding.clRecipeSearch.visibility = View.GONE
                binding.floatingSearchView.clearQuery()
                binding.floatingSearchView.clearSearchFocus()
                navigateToRecipe(foundRecipe[0])
            }
        })

        binding.floatingSearchView.setOnClearSearchActionListener {
            floatingSearchView.clearSuggestions()
        }

        binding.floatingSearchView.setOnFocusChangeListener(object :
            FloatingSearchView.OnFocusChangeListener {
            override fun onFocusCleared() {
                binding.clRecipeSearch.visibility = View.GONE
            }

            override fun onFocus() {
            }
        })

        binding.imgPowerInd.setOnClickListener {
            binding.imgPowerInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgPowerInd.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgPowerInd)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START || indStatus == Constants.Status.PAUSE) {
                    service.writeData(
                        macAddress, Constants.INDQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    checkStatus(Constants.CheckStatus.INDQUICKSTART)
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.indPause.setOnClickListener {
            if (!::service.isInitialized) {
                initializeService(binding.indPause)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START) {
                    service.writeData(macAddress, Constants.INDPAUSE.toByteArray(Charsets.UTF_8))
                } else {
                    service.writeData(macAddress, Constants.INDRESUME.toByteArray(Charsets.UTF_8))
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.magPause.setOnClickListener {
            binding.magPause.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.magPause.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.magPause)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress, Constants.MAGPAUSE.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    service.writeData(
                        macAddress, Constants.MAGRESUME.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.imgPowerMag.setOnClickListener {
            binding.imgPowerMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgPowerMag.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgPowerMag)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START || magStatus == Constants.Status.PAUSE) {
                    service.writeData(
                        macAddress, Constants.MAGQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    checkStatus(Constants.CheckStatus.MAGQUICKSTART)
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Plus microwave power for manual mode-------//
        binding.tvPlusPowerMag.setOnClickListener {
            binding.tvPlusPowerMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvPlusPowerMag.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvPlusPowerMag)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvPowerCountMag.text.toString(),
                            replaceChar = "%"
                        ) >= 100
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPowerMax),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "MAGPOWER=10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strStartMag),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

        }

        //-------Minus microwave power for manual mode-------//
        binding.tvMinusPowerMag.setOnClickListener {
            binding.tvMinusPowerMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvMinusPowerMag.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvMinusPowerMag)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvPowerCountMag.text.toString(),
                            replaceChar = "%"
                        ) <= 10
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPowerMin),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "MAGPOWER=-10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strStartMag),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

        }

        binding.tvPlusInd.setOnClickListener {
            binding.tvPlusInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvPlusInd.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvPlusInd)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress, "INDPROCESSTIME=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    Toast.makeText(
                        requireContext(), getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.tvPlusMag.setOnClickListener {
            binding.tvPlusMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvPlusMag.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvPlusMag)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress, "MAGPROCESSTIME=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    Toast.makeText(
                        requireContext(), "Please start Magnetron", Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.tvMinusInd.setOnClickListener {
            binding.tvMinusInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvMinusInd.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvMinusInd)
                return@setOnClickListener

            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START) {
                    Log.e("---", "init: ${binding.tvIndTime.text.toString().split(":")[0].trim()}")
                    if (binding.tvIndTime.text.toString()
                            .split(":")[0].trim() != "00" || (binding.tvIndTime.text.toString()
                            .split(":")[0].trim() == "00" && binding.tvIndTime.text.toString()
                            .split(":")[1].trim().toInt() > 11)
                    ) service.writeData(
                        macAddress, "INDPROCESSTIME=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        Toast.makeText(
                            requireContext(), getString(R.string.strShortSec), Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(), getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.tvMinusMag.setOnClickListener {
            binding.tvMinusMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvMinusMag.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvMinusMag)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (magStatus == Constants.Status.START) {
                    if (binding.tvMagTime.text.toString()
                            .split(":")[0].trim() != "00" || (binding.tvMagTime.text.toString()
                            .split(":")[0].trim() == "00" && binding.tvMagTime.text.toString()
                            .split(":")[1].trim().toInt() > 11)
                    ) service.writeData(
                        macAddress, "MAGPROCESSTIME=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else Toast.makeText(
                        requireContext(), getString(R.string.strShortSec), Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(), getString(R.string.strStartMag), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.tvPlusPower.setOnClickListener {
            binding.tvPlusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvPlusPower.isEnabled = true
            }, 500)
            Log.e("TAG", "init: PowerPlusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvPlusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START) {
                    if (binding.tvPowerCount.text.toString().replace("%", "").trim()
                            .toInt() != 100
                    ) service.writeData(
                        macAddress, "INDPOWER=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        if (binding.tvPowerCount.text.toString().replace("%", "").trim()
                                .toInt() == 100
                        ) Toast.makeText(
                            requireContext(), getString(R.string.strPowerMax), Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(), getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.tvMinusPower.setOnClickListener {
            binding.tvMinusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvMinusPower.isEnabled = true
            }, 500)
            Log.e("TAG", "init: PowerMinusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvMinusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (indStatus == Constants.Status.START) {
                    Log.e(
                        "TAG",
                        "init: ${binding.tvPowerCount.text.toString().replace("%", "").trim()}"
                    )
                    if (binding.tvPowerCount.text.toString().replace("%", "").trim()
                            .toInt() != 10
                    ) service.writeData(
                        macAddress, "INDPOWER=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        if (binding.tvPowerCount.text.toString().replace("%", "").trim()
                                .toInt() == 10
                        ) Toast.makeText(
                            requireContext(), getString(R.string.strPowerMin), Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(), getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.imgStirrerPower.setOnClickListener {
            binding.imgStirrerPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgStirrerPower.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgStirrerPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (stirrerOn) {
                    service.writeData(
                        macAddress,
                        Constants.STIRRER_OFF.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    if (indStatus == Constants.Status.START || magStatus == Constants.Status.START) {
                        service.writeData(
                            macAddress,
                            getCommandFromSelection(binding.toggleDifficultyLevel.checkedTogglePosition).toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strStartIndOrMag),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Sprinkle power button click-------//
        //-------In firmware, SPRINKLE = PUMP-------//
        //-------In older version on(2.12.9) and before version, we have "pump" instead of "Sprinkle"-------//
        binding.imgSprinklePower.setOnClickListener {
            binding.imgSprinklePower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgSprinklePower.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgSprinklePower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                //-------User can not start Sprinkle(Pump) when Spray(Purge) is on-------//
                if (purgeOn) {
                    Toast.makeText(
                        requireActivity(),
                        "Spray is already working",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                //-------If purge is On user can not turn on pump(sprinkle)-------//
                if (pumpOn) {
                    service.writeData(
                        macAddress,
                        Constants.PUMP_OFF.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    if (binding.etSprinkleValue.text?.isNotEmpty() == true) {
                        //-------Check status before turn on pump, because there is chances fry mode on, at that time no need to start pump-------//
                        checkStatus(status = Constants.CheckStatus.SPRINKLE)
                    } else {
                        Toast.makeText(
                            requireContext(), getString(R.string.strPump), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Spray power button click-------//
        //-------In firmware, Spray == PURGE-------//
        binding.imgSprayPower.setOnClickListener {
            binding.imgSprayPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgSprayPower.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgSprayPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                //-------User can not start Spray(Purge) when Sprinkle(Pump) is on-------//
                if (pumpOn) {
                    Toast.makeText(
                        requireActivity(),
                        "Sprinkle is already working",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (purgeOn) {
                    service.writeData(
                        macAddress,
                        Constants.PURGE_OFF.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    if (binding.etSprayValue.text.toString().isEmpty()) {
                        Toast.makeText(
                            requireContext(), getString(R.string.strPleaseAddMlToStartSpray), Toast.LENGTH_SHORT
                        ).show()
                    } else if ((binding.etSprayValue.text.toString().toIntOrNull() ?: Constants.DEFAULT_ZERO) < Constants.DEFAULT_TEN){
                        //-------Minimum ML for spray is 10-------//
                        Toast.makeText(
                            requireContext(), getString(R.string.strPleaseEnterMinimumTenMlToStartSpray), Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        //-------Check status before turn on purge, because there is chances fry mode on, at that time no need to start purge-------//
                        checkStatus(status = Constants.CheckStatus.SPRAY)
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-----FRY MODE LISTENER-------//
        //-------Fry mode on/off power button-------//
        binding.imgFryModeOnOff.setOnClickListener {
            Log.e("PrinceEWW>>", "Fry mode toggle click")
            binding.imgFryModeOnOff.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgFryModeOnOff.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgFryModeOnOff)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START || fryModeStatus == Constants.Status.PAUSE) {
                    //-------Quick fry mode is ON, turn it OFF-------//
                    service.writeData(
                        macAddress,
                        Constants.FRYQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    //-------Quick fry mode is OFF, turn it ON-------//
                    service.writeData(
                        macAddress,
                        (Constants.FRYQUICKSTART).toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Pause, Resume Fry Mode-------//
        binding.imgFryModePauseResume.setOnClickListener {
            binding.imgFryModePauseResume.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgFryModePauseResume.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgFryModePauseResume)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress, Constants.FRYQUICKPAUSE.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    service.writeData(
                        macAddress, Constants.FRYQUICKRESUME.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode induction plus power listener-------//
        binding.tvFryModeInductionPlusPower.setOnClickListener {
            binding.tvFryModeInductionPlusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeInductionPlusPower.isEnabled = true
            }, 500)
            Log.e("TAG", "init: FryModelPowerPlusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeInductionPlusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvFryModeInductionPowerCount.text.toString(),
                            replaceChar = "%"
                        ) >= 100
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPowerMax),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "INDPOWER=10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode induction minus power listener-------//
        binding.tvFryModeInductionMinusPower.setOnClickListener {
            binding.tvFryModeInductionMinusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeInductionMinusPower.isEnabled = true
            }, 500)
            Log.e("TAG", "init: PowerMinusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeInductionMinusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvFryModeInductionPowerCount.text.toString(),
                            replaceChar = "%"
                        ) <= 10
                    ) {
                        Toast.makeText(
                            requireContext(), getString(R.string.strPowerMin), Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "INDPOWER=-10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode plus threshold listener-------//
        binding.tvFryModeThresholdPlus.setOnClickListener {
            binding.tvFryModeThresholdPlus.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeThresholdPlus.isEnabled = true
            }, 500)
            Log.e("TAG", "init: PowerMinusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeThresholdPlus)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvFryModeThresholdCount.text.toString()
                        ) >= 220
                    ) {
                        Toast.makeText(
                            requireContext(), getString(R.string.strPowerMax), Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "THRESHOLD=10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode plus threshold listener-------//
        binding.tvFryModeThresholdMinus.setOnClickListener {
            binding.tvFryModeThresholdMinus.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeThresholdMinus.isEnabled = true
            }, 500)
            Log.e("TAG", "init: PowerMinusStart")
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeThresholdMinus)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    if (convertStringToInt(fullString = binding.tvFryModeThresholdCount.text.toString()) <= 80
                    ) {
                        Toast.makeText(
                            requireContext(), getString(R.string.strPowerMin), Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "THRESHOLD=-10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode microwave plus power listener-------//
        binding.tvFryModeMicrowavePlusPower.setOnClickListener {
            binding.tvFryModeMicrowavePlusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeMicrowavePlusPower.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeMicrowavePlusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeMagStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvFryModeMicrowavePowerCount.text.toString(),
                            replaceChar = "%"
                        ) >= 100
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPowerMax),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "MAGPOWER=10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strStartMag),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode microwave minus power listener-------//
        binding.tvFryModeMicrowaveMinusPower.setOnClickListener {
            binding.tvFryModeMicrowaveMinusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.tvFryModeMicrowaveMinusPower.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeMicrowaveMinusPower)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeMagStatus == Constants.Status.START) {
                    if (convertStringToInt(
                            fullString = binding.tvFryModeMicrowavePowerCount.text.toString(),
                            replaceChar = "%"
                        ) <= 10
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPowerMin),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "MAGPOWER=-10".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strStartMag),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

        }

        //-------Fry mode microwave plus time listener-------//
        binding.tvFryModeMicrowavePlusTime.setOnClickListener {
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeMicrowavePlusTime)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeMagStatus == Constants.Status.START) {
                    binding.tvFryModeMicrowavePlusTime.isEnabled = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.tvFryModeMicrowavePlusTime.isEnabled = true
                    }, 500)
                    if (binding.tvFryModeMicrowaveTime.text.toString()
                            .split(":")[0].trim()
                            .toInt() >= 5 && binding.tvFryModeMicrowaveTime.text.toString()
                            .split(":")[1].trim().toInt() > 29
                    ) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strMaxTimeForFryModeMicrowave),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        service.writeData(
                            macAddress, "MAGPROCESSTIME=30".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else if (fryModeMagStatus == Constants.Status.PAUSE) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseResumeMicrowave),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //-------According requirement user can change time of microwave before start microwave - fpor quickFryMode-------//
                    if (fryModeMicrowaveTimeLocalTime <= 330) {
                        fryModeMicrowaveTimeLocalTime += 30
                        binding.tvFryModeMicrowaveTime.text =
                            Constants.getFormattedTime(fryModeMicrowaveTimeLocalTime * 1000)
                    } else {
                        binding.tvFryModeMicrowavePlusTime.isEnabled = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.tvFryModeMicrowavePlusTime.isEnabled = true
                        }, 500)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strMaxTimeForFryModeMicrowave),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode microwave minus time listener-------//
        binding.tvFryModeMicrowaveMinusTime.setOnClickListener {
            if (!::service.isInitialized) {
                initializeService(binding.tvFryModeMicrowaveMinusTime)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeMagStatus == Constants.Status.START) {
                    binding.tvFryModeMicrowaveMinusTime.isEnabled = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.tvFryModeMicrowaveMinusTime.isEnabled = true
                    }, 500)
                    if (binding.tvFryModeMicrowaveTime.text.toString()
                            .split(":")[0].trim() != "00" || (binding.tvFryModeMicrowaveTime.text.toString()
                            .split(":")[0].trim() == "00" && binding.tvFryModeMicrowaveTime.text.toString()
                            .split(":")[1].trim().toInt() > 31)
                    ) {
                        service.writeData(
                            macAddress, "MAGPROCESSTIME=-30".toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
                        Toast.makeText(
                            requireContext(), getString(R.string.strShortSec), Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (fryModeMagStatus == Constants.Status.PAUSE) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseResumeMicrowave),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //-------According requirement user can change time of microwave before start microwave - fpor quickFryMode-------//
                    if (fryModeMicrowaveTimeLocalTime >= 60) {
                        fryModeMicrowaveTimeLocalTime -= 30
                        binding.tvFryModeMicrowaveTime.text =
                            Constants.getFormattedTime(fryModeMicrowaveTimeLocalTime * 1000)
                    } else {
                        binding.tvFryModeMicrowaveMinusTime.isEnabled = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.tvFryModeMicrowaveMinusTime.isEnabled = true
                        }, 500)
                        Toast.makeText(
                            requireContext(), getString(R.string.strShortSec), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Fry mode microwave on/off toggle-------//
        binding.imgFryModeMicrowaveOnOff.setOnClickListener {
            binding.imgFryModeMicrowaveOnOff.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgFryModeMicrowaveOnOff.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgFryModeMicrowaveOnOff)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (isQuickFryModeOn) {
                    if (fryModeMagStatus == Constants.Status.START || fryModeMagStatus == Constants.Status.PAUSE) {
                        service.writeData(
                            macAddress, Constants.MAGFRYSTOP.toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
//                        checkStatus(Constants.CheckStatus.MAGQUICKSTART)
                        service.writeData(
                            macAddress,
                            ("MAGFRYSTART=START,$fryModeMicrowaveTimeLocalTime").toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.imgFryModeMicrowavePauseResume.setOnClickListener {
            binding.imgFryModeMicrowavePauseResume.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgFryModeMicrowavePauseResume.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgFryModeMicrowavePauseResume)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeMagStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress, Constants.MAGFRYPAUSE.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    service.writeData(
                        macAddress, Constants.MAGFRYRESUME.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

        }

        //-------Fry mode stirrer on/off toggle-------//
        binding.imgFryModeStirrerOnOff.setOnClickListener {
            binding.imgFryModeStirrerOnOff.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgFryModeStirrerOnOff.isEnabled = true
            }, 500)
            if (!::service.isInitialized) {
                initializeService(binding.imgFryModeStirrerOnOff)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStirrerOn) {
                    service.writeData(
                        macAddress,
                        Constants.STIRRER_OFF.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    if (fryModeStatus == Constants.Status.START) {
                        service.writeData(
                            macAddress,
                            getCommandFromSelection(binding.toggleSwitchFryModeStirrerLevel.checkedTogglePosition).toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.strPleaseStartFryMode),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        //-------Enable disable button according on power time change-------//
        binding.etFryModePowerSavingTime.onTextChange {
            if (it.trim().isEmpty()) {
                binding.btnApplyPowerSavingTime.isEnabled = false
            } else {
                binding.btnApplyPowerSavingTime.isEnabled =
                    (it.trim()
                        .toLong() != fryModePowerSavingTime) && fryModeStatus == Constants.Status.START
            }
        }

        binding.btnApplyPowerSavingTime.setOnClickListener {
            if (!::service.isInitialized) {
                initializeService(binding.btnApplyPowerSavingTime)
                return@setOnClickListener
            }
            if (service.isDeviceConnected(macAddress)) {
                if (fryModeStatus == Constants.Status.START) {
                    service.writeData(
                        macAddress,
                        ("${Constants.POWERSAVING}${binding.etFryModePowerSavingTime.text.toString()}").toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.strPleaseStartFryMode),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }

        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        DebugLog.e("EVENT_BLE_CONNECTION_SUCCESS")
                        if (!::service.isInitialized) { //PrinceEWW, Randomly sometime here service is not initialize so initialize service first
                            initializeService(binding.clRecipe)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            service.writeData(
                                macAddress,
                                Constants.setDateTimeCommand().toByteArray(Charsets.UTF_8)
                            )
                        },100) //Set delay for prevent service not initialize issue
//                        checkStatus(Constants.CheckStatus.DEFAULT)
                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        if (indStatus == Constants.Status.START || indStatus == Constants.Status.PAUSE) {
                            updateIndTime(Constants.FINISH)
                        }
                        if (magStatus == Constants.Status.START || magStatus == Constants.Status.PAUSE) {
                            updateMagTime(Constants.FINISH)
                        }
                        DebugLog.e("EVENT_BLE_CONNECTION_ABORT")
                    }
                }
            }
        }
        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val recieveDataInMac = intent!!.getStringExtra(Constants.MAC_ADDRESS)
                Log.e("PrinceEWW>>>", "communicationReceiver in RecipeFragment for macAddress: $recieveDataInMac and currentScreen Mac is $macAddress")
                if (intent!!.getStringExtra(Constants.MAC_ADDRESS) == macAddress) {
                    parseData(intent)
                } /*else {//Commented by PrinceEww, to prevent receive data multiple time issue (reboot on2Cook device)
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
                }*/
            }
        }
        val listener = object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            override fun onInterceptTouchEvent(
                recyclerView: RecyclerView, event: MotionEvent,
            ): Boolean = when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                }

                MotionEvent.ACTION_MOVE -> {
                    val isScrollingRight = event.x < startX
                    val scrollItemsToRight = isScrollingRight && recyclerView.canScrollRight
                    val scrollItemsToLeft = !isScrollingRight && recyclerView.canScrollLeft
                    val disallowIntercept = scrollItemsToRight || scrollItemsToLeft
                    recyclerView.parent.requestDisallowInterceptTouchEvent(disallowIntercept)
                }

                MotionEvent.ACTION_UP -> {
                    startX = 0f
                }

                else -> Unit
            }.let { false }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        }

        binding.reRecentItems.addOnItemTouchListener(listener)
        binding.reRecentAddedItems.addOnItemTouchListener(listener)

    }

    //-------Toggle of floating action menu visibility-------//
    private fun toggleFloatingActionButton(showMenu: Boolean) { //showMenu = true/false, true for show menu, false for hide menu
        if (showMenu) {
            binding.clMenu.viewShow()
            binding.constraintLayoutMenuBackground.viewShow()
            binding.nestedScroll.alpha = 0.5f
            binding.btnUpload.startAnimation(fabOpen)
            binding.btnUploadAudio?.startAnimation(fabOpen)
            binding.btnCreateFryRecipe.startAnimation(fabOpen)
            binding.btnCreate.startAnimation(fabOpen)
            binding.btnShare.startAnimation(fabOpen)
            binding.btnSetUp?.startAnimation(fabOpen)
            binding.btnAction.startAnimation(fabClock)
            binding.btnOn2CookAI.startAnimation(fabOpen)
        } else {
            binding.clMenu.viewGone()
            binding.constraintLayoutMenuBackground.viewGone()
            binding.nestedScroll.alpha = 1f
            binding.btnUpload.startAnimation(fabClose)
            binding.btnUploadAudio?.startAnimation(fabClose)
            binding.btnCreateFryRecipe.startAnimation(fabClose)
            binding.btnCreate.startAnimation(fabClose)
            binding.btnShare.startAnimation(fabClose)
            binding.btnSetUp?.startAnimation(fabClose)
            binding.btnOn2CookAI.startAnimation(fabClose)
            binding.btnAction.startAnimation(fabAntiClock)
        }
    }

    //-------If menu(Create recipe, Fry recipe, Upload recipes, Upload audio, ...) is open, then close on scroll nested scroll, and any other object click-------//
    fun hideFloatingActionMenu() {
        if (binding.clMenu.isVisible) {
            toggleFloatingActionButton(false) //Pass false for hide menu
        }
    }

    //-------Change UI according screen flow type-------//
    private fun changeUiAccordingScreenFlowtype() {
        binding.clModes.visibleIfOrGone(recipeFragmentScreenFlowType == Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type)
        binding.clQuickFryMode.visibleIfOrGone(recipeFragmentScreenFlowType == Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE.type)
    }

    //-------This function will be called when user create new recipe from tablet-------//
    internal fun newRecipeCreatedInTablet() {
        refreshAllRecipeList()
    }

    //-------After any change in recipe(Add/Update/UploadNewRecipe) we need to refresh allRecipeList-------//
    private fun refreshAllRecipeList() {
        CoroutineScope(Dispatchers.Main).launch {
            allRecipesTotalCount = withContext(Dispatchers.IO) { getAllRecipeTotalCount()}
            Log.e("PrinceEWW>>>", "Execute after get count of allRecipesTotalCount")
//            getAllRecipeTotalCount()
            currentPageAllRecipe = Constants.DEFAULT_ZERO
            allRecipeList.clear()
            allRecipeAdapter.clearList()
            getAllRecipesWithPagination()
        }
    }

    //-------After any change in recipe, we need to refresh recommendedList-------//
    private fun refreshRecommendedList() {
        recommendItemListAdapter.clearList()
        Executors.newSingleThreadExecutor().execute {
            val recipeDbList = OnToCookApplication.dbInstance.recipeDao().getRandomRecipe()
            requireActivity().runOnUiThread {
                Log.e("TAG", "getRandomRecipe: ${recipeDbList.size}")
                if (recipeDbList.isNotEmpty()) {
                    binding.clRecommend.viewShow()
                    recommendItemListAdapter.addAll(recipeDbList)
                } else {
                    binding.clRecommend.viewGone()
                }
            }
        }
    }

    private fun refreshRecentItemList() {
//        recentItemListAdapter.clearRecentItemList()
        if (macAddress == Constants.DummyMacAddress)
            OnToCookApplication.dbInstance.recentlyPlayedDao().getAllRecipe().subscribe({
                if (it.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        binding.clRecentAct.viewShow()
                        recentItemListAdapter.setRecentItemList(it)
                    }
                } else {
                    requireActivity().runOnUiThread {
                        binding.clRecentAct.viewGone()
                    }
                }
            }, {

            })
        else
            OnToCookApplication.dbInstance.recentlyPlayedDao().getAllRecipe(macAddress)
                .subscribe({
                    if (it.isNotEmpty()) {
                        requireActivity().runOnUiThread {
                            binding.clRecentAct.viewShow()
                            recentItemListAdapter.setRecentItemList(it)
                        }
                    } else {
                        requireActivity().runOnUiThread { binding.clRecentAct.viewGone() }
                    }
                }, {

                })
    }

    //-------Get total recipes count - for pagination-------//
    private suspend fun getAllRecipeTotalCount(): Int {
        return withContext(Dispatchers.IO) {
            OnToCookApplication.dbInstance.recipeDao().getAllRecipesCount()
        }.also { count ->
            Log.e("PrinceEWW>>>", "allRecipesTotalCount: $count")
        }
    }

    private fun getAllRecipesWithPagination() {
        changeAllRecipePaginationProgressBarStatus(visibleProgressBar = true)
        if (currentPageAllRecipe < Constants.DEFAULT_ZERO) currentPageAllRecipe =
            Constants.DEFAULT_ZERO
        if (!isFetchingData) {
            isFetchingData = true
            CoroutineScope(Dispatchers.Main).launch {
                val pageSize = Constants.DEFAULT_TWENTY
                val offset = currentPageAllRecipe * pageSize
                val allRecipePaginationList: List<Recipe> = withContext(Dispatchers.IO) {
                    OnToCookApplication.dbInstance.recipeDao().getAllLiveRecipeWithPagination(
                        size = pageSize,
                        offset = offset
                    )
                }
                withContext(Dispatchers.Main) {
                    changeAllRecipePaginationProgressBarStatus(visibleProgressBar = false)
                    if (allRecipePaginationList.isNotEmpty() && currentPageAllRecipe == Constants.DEFAULT_ZERO) {
                        Log.e("PrinceEWW>>>", "clAllrecipe visible")
                        binding.clAllRecipe.visible()
                    }
                    val notifyAdapterFromPosition: Int = allRecipeList.size
                    allRecipeList.addAll(allRecipePaginationList)
                    allRecipeAdapter.updateList(
                        list = allRecipeList as ArrayList<Recipe>,
                        notifyAdapterFromPosition = notifyAdapterFromPosition
                    ) //Pass "notifyAdapterFromPosition" for "notifyItemRangeInserted"
                    isFetchingData = false

                }
            }
        }
    }

    //-------Change state(visibility) of all recipe pagination progressbar-------//
    private fun changeAllRecipePaginationProgressBarStatus(visibleProgressBar: Boolean) {
        binding.progressBarAllRecipePagination.visibleIfOrGone(visibleProgressBar)
    }

    private fun exportDB() {
        val exportDir = File(Environment.getExternalStorageDirectory(), "")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val file = File(exportDir, "recipe.csv")
        try {
            file.createNewFile()
            val csvWrite = CSVWriter(FileWriter(file))
            CoroutineScope(Dispatchers.IO).launch {
//                val curCSV = OnToCookApplication.dbInstance.recipeDao().getAllRecipeCurser()
//                while (curCSV.moveToNext()) {
//                    //Which column you want to exprort
//                    val arrStr =
//                        arrayOf<String>(curCSV.getString(0), curCSV.getString(1), curCSV.getString(2))
//                    csvWrite.writeNext(arrStr)
//                }
//                csvWrite.close()
//                curCSV.close()
            }
        } catch (sqlEx: java.lang.Exception) {
            Log.e("MainActivity", sqlEx.message, sqlEx)
        }
    }

    private fun initializeService(imgPumpPower: View) {
        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun navigateToRecipe(recipe: Recipe) {
        val recipeItem = RecentItem(
            recipe.id,
            R.drawable.ic_mutton,
            recipe.name[0],
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
        recipeItem.name = recipe.name[0]
        recipeItem.recipe = Gson().toJson(recipe, Recipe::class.java)
        recipeItem.desc = recipe.description
        recipeItem.duration = recipe.Instruction.sumOf { it -> it.durationInSec }.toString()
        val bundle = Bundle().apply {
            putSerializable(Constants.RECIPE, recipeItem)
//            putString(Constants.RECIPE_LIST, Gson().toJson(allRecipeAdapter.getList()))
            putBoolean(
                Constants.RECIPE_LIST,
                true
            ) //By PrinceEWW, Previous developer pass whole list which throw byte size error
            putString(
                Constants.RANDOM_RECIPE_LIST,
                Gson().toJson(recommendItemListAdapter.getList())
            )
            putString(Constants.MAC_ADDRESS, macAddress)
            putBoolean(Constants.IS_SHOW, true)
        }
        findNavController().navigate(
            R.id.action_recipeFragment_to_recipeDetailFragment, bundle
        )
    }

    private fun getCommandFromSelection(checkedTogglePosition: Int): String {
        return when (checkedTogglePosition) {
            0 -> return Constants.STIRRER_ON_LOW
            1 -> return Constants.STIRRER_ON_MED
            2 -> return Constants.STIRRER_ON_HIGH
            3 -> return Constants.STIRRER_ON_VERY_HIGH
            else -> {
                return ""
            }
        }
    }

    internal fun parseData(intent: Intent) {
        when (intent.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
            Constants.EVENT_BLE_NOTIFICATION -> {
                val message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
//                if (message.contains(Constants.DATETIME, true)) {
//                    if (message.replace(
//                            "${Constants.DATETIME}",
//                            ""
//                        ).toInt() < System.currentTimeMillis() / 1000
//                    ) {
//                        Toast.makeText(
//                            requireContext(),
//                            "${requireContext().resources.getText(R.string.txt_warn_utc)}",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                    checkStatus(Constants.CheckStatus.DEFAULT)
//                    return
//                }
                if (message.uppercase().contains("INDQUICKSTART=")) {
                    if (recipeFragmentScreenFlowType == Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE.type) {
                        //-------This command is for manual mode, so screen type is "QUICK_FRY_MODE" then need to change to "MANUAL_MODE"-------//
                        recipeFragmentScreenFlowType =
                            Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type
                        changeUiAccordingScreenFlowtype()
                    }
                    if (message.contains(",")) {
                        val messageSize = message.split(",").size
                        if (messageSize > 0) {
                            val indStatus = message.uppercase().split(",")[0].replace(
                                "INDQUICKSTART=", ""
                            )
                            if (indStatus == Constants.IDLE) {
                                val indTime = message.uppercase().split(",")[1].replace(
                                    "IND_RUN=", ""
                                ).toLong() * 1000
                                if (indTime.toInt() != 0) {
                                    updateIndTime(Constants.PAUSE)
                                    binding.tvIndTime.text = Constants.getFormattedTime(indTime)
                                } else {
                                    isAvailable()
                                    //TODO
//                                    updateIndTime(Constants.FINISH)
                                }
                                if (messageSize > 2) {
                                    val magStatus = message.uppercase().split(",")[2].replace(
                                        "MAGQUICKSTART=", ""
                                    )
                                    if (magStatus != Constants.IDLE) {
                                        if (messageSize > 3) {
                                            val magTime =
                                                message.uppercase().split(",")[3].replace(
                                                    "MAG_RUN=", ""
                                                )
                                            startMagTimer(magTime.toLong() * 1000)
                                        }
                                        if (messageSize > 5) {
                                            val stirrerStatus =
                                                message.uppercase().split(",")[5].replace(
                                                    Constants.STIRRER, ""
                                                )
                                            try {
                                                DebugLog.e("stirrerStatus $stirrerStatus")
                                                if (stirrerStatus.toInt() == 0) {
                                                    setStirrerOn(false)
                                                } else {
                                                    if (messageSize > 6) {
                                                        DebugLog.e(
                                                            "stirrerStatus Splitt ${
                                                                message.uppercase()
                                                                    .split(",")[6]
                                                            }"
                                                        )

                                                        when (message.uppercase()
                                                            .split(",")[6]) {
                                                            "LOW" -> {
                                                                selectedPosition = 0
                                                            }

                                                            "MED" -> {
                                                                selectedPosition = 1
                                                            }

                                                            "HIGH" -> {
                                                                selectedPosition = 2
                                                            }

                                                            "VERY_HIGH" -> {
                                                                selectedPosition = 3
                                                            }
                                                        }
                                                        setStirrerOn(true)
                                                    }
                                                    if (messageSize > 7) {

                                                        val pumpStatus =
                                                            message.uppercase()
                                                                .split(",")[7].replace(
                                                                Constants.PUMP,
                                                                ""
                                                            )
                                                        DebugLog.e("stirrerStatus pumpStatus ${pumpStatus}")

                                                        pumpOn = pumpStatus.toInt() != 0
                                                        setPumpOn(pumpOn)
                                                    }
                                                }
                                            } catch (e: java.lang.Exception) {

                                            }
                                        }

                                    } else {
                                        if (messageSize > 3) {
                                            val magTime =
                                                message.uppercase().split(",")[3].replace(
                                                    "MAG_RUN=", ""
                                                ).toLong() * 1000
                                            if (magTime.toInt() != 0) {
                                                updateMagTime(Constants.PAUSE)
                                                binding.tvMagTime.text =
                                                    Constants.getFormattedTime(magTime)
                                            } else {
                                                isAvailable()
                                                //TODO
//                                                updateMagTime(Constants.FINISH)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (messageSize > 1) {
                                    val indTime = message.uppercase().split(",")[1].replace(
                                        "IND_RUN=", ""
                                    )
                                    startIndTimer(indTime.toLong() * 1000)
                                    if (messageSize > 2) {
                                        val magStatus =
                                            message.uppercase().split(",")[2].replace(
                                                "MAGQUICKSTART=", ""
                                            )
                                        if (messageSize > 3) {
                                            val magTime =
                                                message.uppercase().split(",")[3].replace(
                                                    "MAG_RUN=", ""
                                                ).toLong() * 1000
                                            if (magStatus != Constants.IDLE) {
                                                if (messageSize > 3) {
                                                    val magTime =
                                                        message.uppercase()
                                                            .split(",")[3].replace(
                                                            "MAG_RUN=", ""
                                                        )
                                                    startMagTimer(magTime.toLong() * 1000)
                                                }
                                                isAvailable()
                                            } else {
                                                if (magTime.toInt() != 0) {
                                                    updateMagTime(Constants.PAUSE)
                                                    binding.tvMagTime.text =
                                                        Constants.getFormattedTime(
                                                            magTime
                                                        )
                                                } else
                                                //TODO
//                                                    updateMagTime(Constants.FINISH)
                                                    isAvailable()
                                            }
                                        }
                                    }
                                    if (messageSize > 4) {
                                        val indPower =
                                            message.uppercase().split(",")[4].replace(
                                                "INDPOWER=", ""
                                            )
                                        println(indPower)
                                        updateIndPower(indPower)
                                    }
                                    if (messageSize > 5) {
                                        val stirrerStatus =
                                            message.uppercase().split(",")[5].replace(
                                                Constants.STIRRER, ""
                                            )
                                        try {
                                            DebugLog.e("stirrerStatus $stirrerStatus")
                                            if (stirrerStatus.toInt() == 0) {
                                                setStirrerOn(false)
                                            } else {
                                                if (messageSize > 6) {
                                                    DebugLog.e(
                                                        "stirrerStatus Splitt ${
                                                            message.uppercase().split(",")[6]
                                                        }"
                                                    )

                                                    when (message.uppercase().split(",")[6]) {
                                                        "LOW" -> {
                                                            selectedPosition = 0
                                                        }

                                                        "MED" -> {
                                                            selectedPosition = 1
                                                        }

                                                        "HIGH" -> {
                                                            selectedPosition = 2
                                                        }

                                                        "VERY_HIGH" -> {
                                                            selectedPosition = 3
                                                        }
                                                    }
                                                    setStirrerOn(true)
                                                }
                                                if (messageSize > 7) {
                                                    val pumpStatus =
                                                        message.uppercase()
                                                            .split(",")[7].replace(
                                                            Constants.PUMP,
                                                            ""
                                                        )
                                                    DebugLog.e("stirrerStatus pumpStatus ${pumpStatus}")

                                                    pumpOn = pumpStatus.toInt() != 0
                                                    setPumpOn(pumpOn)
                                                }
                                            }
                                        } catch (e: java.lang.Exception) {

                                        }
                                    }

                                }
                            }
                        }
                    } else {
                        if (message.uppercase() == Constants.INDQUICKSTART) {
                            startIndTimer(TimeUnit.MINUTES.toMillis(Constants.DEFAULT_TIME))
                        } else if (message.uppercase() == Constants.INDQUICKSTOP) {
                            updateIndTime(Constants.FINISH)
                        }
                    }
                }

                //-------Regular mode microwave start-------//
                if (message.uppercase() == Constants.MAGQUICKSTART) {
                    if (recipeFragmentScreenFlowType == Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE.type) {
                        //-------This command is for manual mode, so screen type is "QUICK_FRY_MODE" then need to change to "MANUAL_MODE"-------//
                        recipeFragmentScreenFlowType =
                            Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type
                        changeUiAccordingScreenFlowtype()
                    }
                    binding.tvMag.text = getString(R.string.txt_mag_started)
                    startMagTimer(TimeUnit.MINUTES.toMillis(Constants.DEFAULT_TIME))
                }

                //-------Fry mode microwave start-------//
                //-------On start and pause, we got command as "MAGFRYSTART" from hardware-------//
                if (message.uppercase() == Constants.MAGFRYSTART) {
//                    binding.tvFryModeMicrowave.text = getString(R.string.txt_mag_started)
                    changeFryModeMicrowaveStatus(
                        changeStatusTo = Constants.Status.START,
                        fryModeMicrowaveTime = 60 * 1000
                    )
                }
                if (message.uppercase() == Constants.STIRRER_ON || message.uppercase() == Constants.STIRRER_OFF) {
                    //-------If quick fry mode is On then set quick fry mode stirrer status else regular mode status-------//
                    if (isQuickFryModeOn) {
                        fryModeStirrerOn = message == Constants.STIRRER_ON
                        if (fryModeStirrerOn) {
                            fryModeStirrerSelectedPosition =
                                binding.toggleSwitchFryModeStirrerLevel.checkedTogglePosition
                        }
                        setFryModeStirrerState(fryModeStirrerOn)
                    } else {
                        stirrerOn = message == Constants.STIRRER_ON
                        if (isVisible) {
                            if (stirrerOn) {
                                selectedPosition =
                                    binding.toggleDifficultyLevel.checkedTogglePosition
                            }
                            setStirrerOn(stirrerOn)
                        }
                        return
                    }
                }

                if (message.uppercase().contains("STIRRER=ON")) {
                    if (isQuickFryModeOn) {
                        //-------When user start stirrer from hardware product-------//
                        val StirrerLevel = message.replace("STIRRER=ON,", "")
                        when (StirrerLevel) {
                            "LOW" -> {
                                fryModeStirrerSelectedPosition = 0
                            }

                            "MED" -> {
                                fryModeStirrerSelectedPosition = 1
                            }

                            "HIGH" -> {
                                fryModeStirrerSelectedPosition = 2
                            }

                            "VERY_HIGH" -> {
                                fryModeStirrerSelectedPosition = 3
                            }
                        }
                        setFryModeStirrerState(isStirrerOn = true)
                    } else {
                        //-------For manual mode, after start microwave, stirrer will be default on if there is stirrer is on-------//
                        //-------So after start microwave we got success command of microwave start, and just after that we got command for stirrer-------//
                        val StirrerLevel = message.replace("STIRRER=ON,", "")
                        when (StirrerLevel) {
                            "LOW" -> {
                                selectedPosition = 0
                            }

                            "MED" -> {
                                selectedPosition = 1
                            }

                            "HIGH" -> {
                                selectedPosition = 2
                            }

                            "VERY_HIGH" -> {
                                selectedPosition = 3
                            }
                        }
                        setStirrerOn(true)
                    }
                }

                //-------In firmware, Sprinkle == PUMP-------//
                if (message.contains(
                        Constants.PUMP_ON,
                        true
                    ) || message.uppercase() == Constants.PUMP_OFF
                ) {
                    pumpOn = message.uppercase() != Constants.PUMP_OFF
                    if (isVisible)
                        if (pumpOn) {
                            setPumpOn(true)
                        } else {
                            setPumpOn(false)
                        }
                    return
                }

                //-------In firmware, Spray == PURGE-------//
                if (message.contains(
                        Constants.PURGE_ON,
                        true
                    ) || message.uppercase() == Constants.PURGE_OFF
                ) {
                    purgeOn = message.uppercase() != Constants.PURGE_OFF
                    if (isVisible)
                        if (purgeOn) {
                            setPurgeOn(true)
                        } else {
                            setPurgeOn(false)
                        }
                    return
                }
                DebugLog.e("Check Unreach")
                if (message.uppercase().contains("INDPOWER=")) {
                    if (!message.contains(",")) {
                        val power = message.replace("INDPOWER=", "")
                        if (isQuickFryModeOn) {
                            binding.tvFryModeInductionPowerCount.text = "$power %"
                        } else {
                            binding.tvPowerCount.text = "$power %"
                        }
                    }
                }
                if (message.uppercase().contains("MAGPOWER=")) {
                    if (!message.contains(",")) {
                        val power = message.replace("MAGPOWER=", "")
                        if (isQuickFryModeOn) {
                            binding.tvFryModeMicrowavePowerCount.text = "$power %"
                        } else {
                            binding.tvPowerCountMag.text = "$power %"
                        }
                    }
                }
                if (message.uppercase().contains("INDPROCESSTIME=")) {
                    binding.tvInd.text = getString(R.string.txt_ind_started)
                    val long = message.replace("INDPROCESSTIME=", "").toLong()
                    startIndTimer(long * 1000)
                }
                if (message.uppercase().contains("MAGPROCESSTIME=")) {
                    if (isQuickFryModeOn) {
                        val long = message.replace("MAGPROCESSTIME=", "").toLong()
                        updateFryModeMicrowaveTime(fryModeMicrowaveTime = long * 1000)
                    } else {
                        tvMag.text = getString(R.string.txt_mag_started)
                        val long = message.replace("MAGPROCESSTIME=", "").toLong()
                        startMagTimer(long * 1000)
                    }
                }

                //-------Stop manual mode microwave-------//
                if (message.uppercase() == Constants.MAGQUICKSTOP) {
                    updateMagTime(Constants.FINISH)
                }

                //------Stop fry mode microwave-------//
                if (message.uppercase() == Constants.MAGFRYSTOP) {
                    changeFryModeMicrowaveStatus(changeStatusTo = Constants.Status.STOP)
                }

                if (message.lowercase() == Constants.IDLE_DEVICE) {
                    //-------Need to turn off Induction/microwave/fryModeMicrowave, to prevent issue when user is in recipeDetailScreen and stop anything from on2Cook Device(We check status on resume also)-------//
                    changeFryModeMicrowaveStatus(changeStatusTo = Constants.Status.STOP)
                    updateMagTime(Constants.FINISH)
                    updateIndTime(Constants.FINISH)
                    isAvailable()
                }
                if (message.lowercase().contains("recipe=")) {
                    val foundRecipe = listRecipeForSearch.filter { it ->
//                        if (it.name.isNotEmpty()) {
                        it.name[0] == Constants.getRecipeNameFromCommand(message)
//                        }
                    }
                    DebugLog.e("getMode Value ${Constants.getMode(message)}")
                    if (Constants.getMode(message)) return
                    if (foundRecipe.isEmpty()) {
                        val foundRecipe = Constants.RECIPES.filter {
                            it.name == Constants.getRecipeNameFromCommand(message)
                        }
                        if (foundRecipe.isNotEmpty()) {
                            findNavController().navigate(R.id.cookingFragment,
                                Constants.getBundleFromCommand(message).apply {
                                    putSerializable(Constants.RECIPE, foundRecipe[0])
                                    putString(Constants.MAC_ADDRESS, macAddress)
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
                    recentItem.recipe = Gson().toJson(foundRecipe[0])
                    DebugLog.e("If $macAddress")
                    findNavController().navigate(R.id.cookingFragment,
                        Constants.getBundleFromCommand(message).apply {
                            putSerializable(Constants.RECIPE, recentItem)
                            putString(Constants.MAC_ADDRESS, macAddress)
                        })
                }
                if (message.uppercase() == Constants.MAG_PAUSE) {
                    updateMagTime(Constants.PAUSE)
                }
                //-------Fry mode microwave pause-------//
                if (message.uppercase() == Constants.MAGFRYPAUSE) {
                    changeFryModeMicrowaveStatus(changeStatusTo = Constants.Status.PAUSE)
                }
                if (message.uppercase() == Constants.IND_PAUSE) {
                    updateIndTime(Constants.PAUSE)
                }

                //-------"Quick Fry Mode" START response, on resume also we got same command-------//
                if (message.uppercase() == Constants.FRYQUICKSTART) {
                    changeFryModeStatus(changeStatusTo = Constants.Status.START)
                }

                //-------"Quick Fry Mode" Pause response-------//
                if (message.uppercase() == Constants.FRYQUICKPAUSE) {
                    changeFryModeStatus(changeStatusTo = Constants.Status.PAUSE)
                }

                //-------"Quick Fry Mode" STOP response-------//
                if (message.uppercase() == Constants.FRYQUICKSTOP) {
                    changeFryModeStatus(changeStatusTo = Constants.Status.STOP)
                }

                //-------Update "THRESHOLD" of "Quick Fry Mode"-------//
                if (message.uppercase().contains(Constants.THRESHOLD)) {
                    if (!message.contains(",")) {
                        val threshold = message.replace("THRESHOLD=", "")
                        binding.tvFryModeThresholdCount.text = threshold
                    }
                }

                //-------Update fryMode power saving time-------//
                if (message.uppercase().contains(Constants.POWERSAVING)) {
                    if (!message.contains(",")) {
                        fryModePowerSavingTime = message.replace(Constants.POWERSAVING, "").toLong()
                        binding.etFryModePowerSavingTime.setText(fryModePowerSavingTime.toString())
                        //-------We need to store fry mode power saving time in preference for use while give command to hardware after start quick fry mode-------//
                        SharedPreferencesManager.insertData(
                            context = requireActivity(),
                            key = Constants.PREFFRYMODEPOWERSAVINGTIME,
                            value = fryModePowerSavingTime.toString()
                        )
                    }
                }

                if (message.uppercase().contains("FRYQUICKSTART=")) {
                    if (recipeFragmentScreenFlowType == Constants.RecipeFragmentScreenFlowType.MANUAL_MODE.type) {
                        //-------This command is for quick fry mode, so screen type is "MANUAL_MODE" then need to change to "QUICK_FRY_MODE"-------//
                        recipeFragmentScreenFlowType =
                            Constants.RecipeFragmentScreenFlowType.QUICK_FRY_MODE.type
                        changeUiAccordingScreenFlowtype()
                    }
                    if (message.contains(",")) {
                        val messageSize = message.split(",").size
                        if (messageSize > 0) {
                            //-------As of now this feature(Oil volume level) is removed by client-------//
                            /*if (messageSize == 2) {
                                //-------When message size is 2 that time, fry mode is start from hardware, and also on resume fry mode-------//
                                //-------Here "fryModeStatus", will be always "RUN"-------//
                                val fryModeStatus = message.uppercase().split(",")[0].replace(
                                    "FRYQUICKSTART=", ""
                                )

                                changeFryModeStatus(changeStatusTo = Constants.Status.START)

                                selectedOilLevelPosition = message.uppercase()
                                    .split(",")[1].replace("FRYQUICKSTART=START,", "").toInt()
                                    .minus(1)
                                binding.toggleSwitchFryModeOilLevel.checkedTogglePosition =
                                    selectedOilLevelPosition
                            } else {*/
                            //-------On check status, if dry mode is active-------//
                            //-------Here "fryModeStatus", will be always "RUN"-------//
                            val fryModeStatus = message.uppercase().split(",")[0].replace(
                                "FRYQUICKSTART=", ""
                            )
                            val fryModeTime = message.uppercase().split(",")[1].replace(
                                "FRYSEC=", ""
                            )
                            //-------Fry mode time, on check status-------//
                            currentTimeOfFryMode = fryModeTime.toLong() * 1000
                            changeFryModeStatus(changeStatusTo = if (fryModeStatus == "RUN") Constants.Status.START else Constants.Status.PAUSE)

                            //-------Fry mode induction power, on check status-------//
                            val fryModeInductionPower =
                                message.uppercase().split(",")[2].replace(
                                    "INDPOWER=", ""
                                )
                            binding.tvFryModeInductionPowerCount.text =
                                "$fryModeInductionPower %"

                            //-------Fry mode threshold, on check status-------//
                            val fryModeThreshold = message.uppercase().split(",")[3].replace(
                                "THRESHOLD=", ""
                            )
                            binding.tvFryModeThresholdCount.text = "$fryModeThreshold"

                            //-------Fry mode microwave status, on check status-------//
                            val fryModeMicrowaveStatus =
                                message.uppercase().split(",")[4].replace(
                                    "MICROWAVE=", ""
                                )
                            if (fryModeMicrowaveStatus == "RUN") {
                                //-------Fry mode microwave is "ON"-------//
                                val fryModeMicrowavePower =
                                    message.uppercase().split(",")[5].replace(
                                        "MAGPOWER=", ""
                                    )
                                binding.tvFryModeMicrowavePowerCount.text =
                                    fryModeMicrowavePower
                                val fryModeMicrowaveTime =
                                    message.uppercase().split(",")[6].replace(
                                        "MICROWAVESEC=", ""
                                    )
                                changeFryModeMicrowaveStatus(
                                    changeStatusTo = Constants.Status.START,
                                    fryModeMicrowaveTime = fryModeMicrowaveTime.toLong() * 1000
                                )
                            }

                            //-------Fry mode stirrer status, on check status-------//
                            val fryModeStirrerStatus =
                                message.uppercase().split(",")[7].replace(Constants.STIRRER, "")
                            setFryModeStirrerState(isStirrerOn = fryModeStirrerStatus == Constants.DEFAULT_ONE.toString())
                            if (fryModeStirrerStatus == Constants.DEFAULT_ZERO.toString()) {
                                //-------Stirrer is "OFF"-------//
                                setFryModeStirrerState(isStirrerOn = false)
                            } else {
                                //-------Stirrer is "ON"-------//
                                //-------Fry mode stirrer speed, on check status-------//
                                //TODO Prince:: Change to same as manual mode - STIRRER=0/1,Low/MED/HIGH/VHIGH
                                when (message.uppercase().split(",")[8]) {
                                    "LOW" -> {
                                        fryModeStirrerSelectedPosition = 0
                                    }

                                    "MED" -> {
                                        fryModeStirrerSelectedPosition = 1
                                    }

                                    "HIGH" -> {
                                        fryModeStirrerSelectedPosition = 2
                                    }

                                    "VERY_HIGH" -> {
                                        fryModeStirrerSelectedPosition = 3
                                    }
                                }
                                /*fryModeStirrerSelectedPosition =
                                    message.uppercase().split(",")[7].replace(Constants.STIRRER, "")
                                        .toInt() - 1*/
                                setFryModeStirrerState(isStirrerOn = true)
                            }

                            //-------As of now this feature(Oil volume level) is removed by client-------//
                            //-------Oil level-------/
                            /*selectedOilLevelPosition =
                                message.uppercase().split(",")[10].replace("OIL=", "").toInt()
                                    .minus(1)
                            binding.toggleSwitchFryModeOilLevel.checkedTogglePosition =
                                selectedOilLevelPosition*/

//                            }
                        }
                    }
                }
            }
        }
    }

    //-------Check status command-------//
    //-------Need to check device is free(in idle condition or not), before perform some task, like setupDevice, etc...-------//
    private fun checkStatus(status: Constants.CheckStatus) {
        checkStatus = status
        if (macAddress != Constants.DummyMacAddress)
            service.writeData(
                macAddress, Constants.STATUS.toByteArray(
                    Charsets.UTF_8
                )
            )
    }

    //-------Change fry mode timer-------//
    private fun changeFryModeStatus(changeStatusTo: Constants.Status) {
        fryModeStatus = changeStatusTo
        when (fryModeStatus) {
            Constants.Status.START -> {
                isQuickFryModeOn = true
                binding.imgFryModeOnOff.setImageResource(R.drawable.stop)
                binding.imgFryModePauseResume.visible()
                binding.imgFryModePauseResume.setImageResource(R.drawable.ic_pause)
                binding.tvFryModeTime.text = Constants.getFormattedTime(currentTimeOfFryMode)
                updateFryModeTime()
                //-------After start fry mode, we have to fire command for fry mode power saving mode-------//
                service.writeData(
                    macAddress,
                    ("${Constants.POWERSAVING}$fryModePowerSavingTime").toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            Constants.Status.STOP -> {
                isQuickFryModeOn = false
                binding.imgFryModeOnOff.setImageResource(R.drawable.ic_start)
                binding.imgFryModePauseResume.gone()
                quickFryModeTimer?.cancel()
                currentTimeOfFryMode = 0
                binding.tvFryModeTime.text = Constants.getFormattedTime(currentTimeOfFryMode)
                //-------Also need to restart "Induction Power", "Threshold"-------//
                binding.tvFryModeInductionPowerCount.text = "100 %"
                binding.tvFryModeThresholdCount.text = "200"
                //-------Also need to stop microwave if microwave is "ON"-------//
                changeFryModeMicrowaveStatus(changeStatusTo = Constants.Status.STOP)
                //-------Need to stop status of stirrer if stirrer is "ON"-------//
                setFryModeStirrerState(isStirrerOn = false)
            }

            Constants.Status.PAUSE -> {
                binding.imgFryModeOnOff.setImageResource(R.drawable.stop)
                binding.imgFryModePauseResume.visible()
                binding.imgFryModePauseResume.setImageResource(R.drawable.ic_resume)
                binding.tvFryModeTime.text = Constants.getFormattedTime(currentTimeOfFryMode)
                quickFryModeTimer?.cancel()
            }

            else -> {

            }
        }
    }

    //-------Update fry mode timer-------//
    private fun updateFryModeTime() {
        quickFryModeTimer?.cancel()
        quickFryModeTimer = object : CountDownTimer(totalFourHoursInMillis, 1000) {
            override fun onTick(millis: Long) {
                if (currentTimeOfFryMode >= totalFourHoursInMillis) {
                    //-------For safe side, we have to stop quick fry mode if we got more time from then 4 hour, because according requirement quick fry mode will be stop after 4 hour-------//
                    quickFryModeTimer?.cancel()
                    currentTimeOfFryMode = 0
                    binding.tvFryModeTime.text =
                        Constants.getFormattedTime(currentTimeOfFryMode)
                    service.writeData(
                        macAddress,
                        Constants.FRYQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    //-------If "currentTimeOfFryMode" is not >= 4 hour, increment counter-------//
                    currentTimeOfFryMode += 1000
                    val str = Constants.getFormattedTime(currentTimeOfFryMode)
                    binding.tvFryModeTime.text = str
                }
            }

            override fun onFinish() {
                //-------On finish timer we have to stop fry mode-------//
                fryModeStatus = Constants.Status.STOP
                quickFryModeTimer?.cancel()
                currentTimeOfFryMode = 0
                binding.tvFryModeTime.text = Constants.getFormattedTime(currentTimeOfFryMode)
                service.writeData(
                    macAddress,
                    Constants.FRYQUICKSTOP.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
        }.start()
    }

    private fun openRecipeListDialog() {
        val wifiManager = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            requireActivity().openWifiPermissionSettings()
        } else {
            requireActivity().runOnUiThread {
                val FTPDialog = FTPDialog(macAddress)
                FTPDialog.show(childFragmentManager, "")
            }
        }
    }

    private fun setPumpOn(statusOn: Boolean) {
        if (statusOn) {
            binding.etSprinkleValue.isEnabled = false
            binding.imgSprinklePower.setImageResource(R.drawable.stop)
            if (!stirrerOn) {
                stirrerOnForPump = true
                binding.imgStirrerPower.setImageResource(R.drawable.stop)
                binding.lavStirrer.viewShow()
                binding.lavStirrer.playAnimation()
                binding.toggleDifficultyLevel.checkedTogglePosition = 0
            }
        } else {
            binding.etSprinkleValue.isEnabled = true
            binding.etSprinkleValue.text?.clear()
            binding.imgSprinklePower.setImageResource(R.drawable.ic_start)
            if (stirrerOnForPump && !stirrerOn) {
                binding.lavStirrer.pauseAnimation()
                binding.lavStirrer.viewGone()
                binding.imgStirrerPower.setImageResource(R.drawable.ic_start)
            }
        }
    }

    //-------Manage Spray(Purge)-------//
    //-------In firmware, Spray == PURGE-------//
    private fun setPurgeOn(statusOn: Boolean) {
        if (statusOn) {
            binding.etSprayValue.isEnabled = false
            binding.imgSprayPower.setImageResource(R.drawable.stop)
            if (!stirrerOn) {
                stirrerOnForPump = true
                binding.imgStirrerPower.setImageResource(R.drawable.stop)
                binding.lavStirrer.viewShow()
                binding.lavStirrer.playAnimation()
                binding.toggleDifficultyLevel.checkedTogglePosition = 0
            }
        } else {
            binding.etSprayValue.isEnabled = true
            binding.etSprayValue.text?.clear()
            binding.imgSprayPower.setImageResource(R.drawable.ic_start)
            if (stirrerOnForPump && !stirrerOn) {
                binding.lavStirrer.pauseAnimation()
                binding.lavStirrer.viewGone()
                binding.imgStirrerPower.setImageResource(R.drawable.ic_start)
            }
        }
    }

    private fun setStirrerOn(statusOn: Boolean) {
        stirrerOn = statusOn
        DebugLog.e("stirrerStatus StatusOn $statusOn")
        if (statusOn) {
            binding.imgStirrerPower.setImageResource(R.drawable.stop)
            binding.lavStirrer.viewShow()
            binding.lavStirrer.playAnimation()
            binding.toggleDifficultyLevel.checkedTogglePosition = selectedPosition
        } else {
            binding.lavStirrer.pauseAnimation()
            binding.lavStirrer.viewGone()
            binding.imgStirrerPower.setImageResource(R.drawable.ic_start)
        }
    }

    //-------Set fry mode stirrer status (on/off)-------//
    private fun setFryModeStirrerState(isStirrerOn: Boolean) {
        fryModeStirrerOn = isStirrerOn
        DebugLog.e("stirrerStatus StatusOn $isStirrerOn")
        if (isStirrerOn) {
            binding.imgFryModeStirrerOnOff.setImageResource(R.drawable.stop)
            binding.lottieAnimationFryModeStirrer.viewShow()
            binding.lottieAnimationFryModeStirrer.playAnimation()
            binding.toggleSwitchFryModeStirrerLevel.checkedTogglePosition =
                fryModeStirrerSelectedPosition
        } else {
            binding.lottieAnimationFryModeStirrer.pauseAnimation()
            binding.lottieAnimationFryModeStirrer.viewGone()
            binding.imgFryModeStirrerOnOff.setImageResource(R.drawable.ic_start)
        }
    }

    private fun prepareModes() {
        modesList.clear()
//        modesList.add(
//            Modes(
//                R.drawable.ic_start,
//                "Start"
//            )
//        )
        modesList.add(
            Modes(
                R.drawable.ic_bake, "Bake"
            )
        )
        modesList.add(
            Modes(
                R.drawable.ic_pressure, "Pressure"
            )
        )
        modesList.add(
            Modes(
                R.drawable.ic_soup, "Soup"
            )
        )
        modesList.add(
            Modes(
                R.drawable.ic_keep_warm, "Keep warm"
            )
        )
    }

    private fun prepareQuickAccessData() {
        quickAccessList.clear()
        //quickAccessList.add(QuickAccess(R.drawable.ic_start, "Start", "Turn on Start Cooking now"))
        quickAccessList.add(
            QuickAccess(
                R.drawable.ic_schedule, "Schedule", "Schedule cooking in advance"
            )
        )
    }

    override fun onResume() {
        super.onResume()
        Log.e("Prince>>>", "OnResume RecipeFragment:")
        if (Constants.IS_TABLET)
            (requireActivity() as HomeTvActivity).setToolbar(macAddress, true)
        else (requireActivity() as HomeActivity).setToolbar(macAddress, true)
        DebugLog.e("onREsume")
        if (OnToCookApplication.rxBleClient.isScanRuntimePermissionGranted) {
//            if (isBluetoothOn() && !OnToCookApplication.instance.isDeviceConnected())
//                Constants.prepareScanObserver()
        }
//        Handler(Looper.getMainLooper()).postDelayed({
//            if (isBluetoothOn() && service.isDeviceConnected(macAddress)) checkStatus(Constants.CheckStatus.DEFAULT)
//        }, 100)
        requireActivity().bindService(
            Intent(requireContext(), BleService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION)
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
        if (checkStatus == Constants.CheckStatus.FTPCONNECTION) {
            val wifiManager =
                requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
            DebugLog.e("wifiManager.isWifiEnabled ${wifiManager.isWifiEnabled}")
            if (wifiManager.isWifiEnabled) {
                DialogUtils().commonDialog(requireContext(),
                    getString(R.string.txt_delete_device),
                    getString(R.string.txt_to_enable_ftp_connection_please_turn_off_wifi),
                    getString(R.string.button_yes),
                    getString(R.string.button_cancel),
                    true,
                    isCancelable = true,
                    {
                        openRecipeListDialog()
                    },
                    {

                    })
            } else {
                requireActivity().runOnUiThread {
                    val FTPDialog = FTPDialog(macAddress)
                    FTPDialog.show(childFragmentManager, "")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        DebugLog.e("unregisterReceiver")
        activity?.unbindService(
            mConnection
        )
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(communicationReceiver)
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

    private fun updateIndTime(status: String) {
        if (isAdded) when (status) {
            Constants.FINISH -> {
                indTimer?.cancel()
                binding.tvPowerCount.text = "100 %"
                binding.indPause.visibility = View.GONE
                indStatus = Constants.Status.STOP
                binding.imgPowerInd.setImageResource(R.drawable.ic_start)
                binding.tvInd.text = requireContext().getString(R.string.txt_start_ind)
                binding.tvIndTime.text = "00 : 00"
            }

            Constants.PAUSE -> {
                indTimer?.cancel()
                indStatus = Constants.Status.PAUSE
                binding.indPause.visibility = View.VISIBLE
                binding.imgPowerInd.setImageResource(R.drawable.stop)
                binding.indPause.setImageResource(R.drawable.ic_resume)
                binding.tvInd.text = requireContext().getString(R.string.txt_ind_paused)
            }

            else -> {
                indStatus = Constants.Status.START
                binding.tvIndTime.text = status
            }
        }
    }

    private fun updateMagTime(str: String) {
        if (isAdded) when (str) {
            Constants.FINISH -> {
                magTimer?.cancel()
                magStatus = Constants.Status.STOP
                binding.magPause.visibility = View.GONE
                binding.imgPowerMag.setImageResource(R.drawable.ic_start)
                binding.tvMag.text = getString(R.string.txt_start_mag)
                binding.tvMagTime.text = "00 : 00"
                binding.tvPowerCountMag.text = "100 %"
            }

            Constants.PAUSE -> {
                magTimer?.cancel()
                magStatus = Constants.Status.PAUSE
                binding.magPause.visibility = View.VISIBLE
                binding.magPause.setImageResource(R.drawable.ic_resume)
                binding.imgPowerMag.setImageResource(R.drawable.stop)
                binding.tvMag.text = getString(R.string.txt_mag_paused)
            }

            else -> {
                magStatus = Constants.Status.START
                binding.tvMagTime.text = str
            }
        }
    }

    private fun updateIndPower(indPower: String) {
        Log.e("TAG", "updateIndPower: $indPower")
        binding.tvPowerCount.text = "$indPower %"
    }

    //-------After check status of device and device is available(free(in idle state)), need to perform task, for which we check status-------//
    private fun isAvailable() {
        when (checkStatus) {
            Constants.CheckStatus.MAGQUICKSTART -> {
                service.writeData(
                    macAddress, Constants.MAGQUICKSTART.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            Constants.CheckStatus.INDQUICKSTART -> {
                service.writeData(
                    macAddress, Constants.INDQUICKSTART.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            Constants.CheckStatus.FTPCONNECTION -> {
                val wifiManager =
                    requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
                DebugLog.e("wifiManager.isWifiEnabled ${wifiManager.isWifiEnabled}")
                if (wifiManager.isWifiEnabled) {
                    DialogUtils().commonDialog(requireContext(),
                        getString(R.string.txt_delete_device),
                        getString(R.string.txt_to_enable_ftp_connection_please_turn_off_wifi),
                        getString(R.string.button_yes),
                        getString(R.string.button_cancel),
                        true,
                        isCancelable = true,
                        {
                            openRecipeListDialog()
                        },
                        {

                        })
                } else {
                    openRecipeListDialog()
                }

            }

            Constants.CheckStatus.SPRINKLE -> {
                service.writeData(
                    macAddress,
                    (Constants.PUMP_ON + binding.etSprinkleValue.text!!).toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            Constants.CheckStatus.SPRAY -> {
                service.writeData(
                    macAddress,
                    (Constants.PURGE_ON + binding.etSprayValue.text!!).toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            else -> {

            }
        }
        checkStatus = Constants.CheckStatus.DEFAULT
    }

    private fun openAudioDialog() {
        DialogUtils().selectAudioTypeDialog(
            requireContext(),
            isAppLogoDisplay = true,
            isCancelable = true
        ) {
            selectedType = it
            openFilePicker(selectedZip = Constants.AUDIO)
        }

//        DialogUtils().commonDialog(
//            context = this,
//            title = getString(R.string.title_permission_open_settings),
//            message = getString(R.string.message_storage_camera_permission),
//            positiveButton = getString(R.string.button_setting),
//            negativeButton = getString(R.string.button_cancel),
//            isAppLogoDisplay = true,
//            isCancelable = true,
//            callbackSuccess = {

//
//            },
//            callbackNegative = {

//            })
    }

    private fun startIndTimer(long: Long) {
        binding.tvInd.text = getString(R.string.txt_ind_started)
        indStatus = Constants.Status.START
        binding.indPause.setImageResource(R.drawable.ic_pause)
        binding.indPause.visibility = View.VISIBLE
        binding.imgPowerInd.setImageResource(R.drawable.stop)
        indTimer?.cancel()
        indTimer = object : CountDownTimer(long, 1000) {
            override fun onTick(millis: Long) {
                val str = Constants.getFormattedTime(millis)
                binding.tvIndTime.text = str
            }

            override fun onFinish() {
                indStatus = Constants.Status.STOP
                updateIndTime(Constants.FINISH)
            }
        }.start()
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
                Log.e("PrinceEWW>>>", "unZipNew - Execute finally for validateRecipeAndInsert with txtFileName - $txtFileName")
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
            requireActivity().runOnUiThread {
                Toast.makeText(
                    requireContext(), "Invalid Text File", Toast.LENGTH_SHORT
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
                DebugLog.e("validateRecipeAndInsert Inner")
                Toast.makeText(requireContext(), "Invalid Type", Toast.LENGTH_SHORT).show()
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
                            var imgFile = File(location, "${it.title + "_" + it.id}.jpg")
                            if (!imgFile.exists()) {
                                imgFile = File(location, "${it.title}.jpg")
                            }
                            it.image = Uri.fromFile(imgFile).toString()
                        }
                        if (it.pan_type.isNullOrEmpty()) {
                            it.pan_type = Constants.STAINLESS_STEEL
                        }
                    }
                        recipe.Instruction.forEachIndexed { index, it ->
                        if (it.image.isNotEmpty()) {
                            var imgFile = File(location, "${"inst" + it.Text + "_" + it.id}.jpg")
                            if (!imgFile.exists()) {
                                imgFile = File(location, "${it.Text}.jpg")
                            }
                            it.image = Uri.fromFile(imgFile).toString()
                        }
                        if (it.durationInSec == 0) {
                            try {
                                it.durationInSec = it.Induction_on_time.toInt()
                                    .coerceAtLeast(it.Magnetron_on_time.toInt())
                            } catch (e: Exception) {
                                DebugLog.e("Exception ${e.message}")
                            }
                        }
                        if (it.threshold.isNullOrEmpty()) {
                            it.threshold = "0"
                        }
//                        if (!it.audioIUrl.isNullOrEmpty()) {
//                            var imgFile = File(location, "${it.audioI}.mp3")
////                            if (!imgFile.exists()) {
////                                imgFile = File(location, "${it.audioI}.mp3")
////                            }
//                            it.audioIUrl = Uri.fromFile(imgFile).toString()
//                        }
                    }
                    recipe.Instruction.forEachIndexed { index, it ->
                        if (!it.audioPUrl.isNullOrEmpty()) {
                            var audioPFile = File(location, "${it.audioP}.mp3")
                            if (audioPFile.exists()) {
                                val externalStorageDir1 = requireContext().filesDir
                                val des = File(externalStorageDir1, Constants.ACTION_AUDIO_PATH)
                                if (!des.exists()) {
                                    des.mkdirs()
                                }
                                DebugLog.e("ACTION_AUDIO_PATH ${des.exists()}")
                                moveFile(audioPFile.path, des.path, audioPFile.name)
                            }
                        }
                        if (!it.audioQUrl.isNullOrEmpty()) {
                            var audioQFile = File(location, "${it.audioQ}.mp3")
                            if (audioQFile.exists()) {
                                val externalStorageDir1 = requireContext().filesDir
                                val des = File(externalStorageDir1, Constants.QTY_AUDIO_PATH)
                                if (!des.exists()) {
                                    des.mkdirs()
                                }
                                DebugLog.e("ACTION_AUDIO_PATH ${des.exists()}")
                                moveFile(audioQFile.path, des.path, audioQFile.name)
                            }
                        }
                        if (!it.audioIUrl.isNullOrEmpty()) {
                            var audioIFile = File(location, "${it.audioI}.mp3")
                            if (audioIFile.exists()) {
                                val externalStorageDir1 = requireContext().filesDir
                                val des =
                                    File(externalStorageDir1, Constants.INGREDIENTS_AUDIO_PATH)
                                if (!des.exists()) {
                                    des.mkdirs()
                                }
                                DebugLog.e("ACTION_AUDIO_PATH ${des.exists()}")
                                moveFile(audioIFile.path, des.path, audioIFile.name)
                            }
                        }
                    }

                    recipe.id = 0
                    DebugLog.e("Recipe Id ${recipe.id}")
                    OnToCookApplication.dbInstance.recipeDao().insert(recipe)
                }
            } else {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), String.format(
                        resources.getString(R.string.dynamic_message_recipe_already_added),
                        recipe.name[0]
                    ), Toast.LENGTH_SHORT).show()
                }
            }
            DebugLog.e("checkAndInsert: ${Gson().toJson(recipe)}")
//            } catch (e: IOException) {
//                DebugLog.e("checkAndInsert: IOException${e.message}")
//                DebugLog.e("checkAndInsert:IOException ${e.printStackTrace()}")
//                //You'll need to add proper error handling here
//            }
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

    private fun startMagTimer(long: Long) {
        magStatus = Constants.Status.START
        binding.imgPowerMag.setImageResource(R.drawable.stop)
        binding.tvMag.text = getString(R.string.txt_mag_started)
        binding.magPause.setImageResource(R.drawable.ic_pause)
        binding.magPause.visibility = View.VISIBLE
        magTimer?.cancel()
        magTimer = object : CountDownTimer(long, 1000) {
            override fun onTick(millis: Long) {
                val str = Constants.getFormattedTime(millis)
                binding.tvMagTime.text = str
            }

            override fun onFinish() {
                updateMagTime(Constants.FINISH)
            }
        }.start()
    }

    //-------Change status of quick fry mode microwave-------//
    private fun changeFryModeMicrowaveStatus(
        changeStatusTo: Constants.Status,
        fryModeMicrowaveTime: Long = TimeUnit.MINUTES.toMillis(Constants.DEFAULT_TIME), //Default time of microwave is 4 minute
    ) {
        fryModeMagStatus = changeStatusTo
        when (fryModeMagStatus) {
            Constants.Status.START -> {
//                fryModeStirrerOn = true
                fryModeMicrowaveTimeLocalTime =
                    30 //After start microwave for fry mode, we have to set local time of microwave of fry mode which is 30 sec
                binding.imgFryModeMicrowaveOnOff.setImageResource(R.drawable.stop)
                binding.imgFryModeMicrowavePauseResume.visible()
                binding.imgFryModeMicrowavePauseResume.setImageResource(R.drawable.ic_pause)
                binding.tvFryModeMicrowave.text = getString(R.string.txt_mag_started)
                updateFryModeMicrowaveTime(fryModeMicrowaveTime = fryModeMicrowaveTime)
            }

            Constants.Status.STOP -> {
//                fryModeStirrerOn = false
                binding.imgFryModeMicrowaveOnOff.setImageResource(R.drawable.ic_start)
                binding.imgFryModeMicrowavePauseResume.gone()
                quickFryModeMicrowaveTimer?.cancel()
                fryModeMicrowaveTimeLocalTime =
                    30 //After stop microwave for fry mode, we have to set local time of microwave of fry mode which is 30 sec
                binding.tvFryModeMicrowaveTime.text =
                    Constants.getFormattedTime(fryModeMicrowaveTimeLocalTime * 1000)
                binding.tvFryModeMicrowavePowerCount.text = "100 %"
                binding.tvFryModeMicrowave.text = getString(R.string.txt_start_mag)
            }

            Constants.Status.PAUSE -> {
                binding.imgFryModeMicrowaveOnOff.setImageResource(R.drawable.stop)
                binding.imgFryModeMicrowavePauseResume.setImageResource(R.drawable.ic_resume)
                binding.tvFryModeMicrowave.text = getString(R.string.txt_mag_paused)
                quickFryModeMicrowaveTimer?.cancel()
            }

            else -> {

            }
        }
    }

    //-------Update time of quick fry mode microwave-------//
    private fun updateFryModeMicrowaveTime(
        fryModeMicrowaveTime: Long = TimeUnit.MINUTES.toMillis(Constants.DEFAULT_TIME), //Default time of microwave is 4 minute
    ) {
        quickFryModeMicrowaveTimer?.cancel()
        quickFryModeMicrowaveTimer = object : CountDownTimer(fryModeMicrowaveTime, 1000) {
            override fun onTick(millis: Long) {
                val str = Constants.getFormattedTime(millis)
                binding.tvFryModeMicrowaveTime.text = str
            }

            override fun onFinish() {
                //-------On finish timer we have to stop fry mode-------//
                fryModeMagStatus = Constants.Status.STOP
                quickFryModeMicrowaveTimer?.cancel()
                binding.tvFryModeTime.text = Constants.getFormattedTime(currentTimeOfFryMode)
                service.writeData(
                    macAddress,
                    Constants.MAGQUICKSTOP.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
        }.start()
    }
    private fun startHotspot() {
        if (this::reservationG.isInitialized)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reservationG.close()
            }
        val wifiManager =
            requireActivity().getSystemService(Context.WIFI_SERVICE) as WifiManager
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


//            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = RecipeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
                putString(ARG_PARAM2, param2)
            }
        }
    }
}