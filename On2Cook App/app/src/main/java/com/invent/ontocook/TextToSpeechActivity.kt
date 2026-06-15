package com.invent.ontocook

import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.File
import java.util.*


class TextToSpeechActivity : AppCompatActivity() {
    var tts: TextToSpeech? = null
    var voice : Voice? = null
    var destinationFile: File? = null
    var audioNames: String = "Induction Started"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_to_speech)
        tts = TextToSpeech(this) { i ->
            if (i == TextToSpeech.SUCCESS) {
                Log.e("TAG", "onCreate: ${Locale.getDefault()}")
                Log.e("TAG", "onCreate: ${Locale.getDefault().language}")
                Log.e("TAG", "onCreate: ${Locale.getDefault().country}")
                Log.e("TAG", "onCreate: ${Resources.getSystem().configuration.locales}")


                list.clear()
                tts?.voices?.forEachIndexed { index, voice1 ->
                    if (voice1.name.contains("hi-IN", true)) {
                        Log.e("TAG", "generateAudioFile: $index voice: $voice1")
                        Log.e("TAG", "generateAudioFile: voice: ${Gson().toJson(voice1)}")
                        list.add(voice1)
                    }
                }

            }
        }
//        val installIntent = Intent()
//        installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
//        startActivity(installIntent)

        Handler(Looper.getMainLooper()).postDelayed({
            for (i in 0 until list.size) {
                generateGuj(audioNames, i)
            }

        },1500)


    }

    val list: kotlin.collections.ArrayList<Voice> = ArrayList()
    private fun generateGuj(audioNames: String, ind: Int) {
        val a: MutableSet<String> = HashSet()
        a.add("FEMALE")
        voice = list[ind]
        tts?.voice = voice
//        tts?.setPitch(0.5f)
        destinationFile =
            File(OnToCookApplication.instance.externalCacheDir, "$audioNames${list[ind].name}.wav")
        tts!!.synthesizeToFile(audioNames, null, destinationFile, audioNames)
    }

}