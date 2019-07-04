package com.kanedasoftware.masterscrobbler.beans

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.io.Serializable

@Entity
class ScrobbleBean(var artist: String, var track: String, var postTime: Long, var duration: Long) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    var mbid = ""
    var image = ""
    var playtime: Long = 0
    var validated:Boolean = false
}