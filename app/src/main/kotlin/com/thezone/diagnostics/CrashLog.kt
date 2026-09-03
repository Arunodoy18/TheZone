package com.thezone.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash record. CLAUDE.md forbids analytics / network, so an uncaught
 * exception mid-incident would otherwise vanish. This chains a default
 * uncaught-exception handler that writes the stack trace to the app's private
 * files dir (last [KEEP] kept) before the process dies, so the demo can point at
 * what broke. Nothing leaves the device.
 */
object CrashLog {

    private const val PREFIX = "crash-"
    private const val KEEP = 6
    private const val TAG = "TheZone"

    fun install(context: Context) {
        val dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(dir, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Newest first. */
    fun list(context: Context): List<File> =
        context.applicationContext.filesDir
            .listFiles { f -> f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latestText(context: Context): String? = list(context).firstOrNull()?.readText()

    fun clear(context: Context) {
        list(context).forEach { it.delete() }
    }

    private fun write(dir: File, thread: Thread, error: Throwable) {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val body = buildString {
            appendLine("time     ${Date()}")
            appendLine("thread   ${thread.name}")
            appendLine("device   ${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("--")
            append(sw.toString())
        }
        File(dir, "$PREFIX$ts.txt").writeText(body)
        Log.e(TAG, "uncaught on ${thread.name}: ${error.message}")

        dir.listFiles { f -> f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }
}
