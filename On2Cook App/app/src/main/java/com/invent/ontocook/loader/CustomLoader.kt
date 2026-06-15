package com.invent.ontocook.loader

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.invent.ontocook.R
import com.invent.ontocook.databinding.DialogCustomLoaderBinding
import kotlinx.android.synthetic.main.dialog_custom_loader.view.clProgress
import kotlinx.android.synthetic.main.dialog_custom_loader.view.progressView
import kotlinx.android.synthetic.main.dialog_custom_loader.view.tvMessage
import kotlinx.android.synthetic.main.dialog_custom_loader.view.tvPercentage


class CustomLoader(context: Context, val isProgress: Boolean) : Dialog(context) {

    private lateinit var binding: DialogCustomLoaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        binding = DataBindingUtil.inflate(
//            LayoutInflater.from(context),
//            R.layout.dialog_custom_loader,
//            null,
//            false
//        )
//        binding = DialogCustomLoaderBinding.inflate(LayoutInflater.from(context))
        setContentView(R.layout.dialog_custom_loader)
//        binding = DataBindingUtil.setContentView(context as Activity, R.layout.dialog_custom_loader)
        if (isProgress) {
//            binding.clProgress.viewShow()
//            binding.progressView.indeterminateTintList =
//                context.getColorStateList(R.color.yellow)
//            binding.progressView.viewGone()
//            binding.tvMessage.viewGone()
            this.window?.decorView?.clProgress?.visibility = View.VISIBLE
            this.window?.decorView?.progressView?.indeterminateTintList =
                context.getColorStateList(R.color.yellow)
            this.window?.decorView?.progressView?.visibility = View.GONE
            this.window?.decorView?.tvMessage?.visibility = View.GONE
        } else {
//            binding.clProgress.viewGone()
//            binding.clProgress.viewShow()
            this.window?.decorView?.clProgress?.visibility = View.GONE
            this.window?.decorView?.progressView?.visibility = View.VISIBLE
        }
        window?.setLayout(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    fun setMessage(message: String) {
        Log.e("TAG", "setMessage: $message")
//        binding.tvMessage.text = message
        try {
            this.window?.decorView?.tvMessage?.text = message
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateProgress(message: String) {
//        binding.tvPercentage.text = message
        this.window?.decorView?.tvPercentage?.text = message
    }
    fun updateMessage(message: String) {
        Log.e("TAG", "setMessage:updateMessage $message")
//        binding.tvPercentage.text = message
        this.window?.decorView?.tvMessage?.text = message
    }
}