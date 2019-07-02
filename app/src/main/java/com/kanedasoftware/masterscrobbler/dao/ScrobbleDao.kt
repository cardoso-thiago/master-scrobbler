package com.kanedasoftware.masterscrobbler.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean

@Dao
interface ScrobbleDao {

    @Insert
    fun add(vararg scrobble:ScrobbleBean)
}