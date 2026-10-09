package com.example.lovale2

import android.content.Intent
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.filters.SdkSuppress
import com.example.lovale2.services.TripOverlayService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class ZoneOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun description() = WindowInspector.getGlobalWindowViews().mapNotNull { it.contentDescription?.toString() }
        .firstOrNull { it.contains("Arrastrar para mover") }

    @Test fun updatesOnlyMatchingVisibleOfferAndCannotReopenDismissedOne() {
        val context = compose.activity
        try {
            ContextCompat.startForegroundService(context, Intent(context, TripOverlayService::class.java)
                .putExtra("EXTRA_OFFER_ID", "zone-test").putExtra("EXTRA_RATE_KM", 1000.0)
                .putExtra("EXTRA_RATE_HOUR", 18000.0).putExtra("EXTRA_PROFITABILITY_LEVEL", "GREEN"))
            compose.waitUntil(3000) { description() != null }
            compose.runOnUiThread { TripOverlayService.updateZone("another-offer", true, "Palermo", "ZONE") }
            assertFalse(description().orEmpty().contains("Palermo"))
            compose.runOnUiThread { TripOverlayService.updateZone("zone-test", true, "Palermo", "ZONE") }
            compose.waitUntil(3000) { description().orEmpty().contains("Zona no deseada · Palermo") }
            compose.runOnUiThread {
                TripOverlayService.dismissOffer("test")
                TripOverlayService.updateZone("zone-test", true, "Palermo", "ZONE")
            }
            compose.waitUntil(3000) { description() == null }
            compose.runOnUiThread { TripOverlayService.updateZone("zone-test", true, "Palermo", "ZONE") }
            assertNull(description())
        } finally { context.stopService(Intent(context, TripOverlayService::class.java)) }
    }
}
