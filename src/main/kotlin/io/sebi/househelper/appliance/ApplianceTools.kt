package io.sebi.househelper.appliance

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for inspecting and controlling non-light electrical devices in the home")
class ApplianceTools(private val applianceService: ApplianceService) : ToolSet {

    @Tool
    @LLMDescription("List all non-light devices, their purpose, and whether they are on or off")
    fun getAppliances(): String = applianceService.getAppliances().joinToString(separator = "\n", transform = ::describe)

    @Tool
    @LLMDescription("Turn a non-light device on or off")
    fun setAppliancePower(
        @LLMDescription("The device ID: car-charger, towel-warmer, heat-blower, or refrigerator") id: String,
        @LLMDescription("True to turn the device on, false to turn it off") on: Boolean,
    ): String = describe(applianceService.setPower(id, on))

    private fun describe(appliance: Appliance): String = with(appliance) {
        "$id ($name): ${if (on) "on" else "off"}. $description"
    }
}