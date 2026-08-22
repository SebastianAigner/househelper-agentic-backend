package com.example.koogboot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HomePowerServiceTest {

    private val lightService = LightService()
    private val applianceService = ApplianceService()
    private val homePowerService = HomePowerService(lightService, applianceService)

    @Test
    fun `light draw scales with brightness and power state`() {
        assertThat(lightService.getLight("living-room").watts).isEqualTo(12.0)

        lightService.setBrightness("living-room", 25)
        assertThat(lightService.getLight("living-room").watts).isEqualTo(3.0)

        lightService.setPower("living-room", false)
        assertThat(lightService.getLight("living-room").watts).isZero()
    }

    @Test
    fun `busy home has expected aggregate draw`() {
        assertThat(homePowerService.currentWatts()).isEqualTo(9_876)
    }

    @Test
    fun `reset restores every device to busy-home state`() {
        lightService.setPower("living-room", false)
        lightService.setBrightness("kitchen", 5)
        applianceService.setPower("car-charger", false)
        applianceService.setPower("heat-blower", false)
        applianceService.setPower("refrigerator", false)

        val resetDevices = homePowerService.resetHome()

        assertThat(resetDevices).allMatch { it.on }
        assertThat(lightService.getLight("living-room").brightness).isEqualTo(100)
        assertThat(lightService.getLight("kitchen").brightness).isEqualTo(80)
        assertThat(applianceService.getAppliances()).anyMatch {
            it.id == "refrigerator" && it.watts == 500.0
        }
        assertThat(homePowerService.currentWatts()).isEqualTo(9_876)
    }
}