package com.afterlater.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a real version-3 database file with saved rows, lets Room run
 * MIGRATION_3_4 (and its schema validation) on open, and checks that the
 * full-text index works: pre-existing rows are searchable, later writes stay
 * in sync, and Room's own sync triggers are the only ones present.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FtsSearchMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = FrameworkSQLiteOpenHelperFactory()
    private var helper: SupportSQLiteOpenHelper? = null
    private var database: AfterLaterDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        helper?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `entries saved before the migration are found by search`() = runBlocking {
        openVersion3AndClose()

        val opened = openWithRoom()
        val found = opened.savedItemDao()
            .observeArchiveSearch("documentary*", listOf("NEW", "READY", "SNOOZED", "USEFUL", "ARCHIVED"), null, null, "NEWEST")
            .first()

        assertEquals(listOf("pre"), found.map { it.id })
    }

    @Test
    fun `writes after the migration stay indexed`() = runBlocking {
        openVersion3AndClose()
        val opened = openWithRoom()
        val dao = opened.savedItemDao()

        dao.insert(
            SavedItemEntity(
                id = "fresh",
                originalUrl = "https://example.com/fresh",
                canonicalUrl = "https://example.com/fresh",
                title = "Kumquat harvest",
                description = null,
                domain = "example.com",
                thumbnailUrl = null,
                savedAt = 1_000,
                updatedAt = 1_000,
                status = com.afterlater.core.model.ItemStatus.NEW,
                lastReviewedAt = null,
                nextReviewAt = 1_000,
                snoozedUntil = null,
                personalNote = null,
                metadataStatus = com.afterlater.core.model.MetadataStatus.PENDING,
                surfaceHint = com.afterlater.core.model.SurfaceHint.QUEUE,
                expiresAt = null,
            ),
        )
        assertEquals(listOf("fresh"), search(dao, "kumquat"))

        dao.getById("fresh")!!.let { dao.update(it.copy(personalNote = "repeatable recipe")) }
        assertEquals(listOf("fresh"), search(dao, "repeatable"))

        dao.deleteById("fresh")
        assertTrue(search(dao, "kumquat").isEmpty())
    }

    @Test
    fun `room's own sync triggers are the only ones present`() {
        openVersion3AndClose()
        openWithRoom()

        val names = queryTriggerNames()
        // sqlite_master is queried with ORDER BY name, so AFTER sorts before BEFORE.
        val expected = listOf(
            "room_fts_content_sync_saved_items_fts_AFTER_INSERT",
            "room_fts_content_sync_saved_items_fts_AFTER_UPDATE",
            "room_fts_content_sync_saved_items_fts_BEFORE_DELETE",
            "room_fts_content_sync_saved_items_fts_BEFORE_UPDATE",
        )
        assertEquals(expected, names)
    }

    private suspend fun search(dao: SavedItemDao, term: String): List<String> =
        dao.observeArchiveSearch(
            match = "$term*",
            statuses = listOf("NEW", "READY", "SNOOZED", "USEFUL", "ARCHIVED"),
            domain = null,
            tag = null,
            sort = "NEWEST",
        ).first().map { it.id }

    private fun openVersion3AndClose() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) = applySchema(db, 3)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val db = factory.create(configuration).also { helper = it }.writableDatabase
        db.execSQL(ROW_SQL)
        db.execSQL(ROW_TWO_SQL)
        db.close()
    }

    private fun openWithRoom(): AfterLaterDatabase =
        Room.databaseBuilder(context, AfterLaterDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                AfterLaterDatabase.MIGRATION_1_2,
                AfterLaterDatabase.MIGRATION_2_3,
                AfterLaterDatabase.MIGRATION_3_4,
            )
            .allowMainThreadQueries()
            .build()
            .also { database = it }
            .apply { openHelper.writableDatabase }

    private fun queryTriggerNames(): List<String> {
        val db = database!!.openHelper.writableDatabase
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name").use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun applySchema(database: SupportSQLiteDatabase, version: Int) {
        val root = schema(version).getJSONObject("database")
        val entities = root.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (indexIndex in 0 until indices.length()) {
                database.execSQL(
                    indices.getJSONObject(indexIndex).getString("createSql").replace("\${TABLE_NAME}", table),
                )
            }
        }
        val setupQueries = root.optJSONArray("setupQueries") ?: return
        for (index in 0 until setupQueries.length()) {
            database.execSQL(setupQueries.getString(index))
        }
    }

    private fun schema(version: Int): JSONObject {
        val path = "${AfterLaterDatabase::class.java.name}/$version.json"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing exported schema on the test classpath: $path"
        }
        return stream.use { JSONObject(it.readBytes().decodeToString()) }
    }

    private companion object {
        const val DATABASE_NAME = "fts-migration-test"

        val ROW_SQL = """
            INSERT INTO saved_items
                (id, originalUrl, canonicalUrl, title, description, domain, savedAt, updatedAt,
                 status, metadataStatus, surfaceHint)
            VALUES
                ('pre', 'https://example.com/pre', 'https://example.com/pre', 'The pre entry',
                 'a documentary about rivers', 'example.com', 1000, 1000, 'READY', 'PENDING', 'QUEUE')
        """.trimIndent()

        val ROW_TWO_SQL = """
            INSERT INTO saved_items
                (id, originalUrl, canonicalUrl, title, description, domain, savedAt, updatedAt,
                 status, metadataStatus, surfaceHint)
            VALUES
                ('other', 'https://example.com/other', 'https://example.com/other', 'Unrelated',
                 'nothing to find here', 'example.com', 2000, 2000, 'READY', 'PENDING', 'QUEUE')
        """.trimIndent()
    }
}
