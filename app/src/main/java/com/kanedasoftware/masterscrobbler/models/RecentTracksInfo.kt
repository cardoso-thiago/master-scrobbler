package com.kanedasoftware.masterscrobbler.models


import com.google.gson.annotations.SerializedName

data class RecentTracksInfo(
    val recenttracks: Recenttracks = Recenttracks()
) {
    data class Recenttracks(
        @SerializedName("@attr")
        val attr: Attr = Attr(),
        val track: List<Track> = listOf()
    ) {
        data class Track(
            @SerializedName("@attr")
            val attr: Attr? = Attr(),
            val artist: Artist = Artist(),
            val mbid: String = "",
            val image: List<Image> = listOf(),
            val url: String = "",
            val streamable: String = "",
            val album: Album = Album(),
            val name: String = "",
            val loved: String = "",
            val date: Date = Date()
        ) {
            data class Image(
                val size: String = "",
                @SerializedName("#text")
                val text: String = ""
            )

            data class Artist(
                val url: String = "",
                val mbid: String = "",
                val image: List<Image> = listOf(),
                val name: String = ""
            ) {
                data class Image(
                    val size: String = "",
                    @SerializedName("#text")
                    val text: String = ""
                )
            }

            data class Date(
                val uts: String = "",
                @SerializedName("#text")
                val text: String = ""
            )

            data class Attr(
                val nowplaying: String = ""
            )

            data class Album(
                val mbid: String = "",
                @SerializedName("#text")
                val text: String = ""
            )
        }

        data class Attr(
            val page: String = "",
            val perPage: String = "",
            val user: String = "",
            val total: String = "",
            val totalPages: String = ""
        )
    }
}