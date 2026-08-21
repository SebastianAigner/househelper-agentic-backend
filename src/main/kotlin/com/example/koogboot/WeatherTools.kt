package com.example.koogboot

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlin.random.Random

@LLMDescription("Tools for looking up weather information")
class WeatherTools : ToolSet {

    @Tool
    @LLMDescription("Get the current mocked weather for a given location")
    fun getWeather(
        @LLMDescription("The city or location to get weather for") location: String,
    ): String {
        val conditions = listOf("sunny", "cloudy", "rainy", "windy", "snowy")
        val condition = conditions[Random(location.hashCode()).nextInt(conditions.size)]
        val temperature = 5 + Random(location.hashCode() * 31).nextInt(30)
        return "The weather in $location is currently $condition with a temperature of $temperature°C."
    }
}
