package com.invent.ontocook.multiple_connection.ftp

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketException


class MyFTPClientFunctions(private val callbackClose: () -> Unit) {
    private lateinit var mFTPClient: FTPClient
    fun ftpConnect(host: String, username: String?, password: String?, port: Int): Boolean {
        try {
            mFTPClient = FTPClient()
            // connecting to the host
            mFTPClient.connect(host, port)
            DebugLog.e("Testing 123 ${mFTPClient.connectTimeout}")

            // now check the reply code, if positive mean connection success
            if (FTPReply.isPositiveCompletion(mFTPClient.replyCode)) {
                val status: Boolean = mFTPClient.login(username, password)
                // login using username & password

                /*
               * Set File Transfer Mode
               * To avoid corruption issue you must specified a correct
               * transfer mode, such as ASCII_FILE_TYPE, BINARY_FILE_TYPE,
               * EBCDIC_FILE_TYPE .etc. Here, I use BINARY_FILE_TYPE for
               * transferring text, image, and compressed files.
                */
                mFTPClient.setFileType(FTP.BINARY_FILE_TYPE)
                mFTPClient.enterLocalPassiveMode()
                return status
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: could not connect to host $host")
            Log.d(TAG, "Error: could not connect to host ${e.printStackTrace()}")
            Log.d(TAG, "Error: could not connect to host ${e.message}")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException ftpConnect $host")
            Log.d(TAG, "SocketException  ftpConnect ${e.printStackTrace()}")
            Log.d(TAG, "SocketException ${e.message}")
            callbackClose.invoke()
        }
        return false
    }

    fun ftpGetCurrentWorkingDirectory(): String? {
        try {
            return mFTPClient.printWorkingDirectory()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Error: could not get current working directory.")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException: ftpGetCurrentWorkingDirectory ${e.printStackTrace()}")
            callbackClose.invoke()
        }
        return null
    }

    fun isConnected(): Boolean {
        if (!this::mFTPClient.isInitialized) return false
        return mFTPClient.isConnected
    }

    fun getTimeout(): Int {
        DebugLog.e("Testing 123 ..1:  ${mFTPClient.connectTimeout}")
        DebugLog.e("Testing 123 ..2: ${mFTPClient.defaultTimeout}")
        DebugLog.e("Testing 123 ..3: ${mFTPClient.controlKeepAliveTimeout}")
        return mFTPClient.connectTimeout
    }

    fun listenEvent() {
        if (isConnected()) {
            mFTPClient
        }
    }
    // Method to change working directory:

