package com.invent.ontocook

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication.Companion.rxBleClient
import com.invent.ontocook.adapter.*
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.floatingsearch.RxSearchObservable
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.*
import com.invent.ontocook.models.RecipeList
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.*
import com.invent.ontocook.utils.Constants.FINISH
import com.invent.ontocook.utils.Constants.IDLE
import com.invent.ontocook.utils.Constants.INGREDIENT_MODE
import com.invent.ontocook.utils.Constants.PAUSE
import com.invent.ontocook.utils.Constants.REC_SEL_MODE
import com.rx2androidnetworking.Rx2AndroidNetworking
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_cooking.*
import kotlinx.android.synthetic.main.activity_recipe_details2.*
import kotlinx.android.synthetic.main.view_header.*
import kotlinx.android.synthetic.main.view_header.ivLeft
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CookingActivity : AppCompatActivity() {

    private lateinit var recentItemListAdapter: RecentItemListAdapter
    private lateinit var recentAddedAdapter: AddedRecipeAdapter
    private lateinit var quickAccessListAdapter: QuickAccessListAdapter
    private lateinit var modesItemListAdapter: ModesItemListAdapter
    private lateinit var recommendItemListAdapter: RecommendItemListAdapter
    private lateinit var communicationReceiver: BroadcastReceiver

    val BUFFER_SIZE = 4096
    private val REQUEST_PERMISSION_EXTERNAL = 103
    private var quickAccessList = mutableListOf<QuickAccess>()

    //var recentItemList = mutableListOf<RecentItem>()
    private var modesList = mutableListOf<Modes>()
    private var deviceScanDialog: DeviceScanDialog? = null
    lateinit var broadcastReceiver: BroadcastReceiver
    private var indStart = false
    private var magStart = false
    private var recommendedItems = mutableListOf<RecentItem>()
    private var allRecipeItems = mutableListOf<RecentItem>()
    private var allRecipeList: MutableList<Recipe> = mutableListOf()
    private var listRecipe = mutableListOf<Recipe>()
    private var zipFileShare = ArrayList<File>()
    private var indStatus = Constants.Status.DEFAULT
    private var magStatus = Constants.Status.DEFAULT
    var status: Int = -1
    val TAG: String = this::class.java.simpleName

    //private var tts : TextToSpeech? = null
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

                    val path = makeFileCopyInCacheDir(this, uri)
                    val file = File(path)
                    Thread {
                        runOnUiThread {
                            LoadingUtils.showLoading(this, false)
                        }
                        val root = File(externalCacheDir, "Recipes")
                        if (!root.exists()) {
                            root.mkdirs()
                        }
                        val subRoot = File(root, file!!.nameWithoutExtension)
                        if (!subRoot.exists()) {
                            subRoot.mkdirs()
                        }
                        unZipNew(file, subRoot.path).also {
                            runOnUiThread {
                                LoadingUtils.hideDialog()
                            }
                        }
                    }.start()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooking)

        if (ContextCompat.checkSelfPermission(
                this@CookingActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@CookingActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
            } else {
                ActivityCompat.requestPermissions(
                    this@CookingActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101
                )
            }
        }

        init()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == Constants.REQUEST_ZIP && resultCode == RESULT_OK) {
//            val filePath = data!!.data
//            val file = filePath?.path?.let { File(it) }
//            val data: Intent? = data.data
//            data?.data?.also { uri ->
//
//                //Permission needed if you want to retain access even after reboot
//                contentResolver.takePersistableUriPermission(
//                    uri,
//                    Intent.FLAG_GRANT_READ_URI_PERMISSION
//                )
//                // Perform operations on the document using its URI.
//
//                val path = makeFileCopyInCacheDir(uri)
//                binding.tvPathValue.text = path
//            }
//
////            Thread {
////                runOnUiThread {
////                    LoadingUtils.showLoading(this, false)
////                }
////                val root = File(externalCacheDir, "Recipes")
////                if (!root.exists()) {
////                    root.mkdirs()
////                }
////                val subRoot = File(root, file!!.nameWithoutExtension)
////                if (!subRoot.exists()) {
////                    subRoot.mkdirs()
////                }
////                unZipNew(file, subRoot.path).also {
////                    runOnUiThread {
////                        LoadingUtils.hideDialog()
////                    }
////                }
////            }.start()
//        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION_EXTERNAL) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun redirectToCooking() {
        OnToCookApplication.instance.bleBoundService.writeData(
            "recipe=1".toByteArray(
                Charsets.UTF_8
            )
        )
        val intent = Intent(applicationContext, DashboardActivity::class.java)
        //intent.putExtra("recipe", recentItem?.id.toString())
        startActivity(intent)
    }

    class Suggestion(var suggestionId: Int = 0, var suggestionText: String = "") :
        SearchSuggestion {

        constructor(parcel: Parcel) : this()

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            TODO("Not yet implemented")
        }

        override fun getBody(): String {
            return suggestionText
        }

        companion object CREATOR : Parcelable.Creator<Suggestion> {
            override fun createFromParcel(parcel: Parcel): Suggestion {
                return Suggestion(parcel)
            }

            override fun newArray(size: Int): Array<Suggestion?> {
                return arrayOfNulls(size)
            }
        }
    }

    private fun init() {

        val properties = DialogProperties()
        properties.selection_mode = DialogConfigs.SINGLE_MODE
        properties.selection_type = DialogConfigs.FILE_SELECT
        properties.root = File(DialogConfigs.DEFAULT_DIR)
        properties.error_dir = File(DialogConfigs.DEFAULT_DIR)
        properties.offset = File(DialogConfigs.DEFAULT_DIR)
        properties.extensions = null
        properties.show_hidden_files = false

        RxSearchObservable.fromView(floatingSearchView)?.debounce(500, TimeUnit.MILLISECONDS)
            ?.filter(Predicate {
                return@Predicate it.isNotEmpty()
            })?.distinctUntilChanged()?.switchMap {
                println("call api  ---   $it")
                Rx2AndroidNetworking.get(Constants.RECIPE_LISTING_URL + "&query=${it}").build()
                    .getObjectListObservable(RecipeList::class.java).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe({
                floatingSearchView.clearSuggestions()
                val listOfSuggestion = mutableListOf<Suggestion>()
                it.reverse()
                for (list in it) {
                    listOfSuggestion.add(Suggestion(list.id, list.title))
                    println("recipe listing   ${list.id}   ${list.title}")
                }
                floatingSearchView.swapSuggestions(listOfSuggestion)
            }, {
                println("Error   ${it.localizedMessage}")
            })

        floatingSearchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSearchAction(currentQuery: String?) {

            }

            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion?) {
                //var suggestionId = (searchSuggestion as Suggestion).suggestionId
                val suggestionQuery = (searchSuggestion as Suggestion).suggestionText
                clRecipeSearch.visibility = View.GONE
                floatingSearchView.clearQuery()
                floatingSearchView.clearSearchFocus()

//                startActivity(Intent(this@CookingActivity, CreateRecipeAddStepsActivity::class.java)
//                    .putExtra(CreateRecipeAddStepsActivity.RECIPE_ID, suggestionId))

                startActivity(
                    Intent(this@CookingActivity, CreateRecipeAddStepsActivity::class.java).putExtra(
                        CreateRecipeAddStepsActivity.QUERY, suggestionQuery
                    )
                )
            }
        })

        floatingSearchView.setOnClearSearchActionListener {
            floatingSearchView.clearSuggestions()
        }

        floatingSearchView.setOnFocusChangeListener(object :
            FloatingSearchView.OnFocusChangeListener {
            override fun onFocusCleared() {
                clRecipeSearch.visibility = View.GONE
            }

            override fun onFocus() {
            }
        })

        imgPowerInd.setOnClickListener {
            imgPowerInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                imgPowerInd.isEnabled = true
            }, 500)
            Log.e(TAG, "init: InsStart $indStart")
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START || indStatus == Constants.Status.PAUSE) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        Constants.INDQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    checkStatus(2)
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        imgPowerMag.setOnClickListener {
            Log.e(TAG, "init: InsStart $magStart")
            imgPowerMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                imgPowerMag.isEnabled = true
            }, 500)
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (magStatus == Constants.Status.START || magStatus == Constants.Status.PAUSE) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        Constants.MAGQUICKSTOP.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    checkStatus(1)
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvPlusInd.setOnClickListener {
            tvPlusInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvPlusInd.isEnabled = true
            }, 500)
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "INDPROCESSTIME=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    Toast.makeText(
                        this@CookingActivity, getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvPlusMag.setOnClickListener {
            tvPlusMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvPlusMag.isEnabled = true
            }, 500)
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (magStatus == Constants.Status.START) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "MAGPROCESSTIME=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    Toast.makeText(
                        this@CookingActivity, "Please start Magnetron", Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvMinusInd.setOnClickListener {
            tvMinusInd.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvMinusInd.isEnabled = true
            }, 500)
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START) {
                    Log.e("---", "init: ${tvIndTime.text.toString().split(":")[0].trim()}")
                    if (tvIndTime.text.toString()
                            .split(":")[0].trim() != "00" || (tvIndTime.text.toString()
                            .split(":")[0].trim() == "00" && tvIndTime.text.toString()
                            .split(":")[1].trim().toInt() > 11)
                    ) OnToCookApplication.instance.bleBoundService.writeData(
                        "INDPROCESSTIME=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        Toast.makeText(
                            this@CookingActivity,
                            getString(R.string.strShortSec),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@CookingActivity, getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvMinusMag.setOnClickListener {
            tvMinusMag.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvMinusMag.isEnabled = true
            }, 500)
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (magStatus == Constants.Status.START) {
                    if (tvMagTime.text.toString()
                            .split(":")[0].trim() != "00" || (tvMagTime.text.toString()
                            .split(":")[0].trim() == "00" && tvMagTime.text.toString()
                            .split(":")[1].trim().toInt() > 11)
                    ) OnToCookApplication.instance.bleBoundService.writeData(
                        "MAGPROCESSTIME=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else Toast.makeText(
                        this@CookingActivity, getString(R.string.strShortSec), Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@CookingActivity, getString(R.string.strStartMag), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvPlusPower.setOnClickListener {
            tvPlusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvPlusPower.isEnabled = true
            }, 500)
            Log.e(TAG, "init: PowerPlusStart")
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START) {
                    if (tvPowerCount.text.toString().replace("%", "").trim()
                            .toInt() != 100
                    ) OnToCookApplication.instance.bleBoundService.writeData(
                        "INDPOWER=10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        if (tvPowerCount.text.toString().replace("%", "").trim()
                                .toInt() == 100
                        ) Toast.makeText(
                            this@CookingActivity,
                            getString(R.string.strPowerMax),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@CookingActivity, getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        tvMinusPower.setOnClickListener {
            tvMinusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvMinusPower.isEnabled = true
            }, 500)
            Log.e(TAG, "init: PowerMinusStart")
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START) {
                    Log.e(TAG, "init: ${tvPowerCount.text.toString().replace("%", "").trim()}")
                    if (tvPowerCount.text.toString().replace("%", "").trim()
                            .toInt() != 10
                    ) OnToCookApplication.instance.bleBoundService.writeData(
                        "INDPOWER=-10".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    else {
                        if (tvPowerCount.text.toString().replace("%", "").trim()
                                .toInt() == 10
                        ) Toast.makeText(
                            this@CookingActivity,
                            getString(R.string.strPowerMin),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@CookingActivity, getString(R.string.strStartInd), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        indPause.setOnClickListener {
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (indStatus == Constants.Status.START) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "INDQUICKSTART=PAUSE".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "INDQUICKSTART=RESUME".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        magPause.setOnClickListener {
            tvMinusPower.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                tvMinusPower.isEnabled = true
            }, 500)
            Log.e(TAG, "init: PowerMinusStart")
            if (OnToCookApplication.instance.isDeviceConnected()) {
                if (magStatus == Constants.Status.START) {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "MAGQUICKSTART=PAUSE".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } else {
                    OnToCookApplication.instance.bleBoundService.writeData(
                        "MAGQUICKSTART=RESUME".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } else {
                Toast.makeText(
                    this@CookingActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                toggleDeviceScanDialog()
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
//                        redirectToCooking()

                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        Constants.prepareScanObserver()
//                        onBackPressed()

//                        var message =
//                            intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
//                        Toast.makeText(this@CookingActivity, message, Toast.LENGTH_LONG).show()
//                        toggleDeviceScanDialog()

                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        if (message.lowercase().contains("info=")) {
                            var messageChunk =
                                message.lowercase().toString().replace("info=", "").split(",")

                            println("receive message..... $message")

                            var recipeNo = messageChunk[0]
                            var ingredient =
                                if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                            var stepno = if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                            var second = if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                            var foundRecipe =
                                Constants.RECIPES.filter { it.id.toString() == recipeNo }
                            if (foundRecipe.isNotEmpty()) {
                                var intent =
                                    Intent(this@CookingActivity, DashboardActivity::class.java)
                                intent.putExtra("recipe", foundRecipe[0])
                                intent.putExtra("prepreparestep", ingredient)
                                intent.putExtra("currentstep", stepno)
                                intent.putExtra("remainTime", second)
                                startActivity(intent)
                            }
                        }
//                        if (message.toUpperCase().contains("MAGQUICKSTART=START")) {
//                            tvMag.text = getString(R.string.txt_mag_started)
//                            startMagTimer(180000)
//                        }
                        if (message.toUpperCase().contains("INDPOWER=")) {
                            if (!message.contains(",")) {
                                val power = message.replace("INDPOWER=", "")
                                tvPowerCount.text = "$power %"
                            }
                        }
//                        if (message.toUpperCase().contains("INDPROCESSTIME=")) {
//                            tvInd.text = getString(R.string.txt_ind_started)
//                            indTimer?.cancel()
//                            val long = message.replace("INDPROCESSTIME=", "").toLong()
//                            Log.e(TAG, "onReceive: INDPROCESSTIME$long")
//                            startIndTimer(long * 1000)
//                        }
//                        if (message.toUpperCase().contains("MAGPROCESSTIME=")) {
//                            tvMag.text = getString(R.string.txt_mag_started)
//                            magTimer?.cancel()
//                            val long = message.replace("MAGPROCESSTIME=", "").toLong()
//                            Log.e(TAG, "onReceive: MAGPROCESSTIME$long")
//                            startMagTimer(long * 1000)
//                        }
//                        if (message.toUpperCase().contains("MAGQUICKSTART=STOP")) {
//                            stopMagTimer()
//                        }


                        if (message.toUpperCase().contains("INDQUICKSTART=")) {
                            if (message.contains(",")) {
                                val messageSize = message.split(",").size
                                if (messageSize > 0) {
                                    val indStatus = message.toUpperCase().split(",")[0].replace(
                                        "INDQUICKSTART=", ""
                                    )
                                    val indTime = message.uppercase().split(",")[1].replace(
                                        "IND_RUN=", ""
                                    )
                                    if (indStatus == IDLE && indTime.toInt() == 0) {
                                        isAvailable()
                                    } else {
                                        if (messageSize > 2) {
                                            val magTime = message.uppercase().split(",")[3].replace(
                                                "MAG_RUN=", ""
                                            )
                                            val magStatus =
                                                message.toUpperCase().split(",")[2].replace(
                                                    "MAGQUICKSTART=", ""
                                                )
                                            if (magStatus == IDLE && magTime.toInt() == 0) {
                                                isAvailable()
                                            }
                                        }
                                    }
                                }
                            } else {
//                                if (message.toUpperCase() == INDQUICKSTART) {
//                                    tvInd.text = getString(R.string.txt_ind_started)
//                                    startIndTimer(180000)
//                                } else if (message.toUpperCase() == INDQUICKSTOP) {
//                                    stopIndTimer()
//                                }
                            }

                        }
                        if (message.lowercase() == Constants.IDLE_DEVICE) {
                            isAvailable()
                        }

                        if (message.lowercase().contains("recipe=")) {
                            if (!message.contains(",")) {
                                var foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.lowercase()
                                        .replace("recipe=", "")
                                }
                                if (foundRecipe.isNotEmpty()) {
                                    val intent =
                                        Intent(applicationContext, DashboardActivity::class.java)
                                    intent.putExtra("recipe", foundRecipe[0])
                                    intent.putExtra("isResume", false)
                                    startActivity(intent)
                                } else {
                                    listRecipe.mapIndexed { index, recipeDb ->
                                        if (message.split(",")[0].lowercase().replace(
                                                "recipe=", ""
                                            ) == recipeDb.name[0].lowercase()
                                        ) {
                                            val recentItem = Constants.getRecentItemFromDb(
                                                recipeDb
                                            )
                                            val recipe = Constants.convertNewJsonToOldJson(
                                                recipeDb
                                            )
                                            recentItem.recipe = Gson().toJson(recipe)
                                            var intent = Intent(
                                                applicationContext, DashboardActivity::class.java
                                            )
                                            intent.putExtra("isResume", false)
                                            intent.putExtra("recipe", recentItem)
                                            startActivity(intent)
                                        }
                                    }
//                                    var foundRecipe = listRecipe.filter {
//                                        Log.e(TAG, "onReceive: ${it.name}")
//                                        val nameType =
//                                            object : TypeToken<ArrayList<String>>() {}.type
//                                        val nameList: ArrayList<String> =
//                                            Gson().fromJson(it.name, nameType)
//                                        nameList[0].lowercase() == message.lowercase()
//                                            .replace("recipe=", "")
//                                    }
//                                    if (foundRecipe.isNotEmpty()) {
//                                        val recipe =
//                                            Constants.convertNewJsonToOldJson(foundRecipe[0])
//                                        recentItem!!.recipe = Gson().toJson(recipe)
//                                        var intent =
//                                            Intent(
//                                                applicationContext,
//                                                DashboardActivity::class.java
//                                            )
//                                        intent.putExtra("recipe", recentItem)
//                                        intent.putExtra("isResume", false)
//                                        startActivity(intent)
//                                    }
                                }
                            } else {
                                val foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.split(",")[0].lowercase()
                                        .replace("recipe=", "")
                                }
                                val cmdSize: Int = message.split(",").size

                                val stepNo = if (cmdSize > 2) message.split(",")[2].lowercase()
                                    .replace("stepno=", "") else "0"
                                if (cmdSize > 1) when (message.split(",")[1].toUpperCase()
                                    .replace("MODE=", "").toUpperCase()) {
                                    INGREDIENT_MODE -> {
                                        if (status != -1 && status != 0) {
                                            Toast.makeText(
                                                this@CookingActivity,
                                                getString(R.string.txt_recipemode),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        if (foundRecipe.isNotEmpty()) {
//                                                val intent =
//                                                    Intent(
//                                                        applicationContext,
//                                                        DashboardActivity::class.java
//                                                    )
//                                                intent.putExtra("recipe", foundRecipe[0])
//                                                intent.putExtra("isResume", true)
//                                                intent.putExtra(
//                                                    "prepreparestep",
//                                                    stepNo.toInt() - 1
//                                                )
//                                                intent.putExtra("isPrepareRunning", true)
//                                                startActivity(intent)
                                        }
                                    }

                                    Constants.COOKING_MODE -> {
                                        if (status != -1 && status != 0) {
                                            Toast.makeText(
                                                this@CookingActivity,
                                                getString(R.string.txt_recipemode),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        if (foundRecipe.isNotEmpty()) {

//                                                val indTime = message.split(",")[3].lowercase()
//                                                    .replace("ind_run=", "")
//                                                val magTime = message.split(",")[4].lowercase()
//                                                    .replace("mag_run=", "")
//                                                val intent =
//                                                    Intent(
//                                                        applicationContext,
//                                                        DashboardActivity::class.java
//                                                    )
//                                                intent.putExtra("recipe", foundRecipe[0])
//                                                intent.putExtra("isResume", true)
//                                                intent.putExtra("currentStep", stepNo.toInt())
//                                                intent.putExtra("isPrepareRunning", false)
//                                                intent.putExtra(
//                                                    "isPlaying",
//                                                    indTime.toInt()
//                                                        .coerceAtLeast(magTime.toInt()) != 0
//                                                )
//                                                intent.putExtra(
//                                                    "changeTime",
//                                                    indTime.toInt().coerceAtLeast(magTime.toInt())
//                                                )
//                                                startActivity(intent)
                                        }
                                    }

                                    REC_SEL_MODE -> {
                                        isAvailable()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        clAddNewRecipe.setOnClickListener {
//            var intent = Intent(this, FileChooserActivity::class.java)
//            startActivity(intent)
            //startActivity(Intent(this@CookingActivity, CreateRecipeAddStepsActivity::class.java))
            //startActivity(Intent(this@CookingActivity, ExtractWebsiteActivity::class.java))
            clRecipeSearch.visibility = View.VISIBLE
            floatingSearchView.setSearchFocused(true)
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
        clCreateNewRecipe.setOnClickListener {
            val intent = Intent(this@CookingActivity, CreateNewRecipe::class.java)
            intent.putExtra("list", Gson().toJson(recentAddedAdapter.getList()))
            startActivity(intent)
        }
        clInsertRecipe.setOnClickListener {
            PermissionManagerUtils.checkPermission(this,
                this,
                arrayListOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PermissionManagerUtils.PermissionSessionManager(this),
                object : PermissionManagerUtils.PermissionAskListener {
                    override fun onNeedPermission() {
                        ActivityCompat.requestPermissions(
                            this@CookingActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), Constants.REQUEST_CAMERA_PERMISSION
                        )
                    }

                    override fun onPermissionPreviouslyDenied() {
                        ActivityCompat.requestPermissions(
                            this@CookingActivity, arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ), 1
                        )
                    }

                    override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                        //-------------HERE OPEN
                        DialogUtils().commonDialog(context = this@CookingActivity,
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
                        openFilePicker()
                    }

                })
//            val permissionCheck: Int = ContextCompat.checkSelfPermission(
//                this, Manifest.permission.READ_EXTERNAL_STORAGE
//            )
//            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(
//                    this, arrayOf(
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                        Manifest.permission.READ_EXTERNAL_STORAGE
//                    ), REQUEST_PERMISSION_EXTERNAL
//                )
//            } else {
//                openFilePicker()
//            }
        }

        ivLeft.setOnClickListener {
            onBackPressed()
        }
        clShareAllRecipe.setOnClickListener {
            zipFileShare.clear()
            listRecipe.forEach { recipe ->
                val root = File(externalCacheDir, "Recipe")
                if (!root.exists()) {
                    root.mkdirs()
                }
                val listOfFiles = arrayListOf<File>()
                val recipeTextFile = File(root, recipe.name[0] + ".txt")
                val writer = FileOutputStream(recipeTextFile)
                writer.write(Gson().toJson(recipe).toByteArray())
                writer.flush()
                writer.close()
                listOfFiles.add(recipeTextFile)
                if (recipe.imageUrl.isNotEmpty()) {
                    val imageFile = File(root, recipe.name[0] + ".jpg")
                    val imgWriter = FileOutputStream(imageFile)
                    val iStream: InputStream =
                        contentResolver.openInputStream(Uri.parse(recipe.imageUrl))!!
                    val inputData: ByteArray = Constants.getBytes(iStream)!!
                    imgWriter.write(inputData)
                    imgWriter.flush()
                    imgWriter.close()
                    listOfFiles.add(imageFile)
                }
                recipe.Ingredients.forEach {
                    if (it.image.isNotEmpty() && File(it.image).exists()) {
                        val imageFile = File(root, it.title + ".jpg")
                        if (imageFile.exists()) {
                            val imgWriter = FileOutputStream(imageFile)
                            val iStream: InputStream =
                                contentResolver.openInputStream(Uri.parse(it.image))!!
                            val inputData: ByteArray = Constants.getBytes(iStream)!!
                            imgWriter.write(inputData)
                            imgWriter.flush()
                            imgWriter.close()
                            listOfFiles.add(imageFile)
                        }
                    }
                }
                zipFileShare.add(
                    getZipFileFromFiles(
                        listOfFiles, recipe
                    )
                )
            }
            shareZipFile(zipFileShare, "Recipe")
        }

        reRecentItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentItemListAdapter =
            RecentItemListAdapter(object : RecentItemListAdapter.ItemClickListener {
                override fun onItemClick(position: Int, recentlyPlayedRecipe: RecentlyPlayedRecipe) {
                    val intent = Intent(this@CookingActivity, RecipeDetailsActivity::class.java)
                    intent.putExtra("recipe", Constants.RECIPES[position])
                    startActivity(intent)
                }
            })
        reRecentItems.adapter = recentItemListAdapter
//        recentItemListAdapter.setRecentItemList(Constants.RECIPES)
        recentAddedAdapter = AddedRecipeAdapter(object : AddedRecipeAdapter.ItemClickListener {
            override fun onItemClick(position: Int, recipe: Recipe) {
                Log.e(TAG, "onItemClick: ${Gson().toJson(recipe)}")
                var name = ""
                if (recipe.name.isNotEmpty()) {
                    name = recipe.name[0]
                }
                val rexipe = RecentItem(
                    7, R.drawable.ic_vegetable, name, recipe.difficulty, "9 mins", "Indian|Snacks"
                )
                val recipe = Recipe()
                recipe.Ingredients.forEachIndexed { index, ingredients ->
                    val step = IngredientsSteps(
                        ingredients.image, ingredients.title, ingredients.weight, ingredients.text
                    )
                    rexipe.ingredients.add(index, step)
                }
                rexipe.name = name
                recipe.Instruction = recipe.Instruction
                recipe.Ingredients = recipe.Ingredients
                recipe.description = recipe.description
                recipe.imageUrl = recipe.imageUrl
                recipe.difficulty = recipe.difficulty
                Log.e("OnItemClick", "onItemClick: ${Gson().toJson(recipe)}")
                rexipe.recipe = Gson().toJson(recipe, Recipe::class.java)
                val intent = Intent(this@CookingActivity, RecipeDetailsActivity::class.java)
                intent.putExtra("recipe", rexipe)
                intent.putExtra("show", true)
                intent.putExtra("list", Gson().toJson(recentAddedAdapter.getList()))
                startActivity(intent)
            }
        })
        reRecentAddedItems.adapter = recentAddedAdapter

        prepareQuickAccessData()

        reQuickAccess.layoutManager = GridLayoutManager(this, 2)
        quickAccessListAdapter = QuickAccessListAdapter()
        reQuickAccess.adapter = quickAccessListAdapter
        quickAccessListAdapter.setQuickAccessList(quickAccessList)

        prepareModes()

        reModes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        modesItemListAdapter = ModesItemListAdapter()
        reModes.adapter = modesItemListAdapter
        modesItemListAdapter.setModesList(modesList)

        recommendedItems.clear()
        recommendedItems.add(
            RecentItem(
                1, R.drawable.ic_mutton, "Mutton Kofta Curry", "Easy", "9 mins", "Indian|Snacks"
            )
        )
        recommendedItems.add(
            RecentItem(
                2,
                R.drawable.ic_red_saus,
                "Red Sauce Cheese Penne Pasta",
                "Medium",
                "9 mins",
                "Indian|Snacks"
            )
        )

        reRecommend.layoutManager = GridLayoutManager(this, 2)
        recommendItemListAdapter = RecommendItemListAdapter(allRecipeList){i, recipeDb ->  }
        reRecommend.adapter = recommendItemListAdapter
//        recommendItemListAdapter.setModesList(recommendedItems)

        allRecipeItems.clear()
        allRecipeItems.add(
            RecentItem(
                1, R.drawable.ic_vadapao, "Cheese Tomato Vadapav", "Easy", "9 mins", "Indian|Snacks"
            )
        )
        allRecipeItems.add(
            RecentItem(
                2,
                R.drawable.ic_grilled_fish,
                "Grilled fish with lemon and onions",
                "Hard",
                "9 mins",
                "Continental|Snacks"
            )
        )
        allRecipeItems.add(
            RecentItem(
                3,
                R.drawable.ic_fried_chicken,
                "Fried Fiery Chicken",
                "Hard",
                "9 mins",
                "Korean|Snacks"
            )
        )
        allRecipeItems.add(
            RecentItem(
                4,
                R.drawable.ic_samosa,
                "Samosa with tamarind sauce",
                "Medium",
                "9 mins",
                "Indian|Snacks"
            )
        )

        reAllRecipe.layoutManager = GridLayoutManager(this, 2)
//        recommendItemListAdapter = RecommendItemListAdapter()
//        reAllRecipe.adapter = recommendItemListAdapter
//        recommendItemListAdapter.setModesList(allRecipeItems)

    }

    @Throws(IOException::class)
    fun zipAndShare(files: ArrayList<File>, recipe: Recipe? = null, isShare: Boolean) {
        var origin: BufferedInputStream? = null
        val root = File(externalCacheDir, "Recipe")
        if (!root.exists()) {
            root.mkdirs()
        }
        if (isShare) {
            val zipFile = File(root, "Recipe.zip")
            if (!zipFile.exists()) {
                zipFile.createNewFile()
                zipFile.setWritable(true)
            }
            val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
            try {
                val data = ByteArray(BUFFER_SIZE)
                for (i in files.indices) {
                    val fi = FileInputStream(files[i])
                    origin = BufferedInputStream(fi, BUFFER_SIZE)
                    try {
                        val entry =
                            ZipEntry(files[i].name.substring(files[i].name.lastIndexOf("/") + 1))
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    } finally {
                        origin.close()
                    }
                }
            } finally {
                out.close()
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "application/zip"
//                shareIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name))
                val zipUri =
                    FileProvider.getUriForFile(this, this.packageName + ".provider", zipFile)
                shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
                startActivity(Intent.createChooser(shareIntent, "choose one"))
            }
        } else {
            val zipFile = File(root, "${recipe!!.name[0]}.zip")
            if (!zipFile.exists()) {
                zipFile.createNewFile()
                zipFile.setWritable(true)
            }
            val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
            try {
                val data = ByteArray(BUFFER_SIZE)
                for (i in files.indices) {
                    val fi = FileInputStream(files[i])
                    origin = BufferedInputStream(fi, BUFFER_SIZE)
                    try {
                        val entry =
                            ZipEntry(files[i].name.substring(files[i].name.lastIndexOf("/") + 1))
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    } finally {
                        origin.close()
                    }
                }
            } finally {
                zipFileShare.add(zipFile)
                out.close()
            }
        }

    }

    private fun unZipNew(zipFile: File, location: String) {
        try {
            Log.e(TAG, "unzipNew: $location")
            val f = File(location)
            if (!f.isDirectory) {
                f.mkdirs()
            }
            val zin = ZipInputStream(FileInputStream(zipFile))
            try {
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {
                    Log.e(TAG, "unZipNew: ${ze!!.name.contains(".zip")}")
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
                                Log.e(TAG, "unZipNew: exists${zipFile.exists()}")
                            }
                        }
                    }
                }
            } finally {
                validateRecipeAndInsert(location, zipFile.nameWithoutExtension)
                zin.close()
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Unzip exception", e)
        }
    }

    private fun validateRecipeAndInsert(location: String, name: String) {
//Read text from file
        //Read text from file
        val imgFile = File(location, "$name.jpg")
        val textFile = File(location, "$name.txt")
        if (textFile.exists() && textFile.extension != "txt") {
            Toast.makeText(this, "Invalid Type", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val recipe = Gson().fromJson(getTextFromFile(textFile), Recipe::class.java)
            if (checkValidName(recipe.name[0])) {
                Executors.newSingleThreadExecutor().execute {
                    recipe.imageUrl = Uri.fromFile(imgFile).toString()
                    recipe.Ingredients.forEach {
                        val imgFile = File(location, "${it.title}.jpg")
                        it.image = Uri.fromFile(imgFile).toString()
                    }
                    recipe.Ingredients = recipe.Ingredients
                    recipe.id = listRecipe.size + 1
                    OnToCookApplication.dbInstance.recipeDao().insert(recipe)
                }
            }
            Log.e(TAG, "checkAndInsert: ${Gson().toJson(recipe)}")
        } catch (e: IOException) {
            //You'll need to add proper error handling here
        }

    }

    private fun setImageInDatabase(recipe: Recipe, imgFile: File?) {
        recipe.imageUrl = Uri.fromFile(imgFile).toString()
    }

    private fun checkValidName(name: String): Boolean {
        try {
            listRecipe.forEachIndexed { index, recipeDb ->
                DebugLog.e(
                    "First ${Constants.getNameFromRecipe(name)} Second${recipeDb.name[0]}"
                )
                if (name == recipeDb.name[0]) {
                    runOnUiThread {
                        Toast.makeText(this, "Already Added", Toast.LENGTH_SHORT).show()
                    }
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Invalid Text File", Toast.LENGTH_SHORT).show() }
            return false
        }
    }

    private fun isAvailable() {
        when (status) {
            1 -> {
                OnToCookApplication.instance.bleBoundService.writeData(
                    Constants.MAGQUICKSTART.toByteArray(
                        Charsets.UTF_8
                    )
                )
                status = 0
            }

            2 -> {
                OnToCookApplication.instance.bleBoundService.writeData(
                    Constants.INDQUICKSTART.toByteArray(
                        Charsets.UTF_8
                    )
                )
                status = 0
            }
        }
    }

    private fun checkStatus(i: Int) {
        status = i
        OnToCookApplication.instance.bleBoundService.writeData(
            "STATUS=?".toByteArray(
                Charsets.UTF_8
            )
        )
    }

    private fun stopMagTimer() {
        magStart = false
        magTimer?.cancel()
        tvMagTime.text = "00 : 00"
        Log.e(TAG, "stopMagTimer: ")
        tvMag.text = getString(R.string.txt_start_mag)
        imgPowerMag.setImageResource(R.drawable.ic_start)
    }

    private fun stopIndTimer() {
        indStart = false
        indTimer?.cancel()
        tvIndTime.text = "00 : 00"
        tvPowerCount.text = "100 %"
        tvInd.text = getString(R.string.txt_start_ind)
        imgPowerInd.setImageResource(R.drawable.ic_start)
    }

    private fun startIndTimer(long: Long) {
//        tvInd.text = getString(R.string.txt_ind_started)
//        indStart = true
//        imgPowerInd.setImageResource(R.drawable.stop)
//        indTimer = object : CountDownTimer(long, 1000) {
//            override fun onTick(millis: Long) {
//                val str = String.format(
//                    "%02d : %02d",
//                    TimeUnit.MILLISECONDS.toMinutes(millis),
//                    TimeUnit.MILLISECONDS.toSeconds(millis) -
//                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
//                )
//                tvIndTime.text = str
//            }
//
//            override fun onFinish() {
//                imgPowerInd.setImageResource(R.drawable.ic_start)
//                tvInd.text = getString(R.string.txt_start_ind)
//                tvIndTime.text = "00 : 00"
//            }
//        }.start()
    }

    var magTimer: CountDownTimer? = null
    var indTimer: CountDownTimer? = null
    private fun startMagTimer(long: Long) {
//        magStart = true
//        imgPowerMag.setImageResource(R.drawable.stop)
//        tvMag.text = getString(R.string.txt_mag_started)
//
//        magTimer = object : CountDownTimer(long, 1000) {
//            override fun onTick(millis: Long) {
//                val str = String.format(
//                    "%02d : %02d",
//                    TimeUnit.MILLISECONDS.toMinutes(millis),
//                    TimeUnit.MILLISECONDS.toSeconds(millis) -
//                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
//                )
//                tvMagTime.text = str
//            }
//
//            override fun onFinish() {
//                imgPowerMag.setImageResource(R.drawable.ic_start)
//                tvMag.text = getString(R.string.txt_start_mag)
//                tvMagTime.text = "00 : 00"
//            }
//        }.start()
    }

    private fun getImages(name: String): Int {
        Log.e(TAG, "getImages: $name")
        return when (name.lowercase()) {
            "cut vegetables", "vegetable" -> {
                R.drawable.ic_vegetable
            }

            "pizza" -> {
                R.drawable.boiledaloo
            }

            "butter" -> {
                R.drawable.ic_butter
            }

            "butter paper" -> {
                R.drawable.ic_butter_paper
            }

            "idlis" -> {
                R.drawable.ic_idlis
            }

            "preheat" -> {
                R.drawable.ic_step1
            }

            "oil" -> {
                R.drawable.ic_step2
            }

            "ginger & spices" -> {
                R.drawable.ic_step3
            }

            "onions" -> {
                R.drawable.ic_step4
            }

            "potatoes", "tomatoes" -> {
                R.drawable.ic_step7
            }

            "rice" -> {
                R.drawable.ic_step8
            }

            "water" -> {
                R.drawable.ic_step9
            }

            "salt" -> {
                R.drawable.ic_step10
            }

            "close lid" -> {
                R.drawable.ic_step6
            }

            "done" -> {
                R.drawable.ic_done_step
            }

            "Ginger", "ginger garlic paste", "Ginger juliennes", "Ginger powder" -> {
                R.drawable.ic_ginger
            }

            else -> {
                R.drawable.ic_step1
            }
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

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(communicationReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (rxBleClient.isScanRuntimePermissionGranted) {
            if (isBluetoothOn() && !OnToCookApplication.instance.isDeviceConnected()) Constants.prepareScanObserver()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isBluetoothOn() && OnToCookApplication.instance.isDeviceConnected()) checkStatus(0)
        }, 100)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )

        reRecentAddedItems.adapter = recentAddedAdapter
        reRecentAddedItems.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        OnToCookApplication.dbInstance.recipeDao().getAllRecipe1().subscribe({
            Log.e(TAG, "init: ${it.size}")
            runOnUiThread {
                if (it.isNotEmpty()) clAddedRecipe.visibility = View.VISIBLE
                recentAddedAdapter.setRecentItemList(it)
            }
            listRecipe.clear()
            listRecipe.addAll(it)
        }, {

        })

    }

    private fun toggleDeviceScanDialog(isShow: Boolean = false) {
        if (deviceScanDialog != null) {
            deviceScanDialog!!.dismiss()
            deviceScanDialog = null
            return
        }
        if (isShow) {
            deviceScanDialog = DeviceScanDialog()
            deviceScanDialog!!.context = this
            deviceScanDialog!!.show(supportFragmentManager, "")
        }
    }

    fun updateIndTime(str: String) {
        Log.e(TAG, "updateIndTime: $str")
        when (str) {
            FINISH -> {
                tvPowerCount.text = "100 %"
                indStart = false
                indStatus = Constants.Status.STOP
                indPause.visibility = View.GONE
                imgPowerInd.setImageResource(com.invent.ontocook.R.drawable.ic_start)
                tvInd.text = getString(com.invent.ontocook.R.string.txt_start_ind)
                tvIndTime.text = "00 : 00"
            }

            PAUSE -> {
                indStatus = Constants.Status.PAUSE
                indStart = true
                indPause.setImageResource(R.drawable.ic_resume)
                imgPowerInd.setImageResource(R.drawable.stop)
                tvInd.text = getString(R.string.txt_ind_paused)
            }

            else -> {
                indStatus = Constants.Status.START
                tvIndTime.text = str
            }
        }
    }

    fun startInd() {
        tvInd.text = getString(R.string.txt_ind_started)
        indStart = true
        indStatus = Constants.Status.START
        indPause.setImageResource(R.drawable.ic_pause)
        imgPowerInd.setImageResource(R.drawable.stop)
        indPause.visibility = View.VISIBLE
    }

    fun startMag() {
        magStatus = Constants.Status.START
        tvMag.text = getString(R.string.txt_mag_started)
        magStart = true
        magPause.setImageResource(R.drawable.ic_pause)
        imgPowerMag.setImageResource(R.drawable.stop)
        magPause.visibility = View.VISIBLE
    }

    fun updateMagTime(str: String) {
        Log.e(TAG, "updateMagTime: $str")
        when (str) {
            FINISH -> {
                magStatus = Constants.Status.STOP
                magStart = false
                imgPowerMag.setImageResource(R.drawable.ic_start)
                magPause.visibility = View.GONE
                tvMag.text = getString(R.string.txt_start_mag)
                tvMagTime.text = "00 : 00"
            }

            PAUSE -> {
                magStatus = Constants.Status.PAUSE
                magStart = true
                imgPowerMag.setImageResource(R.drawable.stop)
                magPause.setImageResource(R.drawable.ic_resume)
                tvMag.text = getString(R.string.txt_mag_paused)
            }

            else -> {
                magStatus = Constants.Status.START
                tvMagTime.text = str
            }
        }
    }

    fun updateIndPower(indPower: String) {
        Log.e(TAG, "updateIndPower: $indPower")
        tvPowerCount.text = "$indPower %"
    }

}