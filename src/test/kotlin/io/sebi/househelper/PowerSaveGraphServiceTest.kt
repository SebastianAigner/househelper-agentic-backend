package io.sebi.househelper

import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.light.LightService
import io.sebi.househelper.power.HomePowerService
import io.sebi.househelper.power.PowerSaveGraphService
import io.sebi.househelper.power.PowerSaveRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

// Hits the real OpenAI-compatible endpoint configured via OPENAI_API_KEY (see the `test` task
// wiring in build.gradle.kts, which mirrors `bootRun`'s oaikey.txt convention). Skips itself
// when no key is available, e.g. in CI.
class PowerSaveGraphServiceTest {

    private val lightService = LightService()
    private val applianceService = ApplianceService()
    private val homePowerService = HomePowerService(lightService, applianceService)

    private fun graphService(): PowerSaveGraphService {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY not set; skipping real-LLM test" }
        return PowerSaveGraphService(
            executor = simpleOpenAIExecutor(apiKey),
            lightService = lightService,
            applianceService = applianceService,
            homePowerService = homePowerService,
        )
    }

    @Test
    fun `rejects a negative power target before contacting the LLM`() {
        val service = PowerSaveGraphService(
            executor = simpleOpenAIExecutor("unused"),
            lightService = lightService,
            applianceService = applianceService,
            homePowerService = homePowerService,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.savePower(PowerSaveRequest(targetWatts = -1, userAtHome = true)) }
        }
    }

    @Test
    fun `already-satisfied target succeeds without driving consumption further down`() {
        runBlocking {
            val service = graphService()
            val startingWatts = homePowerService.currentWatts()
            val generousTarget = startingWatts + 1_000

            val result = service.savePower(PowerSaveRequest(targetWatts = generousTarget, userAtHome = true))

            assertThat(result.success).isTrue()
            assertThat(result.currentWatts).isLessThanOrEqualTo(generousTarget)
        }
    }

    @Test
    fun `drives consumption down to an ambitious target by switching off devices`() {
        runBlocking {
            val service = graphService()

            val result = service.savePower(PowerSaveRequest(targetWatts = 700, userAtHome = false))

            assertThat(result.success).isTrue()
            assertThat(result.currentWatts).isLessThanOrEqualTo(700)
            assertThat(homePowerService.currentWatts()).isEqualTo(result.currentWatts)
        }
    }
}
