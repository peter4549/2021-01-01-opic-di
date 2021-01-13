package com.duke.elliot.opicdi.database

data class AudioFileModel(
    val id: Long = 0L,
    val duration: Long,
    val name: String,
    val date: String,
    val uriString: String
)