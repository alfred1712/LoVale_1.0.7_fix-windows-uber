package com.example.lovale2.diagnostics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.lovale2.BuildConfig
import com.example.lovale2.domain.ReadingMetrics
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Automatic local metrics, seven daily files, <= 512 KiB/day. No screenshots or raw text. */
object ReadingHealthLog {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val metrics = ReadingMetrics()
    @Volatile private var folder: File? = null
    @Volatile private var platform = "none"
    private val processId = java.util.UUID.randomUUID().toString()
    private var lastWrite = System.currentTimeMillis()
    private const val DAY = 86400000L
    private const val LIMIT = 512 * 1024L
    @Synchronized fun initialize(context: Context) {
        if (folder != null) return
        folder = File(context.applicationContext.noBackupFilesDir, "reading-health")
        worker.scheduleWithFixedDelay({ runCatching { write() }.onFailure {
            Log.w("LoVale", "DIAGNOSTICS escritura pendiente: ${it.javaClass.simpleName}")
        } }, 0, 60, TimeUnit.SECONDS)
    }
    fun platform(label: String?) { platform = label ?: "none" }
    fun event(stage: String, data: Map<String, Any?>) { if (folder != null) metrics.event(platform, stage, data) }
    private fun prune(dir: File, now: Long) {
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[0-9]+\\.jsonl")) }
        files.filter { now - it.lastModified() > 7 * DAY }.forEach { it.delete() }
        files.filter { it.exists() }.sortedByDescending { it.nameWithoutExtension.toLong() }.drop(7).forEach { it.delete() }
    }
    private fun write() {
        val dir = folder ?: return
        if (!dir.exists()) check(dir.mkdirs())
        val now = System.currentTimeMillis()
        prune(dir, now)
        val counters = metrics.drain()
        val start = lastWrite
        if (counters.isEmpty()) return
        val file = File(dir, "${now / DAY}.jsonl")
        val line = Gson().toJson(mapOf("from" to start, "to" to now, "version" to BuildConfig.VERSION_NAME,
            "process" to processId, "counters" to counters)) + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        try {
            val output = when {
                file.length() + bytes.size <= LIMIT - 1024 -> bytes
                file.length() < LIMIT - 1024 -> "{\"storage_limit\":true,\"time\":$now}\n".toByteArray()
                else -> null
            }
            if (output != null) RandomAccessFile(file, "rw").use { stream ->
                val length = stream.length()
                try { stream.seek(length); stream.write(output) }
                catch (failure: Exception) { runCatching { stream.setLength(length) }; throw failure }
            }
            lastWrite = now
        } catch (failure: Exception) {
            metrics.restore(counters)
            throw failure
        }
        prune(dir, now)
    }
    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        initialize(context)
        worker.submit {
            write()
            val files = requireNotNull(folder).listFiles().orEmpty().filter { it.extension == "jsonl" }
            require(files.isNotEmpty()) { "Todavía no hay métricas" }
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { out ->
                ZipOutputStream(out).use { zip ->
                    files.sortedBy { it.name }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        }.get()
    }
}
