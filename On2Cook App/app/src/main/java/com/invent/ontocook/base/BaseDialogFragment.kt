package com.invent.ontocook.base

import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.invent.ontocook.R

/**
 *Created by Parth Patel on 20,January,2023
 *Excellent WebWorld
 *parth.patel.eww@gmail.com
 */

typealias Inflate<T> = (LayoutInflater, ViewGroup?, Boolean) -> T

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseDialogFragment<B : ViewBinding>(
    private val inflate: Inflate<B>,
    private val isCancelable: Boolean = true,
) : DialogFragment() {

    private var _binding: B? = null
    protected val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflate.invoke(inflater, container, false)
        dialog?.let { nonNullDialog ->
            nonNullDialog.window?.let { nonNullWindow ->
                nonNullWindow.requestFeature(Window.FEATURE_NO_TITLE)
                val windowManagerLayoutParams = nonNullWindow.attributes
                windowManagerLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                windowManagerLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                nonNullWindow.attributes = windowManagerLayoutParams
                /*Add For Cancelable false*/
                nonNullDialog.setCancelable(isCancelable)
                nonNullDialog.setCanceledOnTouchOutside(isCancelable)
                /*EO Add For Cancelable false*/
                nonNullWindow.setBackgroundDrawable(
                    ColorDrawable(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.transparent
                        )
                    )
                )
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        createViewModel()
        initControl()
        addObserver()
    }

    open fun addObserver() = Unit
    open fun removeObserver() = Unit

    abstract fun initControl()

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        removeObserver()
        _binding = null
    }

}