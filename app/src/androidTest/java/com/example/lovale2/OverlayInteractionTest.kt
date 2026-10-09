package com.example.lovale2

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.lovale2.services.TripOverlayService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun dragMovesOverlayAndTapClosesIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val prefs = context.getSharedPreferences("overlay_position", 0)
        val original = prefs.all
        prefs.edit().putInt("x", 20).putInt("y", 150).commit()
        fun bounds(): Rect? = automation.windows.firstNotNullOfOrNull { window ->
            val root = window.root ?: return@firstNotNullOfOrNull null
            try {
                if (root.contentDescription?.contains("Arrastrar para mover") == true) Rect().also(root::getBoundsInScreen) else null
            } finally { @Suppress("DEPRECATION") root.recycle() }
        }
        fun touch(action: Int, x: Float, y: Float, down: Long) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        try {
            compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            ContextCompat.startForegroundService(context, Intent(context, TripOverlayService::class.java)
                .putExtra("EXTRA_TEST", true).putExtra("EXTRA_OFFER_ID", "drag-test")
                .putExtra("EXTRA_RATE_KM", 1000.0).putExtra("EXTRA_RATE_HOUR", 18000.0))
            compose.waitUntil(5000) { bounds() != null }
            val before = requireNotNull(bounds())
            val x = before.exactCenterX(); val y = before.exactCenterY()
            val down = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, x, y, down)
            repeat(10) { i -> SystemClock.sleep(16); touch(MotionEvent.ACTION_MOVE, x + (i + 1) * 15, y + (i + 1) * 15, down) }
            touch(MotionEvent.ACTION_UP, x + 150, y + 150, down)
            compose.waitUntil(3000) { (bounds()?.left ?: 0) > before.left + 100 }
            val after = requireNotNull(bounds())
            assertTrue(after.top > before.top + 100)
            val tap = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, after.exactCenterX(), after.exactCenterY(), tap)
            touch(MotionEvent.ACTION_UP, after.exactCenterX(), after.exactCenterY(), tap)
            compose.waitUntil(3000) { bounds() == null }
        } finally {
            context.stopService(Intent(context, TripOverlayService::class.java))
            prefs.edit().clear().apply { original.forEach { (key, value) -> if (value is Int) putInt(key, value) } }.commit()
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun centerBadgesAreSeparateAndLeaveAcceptButtonsUncovered() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.openOfferFixture("didi-center.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        val automation = instrumentation.uiAutomation
        val flags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { this.flags = this.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        try {
            val cards = com.example.lovale2.services.OfferOcrReader(recognizer).read(bitmap).cards
            val image = android.widget.ImageView(compose.activity).apply { setImageBitmap(bitmap); scaleType = android.widget.ImageView.ScaleType.FIT_XY }
            compose.runOnUiThread { compose.activity.setContentView(image); compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            compose.waitUntil(3000) { image.height > 0 }
            val offset = IntArray(2)
            image.getLocationOnScreen(offset)
            val scaleY = image.height.toFloat() / bitmap.height
            val status = if (android.os.Build.VERSION.SDK_INT >= 30) compose.activity.windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top else 0
            val intents = ArrayList<Intent>()
            cards.forEach { card ->
                val evaluator = com.example.lovale2.domain.TripEvaluator(950.0,10000.0)
                val data = evaluator.extraerDatosDeViaje(card.text)
                val result = evaluator.evaluarViaje(data, emptyList())
                intents += Intent().putExtra("EXTRA_FARE",data.precio).putExtra("EXTRA_RATE_KM",result.tarifaPorKm)
                    .putExtra("EXTRA_RATE_HOUR",result.tarifaPorHora).putExtra("EXTRA_PROFITABILITY_LEVEL","RED")
                    .putExtra("EXTRA_CARD_TOP", offset[1] + (card.top * scaleY).toInt() - status).putExtra("EXTRA_CARD_RIGHT",image.width)
            }
            ContextCompat.startForegroundService(context, Intent(context,TripOverlayService::class.java).putParcelableArrayListExtra("EXTRA_CARDS",intents))
            // Non-touchable windows are intentionally omitted from accessibility window enumeration.
            // Inspect this process's actual attached views instead, without production test hooks.
            fun badgeBounds(): List<Rect> {
                val result = mutableListOf<Rect>()
                compose.runOnUiThread {
                    android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { root ->
                        val labels = arrayListOf<android.view.View>()
                        root.findViewsWithText(labels, "/km", android.view.View.FIND_VIEWS_WITH_TEXT)
                        labels.filter { it.isShown }.forEach { view ->
                            val location = IntArray(2); view.getLocationOnScreen(location)
                            result += Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
                            val lp = root.layoutParams as? android.view.WindowManager.LayoutParams
                            assertTrue(lp != null && lp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                        }
                    }
                }
                return result.sortedBy { it.top }
            }
            SystemClock.sleep(500)
            automation.takeScreenshot()?.let { shot ->
                java.io.File(context.getExternalFilesDir(null),"beta33-central.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                shot.recycle()
            }
            compose.waitUntil(5000) { badgeBounds().size == 3 }
            badgeBounds().zip(cards).forEach { (badge,card) ->
                assertTrue("Indicator must be above its Accept button", badge.bottom < offset[1] + requireNotNull(card.action).top * scaleY)
                assertTrue(badge.left >= 0 && badge.right <= image.width)
            }
            automation.takeScreenshot()?.let { shot ->
                java.io.File(context.getExternalFilesDir(null),"beta33-central.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                shot.recycle()
            }
        } finally {
            context.stopService(Intent(context, TripOverlayService::class.java))
            recognizer.close()
            compose.runOnUiThread { compose.activity.setContentView(android.widget.FrameLayout(compose.activity)) }
            bitmap.recycle()
            automation.serviceInfo = automation.serviceInfo.apply { this.flags = flags }
        }
    }

}
