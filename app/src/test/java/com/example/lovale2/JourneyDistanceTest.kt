package com.example.lovale2

import com.example.lovale2.domain.*
import org.junit.Assert.*
import org.junit.Test

class JourneyDistanceTest {
    private fun fix(lat: Double = 0.0, time: Long = 1000, accuracy: Double = 5.0, speed: Double? = 10.0) =
        JourneyFix(lat, 0.0, accuracy, time, speed)
    @Test fun countsMovementOnceAndNotOfferDistance() {
        val counter = JourneyDistance()
        assertEquals(0.0, counter.add(fix(), 1000), .01)
        assertEquals(111.2, counter.add(fix(.001, 11000), 11000), .2)
        assertEquals(0.0, counter.add(fix(.001, 11000), 11000), .01)
    }
    @Test fun ignoresNoiseOldFixesAndBadAccuracy() {
        val counter = JourneyDistance()
        counter.add(fix(), 1000)
        assertEquals(0.0, counter.add(fix(.00002, 6000, speed = 0.0), 6000), .01)
        assertEquals(0.0, counter.add(fix(.001, 11000, 100.0), 11000), .01)
        assertEquals(0.0, counter.add(fix(.001, 11000), 50000), .01)
    }
    @Test fun neverDrawsStraightLineAcrossGpsGap() {
        val counter = JourneyDistance()
        counter.add(fix(), 1000)
        assertEquals(0.0, counter.add(fix(.01, 101000), 101000), .01)
        assertTrue(counter.interrupted)
        assertEquals(111.2, counter.add(fix(.011, 111000), 111000), .2)
    }
    @Test fun rejectsGpsTeleportAndKeepsGoodAnchor() {
        val counter = JourneyDistance()
        counter.add(fix(), 1000)
        assertEquals(0.0, counter.add(fix(1.0, 6000), 6000), .01)
        assertEquals(111.2, counter.add(fix(.001, 11000), 11000), .2)
    }
    @Test fun continuousStationaryFixesAreNotASignalGap() {
        val counter = JourneyDistance()
        for (time in 1000L..301000L step 10000L) assertEquals(0.0, counter.add(fix(time = time, speed = 0.0), time), .01)
        assertFalse(counter.interrupted)
    }
    @Test fun stayingHomeBeforeDepartureDoesNotEndShift() {
        val arrival = HomeArrival(fix())
        for (time in 1000L..601000L step 10000L) assertFalse(arrival.add(fix(time = time, speed = 0.0), time))
    }
    @Test fun closesOnlyAfterLeavingAndReturningForFiveMinutes() {
        val arrival = HomeArrival(fix())
        assertFalse(arrival.add(fix(.01, 1000), 1000))
        for (time in 11000L..301000L step 10000L) assertFalse(arrival.add(fix(time = time, speed = 0.0), time))
        assertTrue(arrival.add(fix(time = 311000, speed = 0.0), 311000))
    }
    @Test fun passingHomeOrGpsGapDoesNotCloseShift() {
        val arrival = HomeArrival(fix())
        arrival.add(fix(.01, 1000), 1000)
        for (time in 11000L..311000L step 10000L) assertFalse(arrival.add(fix(time = time), time))
        assertFalse(arrival.add(fix(time = 321000, speed = 0.0), 321000))
        assertFalse(arrival.add(fix(time = 701000, speed = 0.0), 701000))
    }
    @Test fun resumedShiftStillRecognizesHomeWithoutAnotherDeparture() {
        val arrival = HomeArrival(fix(), alreadyDeparted = true)
        for (time in 1000L..291000L step 10000L) assertFalse(arrival.add(fix(time = time, speed = 0.0), time))
        assertTrue(arrival.add(fix(time = 301000, speed = 0.0), 301000))
    }
}
