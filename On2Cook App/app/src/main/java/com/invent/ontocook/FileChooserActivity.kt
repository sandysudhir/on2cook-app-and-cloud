package com.invent.ontocook

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog
import com.google.gson.Gson
import com.invent.ontocook.dialog.DeviceScanDialog
import com.invent.ontocook.loader.LoadingUtils
import com.invent.ontocook.models.Firmware
import com.invent.ontocook.utils.AudioFormat
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.getTextFromFile
import com.invent.ontocook.utils.requestBluetoothPermission
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_file_chooser.*
import kotlinx.android.synthetic.main.view_header.*
import java.io.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*


class FileChooserActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION_EXTERNAL = 103
    var dialog: FilePickerDialog? = null
    var file: File? = null
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var deviceScanDialog: DeviceScanDialog? = null
    private val rxBleClient = OnToCookApplication.rxBleClient
    private lateinit var communicationReceiver: BroadcastReceiver

    private var tts: TextToSpeech? = null
    private val TAG = this::class.java.simpleName
    var firmware: Firmware? = null
    var isSoundFile = false
    var isSendJsonFile = false
    var acknowledgementIndex: Int = 0     // 0 Initialise
    val mapDataToWrite: HashMap<Int, String> = HashMap()      // 1 For File Receive (Initialise)
    val mapDataToWriteAudio: HashMap<Int, ByteArray> =
        HashMap()      // 1 For File Receive (Initialise)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_chooser)

        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener {
            println("status    ${TextToSpeech.SUCCESS == it} ")
            tts?.language = Locale.US
        })

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                println("done     $utteranceId")
                runOnUiThread {
                    LoadingUtils.hideDialog()
                    convertWavToMp3()
                    //sendFileDataToDevice(file!!)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                println("error     $utteranceId")
                runOnUiThread {
                    LoadingUtils.hideDialog()
                }
            }

            override fun onStart(utteranceId: String?) {
                println("start     $utteranceId")
                runOnUiThread {
                    LoadingUtils.showDialog(
                        this@FileChooserActivity,
                        true,
                        "Generating Sound File.."
                    )
                }
            }
        })

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_CONNECTION_ABORT, Constants.EVENT_BLE_CONNECTION_ERROR -> {
                        var message =
                            intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        Toast.makeText(this@FileChooserActivity, message, Toast.LENGTH_LONG).show()
                        toggleDeviceScanDialog()
                    }
                    Constants.EVENT_BLE_CONNECTION_SUCCESS -> {
                        toggleDeviceScanDialog()
                        if (isSoundFile) {
                            generateSoundFile()
                        } else {
                            file?.let { sendFileDataToDevice(it) }
                        }
                    }
                    Constants.EVENT_BLE_CONNECTION_INIT -> {
                        toggleDeviceScanDialog(true)
                    }
                    Constants.EVENT_BLE_CONNECTION_FOUND_DEVICE -> {
                        var message =
                            intent?.getStringExtra(Constants.EVENT_ERROR_MESSAGE) ?: ""
                        if (deviceScanDialog != null) {
                            deviceScanDialog!!.updateResult(
                                "Connecting to $message"
                            )
                        }
                    }
                }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        communicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(Constants.EVENT_BLE_ACTION) ?: "") {
                    Constants.EVENT_BLE_NOTIFICATION -> {
                        var message = intent?.getStringExtra(Constants.EVENT_MESSAGE) ?: ""
                        if (message.uppercase().contains("FIRMWARE=")) {
                            if (message.uppercase()
                                    .replace("FIRMWARE=", "") != Constants.firmwareVersion
                            ) {
                                Log.e(TAG, "onReceive: Match")
                                btnCheck.text = "Update"
                                tvSubTitle3.text = "Please Update Firmware Version"
                            } else {
                                Log.e(TAG, "onReceive: UnMatch")
                                btnCheck.text = "Check"
                                tvSubTitle3.text = "Firmware is up to date"
                            }
                        }
                        if (message.toUpperCase() == "ACK_CANCEL") {
                            LoadingUtils.hideDialog()
                            file = null
                            tvFileName.text = ""
                            Toast.makeText(
                                applicationContext,
                                "File Already Exist. Please Select Another File",
                                Toast.LENGTH_LONG
                            ).show()
                        }
//                        if (message.toUpperCase().contains("ACK")) {
//                            acknowledgementIndex++
//                            if (isSendJsonFile) {
//                                sendNextData()
//                                handler.removeCallbacksAndMessages(null)
//                                val lastDataSent = acknowledgementIndex
//                                handler.postDelayed({
//                                    if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWrite.size + 2) {
//                                        sendNextData()
//                                    }
//                                }, 3000)
//                            } else {
//                                sendAudioData()
//                                handler.removeCallbacksAndMessages(null)
//                                val lastDataSent = acknowledgementIndex
//                                handler.postDelayed({
//                                    if (lastDataSent == acknowledgementIndex && lastDataSent != mapDataToWriteAudio.size + 2) {
//                                        sendAudioData()
//                                    }
//                                }, 3000)
//                            }
//                            Log.e("TAG", "onReceive: $message")
//                        }
//                        sendFileDataToDevice()
                    }
                    Constants.FILE_UPLOAD_SUCCESS -> {
                        LoadingUtils.hideDialog()
                        file = null
                        tvFileName.text = ""
                        Toast.makeText(
                            applicationContext,
                            "File Uploaded Successfully",
                            Toast.LENGTH_LONG
                        ).show()

                    }
                    Constants.EVENT_BLE_WRITE_SUCCESS -> {
//                        var jsonFile = loadJSONFromFile()
//                        if (jsonFile != null) {
//                            try {
//                                var name = JSONObject(jsonFile).getString("name")
//                                var filteredRecipe = RECIPES.filter { it.name == name }
//                                if (filteredRecipe.isNotEmpty()) {
//                                    filteredRecipe.first().recipe = jsonFile
//                                    var jsonRecipe = Gson().toJson(filteredRecipe.first())
//                                    SharedPreferencesManager.insertData(
//                                        applicationContext,
//                                        "recipe${filteredRecipe.first().id}",
//                                        jsonRecipe.toString()
//                                    )
//
//
//                                    println("Recipe updated successfully....")
//                                }
////                                var recipeItem =  Gson().fromJson(jsonFile.toString(), RecentItem::class.java)
////                                if(recipeItem != null){
////                                    SharedPreferencesManager.insertData(
////                                        applicationContext,
////                                        "recipe${recipeItem.id}",
////                                        jsonFile.toString()
////                                    )
////                                }
//                            } catch (e: Exception) {
//                                println("Error  $e")
//                            }
//                        }


//                        LoadingUtils.hideDialog()
//                        Toast.makeText(
//                            this@FileChooserActivity,
//                            "File uploaded successfully",
//                            Toast.LENGTH_SHORT
//                        ).show()
                    }
                    Constants.EVENT_BLE_WRITE_FAIL -> {
                        LoadingUtils.hideDialog()
                        if (OnToCookApplication.instance.isDeviceConnected()) {
                            Toast.makeText(
                                this@FileChooserActivity,
                                "File uploaded fail",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        val properties = DialogProperties()
        properties.selection_mode = DialogConfigs.SINGLE_MODE
        properties.selection_type = DialogConfigs.FILE_SELECT
        properties.root = File(DialogConfigs.DEFAULT_DIR)
        properties.error_dir = File(DialogConfigs.DEFAULT_DIR)
        properties.offset = File(DialogConfigs.DEFAULT_DIR)
        properties.extensions = null
        properties.show_hidden_files = false

        dialog = FilePickerDialog(this@FileChooserActivity, properties)
        dialog?.setTitle("Select a File")

        dialog?.setDialogSelectionListener {
            println("file   ${it.first()}")
            file = File(it.first())
            if (file != null && file!!.exists()) {
                tvFileName.text = file!!.name
            }
        }

        ivLeft.setOnClickListener {
            onBackPressed()
        }

        btnUploadFile.setOnClickListener {
            isSoundFile = false
            isSendJsonFile = true
            if (file != null && file!!.exists()) {
                acknowledgementIndex = 0     // 0 Initialise

                if (OnToCookApplication.instance.isDeviceConnected()) {
                    val str: String = getTextFromFile(file!!)
                    var size = str.toByteArray(
                        Charsets.UTF_8
                    ).size
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        "{\"RECIPE\":\"${file!!.nameWithoutExtension}\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    Log.e(TAG, "onCreate: Step 1 Sent Success")
                    OnToCookApplication.instance.bleBoundService.sendFileDataToDevice(
                        getTextFromFile(file!!)
                    )
                    LoadingUtils.showDialog(this, false, "File Uploading")
                } else {
                    prepareScanObserver()
                }
            } else {
                Toast.makeText(this@FileChooserActivity, "Please select File", Toast.LENGTH_SHORT)
                    .show()
            }
            //for Dummy Sending
//            val schedulTimer = Timer()
//            sendFileDataToDevice(file!!)
//            schedulTimer?.scheduleAtFixedRate(object : TimerTask() {
//                override fun run() {
//                    runOnUiThread {
//                        Log.e(TAG, "run: ${mapDataToWrite.size}")
//                        acknowledgementIndex++
//                        sendNextData()
//                        if (mapDataToWrite.size + 2 == acknowledgementIndex)
//                            schedulTimer.cancel()
//                        sendAudioData()
////                        sendAudioData()
//                    }
//                }
//            }, 0, 1000)

        }

        btnUploadSound.setOnClickListener {
            acknowledgementIndex = 0
            isSendJsonFile = false
            if (file != null && file!!.exists()) {
                val size = file!!.length().toInt()
                val bytes = ByteArray(size)
                try {
                    val buf = BufferedInputStream(FileInputStream(file))
                    buf.read(bytes, 0, bytes.size)
                    val bytes2 = file!!.readBytes()
                    Log.e(TAG, "onCreate: bytes2 ${bytes2.size}")
                    buf.close()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "{\"AUDIO\":\"${file!!.nameWithoutExtension}\",\"SIZE\":\"$size \",\"SAVE\":\"1\"}".toByteArray(
                        Charsets.UTF_8
                    )
                )
//            Log.e(TAG, "onCreate: ${file!!.length()}")
//            Log.e(TAG, "onCreate: ${bytes.size}")
//            OnToCookApplication.instance.bleBoundService.sendAudioFileFromChoose(
//                bytes,
//                file!!.nameWithoutExtension
//            )
//            OnToCookApplication.instance.bleBoundService.prepareAudioFile(bytes)
//            val schedulTimer = Timer()
//            schedulTimer.scheduleAtFixedRate(object : TimerTask() {
//                override fun run() {
//                    runOnUiThread {
//                        Log.e(TAG, "run: ${mapDataToWriteAudio.size}")
//                        acknowledgementIndex++
//
//                        if (mapDataToWriteAudio.size + 1 == acknowledgementIndex)
//                            schedulTimer.cancel()
//                        sendAudioData()
//                    }
//                }
//            }, 0, 800)

//            isSoundFile = true
//            if (etSoundText.text!!.isNotEmpty()) {
////                if (OnToCookApplication.instance.isDeviceConnected()) {
////                    generateSoundFile()
////                    //sendFileDataToDevice(file!!)
////                } else {
////                    prepareScanObserver()
////                }
//                generateSoundFile()
//            } else {
//                Toast.makeText(this@FileChooserActivity, "Please enter text", Toast.LENGTH_SHORT)
//                    .show()
//            }
            }
        }

        btnSelectFile.setOnClickListener {
            if (Build.VERSION.SDK_INT < 33) {
                val permissionCheck: Int = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                )
                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        REQUEST_PERMISSION_EXTERNAL
                    )
                } else {
                    showDialog()
                }
            } else {
                showDialog()
            }
        }
        btnCheck.setOnClickListener {
//            OnToCookApplication.instance.bleBoundService.writeFileData(
//                "OTA:true,SIZE:${file!!.length()}".toByteArray(
//                    Charsets.UTF_8
//                )
//            )
//            OnToCookApplication.instance.bleBoundService.sendBinFile(
//                file!!.readBytes()
//            )
//            OnToCookApplication.instance.bleBoundService.receiveLogData(
//                file!!.readBytes()
//            )
            if (btnCheck.text == "Check")
                checkVersion()
            else
                downLoadFile()
        }
    }

    private fun downLoadFile() {
//        if (firmware != null)
        val s3Client = Constants.provideAmazonS3Client(this@FileChooserActivity)
        val file = createBinFile()
        val name = firmware!!.url.substring(firmware!!.url.lastIndexOf('/') + 1)
        val observer = Constants.provideTransferUtility(
            this@FileChooserActivity,
            s3Client
        ).download(
            "ota-test2",
            name,
            file
        )
        LoadingUtils.showDialog(this@FileChooserActivity, false, "Downloading File..")

        observer.setTransferListener(object :
            com.amazonaws.mobileconnectors.s3.transferutility.TransferListener {

            override fun onError(id: Int, e: Exception) {
                Log.e("Download", "Error during upload: %s , $id")
                Log.e("Download", "Error during  , ${e.message}")
//                failure.invoke()
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDonef = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDonef.toInt()
                Log.e(
                    "Download",
                    "onProgressChanged: %d, total: %d, current: %d $id + $bytesTotal + $bytesCurrent"
                )
                Log.e("Download", "Percentage: %d  ${percentDone.toString()}")
//                progress.invoke(percentDone.toString())
            }

            override fun onStateChanged(id: Int, newState: TransferState) {
                Log.e("Download", "onStateChanged: %d, %s  $id + $newState")

                if (newState == TransferState.COMPLETED) {
                    LoadingUtils.hideDialog()
                    Log.e("Download", "onStateChanged:COMPLETED  ${file!!.length()}")
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        "OTA:true,SIZE:${file.length()}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    OnToCookApplication.instance.bleBoundService.sendBinFile(
                        file.readBytes()
                    )
                    LoadingUtils.showLoading(this@FileChooserActivity, false, "Firmware Updating..")
                    startProgress()
                } else if (newState == TransferState.FAILED) {
                    Log.e("Download", "onStateChanged:FAILED  ")
                }
            }
        })
    }

    private fun startProgress() {
        Log.e(TAG, "startProgress: " )
        OnToCookApplication.instance.bleBoundService.mutableLiveData.observe(
            this, androidx.lifecycle.Observer {
                Log.e(TAG, "mutableLiveData: sendBinFile $it")
                LoadingUtils.updateText(this,"$it %")
                if (it >= 100) {
                    LoadingUtils.hideDialog()
                }
            }
        )
    }

    private fun createBinFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "ONTOCOOK_" + /*timeStamp +*/ "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val image = File.createTempFile(
            imageFileName,
            ".bin",
            storageDir
        )
        Log.e(TAG, "createImageFile: $image")
        return image
    }

    private fun checkVersion() {
        val s3Client = Constants.provideAmazonS3Client(this@FileChooserActivity)
        val file = createTextFile()
        val observer = Constants.provideTransferUtility(
            this@FileChooserActivity,
            s3Client
        ).download(
            "ota-test2",
            "version.txt",
            file
        )
        observer.setTransferListener(object :
            com.amazonaws.mobileconnectors.s3.transferutility.TransferListener {

            override fun onError(id: Int, e: Exception) {
                Log.e("Download", "Error during upload: %s , $id")
                Log.e("Download", "Error during  , ${e.message}")
//                failure.invoke()
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDonef = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDonef.toInt()
                Log.e(
                    "Download",
                    "onProgressChanged: %d, total: %d, current: %d $id + $bytesTotal + $bytesCurrent"
                )
                Log.e("Download", "Percentage: %d  ${percentDone.toString()}")
//                progress.invoke(percentDone.toString())
            }

            override fun onStateChanged(id: Int, newState: TransferState) {
                Log.e("Download", "onStateChanged: %d, %s  $id + $newState")

                if (newState == TransferState.COMPLETED) {
                    val text = StringBuilder()

                    try {
                        val br = BufferedReader(FileReader(file))
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            text.append(line)
                            text.append('\n')
                        }
                        br.close()
                        Log.e(TAG, "onStateChanged: $text")
                        firmware = Gson().fromJson(text.toString(), Firmware::class.java)
                        if (firmware != null)
                            Constants.firmwareVersion = firmware!!.num
                        if (OnToCookApplication.instance.isDeviceConnected()) {
                            OnToCookApplication.instance.bleBoundService.writeData(
                                "Firmware=?".toByteArray(
                                    Charsets.UTF_8
                                )
                            )
                        }
                    } catch (e: IOException) {
                        //You'll need to add proper error handling here
                    }
                    Log.e("Download", "onStateChanged:COMPLETED  ")
                } else if (newState == TransferState.FAILED) {
                    Log.e("Download", "onStateChanged:FAILED  ")
                }
            }
        })
    }

    private fun createTextFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "ONTOCOOK_" + /*timeStamp +*/ "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val image = File.createTempFile(
            imageFileName,
            ".txt",
            storageDir
        )
        Log.e(TAG, "createImageFile: $image")
        return image
    }


