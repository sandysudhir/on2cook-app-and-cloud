package com.invent.ontocook.utils


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Date
import java.util.UUID

/**
 *   Convert string to model class
 *
 */
inline fun <reified T> String.toObject(): T? {
    justTry {
        return Gson().fromJson(this, T::class.java)
    }
    return null
}

/**
 *  Convert Model to String
 */
fun Any.toJson(): String? {
    justTry {
        return Gson().toJson(this)
    }
    return null
}

/**
 *   Convert string to arraylist of model class
 *
 */
inline fun <reified T> String.toObjectArrayList(): ArrayList<T>? {
    justTry {
        return Gson().fromJson(this, object : TypeToken<ArrayList<T?>?>() {}.type)
    }
    return null
}

/*inline fun <reified T> String.toObjectArrayList(): T? {
    justTry {
        return Gson().fromJson(this, object : TypeToken<T>() {}.type)
    }
    return null
}*/

/**
 * Convert List to JSONArray
 */
fun <T> convertListToJsonArray(list: List<T>?): JSONArray {
    val jsonArray = JSONArray()
    val gson = Gson()

    list?.forEach { item ->
        val json = gson.toJson(item)
        jsonArray.put(JSONObject(json))
    }

    return jsonArray
}

/*fun List<Any>?.notNullAndNotEmpty(): Boolean {
    return this.notNull() && this!!.isNotEmpty()
}*/

fun <T> Collection<T>?.notNullAndNotEmpty(): Boolean {
    return this.notNull() && this!!.isNotEmpty()
}

fun String?.notNullAndNotEmpty(): Boolean {
    return this.notNull() && this!!.isNotEmpty() && this.lowercase() != "null"
}

inline fun <T : Any, R> T?.withNotNull(block: (T) -> R): R? {
    return this?.let(block)
}

/**
 * Get parcelable extension from intent
 * */
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayList(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayList(key)
}

inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
}


/**
 * Implementation of lazy that is not thread safe. Useful when you know what thread you will be
 * executing on and are not worried about synchronization.
 */
fun <T> lazyFast(operation: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE) {
    operation()
}

/** Convenience for callbacks/listeners whose return value indicates an event was consumed. */
inline fun consume(f: () -> Unit): Boolean {
    f()
    return true
}

fun Int.toDoubleDigitString(): String {
    return String.format("%02d", this)
}

/**
 * Allows calls like
 *
 * `viewGroup.inflate(R.layout.foo)`
 */
fun ViewGroup.inflate(@LayoutRes layout: Int, attachToRoot: Boolean = false): View {
    return LayoutInflater.from(context).inflate(layout, this, attachToRoot)
}

/**
 * Allows calls like
 *
 * `supportFragmentManager.inTransaction { add(...) }`
 */
inline fun FragmentManager.inTransaction(func: FragmentTransaction.() -> FragmentTransaction) {
    beginTransaction().func().commitAllowingStateLoss()
}


// endregion
// region Parcelables, Bundles

/** Write an enum value to a Parcel */
fun <T : Enum<T>> Parcel.writeEnum(value: T) = writeString(value.name)

/** Read an enum value from a Parcel */
inline fun <reified T : Enum<T>> Parcel.readEnum(): T = enumValueOf(readString()!!)

/** Write an enum value to a Bundle */
fun <T : Enum<T>> Bundle.putEnum(key: String, value: T) = putString(key, value.name)

/** Read an enum value from a Bundle */
inline fun <reified T : Enum<T>> Bundle.getEnum(key: String): T = enumValueOf(getString(key)!!)

/** Write a boolean to a Parcel (copied from Parcel, where this is @hidden). */
fun Parcel.writeBoolean(value: Boolean) = writeInt((value) then { 1 } ?: 0)

/** Read a boolean from a Parcel (copied from Parcel, where this is @hidden). */
fun Parcel.readBoolean() = readInt() != 0

// endregion
// region LiveData

/** Uses `Transformations.map` on a LiveData */
fun <X, Y> LiveData<X>.map(body: (X) -> Y): LiveData<Y> {
    return this.map(body)
}

/** Uses `Transformations.switchMap` on a LiveData */
fun <X, Y> LiveData<X>.switchMap(body: (X) -> LiveData<Y>): LiveData<Y> {
    return this.switchMap(body)
}

fun <T> MutableLiveData<T>.setValueIfNew(newValue: T) {
    if (this.value != newValue) value = newValue
}

fun <T> MutableLiveData<T>.postValueIfNew(newValue: T) {
    if (this.value != newValue) postValue(newValue)
}
// endregion

