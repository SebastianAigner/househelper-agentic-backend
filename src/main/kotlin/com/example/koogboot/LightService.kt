package com.example.koogboot

import org.springframework.stereotype.Service
import kotlin.math.roundToInt

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

data class LightBulb(
    val id: String,
    val name: String,
    val color: RgbColor,
    val brightness: Int,
    val on: Boolean,
    val maximumWatts: Double,
) {
    val watts: Double
        get() = if (on) maximumWatts * brightness / 100.0 else 0.0
}

data class Appliance(
    val id: String,
    val name: String,
    val description: String,
    val on: Boolean,
    val wattsWhenOn: Double,
) {
    val watts: Double
        get() = if (on) wattsWhenOn else 0.0
}

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
class LightService {

    private val lights = defaultLights()

    @Synchronized
    fun getLights(): List<LightBulb> = lights.values.toList()

    @Synchronized
    fun getLight(id: String): LightBulb = lights[id] ?: unknownLight(id)

    @Synchronized
    fun setPower(id: String, on: Boolean): LightBulb = update(id) { it.copy(on = on) }

    @Synchronized
    fun setBrightness(id: String, brightness: Int): LightBulb {
        require(brightness in 0..100) { "Brightness must be between 0 and 100" }
        return update(id) { it.copy(brightness = brightness) }
    }

    @Synchronized
    fun setColor(id: String, red: Int, green: Int, blue: Int): LightBulb {
        requireValidColor(red, green, blue)
        return update(id) { it.copy(color = RgbColor(red, green, blue)) }
    }

    @Synchronized
    fun reset() {
        lights.clear()
        lights.putAll(defaultLights())
    }

    private fun update(id: String, transform: (LightBulb) -> LightBulb): LightBulb {
        val updated = transform(lights[id] ?: unknownLight(id))
        lights[id] = updated
        return updated
    }

    private fun unknownLight(id: String): Nothing =
        throw IllegalArgumentException("Unknown light '$id'")

    private fun requireValidColor(red: Int, green: Int, blue: Int) {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "RGB values must each be between 0 and 255"
        }
    }

    private companion object {
        fun defaultLights() = linkedMapOf(
            "living-room" to light("living-room", "Living room", brightness = 100, on = true),
            "kitchen" to light("kitchen", "Kitchen", brightness = 80, on = true),
            "bedroom" to light("bedroom", "Bedroom", brightness = 35, on = true),
        )

        fun light(id: String, name: String, brightness: Int, on: Boolean) = LightBulb(
            id = id,
            name = name,
            color = RgbColor(red = 255, green = 255, blue = 255),
            brightness = brightness,
            on = on,
            maximumWatts = 12.0,
        )
    }
}

@Service
class ApplianceService {

    private val appliances = defaultAppliances()

    @Synchronized
    fun getAppliances(): List<Appliance> = appliances.values.toList()

    @Synchronized
    fun setPower(id: String, on: Boolean): Appliance {
        val appliance = appliances[id] ?: throw IllegalArgumentException("Unknown appliance '$id'")
        return appliance.copy(on = on).also { appliances[id] = it }
    }

    @Synchronized
    fun reset() {
        appliances.clear()
        appliances.putAll(defaultAppliances())
    }

    private companion object {
        fun defaultAppliances() = linkedMapOf(
            "car-charger" to appliance(
                id = "car-charger",
                name = "Car charger",
                description = "Charges the electric car; interrupting it is usually acceptable unless charging is urgent.",
                wattsWhenOn = 7_200.0,
            ),
            "towel-warmer" to appliance(
                id = "towel-warmer",
                name = "Towel warmer",
                description = "Keeps bathroom towels warm; this is a comfort device and can safely be switched off.",
                wattsWhenOn = 150.0,
            ),
            "heat-blower" to appliance(
                id = "heat-blower",
                name = "Heat blower",
                description = "Provides space heating; prioritize occupant comfort when deciding whether to switch it off.",
                wattsWhenOn = 2_000.0,
            ),
            "refrigerator" to appliance(
                id = "refrigerator",
                name = "Refrigerator",
                description = "Keeps food safely chilled and should normally remain on, even while the home is unoccupied.",
                wattsWhenOn = 500.0,
            ),
        )

        fun appliance(id: String, name: String, description: String, wattsWhenOn: Double) = Appliance(
            id = id,
            name = name,
            description = description,
            on = true,
            wattsWhenOn = wattsWhenOn,
        )
    }
}

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