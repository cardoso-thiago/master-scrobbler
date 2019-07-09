package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class UserInfo(
    val user: User = User()
) {
    data class User(
        val playlists: String = "",
        val playcount: String = "",
        val gender: String = "",
        val name: String = "",
        val subscriber: String = "",
        val url: String = "",
        val country: String = "",
        val image: List<Image> = listOf(),
        val registered: Registered = Registered(),
        val type: String = "",
        val age: String = "",
        val bootstrap: String = "",
        val realname: String = ""
    ) {
        data class Image(
            val size: String = "",
            @SerializedName("#text")
            val text: String = ""
        )

        data class Registered(
            val unixtime: String = "",
            @SerializedName("#text")
            val text: Int = 0
        )
    }
}