    // Method to change working directory:
    fun ftpChangeDirectory(directory_path: String): Boolean {
        try {
            return mFTPClient.changeWorkingDirectory(directory_path)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Error: could not change directory to $directory_path")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException: ftpChangeDirectory ${e.printStackTrace()}")
            callbackClose.invoke()
        }
        return false
    }

    fun ftpParentDirectory(): Boolean {
        try {
            return mFTPClient.changeToParentDirectory()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Error: could not change directory to ${e.printStackTrace()}")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException: ftpParentDirectory ${e.printStackTrace()}")
            callbackClose.invoke()
        }
        return false
    }

    var returnCode = 0

    @Throws(IOException::class)
    fun checkFileExists(filePath: String?): Boolean {
        try {
            val inputStream: InputStream? = mFTPClient.retrieveFileStream(filePath)
//        val root = File(OnToCookApplication.instance.externalCacheDir, "Files")
//        if (!root.exists())
//        {
//            root.mkdirs()
//        }
//        DebugLog.e("Checking ${root.exists()}")
//        val zipFile = File(root, "ChilliPaneerSamosa.mp3")
//        if (!zipFile.exists()){
//            zipFile.createNewFile()
//            zipFile.setWritable(true)
//        }
//        val outputStream = FileOutputStream(zipFile)
//        var read = 0
//        val maxBufferSize = 1 * 1024 * 1024
//        val bytesAvailable = inputStream!!.available()
//        //int bufferSize = 1024;
//        val bufferSize = bytesAvailable.coerceAtMost(maxBufferSize)
//        val buffers = ByteArray(bufferSize)
//        while (inputStream.read(buffers).also { read = it } != -1) {
//            outputStream.write(buffers, 0, read)
//        }
//        inputStream.close()
//        outputStream.close()
            returnCode = mFTPClient.replyCode
            return !(inputStream == null || returnCode === 550)
        } catch (e: SocketException) {
            return false
        } catch (e: IOException) {
            return false
        }
    }

    // Method to list all files in a directory:

    // Method to list all files in a directory:
    fun ftpPrintFilesList(dir_path: String?): Array<String?>? {
        var fileList: Array<String?>? = null
        return try {
            val ftpFiles = mFTPClient.listFiles(dir_path)
            val length = ftpFiles.size
            fileList = arrayOfNulls(length)
            for (i in 0 until length) {
                val name = ftpFiles[i].name
                val isFile = ftpFiles[i].isFile
                if (isFile) {
                    fileList[i] = "File :: $name"
                    Log.i(TAG, "File : $name")
                } else {
                    fileList[i] = "Directory :: $name"
                    Log.i(TAG, "Directory : $name")
                }
            }
            fileList
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            fileList
        }
    }

    fun ftpPrintAllFilesList(): Array<String?>? {
        DebugLog.e("ftpPrintAllFilesList")

        var fileList: Array<String?>? = null
        return try {
            val ftpFiles = mFTPClient.listFiles()
            val length = ftpFiles.size
            fileList = arrayOfNulls(length)
            for (i in 0 until length) {
                val name = ftpFiles[i].name
                val isFile = ftpFiles[i].isFile
                if (isFile) {
//                    fileList[i] = "File :: $name"
                    fileList[i] = name
                    Log.i(TAG, "File : $name")
                } else {
                    fileList[i] = "Directory :: $name"
                    Log.i(TAG, "Directory : $name")
                }
            }
            fileList
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            fileList
        }
    }

    // Method to create new directory:

    // Method to create new directory:
    fun ftpMakeDirectory(new_dir_path: String): Boolean {
        try {
            return mFTPClient.makeDirectory(new_dir_path)
        } catch (e: java.lang.Exception) {
            Log.d(
                TAG, "Error: could not create new directory named " + new_dir_path
            )
        }
        return false
    }

    // Method to delete/remove a directory:

    // Method to delete/remove a directory:
    fun ftpRemoveDirectory(dir_path: String): Boolean {
        try {
            return mFTPClient.removeDirectory(dir_path)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Error: could not remove directory named $dir_path")
        }
        return false
    }

    // Method to delete a file:

    // Method to delete a file:
    fun ftpRemoveFile(filePath: String?): Boolean {
        try {
            return mFTPClient.deleteFile(filePath)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } catch (e: SocketException) {

            Log.d(TAG, "SocketException Testing 123 ftpRemoveFile: $e")
            callbackClose.invoke()
        } catch (e: IOException) {
            Log.d(TAG, "SocketException Testing 123 IOException ftpRemoveFile: $e")
            callbackClose.invoke()
        }
        return false
    }

    // Method to rename a file:

    // Method to rename a file:
    fun ftpRenameFile(from: String, to: String): Boolean {
        try {
            return mFTPClient.rename(from, to)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Could not rename file: $from to: $to")
        }
        return false
    }

    // Method to download a file from FTP server:

    // Method to download a file from FTP server:
    /**
     * mFTPClient: FTP client connection object (see FTP connection example)
     * srcFilePath: path to the source file in FTP server desFilePath: path to
     * the destination file to be saved in sdcard
     */
    fun ftpDownload(srcFilePath: String?, desFilePath: String?): Boolean {
        var status = false
        try {
            val desFileStream = FileOutputStream(desFilePath)
            status = mFTPClient.retrieveFile(srcFilePath, desFileStream)
            desFileStream.close()
            return status
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "download failed")
        }
        return status
    }

    // Method to upload a file to FTP server:

    // Method to upload a file to FTP server:
    /**
     * mFTPClient: FTP client connection object (see FTP connection example)
     * srcFilePath: source file path in sdcard desFileName: file name to be
     * stored in FTP server desDirectory: directory path where the file should
     * be upload to
     */
    fun ftpUpload(
        srcFilePath: File, desFileName: String?, desDirectory: String?, context: Context?
    ): Boolean {
        var status = false
        try {
            val srcFileStream = FileInputStream(srcFilePath)
            // change working directory to the destination directory
            // if (ftpChangeDirectory(desDirectory)) {
            Log.d(TAG, "upload :desFileName $desFileName")
            Log.d(TAG, "upload :filetype $srcFilePath")
            status = mFTPClient.storeFile(desFileName, srcFileStream)
            // }
            srcFileStream.close()
            return status
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Log.d(TAG, "upload failed: $e")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException ftpUpload: $e")
            callbackClose.invoke()
        }
        return status
    }

    fun ftpUploadFile(
        desFileName: String?, fileType: String?, context: Context
    ): Boolean {
        var status = false
        try {
            Log.d(TAG, "upload :desFileName $desFileName filetype $fileType")
            Log.d(TAG, "upload :filetype $fileType")
            val inputStream = context.assets.open("$fileType")
            Log.d(TAG, "upload :filetype ${inputStream.available()}")

            status = mFTPClient.storeFile(desFileName, inputStream)
            // }
            inputStream.close()
            Log.d(TAG, "upload status: $status")
            return status
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Log.d(TAG, "upload failed: $e")
        } catch (e: SocketException) {
            Log.d(TAG, "SocketException ftpUploadFile: $e")
            callbackClose.invoke()
        }
        return status
    }

    fun ftpDisconnect() {
        if (this::mFTPClient.isInitialized) {
            mFTPClient.disconnect()
        }
        mFTPClient
    }

    private fun getAudioFile(context: Context, desFileName: String?): InputStream {
        val inputStream = context.assets.open("audio/${desFileName}")
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String = ""
        if (reader.readLine().also { if (it != null) line = it } != null) {
            println(desFileName)
            reader.close()
        }
        return inputStream
    }
}