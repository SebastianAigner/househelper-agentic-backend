package io.sebi.househelper.light

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/lights")
class LightController(private val lightService: LightService) {

    @GetMapping
    fun getLights(): List<LightBulb> = lightService.getLights()
}