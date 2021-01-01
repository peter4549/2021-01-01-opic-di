package com.duke.elliot.opicdi.database

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

@Entity(tableName = "script")
@Parcelize
data class Script(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var lastModifiedTime: Long,
    var title: String,
    var script: String,
    var backgroundSurveys: Array<Int>,
    var audioFileUris: Array<String>,
): Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Script

        if (id != other.id) return false
        if (lastModifiedTime != other.lastModifiedTime) return false
        if (title != other.title) return false
        if (script != other.script) return false
        if (!backgroundSurveys.contentEquals(other.backgroundSurveys)) return false
        if (!audioFileUris.contentEquals(other.audioFileUris)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + lastModifiedTime.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + script.hashCode()
        result = 31 * result + backgroundSurveys.contentHashCode()
        result = 31 * result + audioFileUris.contentHashCode()
        return result
    }
}