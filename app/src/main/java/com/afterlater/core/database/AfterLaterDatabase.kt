package com.afterlater.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SavedItemEntity::class, ReviewEventEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AfterLaterDatabase : RoomDatabase() {

    abstract fun savedItemDao(): SavedItemDao

    abstract fun reviewEventDao(): ReviewEventDao

    companion object {
        private const val NAME = "afterlater.db"

        fun build(context: Context): AfterLaterDatabase =
            Room.databaseBuilder(context, AfterLaterDatabase::class.java, NAME)
                .build()
    }
}
