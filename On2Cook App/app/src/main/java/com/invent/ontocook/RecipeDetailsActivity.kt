package com.invent.ontocook

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.adapter.IngredientsListAdapter
import com.invent.ontocook.adapter.NutritionalListAdapter
import com.invent.ontocook.adapter.RecommendItemListAdapter
import com.invent.ontocook.create_recipe.CreateNewRecipe
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.dialog.PreviewDialog
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.models.RecipeNew
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.Constants.INGREDIENT_MODE
import com.invent.ontocook.utils.isBluetoothOn
import com.invent.ontocook.utils.requestBluetoothPermission
import com.invent.ontocook.utils.shareZipFile
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_recipe_details2.*
import java.io.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var ingredientsListAdapter: IngredientsListAdapter
    private lateinit var nutritionalListAdapter: NutritionalListAdapter
    lateinit var recommendItemListAdapter: RecommendItemListAdapter

    var recentItem: RecentItem? = null

    val listRecipe: ArrayList<Recipe> = ArrayList()
    private var deviceScanDialog: DeviceScanDialog? = null
    private lateinit var broadcastReceiver: BroadcastReceiver
    private val rxBleClient = OnToCookApplication.rxBleClient
    private val TAG = this::class.java.simpleName
    var recommendedItems = mutableListOf<RecentItem>()

    val BUFFER_SIZE = 4096

    private lateinit var communicationReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_details2)

        init()
    }

    fun redirectToCooking() {
        if (Constants.IS_PRODUCTION_MODE) {
            Handler(Looper.getMainLooper()).postDelayed({
                OnToCookApplication.instance.bleBoundService.writeData(
                    ("recipe=" + recentItem?.name.toString()).toByteArray(
                        Charsets.UTF_8
                    )
                )
                LoadingUtils.showLoading(this, true, "Please Wait")
            }, 100)
        }
    }

    private fun prepareView() {
        ivRecipeImage.setImageResource(recentItem!!.image)
        val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
        if (recipe.imageUrl.isNotEmpty()) {
            Glide.with(this).load(Uri.parse(recipe.imageUrl)).into(ivRecipeImage)
        }
        tvName.text = recentItem!!.name
        tvDesc.text = recentItem!!.desc
        tvDifficulty.text = recentItem!!.difficulty

        when (tvDifficulty.text.toString().lowercase()) {
            "medium" -> {
                tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            this, R.color.orange
                        ), BlendModeCompat.SRC_IN
                    )
            }

            "hard" -> {
                tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            this, R.color.colorFlamColor
                        ), BlendModeCompat.SRC_IN
                    )
            }

            "easy" -> {
                tvDifficulty.background.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        ContextCompat.getColor(
                            this, R.color.colorMWColor
                        ), BlendModeCompat.SRC_IN
                    )
            }
        }
    }

    private fun init() {
        recentItem = intent.extras?.getSerializable("recipe") as RecentItem
        if (intent.extras?.getBoolean("show", false) == true) {
            btnPreview.visibility = View.VISIBLE
            btnPreview.visibility = View.VISIBLE
            btnSend.visibility = View.VISIBLE
            ivDelete.visibility = View.VISIBLE
            ivEdit.visibility = View.VISIBLE
            ivLike.visibility = View.GONE
        }
        if (intent.extras != null && intent.extras!!.containsKey("list")) {
            val list = intent.extras!!.getString("list")
            val type = object : TypeToken<ArrayList<Recipe>>() {}.type
            val listRecipeJson: ArrayList<Recipe> = Gson().fromJson(list, type)
            listRecipe.addAll(listRecipeJson)
        }
        listRecipe.map {
            Log.e(TAG, "onReceive: ${it.name}")
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        val message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Toast.makeText(this@RecipeDetailsActivity, message, Toast.LENGTH_LONG)
                            .show()
                        toggleDeviceScanDialog()
                    }

                    Constants.EVENT_BLE_CONNECTION_ABORT -> {
                        toggleDeviceScanDialog()
                        Constants.prepareScanObserver()
                    }

                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        toggleDeviceScanDialog()
                        redirectToCooking()
                    }

                    Constants.EVENT_BLE_CONNECTION_INIT -> {
                        toggleDeviceScanDialog(true)
                    }

                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
                        val message = intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        if (deviceScanDialog != null) {
                            deviceScanDialog!!.updateResult(
                                "Connecting to $message"
                            )
                        }
                    }
                }
            }
        }

        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        if (message.lowercase().contains("recipe=")) {
                            btnCookNow.isEnabled = true
                            if (!message.contains(",")) {
                                LoadingUtils.hideDialog()
                                if (message.lowercase()
                                        .replace("recipe=", "") == "none".lowercase()
                                ) {
                                    Toast.makeText(
                                        this@RecipeDetailsActivity,
                                        "Please Send Recipe To Device",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return
                                }
                                var foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.lowercase()
                                        .replace("recipe=", "")
                                }
                                if (foundRecipe.isNotEmpty()) {
                                    var intent =
                                        Intent(applicationContext, DashboardActivity::class.java)
                                    intent.putExtra("recipe", foundRecipe[0])
                                    intent.putExtra("isResume", false)
                                    startActivity(intent)
                                } else {
                                    var foundRecipe = listRecipe.filter {
                                        it.name[0].lowercase() == message.lowercase()
                                            .replace("recipe=", "")
                                    }
                                    if (foundRecipe.isNotEmpty()) {
                                        recentItem!!.recipe = Gson().toJson(foundRecipe[0])
                                        var intent = Intent(
                                            applicationContext, DashboardActivity::class.java
                                        )
                                        intent.putExtra("recipe", recentItem)
                                        intent.putExtra("isResume", false)
                                        startActivity(intent)
                                    }
                                }
                            } else {
                                val foundRecipe = Constants.RECIPES.filter {
                                    it.name.lowercase() == message.split(",")[0].lowercase()
                                        .replace("recipe=", "")
                                }
                                val cmdSize: Int = message.split(",").size

                                val stepNo = if (cmdSize > 2) message.split(",")[2].lowercase()
                                    .replace("stepno=", "") else "0"
                                LoadingUtils.hideDialog()
                                if (cmdSize > 1) when (message.split(",")[1].uppercase()
                                    .replace("MODE=", "").uppercase()) {
                                    INGREDIENT_MODE -> {
                                        if (foundRecipe.isNotEmpty()) {
                                            val intent = Intent(
                                                applicationContext, DashboardActivity::class.java
                                            )
                                            intent.putExtra("recipe", foundRecipe[0])
                                            intent.putExtra("isResume", true)
                                            intent.putExtra(
                                                "prepreparestep", stepNo.toInt() - 1
                                            )
                                            intent.putExtra("isPrepareRunning", true)
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
                                                        applicationContext,
                                                        DashboardActivity::class.java
                                                    )
                                                    intent.putExtra("recipe", recentItem)
                                                    intent.putExtra("isResume", true)
                                                    intent.putExtra(
                                                        "prepreparestep", stepNo.toInt() - 1
                                                    )
                                                    intent.putExtra(
                                                        "isPrepareRunning", true
                                                    )
                                                    startActivity(intent)
                                                }
                                            }
                                        }
                                    }

                                    Constants.COOKING_MODE -> {
                                        if (foundRecipe.isNotEmpty()) {
                                            val indTime = message.split(",")[3].lowercase()
                                                .replace("ind_run=", "")
                                            val magTime = message.split(",")[4].lowercase()
                                                .replace("mag_run=", "")
                                            val status = message.split(",")[5].lowercase()
                                                .replace("status=", "")
                                            val intent = Intent(
                                                applicationContext, DashboardActivity::class.java
                                            )
                                            intent.putExtra("recipe", foundRecipe[0])
                                            intent.putExtra("isResume", true)
                                            intent.putExtra("currentStep", stepNo.toInt())
                                            intent.putExtra("isPrepareRunning", false)
                                            intent.putExtra(
                                                "isPlaying",
                                                indTime.toInt()
                                                    .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                            )

                                            intent.putExtra(
                                                "changeTime",
                                                indTime.toInt().coerceAtLeast(magTime.toInt())
                                            )
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

                                                    val indTime = message.split(",")[3].lowercase()
                                                        .replace("ind_run=", "")
                                                    val magTime = message.split(",")[4].lowercase()
                                                        .replace("mag_run=", "")
                                                    val status = message.split(",")[5].lowercase()
                                                        .replace("status=", "")
                                                    val intent = Intent(
                                                        applicationContext,
                                                        DashboardActivity::class.java
                                                    )
                                                    intent.putExtra("recipe", recentItem)
                                                    intent.putExtra("isResume", true)
                                                    intent.putExtra("currentStep", stepNo.toInt())
                                                    intent.putExtra("isPrepareRunning", false)
                                                    Log.e(
                                                        TAG, "onReceive:CheckStatus ${
                                                            indTime.toInt()
                                                                .coerceAtLeast(magTime.toInt()) != 0
                                                        }"
                                                    )
                                                    Log.e(
                                                        TAG,
                                                        "onReceive:CheckStatus ${status != "pause"}"
                                                    )
                                                    intent.putExtra(
                                                        "isPlaying",
                                                        indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt()) != 0 && status != "pause"
                                                    )
                                                    intent.putExtra(
                                                        "changeTime",
                                                        indTime.toInt()
                                                            .coerceAtLeast(magTime.toInt())
                                                    )
                                                    intent.putExtra("isResume", true)
                                                    intent.putExtra(
                                                        "prepreparestep", stepNo.toInt() - 1
                                                    )
                                                    startActivity(intent)
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        OnToCookApplication.instance.bleBoundService.cancelNotifyTimer()
                                        redirectToCooking()
                                    }
                                }
                            }
                        }
                        if (message.lowercase().contains("delete=")) {
                            Executors.newSingleThreadExecutor().execute {
                                val recipe =
                                    Gson().fromJson(recentItem?.recipe, Recipe::class.java)
//                                OnToCookApplication.dbInstance.recipeDao().delete(recipe)
                                runOnUiThread {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.txt_recipe_delete_successfully),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                            }
                        }

                        if (message.uppercase()
                                .contains("INDQUICKSTART=") && Constants.checkNavigation(message)
                        ) {
                            val intent =
                                Intent(this@RecipeDetailsActivity, CookingActivity::class.java)
                            startActivity(intent)
                        }
                        if (message.lowercase().contains("workstatus=")) {
                            if (message.lowercase()
                                    .replace("workstatus=", "") == "idle"
                            ) redirectToCooking()
                            else {
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.txt_started),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        if (message.uppercase() == "ACK_CANCEL") {
                            LoadingUtils.hideDialog()
                            Toast.makeText(
                                this@RecipeDetailsActivity,
                                "Edit Recipes Name Send Again",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (message.uppercase().contains("INDQUICKSTART=")) {
                            Toast.makeText(
                                this@RecipeDetailsActivity,
                                getString(R.string.txt_quickstart),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (message.lowercase().contains("info=")) {
                            var messageChunk =
                                message.lowercase().toString().replace("info=", "").split(",")

                            println("receive message..... $message")

                            val recipeNo = messageChunk[0]
                            val ingredient =
                                if (messageChunk[1] <= "0") -1 else messageChunk[1].toInt() - 1
                            var stepno = if (messageChunk[2] <= "0") -1 else messageChunk[2].toInt()
                            var second = if (messageChunk[3] <= "0") -1 else messageChunk[3].toInt()

                            var foundRecipe =
                                Constants.RECIPES.filter { it.id.toString() == recipeNo }
                            if (foundRecipe.isNotEmpty()) {
                                var intent = Intent(
                                    this@RecipeDetailsActivity, DashboardActivity::class.java
                                )
                                intent.putExtra("recipe", foundRecipe[0])
                                intent.putExtra("prepreparestep", ingredient)
                                intent.putExtra("currentstep", stepno)
                                intent.putExtra("currentstep", stepno)
                                intent.putExtra("isResume", false)
                                startActivity(intent)
                            }
                        }
                    }

                    Constants.FILE_UPLOAD_SUCCESS -> {
                        LoadingUtils.hideDialog()
                        Toast.makeText(
                            applicationContext, "File Uploaded Successfully", Toast.LENGTH_LONG
                        ).show()

                    }
                }
            }
        }

        ivLeft.setOnClickListener {
            onBackPressed()
        }
        ivShar.setOnClickListener {
            val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
            val root = File(externalCacheDir, "Recipe")
            if (!root.exists()) {
                root.mkdirs()
            }
            val listOfFiles = arrayListOf<File>()
            val recipeTextFile = File(root, recentItem!!.name + ".txt")
            val writer = FileOutputStream(recipeTextFile)
            writer.write(recentItem!!.recipe.toByteArray())
            writer.flush()
            writer.close()
            listOfFiles.add(recipeTextFile)
            if (recipe.imageUrl.isNotEmpty()) {
                val imageFile = File(root, recentItem!!.name + ".jpg")
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
            shareZipFile(listOfFiles, recentItem!!.name)
        }
        btnSend.setOnClickListener {
            val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
            val recipeToSend = Constants.convertRecipeDbToNewJson(recipe)
            Log.e(TAG, "init: $recipeToSend")
            if (OnToCookApplication.instance.isDeviceConnected()) {
                LoadingUtils.showDialog(this@RecipeDetailsActivity, true, "Uploading File..")
                val size = recipeToSend.toByteArray(
                    Charsets.UTF_8
                ).size
                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "{\"RECIPE\":\"${recentItem!!.name}\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(
                        Charsets.UTF_8
                    )
                )
                Log.e(TAG, "onCreate: Step 1 Sent Success")
                OnToCookApplication.instance.bleBoundService.sendFileDataToDevice(
                    recipeToSend
                )
            }
        }
        btnPreview.setOnClickListener {
            openDialog()
        }
        ivDelete.setOnClickListener {
            if (OnToCookApplication.instance.isDeviceConnected()) {
                Constants.showAlertDialog(this,
                    "Delete Recipe",
                    "Are you sure you want to delete ${recentItem!!.name} ?",
                    { dialog, which ->
                        OnToCookApplication.instance.bleBoundService.writeData(
                            ("DELETE=${recentItem!!.name}").toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    },
                    { dialog, which ->
                        Log.e(TAG, "init: No")
                    })
            } else {
//                Executors.newSingleThreadExecutor().execute {
//                    val recipe =
//                        Gson().fromJson(recentItem?.recipe, RecipeDb::class.java)
//                    OnToCookApplication.dbInstance.recipeDao().delete(recipe)
//                    runOnUiThread {
//                        finish()
//                    }
//                }
                Toast.makeText(
                    this@RecipeDetailsActivity, getString(R.string.strConnect), Toast.LENGTH_SHORT
                ).show()
            }
        }

        ivEdit.setOnClickListener {
            val intent = Intent(this, CreateNewRecipe::class.java)
            intent.putExtra("recipe", recentItem!!.recipe)
            startActivity(intent)
        }

        btnCookNow.setOnClickListener {
            if (!Constants.IS_PRODUCTION_MODE) {
                redirectToCooking()
                return@setOnClickListener
            }

            if (OnToCookApplication.instance.isDeviceConnected()) {
                btnCookNow.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({
                    btnCookNow.isEnabled = true
                }, 5000)
                OnToCookApplication.instance.bleBoundService.writeData(
                    ("STATUS=?").toByteArray(
                        Charsets.UTF_8
                    )
                )

            } else {
                DialogUtils().commonDialog(context = this,
                    title = getString(R.string.txt_no_device_connected),
                    message = getString(R.string.txt_not_connected),
                    positiveButton = getString(R.string.txt_connect),
                    negativeButton = getString(R.string.button_cancel),
                    isAppLogoDisplay = true,
                    isCancelable = true,
                    callbackSuccess = {
                        checkPermission()
                    },
                    callbackNegative = {})
            }
        }

        //reNutritional
        //prepareIngredients()

        reIngredients.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        ingredientsListAdapter = IngredientsListAdapter()
        reIngredients.adapter = ingredientsListAdapter
        ingredientsListAdapter.setQuickAccessList(recentItem!!.ingredients)

        //prepareNutritional()

        reNutritional.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        nutritionalListAdapter = NutritionalListAdapter()
        reNutritional.adapter = nutritionalListAdapter
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

        reRelated.layoutManager = GridLayoutManager(this, 2)
//        recommendItemListAdapter = RecommendItemListAdapter()
//        reRelated.adapter = recommendItemListAdapter
//        recommendItemListAdapter.setModesList(recommendedItems)

        if (recentItem != null) {
            prepareView()
        }
    }

