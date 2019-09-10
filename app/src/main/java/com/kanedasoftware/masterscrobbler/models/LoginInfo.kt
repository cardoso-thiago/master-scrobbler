package com.kanedasoftware.masterscrobbler.models

data class LoginInfo(
        val session: Session = Session()
) {
    data class Session(
            val subscriber: Int = 0,
            val name: String = "",
            val key: String = ""
    )
}