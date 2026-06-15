package com.invent.ontocook.create_recipe

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.DownloadListener
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.OnToCookApplication
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ActivityCreateNewRecipeBinding
import com.invent.ontocook.models.RecentItem
import com.invent.ontocook.multiple_connection.model.AudioFileListModel
import com.invent.ontocook.multiple_connection.model.AudioFileModel
import com.invent.ontocook.multiple_connection.model.database.RecentlyPlayedRecipe
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import com.invent.ontocook.utils.getEnum
import com.invent.ontocook.utils.withNotNull
import com.opencsv.CSVReader
import com.rx2androidnetworking.Rx2AndroidNetworking
import kotlinx.android.synthetic.main.activity_create_new_recipe.nav_host_fragment
import kotlinx.android.synthetic.main.view_header.tvPageTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.Executors


class CreateNewRecipe : AppCompatActivity() {

    private lateinit var binding: ActivityCreateNewRecipeBinding

    //-------Indicate screen flow type-------//
    var createRecipeScreenFlowType: Constants.CreateRecipeScreenFlowType? = null

    lateinit var recipe: Recipe
    var panType = Constants.STAINLESS_STEEL
    var isEditRecipe = false
    lateinit var fragment: Array<Fragment>
    var list: ArrayList<String> = ArrayList()
    var recipeNameList = arrayOf(
        "Sauted Vegetables",
        "Gajar ka Halwa",
        "Kheer",
        "Chicken Curry",
        "Cake",
        "Vegetable Samosa",
        "Chicken Samosa",
        "Pink Sauce Pasta",
        "Paneer Tikka Masala",
        "Chicken Kebabs",
        "Tomato & Basil Soup",
        "Gulab Jamun",
        "Baigan ka Bharta",
        "Mutton Rogan Josh",
        "Kung Pao Potatoes",
        "Lebanese Chicken Cutlets",
        "Sabudana Vada",
        "Kachori",
        "Chilli Paneer Samosa",
        "Hariyali Samosa",
        "Bhindi Masala",
        "Minestrone Soup",
        "Cauliflower Samosa",
        "Palak Paneer Samosa",
        "Potato Swirls",
        "Egg fried Potato Swirls",
        "Chicken Lollipop",
        "Nutri Kulcha",
        "Aloo Puff Patties",
        "Chicken Puff Patties"
    )
    private var fragmentManager: FragmentManager? = null
    var recipeName: String = ""
    var createRecipeFragment: Fragment? = null
    var ingredientsFragment: Fragment? = null
    private var instructionFragment: Fragment? = null
    private var selectedTab = 0
    val listRecipe: ArrayList<Recipe> = ArrayList()
    val audioFileList: ArrayList<AudioFileModel> = ArrayList()

