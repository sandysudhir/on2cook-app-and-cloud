package com.invent.ontocook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.invent.ontocook.adapter.SubCategoriesListAdapter
import com.invent.ontocook.models.SubCategory
import kotlinx.android.synthetic.main.activity_create_recipe.*
import kotlinx.android.synthetic.main.view_header.*
import java.io.File

class CreateRecipeActivity : AppCompatActivity() {

    private val PICK_IMAGE_REQUEST = 234
    private var filePath: Uri? = null
    private var fileName: String = ""

    lateinit var subCategoriesListAdapter: SubCategoriesListAdapter
    private var difficultyLevels = arrayListOf<String>("Easy", "Medium", "Hard")
    private var categories = arrayListOf<String>("Vegan", "Vegetarian", "Non Veg")

    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_recipe)

        init()
    }

    private fun showSnackBar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        if (snackbar != null) {
            snackbar?.dismiss()
            snackbar = null
        }
        snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            length
        )
        snackbar?.show()
    }

    private fun checkIsNotEmpty(editText: AppCompatEditText, errorText: String): Boolean {
        return if (editText.text?.isEmpty()!!) {
            editText.error = errorText
            false
        } else {
            editText.error = null
            true
        }
    }

    private fun checkIsNotEmptyTags(): Boolean {
        return if (etTags.tags.size > 0) {
            true
        } else {
            showSnackBar("Please enter recipe tags")
            false
        }
    }

    private fun init() {
        tvPageTitle.text = "Create Recipe"

        rvSubCategories.layoutManager = GridLayoutManager(this, 3)
        subCategoriesListAdapter = SubCategoriesListAdapter()
        rvSubCategories.adapter = subCategoriesListAdapter
        subCategoriesListAdapter.setSubCategoriesList(
            listOf(
                SubCategory("Curry"),
                SubCategory("Fried Meat"),
                SubCategory("Soup"),
                SubCategory("Dessert"),
                SubCategory("Salad"),
                SubCategory("Pasta")
            )
        )

        toggleDifficultyLevel.setLabels(difficultyLevels)
        toggleCategories.setLabels(categories)

        ivLeft.setOnClickListener {
            onBackPressed()
        }

        btnDone.setOnClickListener {
            var intent = Intent(
                this@CreateRecipeActivity,
                CreateRecipeAddStepsActivity::class.java
            )
            startActivity(intent)

//            if(checkIsNotEmpty(etName, "Please enter recipe name")
//                && checkIsNotEmpty(etAbout, "Please enter recipe description")
//                && checkIsNotEmptyTags()){
//                //flProgressContainer.visibility = View.VISIBLE
//
//                var intent = Intent(this@CreateRecipeActivity, CreateRecipeAddStepsActivity::class.java)
//                startActivity(intent)

//                RxFirebaseStorage.putFile(
//                    FirebaseStorage.getInstance(Constants.FIREBASE_IMAGE_STORAGE_BASE_URL)
//                        .getReference(fileName), filePath!!)
//                    .subscribe({
//                        RxFirebaseStorage.getDownloadUrl(it.metadata?.reference!!).subscribe({
//                            println("upload path    ${it}")
//
//                            var recipe = Recipe()
//                            recipe.name = etName.text.toString()
//                            recipe.description = etAbout.text.toString()
//                            recipe.imageUrl = it.toString()
//                            recipe.tags = etTags.tags.joinToString(",")
//                            recipe.difficulty = difficultyLevels[toggleDifficultyLevel.checkedTogglePosition]
//                            recipe.category = categories[toggleCategories.checkedTogglePosition]
//
//                            RxFirestore.addDocument(
//                                Firebase.firestore.collection(Constants.FIREBASE_COLLECTION_RECIPE),
//                                recipe.toMap()
//                            )
//                                .subscribe({
//                                    flProgressContainer.visibility = View.GONE
//                                    Toast.makeText(
//                                        this@CreateRecipeActivity,
//                                        "Recipe added successfully",
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                }, {
//                                    flProgressContainer.visibility = View.GONE
//                                    Toast.makeText(
//                                        this@CreateRecipeActivity,
//                                        it.localizedMessage,
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                })
//                        }, {
//                            flProgressContainer.visibility = View.GONE
//                            Toast.makeText(
//                                this@CreateRecipeActivity,
//                                it.localizedMessage,
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        })
//                    },{
//                        Toast.makeText(
//                            this@CreateRecipeActivity,
//                            it.localizedMessage,
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    })
            //           }
        }

        btnChooseImage.setOnClickListener {
            chooseFile()
        }
    }

    private fun chooseFile() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode === PICK_IMAGE_REQUEST && resultCode === Activity.RESULT_OK
            && data?.data != null
        ) {
            filePath = data.data
            try {
                var file = File(filePath?.path)
                tvImageName.text = file.name
                fileName = "${System.currentTimeMillis()}.${file.extension}"
                val bitmap =
                    MediaStore.Images.Media.getBitmap(contentResolver, filePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}