package com.kanedasoftware.masterscrobbler.utils

class Constants {

    companion object {
        const val API_KEY = "16f108a2f567cee9e15a2f8f6ea0ccac"
        const val SHARED_SECRET = "7ff3b10767ba3a7d1efe22562c76918b"
        const val LOG_TAG = "MASTER_SCROBBLER"
        const val QUIET_NOTIFICATION_CHANNEL = "masterScrobblerQuietNotificationService"
        const val NOTIFICATION_CHANNEL = "masterScrobblerNotificationService"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_NEW_PLAYER_ID = 2
        const val NOTIFICATION_NO_PLAYER_ID = 3
        const val START_SERVICE = "startService"
        const val STOP_SERVICE = "stopService"
        const val SCROBBLE_PENDING_SERVICE = "scrobblePending"

        const val ARTISTS = "artists"
        const val ALBUMS = "albums"

        const val API_GET_MOBILE_SESSION = "auth.getMobileSession"
        const val API_UPDATE_NOW_PLAYING = "track.updateNowPlaying"
        const val API_TRACK_LOVE = "track.love"
        const val API_TRACK_UNLOVE = "track.unlove"
        const val API_TRACK_SCROBBLE = "track.scrobble"
        const val SECURE_SESSION_TAG = "session"
        const val SECURE_USER_TAG = "user"
    }
}