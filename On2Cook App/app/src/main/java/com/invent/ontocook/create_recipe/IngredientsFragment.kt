package com.invent.ontocook.create_recipe

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog
import com.google.gson.Gson
import com.invent.ontocook.R
import com.invent.ontocook.adapter.CreateIngredientsListAdapter
import com.invent.ontocook.databinding.FragmentIngredientsBinding
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.PermissionManagerUtils
import java.io.File

private const val ARG_PARAM1 = "recipe"
private const val ARG_PARAM2 = "isEdit"

class IngredientsFragment : Fragment(), CreateIngredientsListAdapter.OnClick {
    private lateinit var ingredientsListAdapter: CreateIngredientsListAdapter
    lateinit var recipe: String
    var isEdit: Boolean = false
    private val TAG = this::class.java.simpleName
    private lateinit var binding: FragmentIngredientsBinding
    private var fileOpenIndex: Int = -1
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Use the returned uri.
            val uriContent = result.uriContent
            val uriFilePath = result.getUriFilePath(requireContext()) // optional usage
//            Glide.with(requireContext()).load(uriContent).into(binding.ivItem)
            uriContent?.let {
                val fileName = Constants.getFileName(
                    requireContext(), uri = it
                )
                DebugLog.e("position $fileOpenIndex  $fileName")
                if (fileOpenIndex != -1) {
                    ingredientsListAdapter.getList()[fileOpenIndex].image = uriContent.toString()
                    ingredientsListAdapter.notifyItemChanged(fileOpenIndex)
//                    val view = binding.rvIngredients.getChildAt(fileOpenIndex)
//                    if (view != null) view.findViewById<TextView>(R.id.tvImageName).text = fileName
                }
            }
        } else {
            // An error occurred.
            val exception = result.error
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            recipe = it.getString(ARG_PARAM1, "")
            isEdit = it.getBoolean(ARG_PARAM2, false)
        }
        ingredientsListAdapter = context?.let { CreateIngredientsListAdapter(it, this) }!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_ingredients, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun init() {
        binding.rvIngredients.adapter = ingredientsListAdapter
        if (isEdit) {
            Gson().toJson(ingredientsListAdapter.getList())
            val recipe = Gson().fromJson(recipe, Recipe::class.java)
            if (recipe.Ingredients.isNotEmpty()) {
                ingredientsListAdapter.setList(recipe.Ingredients)
            }
        } else
            addItem()
    }

    private fun initListener() {
        binding.btnAddNewStep.setOnClickListener {
            addItem()
        }
        binding.btnCookNow.setOnClickListener {
            //-------Below code is commented by PrinceEWW, because as new "CR" client don't want to Done(Save and backPress) recipe, user should navigate to next(3rd) fragment-------//
            /*if (isEdit)
                (activity as CreateNewRecipe).done()
            else*/
                if (isValid(true)) {
                    setIngredientsInRecipe()
                    (requireActivity() as CreateNewRecipe).setCurrentFragment(2)
                }
        }
    }

    private fun checkPermissionAndOpenImagePicker() {
        val version13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val permissionList = arrayListOf(
            android.Manifest.permission.CAMERA,
        )
        if (!version13) {
            permissionList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        PermissionManagerUtils.checkPermission(
            requireContext(),
            requireActivity(),
            permissionList,
            PermissionManagerUtils.PermissionSessionManager(requireActivity()),
            object : PermissionManagerUtils.PermissionAskListener {
                override fun onNeedPermission() {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissionList.toTypedArray(),
                        Constants.REQUEST_CAMERA_PERMISSION
                    )
                }

                override fun onPermissionPreviouslyDenied() {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissionList.toTypedArray(),
                        1
                    )
                }

                override fun onPermissionPreviouslyDeniedWithNeverAskAgain() {
                    //-------------HERE OPEN
                    DialogUtils().commonDialog(
                        context = requireContext(),
                        title = getString(R.string.title_permission_open_settings),
                        message = getString(R.string.message_storage_camera_permission),
                        positiveButton = getString(R.string.button_setting),
                        negativeButton = getString(R.string.button_cancel),
                        isAppLogoDisplay = true,
                        isCancelable = true,
                        callbackSuccess = {
                            //------Positive CallBack----//
                            context?.openPermissionSettings()
                        },
                        callbackNegative = {
                            //---------Negative CallBack----------//
                        })
                }

                override fun onPermissionGranted() {
                    openFilePicker()
                }

            })
    }

    private fun openFilePicker() {
        cropImage.launch(
            options {
                setGuidelines(CropImageView.Guidelines.ON)
                setAspectRatio(4, 3)
                setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                setRequestedSize(500, 500)
            }
        )
    }

    private fun setIngredientsInRecipe() {
        (requireActivity() as CreateNewRecipe).recipe.Ingredients =
            ingredientsListAdapter.getList()
//        ingredientsListAdapter.getList().forEach {
//            var found = false
//            var foundIndex = 0
//            (requireActivity() as CreateNewRecipe).ingredients.forEachIndexed { index, ingredients ->
//                if (ingredients.equals(it.title, true)) {
//                    found = true
//                    foundIndex = index
//                    return@forEachIndexed
//                }
//            }
//            if (!found && it.title.isNotEmpty()) {
//                (requireActivity() as CreateNewRecipe).ingredients.add(it.title)
//            } else {
//                if (found) {
//                    val new =
//                        (requireActivity() as CreateNewRecipe).ingredients.removeAt(foundIndex)
//                    (requireActivity() as CreateNewRecipe).ingredients.add(new)
//                }
//            }
//            var typeFound = false
//            var typeFoundIndex = 0
//            (requireActivity() as CreateNewRecipe).typeUnits.forEachIndexed { index, ingredients ->
//                if (ingredients.equals(it.audioP, true)) {
//                    typeFound = true
//                    typeFoundIndex = index
//                    return@forEachIndexed
//                }
//            }
//            if (!typeFound && it.audioP.isNotEmpty()) {
//                (requireActivity() as CreateNewRecipe).typeUnits.add(it.audioP)
//            } else {
//                val new = (requireActivity() as CreateNewRecipe).typeUnits.removeAt(typeFoundIndex)
//                (requireActivity() as CreateNewRecipe).typeUnits.add(new)
//            }
////            var unitFound = false
////            (requireActivity() as CreateNewRecipe).gmUnits.forEach { ingredients ->
////                if (ingredients.equals(it.weight.split(" ")[0], true)) {
////                    unitFound = true
////                }
////            }
////            if (!unitFound) {
////                (requireActivity() as CreateNewRecipe).gmUnits.add(it.weight.split(" ")[0])
////            }
//        }
//        (requireActivity() as CreateNewRecipe).ingredients.reverse()
//        (requireActivity() as CreateNewRecipe).typeUnits.reverse()
//        (requireActivity() as CreateNewRecipe).gmUnits.reverse()

    }

    override fun onPause() {
        super.onPause()
    }

    private fun addItem() {
        val recipe = Recipe()
        val list = ingredientsListAdapter.getList()
        recipe.Instruction.mapIndexed { index, instructions ->
            instructions.id = index
        }
        val step = Ingredients()
        ingredientsListAdapter.add(step)
        binding.rvIngredients.scrollToPosition(ingredientsListAdapter.itemCount - 1)
//        btnAddNewStep.isEnabled = false
//        btnCookNow.isEnabled = false
    }

    private fun openDialog(pos: Int) {
        val properties = DialogProperties()
        properties.selection_mode = DialogConfigs.SINGLE_MODE
        properties.selection_type = DialogConfigs.FILE_SELECT
        properties.root = File(DialogConfigs.DEFAULT_DIR)
        properties.error_dir = File(DialogConfigs.DEFAULT_DIR)
        properties.offset = File(DialogConfigs.DEFAULT_DIR)
        properties.extensions = null
        properties.show_hidden_files = false
        val dialog = FilePickerDialog(context, properties)
        dialog.setTitle("Select a File")

        dialog.setDialogSelectionListener {
            val file = File(it.first())
            ingredientsListAdapter.getList()[pos].image = file.path
            val view = binding.rvIngredients.getChildAt(pos)
            if (view != null) view.findViewById<TextView>(R.id.tvImageName).text =
                file.nameWithoutExtension
            dialog.dismiss()
        }
        dialog.show()
    }

    private val getResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
