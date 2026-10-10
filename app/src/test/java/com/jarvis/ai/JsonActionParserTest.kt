package com.jarvis.ai

import com.jarvis.ai.core.agent.CommandFastPath
import com.jarvis.ai.core.agent.JsonActionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonActionParserTest {

    @Test
    fun `parses clean tool call`() {
        val raw = """{"tool":"set_alarm","parameters":{"hour":"7","minute":"30"}}"""
        val action = JsonActionParser.parse(raw)
        assertEquals("set_alarm", action!!.tool)
        assertEquals("7", action.parameters["hour"])
        assertEquals("30", action.parameters["minute"])
    }

    @Test
    fun `parses tool call wrapped in prose`() {
        val raw = "Very good, sir. {\"tool\":\"flashlight\",\"parameters\":{\"state\":\"on\"}} Right away."
        val action = JsonActionParser.parse(raw)
        assertEquals("flashlight", action!!.tool)
        assertEquals("on", action.parameters["state"])
    }

    @Test
    fun `parses tool call in markdown fence`() {
        val raw = "```json\n{\"tool\":\"get_weather\",\"parameters\":{\"city\":\"Seoul\"}}\n```"
        val action = JsonActionParser.parse(raw)
        assertEquals("get_weather", action!!.tool)
        assertEquals("Seoul", action.parameters["city"])
    }

    @Test
    fun `accepts alternate key names`() {
        val raw = """{"name":"start_timer","args":{"seconds":"300"}}"""
        val action = JsonActionParser.parse(raw)
        assertEquals("start_timer", action!!.tool)
        assertEquals("300", action.parameters["seconds"])
    }

    @Test
    fun `accepts sibling scalar params`() {
        val raw = """{"tool":"set_volume","action":"set","level":40}"""
        val action = JsonActionParser.parse(raw)
        assertEquals("set_volume", action!!.tool)
        assertEquals("set", action.parameters["action"])
        assertEquals("40", action.parameters["level"])
    }

    @Test
    fun `normalizes tool name casing and spaces`() {
        val raw = """{"tool":"Set Alarm","parameters":{"hour":"7"}}"""
        val action = JsonActionParser.parse(raw)
        assertEquals("set_alarm", action!!.tool)
    }

    @Test
    fun `returns null for plain prose`() {
        assertNull(JsonActionParser.parse("The weather in Seoul is 12 degrees, sir."))
    }

    @Test
    fun `handles braces inside strings`() {
        val raw = """{"tool":"set_reminder","parameters":{"message":"bring the {blue} folder"}}"""
        val action = JsonActionParser.parse(raw)
        assertEquals("set_reminder", action!!.tool)
        assertEquals("bring the {blue} folder", action.parameters["message"])
    }

    @Test
    fun `extracts multiple balanced objects`() {
        val text = """prefix {"a":1} middle {"tool":"x"} suffix"""
        val objects = JsonActionParser.extractJsonObjects(text)
        assertEquals(2, objects.size)
    }
}
