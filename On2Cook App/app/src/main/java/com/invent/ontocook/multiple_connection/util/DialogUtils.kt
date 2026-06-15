package com.invent.ontocook.multiple_connection.util

import android.app.ActionBar
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.widget.AppCompatTextView
import androidx.databinding.DataBindingUtil
import com.invent.ontocook.R
import com.invent.ontocook.databinding.DialogAudioTypeBinding
import com.invent.ontocook.databinding.DialogCommonBinding
import com.invent.ontocook.databinding.DialogDeleteRecipeBinding
import com.invent.ontocook.extension.setSafeOnClickListener
import com.invent.ontocook.extension.viewGone
import com.invent.ontocook.utils.Constants
import kotlinx.android.synthetic.main.dialog_scan_device_list.*


class DialogUtils {

    //----------This is common dialog for app common used--------//
    fun commonDialog(
        context: Context,
        title: String = "",
        message: String = "",
        positiveButton: String = "",
        negativeButton: String = "",
        isAppLogoDisplay: Boolean,
        isCancelable: Boolean,
        callbackSuccess: () -> Unit,
        callbackNegative: () -> Unit
    ) {
        val binding: DialogCommonBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            com.invent.ontocook.R.layout.dialog_common,
            null,
            false
        )
        val dialog = Dialog(context).apply {
            setContentView(binding.root)
            setCancelable(isCancelable)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )

            binding.textViewMessage.text = message
            binding.buttonPositive.text = positiveButton
            binding.buttonNegative.text = negativeButton

            if (positiveButton.isEmpty()) {
                binding.buttonPositive.viewGone()
            }
            if (negativeButton.isEmpty()) {
                binding.buttonNegative.viewGone()
            }

            if (title.isNotEmpty()) {
                binding.textViewTitle.text = title
            } else {
                binding.textViewTitle.viewGone()
            }

            if (!isAppLogoDisplay) {
                binding.imageViewAppLogo.viewGone()
            }

