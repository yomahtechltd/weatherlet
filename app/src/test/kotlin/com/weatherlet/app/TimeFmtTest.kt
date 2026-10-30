package com.weatherlet.app

import com.weatherlet.app.alerts.TimeFmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeFmtTest {
    @Before fun twelve() { TimeFmt.use24h = false }
    @After fun reset() { TimeFmt.use24h = false }

    @Test fun twelveHourClock() {
        assertEquals("12:00 AM", TimeFmt.time(0, 0))
        assertEquals("7:05 AM", TimeFmt.time(7, 5))
        assertEquals("12:30 PM", TimeFmt.time(12, 30))
        assertEquals("8:00 PM", TimeFmt.time(20, 0))
        assertEquals("11:59 PM", TimeFmt.time(23, 59))
    }

    @Test fun compactHours() {
        assertEquals("12 AM", TimeFmt.hour(0))
        assertEquals("1 PM", TimeFmt.hour(13))
        assertEquals("8 PM", TimeFmt.hour(20))
    }

    @Test fun minutesSinceMidnight() {
        assertEquals("5:30 PM", TimeFmt.minutes(17 * 60 + 30))
        assertEquals("6:00 AM", TimeFmt.minutes(6 * 60))
    }

    @Test fun twentyFourHourStillAvailable() {
        TimeFmt.use24h = true
        assertEquals("20:00", TimeFmt.time(20, 0))
        assertEquals("07:05", TimeFmt.time(7, 5))
        assertEquals("20:00", TimeFmt.hour(20))
    }
}
