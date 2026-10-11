package com.jarvis.ai.core.agent

import kotlin.math.abs

/**
 * Canned butler lines with controlled variety.
 *
 * Used on the degraded and failure paths where no language model is
 * available to improvise. Selection hashes a seed so repeats feel organic
 * without carrying randomness state across turns.
 */
object Repertoire {

    /** Re-entrant command while a turn is already running. */
    val busy = listOf(
        "One moment, sir — still attending to your previous request.",
        "Patience, sir — I remain occupied with your last command.",
        "I haven't finished the previous task, sir. Do give me a moment."
    )

    /** Unrecoverable exception mid-turn. */
    val faults = listOf(
        "A system fault interrupted me mid-thought, sir. Retry at your leisure.",
        "Something snapped in the machinery, sir. Kindly try that again.",
        "I stumbled mid-execution, sir. Your command deserves a second attempt."
    )

    /** Greetings while the language core is offline. */
    val greetingsDormant = listOf(
        "Good day, sir. My conversational core is dormant — no language model loaded — " +
            "but the fast-action systems remain at your service: alarms, timers, torch, weather.",
        "At your service, sir, in a limited capacity. Install a language model and I'll " +
            "regain the gift of conversation; alarms and the torch work regardless."
    )

    /** Identity questions while the language core is offline. */
    val identityDormant = listOf(
        "J.A.R.V.I.S., sir — your chief of staff. Currently running on reflexes alone; " +
            "load a language model in Settings to restore my higher faculties.",
        "Just A Rather Very Intelligent System, sir — though at this moment rather less " +
            "intelligent than usual. A model in Settings would remedy that."
    )

    val thanks = listOf(
        "Always a pleasure, sir.",
        "At your service, as ever.",
        "Think nothing of it, sir."
    )

    /** Generic turn when the language core is offline and no pattern matched. */
    val coreOffline = listOf(
        "My language core is offline, sir. Install a model via Settings — meanwhile I " +
            "retain fast actions: alarms, timers, reminders, torch, volume, weather, and briefings.",
        "Without the language core I'm on reflexes only, sir — alarms, timers, torch, " +
            "weather, and briefings all answer on demand."
    )

    /** Unparseable fast command. */
    val unmapped = listOf(
        "I couldn't map that to an action, sir. Rephrase and I'll attend to it.",
        "That didn't resolve to anything I can execute, sir. Try once more?"
    )

    /** Model returned empty output. */
    val emptyThought = listOf(
        "My thoughts went quiet mid-sentence, sir. Say that again?",
        "I lost the thread there, sir — one more time?"
    )

    fun from(pool: List<String>, seed: Long = System.nanoTime()): String =
        pool[abs((seed % pool.size).toInt()).coerceIn(0, pool.size - 1)]
}
