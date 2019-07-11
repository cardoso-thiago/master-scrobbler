package com.kanedasoftware.masterscrobbler.enums

enum class EnumPeriod(var value: String, var id: String) {
    WEEK("Last 7 days", "7day"),
    MONTH("Last 30 days", "1month"),
    THREE_MONTH("Last 90 days", "3month"),
    SIX_MONTH("Last 180 days", "6month"),
    YEAR("Last 365 days", "12month"),
    OVERALL("All time", "overall");

    override fun toString(): String {
        return value
    }
}