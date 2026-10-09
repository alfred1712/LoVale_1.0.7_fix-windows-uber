package com.example.lovale2

import android.content.Context
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.data.settings.JourneyStore
import com.example.lovale2.domain.cleanHomeAddress
import org.junit.Assert.*
import org.junit.Test

class HomeSecurityTest {
    @Test fun addressPersistsWithoutChangingHomeOrJourney() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = JourneyStore.get(context)
        val before = store.state.value
        try {
            store.update { it.copy(homeAddress = cleanHomeAddress("  Casa de prueba 123\n  ")) }
            assertEquals("Casa de prueba 123", context.getSharedPreferences("journey_metrics", Context.MODE_PRIVATE).getString("homeAddress", null))
            assertEquals(before.copy(homeAddress = "Casa de prueba 123"), store.state.value)
            assertEquals(160, cleanHomeAddress("a".repeat(200)).length)
        } finally { store.update { before } }
    }

    @Test fun cleartextAndNotificationListenerAreUnavailable() {
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("datos.energia.gob.ar"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val services = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        assertFalse(services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" })
        assertTrue(services.filter { it.name.startsWith(context.packageName) && it.exported }.all {
            it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
        })
    }
}
