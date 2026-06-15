package com.invent.ontocook.create_recipe

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentCreateRecipeBinding
import com.invent.ontocook.extension.openPermissionSettings
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.multiple_connection.util.DialogUtils
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.PermissionManagerUtils
import kotlinx.android.synthetic.main.fragment_create_recipe.etAbout
import kotlinx.android.synthetic.main.fragment_create_recipe.toggleDifficultyLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ARG_PARAM1 = "recipe"
private const val ARG_PARAM2 = "isEdit"

class CreateRecipeFragment : Fragment() {
    private var difficultyLevels = arrayListOf("Easy", "Medium", "Hard")
    private var panType = arrayListOf(Constants.STAINLESS_STEEL, Constants.ALUMINIUM)
    lateinit var recipe: String
    var editRecipeName: String = ""
    private var isEdit: Boolean = false
    private lateinit var binding: FragmentCreateRecipeBinding
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Use the returned uri.
            val uriContent = result.uriContent
            val uriFilePath = result.getUriFilePath(requireContext()) // optional usage
//            Glide.with(requireContext()).load(uriContent).into(binding.ivItem)
            (requireActivity() as CreateNewRecipe).recipe.imageUrl = uriContent.toString()
            uriContent?.let {
                val fileName = Constants.getFileName(
                    requireContext(), uri = it
                )
                binding.tvImageName.text = fileName
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
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_create_recipe, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        initListener()
    }

    private fun initListener() {
        binding.btnNext.setOnClickListener {
            if (isValid()) {
                (requireActivity() as CreateNewRecipe).setCurrentFragment(1)
            }
        }
        binding.btnChooseImage.setOnClickListener {
            checkPermissionAndOpenImagePicker()
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
                setRequestedSize(720, 540)
            }
        )
    }

    internal fun isValid(): Boolean {
        Log.e("PrinceEWW>>>", "Check validation - CreateRecipeFragment")
        (requireActivity() as CreateNewRecipe).recipe.difficulty =
            difficultyLevels[toggleDifficultyLevel.checkedTogglePosition]
        (requireActivity() as CreateNewRecipe).panType =
            panType[binding.togglePan.checkedTogglePosition]

        if (binding.etName.text!!.isEmpty()) {
            binding.etName.error = "Please Enter Name"
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            return false
        } else if (!Regex("^[a-zA-Z0-9_\\- ]+\$").matches(binding.etName.text.toString())){
            Toast.makeText(
                context,
                "Recipe Name should not contain special character",
                Toast.LENGTH_SHORT
            ).show()
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            return false
        } else if (binding.etName.text!!.length > 40) {
            Toast.makeText(
                context,
                "Recipe's name should not contain more than 32 character",
                Toast.LENGTH_SHORT
            ).show()
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            return false
        } else if (!checkValidName()) {
            Toast.makeText(
                context, "Recipe Already Created With this Name", Toast.LENGTH_SHORT
            ).show()
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            return false
        } else if (etAbout.text!!.isEmpty()) {
            binding.etName.error = null
            binding.etAbout.error = "Please Enter Description"
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            return false
        }
        (requireActivity() as CreateNewRecipe).recipe.description = etAbout.text.toString()
        (requireActivity() as CreateNewRecipe).recipe.name.clear()
        (requireActivity() as CreateNewRecipe).recipe.audio2.clear()
        (requireActivity() as CreateNewRecipe).recipe.audio1.clear()
        (requireActivity() as CreateNewRecipe).recipe.name.add(0, binding.etName.text.toString())
        (requireActivity() as CreateNewRecipe).recipe.audio1.add(0, binding.etName.text.toString())
        (requireActivity() as CreateNewRecipe).recipe.audio2.add(0, "preparation")
        return true
    }

    /*Change tab of tabLayout programmatically
    * We check validation on click right icon, which is on top-right corner in CreateNewRecipe(Activity)
    * And in case fragment data is not valid && this fragment is not open*/
    private fun openThisTabBecauseValidationFailed() {
        (requireActivity() as CreateNewRecipe).selectTabOfTabLayout(0)
    }

    //-------Check validation of recipe name, because user can not create recipe with same name-------//
    private fun checkValidName(): Boolean {
        //-------For flow of edit recipe, if current recipe name and name before edit are same then no need to check validation-------//
        if (isEdit && binding.etName.text.toString() == editRecipeName) {
            return true
        }
        //Commented by PrinceEWW, (Check using query, that same name recipe is exist or not)
        /*(requireActivity() as CreateNewRecipe).listRecipe.forEachIndexed { index, recipeDb ->
            if (binding.etName.text.toString() == recipeDb.name[0]) {
                return false
            }
        }*/

        //PrinceEWW
        val isSameNameRecipeAvailable = isSameNameRecipeAvailable()
        if (isSameNameRecipeAvailable) {
            return false
        }

        return true
    }

    //-------Check recipe is already created with this name or not-------//
    private fun isSameNameRecipeAvailable(): Boolean {
        var isAvailable = false
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                isAvailable = OnToCookApplication.dbInstance.recipeDao()
                    .isSameNameItemAlreadyExist(binding.etName.text.toString()) //Here, when user enter "Al", we got "Al", "Aloo Matar", "Aloo Paneer"
                    .any { recipe -> recipe.name[0] == binding.etName.text.toString() } //So we need to return true if same name(Al) is exist or not, else false
            }.join() // Wait for the coroutine to finish
        }
        return isAvailable
    }

    private fun init() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_dropdown_text,
            (requireActivity() as CreateNewRecipe).recipeNameList
        )
        binding.etName.setAdapter(adapter)
        binding.etName.threshold = 0
        binding.toggleDifficultyLevel.setLabels(difficultyLevels)
        binding.togglePan.setLabels(panType)
        if (isEdit) {
            val recipe = Gson().fromJson(recipe, Recipe::class.java)
            if (recipe.name.isNotEmpty()) {
                binding.etName.setText(recipe.name[0])
                editRecipeName = recipe.name[0]
            }
            if (recipe.description.isNotEmpty()) {
                binding.etAbout.setText(recipe.description)
            }
            if (recipe.imageUrl.isNotEmpty()) {
                val fileName = Constants.getFileName(
                    requireContext(), uri = Uri.parse(recipe.imageUrl)
                )
                binding.tvImageName.text = fileName
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, isEdit: Boolean) = CreateRecipeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
                putBoolean(ARG_PARAM2, isEdit)
            }
        }
    }
}