package com.kanedasoftware.masterscrobbler.beans

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.io.Serializable

@Entity
class ScrobbleBean : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    var artist: String = ""
    var track: String = ""
    var postTime: Long = 0
    var duration: Long = 0
    var mbid = ""
    var image = ""
    var playtime: Long = 0
    var validated:Boolean = false

    constructor(artist: String, track: String, postTime: Long, duration: Long) {
        this.artist = artist
        this.track = track
        this.postTime = postTime
        this.duration = duration
    }
}