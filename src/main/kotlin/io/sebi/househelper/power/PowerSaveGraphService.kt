package io.sebi.househelper.power

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.ConditionResult
import ai.koog.agents.ext.agent.RetrySubgraphResult
import ai.koog.agents.ext.agent.subgraphWithRetry
import ai.koog.prompt.executor.model.PromptExecutor
import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.appliance.ApplianceTools
import io.sebi.househelper.config.lunaAgentConfig
import io.sebi.househelper.light.LightService
import io.sebi.househelper.light.LightTools

// Storage keys are stateless identifiers, so they're safe to share across agent runs; the
// values behind them live in each run's own AIAgentStorage, not in these top-level vals.
private val requestKey = createStorageKey<PowerSaveRequest>("power-save/request")
private val attemptKey = createStorageKey<Int>("power-save/attempt")

/**
 * Graph-based counterpart to [PowerSaveService]'s functional strategy.
 *
 * Where the original hand-rolls both loops with `while` and local `var`s, this version leans on
 * Koog's built-in [subgraphWithRetry]: its inner `defineAction` block is the LLM-request/tool-call
 * cycle (the original's `while (toolCalls.isNotEmpty())` loop), and `subgraphWithRetry` itself
 * supplies the outer retry cycle, including re-running the action with LLM feedback on rejection
 * and giving up after [MAX_RETRIES] retries.
 */
class PowerSaveGraphService(
    private val executor: PromptExecutor,
    lightService: LightService,
    applianceService: ApplianceService,
    private val homePowerService: HomePowerService,
) {

    private val toolRegistry = ToolRegistry {
        tools(LightTools(lightService).asTools())
        tools(ApplianceTools(applianceService).asTools())
    }

    private val strategy = strategy<PowerSaveRequest, PowerSaveResult>("power-save") {
        val preparePrompt by node<PowerSaveRequest, String> { request ->
            require(request.targetWatts >= 0) { "Power-save target cannot be negative" }
            storage.set(requestKey, request)
            initialPrompt(request)
        }

        val saveUntilTarget by subgraphWithRetry<String, String>(
            maxRetries = MAX_RETRIES,
            condition = {
                val request = storage.getValue(requestKey)
                val currentWatts = homePowerService.currentWatts()
                if (currentWatts <= request.targetWatts) {
                    ConditionResult.Approve
                } else {
                    val attempt = (storage.get(attemptKey) ?: 0) + 1
                    storage.set(attemptKey, attempt)
                    ConditionResult.Reject(failedValidationPrompt(request, currentWatts, attempt))
                }
            },
        ) {
            val callLLM by nodeLLMRequest()
            val executeTool by nodeExecuteTools()
            val sendToolResult by nodeLLMSendToolResults()

            nodeStart then callLLM

            edge(callLLM forwardTo executeTool onToolCalls { true })
            edge(callLLM forwardTo nodeFinish onTextMessage { true })
            edge(executeTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo executeTool onToolCalls { true })
            edge(sendToolResult forwardTo nodeFinish onTextMessage { true })
        }

        val toResult by node<RetrySubgraphResult<String>, PowerSaveResult> { result ->
            PowerSaveResult(
                success = result.success,
                targetWatts = storage.getValue(requestKey).targetWatts,
                currentWatts = homePowerService.currentWatts(),
                retries = result.retryCount - 1,
                response = result.output,
            )
        }

        nodeStart then preparePrompt then saveUntilTarget then toResult then nodeFinish
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        agentConfig = lunaAgentConfig(
            systemPrompt = """
                You manage a home's devices to satisfy a power target.
                Always inspect all available devices before acting, then use the tools to make sensible trade-offs.
                Individual device wattages are unavailable to you. Never ask for or invent individual wattages.
                The agent's strategy graph will calculate aggregate consumption after each action round and ask you to retry if needed.
                Write your final response in English, followed by a Japanese translation.
                Avoid markdown bold or italic formatting in your responses.
            """.trimIndent(),
            maxAgentIterations = 200,
        ),
        toolRegistry = toolRegistry,
        strategy = strategy,
    )

    suspend fun savePower(request: PowerSaveRequest): PowerSaveResult = agent.run(request)

    private fun initialPrompt(request: PowerSaveRequest): String = """
        Reduce total household power consumption to at most ${request.targetWatts} W.
        The user is currently ${if (request.userAtHome) "at home" else "away"}.
        The room is currently at 17°C.
        Assume the space to be well-insulated.
        Inspect the current device states and take an initial set of energy-saving actions.
        Maximize the comfort and convenience of any occupants, if there are any.
        Reaching the target is mandatory, but once it is reached, occupant comfort and convenience take precedence over saving additional energy.
        Account for the user's likely needs based on whether they are home or away.
    """.trimIndent()

    private fun failedValidationPrompt(request: PowerSaveRequest, currentWatts: Int, retries: Int): String = """
        Deterministic validation $retries of $MAX_RETRIES failed: the home currently draws $currentWatts W,
        which is ${currentWatts - request.targetWatts} W above the ${request.targetWatts} W target.
        Take additional energy-saving actions now. You still cannot inspect individual device wattages.
        Assume the space to be well-insulated.
        While reaching the target, maximize the comfort and convenience of any occupants, if there are any.
        Reaching the target is mandatory, but once it is reached, occupant comfort and convenience take precedence over saving additional energy.
        Prefer stronger savings if the user is away, but preserve devices that provide an essential ongoing service.
        ${if (retries == MAX_RETRIES) "This is the final retry; prefer switching off an additional active device over a small adjustment." else ""}
    """.trimIndent()

    private companion object {
        const val MAX_RETRIES = 9
    }
}
