package com.jarvis.ai.core.tools

import com.jarvis.ai.di.AppContainer

/**
 * Registry of executable tools, plus the compact text manifest embedded into
 * the system prompt.
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun all(): List<Tool> = tools.values.toList()

    fun execute(name: String, params: Map<String, String>): ToolResult {
        val tool = tools[name] ?: throw UnknownToolException(name)
        return tool.execute(params)
    }

    /**
     * One manifest line per tool:
     *   set_alarm(hour:int, minute:int, label:string?): Set the alarm clock.
     */
    fun manifest(): String = tools.values.joinToString(separator = "\n") { tool ->
        val params = tool.parameters.joinToString(", ") { spec ->
            "${spec.name}:${spec.type}" + if (!spec.required) "?" else ""
        }
        val paramText = if (params.isEmpty()) "" else "($params)"
        "${tool.name}$paramText: ${tool.description}"
    }
}

/**
 * Assembles the default toolbox with its real-world dependencies.
 */
object Toolbox {

    fun buildDefault(context: android.content.Context, container: AppContainer): ToolRegistry {
        val registry = ToolRegistry()
        com.jarvis.ai.core.tools.impl.ProductivityTools.register(context, container, registry)
        com.jarvis.ai.core.tools.impl.DeviceTools.register(context, container, registry)
        com.jarvis.ai.core.tools.impl.KnowledgeTools.register(context, container, registry)
        com.jarvis.ai.core.tools.impl.KoreanTutorTool.register(registry)
        return registry
    }
}
