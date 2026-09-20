package com.example.lovale2.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.net.TrafficStats
import android.net.Uri
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.example.lovale2.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagnosticState(val active: Boolean = false, val visual: Boolean = false,
    val packageName: String = "", val message: String = "Desactivado", val available: Boolean = false)

/** Opt-in, local-only flight recorder. One bounded session, excluded from backup.
 * All disk/compression work is serialized off the main thread; at most 32 pending records.
 * Process death stops collection. No automatic uploading and no GPS coordinates in metrics.
 */
object DiagnosticRecorder {
    const val MAX_DURATION_MS = 60 * 60 * 1000L
    const val RETENTION_MS = 24 * 60 * 60 * 1000L
    const val MAX_BYTES = 50 * 1024 * 1024L
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val pending = AtomicInteger()
    private val pendingImages = AtomicInteger()
    private var deadline: ScheduledFuture<*>? = null
    private val mutable = MutableStateFlow(DiagnosticState())
    val state = mutable.asStateFlow()
    private val gson = Gson()
    @Volatile private var folder: File? = null
    @Volatile private var session: Session? = null
    private data class Session(val started: Long, val wall: Long, val cpu: Long, val rx: Long,
        val tx: Long, val visual: Boolean, val platform: String, val dropped: AtomicInteger = AtomicInteger(),
        var bytes: Long = 0, var count: Long = 0, val id: String = java.util.UUID.randomUUID().toString())

    @Synchronized fun initialize(context: Context) {
        if (folder != null) return
        val dir = File(context.applicationContext.noBackupFilesDir, "diagnostic-session")
        folder = dir
        worker.execute {
            runCatching {
                if (expired(dir)) dir.deleteRecursively()
                if (session == null) mutable.value = DiagnosticState(available = File(dir, "events.jsonl").exists(),
                    message = if (File(dir, "events.jsonl").exists()) "Registro disponible" else "Desactivado")
            }
        }
    }
    private fun expired(dir: File) = dir.exists() && System.currentTimeMillis() - dir.lastModified() > RETENTION_MS

    @Synchronized fun start(context: Context, platform: String, visual: Boolean) {
        initialize(context)
        if (session != null) return
        require(platform in listOf("com.ubercab.driver", "com.cabify.driver", "com.didiglobal.driver"))
        val current = Session(SystemClock.elapsedRealtime(), System.currentTimeMillis(), Process.getElapsedCpuTime(),
            TrafficStats.getUidRxBytes(Process.myUid()), TrafficStats.getUidTxBytes(Process.myUid()), visual, platform)
        val dir = folder ?: return
        session = current
        worker.execute {
            try {
                dir.deleteRecursively()
                check(dir.mkdirs())
                append(dir, current, "session_start", mapOf("version" to BuildConfig.VERSION_NAME,
                    "platform" to platform, "visual" to visual, "maxMinutes" to 60,
                    "maxBytes" to MAX_BYTES, "samplingMs" to 5000,
                    "notice" to "Muestreo: puede omitir ofertas entre capturas. No prueba exactitud por sí solo."))
            } catch (_: Exception) { fail(current) }
        }
        deadline = worker.schedule({ stop("límite de una hora") }, MAX_DURATION_MS, TimeUnit.MILLISECONDS)
        mutable.value = DiagnosticState(true, visual, platform, "Grabando · máximo 1 h", true)
    }

    fun active(): Boolean {
        val current = session ?: return false
        if (SystemClock.elapsedRealtime() - current.started >= MAX_DURATION_MS) {
            stop("límite de una hora")
            return false
        }
        return true
    }
    fun visualEnabled() = active() && session?.visual == true
    fun sessionId(): String? = session?.id

    @Synchronized fun event(stage: String, vararg fields: Pair<String, Any?>) {
        if (!active()) return
        val current = session ?: return
        val at = SystemClock.elapsedRealtime()
        val values = fields.toMap().mapValues { (_, value) ->
            when {
                value is Double && !value.isFinite() -> "invalid_number"
                value is Float && !value.isFinite() -> "invalid_number"
                value is String -> value.take(12000)
                else -> value
            }
        }
        submit(current) { dir -> append(dir, current, stage, values, at) }
    }
    fun text(source: String, text: String) {
        if (visualEnabled()) event("read_text", "source" to source, "text" to text.take(12000))
    }

