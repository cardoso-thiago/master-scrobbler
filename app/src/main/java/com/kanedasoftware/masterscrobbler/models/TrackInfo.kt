package com.kanedasoftware.masterscrobbler.models

import com.google.gson.annotations.SerializedName

data class TrackInfo(
        val results: Results
) {
    data class Results(
            @SerializedName("opensearch:Query")
            val opensearchQuery: OpensearchQuery,
            @SerializedName("opensearch:totalResults")
            val opensearchTotalResults: String,
            @SerializedName("opensearch:startIndex")
            val opensearchStartIndex: String,
            @SerializedName("opensearch:itemsPerPage")
            val opensearchItemsPerPage: String,
            val trackmatches: Trackmatches,
            @SerializedName("")
            val x: Attr
    ) {
        data class Trackmatches(
                val track: List<Track>
        ) {
            data class Track(
                    val name: String,
                    val artist: String,
                    val url: String,
                    val streamable: String,
                    val listeners: String,
                    val image: List<Image>,
                    val mbid: String
            ) {
                data class Image(
                        @SerializedName("#text")
                        val text: String,
                        val size: String
                )
            }
        }

        data class OpensearchQuery(
                @SerializedName("#text")
                val text: String,
                val role: String,
                val startPage: String
        )

        class Attr
    }
}