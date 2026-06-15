package com.invent.ontocook.utils

import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.Classes.Adding
import com.invent.ontocook.Classes.Boiling
import com.invent.ontocook.Classes.Soakings
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.models.*
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import io.reactivex.android.schedulers.AndroidSchedulers
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit


object Constants {
    /*

    remain

    online recipe create
        need to decide ui
    recipe screen
        need to decide ui
    recipe ingredients to shopping cart
        issues - not able to find api
    auto schedule  - script
        -   screen to add recipe
        -   schedule date time
        -   recipe url, ingredients, schedule date time
        -   cron job at server side
        -   url, ingredients, convert in terms of no of person requirement
        -   check for local ingredients table
        -   extract ingredients required actually
        -   mapping with sites - amazone
        -   amazon - need to go to their page / extract html - order with pid
        -   order delivery date
        -   do order



     custom search
        single recipe ingredients
        extract data
        //add to cart
     */

    enum class Status {
        DEFAULT, START, PAUSE, STOP
    }

    enum class AUDIO_TYPE {
        FEEDBACK, ERROR, TIME, QUANTITY, ACTION, INGREDIENTS
    }

    enum class FILE_TYPE {
        STATIC, UPLOADED, DEFAULT
    }

    enum class FILE_LIST_TYPE {
        DEVICE_RECIPE, LOCAL_AUDIO, DEVICE_AUDIO
    }

    enum class CheckStatus {
        DEFAULT, MAGQUICKSTART, INDQUICKSTART, FTPCONNECTION, SPRINKLE, SPRAY
    }

    enum class LogData {
        All, Filtered, SINGLE_DAY, SINGLE_DAY_FILTERED
    }

    //-------Here we have to pass "categoryForLocalDB" foe store in local DB, at time of create recipe-------//
    enum class CreateRecipeScreenFlowType(val type: String, val categoryForLocalDB: String) {
        CREATE_RECIPE("Create Regular Recipe", "0"),
        CREATE_FRY_RECIPE("Create Fry Mode Recipe", "1")
    }

    //-------Differentiate flow of "RecipeFragment"-------//
    enum class RecipeFragmentScreenFlowType(val type: String) {
        MANUAL_MODE("Manual Mode"),
        QUICK_FRY_MODE("Quick Fry Mode")
    }

    const val DEFAULT_EMPTY_STRING: String = ""

    const val DEFAULT_ZERO: Int = 0
    const val DEFAULT_ONE: Int = 1
    const val DEFAULT_FIVE: Int = 5
    const val DEFAULT_TEN: Int = 10
    const val DEFAULT_TWENTY: Int = 20


    const val PAGE_SIZE: Int = 30
    var DummyMacAddress = "ce:46:50:97:a6:90"

    const val DATE_FORMAT = "dd_MM_yyyy"

    //Commands
    const val STOP = "stop=100"
    var IS_TABLET = false
    const val LOGDATA = "listlogfile=?"
    const val LIVELOGON = "livelog=ON"
    const val LIVELOGOFF = "livelog=OFF"
    const val IDLE_DEVICE = "workstatus=idle"
    const val MANUAL_MODE = "manual mode"
    const val ACK_COMMAND = "ack_command"
    const val ACK = "ack"
    const val LISTEND = "listend"
    const val LOGEND = ">"
    const val ACK_CANCEL = "ack_cancel"
    const val RECIPE_NONE = "RECIPE=NONE"
    const val START = "START"
    const val COMPLETE = "COMPLETE"
    const val MAGQUICKSTART = "MAGQUICKSTART=START"
    const val MAGQUICKSTOP = "MAGQUICKSTART=STOP"
    const val INDQUICKSTART = "INDQUICKSTART=START"
    const val STIRRER = "STIRRER="
    const val PUMP = "PUMP="
    const val INDQUICKSTOP = "INDQUICKSTART=STOP"
    const val INDPAUSE = "INDQUICKSTART=PAUSE"
    const val INDRESUME = "INDQUICKSTART=RESUME"
    const val MAGPAUSE = "MAGQUICKSTART=PAUSE"
    const val MAGRESUME = "MAGQUICKSTART=RESUME"
    const val MAG_PAUSE = "MAG=PAUSE"
    const val IND_PAUSE = "IND=PAUSE"
    const val STIRRER_ON_LOW = "STIRRER=ON,LOW"
    const val STIRRER_ON = "STIRRER=ON"
    const val STIRRER_OFF = "STIRRER=OFF"
    const val PUMP_ON = "PUMP=ON," //Pass Second After Command
    const val PUMP_OFF = "PUMP=OFF" //Pass Second After Command
    const val PURGE_ON = "PURGE=ON,"
    const val PURGE_OFF = "PURGE=OFF"
    const val STIRRER_ON_MED = "STIRRER=ON,MED"
    const val STIRRER_ON_HIGH = "STIRRER=ON,HIGH"
    const val STIRRER_ON_VERY_HIGH = "STIRRER=ON,VERY_HIGH"
    const val STIRRER_OFF_LOW = "STIRRER=OFF,LOW"
    const val STIRRER_OFF_MED = "STIRRER=OFF,MED"
    const val STIRRER_OFF_HIGH = "STIRRER=OFF,HIGH"
    const val STIRRER_OFF_VERY_HIGH = "STIRRER=OFF,VERY_HIGH"

    //-------Fry quick fry commands - PrinceEWW-------//
    const val FRYQUICKSTART = "FRYQUICKSTART=START" //For start quick fry mode
    const val FRYQUICKSTOP = "FRYQUICKSTART=STOP" //For stop quick fry mode
    const val FRYQUICKPAUSE = "FRYQUICKSTART=PAUSE" //For pause quick fry mode
    const val FRYQUICKRESUME = "FRYQUICKSTART=RESUME" //For resume quick fry mode
    const val THRESHOLD = "THRESHOLD=" //For threshold count of quick fry mode
    const val POWERSAVING = "POWERSAVING=" //For power saving time of quick fry mode

    //-------Fro quick fry command - PrinceEWW-------//
    const val MAGFRYSTART = "MAGFRYSTART=START"
    const val MAGFRYSTOP = "MAGFRYSTART=STOP"
    const val MAGFRYPAUSE = "MAGFRYSTART=PAUSE"
    const val MAGFRYRESUME = "MAGFRYSTART=RESUME"

    //-------If recipe already exist in On2Cook device, While share recipe-------//
    const val RECIPE_EXIST = "RECIPE_EXIST"


    var IS_PRODUCTION_MODE = true

    const val REQUEST_TAKE_PHOTO = 4
    const val DEFAULT_TIME = 4L
    const val PIC_CROP = 3
    const val LogSizeField = 19
    const val RESULT_LOAD_IMAGE = 2
    const val REQUEST_CAMERA_PERMISSION = 1
    const val REQUEST_RECORD_PERMISSION = 7
    const val REQUEST_NEARBY_WIFI_DEVICES = 121
    const val REQUEST_ZIP = 5

    const val JSON_FILE = 1
    const val COMPLETE_FILE = 0
    const val BIN_FILE = 2

