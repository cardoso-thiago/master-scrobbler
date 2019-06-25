package com.kanedasoftware.masterscrobbler.model

import com.google.gson.annotations.SerializedName

data class UpdateNowPlayingInfo(
        val nowplaying: Nowplaying = Nowplaying()
) {
    data class Nowplaying(
            val artist: Artist = Artist(),
            val ignoredMessage: IgnoredMessage = IgnoredMessage(),
            val album: Album = Album(),
            val albumArtist: AlbumArtist = AlbumArtist(),
            val track: Track = Track()
    ) {
        data class Track(
                val corrected: String = "",
                @SerializedName("#text")
                val text: String = ""
        )

        data class Artist(
                val corrected: String = "",
                @SerializedName("#text")
                val text: String = ""
        )

        data class IgnoredMessage(
                val code: String = "",
                @SerializedName("#text")
                val text: String = ""
        )

        data class Album(
                val corrected: String = "",
                @SerializedName("#text")
                val text: String = ""
        )

        data class AlbumArtist(
                val corrected: String = "",
                @SerializedName("#text")
                val text: String = ""
        )
    }
}