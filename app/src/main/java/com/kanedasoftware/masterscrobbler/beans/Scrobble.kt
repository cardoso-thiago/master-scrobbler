package com.kanedasoftware.masterscrobbler.beans

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
class Scrobble(var artist: String, var track: String, var postTime: Long, var duration: Long) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    var mbid = ""
    var album = ""
    var playtime: Long = 0
    var validated:Boolean = false
    var validationError:Boolean = false
    var originalArtist:String = ""
    var originalTrack:String = ""
}