    var CHANGE_DUMMY_MAC = "CHANGE_DUMMY_MAC"
    var EVENT_BLE_NOTIFICATION = "EVENT_BLE_NOTIFICATION"
    var EVENT_BLE_CONNECTION_INIT = "EVENT_BLE_CONNECTION_INIT"
    var EVENT_BLE_CONNECTION_FOUND_DEVICE = "EVENT_BLE_CONNECTION_FOUND_DEVICE"
    var EVENT_BLE_CONNECTION_SUCCESS = "EVENT_BLE_CONNECTION_SUCCESS"
    var EVENT_BLE_CONNECTION_DISCONNECTING = "EVENT_BLE_CONNECTION_DISCONNECTING"
    var EVENT_FTP_DISCONNECTED = "ftp_disconnected"
    var EVENT_BLE_CONNECTION_ABORT = "EVENT_BLE_CONNECTION_ABORT"
    var EVENT_BLE_CONNECTION_ERROR = "EVENT_BLE_CONNECTION_ERROR"
    var EVENT_BLE_WRITE_SUCCESS = "EVENT_BLE_WRITE_SUCCESS"
    var FILE_UPLOAD_SUCCESS = "FILE_UPLOAD_SUCCESS"
    var FILE_RECEIVE_SUCCESS = "FILE_RECEIVE_SUCCESS"
    var FILE_ALREADY_EXIST = "FILE_ALREADY_EXIST"
    var EVENT_BLE_WRITE_FAIL = "EVENT_BLE_WRITE_FAIL"
    var EVENT_ERROR_MESSAGE = "EVENT_ERROR_MESSAGE"
    var EVENT_MESSAGE = "EVENT_MESSAGE"
    var EVENT_ERROR_CODE = "EVENT_ERROR_CODE"
    const val INGREDIENT_MODE = "INGREDIENT"
    const val REC_SEL_MODE = "RECEIPE_SEL"
    const val COOKING_MODE = "COOKING"
    const val IDLE = "IDLE"
    const val FINISH = "FINISH"
    const val PAUSE = "PAUSE"
    const val QUICKSTART_MODE = "COOKING"
    const val STATUS = "STATUS=?"
    const val CHECKLOGSTATUS = "LOGSTATUS=?"
    const val LOGIDLE = "LOGSTATUS=IDLE"
    const val LOGBUSY = "LOGSTATUS=BUSY"

    var EVENT_BLE_COMMUNICATION = "EVENT_BLE_COMMUNICATION"
    var EVENT_BLE_CONNECTION = "EVENT_BLE_CONNECTION"
    var EVENT_STOP_SCAN = "EVENT_STOP_SCAN"
    var EVENT_BLE_ACTION = "EVENT_BLE_ACTION"
    var EVENT_LOG = "EVENT_LOG"
    var EVENT_LOG_ACTION = "EVENT_LOG_ACTION"
    var CHANGE_DEVICE_NAME = "CHANGE_DEVICE_NAME"
    var SET_DEVICE_RTCTIME = "SET_DEVICE_RTCTIME"
    const val DATETIME = "DATETIME="

    //-------preferences key - PrinceEWW-------//
    const val PREFFRYMODEPOWERSAVINGTIME = "FryModePowerSavingTime"

    /*PARAM*/
    const val MAC_ADDRESS = "MAC_ADDRESS"
    const val recipeFragmentScreenFlowType = "recipeFragmentScreenFlowType"
    const val DEVICE_NAME = "DEVICE_NAME"
    const val DEVICE_NAME_CHECK = "Devicename="
    const val DEVICE = "DEVICE"
    const val IS_RESUME = "IS_RESUME"
    const val PREPARE_STEP = "PREPARE_STEP"
    const val CURRENT_STEP = "CURRENT_STEP"
    const val IS_PREPARE_RUNNING = "IS_PREPARE_RUNNING"
    const val IS_PLAYING = "IS_PLAYING"
    const val REMAINSEC = "REMAINSEC"
    const val RECIPE_LIST = "RECIPE_LIST"
    const val RANDOM_RECIPE_LIST = "RANDOM_RECIPE_LIST"
    const val CHANGE_TIME = "CHANGE_TIME"
    const val RECIPE = "RECIPE"
    const val RECIPE_ID = "RECIPE_ID"
    const val DEVICE_RECIPE = "recipe"
    const val AUDIO = "AUDIO"
    const val IS_SHOW = "IS_SHOW"
    const val IS_EDIT = "IS_EDIT"
    const val INDEX = "INDEX"

    //-------Create recipe screen flow type-------//
    const val BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE =
        "BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE"

    const val ALUMINIUM = "AL"
    const val STAINLESS_STEEL = "SS"

    const val ERROR_AUDIO_PATH = "Audio/Error"
    const val QTY_AUDIO_PATH = "Audio/Qty"
    const val ACTION_AUDIO_PATH = "Audio/Action"
    const val FEEDBACK_AUDIO_PATH = "Audio/Feedback"
    const val TIME_AUDIO_PATH = "Audio/Time"
    const val INGREDIENTS_AUDIO_PATH = "Audio/Ingredients"
    const val STATIC_ERROR_AUDIO_PATH = "audio/Type"
    const val STATIC_QTY_AUDIO_PATH = "audio/Qty"
    const val STATIC_ACTION_AUDIO_PATH = "audio/Type"
    const val STATIC_FEEDBACK_AUDIO_PATH = "Audio/Feedback"
    const val STATIC_TIME_AUDIO_PATH = "Audio/Time"
    const val STATIC_INGREDIENTS_AUDIO_PATH = "audio/Ingredients"

    //For Resume Recipe
    var remainSecond = 0
    var isPlayStop = false
    var isChangeInRecipe = false
    var currentStep = 1
    var nextDescription = ""
    var isPrepareRunning = true
    var currentPrepareStep = 0
    var isResetGlobal = false
    var firmwareVersion = ""

    var recipeStepList = ArrayList<RecipeSteps>()
    var EVENT_SERVICE_STARTED = "EVENT_SERVICE_STARTED"
    var FIREBASE_COLLECTION_RECIPE = "recipe"
    var FIREBASE_COLLECTION_IMAGES = "images"

    var FIREBASE_IMAGE_STORAGE_BASE_URL = "gs://on2cook.appspot.com"

    var SERVICE_UUID = "ab0828b1-198e-4351-b779-901fa0e0371e"
    //var SERVICE_UUID = "00001805-0000-1000-8000-00805f9b34fb"

    var INGREDIENTS_HOST_URL = "https://spoonacular.com/cdn/ingredients_250x250/"
    var RECIPE_LISTING_URL =
        "https://api.spoonacular.com/recipes/autocomplete?apiKey=10b1e5eff9bc43e4b5fae65c51fe672e&number=5"
    var RECIPE_DETAILS_URL =
        "https://api.spoonacular.com/recipes/{recipe_id}/information?apiKey=10b1e5eff9bc43e4b5fae65c51fe672e"

    var EXTRACT_RECIPE_URL =
        "https://www.googleapis.com/customsearch/v1?key=AIzaSyC-uzcBuaFqqqcbSJ_claQncBng-95duC0&cx=fd41fa40af92a4606&q={query}"
    var READ_RECIPE_PAGE_URL =
        "https://api.spoonacular.com/recipes/extract?url={extract_url}&apiKey=10b1e5eff9bc43e4b5fae65c51fe672e&forceExtraction=true&includeNutrition=true"
    var UUID_FOR_WRITE = "4ac8a682-9736-4e5d-932b-e9b31405049c"
    var UUID_FOR_FileTransfer = "4ac8c682-9736-4e5d-932b-e9b31405049c"


    //Shared Pref KEY

    const val MAC_ADDRESS_LIST = "MAC_ADDRESS_LIST"
    const val RECENTLY_PLAYED_LIST = "RECENTLY_PLAYED_LIST"

    //PrinceEWW
    const val RESULT_KEY = "RESULT_KEY" //setFragmentResultListener key
    const val BUNDLE_DELETE_RECIPE = "BUNDLE_DELETE_RECIPE" //For delete recipe from recipeDetail screen, we need to delete that recipe from RecipeFragment(RecipeListing)
    const val BUNDLE_KEY_DELETE_RECIPE_FROM_DEVICE = "BUNDLE_KEY_DELETE_RECIPE_FROM_DEVICE" //Delete recipe from device = true (when we need to delete recipe from mobile), when user delete recipe for on2cook only no need to delete recipe from list

    var predefineSteps = hashMapOf(
        "soak" to Soakings(), "boil" to Boiling(), "add" to Adding()
    )

