package com.duke.elliot.opicdi.script.audio

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AudioFileMetaData(
        val audioFilePath: String,
        val name: String,
        val duration: Long,
        val date: Long
): Parcelable