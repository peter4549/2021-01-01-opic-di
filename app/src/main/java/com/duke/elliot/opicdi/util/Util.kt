package com.duke.elliot.opicdi.util

import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateFormat(pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(this)
fun getCurrentTime() = Calendar.getInstance().timeInMillis