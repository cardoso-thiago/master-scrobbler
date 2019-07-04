package com.kanedasoftware.masterscrobbler.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean

@Dao
interface ScrobbleDao {

    @Insert
    fun add(vararg scrobble: ScrobbleBean)

    @Delete
    fun delete(vararg scrobbleBean: ScrobbleBean)

    @Query("SELECT * FROM ScrobbleBean")
    fun getAll(): Array<ScrobbleBean>
}