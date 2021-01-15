package com.duke.elliot.opicdi.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import com.duke.elliot.opicdi.main.APPLICATION_DIR_PATH
import com.duke.elliot.opicdi.script.audio.AudioFileMetaData
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


fun Long.toDateFormat(pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(
    this
)

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

fun getAudioFileMetadata(audioFilePath: String): AudioFileMetaData {
    val audioFile = File(audioFilePath)
    val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(audioFilePath)
    return AudioFileMetaData(
        audioFilePath = audioFilePath,
        name = audioFilePath.fileName(),
        duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L,
        date = audioFile.lastModified()
    )
}

@Suppress("DEPRECATION")
fun applicationDir() = Environment.getExternalStorageDirectory().toString() + "/$APPLICATION_DIR_PATH"

fun String.fileName() = this.substring(this.lastIndexOf("/"))

@SuppressLint("Recycle")
@RequiresApi(Build.VERSION_CODES.O)
fun contentUriToPath(context: Context, contentUri: Uri): String? {
    val cursor = context.contentResolver
        .query(contentUri, null, null, null) ?: return null

    cursor.moveToNext()
    val path = cursor.getString(cursor.getColumnIndex("_data"));

    cursor.close()
    return path
}