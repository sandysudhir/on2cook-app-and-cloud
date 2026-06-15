package com.invent.ontocook.utils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.AssetManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.OnScanCompletedListener
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.invent.ontocook.R
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.yalantis.ucrop.util.FileUtils.getDataColumn
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


internal fun getTextFromFile(file: File): String {
    var json: String? = null
    val `is`: InputStream = file.inputStream()
    val size: Int = `is`.available()
    val buffer = ByteArray(size)
    `is`.read(buffer)
    `is`.close()
    json = String(buffer, Charset.forName("UTF-8"))
    return json
}

fun Context.shareApp(recipe: Recipe, gpxfile: File, imgWriter: File) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name))
        var shareMessage = "\nLet me recommend you this Recipe\n\n"
        shareIntent.putExtra(Intent.EXTRA_TEXT, Gson().toJson(recipe))
        val pdfUri = FileProvider.getUriForFile(this, this.packageName + ".provider", gpxfile)
        val imgUri = FileProvider.getUriForFile(this, this.packageName + ".provider", imgWriter)
        val list = arrayListOf(pdfUri, imgUri)
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list);
        startActivity(Intent.createChooser(shareIntent, "choose one"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

const val BUFFER_SIZE = 4096
fun Context.getZipFileFromFiles(fileList: ArrayList<File>, recipe: Recipe): File {
    var origin: BufferedInputStream? = null
    val root = File(externalCacheDir, "Temp")
    if (root.exists()) {
        root.deleteOnExit()
    }
    root.mkdirs()
    var zipFile = File(root, "${recipe.name[0]}.zip")
    if (!zipFile.exists()) {
        zipFile.createNewFile()
        zipFile.setWritable(true)
    }
    val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
    try {
        val data = ByteArray(BUFFER_SIZE)
        for (i in fileList.indices) {
            val fi = FileInputStream(fileList[i])
            origin = BufferedInputStream(fi, BUFFER_SIZE)
            try {
                val entry =
                    ZipEntry(fileList[i].name.substring(fileList[i].name.lastIndexOf("/") + 1))
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
        return zipFile
    }
}

fun getPathFromUri(context: Context, uri: Uri): String? {
    val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            }

            // TODO handle non-primary volumes
        } else if (isDownloadsDocument(uri)) {
            val id = DocumentsContract.getDocumentId(uri)
            val contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
            )
            return getDataColumn(context, contentUri, null, null)
        } else if (isMediaDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split[0]
            var contentUri: Uri? = null
            if ("image" == type) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else if ("video" == type) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if ("audio" == type) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val selection = "_id=?"
            val selectionArgs = arrayOf(
                split[1]
            )
            return getDataColumn(context, contentUri, selection, selectionArgs)
        }
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {

        // Return the remote address
        return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(
            context,
            uri,
            null,
            null
        )
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }
    return null
}

fun moveFile(src: String, dest: String, fileName: String) {
    var result: Path? = null
    try {
        result = Files.move(
            Paths.get(src),
            Paths.get(dest).resolve(fileName),
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (e: IOException) {
        println("Exception while moving file: " + e.message)
    }
    if (result != null) {
        println("File moved successfully.")
    } else {
        println("File movement failed.")
    }
}

fun getDataColumn(
    context: Context, uri: Uri?, selection: String?,
    selectionArgs: Array<String>?
): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(
        column
    )
    try {
        cursor = uri?.let {
            context.getContentResolver().query(
                it, projection, selection, selectionArgs,
                null
            )
        }
        if (cursor != null && cursor.moveToFirst()) {
            val index: Int = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(index)
        }
    } finally {
        if (cursor != null) cursor.close()
    }
    return null
}


/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is ExternalStorageProvider.
 */
fun isExternalStorageDocument(uri: Uri): Boolean {
    return "com.android.externalstorage.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is DownloadsProvider.
 */
fun isDownloadsDocument(uri: Uri): Boolean {
    return "com.android.providers.downloads.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is MediaProvider.
 */
fun isMediaDocument(uri: Uri): Boolean {
    return "com.android.providers.media.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is Google Photos.
 */
fun isGooglePhotosUri(uri: Uri): Boolean {
    return "com.google.android.apps.photos.content" == uri.authority
}

//Copy File In Cache Memory
fun makeFileCopyInCacheDir(context: Context, contentUri: Uri): String {
    try {
        val filePathColumn = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val returnCursor =
            contentUri.let { context.contentResolver.query(it, filePathColumn, null, null, null) }
        if (returnCursor != null) {
            returnCursor.moveToFirst()
            val nameIndex = returnCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            val name = returnCursor.getString(nameIndex)
            val file = File(context.cacheDir, name)
            val inputStream = context.contentResolver.openInputStream(contentUri)
            val outputStream = FileOutputStream(file)
            var read = 0
            val maxBufferSize = 1 * 1024 * 1024
            val bytesAvailable = inputStream!!.available()

            //int bufferSize = 1024;
            val bufferSize = bytesAvailable.coerceAtMost(maxBufferSize)
            val buffers = ByteArray(bufferSize)
            while (inputStream.read(buffers).also { read = it } != -1) {
                outputStream.write(buffers, 0, read)
            }
            inputStream.close()
            outputStream.close()
            return file.absolutePath
        }
    } catch (ex: Exception) {
        Log.e("Exception", ex.message!!)
    }
    return contentUri.let { getPathFromUri(context, it).toString() }
}

fun createAudioFile(
    context: Context,
    contentUri: String,
    ingredients: Constants.AUDIO_TYPE
): File? {
    try {
        val root = File(
            context.filesDir, when (ingredients) {
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
        if (!root.exists()) {
            root.mkdirs()
        }
        return File(root, contentUri)
    } catch (ex: Exception) {
        return null
    }
}

fun Context.shareZipFile(files: ArrayList<File>, name: String) {
    var origin: BufferedInputStream? = null
    val root = File(externalCacheDir, "Temp")
    if (root.exists()) {
        root.deleteOnExit()
    }
    root.mkdirs()
    var zipFile = File(root, "$name.zip")
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
        val zipUri =
            FileProvider.getUriForFile(this, this.packageName + ".provider", zipFile)
        shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
        val chooser = Intent.createChooser(shareIntent, "Share File")
        val resInfoList: List<ResolveInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                this.packageManager.queryIntentActivities(
                    chooser,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            else
                this.packageManager.queryIntentActivities(
                    chooser,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
        for (resolveInfo in resInfoList) {
            val packageName: String = resolveInfo.activityInfo.packageName
            grantUriPermission(
                packageName,
                zipUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        startActivity(chooser)
    }
}

fun getFileSizeFromAssets(assetManager: AssetManager, fileName: String?): Long {
    return try {
        // Open an InputStream to the file in the assets folder
        val inputStream = assetManager.open(fileName!!)

        // Get the file size
        val fileSize = inputStream.available().toLong()

        // Close the InputStream
        inputStream.close()
        fileSize
    } catch (e: IOException) {
        // Handle any exceptions that may occur, such as if the file doesn't exist
        e.printStackTrace()
        -1 // Return -1 to indicate an error
    }
}

//fun setID3Tags(
//    filePath: String,
//    title: String?,
//    artist: String?,
//    album: String?,
//    context: Context
//) {
//    try {
//        val mp3file = Mp3File(filePath)
//        DebugLog.e("Converting If Mp3File ${mp3file.hasId3v2Tag()}")
//
//        // Create or get ID3v2 tag
//        val id3v2Tag: ID3v2
//        if (mp3file.hasId3v2Tag()) {
//            DebugLog.e("Converting If")
//            id3v2Tag = mp3file.id3v2Tag
//        } else {
//            DebugLog.e("Converting else")
//            id3v2Tag = object : ID3v2 {
//                override fun getVersion(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getTrack(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setTrack(track: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getArtist(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setArtist(artist: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getTitle(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setTitle(title: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAlbum(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAlbum(album: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getYear(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setYear(year: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getGenre(): Int {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setGenre(genre: Int) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getGenreDescription(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getComment(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setComment(comment: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun toBytes(): ByteArray {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getPadding(): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setPadding(padding: Boolean) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun hasFooter(): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setFooter(footer: Boolean) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun hasUnsynchronisation(): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setUnsynchronisation(unsynchronisation: Boolean) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getBPM(): Int {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setBPM(bpm: Int) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getGrouping(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setGrouping(grouping: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getKey(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setKey(key: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getDate(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setDate(date: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getComposer(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setComposer(composer: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getPublisher(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setPublisher(publisher: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getOriginalArtist(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setOriginalArtist(originalArtist: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAlbumArtist(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAlbumArtist(albumArtist: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getCopyright(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setCopyright(copyright: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getArtistUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setArtistUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getCommercialUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setCommercialUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getCopyrightUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setCopyrightUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAudiofileUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAudiofileUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAudioSourceUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAudioSourceUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getRadiostationUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setRadiostationUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getPaymentUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setPaymentUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getPublisherUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setPublisherUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getUrl(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setUrl(url: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getPartOfSet(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setPartOfSet(partOfSet: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun isCompilation(): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setCompilation(compilation: Boolean) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getChapters(): java.util.ArrayList<ID3v2ChapterFrameData> {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setChapters(chapters: java.util.ArrayList<ID3v2ChapterFrameData>?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getChapterTOC(): java.util.ArrayList<ID3v2ChapterTOCFrameData> {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setChapterTOC(ctoc: java.util.ArrayList<ID3v2ChapterTOCFrameData>?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getEncoder(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setEncoder(encoder: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAlbumImage(): ByteArray {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAlbumImage(albumImage: ByteArray?, mimeType: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setAlbumImage(
//                    albumImage: ByteArray?,
//                    mimeType: String?,
//                    imageType: Byte,
//                    imageDescription: String?
//                ) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun clearAlbumImage() {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getAlbumImageMimeType(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getWmpRating(): Int {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setWmpRating(rating: Int) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getItunesComment(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setItunesComment(itunesComment: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getLyrics(): String {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setLyrics(lyrics: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun setGenreDescription(text: String?) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getDataLength(): Int {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getLength(): Int {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getObseleteFormat(): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun getFrameSets(): MutableMap<String, ID3v2FrameSet> {
//                    TODO("Not yet implemented")
//                }
//
//                override fun clearFrameSet(id: String?) {
//                    TODO("Not yet implemented")
//                }
//            }
//            mp3file.id3v2Tag = id3v2Tag
//        }
//
//        // Set the ID3 tag fields
//        id3v2Tag.title = title
//        id3v2Tag.artist = artist
//        id3v2Tag.album = album
//
//        // Save the changes
//        // Refresh media library to update changes
//        MediaScannerConnection.scanFile(
//            context, arrayOf(filePath),
//            null
//        ) { p0, p1 -> DebugLog.e("MediaScannerConnection P0 $p0 P1 $p1") }
//        mp3file.save("${context.filesDir}abc.mp3")
//    } catch (e: java.lang.Exception) {
//        DebugLog.e("error Converting  $e")
//        e.printStackTrace()
//    }
//}

//fun addID3TagToMP3(filePath: String, artist: String, title: String, album: String) {
//    try {
//        val mp3File = Mp3File(filePath)
//
//        if (mp3File.hasId3v2Tag()) {
//            val id3v2Tag = mp3File.id3v2Tag
//            id3v2Tag.artist = artist
//            id3v2Tag.title = title
//            id3v2Tag.album = album
//            // You can set more ID3 tags here as needed
//        } else {
////            val id3v2Tag = ID3v2()
////            id3v2Tag.artist = artist
////            id3v2Tag.title = title
////            id3v2Tag.album = album
//            // You can set more ID3 tags here as needed
//
////            mp3File.setId3v2Tag(id3v2Tag)
//        }
//
////        val tempFile = File(filePath + "_new.mp3")
////        val fos = FileOutputStream(tempFile)
////        mp3File.save(fos)
////        fos.close()
////
//        // Replace the original file with the modified one
////        File(filePath).delete()
////        tempFile.renameTo(File(filePath))
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//}

fun Context.shareCSvFile(files: File) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/comma-separated-values"
    val chooser = Intent.createChooser(shareIntent, "Share File")
    val zipUri =
        FileProvider.getUriForFile(this, this.packageName + ".provider", files)
    shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
    val resInfoList: List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            this.packageManager.queryIntentActivities(
                chooser,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        else
            this.packageManager.queryIntentActivities(
                chooser,
                PackageManager.MATCH_DEFAULT_ONLY
            )
    for (resolveInfo in resInfoList) {
        val packageName: String = resolveInfo.activityInfo.packageName
        grantUriPermission(
            packageName,
            zipUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    startActivity(chooser)

}

fun Context.createTempTextFile(name: String, ext: String): File {
    val root = File(externalCacheDir, "Temp")
    if (!root.exists()) {
        root.mkdirs()
    }
    return File.createTempFile(
        name,
        ext,
        root
    )
}


fun Context.createTempFile(root: File, name: String, ext: String): File {
    return File.createTempFile(
        name,
        ext,
        root
    )
}
