package com.invent.ontocook.loader

import android.content.Context
import com.invent.ontocook.utils.DebugLog

open class LoadingUtils {
    companion object {
        private var customLoader: CustomLoader? = null

        fun showDialog(
            context: Context?,
            isCancelable: Boolean,
            message: String = ""
        ) {
            hideDialog()
            if (context != null) {
                try {
                    customLoader = CustomLoader(context, false)
                    customLoader?.let {
                        it.setMessage(message)
                        it.setCanceledOnTouchOutside(true)
                        it.setCancelable(isCancelable)
                        it.show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun hideDialog() {
            if (customLoader != null && customLoader?.isShowing!!) {
                customLoader = try {
                    customLoader?.dismiss()
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }

        fun showLoading(
            context: Context?,
            isCancelable: Boolean,
            message: String = ""
        ) {
            hideDialog()
            if (context != null) {
                try {
                    customLoader = CustomLoader(context, true)
                    customLoader?.let {
                        it.setMessage(message)
                        it.setCanceledOnTouchOutside(true)
                        it.setCancelable(isCancelable)
                        it.show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun showLoadingWithText(
            context: Context?,
            isCancelable: Boolean,
            message: String = ""
        ) {
            hideDialog()
            DebugLog.e("Showloading ")
            if (context != null) {
                try {
                    customLoader = CustomLoader(context, false).apply {
                        show()
                        updateMessage(message)
                    }
//                    customLoader?.let {
//                        it.setMessage(message)
//                        it.setCanceledOnTouchOutside(true)
//                        it.setCancelable(isCancelable)
//                        it.show()
//                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun showDialog2(
            context: Context?,
            isCancelable: Boolean,
            message: String = ""
        ) {
            hideDialog2()
            if (context != null) {
                try {
                    customLoader = CustomLoader(context, true)

                    customLoader?.let {
                        it.setMessage(message)
                        it.setCanceledOnTouchOutside(true)
                        it.setCancelable(isCancelable)
                        it.show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun hideDialog2() {
            if (customLoader != null && customLoader?.isShowing!!) {
                customLoader = try {
                    customLoader?.dismiss()
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }

        fun updateText(context: Context, strProgress: String) {
            if (customLoader != null && customLoader?.isShowing!!) {
                customLoader?.updateProgress(strProgress)
            } else {
                showLoading(context, false, "Firmware Updating..")
            }
        }
    }
}