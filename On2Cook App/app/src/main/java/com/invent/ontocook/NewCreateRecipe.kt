package com.invent.ontocook

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.invent.ontocook.models.Ingredients
import com.invent.ontocook.multiple_connection.model.database.Recipe
import com.invent.ontocook.models.RecipeObject
import com.invent.ontocook.models.StepView
import com.invent.ontocook.utils.Constants
import com.sixthsolution.apex.nlp.dict.DictionaryBuilder
import com.sixthsolution.apex.nlp.dict.Tag
import com.sixthsolution.apex.nlp.english.CookStepDetector
import com.sixthsolution.apex.nlp.english.EnglishTokenizer
import com.sixthsolution.apex.nlp.english.PreStepDetector
import com.sixthsolution.apex.nlp.ner.Entity
import com.sixthsolution.apex.nlp.ner.regex.RegExChunker
import com.sixthsolution.apex.nlp.tagger.StandardTagger
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

class NewCreateRecipe : AppCompatActivity() {

    lateinit var stepView : StepView
    lateinit var stepView2 : StepView
    lateinit var stepView3 : StepView

    lateinit var finalRecipeObject : Recipe

    var finalSteps = mutableListOf<String>()
    var timeSlap : HashMap<String, String> = HashMap<String, String>()
    var timePercent = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_create_recipe)

        init()
    }

    fun init(){
        val vb = DictionaryBuilder()

        vb.tag(
            Tag.PRESTEP_PREFIX,
            Entity.PRESTEP
        )
            .e(arrayOf("pour", "mix", "wash", "soak", "add", "cut", "pulse",
                "spoon", "drain", "put", "take", "make", "place", "peel", "chunk", "combine", "chop"))

//        vb.tag(
//            Tag.PRESTEP_SEPARATOR,
//            Entity.PRESTEP
//        )
//            .e(arrayOf("and", ",", "or", "the"))

        vb.tag(
            Tag.COOKSTEP_MATCH,
            Entity.COOKSTEP
        )
            .e(arrayOf("cook", "heat", "bake", "preheat", "steam", "fry", "boil"))

        vb.tag(
            Tag.DATE_SEPARATOR,
            Entity.COOKSTEP
        )
            .e(arrayOf(",", "."))

        var recipe = Gson().fromJson(loadJSONFromAsset(), RecipeObject::class.java)

        finalRecipeObject = Recipe()
        finalRecipeObject.name.add(recipe.title)
        finalRecipeObject.description = recipe.summary
        finalRecipeObject.imageUrl = recipe.image
        finalRecipeObject.tags = recipe.dishTypes.joinToString(",") { it }

        recipe.extendedIngredients.map {
            finalRecipeObject.Ingredients.add(Ingredients(title = it.name, text = it.original,
                weight = "${it.amount}${it.unit}", image = Constants.INGREDIENTS_HOST_URL + it.image))
        }

        if(recipe.analyzedInstructions.isNotEmpty()){
            recipe.analyzedInstructions.map {
                if(it.steps.isNotEmpty()){
                    it.steps.map { it ->
                        var ingredients = mutableListOf<String>()
                        it.ingredients.map { it.name.split(" ").map { ingredients.add(it) } }
                        vb.tag(
                            Tag.PRESTEP_SUFFIX,
                            Entity.PRESTEP

                        )
                            .e(ingredients.toTypedArray())
                        it.step.split(". ").map {splitStr ->
                            println("main str   $splitStr")

                            var tokens = EnglishTokenizer().tokenize(splitStr.lowercase())
                            //var tags = StandardTagger(EnglishVocabulary.build()).tag(tokens)
                            var tags = StandardTagger(vb.build()).tag(tokens)
                            //IngredientsDetector()
                            //PreStepDetector(),
                            var chunk = RegExChunker(listOf(PreStepDetector()))
                                .chunk(tags).joinToString(" ") {
                                    it.toStringTaggedWords()
                                }

                            if(chunk.isNotEmpty() && chunk.split("\\s+".toRegex()).size > 1){
                                //finalSteps.add(chunk)
                                println("Step -> $chunk")
                                //finalRecipeObject.instruction.add(Instructions(chunk, "0", "30", "100", "30", "100"))
                            }

                            var cookChunk = RegExChunker(listOf(CookStepDetector()))
                                .chunk(tags).joinToString(" ") {
                                    it.toStringTaggedWords()
                                }

                            if(cookChunk.isNotEmpty() && cookChunk.split("\\s+".toRegex()).size > 1){
                                //finalSteps.add(cookChunk)
                                println("Cook Step -> $cookChunk")
//                        if(it.length != null){
//                            var totalTime = 0
//                            if(it.length!!.unit.lowercase() == "minutes"){
//                                totalTime = (it.length!!.number * timePercent) / 100
//                            }else if(it.length!!.unit.lowercase() == "seconds"){
//                                totalTime = it.length!!.number
//                            }
//                            finalRecipeObject.instruction.add(Instructions(chunk, "0", "$totalTime", "100", "$totalTime", "100"))
//                        }else{
//                            finalRecipeObject.instruction.add(Instructions(chunk, "0", "30", "100", "30", "100"))
//                        }
                            }
                            println("----------")
                        }
                    }
                }
            }
        }
        //finalSteps.map { println("steps  -> $it") }
    }

    private fun loadJSONFromAsset(): String? {
        var json: String? = null
        json = try {
            val `is`: InputStream = assets.open("jsonfile.txt")
            val size: Int = `is`.available()
            val buffer = ByteArray(size)
            `is`.read(buffer)
            `is`.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return json
    }

//    var stepFormListener = object : StepperFormListener{
//        override fun onCompletedForm() {
//
//        }
//
//        override fun onCancelledForm() {
//
//        }
//    }
}