    fun getDp(dp: Int, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }

    fun setDateTimeCommand(): String {
        Log.e("PrinceEWW>>>", "DATETIME LOG - Send Time: ${System.currentTimeMillis() / 1000}")
        return "$DATETIME${System.currentTimeMillis() / 1000}"
    }

    fun writeImageToStorage(context: Context, imageUrl: String) {
        val root = File(context.externalCacheDir, "Drive")
        if (!root.exists()) {
            root.mkdirs()
        }
        val file = File(root, "Audio.csv")
        file.createNewFile()
        getCSVFromUrl(
            imageUrl, file
        )

    }

    fun getCSVData(context: Context): File {
        val root = File(context.externalCacheDir, "Drive")
        return File(root, "Audio.csv")
    }

    private fun getCSVFromUrl(string: String, root: File) {
        val url: URL = URL(string)
        val connection: HttpURLConnection?
        try {
            connection = url.openConnection() as HttpURLConnection?
            val `is` = url.openStream()

            val dis = DataInputStream(`is`)

            val buffer = ByteArray(1024)
            var length: Int

            val fos = FileOutputStream(root)
            while (dis.read(buffer).also { length = it } > 0) {
                fos.write(buffer, 0, length)
            }
            dis.close()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun prepareScanObserver() {
        if (OnToCookApplication.rxBleClient.isScanRuntimePermissionGranted) {
            Log.e("TAG", "prepareScanObserver: ")
            if (isBluetoothOn()) OnToCookApplication.instance.isBluetoothConnected()
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    OnToCookApplication.instance.startBoundService(true)
                }
        } else {
            if (isBluetoothOn()) OnToCookApplication.instance.currentActivity?.requestBluetoothPermission(
                OnToCookApplication.rxBleClient
            )
        }
    }

    fun showAlertDialog(
        context: Context,
        title: String,
        message: String,
        positiveListener: DialogInterface.OnClickListener,
        negativeListener: DialogInterface.OnClickListener,
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Yes", positiveListener)
        builder.setNegativeButton("No", negativeListener)
        builder.show()
    }

    fun convertToBase64(attachment: File): String {
        return Base64.encodeToString(attachment.readBytes(), Base64.NO_WRAP)
    }

    fun getExternalStorageDirectory(): File? {
        return EXTERNAL_STORAGE_DIRECTORY
    }

    private val EXTERNAL_STORAGE_DIRECTORY = getDirectory("EXTERNAL_STORAGE", "/sdcard")

    private fun getDirectory(variableName: String?, defaultPath: String?): File {
        val path = System.getenv(variableName)
        return path?.let { File(it) } ?: File(defaultPath)
    }

    fun Float.Round(format: String = "%.1f"): Float {
        return String.format(format, this).toFloat()
    }

    fun Float.RoundTwo(format: String = "%.2f"): Float {
        return String.format(format, this).toFloat()
    }

    internal fun setRound(remainSecond: Int): Int {

        // Smaller multiple
        val a = (remainSecond / 10) * 10;

        // Larger multiple
        val b = a + 10;

        // Return of closest of two
        return if (remainSecond - a > b - remainSecond) b else a

    }

    fun provideAmazonS3Client(context: Context): AmazonS3Client {
        val clientConfiguration = ClientConfiguration()
        val also = 10.also { clientConfiguration.maxErrorRetry = it }
        clientConfiguration.connectionTimeout = 5 * 60 * 1000 // default is 10 setCurrentBleState
        clientConfiguration.socketTimeout = 5 * 60 * 1000 // default is 50 secs
        val basicAWSCredentials = BasicAWSCredentials(
            "AKIAX6MDCCR2BBSHV4DV", "MakTAnqO41fdfQHAm/cBZOjOP+1iIi3jQ7gMe/aQ"
        )
        return AmazonS3Client(
            basicAWSCredentials, Region.getRegion(Regions.AP_SOUTH_1), clientConfiguration
        )
    }


    fun provideTransferUtility(context: Context, amazonS3Client: AmazonS3Client): TransferUtility {
        return TransferUtility.builder().context(context.applicationContext)
            .s3Client(amazonS3Client).defaultBucket("ota-test2").build()
    }

    fun checkNavigation(message: String): Boolean {
        if (message.contains(",")) {
            val messageSize = message.split(",").size
            if (messageSize > 0) {
                val indStatus = message.uppercase().split(",")[0].replace(
                    "INDQUICKSTART=", ""
                )
                if (indStatus == IDLE) {
                    val indTime = message.uppercase().split(",")[1].replace(
                        "IND_RUN=", ""
                    )
                    if (indTime.toInt() != 0) {
                        return true
                    }
                    if (messageSize > 2) {
                        val magStatus = message.uppercase().split(",")[2].replace(
                            "MAGQUICKSTART=", ""
                        )
                        if (magStatus != IDLE) {
                            return true
                        } else {
                            if (messageSize > 3) {
                                val magTime = message.uppercase().split(",")[3].replace(
                                    "MAG_RUN=", ""
                                )
                                if (magTime.toInt() != 0) return true
                            }
                        }

                    }
                } else {
                    return true
                }
            }
        } else {
            if (message.uppercase() == INDQUICKSTART) {
                return true
            }
        }
        return false
    }

    fun readImageFromStorage(context: Context, imageUrl: String, callBack: (Bitmap) -> Unit) {
        var bitmap: Bitmap? = null
        val filename = this.getFileName(context, Uri.parse(imageUrl))
        context.openFileInput(filename).also {
            callBack.invoke(BitmapFactory.decodeStream(it))
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor.use {
                    if (cursor?.moveToFirst() == true) {
                        return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }
            }
            return uri.path?.substring(uri.path!!.lastIndexOf('/') + 1) ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len = 0
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    fun getEncodedImage(filePath: Uri?): String? {
        val imageStream: InputStream =
            OnToCookApplication.instance.contentResolver.openInputStream(filePath!!)!!
        Log.e("TAG", ": $imageStream")
        val selectedImage = BitmapFactory.decodeStream(imageStream)
        val baos = ByteArrayOutputStream()
        selectedImage.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val b: ByteArray = baos.toByteArray()
        val encImage = Base64.encodeToString(b, Base64.DEFAULT)
        return encImage
    }

    fun getDecodeImage(image: String): Bitmap {
        val decodedString = Base64.decode(
            image, Base64.DEFAULT
        )
        val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
        return decodedByte
    }

    fun convertRecipeDbToNewJson(recipe: Recipe): String {
//        val new = RecipeNew()
//        val nameType = object : TypeToken<ArrayList<String>>() {}.type
//        val nameList: ArrayList<String> = Gson().fromJson(recipe.name, nameType)
//        val audioList: ArrayList<String> = Gson().fromJson(recipe.audio1, nameType)
//        val audioList1: ArrayList<String> = Gson().fromJson(recipe.audio2, nameType)
//        new.name.addAll(nameList)
//        new.audio1.addAll(audioList)
//        new.audio2.addAll(audioList1)
//        new.id = "${recipe.id}"
//        new.description = recipe.description
//        val type = object : TypeToken<ArrayList<Instructions>>() {}.type
//        val typeIng = object : TypeToken<ArrayList<Ingredients>>() {}.type
//        val list: ArrayList<Instructions> = Gson().fromJson(recipe.Instruction, type)
//        val listIng: ArrayList<Ingredients> = Gson().fromJson(recipe.Ingredients, typeIng)
//        new.Instruction.addAll(list)
//        new.Ingredients.addAll(listIng)
        return Gson().toJson(recipe)
    }

    fun getRecentItemFromDb(recipe: Recipe): RecentItem {
        var name = ""
        if (recipe.name.isNotEmpty()) {
            name = recipe.name[0]
        }
        val recentItem = RecentItem(
            7, R.drawable.ic_vegetable, name, recipe.difficulty, "9 mins", "Indian|Snacks"
        )
        recipe.Ingredients.forEachIndexed { index, ingredients ->
            val step = IngredientsSteps(
                ingredients.image, ingredients.title, ingredients.weight, ingredients.text
            )
            recentItem.ingredients.add(index, step)
        }
        recentItem.name = name
        recentItem.recipe = Gson().toJson(recipe, Recipe::class.java)
        return recentItem
    }

    fun convertNewJsonToOldJson(recipe: Recipe): Recipe {
//        val new = Recipe()
//        val audioList: ArrayList<String> = ArrayList()
//        audioList.add("")
//        new.name = recipe.name[0]
//        new.description = recipe.description
//        new.imageUrl = recipe.description
//        new.difficulty = recipe.difficulty
//        new.tags = recipe.tags
//        new.category = recipe.category
//        new.subCategories = recipe.subCategories
//        val type = object : TypeToken<ArrayList<Instructions>>() {}.type
//        val typeIng = object : TypeToken<ArrayList<Ingredients>>() {}.type
//        val list: ArrayList<Instructions> = Gson().fromJson(recipe.Instruction, type)
//        val listIng: ArrayList<Ingredients> = Gson().fromJson(recipe.Ingredients, typeIng)
//        new.Instruction.addAll(list)
//        new.Ingredients.addAll(listIng)
        return recipe
    }

    fun convertRecipeToRecentlyPlayedRecipe(recipe: Recipe): RecentlyPlayedRecipe {
        val new = RecentlyPlayedRecipe()
        val nameType = object : TypeToken<RecentlyPlayedRecipe>() {}.type
        val nameList = Gson().toJson(recipe, nameType)
        DebugLog.e("convertRecipeToRecentlyPlayedRecipe $nameList")
        return new
    }

    fun generateAudioFile(audioNames: String): LiveData<File> {
        var tts: TextToSpeech? = null
        var destinationFile: File? = null
        val live: MutableLiveData<File> = MutableLiveData()
        tts = TextToSpeech(OnToCookApplication.instance) { i ->
            if (i == TextToSpeech.SUCCESS) {
                val locale = Locale("hi", "IN")
                tts?.language = locale
                val a: MutableSet<String> = HashSet()
                a.add("FEMALE")
                a.add("QUALITY_VERY_LOW")

                val voice = Voice(
                    "gu-in-x-gum-local",
                    locale,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_VERY_HIGH,
                    false,
                    a
                )
                tts?.voice = voice
                Log.e("TAG", "generateAudioFile: ${tts?.voice}")
                tts?.setPitch(0.5f)
                destinationFile =
                    File(OnToCookApplication.instance.externalCacheDir, "$audioNames High.wav")
                tts!!.synthesizeToFile(audioNames, null, destinationFile, audioNames)
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {

            }

            override fun onDone(utteranceId: String?) {
                live.postValue(destinationFile)
            }

            @Deprecated("Deprecated in Java", ReplaceWith("live.postValue(destinationFile)"))
            override fun onError(utteranceId: String?) {
                live.postValue(destinationFile)
            }

        })
        return live
    }

    fun getFormattedTime(indTime: Long): String {
        return String.format(
            "%02d : %02d",
            TimeUnit.MILLISECONDS.toMinutes(indTime),
            TimeUnit.MILLISECONDS.toSeconds(indTime) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(indTime)
            )
        )
    }

    fun getAudioFromTime(time: String): String {
        try {
            val string = java.lang.StringBuilder()
            val min = TimeUnit.SECONDS.toMinutes(time.toLong())
            val sec = time.toInt() % 60
            if (min != 0L) {
                string.append("$min")
                string.append("Minute")
            }
            if (sec != 0) {
                string.append("$sec")
                string.append("Second")
            }
            Log.e("TAG", "getAudioFromTime: ${string.toString()}")
            return string.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "0"
        }
    }

    fun getNameFromRecipe(recipeName: String): String {
        val nameType = object : TypeToken<java.util.ArrayList<String>>() {}.type
        val nameList: java.util.ArrayList<String> = Gson().fromJson(recipeName, nameType)
        return if (nameList.isNotEmpty()) {
            nameList[0]
        } else ""
    }

    fun getBundleFromCommand(message: String): Bundle {
        val bundle = Bundle()
        if (!message.contains(",")) {
            bundle.apply {
                putBoolean(IS_RESUME, false)
            }
        } else {
            val cmdSize: Int = message.split(",").size

            val stepNo =
                if (cmdSize > 2) message.split(",")[2].lowercase().replace("stepno=", "") else "0"
            if (cmdSize > 1) when (message.split(",")[1].uppercase().replace("MODE=", "")
                .uppercase()) {
                INGREDIENT_MODE -> {
                    bundle.apply {
                        putBoolean(IS_RESUME, true)
                        putInt(PREPARE_STEP, stepNo.toInt() - 1)
                        putBoolean(IS_PREPARE_RUNNING, true)
                    }
                }

                COOKING_MODE -> {
                    val indTime = message.split(",")[3].lowercase().replace("ind_run=", "")
                    val magTime = message.split(",")[4].lowercase().replace("mag_run=", "")
                    bundle.apply {
                        putBoolean(IS_RESUME, true)
                        putInt(CURRENT_STEP, stepNo.toInt())
                        putBoolean(IS_PREPARE_RUNNING, false)
                        putBoolean(
                            IS_PLAYING, indTime.toInt().coerceAtLeast(magTime.toInt()) != 0
                        )
                        putInt(CHANGE_TIME, indTime.toInt().coerceAtLeast(magTime.toInt()))
                    }
                }
            }
        }
        return bundle

    }

    fun getMode(message: String): Boolean {
        return if (message.contains(",")) {
            val cmdSize = message.split(",")
            cmdSize.size == 2
        } else {
            false
        }
    }

    fun getRecipeNameFromCommand(message: String): String {
        if (!message.contains(",")) {
            return message.replace("RECIPE=", "")
        } else {
            return message.split(",")[0].replace("RECIPE=", "")
        }
    }

    fun getPower(message: String): String {
        try {
            if (message.toInt() == 0) return "0"
            return "${0.048169 * message.toInt() + 1.28324}"
//            when (message.toInt()) {
//                in 0..12 -> {
//                    return "0.0"
//                }
//
//                in 13..16 -> {
//                    return "1.9"
//                }
//
//                in 27..52 -> {
//                    return "2.8"
//                }
//
//                in 53..87 -> {
//                    return "4.2"
//                }
//
//                in 88..107 -> {
//                    return "5.3"
//                }
//
//                in 108..130 -> {
//                    return "6.2"
//                }
//
//                in 131..153 -> {
//                    return "7.2"
//                }
//
//                in 154..172 -> {
//                    return "8.3"
//                }
//
//                in 173..190 -> {
//                    return "9.6"
//                }
//
//                in 191..205 -> {
//                    return "11"
//                }
//
//                else ->
//                    return ""
//            }
        } catch (_: Exception) {
            return ""
        }
    }

    fun getFloat(text: String): Float {
        return try {
            text.toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    fun getTemp(text: String): Float {
        return try {
            (-1.117f * text.toFloat()) + 199.2f
        } catch (e: Exception) {
            0f
        }
    }

    var RECIPES = mutableListOf(
        //5
        RecentItem(
            1,
            R.drawable.ic_veg_saute,
            "Veg Saute",
            "Medium",
            "9",
            desc = "Fried rice is a dish of cooked rice that has been stir-fried in a wok or a frying pan and is usually mixed with other ingredients such as eggs, vegetables, seafood, or meat. It is often eaten by itself or as an accompaniment to another dish",
            ingredients = mutableListOf(
                IngredientsSteps(
                    "R.drawable.ic_oil", "Oil", "50g|2Tbsp", "Refined"
                ), IngredientsSteps(
                    "R.drawable.ic_onion", "Vegetable", "50g|1Tbsp", "Finely Chopped"
                ), IngredientsSteps(
                    "R.drawable.ic_carrot", "Done", "50g|1/2Cup", "Finely Chopped"
                ), IngredientsSteps(
                    " R.drawable.ic_bean", "French beans", "50g|1/4 Cup", "Finely Chopped"
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbs", "51%", "1500 gms"
                ), Nutritional(
                    "Protein", "16%", "504 gms"
                ), Nutritional("Fat", "33%", "594 gms"), Nutritional("Calories", "12600", "")
            ),
            recipe = "{\n" + "   \"name\":\n" + "      \"Veg Saute\",\n" + "   \"audio\":\"98\",\n" + "   \"ingredients\":[\n" + "      {\n" + "         \"id\":\"1\",\n" + "         \"title\":\" Cut Vegetables\",\n" + "         \"app_audio\":\"Cut Vegetables. 1 Cup.\",\n" + "         \"text\":\" Take 1 step of Cut Vegetables in a bowl. Wash & rinse atleast twice to remove the excess starch\",\n" + "         \"weight\":\"1 Cup\",\n" + "         \"audio\":\"2\"\n" + "      },\n" + "      {\n" + "         \"id\":\"2\",\n" + "         \"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "         \"text\":\"\",\n" + "         \"weight\":\"\",\n" + "         \"audio1\":\"11\",\n" + "         \"audio2\":\"\"\n" + "      }\n" + "   ],\n" + "   \"instruction\":[\n" + "      {\n" + "         \"id\":\"1\",\n" + "         \"Text\":\"oil\",\n" + "         \"app_audio\":\"Add oil\",\n" + "         \"Weight\":\"0\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"60\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"10\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"2\",\n" + "         \"Text\":\"vegetable\",\n" + "         \"app_audio\":\"Add Vegetables\",\n" + "         \"Weight\":\"1 Cup\",\n" + "         \"Magnetron_on_time\":\"240\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"240\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"11\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"3\",\n" + "         \"Text\":\"done\",\n" + "         \"app_audio\":\"Veg saute is ready\",\n" + "         \"Weight\":\"1Tbsp\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"0\",\n" + "         \"Induction_on_time\":\"0\",\n" + "         \"Induction_power\":\"0\",\n" + "         \"Audio\":\"12\",\n" + "         \"lid\":\"open\"\n" + "      }\n" + "   ]\n" + "}\n"
        )/*, RecentItem(
            2,
            R.drawable.ic_ver_rice,
            "Veg Fried Rice",
            "Medium",
            "9",
            desc = "Fried rice is a dish of cooked rice that has been stir-fried in a wok or a frying pan and is usually mixed with other ingredients such as eggs, vegetables, seafood, or meat. It is often eaten by itself or as an accompaniment to another dish",
            ingredients = mutableListOf(
                IngredientsSteps(
                    R.drawable.ic_oil, "Oil", "50g|2Tbsp", "Refined"
                ), IngredientsSteps(
                    R.drawable.ic_onion, "Garlic", "50g|1Tbsp", "Finely Chopped"
                ), IngredientsSteps(
                    R.drawable.ic_carrot, "Carrots", "50g|1/2Cup", "Finely Chopped"
                ), IngredientsSteps(
                    R.drawable.ic_bean, "French beans", "50g|1/4 Cup", "Finely Chopped"
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbs", "51%", "1500 gms"
                ), Nutritional(
                    "Protein", "16%", "504 gms"
                ), Nutritional("Fat", "33%", "594 gms"), Nutritional("Calories", "12600", "")
            ),
            recipe = "{\n" + "   \"name\":\n" + "      \"Veg Fried Rice\",\n" + "   \"audio\":\"30\",\n" + "   \"ingredients\":[\n" + "      {\n" + "         \"id\":\"1\",\n" + "         \"title\":\" Wash Rice\",\n" + "         \"app_audio\":\"Wash Rice. 1 Cup.\",\n" + "         \"text\":\" Take 1 step of Rice in a bowl. Wash & rinse atleast twice to remove the excess starch\",\n" + "         \"weight\":\"1 Cup\",\n" + "         \"audio\":\"2\"\n" + "      },\n" + "      {\n" + "         \"id\":\"2\",\n" + "         \"title\":\"Soak Rice \",\n" + "         \"app_audio\":\"Soak Rice. 1 Cup.\",\n" + "         \"text\":\" Take 1 step of Rice in a bowl. Wash & rinse atleast twice to remove the excess starch\",\n" + "         \"weight\":\"1 Cup\",\n" + "         \"audio\":\"3\"\n" + "      },\n" + "      {\n" + "         \"id\":\"3\",\n" + "         \"title\":\"Ginger&Spices\",\n" + "         \"app_audio\":\"Keep Finely chopped Ginger. 1 Table Spoon.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\"1 Tbsp\",\n" + "         \"audio\":\"4\"\n" + "      },\n" + "      {\n" + "         \"id\":\"4\",\n" + "         \"title\":\"Green Chilly \",\n" + "         \"app_audio\":\"Keep Chopped Green Chilly. 1 Table Spoon.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\" 1 Cup\",\n" + "         \"audio\":\"5\"\n" + "      },\n" + "      {\n" + "         \"id\":\"5\",\n" + "         \"title\":\"Diced Onions \",\n" + "         \"app_audio\":\"Keep Diced Onions. 2 Serves.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\" 1 Cup\",\n" + "         \"audio\":\"6\"\n" + "      },\n" + "      {\n" + "         \"id\":\"6\",\n" + "         \"title\":\"Diced French Beans \",\n" + "         \"app_audio\":\"Keep Diced French Beans. 2 Serves.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\" 1 Cup\",\n" + "         \"audio\":\"7\"\n" + "      },\n" + "      {\n" + "         \"id\":\"7\",\n" + "         \"title\":\"Chopped Potatoes \",\n" + "         \"app_audio\":\"Keep Chopped Potatoes. 2 Serves.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\" 2 Serves\",\n" + "         \"audio\":\"8\"\n" + "      },\n" + "      {\n" + "         \"id\":\"8\",\n" + "         \"title\":\"Chopped Cabbage \",\n" + "         \"app_audio\":\"Keep Chopped Cabbage. 2 Serves.\",\n" + "         \"text\":\" Chop into small pieces\",\n" + "         \"weight\":\"2 Serves\",\n" + "         \"audio\":\"9\"\n" + "      },\n" + "{\n" + "         \"id\":\"9\",\n" + "         \"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "         \"text\":\"\",\n" + "         \"weight\":\"\",\n" + "         \"audio1\":\"11\",\n" + "         \"audio2\":\"\"\n" + "      }\n" + "   ],\n" + "   \"instruction\":[\n" + "      {\n" + "         \"id\":\"1\",\n" + "         \"Text\":\"PreHeat\",\n" + "         \"app_audio\":\"PreHeat the Pan\",\n" + "         \"Weight\":\"0\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"30\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"10\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"3\",\n" + "         \"Text\":\"Oil\",\n" + "         \"app_audio\":\"Add Refined Oil\",\n" + "         \"Weight\":\"2Tbsp\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"30\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"11\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"3\",\n" + "         \"Text\":\"Ginger & Spices\",\n" + "         \"app_audio\":\"Add Ginger & Spices\",\n" + "         \"Weight\":\"1Tbsp\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"40\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"12\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"4\",\n" + "         \"Text\":\"Onions\",\n" + "         \"app_audio\":\"Add Chopped Onions\",\n" + "         \"Weight\":\"1/4 cup\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"40\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"13\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"5\",\n" + "         \"Text\":\"Beans\",\n" + "         \"app_audio\":\"Add French Beans\",\n" + "         \"Weight\":\"2 serves\",\n" + "         \"Magnetron_on_time\":\"0\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"60\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"14\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"7\",\n" + "         \"Text\":\"Potatoes\",\n" + "         \"app_audio\":\"Add Potatoes\",\n" + "         \"Weight\":\"2 serves\",\n" + "         \"Magnetron_on_time\":\"15\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"15\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"16\",\n" + "         \"lid\":\"close\"\n" + "      },\n" + "      {\n" + "         \"id\":\"8\",\n" + "         \"Text\":\"Tomatoes\",\n" + "         \"app_audio\":\"Add Tomatoes\",\n" + "         \"Weight\":\"2 serves\",\n" + "         \"Magnetron_on_time\":\"15\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"15\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"17\",\n" + "         \"lid\":\"close\"\n" + "      },\n" + "      {\n" + "         \"id\":\"9\",\n" + "         \"Text\":\"Rice\",\n" + "         \"app_audio\":\"Add Rice\",\n" + "         \"Weight\":\"2 serves\",\n" + "         \"Magnetron_on_time\":\"30\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"30\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"28\",\n" + "         \"lid\":\"close\"\n" + "      },\n" + "      {\n" + "         \"id\":\"10\",\n" + "         \"Text\":\"Water\",\n" + "         \"app_audio\":\"Add Water\",\n" + "         \"Weight\":\"1 cup\",\n" + "         \"Magnetron_on_time\":\"30\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"30\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"28\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"9\",\n" + "         \"Text\":\"Salt\",\n" + "         \"app_audio\":\"Add Salt\",\n" + "         \"Weight\":\"half cup\",\n" + "         \"Magnetron_on_time\":\"30\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"30\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"28\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "      {\n" + "         \"id\":\"10\",\n" + "         \"Text\":\"Close Lid\",\n" + "         \"app_audio\":\"Close the Lid\",\n" + "         \"Weight\":\"half cup\",\n" + "         \"Magnetron_on_time\":\"30\",\n" + "         \"Magnetron_power\":\"100\",\n" + "         \"Induction_on_time\":\"360\",\n" + "         \"Induction_power\":\"100\",\n" + "         \"Audio\":\"28\",\n" + "         \"lid\":\"open\"\n" + "      },\n" + "{\n" + "\"id\":\"11\",\n" + "\"Text\":\"Done\",\n" + "         \"app_audio\":\"Veg Fried Rice is ready\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "}\n" + "   ]\n" + "}\n"
        ),
        //1
        RecentItem(
            3,
            R.drawable.pocorn,
            "Butter Popcorn",
            "Medium",
            "10",
            desc = "Popcorn is a variety of corn kernel which expands and puffs up when heated; the same names are also used to refer to the foodstuff produced by the expansion. A popcorn kernel's strong hull contains the seed's hard, starchy shell endosperm with 14–20% moisture, which turns to steam as the kernel is heated.",
            ingredients = mutableListOf(
                IngredientsSteps(
                    R.drawable.ic_oil, "Oil", "3Tbsp", "Coconut Oil"
                ), IngredientsSteps(
                    R.drawable.ic_popcorn_kernels, "Popcorn Kernels", "1/3Cups", "High Quality"
                ), IngredientsSteps(
                    R.drawable.ic_butter_ing, "Butter", "1Tbsp", "Optional"
                ), IngredientsSteps(
                    R.drawable.ic_salt, "Salt", "To Taste", ""
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbohydrates", "51%", "18.6 gms"
                ), Nutritional(
                    "Protein", "16%", "3 gms"
                ), Nutritional("Fat", "33%", "1.1 gms"), Nutritional("Calories", "93", "")
            ),
            recipe = "{\n" + "\"name\": \"Butter Popcorn\",\n" + "\"ingredients\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"title\":\"Popcorn ACTII\",\n" + "         \"app_audio\":\"We will use Act2 Popcorn. 1 Packet.\",\n" + "\"text\":\" \",\n" + "\"weight\":\" 1 Packet\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"title\":\"Butter\",\n" + "         \"app_audio\":\"Keep Butter. 1 table spoon.\",\n" + "\"text\":\"\",\n" + "\"weight\":\"1 Tbsp\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "}\n" + "],\n" + "\"instruction\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"Text\":\"PreHeat\",\n" + "         \"app_audio\":\"PreHeat the Pan\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"Text\":\"Popcorn ACTII\",\n" + "         \"app_audio\":\"We will use Act2 Popcorn\",\n" + "\"Weight\":\"1 Packet \",\n" + "\"Magnetron_on_time\":\"20\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"20\",\n" + "\"Induction_power\":\"100\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"Text\":\"Butter\",\n" + "         \"app_audio\":\"Add Butter and Stir\",\n" + "\"Weight\":\"1 Tbsp.\",\n" + "\"Magnetron_on_time\":\"10\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"10\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"Text\":\"Close Lid\",\n" + "         \"app_audio\":\"Close the Lid\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"60\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"60\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"close\"\n" + "},\n" + "{\n" + "\"id\":\"5\",\n" + "\"Text\":\"Done\",\n" + "         \"app_audio\":\"Butter Popcorn is ready\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "}\n" + "]\n" + "}"
        ),
        //
        RecentItem(
            1,
            R.drawable.boiledaloo,
            "Vegetable Pattie",
            "Medium",
            "15",
            desc = "One of several varieties of medium size round red or white potatoes that are suitable for boiling, as well as roasting or frying. They are firm textured and keep their shape well when boiled.",
            ingredients = mutableListOf(
                IngredientsSteps(
                    R.drawable.ic_oil, "Oil", "3Tbsp", "Coconut Oil"
                ), IngredientsSteps(
                    R.drawable.ic_baby_potatoes, "Baby Potatoes", "", "High Quality"
                ), IngredientsSteps(
                    R.drawable.ic_salt, "Salt", "To Taste", ""
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbohydrates", "51%", "18.6 gms"
                ), Nutritional(
                    "Protein", "16%", "3 gms"
                ), Nutritional("Fat", "33%", "1.1 gms"), Nutritional("Calories", "93", "")
            ),
            recipe = "{\n" + "\"name\":\"Vegetable Pattie\",\n" + "\"ingredients\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"title\":\"Frozen Pattie\",\n" + "         \"app_audio\":\"Keep Frozen Pattie Ready. 2 Pieces.\",\n" + "\"text\":\" \",\n" + "\"weight\":\" 2 Pieces\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"title\":\"Refined Oil\",\n" + "         \"app_audio\":\"Keep Refined Oil Ready. 1/2 Cup.\",\n" + "\"text\":\"\",\n" + "\"weight\":\"1/2 Cup\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "}\n" + "],\n" + "\"instruction\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"Text\":\"PreHeat\",\n" + "         \"app_audio\":\"PreHeat the Pan\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"Text\":\"Oil\",\n" + "         \"app_audio\":\"Add and Preheat oil for frying\",\n" + "\"Weight\":\"1 Packet \",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"Text\":\"Frozen Pattie\",\n" + "         \"app_audio\":\"Add Frozen Pattie\",\n" + "\"Weight\":\"2 Pieces\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"49\",\n" + "\"Audio2\":\"50\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"Text\":\"Close Lid\",\n" + "         \"app_audio\":\"Close the Lid\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"60\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"60\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"close\"\n" + "},\n" + "{\n" + "\"id\":\"5\",\n" + "\"Text\":\"Done\",\n" + "         \"app_audio\":\"Vegetable Pattie is ready\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "}\n" + "]\n" + "}"
        ),
        //3
        RecentItem(
            5,
            R.drawable.ic_idly,
            "Idli",
            "Medium",
            "12",
            desc = "Idli is soft & fluffy steamed cake made of fermented rice & lentil batter. These are one of the healthiest protein packed breakfasts from South Indian cuisine. Idli are easily digestible as the rice & lentils known as dal are socked, ground, fermented & then prepared by steaming the batter. These are served with a chutney and or with a Tiffin Sambhar.",
            ingredients = mutableListOf(
                IngredientsSteps(
                    R.drawable.ic_urad_dal, "Urad dal", "1/2Cup", ""
                ), IngredientsSteps(
                    R.drawable.ic_idli_rice, "Idli Rice", "1Cups", "Parboiled Rice"
                ), IngredientsSteps(
                    R.drawable.ic_thick_poha, "Thick Poha", "2Tbsp", ""
                ), IngredientsSteps(
                    R.drawable.ic_water, "Water", "1/2Cups", ""
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbohydrates", "2%", "7.2 gms"
                ), Nutritional("Fiber", "1%", "0.3 gms"), Nutritional("Fat", "0%", "0.1 gms")
            ),
            recipe = "{\n" + "\"name\": \"Idli\",\n" + "\"ingredients\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"title\":\"Urad Dal\",\n" + "         \"app_audio\":\"Keep Urad Dal. 1/2 Cup.\",\n" + "\"text\":\" \",\n" + "\"weight\":\"1/2 Cup\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"title\":\"Rice\",\n" + "         \"app_audio\":\"Keep Rice. 1 Cup.\",\n" + "\"text\":\"\",\n" + "\"weight\":\"1 Cup\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"title\":\"Wash Dal & Rice\",\n" + "         \"app_audio\":\"Wash Dal & Rice\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"title\":\"Sock Dal & Rice\",\n" + "         \"app_audio\":\"Sock Dal & Rice\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "},\n" + "{\n" + "\"id\":\"5\",\n" + "\"title\":\"Grind & Mix for Batter\",\n" + "         \"app_audio\":\"Grind & Mix dal & rice for Batter\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "},\n" + "{\n" + "\"id\":\"6\",\n" + "\"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\"\n" + "}\n" + "],\n" + "\"instruction\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"Text\":\"Preheat\",\n" + "         \"app_audio\":\"PreHeat the Pan\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"Text\":\"Water\",\n" + "         \"app_audio\":\"Add Water\",\n" + "\"Weight\":\"1 cup\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"Text\":\"Idlis\",\n" + "         \"app_audio\":\"Add batter for Idlis\",\n" + "\"Weight\":\"6 pcs\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"close\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"Text\":\"Close Lid\",\n" + "         \"app_audio\":\"Close the Lid\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"180\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"180\",\n" + "\"Induction_power\":\"100\",\n" + "\"lid\":\"close\"\n" + "},\n" + "{\n" + "\"id\":\"5\",\n" + "\"Text\":\"Done\",\n" + "         \"app_audio\":\"Idli is ready\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "}\n" + "]\n" + "}"
        ),
        //2
        RecentItem(
            4,
            R.drawable.ic_frozen_pizza,
            "Frozen Pizza",
            "Medium",
            "12",
            desc = "Frozen pizzas have their time and place, like when we need a backup meal when we don’t have time to shop or an easy heat-and-serve dinner for babysitters. There’s nothing wrong with a good store-bought frozen pizza in these situations, but making your own homemade frozen pizzas from scratch will probably save you some pennies — plus, you get exactly the toppings you want!\n" + "\n" + "It’s also easy to do. With just two little tricks you can fill your freezer with all the made-ahead frozen pizzas you could ever want.",
            ingredients = mutableListOf(
                IngredientsSteps(
                    R.drawable.ic_pizza_dough, "Pizza Dough", "As Desired", ""
                ), IngredientsSteps(
                    R.drawable.ic_oil, "Canola oil", "1Tbsp", ""
                ), IngredientsSteps(
                    R.drawable.ic_pizza_sauce, "Pizza sauce", "As Desired", ""
                ), IngredientsSteps(
                    R.drawable.ic_pizza_toppings,
                    "Pizza Toppings",
                    "As Desireds",
                    "Meat Veggies, Cheese"
                )
            ),
            nutritionalInfo = mutableListOf(
                Nutritional(
                    "Carbohydrates", "2%", "7.2 gms"
                ), Nutritional("Fiber", "1%", "0.3 gms"), Nutritional("Fat", "0%", "0.1 gms")
            ),
            recipe = "{\n" + "\"name\": \"Frozen Pizza\",\n" + "\"ingredients\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"title\":\"Butter\",\n" + "         \"app_audio\":\"Keep Butter. 1 table spoon.\",\n" + "\"text\":\" \",\n" + "\"weight\":\"1 Tbsp\",\n" + "\"audio1\":\"36\", \n" + "\"audio2\":\"37\"   \n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"title\":\"Butter Paper\",\n" + "         \"app_audio\":\"Butter Paper\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\",\n" + "\"audio1\":\"38\", \n" + "\"audio2\":\"24\" \n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"title\":\"Frozen Pizza\",\n" + "         \"app_audio\":\"One Frozen Pizza\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\",\n" + "\"audio1\":\"11\",\n" + "\"audio2\":\"\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"title\":\"Seasoning\",\n" + "         \"app_audio\":\"Keep Seasoning\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\",\n" + "\"audio1\":\"11\",\n" + "\"audio2\":\"\"\n" + "},\n" + "{\n" + "\"id\":\"5\",\n" + "\"title\":\"Now COOK\",\n" + "         \"app_audio\":\"Get Ready to Cook\",\n" + "\"text\":\"\",\n" + "\"weight\":\"\",\n" + "\"audio1\":\"11\",\n" + "\"audio2\":\"\"\n" + "}\n" + "],\n" + "\"instruction\":[\n" + "{\n" + "\"id\":\"1\",\n" + "\"Text\":\"Butter Paper\",\n" + "         \"app_audio\":\"place butter paper, and spread butter over it\",\n" + "\"Weight\":\"1 cup\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"36\", \n" + "\"Audio2\":\"42\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"2\",\n" + "\"Text\":\"Pizza\",\n" + "         \"app_audio\":\"Place Frozen Pizza\",\n" + "\"Weight\":\"1 Piece\",\n" + "\"Magnetron_on_time\":\"30\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"30\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"55\", \n" + "\"Audio2\":\"29\",\n" + "\"lid\":\"open\"\n" + "},\n" + "{\n" + "\"id\":\"3\",\n" + "\"Text\":\"Close Lid\",\n" + "         \"app_audio\":\"Close the Lid\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"100\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"100\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "},\n" + "{\n" + "\"id\":\"4\",\n" + "\"Text\":\"Done\",\n" + "         \"app_audio\":\"Frozen Pizza is ready\",\n" + "\"Weight\":\"\",\n" + "\"Magnetron_on_time\":\"0\",\n" + "\"Magnetron_power\":\"100\",\n" + "\"Induction_on_time\":\"0\",\n" + "\"Induction_power\":\"100\",\n" + "\"Audio1\":\"22\",\n" + "\"Audio2\":\"31\",\n" + "\"lid\":\"close\"\n" + "}\n" + "]\n" + "}"
        )*/
    )

//00001805-0000-1000-8000-00805f9b34fb
//ab0828b1-198e-4351-b779-901fa0e0371e

//    var NOTI_UUID = "CharUUIDNotify"
//    var WRITE_UUID = "CharUUIDWrite"

    /*

    -    remove unwanted code from current version..

    Test Cases :

    -   Need to show alert that some recipe is currently running... (if connect device in between running recipe)
        -   Running recipe section - snake bar, section in home.....
        -   if going to start other recipe when already other running - show alert - start new one or continue with running one

    -   need to design data package that allows to easily scale system
        -   when 2-3 recipe working at same time need to sync all three data
        -   identify threading system to allows to connect multiple bluetooth at a same time..

        package - recipe|(pre|step)|(pre-step no.)|(running|pause)|(currentTime)|
        1(0/1)1(1/0)
        currentTime - step time or actual clock time

    -   Highlight for device is currently connected...   (green bar or red bar.....)
        if disconnected - tap on bar allows to reconnect

    -   get already created recipe listing from firebase...

    -   create recipe :
        -   get listing of recipe from api
        -   get recipe details - parse response -   create optimised recipe steps
        -   [add required steps - close lid open lid preheat]
        -   save recipe to firebase
        -   allows to edit delete recipe from firebase....

    -   cook for created recipe
    -   get listing of recipes from sd card (read sdcard command)
        [for check to send json and sound file or need to update recipe in json file
        - keep file name as recipe_id_timestamp - convert time step to date time and check with firebase]
    -   create sound file from steps (pre - steps)
    -   upload json and sound file to device.....

    -   create separate page to select json file and upload to device....
    -   create separate page to get listing files and manage...

    -   Allows to minimize application even if recipe running - stop playback, resume when again start
    -   Allows to play pause recipe - from app to device and vice versa
    -   Allows to close current recipe - on back press in app - same as from device..

    -   Sync recipe with device time.......
    -   when done recipe come out from recipe running page

    -   if user change in steps - (+, -) in step timing - [this will be new recipe or need to edit on existing]
    -   when recipe completed or user click on back - if edited ask for edit existing recipe and close or only close

    -   if device disconnected at any time in between running recipe -
            step progress - come out of progress screen
            pause progress - disconnect dialog  - reconnect - retrieve data - rearrange

    -   What to do with delay - in app when step at 10 second then in device it start for step
    -   time share - when get interrupt it will stop recipe progress

    -   need one screen to show progress of uploading json and sound file to device

    -   add recipe - type name - get recipe listing - [get recipe details] -
        parse steps - [optimize steps - add required steps] -
        [upload recipe to firebase] - recipe listing (get from firebase) -
        click on recipe - Recipe details - cook recipe -
        check for connection with device - connect if not connected -
        [check if any other recipe working or not - need data from device] -
        [display dialog - continue with running recipe or start new one] -
            [continue with running  - retrieve current running recipe]
            [readjust steps for recipe]
        [start new one]
        [read sd card  - get listing of recipe - check for date and recipe id -
        if not found - create sound file from text - upload json and sound file - wait till upload ~ 3-4 min]
        --[how to assign name for audio so can identify easily]

        start recipe
        [on back press - dialog for asking if need to stop recipe or not]
            [yes  - send command to stop recipe to device]

        --[remove delay between pre-step and actual steps]
        --[need to sync steps - use recipe time - send at every 500ms ~ 1ms  - set timer at application]
        --[issues in increment - decrement time from device  - need to reflect to application]

        --[remove update based on progress  - use time] - app side

        -   sending file to device taking long for sound file
        -   for txt file it's working fine..
        -   70-75% accuracy for recipe steps
        -   some recipe not found for api
        -   api prising - 150 call / day
        -   add other steps
        -   new ui for create recipe
        -   dynamic fragment for 2 different recipe
        -   same app with multiple ble device
        -   parsing html form...  1.




    //https://stackoverflow.com/questions/37185677/java-io-ioexception-error-running-exec-working-directory-null-environment-n

    /data/user/0/com.invent.ontocook/files/ffmpeg -y -I /storage/emulated/0/Android/data/com.invent.ontocook/files/sound.wav /storage/emulated/0/Android/data/com.invent.ontocook/files/sound.mp3



    //bar 3 second
    //need to update add second popup
    //add induction and magnatron time on bar
    //show step to done action.
    //some time issues with odd increment values in add plus minus time (at end not completed all steps)





    1- popcorn
    2 -pizza
    3 - idli
    4 - patty
    5- vegrize






    Plan for get recipe content :


    1.  need to search for all recipe listing match with query - based on name -
    1.  api - free with no option for specify website
        get id and get content
    2.  hit all url with query - 10 website - apply ranking - o(n)
        B2B -   need recipe only once - repeat same again
        B2C -   need recipe only once - user modify - create new one - cycle
            -   10
            -   order based on ranking - sort..   - get one by one -
            -   loop
                -   search for query
                -   get listing - get url
                    - loop - check if exact match with name - different response time
                -   call api for get response..
    3.  not found recipe in auto listing then what
    4.  option for get data - api -
        -   get listing of recipe from api - auto suggest
        -   get data from selected recipe websites  -   1,2,3
        -

     Final Plan :

        1.  Call api for get suggestion for recipe name
        2.  [Algo 2 -
                1.  get sorted website url listing for recipe based on criteria (success rate - fail rate)
                2.  Loop - call each url for get recipe
                3.  on success stop
                ]
        3.  Get recipe data from url using api
        4.  Parse data based on keyword



        create one local database for store recipe search action
        - url   lastaccess(datetime)    success  fail  totalcall created(datetime)

        ->  first install app install with zero
        ->  when api called after get result - modify database
        ->  when search next get data based on success rate of website


        ->  need to store recipe in firebase database?
            1 Gb - more enough for store recipe for now,,
            need to update  - code for get/store firebase database with server [server api code also include]

        ->  Send JSON and mp3 send time at initial also when update recipe
            also need to identify which data change and also send only those file
            also identify if sound file is already there

        ->  Change sound file for all recipe

        ->  we can also identify best available offer for order and do order [criteria to choose from vendor]

        ->

        --> identify some indian words  - Ex. boiled aloo (instead of potatoes)




        ----> problem  - ffmpeg takes long size - solve with split apk option.



        -   we can prioritize with success probability for different device  - will different
        -


        * some recipe not found from name - mutter paneer, rava dosa

        1   https://www.allrecipes.com/recipe/paneer-makhani/ - modify date
        2   https://www.tasteofhome.com/recipes/paneer-makhani/ -
        3   https://www.bbcgoodfood.com/recipes/paneer-makhani
        4   https://hebbarskitchen.com/paneer-makhani/ - 1
        5   https://www.indianhealthyrecipes.com/rava-dosa/



        option
        1.  spoonculer api
        2.  spooncluer api auto complete -> search for all api -> parse response
        3.  google search



        1.  auto search
        2.  google search with query
        3.  issues : not getting recipe data for some pages
            Fix issues for pages
        4.




        Scheduling
            schedule recipe on calender (with no of person)
            Notify for scheduled recipe
        Ordering
            Identify Ingredients
                Auto identify ingredients requirement for scheduled recipe (consider inventory stocks)
                Bunch all ingredients of scheduled recipe for one week(can be vary)
                Notify for confirmation of order
            Identify Vendor [Amazone, bigbasket, local vendor]
                Identify vendor based on available/best suite offer
            Order Ingredients
                Order ingredients based on required quantity
            Notify for order status
                Notify for confirm, expected to receive order

        Inventory Management



        Sharing
            Share updated/created recipe on global or local community


        Voice Command
            Search for recipe
            Manage navigation in recipe
            AI - get suggestion from system for recipe cooking

            Suggestion from system for amount of time for recipe
            (based on criteria - availability of other device)


        class soaking extend step

                            step
        soaking     boiling     fine chopping       preheating


        soaking dal

        1.



        ingre
            name
            amount
            unit


        soaking
            getsteps(ing, )
            return array of step


        array

        hasmap

        soaking         soaking
        boiling         boiling



        soak rice & dal
        ->  Soaking(ingA[], ingB[], )


        Soaking(){

        }


        1. voice - Auto - auto recipe generate
        2. manual   -   manually add in between step - ui required





        Take "QU1" "A" in a bowl - voice
        Add "B" to "A" and Wash twice - voice
        Immerse "A" in "QU2" "B" and leave for "Ti" - voice


        id
        text
        desc
        voicetext
        megPower
        inducpower
        megtime
        inductime


     */


    /*

    Already preheat
        run till 40 seconds
        wait for 30 seconds
        run for 40 min.
        wait for 30 seconds
        run for 30 seconds


     */


}