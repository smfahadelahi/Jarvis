package com.jarvis.ai.core.tools.impl

import com.jarvis.ai.core.tools.ParamSpec
import com.jarvis.ai.core.tools.Tool
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.ToolResult

/**
 * Korean language tutoring tool.
 *
 * The tool itself carries no pedagogy — it assembles a disciplined lesson
 * scaffold and hands it to the language core, which renders the actual
 * instruction. The scaffold keeps a 1B model focused: one concept, hangul
 * first, romanization, meaning, example, practice.
 */
class KoreanTutorTool : Tool {
    override val name = "korean_tutor"
    override val description =
        "Deliver a Korean mini-lesson. Params: topic (string, optional), level (string, optional: beginner|intermediate)."
    override val parameters = listOf(
        ParamSpec("topic", "string", required = false, description = "lesson topic or theme"),
        ParamSpec("level", "string", required = false, description = "beginner or intermediate")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val topic = params["topic"]?.trim()?.takeIf { it.isNotEmpty() } ?: "everyday essentials"
        val level = params["level"]?.trim()?.lowercase()?.takeIf { it in setOf("beginner", "intermediate") }
            ?: "beginner"

        val scaffold = buildString {
            appendLine("Korean mini-lesson requested. topic: $topic, level: $level.")
            appendLine("Deliver, in this exact order:")
            appendLine("1. One target phrase (Hangul) + romanization + natural English meaning.")
            appendLine("2. Word-by-word breakdown, hyphenated romanization.")
            appendLine("3. One short example dialogue (2 lines).")
            appendLine("4. One practice question for the user to answer in Korean.")
            appendLine("Keep it under 120 words. No preamble.")
        }
        return ToolResult(
            true,
            "Korean lesson prepared, sir. Shall we begin?",
            dataForLlm = scaffold
        )
    }

    companion object {
        fun register(registry: ToolRegistry) {
            registry.register(KoreanTutorTool())
        }
    }
}
