package com.invent.ontocook.dialog

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.DialogFragment
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.dialog_preview.view.*

class PreviewDialog(val recipe: String) : DialogFragment() {

    lateinit var context: AppCompatActivity
    private lateinit var currentView: View

    interface OnDoneButtonClickListener {
        fun onDoneButtonClick()
    }

    var mOnDoneButtonClickListener: OnDoneButtonClickListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(R.layout.dialog_preview, container, false)
        dialog?.setTitle("")
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        currentView.tvTitle.text = "Created Json :- "
        currentView.tvResult.text = recipe
        currentView.ivCopy.setOnClickListener {
            val clipboard: ClipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)!!
            val clip: ClipData = ClipData.newPlainText("label", recipe)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getText(R.string.strRecipeCopied), Toast.LENGTH_SHORT).show()
        }
         return currentView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme_Dialog_Custom)
    }

}