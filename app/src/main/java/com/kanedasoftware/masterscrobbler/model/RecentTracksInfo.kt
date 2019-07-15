package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class RecentTracksInfo(
    @SerializedName("recenttracks")
    val recenttracks: Recenttracks
) {
    data class Recenttracks(
        @SerializedName("@attr")
        val attr: Attr,
        @SerializedName("track")
        val track: List<Track>
    ) {
        data class Attr(
            @SerializedName("page")
            val page: String,
            @SerializedName("perPage")
            val perPage: String,
            @SerializedName("total")
            val total: String,
            @SerializedName("totalPages")
            val totalPages: String,
            @SerializedName("user")
            val user: String
        )

        data class Track(
            @SerializedName("album")
            val album: Album,
            @SerializedName("artist")
            val artist: Artist,
            @SerializedName("@attr")
            val attr: Attr,
            @SerializedName("date")
            val date: Date,
            @SerializedName("image")
            val image: List<Image>,
            @SerializedName("loved")
            val loved: String,
            @SerializedName("mbid")
            val mbid: String,
            @SerializedName("name")
            val name: String,
            @SerializedName("streamable")
            val streamable: String,
            @SerializedName("url")
            val url: String
        ) {
            data class Artist(
                @SerializedName("image")
                val image: List<Image>,
                @SerializedName("mbid")
                val mbid: String,
                @SerializedName("name")
                val name: String,
                @SerializedName("url")
                val url: String
            ) {
                data class Image(
                    @SerializedName("size")
                    val size: String,
                    @SerializedName("#text")
                    val text: String
                )
            }

            data class Album(
                @SerializedName("mbid")
                val mbid: String,
                @SerializedName("#text")
                val text: String
            )

            data class Attr(
                @SerializedName("nowplaying")
                val nowplaying: String
            )

            data class Date(
                @SerializedName("#text")
                val text: String,
                @SerializedName("uts")
                val uts: String
            )

            data class Image(
                @SerializedName("size")
                val size: String,
                @SerializedName("#text")
                val text: String
            )
        }
    }
}