//    suspend fun getObjectBytes(bucketName: String, keyName: String, path: String) {
//
//        val request = GetObjectRequest {
//            key = keyName
//            bucket = bucketName
//        }
//
//        S3Client {
//            region = "ap-south-1"
//            credentialsProvider = EnvironmentCredentialsProvider()
//        }.use { s3 ->
//            s3.getObject(request) { resp ->
//                val myFile = File(path)
//                resp.body?.writeToFile(myFile)
//                println("Successfully read $keyName from $bucketName")
//            }
//        }
//    }
//    fun initAmazonS3Client(
//        endpoint: String,
//        accessKey: String,
//        secretKey: String
//    ) = aws.sdk.kotlin.services.s3.S3Client(
//        BasicAWSCredentials(accessKey, secretKey)
//    ).apply {
//            setEndpoint(endpoint).apply {
//                println("S3 endpoint is ${endpoint}")
//            }
//            setS3ClientOptions(
//                S3ClientOptions.builder()
//                    .setPathStyleAccess(true).build()
//            )
//        }


    fun generateAudio(bytes: ByteArray) {
        val wallpaperDirectory = File("$externalCacheDir /sdcard/Wallpaper/")
// have the object build the directory structure, if needed.
// have the object build the directory structure, if needed.
        wallpaperDirectory.mkdirs()
        val mainPicture = File(wallpaperDirectory, "${file!!.name}")
        try {
            val fos = FileOutputStream(mainPicture)
            fos.write(bytes)
            fos.close()
            Log.e(TAG, "generateAudio: ${mainPicture.length()}")
            Log.e(TAG, "generateAudio: ${mainPicture.exists()}")
            Log.e(TAG, "generateAudio: ${mainPicture.name}")
            Log.e(TAG, "generateAudio: ${mainPicture.nameWithoutExtension}")
            Log.e(TAG, "generateAudio: ${mainPicture.path}")
            Log.e(TAG, "generateAudio:ab ${mainPicture.absolutePath}")

//            val fis: FileInputStream = FileInputStream(filePath);
//            ByteArrayOutputStream bos = new ByteArrayOutputStream();
//            byte[] b = new byte[1024];
//            for (int readNum; (readNum = fis.read(b)) != -1; ) {
//                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream (yourFile));
//                bos.write(fileBytes);
//                bos.flush();
//                bos.close();
//            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendNextData() {
        when (acknowledgementIndex) {
            mapDataToWrite.size + 1 -> {
                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
            mapDataToWrite.size + 2 -> {
                Toast.makeText(
                    this@FileChooserActivity,
                    "File uploaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
                LoadingUtils.hideDialog()
                Log.e("TAG", "onReceive: File Sent Successfully")
            }
            else -> {
                val pnoNumber = acknowledgementIndex
                if (mapDataToWrite.contains(pnoNumber)) {
                    val data = mapDataToWrite[pnoNumber]
                    Log.e("TAG", "onReceive: File Sent $data")
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        "PNO=${pnoNumber},DATA=${data}".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            }
        }
    }

    private fun sendAudioData() {
        Log.e(TAG, "sendAudioData: $acknowledgementIndex")

        when (acknowledgementIndex) {

            mapDataToWriteAudio.size + 1 -> {
                OnToCookApplication.instance.bleBoundService.writeFileData(
                    "COMPLETE".toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
            mapDataToWriteAudio.size + 2 -> {
                Toast.makeText(
                    this@FileChooserActivity,
                    "File uploaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
                LoadingUtils.hideDialog()
            }
            else -> {
                val pnoNumber = acknowledgementIndex
                Log.e(TAG, "sendAudioData: $pnoNumber")
                if (mapDataToWriteAudio.contains(pnoNumber)) {
                    val data = mapDataToWriteAudio[pnoNumber]
                    val firstBytes: ByteArray = "PNO=${pnoNumber},DATA=".toByteArray(
                        Charsets.UTF_8
                    )
                    val dataToWrite = ByteArray(firstBytes.size + data!!.size)
                    System.arraycopy(firstBytes, 0, dataToWrite, 0, firstBytes.size)
                    System.arraycopy(data, 0, dataToWrite, firstBytes.size, data.size)
                    Log.e(TAG, "sendAudioData: Data ${data.size}")
                    Log.e(TAG, "sendAudioData:Total ${dataToWrite.size}")

//                    val outputStream = ByteArrayOutputStream()
//                    outputStream.write(a)
//                    outputStream.write(data)
//                    val dataToWrite = outputStream.toByteArray()
                    OnToCookApplication.instance.bleBoundService.writeFileData(
                        dataToWrite
                    )
                }
            }
        }
    }

    private fun loadJSONFromFile(): String? {
        var json: String? = null
        json = try {
            val `is`: InputStream = file!!.inputStream()
            val size: Int = `is`.available()
            val buffer = ByteArray(size)
            `is`.read(buffer)
            `is`.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return json
    }

    private fun getConvertedFile(
        originalFile: File,
        format: AudioFormat
    ): File? {
        val f =
            originalFile.path.split("\\.".toRegex()).toTypedArray()
        val filePath =
            originalFile.path.replace(f[f.size - 1], format.format)
        return File(filePath)
    }

    fun convertWavToMp3() {
//        val convertedFile = getConvertedFile(file!!, AudioFormat.MP3)
//        val cmd =
//            arrayOf("-y", "-i", file?.path, "-codec:a", "libmp3lame", convertedFile?.path)
//        println("Execute command    ${cmd.joinToString(" ")}")
//        FFmpeg.executeAsync(cmd.joinToString(" ")
//         ) { _, returnCodse ->
//             if(returnCode == Config.RETURN_CODE_SUCCESS){
//                 println("success")
//             }else if(returnCode == Config.RETURN_CODE_CANCEL){
//                 println("cancel")
//             }
//             println(" return code    ${returnCode} ")
//             Config.printLastCommandOutput(Log.INFO);
//         }
    }

    fun generateSoundFile() {
        //
        //var path = Constants.getExternalStorageDirectory()?.path + "/Download/"
        file = File(getExternalFilesDir(null), "sound.wav")
        println("file path      ${file?.path}")
        tts?.synthesizeToFile(etSoundText.text.toString(), null, file, "123433")
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

    private fun sendAudioFile(bytes: ByteArray) {
        LoadingUtils.showDialog(this@FileChooserActivity, true, "Uploading File..")
        val totalSizeToSend = bytes.size
        var startByteIndex = 0
        var endingByteIndex = 450
        if (totalSizeToSend > 450) {
            val sendingSizePerPacket = 450
            var packets = totalSizeToSend / sendingSizePerPacket
            val lastByteSizeToSend = totalSizeToSend % sendingSizePerPacket
            if (lastByteSizeToSend != 0) {
                packets++
            }
            for (i in 0 until packets) {
                val pnoNumber = i + 1
                if (i == packets - 1 && lastByteSizeToSend != 0) {
                    endingByteIndex -= sendingSizePerPacket
                    endingByteIndex += lastByteSizeToSend
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    mapDataToWriteAudio[pnoNumber] = dataToWrite
                } else {
                    val dataToWrite = bytes.copyOfRange(startByteIndex, endingByteIndex)
                    startByteIndex += sendingSizePerPacket
                    endingByteIndex += sendingSizePerPacket
                    mapDataToWriteAudio[pnoNumber] = dataToWrite
                }
            }
            Log.e(TAG, "sendFileDataToDevice: ${mapDataToWriteAudio.size}")
            mapDataToWriteAudio.forEach { (i, s) ->
                Log.e(TAG, "Key $i Value $s")
            }
        }
    }

    fun sendFileDataToDevice(fileToUpload: File) {
        LoadingUtils.showDialog(this@FileChooserActivity, true, "Uploading File..")
        val str: String = getTextFromFile(file!!)
        val size = str.toByteArray(
            Charsets.UTF_8
        ).size
        var start = 0
        var end = 500
        if (size > 500) {
            val data = str.toByteArray(Charsets.UTF_8)
            var sendingSize = 500
            var divide = size / sendingSize
            val divider = size % sendingSize
            if (divider != 0) {
                divide++
            }
            for (i in 0 until divide) {
                val pnoNumber = i + 1
                if (i == divide - 1 && divider != 0) {
                    end -= sendingSize
                    end += divider
                    Log.e(TAG, "sendFileDataToDevice: $start")
                    Log.e(TAG, "sendFileDataToDevice: ${end - 1}")
                    val dataToWrite = data.copyOfRange(start, end)
                    val s = String(dataToWrite, Charsets.UTF_8)
                    mapDataToWrite[pnoNumber] = s
                } else {
                    val dataToWrite = data.copyOfRange(start, end)
                    start += sendingSize
                    end += sendingSize
                    val s = String(dataToWrite, Charsets.UTF_8)
                    mapDataToWrite[pnoNumber] = s
                }
            }
            Log.e(TAG, "sendFileDataToDevice: HashMap")
            mapDataToWrite.forEach { (i, s) ->
                Log.e(TAG, "Key $i Value $s")
            }
        }


//        var jsonFile = loadJSONFromFile()
//        if(jsonFile != null){
//            var recipeId = ""
//            var recipeName = ""
//
//            try{
//                recipeName = JSONObject(jsonFile).getString("name");
//                recipeId = JSONObject(jsonFile).getString("id");
////                var filteredRecipe = RECIPES.filter { it.name == name }
////                if(filteredRecipe.isNotEmpty()){
////                    recipeId = filteredRecipe.first().id.toString()
////                }
//            }catch (e : Exception){
//            }
//
//            //"id:$recipeId,filename:${fileToUpload.name},fileData:${jsonFile}EOF"
//            println("send data.....  start..")
//            OnToCookApplication.instance.bleBoundService.writeFileData(
//                "json:<recipe_${recipeName}_${recipeId}>${jsonFile}EOF##".toByteArray(
//                    Charsets.UTF_8
//                )
//            )
//        }
    }

    private fun sendFileDataToDevice(str: String, size: Int) {
        LoadingUtils.showDialog(this@FileChooserActivity, true, "Uploading File..")


        Log.e("TAG", "init:Size $size")
        Log.e("TAG", "init:file name ${file!!.name}")
        var start = 0
        var end = 500
        Log.e("TAG", "init:Remain ${size % 500}")
        if (size > 500) {
            val data = str.toByteArray(Charsets.UTF_8)
            var sendingSize = 500
            var divide = size / sendingSize
            val divider = size % sendingSize
            if (divider != 0) {
                divide++
            }
            for (i in 0 until divide) {
                val pnoNumber = i + 1
                if (i == divide - 1 && divider != 0) {
                    end -= sendingSize
                    end += divider
                    val dataToWrite = data.copyOfRange(start, end - 1)
                    val s = String(dataToWrite, Charsets.UTF_8)
                    mapDataToWrite[pnoNumber] = s
//                    OnToCookApplication.instance.bleBoundService.writeFileData(
//                        "PNO=${pnoNumber},DATA=${s}".toByteArray(
//                            Charsets.UTF_8
//                        )
//                    )
                } else {
                    val dataToWrite = data.copyOfRange(start, end - 1)
                    start += sendingSize
                    end += sendingSize
                    val s = String(dataToWrite, Charsets.UTF_8)
                    mapDataToWrite[pnoNumber] = s
//                    OnToCookApplication.instance.bleBoundService.writeFileData(
//                        "PNO=${pnoNumber},DATA=${s}".toByteArray(
//                            Charsets.UTF_8
//                        )
//                    )
                }
            }
            Log.e(TAG, "sendFileDataToDevice: HashMap")
            mapDataToWrite.forEach { i, s ->
                Log.e(TAG, "Key $i Value $s")
            }
        }


//        var jsonFile = loadJSONFromFile()
//        if(jsonFile != null){
//            var recipeId = ""
//            var recipeName = ""
//
//            try{
//                recipeName = JSONObject(jsonFile).getString("name");
//                recipeId = JSONObject(jsonFile).getString("id");
////                var filteredRecipe = RECIPES.filter { it.name == name }
////                if(filteredRecipe.isNotEmpty()){
////                    recipeId = filteredRecipe.first().id.toString()
////                }
//            }catch (e : Exception){
//            }
//
//            //"id:$recipeId,filename:${fileToUpload.name},fileData:${jsonFile}EOF"
//            println("send data.....  start..")
//            OnToCookApplication.instance.bleBoundService.writeFileData(
//                "json:<recipe_${recipeName}_${recipeId}>${jsonFile}EOF##".toByteArray(
//                    Charsets.UTF_8
//                )
//            )
//        }
    }

    fun sendSoundFileDataToDevice(fileToUpload: File) {
        LoadingUtils.showDialog(this@FileChooserActivity, true, "Uploading File..")
        OnToCookApplication.instance.bleBoundService.writeFileData(
            fileToUpload.readBytes()
        )
    }

    @SuppressLint("CheckResult")
    private fun prepareScanObserver() {
        if (rxBleClient.isScanRuntimePermissionGranted) {
            OnToCookApplication.instance.isBluetoothConnected()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    OnToCookApplication.instance.startBoundService(true)
                }
        } else {
            requestBluetoothPermission(rxBleClient)
        }
    }

    private fun showDialog() {
        dialog?.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION_EXTERNAL) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                showDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(Constants.EVENT_BLE_CONNECTION)
            )
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                communicationReceiver,
                IntentFilter(Constants.EVENT_BLE_COMMUNICATION)
            )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(communicationReceiver)
        if (OnToCookApplication.instance.isDeviceConnected()) {        OnToCookApplication.instance.bleBoundService.mutableLiveData.removeObservers(this)}
    }
}