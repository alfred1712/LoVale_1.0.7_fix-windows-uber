package com.example.lovale2

import com.example.lovale2.services.MonitoringHealth
import org.junit.Assert.*
import org.junit.Test

class MonitoringHealthTest {
    @Test fun idleIsNotAnError() {
        assertFalse(MonitoringHealth.assess(true, true, true, true, false, false).warning)
        assertFalse(MonitoringHealth.assess(true, true, true, false, false, true).warning)
    }
    @Test fun actualFailuresAreVisible() {
        assertTrue(MonitoringHealth.assess(true, false, true, true, true, false).warning)
        assertTrue(MonitoringHealth.assess(true, true, false, true, true, false).warning)
        assertTrue(MonitoringHealth.assess(true, true, true, true, true, true).warning)
        assertFalse(MonitoringHealth.assess(false, false, false, false, false, true).warning)
    }
}
