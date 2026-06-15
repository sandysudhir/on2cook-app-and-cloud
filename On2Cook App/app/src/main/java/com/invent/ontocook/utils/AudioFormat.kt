package com.invent.ontocook.utils

enum class AudioFormat {
    AAC, MP3, M4A, WMA, WAV, FLAC;

    val format: String
        get() = name.lowercase()
}