            binding.buttonNegative.setSafeOnClickListener {
                dismiss()
                callbackNegative.invoke()
            }
            binding.buttonPositive.setSafeOnClickListener {
                dismiss()
                callbackSuccess.invoke()
            }
        }
        dialog.show()
    }

    fun deleteRecipeDialog(
        context: Context,
        title: String = "",
        message: String = "",
        isAppLogoDisplay: Boolean,
        isCancelable: Boolean,
        deleteRecipeFromOn2CookDeviceCallback: () -> Unit, //Callback of delete recipe from on2cook device
        deleteRecipeFromMobileDeviceCallback: () -> Unit, //Callback of delete recipe from application mobile device
        deleteRecipeFromBothDevices: () -> Unit //Callback of delete recipe from On2Cook device and application mobile device
    ) {
        val binding: DialogDeleteRecipeBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.dialog_delete_recipe,
            null,
            false
        )
        val dialog = Dialog(context).apply {
            setContentView(binding.root)
            setCancelable(isCancelable)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )

            binding.textViewMessage.text = message

            if (title.isNotEmpty()) {
                binding.textViewTitle.text = title
            } else {
                binding.textViewTitle.viewGone()
            }

            if (!isAppLogoDisplay) {
                binding.imageViewAppLogo.viewGone()
            }

            binding.buttonOn2CookDevice.setSafeOnClickListener {
                deleteRecipeFromOn2CookDeviceCallback.invoke()
                dismiss()
            }
            binding.buttonApplication.setSafeOnClickListener {
                deleteRecipeFromMobileDeviceCallback.invoke()
                dismiss()
            }
            binding.buttonBoth.setSafeOnClickListener {
                deleteRecipeFromBothDevices.invoke()
                dismiss()
            }
        }
        dialog.show()
    }

    fun selectAudioTypeDialog(
        context: Context,
        isAppLogoDisplay: Boolean,
        isCancelable: Boolean,
        selectedType: (Constants.AUDIO_TYPE) -> Unit
    ) {
        val binding: DialogAudioTypeBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.dialog_audio_type,
            null,
            false
        )
        var lastSelectedId: AppCompatTextView = binding.btnTime
        val dialog = Dialog(context).apply {
            setContentView(binding.root)
            setCancelable(isCancelable)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )
            if (!isAppLogoDisplay) {
                binding.imageViewAppLogo.viewGone()
            }

            binding.btnError.setSafeOnClickListener {
                lastSelectedId = select(binding.btnError, lastSelectedId,context)
                selectedType.invoke(Constants.AUDIO_TYPE.ERROR)
                dismiss()
            }
            binding.btnAction.setSafeOnClickListener {
                lastSelectedId = select(binding.btnAction, lastSelectedId, context)
                selectedType.invoke(Constants.AUDIO_TYPE.ACTION)
                dismiss()
            }
            binding.btnFeedback.setSafeOnClickListener {
                lastSelectedId = select(binding.btnFeedback, lastSelectedId, context)
                selectedType.invoke(Constants.AUDIO_TYPE.FEEDBACK)
                dismiss()
            }
            binding.btnIngredients.setSafeOnClickListener {
                lastSelectedId = select(binding.btnIngredients, lastSelectedId, context)
                selectedType.invoke(Constants.AUDIO_TYPE.INGREDIENTS)
                dismiss()
            }
            binding.btnQuantity.setSafeOnClickListener {
                lastSelectedId = select(binding.btnQuantity, lastSelectedId, context)
                selectedType.invoke(Constants.AUDIO_TYPE.QUANTITY)
                dismiss()
            }
            binding.btnTime.setSafeOnClickListener {
                lastSelectedId = select(binding.btnTime, lastSelectedId, context)
                selectedType.invoke(Constants.AUDIO_TYPE.TIME)
                dismiss()
            }
        }
        dialog.show()
    }

    private fun select(
        btnAction: AppCompatTextView,
        lastSelectedId: AppCompatTextView?,
        context: Context
    ): AppCompatTextView {
        lastSelectedId?.apply {
            setBackgroundResource(R.drawable.ic_round_corner_button_white)
            setTextColor(context.getColor(R.color.dark_grey4))
        }
        btnAction.setBackgroundResource(R.drawable.ic_round_corner_button_orange_deselect)
        btnAction.setTextColor(context.getColor(R.color.white))
        return btnAction
    }

    fun fullScreenDialog(
        context: Context,
        title: String = "",
        message: String = "",
        positiveButton: String = "",
        negativeButton: String = "",
        isAppLogoDisplay: Boolean,
        isCancelable: Boolean,
        callbackSuccess: () -> Unit,
        callbackNegative: () -> Unit
    ) {
        val binding: DialogCommonBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            com.invent.ontocook.R.layout.dialog_common,
            null,
            false
        )
        val dialog = Dialog(context).apply {
            setContentView(binding.root)
            setCancelable(isCancelable)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ActionBar.LayoutParams.MATCH_PARENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )
            binding.textViewMessage.text = message
            binding.buttonPositive.text = positiveButton
            binding.buttonNegative.text = negativeButton

            if (positiveButton.isEmpty()) {
                binding.buttonPositive.viewGone()
            }
            if (negativeButton.isEmpty()) {
                binding.buttonNegative.viewGone()
            }

            if (title.isNotEmpty()) {
                binding.textViewTitle.text = title
            } else {
                binding.textViewTitle.viewGone()
            }

            if (!isAppLogoDisplay) {
                binding.imageViewAppLogo.viewGone()
            }

            binding.buttonNegative.setSafeOnClickListener {
                dismiss()
                callbackNegative.invoke()
            }
            binding.buttonPositive.setSafeOnClickListener {
                dismiss()
                callbackSuccess.invoke()
            }
        }
        dialog.show()
    }

    fun scanDialog(
        context: Context,
        title: String = "",
        isCancelable: Boolean
    ) {
        val dialog = Dialog(context).apply {
            setContentView(com.invent.ontocook.R.layout.dialog_scan_device)
            setCancelable(isCancelable)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ActionBar.LayoutParams.MATCH_PARENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )

            tvTitle.text = title
        }
        dialog.show()
    }

}