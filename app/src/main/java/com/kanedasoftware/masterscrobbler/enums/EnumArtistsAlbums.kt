package com.kanedasoftware.masterscrobbler.enums

enum class EnumArtistsAlbums(var value: String, var id: String) {
    ARTISTS("Top Artists", "artists"),
    ALBUMS("Top Albuns", "albums");

    override fun toString(): String {
        return value
    }
}