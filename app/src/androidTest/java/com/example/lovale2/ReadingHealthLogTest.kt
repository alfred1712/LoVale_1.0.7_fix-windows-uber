package com.example.lovale2

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.diagnostics.ReadingHealthLog
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReadingHealthLogTest {
    @Test fun exportFlushesAggregatesWithoutPrivateFields() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val zip = File(context.cacheDir, "reading-health-test.zip")
        fun count(): Long = ZipFile(zip).use { archive ->
            archive.entries().asSequence().sumOf { entry ->
                val text = archive.getInputStream(entry).bufferedReader().use { it.readText() }
                assertFalse(text.contains("PRIVATE-TEST-ADDRESS"))
                text.lineSequence().filter { it.isNotBlank() }.sumOf { line ->
                    JsonParser.parseString(line).asJsonObject.getAsJsonObject("counters")
                        ?.get("none.history_error.none.count")?.asLong ?: 0L
                }
            }
        }
        try {
            ReadingHealthLog.initialize(context)
            ReadingHealthLog.platform(null)
            ReadingHealthLog.event("history_error", mapOf("text" to "PRIVATE-TEST-ADDRESS"))
            ReadingHealthLog.export(context, Uri.fromFile(zip))
            val initial = count()
            ReadingHealthLog.event("history_error", mapOf("text" to "PRIVATE-TEST-ADDRESS"))
            ReadingHealthLog.export(context, Uri.fromFile(zip))
            assertEquals(initial + 1, count())
            ZipFile(zip).use { archive ->
                assertTrue(archive.size() in 1..7)
                assertTrue(archive.entries().asSequence().all { it.size <= 512 * 1024 })
            }
        } finally { zip.delete() }
    }
}
