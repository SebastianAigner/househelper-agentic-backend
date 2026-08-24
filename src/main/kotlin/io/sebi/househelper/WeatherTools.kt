package io.sebi.househelper

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for looking up weather information")
class WeatherTools(private val weatherService: WeatherService) : ToolSet {

    @Tool
    @LLMDescription("Get the current mocked weather for a given location")
    suspend fun getWeather(
        @LLMDescription("The city or location to get weather for") location: String,
    ): WeatherConditions = weatherService.getWeather(location)
}
