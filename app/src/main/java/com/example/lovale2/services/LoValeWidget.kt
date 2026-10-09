package com.example.lovale2.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.lovale2.MainActivity
import com.example.lovale2.R
import com.example.lovale2.data.settings.OfferHistory
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.domain.periodStart
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LoValeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { withTimeout(8000) { render(context.applicationContext) } }
            catch (_: Exception) { /* Host update can be retried later. */ }
            finally { pending.finish() }
        }
    }
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null
        @Synchronized fun refresh(context: Context) {
            val app = context.applicationContext
            job?.cancel()
            job = scope.launch { delay(400); try { render(app) } catch (_: Exception) { } }
        }
        suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LoValeWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context))
        }
        suspend fun views(context: Context): RemoteViews {
            val settings = SettingsRepository(context).settingsFlow.first()
            val now = System.currentTimeMillis()
            val count = OfferHistory(context).records.first().count { it.timestamp in periodStart(now)..now }
            return RemoteViews(context.packageName, R.layout.widget_lovale).apply {
                setTextViewText(R.id.widget_status, if (settings.serviceActive) "Activo · ${settings.selectedApp?.label.orEmpty()}" else "Pausado")
                setTextViewText(R.id.widget_summary, "Hoy · $count ofertas analizadas")
                setTextViewText(R.id.widget_updated, "${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())} · Abrir LoVale")
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 901, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
    }
}
