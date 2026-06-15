package com.masoudss.lib

import android.content.Context
import linc.com.amplituda.Amplituda
import linc.com.amplituda.callback.AmplitudaSuccessListener
import java.io.File

object WaveformOptions {

    private var amplituda: Amplituda? = null

    @JvmStatic
    fun init(context: Context) {
        if(amplituda == null) {
            amplituda = Amplituda(context)
        }
    }

    @JvmStatic
    fun getSampleFrom(file: File, onSuccess:(samples: IntArray) -> Unit) {
        amplituda!!.processAudio(file)
            .get(AmplitudaSuccessListener {
                onSuccess(it.amplitudesAsList().toIntArray())
            })
    }

    @JvmStatic
    fun getSampleFrom(path: String, onSuccess: (IntArray) -> Unit) {
        amplituda!!.processAudio(path)
            .get(AmplitudaSuccessListener {
                onSuccess(it.amplitudesAsList().toIntArray())
            })
    }

}