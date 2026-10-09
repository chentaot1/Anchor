package com.anchor.adhd.ai

import com.anchor.adhd.data.model.AiJobType
import com.anchor.adhd.data.model.BreakdownGranularity

/**
 * GBNF grammars for constrained JSON output (llama.cpp grammar sampler).
 * Based on llama.cpp grammars/json.gbnf with job-specific roots where helpful.
 */
object AiGrammar {
    private val JSON_VALUE = """
value ::= object | array | string | number | ("true" | "false" | "null") ws
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}" ws
array ::= "[" ws (value ("," ws value)*)? "]" ws
string ::= "\"" ([^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}))* "\"" ws
number ::= ("-"? ([0-9] | [1-9] [0-9]{0,15})) ("." [0-9]+)? ([eE] [-+]? [0-9] [1-9]{0,15})? ws
ws ::= | " " | "\n" [ \t]{0,20}
""".trimIndent()

    private val BREAKDOWN_ROOT = """
root ::= breakdown
breakdown ::= "{" ws "\"steps\"" ws ":" ws steps-array ws "," ws "\"next_action\"" ws ":" ws string ws breakdown-tail
breakdown-tail ::= "," ws "\"minutes_estimate\"" ws ":" ws mins-array breakdown-if-then | breakdown-if-then | ws "}"
breakdown-if-then ::= "," ws "\"if_then\"" ws ":" ws string ws "}" | ws "}"
steps-array ::= "[" ws string "," ws string ("," ws string)? ("," ws string)? ("," ws string)? ws "]"
mins-array ::= "[" ws number "," ws number ("," ws number)? ("," ws number)? ("," ws number)? ws "]"
""".trimIndent()

    private val BRAINDUMP_ROOT = """
root ::= brain
brain ::= "{" ws "\"tasks\"" ws ":" ws tasks-array brain-tail
brain-tail ::= "," ws "\"notes\"" ws ":" ws string ws "}" | ws "}"
tasks-array ::= "[" ws string ("," ws string)? ("," ws string)? ("," ws string)? ("," ws string)? ("," ws string)? ("," ws string)? ("," ws string)? ws "]"
""".trimIndent()

    private val TRIAGE_ROOT = """
root ::= triage
triage ::= "{" ws "\"ordered_task_titles\"" ws ":" ws triage-array triage-tail
triage-tail ::= "," ws "\"rationale\"" ws ":" ws string ws "}" | ws "}"
triage-array ::= "[" ws string ("," ws string)* ws "]"
""".trimIndent()

    private val REPLAN_ROOT = """
root ::= replan
replan ::= "{" ws "\"recommended_titles\"" ws ":" ws rec-array ws "," ws "\"defer_titles\"" ws ":" ws def-array replan-tail
replan-tail ::= "," ws "\"message\"" ws ":" ws string ws "}" | ws "}"
rec-array ::= "[" ws string ("," ws string)* ws "]"
def-array ::= "[" ws (string ("," ws string)*)? ws "]"
""".trimIndent()

    fun breakdownGrammarFor(granularity: BreakdownGranularity): String {
        val range = when (granularity) {
            BreakdownGranularity.MILD -> 2..3
            BreakdownGranularity.NORMAL -> 3..5
            BreakdownGranularity.SPICY -> 5..7
        }
        fun arrayRule(name: String, item: String): String {
            val required = List(range.first) { item }.joinToString(" \",\" ws ")
            val optional = List(range.last - range.first) { "(\",\" ws $item)?" }.joinToString(" ")
            val tail = if (optional.isBlank()) "" else " $optional"
            return "$name ::= \"[\" ws $required$tail ws \"]\""
        }
        val root = BREAKDOWN_ROOT
            .replace(Regex("(?m)^steps-array ::=.*$"), arrayRule("steps-array", "string"))
            .replace(Regex("(?m)^mins-array ::=.*$"), arrayRule("mins-array", "number"))
        return "$root\n$JSON_VALUE"
    }

    fun forJob(type: AiJobType): String = when (type) {
        AiJobType.BREAKDOWN, AiJobType.WEEKLY, AiJobType.IF_THEN ->
            "$BREAKDOWN_ROOT\n$JSON_VALUE"
        AiJobType.BRAINDUMP ->
            "$BRAINDUMP_ROOT\n$JSON_VALUE"
        AiJobType.TRIAGE ->
            "$TRIAGE_ROOT\n$JSON_VALUE"
        AiJobType.REPLAN ->
            "$REPLAN_ROOT\n$JSON_VALUE"
    }

    /**
     * Closed GBNF {intent, query} with bounded ws. Grammar completion stops the sampler.
     */
    val chatTurnGrammar: String by lazy {
        val verbs = listOf(
            "capture_tasks", "whats_today", "remind", "mark_done", "move_someday",
            "breakdown", "brain_dump", "schedule", "triage", "replan", "start_focus",
            "snooze", "pick_one", "chat", "clarify"
        )
        val alternatives = verbs.joinToString(" | ") { "\"$it\"" }
        """
        root ::= object
        object ::= "{" ws "\"intent\"" ws ":" ws intent-val ws "," ws "\"query\"" ws ":" ws string ws "}" ws
        intent-val ::= "\"" intent-word "\""
        intent-word ::= $alternatives
        string ::=
          "\"" (
            [^"\\\x7F\x00-\x1F] |
            "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4})
          )* "\"" ws
        ws ::= | " " | "\n" [ \t]{0,20}
        """.trimIndent()
    }

    /**
     * Phase B intent-classifier grammar. Locks the intent value to the closed verb set so the
     * on-device model can't emit a hallucinated intent name. Cached to avoid rebuilding per call.
     */
    val intentClassifierGrammar: String by lazy {
        val verbs = listOf(
            "capture_tasks", "whats_today", "remind", "mark_done", "move_someday",
            "breakdown", "brain_dump", "schedule", "triage", "replan", "start_focus", "snooze", "chat", "clarify"
        )
        val alternatives = verbs.joinToString(" | ") { "\"$it\"" }
        """
        root ::= "{" ws "\"intent\"" ws ":" ws intent-val ws "}" ws
        intent-val ::= "\"" intent-word "\""
        intent-word ::= $alternatives
        ws ::= | " " | "\n" [ \t]{0,20}
        """.trimIndent()
    }
}