    var qtyUnits = arrayOf("gm", "ml", "cup", "tsp", "tbsp", "number", "liter")
    var ingredients = arrayListOf(
        "ajwain",
        "All purpose flour",
        "amchur powder",
        "anar dana",
        "asofoetida (Hing)",
        "Baking Powder",
        "basil",
        "bayleaf",
        "Beans",
        "Black salt",
        "Boiled potatoes",
        "Boneless chicken Breast",
        "Bread Crumbs",
        "Brocolli",
        "Butter",
        "capsicum",
        "Cardamom Powder",
        "Carrots",
        "Cashew",
        "Cauliflower",
        "celery sticks",
        "chaat masala",
        "Chicken",
        "chicken wings",
        "chilli sauce",
        "Cinamon",
        "Coriander",
        "Coriander Powder",
        "cornflour",
        "cream",
        "Cumin",
        "Cumin Powder",
        "curd",
        "dry red chillies",
        "eggs",
        "fennel seeds",
        "Garam Masala",
        "Garlic",
        "ghee",
        "Ginger",
        "ginger garlic paste",
        "Ginger juliennes",
        "Ginger powder",
        "Green Chillies",
        "groundnuts",
        "kasuri methi",
        "Khada Masala",
        "Lime juice",
        "macaroni",
        "Milk",
        "Milk Powder",
        "mixed herbs",
        "Mutton",
        "nutmeg",
        "nutri keema",
        "nutri nuggets",
        "Oil",
        "olive oil",
        "Onion",
        "Oregano",
        "Paneer",
        "pasta",
        "peanuts",
        "Peas",
        "Pepper",
        "Potatoes",
        "red chilli flakes",
        "Red Chilli Powder",
        "Rice",
        "Roasted peanuts",
        "Rose water",
        "Rose water essence",
        "Saffron",
        "Salt",
        "schezwan sauce",
        "shredded mozerella",
        "Soaked sabudana",
        "soya sauce",
        "spinach",
        "spring onions",
        "Sugar",
        "thyme",
        "tomato puree",
        "tomatoes",
        "Turmeric",
        "Vanilla essence",
        "vegetable broth",
        "vinegar",
        "Water"
    )
    val gmUnits = arrayListOf(
        "10",
        "30",
        "50",
        "100",
        "200",
        "300",
        "400",
        "500",
        "600",
        "700",
        "800",
        "900",
        "1000",
        "1500",
        "2000",
        "2500",
        "3000"
    )
    var typeUnits = arrayListOf(
        "Chop",
        "Mince",
        "Wash",
        "Soak",
        "Finely Chop",
        "Mix",
        "Pcs",
        "Refrigerate",
        "Marinate",
        "Divide",
        "Dip",
        "Cut",
        "Shape",
        "Roll",
        "Cook",
        "Heat",
        "Serve",
        "Open",
        "Cover",
        "Close",
        "Take",
        "Mash",
        "Shape"
    )
    val cupUnits = arrayOf(
        "0.5", "1", "1.5", "2", "2.5", "3", "4.5", "5"
    )
    val mlUnits = arrayOf(
        "5", "10", "20", "30", "50", "100", "150", "200", "500", "750", "1000"
    )
    val tspUnits = arrayOf(
        "0.25", "0.5", "1", "2", "4", "5"
    )
    val tbspUnits = arrayOf(
        "0.5", "1", "1.5", "2", "2.5", "3", "4", "5"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_create_new_recipe)
        init()
        initListener()
    }

    private fun initListener() {
        binding.tabHome.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onTabSelected(tab: TabLayout.Tab) {
                Log.e("PrinceEWW>>>", "CurrentSelectedTab Position: ${binding.tabHome.selectedTabPosition}")
                if (isEditRecipe) {
                    setCurrentFragment(tab.position)
                    return
                }
                if (!(createRecipeFragment as CreateRecipeFragment).isValid()) {
                    binding.tabHome.selectTab(binding.tabHome.getTabAt(0))
                    return
                } else if (tab.position == 0) {
                    Log.e("TAG", "onTabSelected: Pos")
                    setCurrentFragment(0)
                    return
                }
                if (!ingredientsFragment!!.isAdded) {
                    setCurrentFragment(1)
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.tabHome.selectTab(binding.tabHome.getTabAt(1))
                    }, 100)
                    return
                }
                if (ingredientsFragment!!.isAdded && !(ingredientsFragment as IngredientsFragment).isValid(
                        tab.position != 1
                    ) //Change to "tab.position != 1", from "false", by PrinceEWW, because need to show toast, when other then 2nd tab(Ingredients) is clicked
                ) {
                    setCurrentFragment(1)
                    binding.tabHome.selectTab(binding.tabHome.getTabAt(1))
                    return
                }
                setCurrentFragment(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.icon?.clearColorFilter()
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
        binding.viewHeader.ivLeft.setOnClickListener {
            Constants.showAlertDialog(this,
                "Exit Recipe",
                "Are you sure you want to Exit ?",
                { dialog, which ->
                    finish()
                },
                { dialog, which ->

                })
        }
        binding.viewHeader.ivRight.setOnClickListener {
            if ((createRecipeFragment as CreateRecipeFragment).isValid()) { //Check validation of "CreateRecipeFragment"
                /*if (ingredientsFragment!!.isAdded)
                    (ingredientsFragment as IngredientsFragment).isValid(false)*/
                if (isIngredientsValid()) { //Check validation of "Ingredients" section in "IngredientsFragment"
                    /*if (instructionFragment!!.isAdded)
                        (instructionFragment as InstructionFragment).isValid()*/
                    if (isInstructionsValid()) { //Check validation of "Instruction" section in "InstructionFragment"
                        //-------If all 3 screen data is valid, update recipe in database-------//
                        Executors.newSingleThreadExecutor().execute {
                            OnToCookApplication.dbInstance.recipeDao().update(recipe) //Update recipe in data base of Recipe
                            val oldRecentlyPlayed =
                                OnToCookApplication.dbInstance.recentlyPlayedDao()
                                    .getAllRecipeList()
                                    .firstOrNull {
                                        it.id == recipe.id
                                    }
                            oldRecentlyPlayed?.let {
                                val recipeRecent = RecentlyPlayedRecipe()
                                Gson().toJson(recipe)
                                recipeRecent.name = recipe!!.name
                                recipeRecent.audio1 = recipe!!.audio1
                                recipeRecent.audio2 = recipe!!.audio2
                                recipeRecent.Instruction = recipe!!.Instruction
                                recipeRecent.Ingredients = recipe.Ingredients
                                recipeRecent.macAddress = it.macAddress
                                recipeRecent.description = recipe.description
                                recipeRecent.id = recipe.id
                                recipeRecent.imageUrl = recipe.imageUrl
                                recipeRecent.difficulty = recipe.difficulty
                                recipeRecent.tags = recipe.tags
                                recipeRecent.category = recipe.category
                                recipeRecent.insertTime = it.insertTime
                                OnToCookApplication.dbInstance.recentlyPlayedDao()
                                    .update(recipeRecent) //Update recipe in recently played recipe
                            }
                            runOnUiThread {
                                Log.e("PrinceEWW>>>", "RESULT_OK - edit recipe Done")
                                //-------After update recipe, we have to set result, for update item in recipeDetail screen and recipeListing screen(RecipeFragment)-------//
                                val resultIntent = Intent()
                                resultIntent.putExtra(Constants.IS_EDIT, true)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                        }
                    }
                }

            }
        }
    }

    /*Change tab programmatically
    * in case data of any fragment(CreateRecipeFragment || IngredientsFragment || InstructionFragment) are not valid,
    * Need to select tab of that fragment*/
    fun selectTabOfTabLayout(index: Int) {
        if (binding.tabHome.selectedTabPosition != index) {
            binding.tabHome.getTabAt(index)?.select()
        }
    }

    //-----Check validation of ingredients-------//
    private fun isIngredientsValid() : Boolean {
       if (ingredientsFragment!!.isAdded) {
           //-------If ingredientsFragment is added - in case user "navigate to ingredientsFragment", check validation in "ingredientsFragment"-------//
           return (ingredientsFragment as IngredientsFragment).isValid(true)
       } else {
           Log.e("PrinceEWW>>>", "Check validation - for ingredients from createNewRecipeActivity")
           //-------In case user come from edit flow - in case user "not navigate to ingredientsFragment"(in this case ingredientsFragment!!.isAdded will be "false"), check validation "here"-------//
           recipe.Ingredients.forEachIndexed { index, ingredients ->
//               ingredients.pan_type = (requireActivity() as CreateNewRecipe).panType
               if (ingredients.title.trim().isEmpty() || ingredients.weight.split(" ")[0].trim().isEmpty()) {
                   binding.tabHome.getTabAt(1)?.select()
                   //-------After select second tab need to set handler for show toast and expand that position in ingredientsFragment-------//
                   Handler(Looper.getMainLooper()).postDelayed({
                       if (ingredientsFragment!!.isAdded) {
                           (ingredientsFragment as IngredientsFragment).showToast(index)
                       }
                   }, 100)
                   return false
               }
           }
           return true
       }
    }

    //-------Check validation of instruction-------//
    private fun isInstructionsValid(): Boolean {
        if (instructionFragment!!.isAdded) {
            //-------If ingredientsFragment is added - in case user "navigate to instructionFragment", check validation in "instructionFragment"-------//
            return (instructionFragment as InstructionFragment).isValid()
        } else {
            Log.e("PrinceEWW>>>", "Check validation - for instructions  from createNewRecipeActivity")
            //-------In case user come from edit flow - in case user "not navigate to instructionFragment"(in this case instructionFragment!!.isAdded will be "false"), check validation "here"-------//
            recipe.Instruction.forEachIndexed { index, ingredients ->
                Log.e("PrinceEWW>>>", "Instruction validation - Check $index step instruction")
                if (ingredients.Text.isEmpty()) {
                    openInstructionSectionForValidationFailed(
                        index,
                        "Please Complete Step No:- ${index + 1}"
                    )
                    return false
                } else if (ingredients.Weight.isEmpty()) {
                    openInstructionSectionForValidationFailed(
                        index,
                        "Please Complete Step No:- ${index + 1}"
                    )
                    return false
                } else if (index == 0 && createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE && ingredients.threshold.isNullOrEmpty()) {
                    //--------Need to set validation of threshold only for fry mode and for first step only-------//
                    openInstructionSectionForValidationFailed(
                        index,
                        "Please Complete Step No:- ${index + 1}"
                    )
                    return false
                } else if (index == 0 && createRecipeScreenFlowType == Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE && ((ingredients.threshold?.toInt()
                        ?: 0) < 150 || (ingredients.threshold?.toInt() ?: 0) > 210)
                ) {
                    //--------Need to set validation of threshold only for fry mode and for first step only-------//
                    openInstructionSectionForValidationFailed(index, "Please enter threshold value between 150 - 210 in step No:- ${index + 1}")
                    return false
                } else if (ingredients.lid == "close" && (ingredients.Magnetron_on_time.isEmpty() /*|| ingredients.Magnetron_on_time == "0"*/ || ingredients.Magnetron_power.isEmpty() /*|| ingredients.Magnetron_power == "0"*/)) {
                    openInstructionSectionForValidationFailed(index, "Please Complete Step No:- ${index + 1}")
                    return false
                } else if (ingredients.lid == "close" && ingredients.mag_severity == "low" && (ingredients.Indtime_lid_con.isEmpty() || ingredients.Indtime_lid_con == "0")) {
                    openInstructionSectionForValidationFailed(index, "Please Complete Step No:- ${index + 1}")
                    return false
                } else if (ingredients.lid == "open" && (ingredients.Induction_on_time.isEmpty() /*|| ingredients.Induction_on_time == "0"*/ || ingredients.Induction_power.isEmpty() /*|| ingredients.Induction_power == "0"*/)) {
                    openInstructionSectionForValidationFailed(index, "Please Complete Step No:- ${index + 1}")
                    return false
                } else if (ingredients.purge_on.isNotEmpty() && (ingredients.purge_on.toIntOrNull() ?: Constants.DEFAULT_ZERO) < Constants.DEFAULT_TEN) {
                    openInstructionSectionForValidationFailed(index, "Please enter minimum 10 ml to start spray in step No:- ${index + 1}")
                    return false
                }

                //set threshold value in instruction
                if (ingredients.threshold.isNullOrEmpty())
                    ingredients.threshold = "0"
            }
            return true
        }
    }

    //-------If Instruction screen validation is not proper, need to navigate to instruction screen and show validation message toast-------//
    private fun openInstructionSectionForValidationFailed(index: Int, toastMessage:String){
        binding.tabHome.getTabAt(2)?.select()
        //-------After select second tab need to set handler for show toast and expand that position in instructionFragment-------//
        Handler(Looper.getMainLooper()).postDelayed({
            if (instructionFragment!!.isAdded) {
                (instructionFragment as InstructionFragment).showToast(index, toastMessage)
            }
        }, 100)
    }


    private fun init() {
        //-------Below code is commented by "PrinceEWW", because in below code developer trying to fetch all the data from localdatabase, and because of this we face memory issue-------//
        CoroutineScope(Dispatchers.IO).launch {
//            listRecipe.addAll(OnToCookApplication.dbInstance.recipeDao().getAllRecipeList())
            val data: ArrayList<AudioFileListModel> = ArrayList()
            try {
                //addding from local memory
                var listIndex = -1

                val assetManager = assets
                try {
                    // List all files and folders in the root path of assets
                    val list = assetManager.list("audio")
                    if (list != null) {
                        for (item in list) {
                            // Check if the item is a folder (subdirectory)
                            if (assetManager.list("audio/$item")!!.isNotEmpty()) {
                                val fileModelList: ArrayList<AudioFileModel> = ArrayList()
                                assetManager.list("audio/$item")!!.forEachIndexed { index, it ->
                                    listIndex++
                                    val model = AudioFileModel(
                                        listIndex, it, "audio/$item/", Constants.FILE_TYPE.STATIC,
                                        false, null
                                    )
                                    fileModelList.add(
                                        model
                                    )
                                    audioFileList.add(model)
                                }
                                data.add(
                                    AudioFileListModel(
                                        id = data.size,
                                        name = item,
                                        false,
                                        fileModel = fileModelList.toList()
                                    )
                                )
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                Log.e("TAG", "readRawData: ${data.size}")
                val externalStorageDir = filesDir

                val folder = File(externalStorageDir, "Audio")
                if (folder.exists()) {
                    folder.list()?.forEach { it ->
                        val folder = File(folder, "/$it")
                        val fileModelList: ArrayList<AudioFileModel> = ArrayList()
                        val listSorted = folder.listFiles()
                        if (listSorted != null && listSorted.isNotEmpty()) {
                            Arrays.sort(
                                listSorted
                            ) { object1, object2 -> (if (object1.lastModified() > object2.lastModified()) object1.lastModified() else object2.lastModified()).toInt() }
                        }
                        folder.listFiles()?.forEachIndexed { index, file ->
                            listIndex++
                            val model = AudioFileModel(
                                id = listIndex,
                                fileName = file.name,
                                filePath = null, type = Constants.FILE_TYPE.UPLOADED,
                                isSelected = false,
                                file = file
                            )
                            fileModelList.add(
                                model
                            )
                            audioFileList.add(model)
                        }
                        data.add(
                            AudioFileListModel(
                                id = data.size,
                                name = "$it File",
                                false,
                                fileModel = fileModelList.toList()
                            )
                        )
                    }
                }

                audioFileList.forEach {
                    DebugLog.e("File List ${it.id} Type ${it.type} Name :- ${it.fileName}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }



//        if (intent.extras != null && intent.extras!!.containsKey(Constants.RECIPE_LIST)) {
////            val list = intent.extras!!.getString(Constants.RECIPE_LIST)
//            val list1 : ArrayList<Recipe> = intent.getParcelableExtra(Constants.RECIPE_LIST)!!
////            val type = object : TypeToken<ArrayList<Recipe>>() {}.type
////            val listRecipeJson: ArrayList<Recipe> = Gson().fromJson(list, type)
//            listRecipe.addAll(list1)
//        }
        externalCacheDir?.let {
            val file = File(it.absolutePath, "Audio2.csv")
            file.createNewFile()
            Rx2AndroidNetworking.download(
                "https://docs.google.com/spreadsheets/d/1xE8pAeBYciX1RK8xS-u_5DEtFgK0buZrIS7WPwQ7Hf8/export?format=csv",
                it.absolutePath,
                file.name
            ).build().setDownloadProgressListener { bytesDownloaded, totalBytes ->
                Log.e(
                    "TAG", "onProgress: $bytesDownloaded  $totalBytes"
                )
            }.startDownload(object : DownloadListener {
                override fun onDownloadComplete() {
                    Log.e("TAG", "onDownloadComplete: ")
                }

                override fun onError(anError: ANError?) {
                    Log.e("TAG", "onDownloadComplete: $anError")
                }
            })
        }

        try {
            if (Constants.getCSVData(this).exists()) {
                val reader = CSVReader(FileReader(Constants.getCSVData(this)))
                var nextLine: Array<String>
                while (reader.readNext().also { nextLine = it } != null) {
                    // nextLine[] is an array of values from the line
                    println("${nextLine.size}")
                    if (nextLine[1] == "Recipe Name") {
                        list.clear()
                    }
                    if (nextLine.isNotEmpty()) {
                        list.add(nextLine[1])
                    }
                    if (nextLine[1] == "Ingredients") {
                        list.removeLast()
                        recipeNameList = list.toTypedArray()
                        recipeNameList.forEachIndexed { index, names ->
                            Log.e("TAG", "init: $index Name:-$names")
                        }
                        list.clear()
                    }
                    if (nextLine[1] == "Type") {
                        ingredients.clear()
                        ingredients.addAll(list)
                        list.clear()
                    }
                    println(nextLine[0] + nextLine[1] + "etc...")
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("TAG", "init: ${e.printStackTrace()}")
            e.printStackTrace()
        }
        if (intent.extras?.containsKey(Constants.RECIPE) == true) {
            val recentItem = intent.extras!!.getSerializable(Constants.RECIPE) as RecentItem?
            val type = object : TypeToken<Recipe>() {}.type
            val listRecipeJson: Recipe = Gson().fromJson(recentItem!!.recipe, type)
            recipe = listRecipeJson
            isEditRecipe = true
            recipeName = recipe.name[0]
            binding.viewHeader.tvPageTitle.text = recipe.name[0]
            binding.viewHeader.ivMenu.visibility = View.GONE
            binding.viewHeader.ivRight.visibility = View.VISIBLE
            //-------For edit recipe we have to manage screen flow type(Regular/Fry mode) according "recipe.category"-------//
            recipe.category.withNotNull { categotry ->
                createRecipeScreenFlowType =
                    if (categotry == Constants.DEFAULT_ONE.toString()) Constants.CreateRecipeScreenFlowType.CREATE_FRY_RECIPE else Constants.CreateRecipeScreenFlowType.CREATE_RECIPE
            }
        } else {
            intent.extras.withNotNull { bundle ->
                bundle.getEnum<Constants.CreateRecipeScreenFlowType>(Constants.BUNDLE_KEY_CREATE_RECIPE_SCREEN_FLOW_TYPE)
                    .withNotNull {
                        createRecipeScreenFlowType = it
                    } //Differentiate this screen flow
            }
            createRecipe()
        }
//        if (intent.extras != null && intent.extras!!.containsKey("recipe")) {
//            val recipeIntent = intent.extras!!.getString("recipe")
//            val type = object : TypeToken<Recipe>() {}.type
//            val listRecipeJson: Recipe = Gson().fromJson(recipeIntent, type)
//            recipe = listRecipeJson
//            isEditRecipe = true
//            recipeName = recipe.name[0]
//            binding.viewHeader.tvPageTitle.text = recipe.name[0]
//            binding.viewHeader.ivMenu.visibility = View.GONE
//            binding.viewHeader.ivRight.visibility = View.VISIBLE
//        } else
//            createRecipe()
        createRecipeFragment = CreateRecipeFragment.newInstance(Gson().toJson(recipe), isEditRecipe)
        ingredientsFragment = IngredientsFragment.newInstance(Gson().toJson(recipe), isEditRecipe)
        instructionFragment = InstructionFragment.newInstance(
            Gson().toJson(recipe),
            isEditRecipe,
            createRecipeScreenFlowType!!
        )
        fragmentManager = supportFragmentManager

        binding.statusViewScroller.statusView.run {
            currentCount = 2
            circleFillColorCurrent = Color.RED
        }
        setCurrentFragment(selectedTab)
    }

    private fun createRecipe() {
        recipe = Recipe()
        //-------Set category to local database according screen flow type-------//
        recipe.category = createRecipeScreenFlowType?.categoryForLocalDB
            ?: Constants.CreateRecipeScreenFlowType.CREATE_RECIPE.categoryForLocalDB
    }

    internal fun setCurrentFragment(position: Int) {
        fragmentManager!!.beginTransaction().apply {
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            when (position) {
                0 -> {
                    binding.viewHeader.tvPageTitle.text =
                        resources.getString(R.string.txt_recipe_detail)
                    selectedTab = 0
                    if (createRecipeFragment!!.isAdded) {
                        show(createRecipeFragment!!)
                    } else {
                        add(nav_host_fragment.id, createRecipeFragment!!, "dashBoardFragment")
                        addToBackStack("dashBoardFragment")
                    }
                    if (ingredientsFragment!!.isAdded) {
                        hide(ingredientsFragment!!)
                    }
                    if (instructionFragment!!.isAdded) {
                        hide(instructionFragment!!)
                    }
                }

                1 -> {
                    if (recipe.name.isNotEmpty()) tvPageTitle.text = recipe.name[0]
                    selectedTab = 1
                    if (ingredientsFragment!!.isAdded) {
                        show(ingredientsFragment!!)
                    } else {
                        add(nav_host_fragment.id, ingredientsFragment!!, "musicFragment")
                        addToBackStack("musicFragment")
                    }
                    if (createRecipeFragment!!.isAdded) {
                        hide(createRecipeFragment!!)
                    }
                    if (instructionFragment!!.isAdded) {
                        hide(instructionFragment!!)
                    }
                }

                2 -> {
                    Log.e("TAG", "setCurrentFragment: ${recipe.Ingredients}")
                    if (recipe.name.isNotEmpty()) tvPageTitle.text = recipe.name[0]
                    selectedTab = 2
                    if (instructionFragment!!.isAdded) {
                        show(instructionFragment!!)
                        instructionFragment!!.onResume()
                    } else {
                        add(nav_host_fragment.id, instructionFragment!!, "podcastFragment")
                        addToBackStack("podcastFragment")
                    }
                    if (createRecipeFragment!!.isAdded) {
                        hide(createRecipeFragment!!)
                    }
                    if (ingredientsFragment!!.isAdded) {
                        hide(ingredientsFragment!!)
                    }
                }
            }
            commit()
        }
        Handler(Looper.getMainLooper()).postDelayed(
            { binding.tabHome.selectTab(binding.tabHome.getTabAt(position)) }, 100
        )
    }

    fun openFragment(i: Int) {
        val manager = supportFragmentManager
        val transaction: FragmentTransaction = manager.beginTransaction()
        when (i) {
            0 -> {
                transaction.replace(R.id.nav_host_fragment, createRecipeFragment!!)
            }

            1 -> {
                transaction.replace(R.id.nav_host_fragment, ingredientsFragment!!)
            }

            2 -> {
                transaction.replace(R.id.nav_host_fragment, instructionFragment!!)
            }
        }
        transaction.commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (selectedTab) {
            2 -> {
                if (ingredientsFragment!!.isAdded)
                    binding.tabHome.selectTab(binding.tabHome.getTabAt(1))
                else
                    setCurrentFragment(0)
            }

            1 -> {
                setCurrentFragment(0)
                binding.tabHome.selectTab(binding.tabHome.getTabAt(0))
            }

            else -> {
                fragmentManager?.let {
                    for (i in 0 until fragmentManager!!.backStackEntryCount) {
                        fragmentManager!!.popBackStack()
                    }
                }
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    fun done() {
        binding.viewHeader.ivRight.performClick()
    }


    fun setResultForUpdateListOnRecipeFragment() {
        setResult(RESULT_OK)
        finish()
    }


}