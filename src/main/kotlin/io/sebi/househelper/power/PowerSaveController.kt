package io.sebi.househelper.power

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/power-save")
class PowerSaveController(private val powerSaveService: PowerSaveService) {

    @PostMapping
    suspend fun savePower(@RequestBody request: PowerSaveRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(powerSaveService.savePower(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid power-save request")))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Power-save request failed")))
        }
}