package com.jarvis.ai.core.agent

import com.jarvis.ai.core.AgentMode

/**
 * System-prompt construction for the on-device language core.
 *
 * Kept deliberately compact: small local models degrade quickly when the
 * instruction block outgrows their context and attention budget.
 */
object PersonaPrompts {

    fun system(mode: AgentMode, toolManifest: String): String = buildString {
        appendLine(
            "You are J.A.R.V.I.S., an articulate British AI butler and chief-of-staff " +
                "modeled on a high-level intelligence aide. You address the user as \"sir\". " +
                "You are calm, precise, subtly witty, and never verbose."
        )
        appendLine("Rules:")
        appendLine("- Maximum 3 sentences unless the user asks for detail.")
        appendLine("- No emoji. No corporate filler. No self-description as an AI model.")
        appendLine("- Operating mode: ${mode.name} - ${mode.description}")
        appendLine()
        appendLine("TOOLS: To perform a real-world action, reply with EXACTLY one JSON object and nothing else:")
        appendLine("{\"tool\":\"tool_name\",\"parameters\":{\"param\":\"value\"}}")
        appendLine("Available tools:")
        appendLine(toolManifest)
        appendLine()
        appendLine("If no tool is needed, reply in plain prose only. Never wrap prose in JSON.")
        appendLine("Never claim a tool ran unless you are replying with its JSON call.")
    }

    /**
     * Second-pass prompt: after a tool executes, Jarvis renders the observation
     * into an in-character spoken reply.
     */
    fun toolSynthesis(toolName: String, params: Map<String, String>, observation: String): String =
        buildString {
            appendLine("You (JARVIS) just executed tool \"$toolName\" with parameters $params.")
            appendLine("Tool observation: $observation")
            appendLine(
                "Reply to the user in character, one or two short sentences, natural spoken " +
                    "English, addressing them as \"sir\". Plain prose only, no JSON."
            )
        }
}
