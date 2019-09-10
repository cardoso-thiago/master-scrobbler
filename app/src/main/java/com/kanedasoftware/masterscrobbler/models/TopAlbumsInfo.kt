package com.kanedasoftware.masterscrobbler.models


import com.google.gson.annotations.SerializedName

data class TopAlbumsInfo(
    @SerializedName("topalbums")
    val topalbums: Topalbums
) {
    data class Topalbums(
        @SerializedName("album")
        val album: List<Album>,
        @SerializedName("@attr")
        val attr: Attr
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

        data class Album(
            @SerializedName("artist")
            val artist: Artist,
            @SerializedName("@attr")
            val attr: Attr,
            @SerializedName("image")
            val image: List<Image>,
            @SerializedName("mbid")
            val mbid: String,
            @SerializedName("name")
            val name: String,
            @SerializedName("playcount")
            val playcount: String,
            @SerializedName("url")
            val url: String
        ) {
            data class Attr(
                @SerializedName("rank")
                val rank: String
            )

            data class Artist(
                @SerializedName("mbid")
                val mbid: String,
                @SerializedName("name")
                val name: String,
                @SerializedName("url")
                val url: String
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