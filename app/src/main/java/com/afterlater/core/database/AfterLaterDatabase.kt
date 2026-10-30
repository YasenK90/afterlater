package com.afterlater.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedItemEntity::class,
        ReviewEventEntity::class,
        TagEntity::class,
        SavedItemTagEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AfterLaterDatabase : RoomDatabase() {

    abstract fun savedItemDao(): SavedItemDao

    abstract fun reviewEventDao(): ReviewEventDao

    abstract fun tagDao(): TagDao

    companion object {
        private const val NAME = "afterlater.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE saved_items ADD COLUMN surfaceHint TEXT NOT NULL DEFAULT 'QUEUE'")
                database.execSQL("ALTER TABLE saved_items ADD COLUMN expiresAt INTEGER")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` " +
                        "(`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`name`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_item_tags` " +
                        "(`itemId` TEXT NOT NULL, `tagName` TEXT NOT NULL, " +
                        "PRIMARY KEY(`itemId`, `tagName`), " +
                        "FOREIGN KEY(`itemId`) REFERENCES `saved_items`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_item_tags_itemId` ON `saved_item_tags` (`itemId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_item_tags_tagName` ON `saved_item_tags` (`tagName`)",
                )
            }
        }

        fun build(context: Context): AfterLaterDatabase =
            Room.databaseBuilder(context, AfterLaterDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
