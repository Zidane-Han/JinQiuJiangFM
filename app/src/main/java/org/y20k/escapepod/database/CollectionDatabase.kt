/*
 * CollectionDatabase.kt
 * Implements the CollectionDatabase class
 * A CollectionDatabase class stores podcasts, episodes and the playback queue in a Room database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.y20k.escapepod.database.daos.EpisodeDao
import org.y20k.escapepod.database.daos.EpisodeDescriptionDao
import org.y20k.escapepod.database.daos.PodcastDao
import org.y20k.escapepod.database.daos.PodcastDescriptionDao
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.database.wrappers.EpisodeLatestView
import java.util.GregorianCalendar


/*
 * CollectionDatabase class
 */
@Database(
    version = 8,
    entities = [Podcast::class, PodcastDescription::class, Episode::class, EpisodeDescription::class],
    views = [EpisodeLatestView::class],
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = CollectionDatabase.CustomMigrationSpec2::class), AutoMigration(from = 4, to = 5)],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CollectionDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    abstract fun podcastDescriptionDao(): PodcastDescriptionDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun episodeDescriptionDao(): EpisodeDescriptionDao

    /* Specifies the database changes from version 1 to version 2 */
    @RenameColumn(fromColumnName = "playback_state", toColumnName = "is_playing", tableName = "episodes")
    class CustomMigrationSpec2: AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            super.onPostMigrate(db)
            db.execSQL("UPDATE episodes SET is_playing = 0")
        }
    }


    /* Object used to create an offer an instance of the collection database */
    companion object {

        var INSTANCE: CollectionDatabase? = null

        fun getInstance(context: Context): CollectionDatabase {
            synchronized(this) {
                var instance: CollectionDatabase? = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(context.applicationContext, CollectionDatabase::class.java, "collection_database").apply {
                        addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                        // fallbackToDestructiveMigration() // uncomment to test migrations
                    }.build()
                    INSTANCE = instance
                }
                return instance
            }
        }


        /* Specifies the database changes from version 2 to version 3 (auto-migration did not work for these changes, probably a bug) */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentTimestamp: Long = GregorianCalendar.getInstance().timeInMillis
                db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `auto_download_episodes` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE `podcasts` SET `auto_download_episodes` = 1")
                db.execSQL("ALTER TABLE `episodes` ADD COLUMN `last_listened` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `episodes` SET `last_listened` = $currentTimestamp")
                db.execSQL("DROP VIEW IF EXISTS EpisodeMostRecentView")
                db.execSQL("DROP VIEW IF EXISTS EpisodeLatestView")
                db.execSQL("CREATE VIEW `EpisodeLatestView` AS SELECT * FROM episodes e WHERE (SELECT Count(*) FROM episodes e1 WHERE e1.episode_remote_podcast_feed_location = e.episode_remote_podcast_feed_location AND e1.publication_date >= e.publication_date) <= 1")
            }
        }

        /* Specifies the database changes from version 3 to version 4 (auto-migration did not work for these changes, probably a bug) */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `episode_list_display_filter` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `podcasts` SET `episode_list_display_filter` = 0")
            }
        }

        /* Specifies the database changes from version 5 to version 6 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN content_type TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE episodes SET remote_cover_file_location = COALESCE((SELECT p.remote_image_file_location FROM podcasts p WHERE p.remote_podcast_feed_location = episodes.episode_remote_podcast_feed_location), '')")            }
        }

        /* Specifies the database changes from version 6 to version 7 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `cover_version` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `podcasts` SET `cover_version` = 0")
                db.execSQL("ALTER TABLE `episodes` ADD COLUMN `cover_version` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `episodes` SET `cover_version` = 0")
            }
        }

        /* Specifies the database changes from version 7 to version 8 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `episode_list_search_term` TEXT NOT NULL DEFAULT ''")
            }
        }

    }

}
