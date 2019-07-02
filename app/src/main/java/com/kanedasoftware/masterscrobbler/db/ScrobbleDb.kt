package com.kanedasoftware.masterscrobbler.db

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean
import com.kanedasoftware.masterscrobbler.dao.ScrobbleDao

@Database(entities = [ScrobbleBean::class], version = 2)
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