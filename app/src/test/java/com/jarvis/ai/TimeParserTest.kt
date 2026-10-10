package com.jarvis.ai

import com.jarvis.ai.core.agent.TimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeParserTest {

    @Test
    fun `parses simple hour as morning`() {
        val t = TimeParser.parseClockTime("set an alarm for 7")!!
        assertEquals(7, t.hour)
        assertEquals(0, t.minute)
    }

    @Test
    fun `parses hour with minutes`() {
        val t = TimeParser.parseClockTime("alarm at 6:45")!!
        assertEquals(6, t.hour)
        assertEquals(45, t.minute)
    }

    @Test
    fun `parses pm meridiem`() {
        val t = TimeParser.parseClockTime("remind me at 3 pm")!!
        assertEquals(15, t.hour)
    }

    @Test
    fun `parses am noon edge`() {
        val t = TimeParser.parseClockTime("alarm at 12 am")!!
        assertEquals(0, t.hour)
        val noon = TimeParser.parseClockTime("alarm at 12 pm")!!
        assertEquals(12, noon.hour)
    }

    @Test
    fun `parses 24h time with minutes`() {
        val t = TimeParser.parseClockTime("alarm at 19:30")!!
        assertEquals(19, t.hour)
        assertEquals(30, t.minute)
    }

    @Test
    fun `parses dotted time`() {
        val t = TimeParser.parseClockTime("alarm at 7.15")!!
        assertEquals(7, t.hour)
        assertEquals(15, t.minute)
    }

    @Test
    fun `parses half past with word number`() {
        val t = TimeParser.parseClockTime("alarm at half past seven")!!
        assertEquals(7, t.hour)
        assertEquals(30, t.minute)
    }

    @Test
    fun `parses quarter to`() {
        val t = TimeParser.parseClockTime("quarter to six")!!
        assertEquals(5, t.hour)
        assertEquals(45, t.minute)
    }

    @Test
    fun `returns null when no time present`() {
        assertNull(TimeParser.parseClockTime("what a lovely day"))
    }

    @Test
    fun `parses durations`() {
        assertEquals(300, TimeParser.parseDurationSeconds("timer for 5 minutes"))
        assertEquals(3600, TimeParser.parseDurationSeconds("1 hour"))
        assertEquals(90, TimeParser.parseDurationSeconds("one minute and 30 seconds"))
        assertEquals(45, TimeParser.parseDurationSeconds("forty five seconds"))
        assertNull(TimeParser.parseDurationSeconds("forever"))
    }
}