//    private fun prepareIngredients() {
//        ingredientsList.clear()
//        ingredientsList.add(IngredientsSteps(R.drawable.ic_oil, "Oil", "50g|2Tbsp", "Refined"))
//        ingredientsList.add(
//            IngredientsSteps(
//                R.drawable.ic_onion,
//                "Garlic",
//                "50g|1Tbsp",
//                "Finely Chopped"
//            )
//        )
//        ingredientsList.add(
//            IngredientsSteps(
//                R.drawable.ic_carrot,
//                "Carrots",
//                "50g|1/2Cup",
//                "Finely Chopped"
//            )
//        )
//        ingredientsList.add(
//            IngredientsSteps(
//                R.drawable.ic_bean,
//                "French beans",
//                "50g|1/4 Cup",
//                "Finely Chopped"
//            )
//        )
//    }

    //    private fun prepareNutritional() {
//        nutritionalList.clear()
//        nutritionalList.add(Nutritional("Carbs", "51%", "1500 gms"))
//        nutritionalList.add(Nutritional("Protein", "16%", "504 gms"))
//        nutritionalList.add(Nutritional("Fat", "33%", "594 gms"))
//        nutritionalList.add(Nutritional("Calories", "12600", ""))
//    }
    private fun openDialog() {
        val recipe = Gson().fromJson(recentItem?.recipe, Recipe::class.java)
        val recipeToSend = Constants.convertRecipeDbToNewJson(recipe)
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(Gson().fromJson(recipeToSend, RecipeNew::class.java))
        val previewDialog = PreviewDialog(json)
        previewDialog.show(supportFragmentManager, "")
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(communicationReceiver)
    }

    @Throws(IOException::class)
    fun zipAndShare(files: ArrayList<File>, shareIntent: Intent) {
        var origin: BufferedInputStream? = null
        val root = File(externalCacheDir, "Recipe")
        if (!root.exists()) {
            root.mkdirs()
        }
        val zipFile = File(root, "${recentItem!!.name}.zip")
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
            val zipUri = FileProvider.getUriForFile(this, this.packageName + ".provider", zipFile)
            shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
            startActivity(Intent.createChooser(shareIntent, "choose one"))
            out.close()
        }
    }

    override fun onResume() {
        super.onResume()
        if (OnToCookApplication.rxBleClient.isScanRuntimePermissionGranted) {
            Log.e(TAG, "onResume:isBluetoothOn ${isBluetoothOn()}")
            Log.e(
                TAG,
                "onResume:isDeviceConnected ${OnToCookApplication.instance.isDeviceConnected()}"
            )
            if (isBluetoothOn() && !OnToCookApplication.instance.isDeviceConnected()) Constants.prepareScanObserver()
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, IntentFilter(Constants.EVENT_BLE_CONNECTION)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            communicationReceiver, IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
        )
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

    /*override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (isBluetoothPermissionGranted(requestCode, grantResults)) {
            prepareScanObserver()
        }
    }*/

    private fun prepareScanObserver() {
        if (rxBleClient.isScanRuntimePermissionGranted) {
            OnToCookApplication.instance.isBluetoothConnected()
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    OnToCookApplication.instance.startBoundService(true)
                }
        } else {
            requestBluetoothPermission(rxBleClient)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            201 -> {
                if (grantResults.isNotEmpty() && grantResults[0] === PackageManager.PERMISSION_GRANTED) {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    getResultForBLE.launch(enableIntent)
                }
                return
            }

            111 -> {
                checkPermission()
            }

            else -> {
                checkPermission()
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e("in", "sdk version 31 and above")
            Dexter.withContext(this@RecipeDetailsActivity).withPermissions(
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
                                this@RecipeDetailsActivity
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
                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@RecipeDetailsActivity
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
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest();
                }


            }).withErrorListener {
                Log.e("in", "error listener")
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
                                this@RecipeDetailsActivity
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
//                                    startScan()
                                prepareScanObserver()
                            }
                        }
                    } else {
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(
                                this@RecipeDetailsActivity
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
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).withErrorListener {
                Log.e("in", "error listener")
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

    private fun hasGPSDevice(context: Context): Boolean {
        val mgr =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager ?: return false
        val providers = mgr.allProviders ?: return false
        return providers.contains(LocationManager.GPS_PROVIDER)
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


    protected fun showBLEDialog() {
        if (!isBLEEnabled()) {
            Log.e("in fun", "ble enabled")
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            getResultForBLE.launch(enableIntent)
        }
    }

    private val getResultForBLE = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        getResult.launch(intent)
    }

    private val getResult = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            checkPermission()
        }
    }
}