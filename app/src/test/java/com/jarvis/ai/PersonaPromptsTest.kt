package com.jarvis.ai

import com.jarvis.ai.core.AgentMode
import com.jarvis.ai.core.agent.PersonaPrompts
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PersonaPromptsTest {

    private val manifest = "set_alarm(hour:int, minute:int?): Set the alarm clock.\nflashlight(state:string): Torch."

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 11, 9, 30)

    @Test
    fun `embeds tool manifest verbatim`() {
        val prompt = PersonaPrompts.system(AgentMode.HUD, manifest, now)
        assertTrue(prompt.contains("set_alarm(hour:int, minute:int?)"))
        assertTrue(prompt.contains("flashlight(state:string)"))
    }

    @Test
    fun `embeds current mode name`() {
        assertTrue(PersonaPrompts.system(AgentMode.HUD, manifest, now).contains("Mode HUD"))
        assertTrue(PersonaPrompts.system(AgentMode.TACTICAL, manifest, now).contains("Mode TACTICAL"))
        assertTrue(PersonaPrompts.system(AgentMode.AUTONOMOUS, manifest, now).contains("Mode AUTONOMOUS"))
    }

    @Test
    fun `injects current date and time`() {
        val prompt = PersonaPrompts.system(AgentMode.HUD, manifest, now)
        assertTrue(prompt.contains("2026"))
        assertTrue(prompt.contains("09:30"))
    }

    @Test
    fun `contains one worked tool example and one prose example`() {
        val prompt = PersonaPrompts.system(AgentMode.HUD, manifest, now)
        assertTrue(prompt.contains("\"tool\":\"flashlight\""))
        assertTrue(prompt.contains("Good morning, sir"))
    }

    @Test
    fun `demands json schema shape`() {
        val prompt = PersonaPrompts.system(AgentMode.HUD, manifest, now)
        assertTrue(prompt.contains("{\"tool\":\"tool_name\",\"parameters\":{\"param\":\"value\"}}"))
    }

    @Test
    fun `synthesis prompt forbids json echo`() {
        val prompt = PersonaPrompts.toolSynthesis(
            "set_alarm",
            mapOf("hour" to "7"),
            "alarm scheduled at 07:00 today"
        )
        assertTrue(prompt.contains("set_alarm"))
        assertTrue(prompt.contains("alarm scheduled at 07:00 today"))
        assertTrue(prompt.contains("never JSON"))
    }
}
