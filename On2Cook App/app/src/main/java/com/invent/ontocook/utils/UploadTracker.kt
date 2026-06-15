package com.invent.ontocook.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the set of raw filenames that have already been successfully uploaded
 * to Supabase, keyed per device MAC address.
 *
 * Storage format (SharedPreferences):
 *   Key  : "uploaded_${mac}"
 *   Value: Set<String> of rawFileName strings (e.g. "device_log_17_2_26_14.txt")
 *
 * Usage:
 *   val tracker = UploadTracker(context)
 *   if (!tracker.isUploaded(mac, fileName)) {
 *       // upload …
 *       tracker.markUploaded(mac, fileName)
 *   }
 */
class UploadTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns true if [rawFileName] for [mac] has already been uploaded. */
    fun isUploaded(mac: String, rawFileName: String): Boolean {
        return prefs.getStringSet(key(mac), emptySet())?.contains(rawFileName) == true
    }

    /** Persists [rawFileName] for [mac] as uploaded. Thread-safe via apply(). */
    fun markUploaded(mac: String, rawFileName: String) {
        val current = prefs.getStringSet(key(mac), emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(rawFileName)
        prefs.edit().putStringSet(key(mac), current).apply()
    }

    /** Returns all uploaded filenames for [mac] — useful for debugging. */
    fun getUploaded(mac: String): Set<String> =
        prefs.getStringSet(key(mac), emptySet())?.toSet() ?: emptySet()

    /** Clears upload history for a specific device (e.g. factory reset flow). */
    fun clearDevice(mac: String) {
        prefs.edit().remove(key(mac)).apply()
    }

    private fun key(mac: String) = "uploaded_${mac.replace(":", "_")}"

    companion object {
        private const val PREFS_NAME = "supabase_upload_tracker"
    }
}