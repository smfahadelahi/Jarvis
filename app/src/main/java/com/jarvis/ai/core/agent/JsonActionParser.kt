package com.jarvis.ai.core.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust extraction of a tool-call from raw LLM output.
 *
 * Small on-device models leak prose around JSON, wrap JSON in markdown fences,
 * or rename keys ("name"/"action"/"tool"). This parser tolerates all of it and
 * returns null when the output is clearly plain prose.
 */
object JsonActionParser {

    data class Action(val tool: String, val parameters: Map<String, String>)

    private val TOOL_KEYS = listOf("tool", "tool_name", "name", "action")
    private val PARAM_KEYS = listOf("parameters", "params", "args", "arguments")

    /** Returns the first parseable action in [raw], or null. */
    fun parse(raw: String): Action? {
        val cleaned = raw.replace("```json", "").replace("```", "")
        for (candidate in extractJsonObjects(cleaned)) {
            parseObject(candidate)?.let { return it }
        }
        return null
    }

    private fun parseObject(text: String): Action? {
        val obj = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }

        val toolKey = TOOL_KEYS.firstOrNull { obj.has(it) } ?: return null
        val tool = normalizeToolName(obj.optString(toolKey))
        if (tool.isEmpty()) return null

        val params = LinkedHashMap<String, String>()

        for (key in PARAM_KEYS) {
            val value = obj.opt(key)
            if (value is JSONObject) {
                value.keys().forEach { paramKey ->
                    params[paramKey] = flatten(value.opt(paramKey))
                }
                break
            }
        }

        // Fallback: sibling scalar keys act as parameters ({"tool":"x","hour":7}).
        if (params.isEmpty()) {
            obj.keys().forEach { key ->
                if (TOOL_KEYS.contains(key)) return@forEach
                val value = obj.opt(key)
                if (value is JSONObject || value is JSONArray) return@forEach
                params[key] = flatten(value)
            }
        }

        return Action(tool, params)
    }

    private fun normalizeToolName(raw: String): String =
        raw.trim().lowercase().replace(' ', '_').replace('-', '_')

    private fun flatten(value: Any?): String = when (value) {
        null -> ""
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        else -> value.toString()
    }

    /**
     * Scans [text] for balanced `{...}` blocks, ignoring braces that appear
     * inside JSON string literals. Returns the raw text of each block.
     */
    internal fun extractJsonObjects(text: String): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '{') {
                i++
                continue
            }
            val sb = StringBuilder()
            var depth = 0
            var inString = false
            var escaped = false
            var closed = false
            var j = i
            while (j < text.length) {
                val ch = text[j]
                sb.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = !inString
                    ch == '{' && !inString -> depth++
                    ch == '}' && !inString -> {
                        depth--
                        if (depth == 0) {
                            closed = true
                            break
                        }
                    }
                }
                j++
            }
            if (closed) {
                results.add(sb.toString())
                i = j + 1
            } else {
                i++
            }
        }
        return results
    }
}
