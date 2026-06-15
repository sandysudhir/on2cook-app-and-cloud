package com.invent.ontocook.create_recipe

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.adapter.CreateInstructionAdapter
import com.invent.ontocook.databinding.FragmentInstructionBinding
import com.invent.ontocook.models.Instructions
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.getEnum
import com.invent.ontocook.utils.putEnum
import org.apache.commons.lang3.EnumUtils
import java.util.concurrent.Executors

private const val ARG_PARAM1 = "recipe"
private const val ARG_PARAM2 = "isEdit"
private const val ARG_PARAM3 = "createRecipeScreenFlowType"

class InstructionFragment : Fragment(), CreateInstructionAdapter.OnClick {
    //-------Indicate createRecipe screen flow type-------//
    private var createRecipeScreenFlowType: Constants.CreateRecipeScreenFlowType? = null

    private lateinit var instructionAdapter: CreateInstructionAdapter
    private lateinit var recipe: String
    private var isEdit: Boolean = false

    private lateinit var binding: FragmentInstructionBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            recipe = it.getString(ARG_PARAM1, "")
            isEdit = it.getBoolean(ARG_PARAM2, false)
            createRecipeScreenFlowType =
                it.getEnum<Constants.CreateRecipeScreenFlowType>(ARG_PARAM3)
        }
        instructionAdapter = context?.let {
            CreateInstructionAdapter(
                context = it,
                createRecipeScreenFlowType = createRecipeScreenFlowType, listener = this
            )
        }!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(
            layoutInflater, R.layout.fragment_instruction, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    override fun onResume() {
        super.onResume()
        instructionAdapter.ingredients.clear()
        (requireActivity() as CreateNewRecipe).recipe.Ingredients.mapIndexed { index, c ->
            instructionAdapter.ingredients.add(c.title)
        }
        var foundIndex = 0
        instructionAdapter.listVisible.forEachIndexed { index, it ->
            if (it == 0) {
                foundIndex = index
                return@forEachIndexed
            }
        }
        instructionAdapter.notifyItemChanged(foundIndex)
    }

    private fun init() {
//        tvPageTitle.text = resources.getString(R.string.txt_instr)
        binding.rvSteps.adapter = instructionAdapter
        binding.rvSteps.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->

        }
        binding.rvSteps.setRecyclerListener { }
        binding.rvSteps.isNestedScrollingEnabled = false
        if (isEdit) {
            val recipe = Gson().fromJson(recipe, Recipe::class.java)
            if (recipe.Instruction.isNotEmpty()) {
                instructionAdapter.setList(recipe.Instruction)
            }
        } else
            addItem()
        binding.btnAddNewStep.setOnClickListener {
            addItem()
        }
        binding.btnCookNow.setOnClickListener {
            if (isEdit) {
                if (isValid()) {
                    (activity as CreateNewRecipe).done()
                }
            } else
                if (isValid()) {
                    Executors.newSingleThreadExecutor().execute {
                        OnToCookApplication.dbInstance.recipeDao()
                            .insert((requireActivity() as CreateNewRecipe).recipe)
                        activity?.runOnUiThread {
                            Log.e("PrinceEWW>>>", "RESULT_OK - for create new recipe")
                            (activity as CreateNewRecipe).setResult(Activity.RESULT_OK)
                            (activity as CreateNewRecipe)
                            requireActivity().finish()
                        }
                    }
                }
        }
    }

    internal fun isValid(): Boolean {
        Log.e("PrinceEWW>>>", "Check validation - InstructionFragment")
        instructionAdapter.getList().forEachIndexed { index, ingredients ->
            if (ingredients.Text.isEmpty()) {
                showToast(index, "Please Complete Step No:- ${index + 1}")
                return false
            } else if (ingredients.Weight.isEmpty()) {
                showToast(index,"Please Complete Step No:- ${index + 1}")
                return false
            } else if(index == 0 && createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE && ingredients.threshold.isNullOrEmpty()){
                //--------Need to set validation of threshold only for fry mode and for first step only-------//
                showToast(index, "Please Complete Step No:- ${index + 1}")
                return false
            } else if(index == 0 && createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE && ((ingredients.threshold?.toInt() ?: 0) < 150 || (ingredients.threshold?.toInt() ?: 0) > 210)){
                //--------Need to set validation of threshold only for fry mode and for first step only-------//
                showToast(index, "Please enter threshold value between 150 - 210 in step No:- ${index + 1}")
                return false
            } else if (ingredients.lid == "close" && (ingredients.Magnetron_on_time.isEmpty() /*|| ingredients.Magnetron_on_time == "0"*/ || ingredients.Magnetron_power.isEmpty() /*|| ingredients.Magnetron_power == "0"*/)) {
                showToast(index, "Please Complete Step No:- ${index + 1}")
                return false
            } else if (ingredients.lid == "close" && ingredients.mag_severity == "low" && (ingredients.Indtime_lid_con.isEmpty() || ingredients.Indtime_lid_con == "0")) {
                showToast(index, "Please Complete Step No:- ${index + 1}")
                return false
            } else if (ingredients.lid == "open" && (ingredients.Induction_on_time.isEmpty() /*|| ingredients.Induction_on_time == "0"*/ || ingredients.Induction_power.isEmpty() /*|| ingredients.Induction_power == "0"*/)) {
                showToast(index, "Please Complete Step No:- ${index + 1}")
                return false
            } else if (ingredients.purge_on.isNotEmpty() && (ingredients.purge_on.toIntOrNull() ?: Constants.DEFAULT_ZERO) < Constants.DEFAULT_TEN) {
                showToast(index, "Please enter minimum 10 ml to start spray in step No:- ${index + 1}")
                return false
            }

            //set threshold value in instruction
            if (ingredients.threshold.isNullOrEmpty())
                ingredients.threshold = "0"
        }
        setInstructionInRecipe()
        Log.e("PrinceEWW>>>", "Instruction validation - return true in validation of instruction step")
        return true
    }

    fun showToast(index: Int, toastMessage: String) {
        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                toastMessage,
                Toast.LENGTH_SHORT
            ).show()
            openThisTabBecauseValidationFailed() //Change Tab programmatically, in case this tab is not selected
            instructionAdapter.open(index)
        }
    }

    /*Change tab of tabLayout programmatically
    * We check validation on click right icon, which is on top-right corner in CreateNewRecipe(Activity)
    * And in case fragment data is not valid && this fragment is not open*/
    private fun openThisTabBecauseValidationFailed() {
        (requireActivity() as CreateNewRecipe).selectTabOfTabLayout(2)
    }

    private fun setInstructionInRecipe() {
        (requireActivity() as CreateNewRecipe).recipe.Instruction = instructionAdapter.getList()
    }

    private fun addItem() {
        val step = Instructions()
        instructionAdapter.add(step)
//        btnAddNewStep.isEnabled = false
//        btnCookNow.isEnabled = false
        binding.rvSteps.layoutManager!!.scrollToPosition(0)
        instructionAdapter.notifyDataSetChanged()
        instructionAdapter.notifyItemInserted(instructionAdapter.getList().size)
    }

    override fun OnItemClick(index: Int) {
        binding.rvSteps.layoutManager!!.scrollToPosition(0)
        instructionAdapter.notifyDataSetChanged()

//        val view = rvSteps.getChildAt(index)
//        view.clInsItem.visibility = View.GONE
    }

    override fun enableButton(btnEnable: Boolean) {
//        btnAddNewStep.isEnabled = btnEnable
//        btnCookNow.isEnabled = btnEnable
    }

    companion object {
        @JvmStatic
        fun newInstance(
            param1: String,
            isEdit: Boolean,
            createRecipeScreenFlowType: Constants.CreateRecipeScreenFlowType,
        ) =
            InstructionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putBoolean(ARG_PARAM2, isEdit)
                    putEnum(ARG_PARAM3, createRecipeScreenFlowType)
                }
            }
    }
}