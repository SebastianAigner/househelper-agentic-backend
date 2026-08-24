package io.sebi.househelper

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.executor.model.PromptExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

sealed interface ChatStreamEvent {
    data class Text(val text: String) : ChatStreamEvent
    data class ToolCall(val text: String) : ChatStreamEvent
}

private data class StreamChatInput(
    val message: String,
    val emit: suspend (ChatStreamEvent) -> Unit,
)

@Service
class ChatService(
    @Qualifier("openAIExecutor")
    private val executor: PromptExecutor,
    lightService: LightService,
    applianceService: ApplianceService,
    weatherService: WeatherService,
) {

    private val systemPrompt = """
        You are a helpful home assistant. Use the available tools to perform requested actions.
        Complete every requested action before replying.
        When setting a light's color, turn it on unless the user explicitly asks you not to.
        Never claim that you performed or will perform an action unless you actually invoke the required tool first.
        Avoid markdown bold or italic formatting.
        Always respond in English first, then provide a Japanese translation of your full response beneath it, separated by a blank line.
    """.trimIndent()

    private val toolRegistry = ToolRegistry {
        tools(WeatherTools(weatherService).asTools()) // RM
        tools(LightTools(lightService).asTools())
        tools(ApplianceTools(applianceService).asTools())
    }

    private val strategy = functionalStrategy<String, String> { input ->
        var response = requestLLM(input)
        var iterations = 0
        var toolCalls = getToolCalls(response)

        while (toolCalls.isNotEmpty()) {
            check(++iterations <= 50) { "Agent exceeded the tool-call limit" }
            response = sendToolResults(executeTools(toolCalls))
            toolCalls = getToolCalls(response)
        }

        getTextParts(response)
            .joinToString(separator = "\n") { it.text }
    }

    private val streamingStrategy = functionalStrategy<StreamChatInput, String> { (message, emit) ->
        var response = requestLLM(message)
        var iterations = 0

        while (true) {
            response.parts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> part.text.chunked(48).forEach { chunk ->
                        emit(ChatStreamEvent.Text(chunk))
                    }
                    is MessagePart.Tool.Call -> emit(ChatStreamEvent.ToolCall("${part.tool} ${part.args}"))
                    else -> Unit
                }
            }

            val toolCalls = getToolCalls(response)
            if (toolCalls.isEmpty()) break

            check(++iterations <= 50) { "Agent exceeded the tool-call limit" }
            response = sendToolResults(executeTools(toolCalls))
        }

        getTextParts(response)
            .joinToString(separator = "\n") { it.text }
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        llmModel = GPT56Luna,
        toolRegistry = toolRegistry,
        strategy = strategy,
        systemPrompt = systemPrompt,
    )

    private val streamingAgent = AIAgent(
        promptExecutor = executor,
        llmModel = GPT56Luna,
        toolRegistry = toolRegistry,
        strategy = streamingStrategy,
        systemPrompt = systemPrompt,
    )

    suspend fun chat(message: String): String = agent.run(message)

    suspend fun streamChat(message: String, emit: suspend (ChatStreamEvent) -> Unit) {
        streamingAgent.run(StreamChatInput(message, emit))
    }
}