/**
 * Helper to force a when statement to assert all options are matched in a when statement.
 *
 * By default, Kotlin doesn't care if all branches are handled in a when statement. However, if you
 * use the when statement as an expression (with a value) it will force all cases to be handled.
 *
 * This helper is to make a lightweight way to say you meant to match all of them.
 *
 * Usage:
 *
 * ```
 * when(sealedObject) {
 *     is OneType -> //
 *     is AnotherType -> //
 * }.checkAllMatched
 */
val <T> T.checkAllMatched: T
    get() = this

// region UI utils

/**
 * Retrieves a color from the theme by attributes. If the attribute is not defined, a fall back
 * color will be returned.
 */
@ColorInt
fun Context.getThemeColor(
    @AttrRes attrResId: Int,
    @ColorRes fallbackColorResId: Int,
): Int {
    val tv = TypedValue()
    return ((theme.resolveAttribute(attrResId, tv, true)) then { tv.data })
        ?: ContextCompat.getColor(
            this,
            fallbackColorResId
        )

}

// endregion

/**
 * Helper to throw exceptions only in Debug builds, logging a warning otherwise.
 */

fun <T : Any?> MutableLiveData<T>.default(initialValue: T) = apply { setValue(initialValue) }

/**
 * Get value in dp
 */
val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

/**
 * Get value in px
 */
val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()


infix fun <T : Any> Boolean.then(param: () -> T): T? = if (this) param() else null


fun Double?.decimalCeil(): Double {
    val df = DecimalFormat("#.##")
    df.roundingMode = RoundingMode.CEILING
    return df.format(this).toDouble()
}

fun Double.decimalFloor(): Double {
    val df = DecimalFormat("#.##")
    df.roundingMode = RoundingMode.FLOOR
    return df.format(this).toDouble()
}


fun Fragment.loadChlidFragment(placeholder: Int, fragment: Fragment) {
    childFragmentManager.beginTransaction().replace(placeholder, fragment).commit()
}

fun createFileName(imagePath: String): String {
    return Date().time.toString() + "." + MimeTypeMap.getFileExtensionFromUrl(imagePath)
}

// Function for hiding keyboard
fun Context.hideKeyboard(view: View?) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
}

// Function for showing keyboard
fun Context.showKeyboard(view: View?) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, 0)
}

fun Context.isKeyboardHide(): Boolean {
    val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.isAcceptingText
}

/**Show Toast message*/
fun Any.showToast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

fun Context.showToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Any.showLongToast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

/**Null value check*/
fun UUID?.nullSafe(): UUID {
    return this ?: UUID.randomUUID()
}

fun String?.nullSafe(defaultValue: String = ""): String {
    return this ?: defaultValue
}

fun Int?.nullSafe(defaultValue: Int = 0): Int {
    return this ?: defaultValue
}

fun Float?.nullSafe(defaultValue: Float = 0.0f): Float {
    return this ?: defaultValue
}

fun Long?.nullSafe(defaultValue: Long = 0L): Long {
    return this ?: defaultValue
}

fun Double?.nullSafe(defaultValue: Double = 0.0): Double {
    return this ?: defaultValue
}

fun BigDecimal?.nullSafe(defaultValue: BigDecimal = BigDecimal(0)): BigDecimal {
    return this ?: defaultValue
}

fun Boolean?.nullSafe(defaultValue: Boolean = false): Boolean {
    return this ?: defaultValue
}

/*fun Context.getImageMultipart(uri: Uri, name: String): MultipartBody.Part {
    val file = ImageUtilNew.from(this, uri)
    var selectedFile = saveBitmapToFile(file)

    val requestFile: RequestBody =
        selectedFile!!.asRequestBody("multipart/form-data".toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(name, selectedFile.name, requestFile)
}*/

// Share app function
/*fun Context.shareApp() {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name))
        var shareMessage = "\nLet me recommend you this application\n\n"
        shareMessage =
            "${shareMessage}https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}\n\n".trimIndent()
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
        startActivity(Intent.createChooser(shareIntent, "choose one"))

    } catch (e: Exception) {
        e.printStackTrace()
    }
}*/

// Open link in browser
fun Context.openUrlInBrowser(url: String?) {
    val openURL = Intent(Intent.ACTION_VIEW)
    openURL.data = Uri.parse(url?.fixHttp())
    startActivity(openURL)
}

// Fix url protocol
fun String.fixHttp(): String {
    return if (this.startsWith("http") || this.startsWith("https")) this else "https://$this"
}