package io.sebi.househelper.power

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.appliance.ApplianceTools
import io.sebi.househelper.config.GPT56Luna
import io.sebi.househelper.light.LightService
import io.sebi.househelper.light.LightTools
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

data class PowerSaveRequest(
    val targetWatts: Int,
    val userAtHome: Boolean,
)

data class PowerSaveResult(
    val success: Boolean,
    val targetWatts: Int,
    val currentWatts: Int,
    val retries: Int,
    val response: String,
)

@Service
class PowerSaveService(
    @Qualifier("openAIExecutor")
    private val executor: PromptExecutor,
    lightService: LightService,
    applianceService: ApplianceService,
    private val homePowerService: HomePowerService,
) {

    private val toolRegistry = ToolRegistry {
        tools(LightTools(lightService).asTools())
        tools(ApplianceTools(applianceService).asTools())
    }

    private val strategy = functionalStrategy<PowerSaveRequest, PowerSaveResult> { request ->
        require(request.targetWatts >= 0) { "Power-save target cannot be negative" }

        var response = requestLLM(initialPrompt(request))
        var toolIterations = 0
        var retries = 0
        val toolCallTrace = mutableListOf<String>()
        var powerDrawReported = false
        // ⌄⌄⌄⌄⌄⌄⌄
        while (true) {
            var toolCalls = getToolCalls(response)
            while (toolCalls.isNotEmpty()) {
                check(++toolIterations <= MAX_TOOL_ITERATIONS) { "Agent exceeded the tool-call limit" }
                toolCallTrace += toolCalls.map { "[Tool] ${it.tool} ${it.args}" }
                response = sendToolResults(executeTools(toolCalls))
                toolCalls = getToolCalls(response)
                powerDrawReported = false
            }

            val currentWatts = homePowerService.currentWatts()
            when {
                currentWatts <= request.targetWatts && !powerDrawReported -> {
                    powerDrawReported = true
                    response = requestLLM(successValidationPrompt(currentWatts, request.targetWatts))
                }

                currentWatts <= request.targetWatts || retries == MAX_RETRIES ->
                    return@functionalStrategy PowerSaveResult(
                        success = currentWatts <= request.targetWatts,
                        targetWatts = request.targetWatts,
                        currentWatts = currentWatts,
                        retries = retries,
                        response = (toolCallTrace + response.textContent())
                            .filter(String::isNotBlank)
                            .joinToString(separator = "\n"),
                    )

                else -> {
                    retries++
                    response = requestLLM(failedValidationPrompt(request, currentWatts, retries))
                }
            }
        }

        error("Power-save strategy loop terminated unexpectedly")
        // ⌃⌃⌃⌃⌃⌃⌃ instead, TODO("Implement power saving strategy").
    }

    // ⌄⌄⌄⌄⌄⌄⌄
    private val minimalStrategy = functionalStrategy<PowerSaveRequest, PowerSaveResult> { request ->
        require(request.targetWatts >= 0) { "Power-save target cannot be negative" }

        var response = requestLLM(initialPrompt(request))
        while (true) {
            var toolCalls = getToolCalls(response)
            while (toolCalls.isNotEmpty()) {
                response = sendToolResults(executeTools(toolCalls))
                toolCalls = getToolCalls(response)
            }

            val currentWatts = homePowerService.currentWatts()
            if (currentWatts <= request.targetWatts) {
                return@functionalStrategy PowerSaveResult(
                    true,
                    request.targetWatts,
                    currentWatts,
                    0,
                    response.textContent()
                )
            }

            response = requestLLM(
                "The home currently draws $currentWatts W, which is above the ${request.targetWatts} W target. " +
                        "Take additional energy-saving actions now."
            )
        }

        error("Power-save strategy loop terminated unexpectedly")
    }
    // ⌃⌃⌃⌃⌃⌃⌃

    private val agent = AIAgent(
        promptExecutor = executor,
        llmModel = GPT56Luna,
        toolRegistry = toolRegistry,
        strategy = strategy,
        systemPrompt = """
            You manage a home's devices to satisfy a power target.
            Always inspect all available devices before acting, then use the tools to make sensible trade-offs.
            Individual device wattages are unavailable to you. Never ask for or invent individual wattages.
            The functional strategy will calculate aggregate consumption after each action round and ask you to retry if needed.
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
        const val MAX_TOOL_ITERATIONS = 50
    }
}