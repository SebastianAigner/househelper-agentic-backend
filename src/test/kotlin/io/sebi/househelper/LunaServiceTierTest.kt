package io.sebi.househelper

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import io.sebi.househelper.config.GPT56Luna
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

// OpenAI's docs only document Fast mode (service_tier=priority/fast) for gpt-5.6-sol; Luna
// support is an unverified third-party claim. This measures real request latency with and
// without service_tier=priority instead of trusting docs either way.
// Hits the real OpenAI endpoint via OPENAI_API_KEY (see PowerSaveGraphServiceTest); skips
// itself when no key is available, e.g. in CI.
class LunaServiceTierTest {

    private data class RunResult(val elapsedMs: Long, val wordCount: Int, val finishReason: String?)

    private fun timedRun(
        executor: PromptExecutor,
        tier: ServiceTier?,
        userMessage: String,
        maxTokens: Int? = null,
    ): RunResult {
        val prompt = Prompt.build(
            id = "luna-service-tier-probe",
            params = OpenAIResponsesParams(serviceTier = tier, maxTokens = maxTokens),
        ) {
            user(userMessage)
        }
        val start = System.nanoTime()
        val response = runBlocking { executor.execute(prompt, GPT56Luna) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val text = response.parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text }
        return RunResult(elapsedMs, text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size, response.finishReason)
    }

    @Test
    fun `compares latency with and without service_tier=priority`() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY not set; skipping real-LLM test" }
        val executor = simpleOpenAIExecutor(apiKey)
        val shortPrompt = "Reply with exactly one word: OK"

        val baseline = List(3) { timedRun(executor, null, shortPrompt) }
        val priority = List(3) { timedRun(executor, ServiceTier.PRIORITY, shortPrompt) }

        println("baseline (no service_tier): ${baseline.map { it.elapsedMs }}, avg=${baseline.map { it.elapsedMs }.average()}")
        println("service_tier=priority:      ${priority.map { it.elapsedMs }}, avg=${priority.map { it.elapsedMs }.average()}")
    }

    @Test
    fun `compares latency with and without service_tier=priority for a long-form essay`() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY not set; skipping real-LLM test" }
        val executor = simpleOpenAIExecutor(apiKey)
        val essayPrompt = "Write a roughly 2000-word essay on the history of beer in Munich, " +
            "covering its brewing traditions, key breweries, the Reinheitsgebot, and Oktoberfest."

        val baseline = List(2) { timedRun(executor, null, essayPrompt, maxTokens = 6000) }
        val priority = List(2) { timedRun(executor, ServiceTier.PRIORITY, essayPrompt, maxTokens = 6000) }

        baseline.forEachIndexed { i, r -> println("baseline run $i: ${r.elapsedMs}ms, ${r.wordCount} words, finishReason=${r.finishReason}") }
        priority.forEachIndexed { i, r -> println("priority run $i: ${r.elapsedMs}ms, ${r.wordCount} words, finishReason=${r.finishReason}") }

        println("baseline avg ms:            ${baseline.map { it.elapsedMs }.average()}")
        println("service_tier=priority avg ms: ${priority.map { it.elapsedMs }.average()}")
    }
}
