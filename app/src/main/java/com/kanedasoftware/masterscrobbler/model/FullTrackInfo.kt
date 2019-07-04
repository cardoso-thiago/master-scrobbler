package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class FullTrackInfo(
        val track: Track = Track()
) {
    data class Track(
            val name: String = "",
            val mbid: String = "",
            val url: String = "",
            val duration: String = "",
            val streamable: Streamable = Streamable(),
            val listeners: String = "",
            val playcount: String = "",
            val artist: Artist = Artist(),
            val album: Album = Album(),
            val toptags: Toptags = Toptags()
    ) {
        data class Toptags(
                val tag: List<Tag> = listOf()
        ) {
            data class Tag(
                    val name: String = "",
                    val url: String = ""
            )
        }

        data class Streamable(
                @SerializedName("#text")
                val text: String = "",
                val fulltrack: String = ""
        )

        data class Artist(
                val name: String = "",
                val mbid: String = "",
                val url: String = ""
        )

        data class Album(
                val artist: String = "",
                val title: String = "",
                val mbid: String = "",
                val url: String = "",
                val image: List<Image> = listOf()
        ) {
            data class Image(
                    @SerializedName("#text")
                    val text: String = "",
                    val size: String = ""
            )
        }
    }
}