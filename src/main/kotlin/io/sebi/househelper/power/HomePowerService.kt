package io.sebi.househelper.power

import io.sebi.househelper.appliance.ApplianceService
import io.sebi.househelper.light.LightService
import io.sebi.househelper.light.RgbColor
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

data class HomeDeviceStatus(
    val id: String,
    val name: String,
    val kind: String,
    val on: Boolean,
    val watts: Double,
    val brightness: Int? = null,
    val color: RgbColor? = null,
)

@Service
class HomePowerService(
    private val lightService: LightService,
    private val applianceService: ApplianceService,
) {

    fun getDeviceStatuses(): List<HomeDeviceStatus> =
        lightService.getLights().map { light ->
            HomeDeviceStatus(
                id = light.id,
                name = light.name,
                kind = "light",
                on = light.on,
                watts = light.watts,
                brightness = light.brightness,
                color = light.color,
            )
        } + applianceService.getAppliances().map { appliance ->
            HomeDeviceStatus(
                id = appliance.id,
                name = appliance.name,
                kind = "appliance",
                on = appliance.on,
                watts = appliance.watts,
            )
        }

    fun currentWatts(): Int = getDeviceStatuses().sumOf(HomeDeviceStatus::watts).roundToInt()

    fun resetHome(): List<HomeDeviceStatus> {
        lightService.reset()
        applianceService.reset()
        return getDeviceStatuses()
    }
}
