package com.anchor.adhd.ai

import com.anchor.adhd.data.db.AiJobDao
import com.anchor.adhd.data.model.AiJobEntity
import com.anchor.adhd.data.model.AiJobStatus
import com.anchor.adhd.data.model.AiJobType
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.domain.parseBrainDumpWithoutModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class AiRepository(
    private val aiEngine: LocalAiEngine,
    private val aiJobDao: AiJobDao,
    private val preferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun breakdownTask(
        title: String,
        notes: String = ""
    ): Result<BreakdownWithPreview> =
        runJob(AiJobType.BREAKDOWN, "$title\n$notes") { input ->
            val titleLine = input.lines().firstOrNull().orEmpty()
            val system = LocalAiEngine.BREAKDOWN_SYSTEM
            val raw = generate(
                AiJobType.BREAKDOWN,
                system,
                "Break down this task: $titleLine" + notes.takeIf { it.isNotBlank() }?.let { ". Notes: $it" }.orEmpty()
            )
            BreakdownWithPreview(decodeBreakdown(raw), raw)
        }

    suspend fun brainDump(text: String): Result<AiBrainDumpResult> {
        if (!preferences.isModelDownloaded()) {
            return Result.success(
                AiBrainDumpResult(tasks = parseBrainDumpWithoutModel(text))
            )
        }
        return runJob(AiJobType.BRAINDUMP, text) {
            decodeBrainDump(generate(AiJobType.BRAINDUMP, LocalAiEngine.BRAINDUMP_SYSTEM, "Parse brain dump:\n$it"))
        }
    }

    suspend fun triage(taskTitles: List<String>): Result<AiTriageResult> =
        runJob(AiJobType.TRIAGE, taskTitles.joinToString("\n")) {
            decodeTriage(generate(AiJobType.TRIAGE, LocalAiEngine.TRIAGE_SYSTEM, "Order these tasks:\n${taskTitles.joinToString("\n") { title -> "- $title" }}"))
        }

    suspend fun replanAssistant(missedTitles: List<String>): Result<AiReplanResult> =
        runJob(AiJobType.REPLAN, missedTitles.joinToString("\n")) {
            decodeReplan(generate(AiJobType.REPLAN, LocalAiEngine.REPLAN_SYSTEM, "Replan missed tasks:\n${missedTitles.joinToString("\n") { title -> "- $title" }}"))
        }

    suspend fun weeklyPlan(context: String): Result<AiBreakdownResult> =
        runJob(AiJobType.WEEKLY, context) {
            decodeBreakdown(
                generate(AiJobType.WEEKLY, LocalAiEngine.WEEKLY_SYSTEM, "Weekly plan context:\n$it")
            )
        }

    suspend fun generateIfThen(routineName: String, cue: String): Result<AiBreakdownResult> =
        runJob(AiJobType.IF_THEN, "$routineName\n$cue") {
            decodeBreakdown(
                generate(
                    AiJobType.IF_THEN,
                    LocalAiEngine.IF_THEN_SYSTEM,
                    "Routine: $routineName. Cue: $cue"
                )
            )
        }

    /**
     * One GBNF chat turn: closed {intent, query}, max 128 tokens. Kotlin templates the bubble.
     */
    suspend fun planChatTurn(text: String, compactDayContext: String): Pair<String, String>? {
        if (!preferences.isModelDownloaded()) return null
        val user = "Day: $compactDayContext\nUser: $text"
        return runCatching {
            val raw = aiEngine.generateJson(
                LocalAiEngine.CHAT_TURN_SYSTEM,
                user,
                grammar = AiGrammar.chatTurnGrammar,
                maxTokens = 128,
                keepLoaded = true,
                contextSize = LocalAiEngine.CHAT_CONTEXT_SIZE
            )
            val intent = Regex("\"intent\"\\s*:\\s*\"([a-z_]+)\"").find(raw)?.groupValues?.get(1)
            val query = Regex("\"query\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(raw)?.groupValues?.get(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: text
            if (intent.isNullOrBlank()) null else intent to query
        }.getOrNull()
    }

    /**
     * Phase B: on-device intent classification. Returns one of the Anchor Chat verb strings,
     * or null if the model isn't downloaded / classification fails. The GBNF locks the verb set.
     */
    suspend fun classifyIntent(text: String): String? = planChatTurn(text, compactDayContext = "")?.first

    /**
     * Answers conversational turns with the actual local model instead of treating every
     * non-command as a capability-list request.
     */
    suspend fun answerChat(text: String, compactDayContext: String, history: List<Pair<String, String>> = emptyList()): Result<String> = runCatching {
        check(preferences.isModelDownloaded()) { "The bundled desktop model is still being prepared" }
        val output = StringBuilder()
        aiEngine.generate(
            if (compactDayContext.isBlank()) LocalAiEngine.CHAT_RESPONSE_SYSTEM else
                "${LocalAiEngine.CHAT_RESPONSE_SYSTEM}\n\nUser's current uncompleted tasks:\n${compactDayContext.take(2000)}",
            text,
            maxTokens = 256,
            keepLoaded = true,
            contextSize = LocalAiEngine.CHAT_CONTEXT_SIZE,
            history = history
        ).collect { output.append(it) }
        cleanChatAnswer(output.toString())
    }.mapCatching { answer ->
        if (answer.isBlank()) error("The model returned an empty answer") else answer
    }

    private fun cleanChatAnswer(raw: String): String {
        var answer = raw
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace("<|im_end|>", "")
            .trim()
        if (answer.startsWith("response", ignoreCase = true)) {
            answer = answer.substringAfter('\n', answer).trim()
        }
        return answer.take(1200)
    }

    private suspend fun <T> runJob(
        type: AiJobType,
        input: String,
        block: suspend (String) -> T
    ): Result<T> {
        val job = AiJobEntity(type = type, inputText = input)
        val jobId = aiJobDao.insert(job)
        aiJobDao.update(job.copy(id = jobId, status = AiJobStatus.RUNNING))
        var rawOutput = ""
        var elapsed = 0L
        return try {
            var result: T
            elapsed = measureTimeMillis {
                result = block(input)
                rawOutput = encodeJobOutput(result)
            }
            aiJobDao.update(
                job.copy(id = jobId, outputJson = rawOutput, status = AiJobStatus.SUCCESS, durationMs = elapsed)
            )
            Result.success(result)
        } catch (e: CancellationException) {
            aiJobDao.update(
                job.copy(id = jobId, rawOutput = rawOutput, status = AiJobStatus.FAILED, durationMs = elapsed)
            )
            throw e
        } catch (e: Exception) {
            aiJobDao.update(
                job.copy(
                    id = jobId,
                    outputJson = rawOutput.takeIf { it.isNotBlank() },
                    rawOutput = rawOutput,
                    status = AiJobStatus.FAILED,
                    durationMs = elapsed
                )
            )
            Result.failure(e)
        }
    }

    private fun encodeJobOutput(result: Any?): String = when (result) {
        is BreakdownWithPreview -> result.rawJson
        is AiBrainDumpResult -> json.encodeToString(AiBrainDumpResult.serializer(), result)
        is AiTriageResult -> json.encodeToString(AiTriageResult.serializer(), result)
        is AiReplanResult -> json.encodeToString(AiReplanResult.serializer(), result)
        is AiBreakdownResult -> json.encodeToString(AiBreakdownResult.serializer(), result)
        null -> error("AI job produced null result")
        else -> error("Unsupported AI job output type: ${result::class.simpleName}")
    }

    private suspend fun generate(
        type: AiJobType,
        system: String,
        user: String
    ): String {
        return if (preferences.isModelDownloaded()) {
            aiEngine.generateJson(
                system,
                user,
                grammar = AiGrammar.forJob(type),
                maxTokens = maxTokensFor(type),
                keepLoaded = true,
                contextSize = LocalAiEngine.CHAT_CONTEXT_SIZE
            )
        } else {
            StubAiResponses.generate(type, user)
        }
    }

    private fun decodeBreakdown(raw: String): AiBreakdownResult =
        runCatching { json.decodeFromString<AiBreakdownResult>(raw) }
            .getOrElse {
                val lines = linesFallback(raw)
                AiBreakdownResult(
                    steps = lines.take(7).ifEmpty { listOf("Start task") },
                    next_action = lines.firstOrNull() ?: "Start task",
                    minutes_estimate = List(lines.take(7).size.coerceAtLeast(1)) { 15 }
                )
            }

    private fun decodeBrainDump(raw: String): AiBrainDumpResult =
        runCatching { json.decodeFromString<AiBrainDumpResult>(raw) }
            .getOrElse { AiBrainDumpResult(tasks = linesFallback(raw).take(8)) }

    private fun decodeTriage(raw: String): AiTriageResult =
        runCatching { json.decodeFromString<AiTriageResult>(raw) }
            .getOrElse {
                AiTriageResult(
                    ordered_task_titles = linesFallback(raw).take(12),
                    rationale = "Parsed from line fallback"
                )
            }

    private fun decodeReplan(raw: String): AiReplanResult =
        runCatching { json.decodeFromString<AiReplanResult>(raw) }
            .getOrElse {
                val lines = linesFallback(raw)
                AiReplanResult(
                    recommended_titles = lines.take(2),
                    defer_titles = lines.drop(2),
                    message = "Parsed from line fallback"
                )
            }

    private fun maxTokensFor(type: AiJobType): Int = DesktopInferenceProfile.MAX_TOKENS

    fun linesFallback(raw: String): List<String> =
        raw.lines().map { it.trim().trimStart('-', '*', '•', ' ') }
            .filter { it.isNotBlank() }

    /**
     * Builds a parent task (the thing being broken down) + child steps linked via parentTaskId,
     * so steps do not flood the inbox as independent tasks.
     */
    fun breakdownParentAndChildren(parentTitle: String, result: AiBreakdownResult): Pair<TaskEntity, List<TaskEntity>> {
        val parentMinutes = result.minutes_estimate.sum().coerceAtLeast(result.steps.size * 5)
        val parent = TaskEntity(
            title = parentTitle.ifBlank { result.next_action },
            inboxState = InboxState.TODAY,
            durationMinutes = parentMinutes,
            estimatedMinutes = parentMinutes,
            isNextAction = true,
            aiGenerated = true,
            ifThen = result.if_then,
            difficulty = when {
                parentMinutes <= 15 -> TaskDifficulty.LIGHT
                parentMinutes <= 30 -> TaskDifficulty.MEDIUM
                else -> TaskDifficulty.DEEP
            }
        )
        val children = result.steps.mapIndexed { index, step ->
            val minutes = result.minutes_estimate.getOrNull(index) ?: 15
            TaskEntity(
                title = step,
                inboxState = InboxState.TODAY,
                durationMinutes = minutes,
                estimatedMinutes = minutes,
                isNextAction = step == result.next_action,
                aiGenerated = true,
                sortOrder = index,
                difficulty = when {
                    minutes <= 15 -> TaskDifficulty.LIGHT
                    minutes <= 30 -> TaskDifficulty.MEDIUM
                    else -> TaskDifficulty.DEEP
                }
            )
        }
        return parent to children
    }

    fun breakdownToTasks(result: AiBreakdownResult, parentTitle: String): List<TaskEntity> {
        return result.steps.mapIndexed { index, step ->
            val minutes = result.minutes_estimate.getOrNull(index) ?: 15
            TaskEntity(
                title = step,
                inboxState = InboxState.TODAY,
                durationMinutes = minutes,
                estimatedMinutes = minutes,
                isNextAction = step == result.next_action,
                aiGenerated = true,
                ifThen = if (index == 0) result.if_then else null,
                difficulty = when {
                    minutes <= 15 -> TaskDifficulty.LIGHT
                    minutes <= 30 -> TaskDifficulty.MEDIUM
                    else -> TaskDifficulty.DEEP
                }
            )
        }
    }

    fun brainDumpToTasks(result: AiBrainDumpResult): List<TaskEntity> =
        result.tasks.map { TaskEntity(title = it, inboxState = InboxState.TODAY, aiGenerated = true) }
}

data class BreakdownWithPreview(
    val result: AiBreakdownResult,
    val rawJson: String
)
