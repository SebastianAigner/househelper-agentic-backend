package com.example.koogboot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

// Sonnet 5 isn't in ai.koog's AnthropicModels catalog yet, so it's defined here directly.
val ClaudeSonnet5: LLModel = LLModel(
    provider = LLMProvider.Anthropic,
    id = "claude-sonnet-5",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Vision.Image,
        LLMCapability.Document,
        LLMCapability.Completion,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.Thinking,
        LLMCapability.PromptCaching,
    ),
    contextLength = 200_000,
    maxOutputTokens = 64_000,
)

@Service
class ChatService(
    @Qualifier("anthropicExecutor")
    private val executor: PromptExecutor,
    lightService: LightService,
) {

    private val toolRegistry = ToolRegistry {
        tools(WeatherTools().asTools())
        tools(LightTools(lightService).asTools())
    }

    private val strategy = functionalStrategy<String, String> { input ->
        var response = requestLLM(input)
        var iterations = 0
        var toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()

        while (toolCalls.isNotEmpty()) {
            check(++iterations <= 50) { "Agent exceeded the tool-call limit" }
            response = sendToolResults(executeTools(toolCalls))
            toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
        }

        response.parts.filterIsInstance<MessagePart.Text>()
            .joinToString(separator = "\n") { it.text }
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        llmModel = ClaudeSonnet5,
        toolRegistry = toolRegistry,
        strategy = strategy,
        systemPrompt = """
            You are a helpful home assistant. Use the available tools to perform requested actions.
            Complete every requested action before replying. Use one individual tool call per affected light.
            When setting a light's color, turn it on unless the user explicitly asks you not to.
            Never claim that you performed or will perform an action unless you actually invoke the required tool first.
            Avoid markdown bold or italic formatting.
        """.trimIndent(),
    )

    suspend fun chat(message: String): String = agent.run(message)
}
