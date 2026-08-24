package io.sebi.househelper.sample

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.sebi.househelper.config.GPT56Luna
import kotlinx.coroutines.runBlocking

data class MyInput(val temperature: Int)
data class MyOutput(val answer: String, val userId: Int)

val myStrategy =
    functionalStrategy<MyInput, MyOutput> { input ->
        var response = requestLLM("How much power for ${input.temperature} in watts?")
        var toolCalls = getToolCalls(response)

        while (toolCalls.isNotEmpty()) {
            response = sendToolResults(executeTools(toolCalls))
            toolCalls = getToolCalls(response)
        }

        val text = response.textContent()
        if ("watts" !in text) {
            println("no watts...")
        }

        MyOutput(text, 123)
    }

fun main() = runBlocking {
    val agent = AIAgent(
        // koog-spring-ai-starter-model-chat doesn't bring prompt-executor-llms-all's
        // simpleOpenAIExecutor(); build the same MultiLLMPromptExecutor(OpenAILLMClient) it would.
        promptExecutor = MultiLLMPromptExecutor(OpenAILLMClient(System.getenv("OPENAI_API_KEY"))),
        llmModel = GPT56Luna,
    )

    val answer = agent.run("What's the smallest planet in the solar system?")
}