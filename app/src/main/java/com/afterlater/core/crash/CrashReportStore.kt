package com.afterlater.core.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.afterlater.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class CrashSummary(
    val count: Int,
    val latestAtMillis: Long?,
)

/**
 * Local, private crash capture for the closed beta.
 *
 * Uncaught exceptions are appended to app-private storage (the newest
 * [MAX_REPORTS] kept) so the user can share a debug report from Settings.
 * Nothing is uploaded and no third-party service sees a stack trace; sharing
 * hands the text to whatever app the user picks. The previous handler always
 * runs afterwards so the system crash flow stays intact.
 */
class CrashReportStore(context: Context) {

    private val appContext = context.applicationContext
    private val crashCounter = AtomicInteger(0)

    fun installAsHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun summary(): CrashSummary {
        val files = reportFiles()
        return CrashSummary(
            count = files.size,
            latestAtMillis = files.firstOrNull()?.let(::timestampOf),
        )
    }

    /**
     * A shareable bug-report header plus every stored crash trace. Stack
     * traces can occasionally contain a URL from an exception message, so the
     * settings screen tells the user to redact before posting publicly.
     */
    fun reportText(): String {
        val header = buildString {
            appendLine("AFTERLATER debug info")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }
        val reports = reportFiles().joinToString("\n\n") { file ->
            file.readText()
        }
        return if (reports.isEmpty()) header else "$header\n\n$reports"
    }

    fun clear() {
        reportFiles().forEach { it.delete() }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val dir = directory()
        val stamp = System.currentTimeMillis()
        val name = "crash-%013d-%03d.txt".format(
            Locale.US,
            stamp,
            crashCounter.getAndIncrement() % 1_000,
        )
        dir.mkdirs()
        val body = buildString {
            appendLine("AFTERLATER crash")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(throwable))
        }
        File(dir, name).writeText(body)

        val files = reportFiles()
        files.drop(MAX_REPORTS).forEach { it.delete() }
    }

    private fun reportFiles(): List<File> =
        directory().listFiles { file -> file.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    private fun timestampOf(file: File): Long? =
        file.name.removePrefix("crash-").substringBefore('-').toLongOrNull()

    private fun directory(): File = File(appContext.filesDir, DIRECTORY)

    private companion object {
        const val DIRECTORY = "crash-reports"
        const val MAX_REPORTS = 5
    }
}
