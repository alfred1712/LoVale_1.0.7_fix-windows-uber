package com.example.lovale2.diagnostics

import android.content.Context
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One short-lived reading in RAM. Disk persistence requires the driver's Report action. */
object ReadingReport {
    private var text = ""
    private var at = 0L
    @Synchronized fun observed(source: String, raw: String) { text = "$source\n${raw.take(24000)}"; at = SystemClock.elapsedRealtime() }
    @Synchronized fun recent(): String? = text.takeIf { it.isNotBlank() && SystemClock.elapsedRealtime() - at in 0..120_000 }
    suspend fun save(context: Context): Boolean {
        val snapshot = recent() ?: return false
        return withContext(Dispatchers.IO) { file(context).writeText(snapshot); true }
    }
    suspend fun read(context: Context): String? = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) null else if (System.currentTimeMillis() - f.lastModified() > 86400000) { f.delete(); null } else f.readText().take(24100)
    }
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) { file(context).delete() }
    private fun file(context: Context) = File(context.noBackupFilesDir, "reading-report.txt")
}
