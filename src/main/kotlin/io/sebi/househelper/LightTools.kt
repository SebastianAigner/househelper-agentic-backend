package io.sebi.househelper

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for inspecting and controlling the mocked lights in the home")
class LightTools(private val lightService: LightService) : ToolSet {

    @Tool
    @LLMDescription("List all lights and their current power, brightness, and RGB color state")
    fun getLights(): String = lightService.getLights().joinToString(separator = "\n", transform = ::describe)

    @Tool
    @LLMDescription("Turn a light on or off; this is the dedicated tool for changing a light's power state")
    fun setLightPower(
        @LLMDescription("The light ID, such as living-room, kitchen, or bedroom") id: String,
        @LLMDescription("True to turn the light on, false to turn it off") on: Boolean,
    ): String = describe(lightService.setPower(id, on))

    @Tool
    @LLMDescription("Set a light's brightness")
    fun setLightBrightness(
        @LLMDescription("The light ID, such as living-room, kitchen, or bedroom") id: String,
        @LLMDescription("Brightness from 0 to 100") brightness: Int,
    ): String = describe(lightService.setBrightness(id, brightness))

    @Tool
    @LLMDescription("Set a light's RGB color without changing whether it is on or off; use setLightPower separately to change its power state")
    fun setLightColor(
        @LLMDescription("The light ID, such as living-room, kitchen, or bedroom") id: String,
        @LLMDescription("Red channel from 0 to 255") red: Int,
        @LLMDescription("Green channel from 0 to 255") green: Int,
        @LLMDescription("Blue channel from 0 to 255") blue: Int,
    ): String = describe(lightService.setColor(id, red, green, blue))

    private fun describe(light: LightBulb): String = with(light) {
        "$id ($name): ${if (on) "on" else "off"}, brightness $brightness, " +
            "color rgb(${color.red}, ${color.green}, ${color.blue})"
    }
}