package io.sebi.househelper.light

import org.springframework.stereotype.Service

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
