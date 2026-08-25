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
import ai.koog.prompt.executor.model.PromptExecutor
import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.appliance.ApplianceTools
import io.sebi.househelper.config.GPT56Luna
import io.sebi.househelper.light.LightService
import io.sebi.househelper.light.LightTools

// Storage keys are stateless identifiers, so they're safe to share across agent runs; the
// values behind them live in each run's own AIAgentStorage, not in these top-level vals.
private val requestKey = createStorageKey<PowerSaveRequest>("power-save/request")
private val retriesKey = createStorageKey<Int>("power-save/retries")
private val awaitingFinalMessageKey = createStorageKey<Boolean>("power-save/awaiting-final-message")

private sealed interface PowerCheckOutcome {
    data class Continue(val prompt: String) : PowerCheckOutcome
    data class Finished(val result: PowerSaveResult) : PowerCheckOutcome
}

/**
 * Graph-based counterpart to [PowerSaveService]'s functional strategy.
 *
 * The mapping from the imperative version is direct: its inner `while (toolCalls.isNotEmpty())`
 * loop becomes the executeTool/sendToolResult cycle below, and its outer retry `while (true)` loop
 * becomes the checkPower/callLLM cycle. Per-run state (the request, retry count, whether we're
 * waiting on a final confirmation message) lives in [ai.koog.agents.core.agent.entity.AIAgentStorage]
 * instead of local `var`s, since the graph itself is built once and reused across runs.
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
            storage.set(retriesKey, 0)
            storage.set(awaitingFinalMessageKey, false)
            initialPrompt(request)
        }

        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTools()
        val sendToolResult by nodeLLMSendToolResults()

        val checkPower by node<String, PowerCheckOutcome> { responseText ->
            val request = storage.getValue(requestKey)
            val currentWatts = homePowerService.currentWatts()

            when {
                currentWatts <= request.targetWatts && storage.getValue(awaitingFinalMessageKey) ->
                    PowerCheckOutcome.Finished(
                        PowerSaveResult(
                            success = true,
                            targetWatts = request.targetWatts,
                            currentWatts = currentWatts,
                            retries = storage.getValue(retriesKey),
                            response = responseText,
                        )
                    )

                currentWatts <= request.targetWatts -> {
                    storage.set(awaitingFinalMessageKey, true)
                    PowerCheckOutcome.Continue(successValidationPrompt(currentWatts, request.targetWatts))
                }

                storage.getValue(retriesKey) >= MAX_RETRIES ->
                    PowerCheckOutcome.Finished(
                        PowerSaveResult(
                            success = false,
                            targetWatts = request.targetWatts,
                            currentWatts = currentWatts,
                            retries = storage.getValue(retriesKey),
                            response = responseText,
                        )
                    )

                else -> {
                    val retries = storage.getValue(retriesKey) + 1
                    storage.set(retriesKey, retries)
                    storage.set(awaitingFinalMessageKey, false)
                    PowerCheckOutcome.Continue(failedValidationPrompt(request, currentWatts, retries))
                }
            }
        }

        edge(nodeStart forwardTo preparePrompt)
        edge(preparePrompt forwardTo callLLM)

        edge(callLLM forwardTo executeTool onToolCalls { true })
        edge(callLLM forwardTo checkPower onTextMessage { true })

        edge(executeTool forwardTo sendToolResult)
        edge(sendToolResult forwardTo executeTool onToolCalls { true })
        edge(sendToolResult forwardTo checkPower onTextMessage { true })

        edge(
            checkPower forwardTo callLLM
                onCondition { it is PowerCheckOutcome.Continue }
                transformed { (it as PowerCheckOutcome.Continue).prompt }
        )
        edge(
            checkPower forwardTo nodeFinish
                onCondition { it is PowerCheckOutcome.Finished }
                transformed { (it as PowerCheckOutcome.Finished).result }
        )
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        llmModel = GPT56Luna,
        toolRegistry = toolRegistry,
        strategy = strategy,
        maxIterations = 200,
        systemPrompt = """
            You manage a home's devices to satisfy a power target.
            Always inspect all available devices before acting, then use the tools to make sensible trade-offs.
            Individual device wattages are unavailable to you. Never ask for or invent individual wattages.
            The agent's strategy graph will calculate aggregate consumption after each action round and ask you to retry if needed.
            Write your final response in English, followed by a Japanese translation.
            Avoid markdown bold or italic formatting in your responses.
        """.trimIndent(),
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

    private fun successValidationPrompt(currentWatts: Int, targetWatts: Int): String = """
        Deterministic validation: the home now draws $currentWatts W, which is within the $targetWatts W target.
        The final power draw must remain at or below the target.
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