//        if (it.resultCode == AppCompatActivity.RESULT_OK) {
//            val result = CropImage.getActivityResult(it.data)
//            if (it.resultCode === AppCompatActivity.RESULT_OK) {
//                val resultUri = result.uri
//                val index = it.data?.getIntExtra(Constants.INDEX, 0)
//                Log.e(TAG, "onActivityResult: $resultUri")
//                Log.e(TAG, "onActivityResult: $index")
//                val view = binding.rvIngredients.getChildAt(index!!)
//                resultUri?.let {
//                    val fileName = Constants.getFileName(
//                        requireContext(), uri = it
//                    )
//                    Log.e(
//                        "TAG Constants", ": ${
//                            Constants.getFileName(
//                                requireContext(), uri = it
//                            )
//                        }"
//                    )
//                    view.findViewById<TextView>(R.id.tvImageName).text = fileName
//                }
//                ingredientsListAdapter.getList()[index!!].image = resultUri.toString()
//            } else if (it.resultCode === CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
//                val error = result.error
//            }
//        }
    }

    override fun onItemClick(index: Int) {
//        val intent = CropImage.activity().setGuidelines(CropImageView.Guidelines.ON)
//            .setCropShape(CropImageView.CropShape.RECTANGLE).setFixAspectRatio(true)
//            .setAspectRatio(100, 100)
//            .setBorderLineColor(Color.RED)
//            .setGuidelinesColor(Color.GREEN)
//            .setBorderLineThickness(
//                resources.getDimensionPixelSize(R.dimen.activity_margin)
//                    .toFloat()
//            )
//            .getIntent(requireContext())
//        intent.putExtra(Constants.INDEX, index)
//        getResult.launch(intent)
        binding.rvIngredients.layoutManager!!.scrollToPosition(0)
    }

    override fun enableButton(b: Boolean) {
//        (requireActivity() as CreateNewRecipe).recipe.Ingredients =
//            Gson().toJson(ingredientsListAdapter.getList())
//        btnAddNewStep.isEnabled = b
//        btnCookNow.isEnabled = b
    }

    override fun onOpenFilePicker(pos: Int) {
        fileOpenIndex = pos
        checkPermissionAndOpenImagePicker()
    }

    internal fun isValid(showToast: Boolean): Boolean {
        Log.e("PrinceEWW>>>", "Check validation - IngredientsFragment")
        ingredientsListAdapter.getList().forEachIndexed { index, ingredients ->
            Log.e("PrinceEWW>>>", "Check ingredients validation in Ingredients fragment - index: $index")
            ingredients.pan_type = (requireActivity() as CreateNewRecipe).panType
            if (ingredients.title.trim().isEmpty() || ingredients.weight.split(" ")[0].trim().isEmpty()) {
                if (showToast) showToast(index)
                return false
            }
        }
//        if (!showToast) { //Commented by PrinceEWW, because need to update recipe data in recipe object, after check validation successfully
            setIngredientsInRecipe()
//        }
        Log.e("TAG", "isValid: ${binding.btnAddNewStep.isEnabled}")
//        return binding.btnAddNewStep.isEnabled //Commented by PrinceEWW, because it is always true
        return true
    }

    fun showToast(index: Int) {
        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                "Please Complete Step No:- ${index + 1}",
                Toast.LENGTH_SHORT
            ).show()
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            ingredientsListAdapter.open(index)
        }
    }

    /*Change tab of tabLayout programmatically
    * We check validation on click right icon, which is on top-right corner in CreateNewRecipe(Activity)
    * And in case fragment data is not valid && this fragment is not open*/
    private fun openThisTabBecauseValidationFailed() {
        (requireActivity() as CreateNewRecipe).selectTabOfTabLayout(1)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, isEdit: Boolean) =
            IngredientsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putBoolean(ARG_PARAM2, isEdit)
                }
            }
    }
}