package com.kanedasoftware.masterscrobbler.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kanedasoftware.masterscrobbler.beans.Scrobble

@Dao
interface ScrobbleDao {

    @Insert
    fun add(vararg scrobble: Scrobble)

    @Delete
    fun delete(vararg scrobble: Scrobble)

    @Query("SELECT * FROM Scrobble")
    fun getAll(): Array<Scrobble>
}