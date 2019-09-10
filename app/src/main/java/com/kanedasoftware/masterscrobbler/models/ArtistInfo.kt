package com.kanedasoftware.masterscrobbler.models


import com.google.gson.annotations.SerializedName

data class ArtistInfo(
    val results: Results = Results()
) {
    data class Results(
        @SerializedName("opensearch:Query")
        val opensearchQuery: OpensearchQuery = OpensearchQuery(),
        @SerializedName("opensearch:totalResults")
        val opensearchTotalResults: String = "",
        @SerializedName("opensearch:startIndex")
        val opensearchStartIndex: String = "",
        @SerializedName("opensearch:itemsPerPage")
        val opensearchItemsPerPage: String = "",
        val artistmatches: Artistmatches = Artistmatches(),
        @SerializedName("@attr")
        val attr: Attr = Attr()
    ) {
        data class Artistmatches(
            val artist: List<Artist> = listOf()
        ) {
            data class Artist(
                val name: String = "",
                val listeners: String = "",
                val mbid: String = "",
                val url: String = "",
                val streamable: String = "",
                val image: List<Image> = listOf()
            ) {
                data class Image(
                    @SerializedName("#text")
                    val text: String = "",
                    val size: String = ""
                )
            }
        }

        data class OpensearchQuery(
            @SerializedName("#text")
            val text: String = "",
            val role: String = "",
            val searchTerms: String = "",
            val startPage: String = ""
        )

        data class Attr(
            @SerializedName("for")
            val forX: String = ""
        )
    }
}