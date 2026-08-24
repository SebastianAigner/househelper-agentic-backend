package io.sebi.househelper

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class WeatherConditions(
    val location: String,
    val condition: String,
    val temperature: Int,
)

@Service
class WeatherService {

    suspend fun getWeather(location: String): WeatherConditions {
        delay(200.milliseconds) // simulate service delay
        val conditions = listOf("sunny", "cloudy", "rainy", "windy", "snowy")
        val condition = conditions[Random(location.hashCode()).nextInt(conditions.size)]
        val temperature = 5 + Random(location.hashCode() * 31).nextInt(30)
        return WeatherConditions(location, condition, temperature)
    }
}
