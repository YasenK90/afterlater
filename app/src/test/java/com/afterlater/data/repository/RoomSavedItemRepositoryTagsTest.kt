package com.afterlater.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlater.core.database.AfterLaterDatabase
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.database.SavedItemEntity
import com.afterlater.data.metadata.MetadataScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RoomSavedItemRepositoryTagsTest {

    private lateinit var database: AfterLaterDatabase
    private lateinit var repository: RoomSavedItemRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AfterLaterDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomSavedItemRepository(
            database = database,
            metadataScheduler = object : MetadataScheduler {
                override fun enqueue(itemId: String, replaceExisting: Boolean) = Unit
            },
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `chosen tags are stored trimmed, deduplicated, and in name order`() = runBlocking {
        database.savedItemDao().insert(item(id = "one"))

        repository.applyTags("one", listOf(" Recipe ", "Useful", "Recipe"))

        assertEquals(listOf("Recipe", "Useful"), database.tagDao().tagsForItem("one"))
    }

    @Test
    fun `applying again replaces the earlier tags`() = runBlocking {
        database.savedItemDao().insert(item(id = "one"))
        repository.applyTags("one", listOf("Recipe", "Useful"))

        repository.applyTags("one", listOf("Buy It"))

        assertEquals(listOf("Buy It"), database.tagDao().tagsForItem("one"))
    }

    @Test
    fun `tagging bumps updatedAt without writing a review event`() = runBlocking {
        database.savedItemDao().insert(item(id = "one", updatedAt = 1_000))

        repository.applyTags("one", listOf("Recipe"))

        assertEquals(
            Instant.parse("2026-10-30T12:00:00Z").toEpochMilli(),
            database.savedItemDao().getById("one")?.updatedAt,
        )
        assertTrue(database.reviewEventDao().getAll().isEmpty())
    }

    @Test
    fun `tagging an unknown entry is a no-op`() = runBlocking {
        repository.applyTags("missing", listOf("Recipe"))

        assertTrue(database.tagDao().tagsForItem("missing").isEmpty())
        assertTrue(database.tagDao().observeAll().first().isEmpty())
    }

    @Test
    fun `removing an entry removes its tag links`() = runBlocking {
        database.savedItemDao().insert(item(id = "one"))

        repository.applyTags("one", listOf("Recipe"))
        database.savedItemDao().deleteById("one")

        assertTrue(database.tagDao().tagsForItem("one").isEmpty())
    }

    private fun item(
        id: String,
        updatedAt: Long = 1_000,
    ) = SavedItemEntity(
        id = id,
        originalUrl = "https://example.com/$id",
        canonicalUrl = "https://example.com/$id",
        title = null,
        description = null,
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = 1_000,
        updatedAt = updatedAt,
        status = ItemStatus.READY,
        lastReviewedAt = null,
        nextReviewAt = 1_000,
        snoozedUntil = null,
        personalNote = null,
        metadataStatus = MetadataStatus.PENDING,
    )
}
