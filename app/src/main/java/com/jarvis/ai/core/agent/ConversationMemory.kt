package com.jarvis.ai.core.agent

/**
 * Rolling in-memory conversation window fed to the language core.
 *
 * Small models have tight context budgets, so the window is hard-capped and
 * rendered as flat dialogue lines rather than structured chat markup.
 */
class ConversationMemory(private val maxTurns: Int = 12) {

    private data class Entry(val speaker: String, val text: String)

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = entries.size

    fun addUser(text: String) = push("User", text)

    fun addJarvis(text: String) = push("JARVIS", text)

    fun addObservation(text: String) = push("System", text)

    private fun push(speaker: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        entries.addLast(Entry(speaker, trimmed))
        while (entries.size > maxTurns) entries.removeFirst()
    }

    fun render(): String = entries.joinToString(separator = "\n") { (speaker, text) ->
        "$speaker: $text"
    }

    fun clear() = entries.clear()
}
