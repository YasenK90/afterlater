package com.afterlater.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SchemaMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = FrameworkSQLiteOpenHelperFactory()
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migration adds exactly the columns the current entities expect`() {
        val database = openVersion(1)
        database.execSQL(ROW_SQL)

        AfterLaterDatabase.MIGRATION_1_2.migrate(database)

        assertEquals(
            schema(2).savedItemColumns(),
            database.notNullByColumn("saved_items"),
        )
    }
    @Test
    fun `rows written before the migration keep their data and gain the default hint`() {
        val database = openVersion(1)
        database.execSQL(ROW_SQL)

        AfterLaterDatabase.MIGRATION_1_2.migrate(database)

        database.query("SELECT id, title, surfaceHint, expiresAt FROM saved_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("one", cursor.getString(0))
            assertEquals("An entry", cursor.getString(1))
            assertEquals("QUEUE", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun `a deadline can be written to a migrated row`() {
        val database = openVersion(1)
        database.execSQL(ROW_SQL)

        AfterLaterDatabase.MIGRATION_1_2.migrate(database)
        database.execSQL("UPDATE saved_items SET expiresAt = 5000 WHERE id = 'one'")

        database.query("SELECT expiresAt FROM saved_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5_000L, cursor.getLong(0))
        }
    }

    @Test
    fun `migration 2 to 3 creates exactly the tag tables the current entities expect`() {
        val database = openVersion(2)

        AfterLaterDatabase.MIGRATION_2_3.migrate(database)

        assertEquals(schema(3).columnsOf("tags"), database.notNullByColumn("tags"))
        assertEquals(
            schema(3).columnsOf("saved_item_tags"),
            database.notNullByColumn("saved_item_tags"),
        )
    }

    @Test
    fun `deleting a migrated entry removes its tag links but keeps the tag rows`() {
        val database = openVersion(2)
        database.execSQL(ROW_SQL)

        AfterLaterDatabase.MIGRATION_2_3.migrate(database)
        database.execSQL("INSERT INTO tags (name, createdAt) VALUES ('RECIPE', 1000)")
        database.execSQL("INSERT INTO saved_item_tags (itemId, tagName) VALUES ('one', 'RECIPE')")

        // Room turns foreign-key enforcement on when it opens the real database;
        // the raw helper used here needs it enabled explicitly for the cascade.
        database.execSQL("PRAGMA foreign_keys = ON")
        database.execSQL("DELETE FROM saved_items WHERE id = 'one'")

        database.query("SELECT COUNT(*) FROM saved_item_tags").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.query("SELECT COUNT(*) FROM tags").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private fun openVersion(version: Int): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = applySchema(db, version)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        return factory.create(configuration).also { helper = it }.writableDatabase
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
                val statement = indices.getJSONObject(indexIndex).getString("createSql")
                database.execSQL(statement.replace("\${TABLE_NAME}", table))
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

    private fun JSONObject.columnsOf(table: String): Map<String, Boolean> {
        val entities = getJSONObject("database").getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            if (entity.getString("tableName") != table) continue

            val fields = entity.getJSONArray("fields")
            return buildMap {
                for (fieldIndex in 0 until fields.length()) {
                    val field = fields.getJSONObject(fieldIndex)
                    put(field.getString("columnName"), field.getBoolean("notNull"))
                }
            }
        }
        error("$table is missing from the exported schema")
    }

    private fun JSONObject.savedItemColumns(): Map<String, Boolean> = columnsOf("saved_items")

    private fun SupportSQLiteDatabase.notNullByColumn(table: String): Map<String, Boolean> {
        val columns = mutableMapOf<String, Boolean>()
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getInt(notNullIndex) != 0
            }
        }
        return columns
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"

        val ROW_SQL = """
            INSERT INTO saved_items
                (id, originalUrl, canonicalUrl, title, domain, savedAt, updatedAt, status, metadataStatus)
            VALUES
                ('one', 'https://example.com/a', 'https://example.com/a', 'An entry', 'example.com',
                 1000, 1000, 'READY', 'PENDING')
        """.trimIndent()
    }
}
