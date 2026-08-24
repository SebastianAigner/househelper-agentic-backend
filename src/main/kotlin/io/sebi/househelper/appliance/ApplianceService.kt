package io.sebi.househelper.appliance

import org.springframework.stereotype.Service

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
