package com.afterlater.core.crash

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CrashReportStoreTest {

    private lateinit var store: CrashReportStore
    private var previousHandlerCalled = false

    @Before
    fun setUp() {
        store = CrashReportStore(ApplicationProvider.getApplicationContext())
        store.clear()
        previousHandlerCalled = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousHandlerCalled = true }
    }

    @After
    fun tearDown() {
        store.clear()
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun crash(message: String) {
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), RuntimeException(message))
    }

    @Test
    fun `a crash is stored with device details and the stack trace`() {
        store.installAsHandler()

        crash("boom for test")

        val summary = store.summary()
        assertEquals(1, summary.count)
        assertTrue(summary.latestAtMillis != null)
        val text = store.reportText()
        assertTrue(text.contains("AFTERLATER crash"))
        assertTrue(text.contains("thread:"))
        assertTrue(text.contains("boom for test"))
        assertTrue(text.contains("java.lang.RuntimeException"))
    }

    @Test
    fun `the previous handler still runs after ours`() {
        store.installAsHandler()

        crash("chained")

        assertTrue(previousHandlerCalled)
        assertEquals(1, store.summary().count)
    }

    @Test
    fun `only the newest five crashes are kept`() {
        store.installAsHandler()

        repeat(7) { index -> crash("crash number $index") }

        assertEquals(5, store.summary().count)
        val text = store.reportText()
        assertTrue(text.contains("crash number 6"))
        assertFalse(text.contains("crash number 0"))
    }

    @Test
    fun `an empty store reports zero and shares just the header`() {
        val summary = store.summary()

        assertEquals(0, summary.count)
        assertTrue(summary.latestAtMillis == null)
        val text = store.reportText()
        assertTrue(text.contains("AFTERLATER debug info"))
        assertFalse(text.contains("AFTERLATER crash"))
    }

    @Test
    fun `clear removes every report`() {
        store.installAsHandler()
        crash("to be cleared")

        store.clear()

        assertEquals(0, store.summary().count)
    }
}
