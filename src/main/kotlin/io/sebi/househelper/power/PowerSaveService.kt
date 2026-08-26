package io.sebi.househelper.power

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.appliance.ApplianceTools
import io.sebi.househelper.config.lunaAgentConfig
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
    val retries: Int = 0,
    val response: String = "(no response)",
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
        TODO("Implement power saving strategy")
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        agentConfig = lunaAgentConfig(
            """
                You manage a home's devices to satisfy a power target.
                Always inspect all available devices before acting, then use the tools to make sensible trade-offs.
                Individual device wattages are unavailable to you. Never ask for or invent individual wattages.
                The functional strategy will calculate aggregate consumption after each action round and ask you to retry if needed.
                Write your final response in English, followed by a Japanese translation.
                Avoid markdown bold or italic formatting in your responses.
            """.trimIndent(),
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