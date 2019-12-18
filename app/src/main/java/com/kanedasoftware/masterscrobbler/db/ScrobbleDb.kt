package com.kanedasoftware.masterscrobbler.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kanedasoftware.masterscrobbler.beans.Scrobble
import com.kanedasoftware.masterscrobbler.dao.ScrobbleDao

@Database(entities = [Scrobble::class], version = 9)
abstract class ScrobbleDb : RoomDatabase() {
    abstract fun scrobbleDao(): ScrobbleDao
}