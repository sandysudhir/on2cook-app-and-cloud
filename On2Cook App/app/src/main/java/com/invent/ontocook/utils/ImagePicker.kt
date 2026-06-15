package com.invent.ontocook.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.invent.ontocook.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class ImagePicker : BottomSheetDialogFragment(), View.OnClickListener {

    internal var mCurrentPhotoPath: String? = null

    internal lateinit var imagePickerResult: ImagePickerResult
    lateinit var uri: Uri

    fun setImagePickerResult(imagePickerResult: ImagePickerResult) {
        this.imagePickerResult = imagePickerResult

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_image_picker_layout, container)
        dialog!!.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        isCancelable = true
        dialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
        super.onCreate(savedInstanceState)

        retainInstance = true

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.camera).setOnClickListener(this)
        view.findViewById<TextView>(R.id.gallery).setOnClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

    }


    override fun onDestroyView() {
        super.onDestroyView()

    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.camera ->
                dispatchTakePictureIntent()

            R.id.gallery ->
                openGallary()

        }
    }

    private fun openGallary() {
//        selectImageFromGalleryResult.launch("image/*")
    }
//    private val selectImageFromGalleryResult =
//      registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
//            uri?.let { imageUri ->
//                dismiss()
//                imagePickerResult.onResult(imageUri.toString())
//            }
//        }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent

        // Create the File where the photo should go
        var photoFile: File? = null
        var photoURI: Uri? = null
        try {
            photoFile = createImageFile()
            photoURI = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".provider",
                photoFile
            )
            uri = Uri.fromFile(photoFile)
        } catch (ex: IOException) {
            Log.e("TakePicture", ex.message!!)
            Log.e("context!!.packageName ", requireContext().packageName)
        }

        // Continue only if the File was successfully created
        if (photoFile != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(takePictureIntent, Constants.REQUEST_TAKE_PHOTO)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        mCurrentPhotoPath = image.absolutePath
        return image
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.REQUEST_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            if (mCurrentPhotoPath != null) {
                imagePickerResult.onResult(mCurrentPhotoPath!!,uri)
                dismiss()
            }
        } else if (requestCode == Constants.RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK) {

            if (data != null) {
                val selectedImage = data.data
                if (selectedImage != null) {
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor = requireActivity().contentResolver.query(
                        selectedImage,
                        filePathColumn,
                        null,
                        null,
                        null
                    )
                    if (cursor != null) {
                        cursor.moveToFirst()
                        val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                        mCurrentPhotoPath = cursor.getString(columnIndex)
                        cursor.close()
                        if (mCurrentPhotoPath != null) {
                            {

                            }
                        }
                        try {
                            imagePickerResult.onResult(mCurrentPhotoPath!!, uri)
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                        }
                    }
                }
                dismiss()
            }
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        @NonNull permissions: Array<String>,
        @NonNull grantResults: IntArray
    ) {
        /*      if (requestCode == Constants.REQUEST_CAMERA_PERMISSION) {

                  // We have requested multiple permissions for camera and storage, so all of them need to be
                  // checked.
                  if (PermissionUtil.verifyPermissions(grantResults)) {
                      // All required permissions have been granted, display open camera.
                      dispatchTakePictureIntent()

                  } else {
                       openPermissionSettingPopup()
                       Log.i("ImagePicker", "camera and storage permission is not granted.")
                  }

              } else if (requestCode == Constants.REQUEST_GALLERY_PERMISSION) {

                  if (PermissionUtil.verifyPermissions(grantResults)) {
                      // All required permissions have been granted, display open gallary.
                      openGallary()
                  } else {
                       openPermissionSettingPopup()
                       Log.i("ImagePicker", "camera and storage permission is not granted.")
                  }

              } else {
                  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
              }*/
    }

    interface ImagePickerResult {
        fun onResult(path: String, uri: Uri)
    }

    private fun openPermissionSettingPopup() {
//        DialogUtils().commonDialog(context = requireContext(),
//            title = getString(R.string.title_permission),
//            message = getString(R.string.message_storage_camera_permission),
//            positiveButton = getString(R.string.button_setting),
//            negativeButton = getString(R.string.button_cancel),
//            isAppLogoDisplay = false,
//            isCancelable = false,
//            callbackSuccess = {
//                //------Positive CallBack----//
//                DebugLog.e("Clicked Positive")
//                requireActivity().openPermissionSettings()
//            },
//            callbackNegative = {
//                //---------Negative CallBack----------//
//                DebugLog.e("Clicked Negative")
//            })

    }

}
