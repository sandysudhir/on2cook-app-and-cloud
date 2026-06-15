package com.invent.ontocook.utils

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.invent.ontocook.R


class UploadToS3Bucket {

    private var observerThumb: TransferObserver? = null
    private var observer: TransferObserver? = null

    fun uploadImage(
        transferUtility: TransferUtility,
        context: Context,
        imagePath: String,
        S3imagePath: String,
        filename:String,
        success: () -> Unit,
        failure: () -> Unit,
        progress: (String) -> Unit
    ) {
//        val extension = ".jpg"

//        val image = com.grs.structure.utils.FileUtils.getFile(context,imagePath.toUri())

//        observer = transferUtility.upload(
//           "bucket name",
//            S3imagePath + filename,
//            image,
//            CannedAccessControlList.PublicRead
//        )

        observer?.setTransferListener(object : TransferListener {

            override fun onError(id: Int, e: Exception) {
                Log.e("","Error during upload: %s , $id")
                failure.invoke()
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDonef = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDonef.toInt()
                Log.e("","onProgressChanged: %d, total: %d, current: %d $id + $bytesTotal + $bytesCurrent")
                Log.e("","Percentage: %d  ${percentDone.toString()}")
                progress.invoke(percentDone.toString())
            }

            override fun onStateChanged(id: Int, newState: TransferState) {
                Log.e("","onStateChanged: %d, %s  $id + $newState")
                Log.e("","File: %s%s $filename")

                if (newState == TransferState.COMPLETED) {
                    success.invoke()
                } else if (newState == TransferState.FAILED) {
//                    showSnackBar(context.getString(R.string.message_unable_to_upload_Image),1,context)
                    failure.invoke()
                }
            }
        })
    }
}
