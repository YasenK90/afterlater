package com.afterlater.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SavedItemDaoTest {

    private lateinit var database: AfterLaterDatabase
    private lateinit var dao: SavedItemDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AfterLaterDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.savedItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an entry past its deadline is not counted as due`() = runBlocking {
        dao.insert(item(id = "live", expiresAt = 10_000))
        dao.insert(item(id = "dead", expiresAt = 4_000))

        assertEquals(1, dao.observeDueCount(now = 5_000).first())
    }

    @Test
    fun `an entry past its deadline is not offered as a candidate`() = runBlocking {
        dao.insert(item(id = "live", expiresAt = 10_000))
        dao.insert(item(id = "dead", expiresAt = 4_000))

        val ids = dao.getDueCandidates(now = 5_000, limit = 10).map { it.id }

        assertEquals(listOf("live"), ids)
    }

    @Test
    fun `an entry without a deadline is still due`() = runBlocking {
        dao.insert(item(id = "none"))

        assertEquals(1, dao.observeDueCount(now = 5_000).first())
    }

    @Test
    fun `a deadline exactly at the current instant is excluded`() = runBlocking {
        dao.insert(item(id = "edge", expiresAt = 5_000))

        assertEquals(0, dao.observeDueCount(now = 5_000).first())
    }

    @Test
    fun `snoozed entries wait for the snooze to pass`() = runBlocking {
        dao.insert(item(id = "later", snoozedUntil = 9_000))

        assertEquals(0, dao.observeDueCount(now = 5_000).first())
        assertEquals(1, dao.observeDueCount(now = 9_000).first())
    }

    private fun item(
        id: String,
        expiresAt: Long? = null,
        snoozedUntil: Long? = null,
    ) = SavedItemEntity(
        id = id,
        originalUrl = "https://example.com/$id",
        canonicalUrl = "https://example.com/$id",
        title = null,
        description = null,
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = 1_000,
        updatedAt = 1_000,
        status = ItemStatus.READY,
        lastReviewedAt = null,
        nextReviewAt = 1_000,
        snoozedUntil = snoozedUntil,
        personalNote = null,
        metadataStatus = MetadataStatus.PENDING,
        expiresAt = expiresAt,
    )
}