    /** Takes ownership of bitmap even when the session was stopped or queue is full. */
    @Synchronized fun image(bitmap: Bitmap, captureAt: Long, platform: String) {
        val current = session
        if (current == null || !current.visual || current.platform != platform || !active()) {
            bitmap.recycle(); return
        }
        if (pendingImages.incrementAndGet() > 1) {
            pendingImages.decrementAndGet(); current.dropped.incrementAndGet(); bitmap.recycle(); return
        }
        val accepted = submit(current) { dir ->
            try {
                if (current.bytes >= MAX_BYTES - 2 * 1024 * 1024) {
                    if (session === current) stop("límite de almacenamiento")
                    return@submit
                }
                val file = File(dir, "window-$captureAt.jpg")
                try {
                    file.outputStream().use { output ->
                        var remaining = minOf(2 * 1024 * 1024L, MAX_BYTES - current.bytes - 65536)
                        val limited = object : OutputStream() {
                            override fun write(value: Int) { check(remaining-- > 0); output.write(value) }
                            override fun write(bytes: ByteArray, offset: Int, count: Int) {
                                check(count <= remaining); remaining -= count; output.write(bytes, offset, count)
                            }
                        }
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, limited))
                    }
                } catch (_: Exception) {
                    file.delete()
                    current.dropped.incrementAndGet()
                    append(dir, current, "reference_skipped", mapOf("reason" to "imagen excede límite/error de escritura"), captureAt)
                    return@submit
                }
                current.bytes += file.length()
                append(dir, current, "reference_image", mapOf("file" to file.name,
                    "width" to bitmap.width, "height" to bitmap.height), captureAt)
            } finally { bitmap.recycle(); pendingImages.decrementAndGet() }
        }
        if (!accepted) { bitmap.recycle(); pendingImages.decrementAndGet() }
    }

    private fun submit(current: Session, action: (File) -> Unit): Boolean {
        if (pending.incrementAndGet() > 32) {
            pending.decrementAndGet(); current.dropped.incrementAndGet(); return false
        }
        worker.execute {
            try { folder?.let(action) } catch (_: Exception) { fail(current) }
            finally { pending.decrementAndGet() }
        }
        return true
    }
    private fun append(dir: File, current: Session, stage: String, data: Map<String, Any?>,
                       at: Long = SystemClock.elapsedRealtime()) {
        if (current.bytes >= MAX_BYTES - 65536 || current.count >= 20000) {
            current.dropped.incrementAndGet()
            if (session === current) stop("límite de almacenamiento/eventos")
            return
        }
        val bytes = (gson.toJson(mapOf("id" to ++current.count, "elapsedMs" to at - current.started,
            "time" to current.wall + at - current.started, "stage" to stage, "data" to data)) + "\n").toByteArray(Charsets.UTF_8)
        File(dir, "events.jsonl").appendBytes(bytes)
        current.bytes += bytes.size
    }
    private fun fail(current: Session) {
        synchronized(this) {
            if (session === current) {
                session = null
                deadline?.cancel(false)
                deadline = null
                mutable.value = mutable.value.copy(active = false, message = "Error al guardar diagnóstico")
            }
        }
    }
    @Synchronized fun stop(reason: String = "finalizado") {
        val current = session ?: return
        session = null
        deadline?.cancel(false)
        deadline = null
        val elapsed = SystemClock.elapsedRealtime() - current.started
        val cpu = Process.getElapsedCpuTime() - current.cpu
        val rx = delta(current.rx, TrafficStats.getUidRxBytes(Process.myUid()))
        val tx = delta(current.tx, TrafficStats.getUidTxBytes(Process.myUid()))
        val dir = folder ?: return
        mutable.value = mutable.value.copy(active = false, message = "Registro listo · $reason")
        worker.execute {
            runCatching {
                val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                File(dir, "summary.json").writeText(gson.toJson(mapOf("reason" to reason,
                    "durationMs" to elapsed, "processCpuMs" to cpu, "uidRxBytes" to rx, "uidTxBytes" to tx,
                    "pssKiBAtEnd" to memory.totalPss, "droppedRecords" to current.dropped.get(),
                    "events" to current.count, "storedBytes" to current.bytes,
                    "scope" to "CPU del proceso; red del UID en todas las interfaces; memoria al finalizar. No mide batería atribuible ni porcentaje de ofertas perdidas.")), Charsets.UTF_8)
            }.onFailure { mutable.value = mutable.value.copy(message = "Registro parcial; error al guardar resumen") }
        }
    }
    fun delta(before: Long, after: Long): Long? = if (before >= 0 && after >= before) after - before else null

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        stop("exportado")
        worker.submit {
            val dir = requireNotNull(folder)
            if (expired(dir)) { dir.deleteRecursively(); mutable.value = DiagnosticState(); error("El registro venció (24 h)") }
            check(File(dir, "events.jsonl").exists()) { "No hay registro" }
            context.contentResolver.openOutputStream(destination, "wt").use { output ->
                requireNotNull(output)
                ZipOutputStream(output).use { zip ->
                    dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }.get()
    }
    suspend fun delete() = withContext(Dispatchers.IO) {
        stop("borrado")
        worker.submit { folder?.deleteRecursively(); mutable.value = DiagnosticState() }.get()
    }
}
