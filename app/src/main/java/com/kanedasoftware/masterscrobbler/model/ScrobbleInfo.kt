package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class ScrobbleInfo(
        val scrobbles: Scrobbles = Scrobbles()
) {
    data class Scrobbles(
            @SerializedName("")
            val scrobble: Scrobble = Scrobble()
    ) {
        data class Scrobble(
                val artist: Artist = Artist(),
                val ignoredMessage: IgnoredMessage = IgnoredMessage(),
                val albumArtist: AlbumArtist = AlbumArtist(),
                val timestamp: String = "",
                val album: Album = Album(),
                val track: Track = Track()
        ) {
            data class Artist(
                    val corrected: String = "",
                    @SerializedName("#text")
                    val text: String = ""
            )

            data class Album(
                    val corrected: String = ""
            )

            data class IgnoredMessage(
                    val code: String = "",
                    @SerializedName("#text")
                    val text: String = ""
            )

            data class AlbumArtist(
                    val corrected: String = "",
                    @SerializedName("#text")
                    val text: String = ""
            )

            data class Track(
                    val corrected: String = "",
                    @SerializedName("#text")
                    val text: String = ""
            )
        }
    }
}