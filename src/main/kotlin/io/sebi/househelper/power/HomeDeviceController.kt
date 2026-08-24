package io.sebi.househelper.power

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/devices")
class HomeDeviceController(private val homePowerService: HomePowerService) {

    @GetMapping
    fun getDevices(): List<HomeDeviceStatus> = homePowerService.getDeviceStatuses()

    @PostMapping("/reset")
    fun resetHome(): List<HomeDeviceStatus> = homePowerService.resetHome()
}