package com.kanedasoftware.masterscrobbler.utils

class Constants {

    companion object {
        const val API_KEY = "16f108a2f567cee9e15a2f8f6ea0ccac"
        const val FAN_ART_API_KEY = "a9326bf1a6120c139bc1a0f2b0f05667"
        const val SHARED_SECRET = "7ff3b10767ba3a7d1efe22562c76918b"
        const val LOG_TAG = "MASTER_SCROBBLER"
        const val NOTIFICATION_CHANNEL = "masterScrobblerNotificationService"
        const val NOTIFICATION_ID = 1

        const val API_GET_MOBILE_SESSION = "auth.getMobileSession"
        const val API_UPDATE_NOW_PLAYING = "track.updateNowPlaying"
        const val API_TRACK_SCROBBLE = "track.scrobble"
    }
}