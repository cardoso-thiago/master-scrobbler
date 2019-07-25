package com.kanedasoftware.masterscrobbler.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean
import com.kanedasoftware.masterscrobbler.dao.ScrobbleDao

@Database(entities = [ScrobbleBean::class], version = 6)
abstract class ScrobbleDb : RoomDatabase() {
    abstract fun scrobbleDao(): ScrobbleDao

    companion object : SingletonHolder<ScrobbleDb, Context>({
        Room.databaseBuilder(it.applicationContext,
                ScrobbleDb::class.java, "MasterScrobbler.db")
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()
    })
}