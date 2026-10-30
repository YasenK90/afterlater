package com.afterlater.data.export

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.SavedItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBackupCodecTest {

    private val exportedAt = Instant.parse("2026-03-01T10:00:00Z")
    private val savedAt = Instant.parse("2026-02-01T09:00:00Z")

    @Test
    fun `export includes version and every restorable field`() {
        val item = item(
            status = ItemStatus.SNOOZED,
            personalNote = "Read again after lunch.",
            lastReviewedAt = Instant.parse("2026-02-10T09:00:00Z"),
            nextReviewAt = Instant.parse("2026-02-11T09:00:00Z"),
            snoozedUntil = Instant.parse("2026-02-11T09:00:00Z"),
        )
        val event = event(
            id = 7,
            itemId = item.id,
            action = ReviewAction.SNOOZED,
            snoozeUntil = item.snoozedUntil,
        )

        val payload = ArchiveBackupCodec.encode(
            ArchiveSnapshot(items = listOf(item), events = listOf(event)),
            exportedAt,
        )

        assertTrue(payload.contains("\"format\": \"afterlater.archive\""))
        assertTrue(payload.contains("\"version\": 1"))
        assertTrue(payload.contains("\"personalNote\": \"Read again after lunch.\""))
        assertTrue(payload.contains("\"status\": \"SNOOZED\""))
        assertTrue(payload.contains("\"snoozedUntil\": \"2026-02-11T09:00:00Z\""))

        val decoded = ArchiveBackupCodec.decode(payload) as BackupDecodeResult.Success
        assertEquals(exportedAt, decoded.backup.exportedAt)
        assertEquals(listOf(item), decoded.backup.items)
        assertEquals(listOf(event), decoded.backup.events)
    }

    @Test
    fun `restore plan skips existing and repeated entries without changing them`() {
        val existing = item(id = "existing", canonicalUrl = "https://example.com/a")
        val new = item(id = "new", canonicalUrl = "https://example.com/b")
        val backup = ArchiveBackup(
            exportedAt = exportedAt,
            items = listOf(
                item(id = "existing", canonicalUrl = "https://example.com/a"),
                item(id = "same-canonical", canonicalUrl = "https://example.com/a"),
                new,
                item(id = "new", canonicalUrl = "https://example.com/c"),
                item(id = "same-canonical-as-new", canonicalUrl = "https://example.com/b"),
            ),
            events = listOf(
                event(id = 1, itemId = "existing", action = ReviewAction.OPENED),
                event(id = 2, itemId = "new", action = ReviewAction.OPENED),
                event(id = 3, itemId = "same-canonical-as-new", action = ReviewAction.OPENED),
            ),
        )

        val plan = ArchiveRestorePlanner.plan(
            backup = backup,
            existing = ArchiveSnapshot(items = listOf(existing), events = emptyList()),
        )

        assertEquals(listOf(new), plan.newItems)
        assertEquals(4, plan.duplicateCount)
        assertEquals(1, plan.newEvents.size)
        assertEquals("new", plan.newEvents.single().itemId)
        assertEquals(0, plan.newEvents.single().id)
        assertEquals(2, plan.skippedEventCount)
    }

    @Test
    fun `a duplicate within the backup is added only once`() {
        val first = item(id = "first", canonicalUrl = "https://example.com/a")
        val second = item(id = "second", canonicalUrl = "https://example.com/a")
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(exportedAt, listOf(first, second), emptyList()),
            existing = ArchiveSnapshot(emptyList(), emptyList()),
        )

        assertEquals(listOf(first), plan.newItems)
        assertEquals(1, plan.duplicateCount)
    }

    @Test
    fun `an empty file is rejected with a clear message`() {
        val result = ArchiveBackupCodec.decode("   ")

        val failure = result as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.EMPTY, failure.kind)
        assertEquals("The selected file is empty.", failure.message)
    }

    @Test
    fun `corrupted json is rejected with a clear message`() {
        val result = ArchiveBackupCodec.decode("""{"format":"afterlater.archive","version":1,"items":[""")

        val failure = result as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.CORRUPTED, failure.kind)
        assertEquals("The backup file is damaged or incomplete.", failure.message)
    }

    @Test
    fun `a foreign json file is not treated as a backup`() {
        val result = ArchiveBackupCodec.decode("""{"hello":"world"}""")

        val failure = result as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.NOT_AFTERLATER, failure.kind)
        assertEquals("This file is not an AFTERLATER backup.", failure.message)
    }

    @Test
    fun `a newer backup version is rejected without touching data`() {
        val result = ArchiveBackupCodec.decode(
            """{"format":"afterlater.archive","version":2,"exportedAt":"2026-03-01T10:00:00Z","items":[]}""",
        )

        val failure = result as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.UNSUPPORTED_VERSION, failure.kind)
        assertTrue(failure.message.contains("version 2"))
    }

    @Test
    fun `an invalid status is rejected with the entry position`() {
        val payload = ArchiveBackupCodec.encode(
            ArchiveSnapshot(listOf(item()), emptyList()),
            exportedAt,
        ).replace("\"status\": \"NEW\"", "\"status\": \"BROKEN\"")

        val failure = ArchiveBackupCodec.decode(payload) as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.INVALID_DATA, failure.kind)
        assertTrue(failure.message.contains("Backup entry 1"))
    }

    @Test
    fun `an invalid timestamp is rejected`() {
        val payload = ArchiveBackupCodec.encode(
            ArchiveSnapshot(listOf(item()), emptyList()),
            exportedAt,
        ).replace(savedAt.toString(), "not-a-timestamp")

        val failure = ArchiveBackupCodec.decode(payload) as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.INVALID_DATA, failure.kind)
        assertTrue(failure.message.contains("saved date"))
    }

    @Test
    fun `an empty archive is a valid backup with nothing to restore`() {
        val payload = ArchiveBackupCodec.encode(ArchiveSnapshot(emptyList(), emptyList()), exportedAt)

        val decoded = ArchiveBackupCodec.decode(payload) as BackupDecodeResult.Success
        val plan = ArchiveRestorePlanner.plan(
            backup = decoded.backup,
            existing = ArchiveSnapshot(emptyList(), emptyList()),
        )

        assertEquals(0, plan.newItemCount)
        assertEquals(0, plan.duplicateCount)
    }

    @Test
    fun `missing item list is rejected`() {
        val result = ArchiveBackupCodec.decode(
            """{"format":"afterlater.archive","version":1,"exportedAt":"2026-03-01T10:00:00Z"}""",
        )

        val failure = result as BackupDecodeResult.Failure
        assertEquals(BackupFailureKind.INVALID_DATA, failure.kind)
        assertEquals("The backup has no item list.", failure.message)
    }

    private fun item(
        id: String = "item-1",
        canonicalUrl: String = "https://example.com/article",
        status: ItemStatus = ItemStatus.NEW,
        personalNote: String? = null,
        lastReviewedAt: Instant? = null,
        nextReviewAt: Instant? = Instant.parse("2026-02-02T09:00:00Z"),
        snoozedUntil: Instant? = null,
    ) = SavedItem(
        id = id,
        originalUrl = canonicalUrl,
        canonicalUrl = canonicalUrl,
        title = "Example article",
        description = "A saved article.",
        domain = "example.com",
        thumbnailUrl = "https://example.com/image.png",
        savedAt = savedAt,
        updatedAt = savedAt,
        status = status,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        snoozedUntil = snoozedUntil,
        personalNote = personalNote,
        metadataStatus = MetadataStatus.SUCCESS,
    )

    private fun event(
        id: Long,
        itemId: String,
        action: ReviewAction,
        snoozeUntil: Instant? = null,
    ) = ReviewEvent(
        id = id,
        itemId = itemId,
        action = action,
        timestamp = Instant.parse("2026-02-10T09:00:00Z"),
        snoozeUntil = snoozeUntil,
    )
}
