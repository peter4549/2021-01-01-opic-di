package com.duke.elliot.opicdi.util

import android.content.res.Resources
import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateFormat(pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(this)

fun getCurrentTime() = Calendar.getInstance().timeInMillis

fun Int.isNotZero(): Boolean = this != 0

fun Int.toPx(): Float {
    return this * Resources.getSystem().displayMetrics.density
}

fun Float.toPx(): Float {
    return this * Resources.getSystem().displayMetrics.density
}

fun Int.toDp(): Float {
    return this / Resources.getSystem().displayMetrics.density
}

fun Float.toDp(): Float {
    return this / Resources.getSystem().displayMetrics.density
}