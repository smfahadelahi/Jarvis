package com.jarvis.ai

import com.jarvis.ai.core.agent.CommandFastPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandFastPathTest {

    @Test
    fun `detects plain alarm`() {
        val cmd = CommandFastPath.detect("Set an alarm for 7:30 am") as CommandFastPath.FastCommand.SetAlarm
        assertEquals(7, cmd.time.hour)
        assertEquals(30, cmd.time.minute)
    }

    @Test
    fun `detects wake me phrasing`() {
        val cmd = CommandFastPath.detect("wake me up at six") as CommandFastPath.FastCommand.SetAlarm
        assertEquals(6, cmd.time.hour)
    }

    @Test
    fun `does not fire on alarm cancellation`() {
        assertNull(CommandFastPath.detect("cancel my alarm"))
    }

    @Test
    fun `detects timer`() {
        val cmd = CommandFastPath.detect("set a timer for 10 minutes") as CommandFastPath.FastCommand.SetTimer
        assertEquals(600, cmd.seconds)
    }

    @Test
    fun `detects reversed timer phrasing`() {
        val cmd = CommandFastPath.detect("5 minute timer please") as CommandFastPath.FastCommand.SetTimer
        assertEquals(300, cmd.seconds)
    }

    @Test
    fun `detects flashlight toggle`() {
        val cmd = CommandFastPath.detect("flashlight") as CommandFastPath.FastCommand.Flashlight
        assertNull(cmd.on)
        val on = CommandFastPath.detect("turn the torch on") as CommandFastPath.FastCommand.Flashlight
        assertEquals(true, on.on)
    }

    @Test
    fun `detects weather with city`() {
        val cmd = CommandFastPath.detect("what's the weather in busan?") as CommandFastPath.FastCommand.Weather
        assertEquals("busan", cmd.city)
    }

    @Test
    fun `detects briefing variants`() {
        assertTrue(CommandFastPath.detect("good morning") is CommandFastPath.FastCommand.Briefing)
        assertTrue(CommandFastPath.detect("brief me") is CommandFastPath.FastCommand.Briefing)
    }

    @Test
    fun `detects korean lesson`() {
        assertTrue(CommandFastPath.detect("teach me a korean word") is CommandFastPath.FastCommand.KoreanLesson)
    }

    @Test
    fun `detects volume set`() {
        val cmd = CommandFastPath.detect("set volume to 40%") as CommandFastPath.FastCommand.Volume
        assertEquals(40, cmd.levelPercent)
        assertEquals(CommandFastPath.VolumeAction.SET, cmd.action)
    }

    @Test
    fun `ignores ordinary conversation`() {
        assertNull(CommandFastPath.detect("what do you think about the nature of consciousness, jarvis?"))
        assertNull(CommandFastPath.detect("write me a poem about the sea"))
    }
}
