package com.jarvis.ai.core.tools

/**
 * Tool abstraction for the agent loop.
 *
 * Tools are the only way Jarvis touches the real world. Each declares a
 * compact manifest (name, one-line description, parameters) that is embedded
 * in the LLM system prompt, so the manifest must stay terse for small models.
 */
interface Tool {
    /** Machine name used in tool calls, e.g. "set_alarm". Snake case. */
    val name: String

    /** One-line description shown to the language model. */
    val description: String

    /** Declared parameters. */
    val parameters: List<ParamSpec>

    /**
     * Executes the tool. Implementations must never throw for user-visible
     * failure modes — return a failed [ToolResult] instead.
     */
    suspend fun execute(params: Map<String, String>): ToolResult
}

data class ParamSpec(
    val name: String,
    val type: String = "string",
    val required: Boolean = true,
    val description: String = ""
)

data class ToolResult(
    /** Whether the action succeeded. */
    val ok: Boolean,

    /**
     * A complete, in-character spoken reply for the user. Used directly when
     * no LLM synthesis pass follows.
     */
    val spokenFallback: String,

    /**
     * Raw observation data for the LLM to synthesize into a reply. Null when
     * [spokenFallback] alone suffices.
     */
    val dataForLlm: String? = null
)

/** Thrown by the registry when the model invents a tool that does not exist. */
class UnknownToolException(toolName: String) :
    Exception("Unknown tool: $toolName")
