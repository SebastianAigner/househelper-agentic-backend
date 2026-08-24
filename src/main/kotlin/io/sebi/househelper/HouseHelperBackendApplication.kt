package io.sebi.househelper

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment

@SpringBootApplication
class HouseHelperBackendApplication(private val environment: Environment) {

    @EventListener(ApplicationReadyEvent::class)
    fun logStartup() {
        val port = environment.getProperty("local.server.port") ?: environment.getProperty("server.port") ?: "8080"
        println()
        println("🏠✨ HouseHelper backend running on http://localhost:$port ✨🏠")
        println()
    }
}

fun main(args: Array<String>) {
    runApplication<HouseHelperBackendApplication>(*args)
}
