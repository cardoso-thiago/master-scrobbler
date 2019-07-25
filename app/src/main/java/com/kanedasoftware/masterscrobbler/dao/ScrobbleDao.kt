package com.kanedasoftware.masterscrobbler.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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