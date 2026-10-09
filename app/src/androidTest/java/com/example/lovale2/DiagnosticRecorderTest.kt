package com.example.lovale2

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.diagnostics.DiagnosticRecorder
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.data.settings.RideApp
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DiagnosticRecorderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zip = File(context.cacheDir, "diagnostic-test.zip")
    private val dir = File(context.noBackupFilesDir, "diagnostic-session")
    @After fun cleanup() = runBlocking { DiagnosticRecorder.delete(); zip.delete(); Unit }
    @Test fun technicalModeDoesNotStoreTextAndStopFlushesExport() = runBlocking {
        DiagnosticRecorder.start(context, RideApp.DIDI.packageName, false)
        DiagnosticRecorder.text("OCR", "PRIVATE ADDRESS MUST NOT BE STORED")
        DiagnosticRecorder.event("evaluation", "fare" to 8174.0, "totalKm" to 10.4)
        DiagnosticRecorder.stop()
        DiagnosticRecorder.event("after_stop")
        DiagnosticRecorder.export(context, Uri.fromFile(zip))
        ZipFile(zip).use { archive ->
            val text = archive.getInputStream(archive.getEntry("events.jsonl")).bufferedReader().use { it.readText() }
            assertTrue(text.contains("evaluation"))
            assertFalse(text.contains("PRIVATE"))
            assertFalse(text.contains("after_stop"))
            assertNotNull(archive.getEntry("summary.json"))
            assertFalse(archive.entries().asSequence().any { it.name.endsWith(".jpg") })
        }
        assertFalse(DiagnosticRecorder.state.value.active)
    }
    @Test fun visualConsentStoresImageAndNextSessionReplacesOldData() = runBlocking {
        DiagnosticRecorder.start(context, RideApp.DIDI.packageName, true)
        DiagnosticRecorder.text("OCR", "SYNTHETIC OFFER")
        DiagnosticRecorder.image(Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888), android.os.SystemClock.elapsedRealtime(), RideApp.DIDI.packageName)
        DiagnosticRecorder.export(context, Uri.fromFile(zip))
        ZipFile(zip).use { archive ->
            assertTrue(archive.entries().asSequence().any { it.name.endsWith(".jpg") && it.size > 0 })
            assertTrue(archive.getInputStream(archive.getEntry("events.jsonl")).bufferedReader().use { it.readText() }.contains("SYNTHETIC OFFER"))
        }
        DiagnosticRecorder.start(context, RideApp.UBER.packageName, false)
        DiagnosticRecorder.export(context, Uri.fromFile(zip))
        ZipFile(zip).use { archive ->
            assertFalse(archive.entries().asSequence().any { it.name.endsWith(".jpg") })
            assertFalse(archive.getInputStream(archive.getEntry("events.jsonl")).bufferedReader().use { it.readText() }.contains("SYNTHETIC OFFER"))
        }
    }
    @Test fun expiredSessionCannotBeExported() = runBlocking {
        DiagnosticRecorder.start(context, RideApp.DIDI.packageName, false)
        DiagnosticRecorder.export(context, Uri.fromFile(zip))
        assertTrue(dir.setLastModified(System.currentTimeMillis() - DiagnosticRecorder.RETENTION_MS - 1000))
        var rejected = false
        try { DiagnosticRecorder.export(context, Uri.fromFile(zip)) } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
        assertFalse(dir.exists())
    }
    @Test fun burstIsBoundedAndDroppedCountIsExported() = runBlocking {
        DiagnosticRecorder.start(context, RideApp.DIDI.packageName, false)
        repeat(25000) { DiagnosticRecorder.event("burst", "index" to it) }
        DiagnosticRecorder.export(context, Uri.fromFile(zip))
        ZipFile(zip).use { archive ->
            val summary = JsonParser.parseString(archive.getInputStream(archive.getEntry("summary.json")).bufferedReader().use { it.readText() }).asJsonObject
            assertTrue(summary["droppedRecords"].asInt > 0)
            assertTrue(summary["events"].asInt <= 20000)
        }
        assertTrue(dir.listFiles().orEmpty().sumOf { it.length() } <= DiagnosticRecorder.MAX_BYTES)
    }
    @Test fun changingPlatformOrPausingStopsCollection() = runBlocking {
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first()
        try {
            repository.setSelectedApp(RideApp.DIDI)
            repository.setServiceActive(true)
            DiagnosticRecorder.start(context, RideApp.DIDI.packageName, false)
            repository.setServiceActive(false)
            assertFalse(DiagnosticRecorder.state.value.active)
            DiagnosticRecorder.start(context, RideApp.DIDI.packageName, false)
            repository.setSelectedApp(RideApp.UBER)
            assertFalse(DiagnosticRecorder.state.value.active)
            assertFalse(repository.settingsFlow.first().serviceActive)
        } finally { repository.setSelectedApp(original.selectedApp) }
    }
    @Test fun unsupportedTrafficCountersAreNotReportedAsZero() {
        assertNull(DiagnosticRecorder.delta(-1, -1))
        assertNull(DiagnosticRecorder.delta(200, 100))
        assertEquals(150L, DiagnosticRecorder.delta(100, 250))
    }
}
